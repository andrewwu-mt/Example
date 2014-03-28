package com.reuters.rfa.example.omm.multipleConsumers;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * Application client to manage event source & login
 * 
 * <pre>
 * Responsibilities
 * - Create OMMConsumer Event Source, Send Login Request 
 * - Use EventQ (and ResponseDispatcher) for responses, if configured
 * - Use RequestDispatcher for making item requests
 * 
 *   :----------------: 		  
 *   | ConsumerClient |-->- Session - Session
 *   :----------------:   - OMMConsumer - EventSource
 *   | ConsumerClient |   - ItemClient - Handle Item Requests/responses
 *   :----------------:   - EventQ (if configured) - For dispatching events
 *       :                - ResponseDispatcher thread - Dispatch events, if EventQ is used
 *       :                - RequestDispatcher thread - Create & Send Item requests
 * </pre>
 * 
 */
public class ConsumerClient implements Client
{
    Session m_session; // RFA session
    OMMConsumer m_consumer; // Event Source
    OMMPool m_pool; // Pool

    ItemClient m_itemClient; // Manager Item requests & responses

    // Response Message Dispatching
    private EventQueue m_responseQ;
    private ResponseDispatcher m_responseMessageDispatcher;

    // Login Status
    private Handle m_loginHandle;
    boolean m_loggedIn;

    /*
     * Constructor
     */
    protected ConsumerClient(Session session)
    {
        // _application = application;
        m_session = session;

        m_loggedIn = false;
        m_responseQ = null;
        m_responseMessageDispatcher = null;
    }

    /*
     * Cleanup
     */
    void stopTest()
    {
        log("Shutting down....");
        if (m_responseQ != null)
        {
            if (m_responseQ != null)
            {
                log("Deactivating responseQ...");
                m_responseQ.deactivate();
                m_responseQ = null;
            }

            if (m_responseMessageDispatcher != null)
            {
                log("Response Dispatcher terminating...");
                m_responseMessageDispatcher.terminate();
                m_responseMessageDispatcher = null;
                log("Response Dispatcher terminated...");
            }
        }

        if (m_pool != null)
        {
            log("Destroying pool...");
            m_pool.destroy();
            m_pool = null;
        }
        if (m_consumer != null)
        {
            log("UnregisterClient...");
            m_consumer.unregisterClient(null);
        }

        if (m_loginHandle != null)
        {
            log("Dending logoff request");
            m_consumer.unregisterClient(m_loginHandle);
            m_loginHandle = null;
        }

        if (m_consumer != null)
        {
            log("OMMConsumer destroy...");
            m_consumer.destroy();
            m_consumer = null;
        }

        if (m_session != null)
        {
            log("Session release...");
            m_session.release();
            log("Shutdown completed!!!");
            m_session = null;
        }
    }

    /*
     * Initialize, Setup EventQ if configured, Send Login Request, Start Item
     * request Dispatcher
     */
    void login(boolean bUseEventQ, int itemCount, int decodeLevel)
    {
        log("Creating pool............");
        m_pool = OMMPool.create();

        log("Creating item client............");
        m_itemClient = new ItemClient(this, itemCount, decodeLevel);

        log("Creating OMM Consumer Event Source............");
        m_consumer = (OMMConsumer)m_session.createEventSource(EventSource.OMM_CONSUMER, "Consumer",
                                                              false);

        if (bUseEventQ)
        {
            log("Using EventQ for Response Dispatching............");

            m_responseQ = EventQueue.create(m_session.getName() + "Queue");

            m_responseMessageDispatcher = new ResponseDispatcher(this, m_session.getName(),
                    m_responseQ); // this is a seperate
                                  // thread
            // TODO
            // _responseMessageDispatcher.setPriority(_dispatchPriority);
        }

        // create & send login request
        makeLoginRequest(m_responseQ);

        // start requestDispatcher
        RequestDispatcher itemRequestDispatcher = new RequestDispatcher(this, m_session.getName());
        itemRequestDispatcher.start();

        // log a blank line
        logLine("\n");
    }

    /*
     * Create & Send Login Request
     */
    void makeLoginRequest(EventQueue queue)
    {
        String user = CommandLine.variable("user");
        String appid = CommandLine.variable("application");
        String pos = CommandLine.variable("position");

        log("Sending Login Request for user " + user);

        OMMMsg msg = m_pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.LOGIN);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        msg.setAttribInfo(null, user, RDMUser.NameType.USER_NAME);

        OMMEncoder encoder = m_pool.acquireEncoder();
        encoder.initialize(OMMTypes.MSG, 200);
        encoder.encodeMsgInit(msg, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);
        encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        encoder.encodeElementEntryInit(RDMUser.Attrib.ApplicationId, OMMTypes.ASCII_STRING);
        encoder.encodeString(appid, OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit(RDMUser.Attrib.Position, OMMTypes.ASCII_STRING);
        encoder.encodeString(pos, OMMTypes.ASCII_STRING);
        encoder.encodeAggregateComplete();

        OMMItemIntSpec spec = new OMMItemIntSpec();
        spec.setMsg((OMMMsg)encoder.getEncodedObject());

        m_loginHandle = m_consumer.registerClient(queue, spec, this, null);

        m_pool.releaseEncoder(encoder);
    }

    /*
     * Start Dispatcher to dispatch responses; This is available only if eventQ
     * is used
     */
    void startResponseDispatcher()
    {
        if (m_responseMessageDispatcher == null)
            return;

        m_responseMessageDispatcher.start();
    }

    /*
     * Handle Login events delivered by RFA
     */
    public void processEvent(Event event)
    {
        OMMMsg msg = ((OMMItemEvent)event).getMsg();

        GenericOMMParser.parseMsg(msg, System.out);

        if (msg.isFinal())
        {
            stopTest();
        }
        else if (!m_loggedIn
                && ((msg.getMsgType() == OMMMsg.MsgType.STATUS_RESP) && (msg.has(OMMMsg.HAS_STATE))
                        && (msg.getState().getStreamState() == OMMState.Stream.OPEN) && (msg
                        .getState().getDataState() == OMMState.Data.OK)))
        {
            m_loggedIn = true;
            log("Received Login Success; Ready to send item requests...");
        }
    }

    /*
     * Create & Send Item requests if Logged in; Called by Request Dispatcher
     */
    boolean makeItemRequests()
    {
        if (m_loggedIn == false)
            return false;

        m_itemClient.makeRequests(m_responseQ);
        return true;
    }

    /*
     * Log Messages to console
     */
    void log(String text)
    {
        System.out.print("=>" + m_session.getName() + "-");
        System.out.println(text);
    }

    /*
     * Log Line to console
     */
    void logLine(String text)
    {
        System.out.println(text);
    }
}
// //////////////////////////////////////////////////////////////////////////////
// / End of file
// //////////////////////////////////////////////////////////////////////////////

