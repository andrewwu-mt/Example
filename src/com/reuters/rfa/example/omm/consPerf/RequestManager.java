package com.reuters.rfa.example.omm.consPerf;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.TimerIntSpec;
import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * <p>
 * Class to handle all requests.
 * </p>
 * This class is responsible for the following methods:
 * <ul>
 * <li>Create a {@link com.reuters.rfa.session.Session Session} and optionally
 * an {@link com.reuters.rfa.common.EventQueue EventQueue} for response
 * messages.
 * <li>Create an {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer}
 * event source, an {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder} and an
 * {@link com.reuters.rfa.omm.OMMPool OMMPool}.
 * <li>Encode and register streaming request message for Login using OMM message
 * to RFA.
 * <li>Encode and register streaming request message for multiple items in
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_PRICE MARKET_PRICE} domain.
 * <li>Register timer for display statistics at displayInterval.
 * <li>Close the item requests.
 * <li>Unregistered login, logout from server.
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder},
 * {@link com.reuters.rfa.omm.OMMPool OMMPool},
 * {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer} and
 * {@link com.reuters.rfa.example.omm.consPerf.ResponseManager ResponseManager}
 * 
 * @see ResponseManager
 */
public class RequestManager
{
    ResponseManager _responseMgr;
    String _className = "RequestManager";

    // RFA objects
    Session _session;
    EventQueue _eventQueue;
    OMMConsumer _ommConsumer;
    OMMEncoder _encoder;
    OMMPool _pool;
    OMMMsg _requestMessage;
    OMMItemIntSpec _ommItemIntSpec;
    Handle _loginHandle;

    LinkedList<Handle> _itemHandles;
    int _dispInterval;
    boolean _nullEQ;

    public void init(ResponseManager responseMgr)
    {
        this._responseMgr = responseMgr;
        _dispInterval = CommandLine.intVariable("displayInterval");

        // Create a Session
        String sessionName = CommandLine.variable("session");
        _session = Session.acquire(sessionName);
        if (_session == null)
        {
            System.out.println("Could not acquire session.");
            Context.uninitialize();
            System.exit(1);
        }
        System.out.println("RFA Version: " + Context.getRFAVersionInfo().getProductVersion());

        _nullEQ = CommandLine.booleanVariable("nullEQ");

        // Event Queue
        _eventQueue = _nullEQ ? null : EventQueue.create("myEventQueue");

        // Create a OMMPool.
        _pool = OMMPool.create();

        // Create a Request message
        _requestMessage = _pool.acquireMsg();

        // create a reusable item interest spec
        _ommItemIntSpec = new OMMItemIntSpec();

        // Create an OMMEncoder
        _encoder = _pool.acquireEncoder();
        _encoder.initialize(OMMTypes.MSG, 5000);

        // Create an OMMConsumer event source
        // It does not register itself for completion events
        _ommConsumer = (OMMConsumer)_session.createEventSource(EventSource.OMM_CONSUMER,
                                                               "myOMMConsumer", false);

        _itemHandles = new LinkedList<Handle>();
    }

    public void requestLogin()
    {
        _requestMessage.clear();
        _requestMessage.setMsgType(OMMMsg.MsgType.REQUEST);
        _requestMessage.setMsgModelType(RDMMsgTypes.LOGIN);
        _requestMessage.setIndicationFlags(OMMMsg.Indication.REFRESH);
        _requestMessage.setAttribInfo(null, CommandLine.variable("user"),
                                      RDMUser.NameType.USER_NAME);

        _encoder.encodeMsgInit(_requestMessage, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);
        _encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        _encoder.encodeElementEntryInit(RDMUser.Attrib.ApplicationId, OMMTypes.ASCII_STRING);
        _encoder.encodeString(CommandLine.variable("application"), OMMTypes.ASCII_STRING);
        _encoder.encodeElementEntryInit(RDMUser.Attrib.Position, OMMTypes.ASCII_STRING);
        _encoder.encodeString(CommandLine.variable("position"), OMMTypes.ASCII_STRING);
        _encoder.encodeElementEntryInit(RDMUser.Attrib.Role, OMMTypes.UINT);
        _encoder.encodeUInt((long)RDMUser.Role.CONSUMER);
        _encoder.encodeAggregateComplete();

        _ommItemIntSpec.setMsg((OMMMsg)_encoder.getEncodedObject());
        System.out.println(_className + ": Sending login request...");
        _loginHandle = _ommConsumer.registerClient(_nullEQ ? null : _eventQueue, _ommItemIntSpec,
                                                   _responseMgr, null);

        _requestMessage.clear();
    }

