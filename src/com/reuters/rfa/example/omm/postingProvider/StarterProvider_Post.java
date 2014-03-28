package com.reuters.rfa.example.omm.postingProvider;

import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.config.ConfigUtil;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.omm.hybrid.OMMMsgReencoder;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.ExampleUtil;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMActiveClientSessionEvent;
import com.reuters.rfa.session.omm.OMMClientSessionIntSpec;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;
import com.reuters.rfa.session.omm.OMMInactiveClientSessionCmd;
import com.reuters.rfa.session.omm.OMMListenerEvent;
import com.reuters.rfa.session.omm.OMMListenerIntSpec;
import com.reuters.rfa.session.omm.OMMProvider;

/**
 * 
 * <p>
 * StarterProvider_Post is a simple interactive server post provider example to
 * demonstrate how to receive post messages, send ack messages and re-send
 * messages received via post. It is also capable of interactively publishing
 * Reuters Domain Model data using the Open Message Model.
 * </p>
 * 
 * The StarterProvider_Post supports posting on both On-Stream(Item Stream) and
 * Off-Stream(Login Stream).
 * 
 * This application is single-threaded server application. A consumer
 * application, either source distributor or RFA OMMConsumer, must connect to
 * the server to request data, post data or post messages.
 * 
 * Currently, this application provides Logins, Service Directories,
 * Dictionaries, and Market Price. It can be easily extended to provide other
 * types of data.
 * 
 */

/*
 * com.reuters.rfa.example.omm.postingProvider.StarterProvider_Post -provSession
 * rsslJava::aProviderLocal02 -sendUpdates false -updateInterval 1 -updateRate 2
 * -supportPost true -ackList 1-2,4 -positiveAck true -setStatusCode false
 * -setStatusText false -setStateUsingOMMState true -ackEncodeAttrib false
 * -ackEncodePayload false -sendPublisherInfo true -forwardPostPayloadMsg true
 */
public class StarterProvider_Post implements Client
{
    OMMProvider _provider;
    OMMPool _pool;
    private Session _session;
    EventQueue _eventQueue;
    private final Handle _csListenerIntSpecHandle;
    private Handle _errIntSpecHandle;

    FieldDictionary _rwfDictionary;

    boolean _acceptSession_ip;
    HashMap<Handle, PostProviderClientSession> _clientSessions;
    PostProviderClientSession _pcs;

    boolean _bSupportPost_ip;

    String _serviceName_ip;

    boolean _bSendPublisherInfo_ip;

    boolean _bForwardPostPayloadMsg_ip;

    HashMap<Integer, Object> _ackList_ip;
    boolean _bAckEncodeAttrib_ip;
    boolean _bAckEncodePayload_ip;
    boolean _bPositiveAck_ip;
    boolean _bSetStatusCode_ip;
    boolean _bSetStatusText_ip;
    boolean _bSetStateUsingOMMState_ip;

    boolean _bSendUpdates_ip;
    int _updateInterval_ip;
    int _updateRate_ip;

    Object _available = new Object();

    String INFO_APPNAME;
    String APPNAME = "JPostProvider";