    /**
     * Encodes streaming request messages and register them to RFA
     */
    public void requestItems()
    {
        System.out.println(_className + ".requestItems: Requesting item(s)...");
        String serviceName = CommandLine.variable("serviceName");
        String itemNames = CommandLine.variable("itemName");
        // Note: "," is a valid character for RIC name.
        // This application need to be modified if RIC names have ",".
        StringTokenizer st = new StringTokenizer(itemNames, ",");
        LinkedList<String> itemNamesList = new LinkedList<String>();
        while (st.hasMoreTokens())
            itemNamesList.add(st.nextToken().trim());

        Iterator<String> iter = itemNamesList.iterator();

        while (iter.hasNext())
        {
            // Preparing to send item request message
            _requestMessage.clear();
            _requestMessage.setMsgType(OMMMsg.MsgType.REQUEST);
            _requestMessage.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
            _requestMessage.setIndicationFlags(OMMMsg.Indication.REFRESH);
            _requestMessage.setPriority((byte)1, 1);
            String itemName = (String)iter.next();
            System.out.println(_className + ": Subscribing to " + itemName);

            _requestMessage.setAttribInfo(serviceName, itemName, RDMInstrument.NameType.RIC);

            // Set the message into interest spec
            _ommItemIntSpec.setMsg(_requestMessage);
            Handle itemHandle = _ommConsumer.registerClient(_nullEQ ? null : _eventQueue,
                                                            _ommItemIntSpec, _responseMgr, null);
            // _requestMessage.clear();
            _itemHandles.add(itemHandle);
        }
    }

    public void registerTimer()
    {
        TimerIntSpec timerIntSpec = new TimerIntSpec();
        timerIntSpec.setDelay(_dispInterval * 1000);
        timerIntSpec.setRepeating(true);
        // Handle timerHandle = _ommConsumer.registerClient(_nullEQ ? null :
        // _eventQueue, timerIntSpec,_responseMgr, null); // TODO not needed?
        // Converted this line to the next
        _ommConsumer.registerClient(_nullEQ ? null : _eventQueue, timerIntSpec, _responseMgr, null);
    }

    /**
     * Unregisters/unsubscribes all items individually
     */
    public void closeItemRequest()
    {
        Iterator<Handle> iter = _itemHandles.iterator();
        Handle itemHandle = null;
        while (iter.hasNext())
        {
            itemHandle = (Handle)iter.next();
            _ommConsumer.unregisterClient(itemHandle);
        }
        _itemHandles.clear();
    }

    /**
     * Unregisters/unsubscribes login handle
     * 
     */
    public void closeLoginRequest()
    {
        if (_loginHandle != null)
        {
            _ommConsumer.unregisterClient(_loginHandle);
            _loginHandle = null;
        }
    }

    public void cleanup(int val)
    {
        cleanup(val, true);
    }

    public void cleanup(int val, boolean doLoginCleanup)
    {
        System.out.println(Context.string());
        closeItemRequest();

        if (doLoginCleanup)
            closeLoginRequest();

        if (_ommConsumer != null)
            _ommConsumer.destroy();
        if (_eventQueue != null)
            _eventQueue.deactivate();
        if (_session != null)
            _session.release();
        System.out.println(_className + " exiting.");
        if (val != 0)
            System.exit(val);
    }

    public EventQueue getResponseQueue()
    {
        return _eventQueue;
    }
}