    /**
     * The main class
     */
    public StarterProvider_Post()
    {
        System.out.println("*****************************************************************************");
        System.out.println("*            Begin RFA Java StarterProvider_Post Program                    *");
        System.out.println("*****************************************************************************");
        System.out.println("Initializing Post Provider ...");
        // readConfiguration();
        initializeLogger();

        Context.initialize();

        _pool = OMMPool.create();
        _clientSessions = new HashMap<Handle, PostProviderClientSession>();
        loadDictionary();

        String sessionName = CommandLine.variable("provSession");
        
        // prior to acquiring the session, update the provider connection
        // to use the request message type (OMMMsg.MsgType.REQUEST) rather 
        // than the deprecated request message types (see OMMMsg.MsgType).
        ConfigUtil.useDeprecatedRequestMsgs(sessionName, false);
        
        _session = Session.acquire(sessionName);
        if (_session == null)
        {
            System.out.println("Could not acquire session.");
            System.exit(1);
        }
        System.out.println("RFA Version: " + Context.getRFAVersionInfo().getProductVersion());

        /**
         * createEventSource is the interface for creating OMMProvider event
         * source.
         */
        _provider = (OMMProvider)_session.createEventSource(EventSource.OMM_PROVIDER,
                                                            "OMMProvider Server");

        /**
         * creates the client
         */
        _eventQueue = EventQueue.create("OMMProvider Server EventQueue");

        /**
         * OMMListenerIntSpec is used to register interest for any incoming
         * sessions specified on the port provided by the configuration.
         */
        OMMListenerIntSpec listenerIntSpec = new OMMListenerIntSpec();
        
        // listener name is the name of the prov connection you want to listen on.
        String connection = CommandLine.variable("listenerName");
        
        listenerIntSpec.setListenerName(connection);
        _csListenerIntSpecHandle = _provider.registerClient(_eventQueue, listenerIntSpec, this,
                                                            null);

        /**
         * OMMErrorIntSpec is used to register interest for any error events
         * during the publishing cycle.
         */
        OMMErrorIntSpec errIntSpec = new OMMErrorIntSpec();
        _errIntSpecHandle = _provider.registerClient(_eventQueue, errIntSpec, this, null);

        INFO_APPNAME = "*Info* " + APPNAME + ": ";
    }

    /**
     * read configuration
     */
    public void initialize()
    {
        // dump command line arguments
        ExampleUtil.dumpCommandArgs();

        // read command args
        _acceptSession_ip = CommandLine.booleanVariable("acceptSession");

        _bSupportPost_ip = CommandLine.booleanVariable("supportPost");

        _bSendUpdates_ip = CommandLine.booleanVariable("sendUpdates");
        _updateInterval_ip = CommandLine.intVariable("updateInterval");
        _updateRate_ip = CommandLine.intVariable("updateRate");

        _serviceName_ip = CommandLine.variable("serviceName");

        _bSendPublisherInfo_ip = CommandLine.booleanVariable("sendPublisherInfo");

        _bForwardPostPayloadMsg_ip = CommandLine.booleanVariable("forwardPostPayloadMsg");

        _bPositiveAck_ip = CommandLine.booleanVariable("positiveAck");
        _bSetStatusCode_ip = CommandLine.booleanVariable("setStatusCode");
        _bSetStatusText_ip = CommandLine.booleanVariable("setStatusText");
        _bSetStateUsingOMMState_ip = CommandLine.booleanVariable("setStateUsingOMMState");
        _bAckEncodeAttrib_ip = CommandLine.booleanVariable("ackEncodeAttrib");
        _bAckEncodePayload_ip = CommandLine.booleanVariable("ackEncodePayload");

        String ackList = CommandLine.variable("ackList");

        _ackList_ip = new HashMap<Integer, Object>();

        StringTokenizer tk = new StringTokenizer(ackList, ",");

        while (tk.hasMoreTokens())
        {
            String part = tk.nextToken().trim();

            String pieces[] = part.split("-");
            if (pieces.length == 2)
            {
                int start = Integer.parseInt(pieces[0]);
                int end = Integer.parseInt(pieces[1]);

                for (int i = start; i <= end; i++)
                {
                    _ackList_ip.put(i, _available);
                }
            }
            else
            {
                int id = Integer.parseInt(pieces[0]);
                _ackList_ip.put(id, _available);
            }
        }

        System.out.println("Initialization complete, waiting for client sessions");
    }

    protected void initializeLogger()
    {
        boolean debug = CommandLine.booleanVariable("debug");
        if (debug)
        {
            // Enable debug logging
            Logger logger = Logger.getLogger("com.reuters.rfa");
            logger.setLevel(Level.FINE);
            Handler[] handlers = logger.getHandlers();

            if (handlers.length == 0)
            {
                Handler handler = new ConsoleHandler();
                handler.setLevel(Level.FINE);
                logger.addHandler(handler);
            }

            for (int index = 0; index < handlers.length; index++)
            {
                handlers[index].setLevel(Level.FINE);
            }
        }
    }

    /**
     * load dictionary
     */
    private void loadDictionary()
    {
        _rwfDictionary = FieldDictionary.create();
        try
        {
            String rdmDictionary = CommandLine.variable("rdmFieldDictionary");
            String enumType = CommandLine.variable("enumType");
            FieldDictionary.readRDMFieldDictionary(_rwfDictionary, rdmDictionary);
            FieldDictionary.readEnumTypeDef(_rwfDictionary, enumType);

            OMMMsgReencoder.initializeDictionary(rdmDictionary, enumType);
            GenericOMMParser.initializeDictionary(rdmDictionary, enumType);

        }
        catch (DictionaryException e)
        {
            System.out.println("Dictionary read error: " + e.getMessage());
            if (e.getCause() != null)
                System.err.println(": " + e.getCause().getMessage());
            System.exit(-1);
        }
    }

    /**
     * Application implemented method to process events delivered by FA
     */
    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_ACTIVE_CLIENT_SESSION_PUB_EVENT: // for listen handle
                processActiveClientSessionEvent((OMMActiveClientSessionEvent)event);
                break;
            case Event.OMM_LISTENER_EVENT:
                OMMListenerEvent listenerEvent = (OMMListenerEvent)event;
                System.out.print("Received OMM LISTENER EVENT: " + listenerEvent.getListenerName());
                System.out.println("  " + listenerEvent.getStatus().toString());
                break;
            case Event.OMM_CMD_ERROR_EVENT: // for cmd error handle
                processOMMCmdErrorEvent((OMMCmdErrorEvent)event);
                break;
            default:
                System.out.println("StarterProvider_Interactive: unhandled event type: " + event.getType());
                break;
        }
    }

    /*
     * Publication error event. In case of any error that occurs along the way
     * of publishing the data to the network, OMMCmdErrorEvent will be sent back
     * to the provider application.
     */
    protected void processOMMCmdErrorEvent(OMMCmdErrorEvent errorEvent)
    {
        System.out.println("Received OMMCmd ERROR EVENT for id: " + errorEvent.getCmdID() + "  "
                + errorEvent.getStatus().getStatusText());
    }

    /*
     * Accept/Reject client session
     */
    private void processActiveClientSessionEvent(OMMActiveClientSessionEvent event)
    {
        String clientName = event.getClientIPAddress() + "/" + event.getClientHostName() + "/"
                + event.getListenerName() + "/" + (_clientSessions.size() + 1);
        System.out.println("Receive OMMActiveClientSessionEvent from client position : "
                + clientName);

        if (_acceptSession_ip) // Application defaults to accept all incoming
                               // sessions.
        {
            System.out.println("Pub session accepted.");

            // Accepting a session through the registerClient interface.
            // This will return a client session handle, which indicates to the
            // application which connection this handle is referring.
            Handle handle = event.getClientSessionHandle();
            OMMClientSessionIntSpec intSpec = new OMMClientSessionIntSpec();
            intSpec.setClientSessionHandle(handle);

            _pcs = new PostProviderClientSession(this, _serviceName_ip, clientName);
            PostProviderClientSession pcs = _pcs;
            Handle clientSessionHandle = _provider.registerClient(_eventQueue, intSpec, pcs, null);
            pcs._clientSessionHandle = clientSessionHandle;
            _clientSessions.put(clientSessionHandle, pcs); // Add the client
                                                           // session's handle
                                                           // to the list.

            OMMErrorIntSpec errIntSpec = new OMMErrorIntSpec();
            _errIntSpecHandle = _provider.registerClient(_eventQueue, errIntSpec, pcs, null);

        }
        else
        // use submit call to REJECT the session.
        {
            System.out.println("Pub session denied.");

            // Rejecting a session through the submit interface.
            OMMInactiveClientSessionCmd inactivecmd = new OMMInactiveClientSessionCmd();
            inactivecmd.setClientSessionHandle(event.getClientSessionHandle());

            _provider.submit(inactivecmd, null);
        }
    }

    /*
     * Dispatch events
     */
    public void dispatchDemo(int secs)
    {
        long startTime = System.currentTimeMillis();

        while ((System.currentTimeMillis() - startTime) < secs * 1000)
        {
            try
            {
                _eventQueue.dispatch(1000); // Will dispatch from this event
                                            // queue. if no events, then wait
                                            // 1000 milliseconds.
            }
            catch (DispatchException de)
            {
                System.out.println("Queue deactivated");
                System.exit(1);
            }
        }
        System.out.println(Context.string());
        System.out.println(secs + " seconds elapsed, " + getClass().toString() + " exiting");
    }

    /*
     * fan-out OMMMsg from a Client Session to all connected clients
     */
    protected void fanoutItemOMMMsg2AllClients(OMMMsg ommMsg, String itemName)
    {
        Iterator<Handle> iter = _clientSessions.keySet().iterator();
        while (iter.hasNext())
        {
            Handle clientSessionHandle = (Handle)(iter.next());
            PostProviderClientSession cs = (PostProviderClientSession)(_clientSessions
                    .get(clientSessionHandle));
            cs.sendOMMMsg2Client(ommMsg, itemName);
        }
    }

    /*
     * Cleanup
     */
    protected synchronized void cleanup()
    {
        System.out.println("Cleaning up resources....");
        _provider.unregisterClient(_errIntSpecHandle);
        _provider.unregisterClient(_csListenerIntSpecHandle);
        _provider.destroy();
        _eventQueue.deactivate();
        _session.release();
        Context.uninitialize();
        System.exit(0);
    }

    static void addCommandLineOptions()
    {
        CommandLine.addOption("debug", false, "enable debug tracing");
        CommandLine.addOption("provSession", "myNamespace::provSession",
                              "Provider session.  Defaults to myNamespace::provSession");
        CommandLine.addOption("listenerName", "",
                              "Unique name that specifies a connection to listen.  Defaults to \"\" ");
        CommandLine.addOption("serviceName", "DIRECT_FEED",
                              "Service name for the SrcDirectory response.  Defaults to DIRECT_FEED");
        CommandLine.addOption("rdmFieldDictionary", "/var/triarch/RDMFieldDictionary",
                              "RDMField dictionary name and location.  Defaults to /var/triarch/RDMFieldDictionary");
        CommandLine.addOption("enumType", "/var/triarch/enumtype.def",
                              "RDMEnum dictionary name and location.  Defaults to /var/triarch/enumtype.def");

        CommandLine.addOption("runTime", 600,
                              "Run time of the application.  Defaults to 600 secs.");

        CommandLine.addOption("acceptSession", "true",
                              "Whether or not to accept the consumer session. Defaults to true");

        CommandLine.addOption("sendUpdates", false, "Send item updates");
        CommandLine.addOption("updateInterval", 1, "Update interval.  Defaults to 1 seconds.");
        CommandLine.addOption("updateRate", 2,
                              "Update rate per interval.  Defaults to 2 update /interval.");

        CommandLine.addOption("supportPost", true, "Support OMM Post feature");

        CommandLine.addOption("ackList", "1-1000,1500",
                              "Comma separated list of post ids requiring ack msgs; Ranges also supported");
        CommandLine.addOption("positiveAck", true, "Send positive ack");
        CommandLine.addOption("setStatusCode", false,
                              "Set Status code in the ack; Not required for positive ack");
        CommandLine.addOption("setStatusText", false,
                              "Set Status text in the ack; Not required for positive ack");
        CommandLine.addOption("setStateUsingOMMState", true,
                              "Set Ack Status indirectly on the OMMMsg using OMMState");
        CommandLine.addOption("ackEncodeAttrib", false, "Encode Attrib data in the ack Message");
        CommandLine.addOption("ackEncodePayload", false, "Encode Payload data in the ack Message");

        CommandLine.addOption("sendPublisherInfo", true,
                              "Send Publisher Info on Refresh/Update/Status messages");
        CommandLine.addOption("forwardPostPayloadMsg", true,
                              "Send post payload OMMMsg to all consumer applications; Only item stream are supported");
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        StarterProvider_Post demo = new StarterProvider_Post();
        demo.initialize();

        // Defaults the run time of the application to 600.
        int secs = CommandLine.intVariable("runTime");
        demo.dispatchDemo(secs);
        demo.cleanup();
    }
}
