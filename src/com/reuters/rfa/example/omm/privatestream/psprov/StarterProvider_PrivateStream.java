package com.reuters.rfa.example.omm.privatestream.psprov;

import java.util.HashMap;
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
import com.reuters.rfa.example.utility.CommandLine;
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
 * public class StarterProvider_PrivateStream is a simple interactive server
 * provider example to demonstrate how to interactively publish Reuters Domain
 * Model data using the Open Message Model.
 * </p>
 * 
 * This application is single-threaded server application. A consumer
 * application, either source distributor or RFA OMMConsumer, must connect to
 * the server to request data.
 * 
 * Currently, this application provides Logins, Service Directories,
 * Dictionaries, and Market Price. It can be easily extended to provide other
 * types of data.
 * 
 * 
 * This provider application also demonstrates how to handle a switch from a
 * standard/public request to a private stream. This is where the provider
 * application does not accept the stream as standard/public and suggests the
 * stream to be private, while it was requested as standard/public. In our
 * example, we use RES-DS.N (Restricted Data Set) market price item request to
 * show this feature. The Closed Redirect status with Private Stream flag set
 * mechanism will be utilized for this. This Redirect message will flow to the
 * client application. The consumer client will then need to re-request the same
 * item, but this time as a private stream.
 * 
 * @see PrivateStrmProvClientSession
 * 
 */
public class StarterProvider_PrivateStream implements Client
{
    FieldDictionary _rwfDictionary;

    private Session _session;
    private final Handle _csListenerIntSpecHandle, _errIntSpecHandle;
    EventQueue _eventQueue;

    boolean _acceptSession;
    HashMap<Handle, PrivateStrmProvClientSession> _clientSessions;

    OMMProvider _provider;
    OMMPool _pool;
    int _updateInterval, _updateRate;
    String _service;

    /**
	 *
	 */
    public StarterProvider_PrivateStream()
    {
        System.out.println("*****************************************************************************");
        System.out.println("*        Begin RFA Java StarterProvider_PrivateStream Program               *");
        System.out.println("*****************************************************************************");
        System.out.println("Initializing Private Stream Provider ...");
        readConfiguration();
        Context.initialize();
        _pool = OMMPool.create();
        _clientSessions = new HashMap<Handle, PrivateStrmProvClientSession>();
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

        System.out.println("Initialization complete, waiting for client sessions");
    }

    private void loadDictionary()
    {
        _rwfDictionary = FieldDictionary.create();
        try
        {
            String rdmDictionary = CommandLine.variable("rdmFieldDictionary");
            String enumType = CommandLine.variable("enumType");
            FieldDictionary.readRDMFieldDictionary(_rwfDictionary, rdmDictionary);
            FieldDictionary.readEnumTypeDef(_rwfDictionary, enumType);
        }
        catch (DictionaryException e)
        {
            System.out.println("Dictionary read error: " + e.getMessage());
            if (e.getCause() != null)
                System.err.println(": " + e.getCause().getMessage());
            System.exit(-1);
        }
    }

    protected void readConfiguration()
    {
        _acceptSession = CommandLine.booleanVariable("acceptSession");
        _updateInterval = CommandLine.intVariable("updateInterval");
        _updateRate = CommandLine.intVariable("updateRate");
        _service = CommandLine.variable("serviceName");

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
                System.out.println("StarterProvider_PrivateStream: unhandled event type: " + event.getType());
                break;
        }
    }

    // Publication error event. In case of any error that occurs along the way
    // of publishing the data to the network,
    // OMMCmdErrorEvent will be sent back to the provider application.
    protected void processOMMCmdErrorEvent(OMMCmdErrorEvent errorEvent)
    {
        System.out.println("Received OMMCmd ERROR EVENT for id: " + errorEvent.getCmdID() + "  "
                + errorEvent.getStatus().getStatusText());
    }

    private void processActiveClientSessionEvent(OMMActiveClientSessionEvent event)
    {
        System.out.println("Receive OMMActiveClientSessionEvent from client position : "
                + event.getClientIPAddress() + "/" + event.getClientHostName() + "/"
                + event.getListenerName());
        if (_acceptSession) // Application defaults to accept all incoming
                            // sessions.
        {
            System.out.println("Pub session accepted.");

            // Accepting a session through the registerClient interface.
            // This will return a client session handle, which indicates to the
            // application which connection this handle is referring.
            Handle handle = event.getClientSessionHandle();
            OMMClientSessionIntSpec intSpec = new OMMClientSessionIntSpec();
            intSpec.setClientSessionHandle(handle);
            PrivateStrmProvClientSession pcs = new PrivateStrmProvClientSession(this, _service);
            Handle clientSessionHandle = _provider.registerClient(_eventQueue, intSpec, pcs, null);
            pcs._clientSessionHandle = clientSessionHandle;
            _clientSessions.put(clientSessionHandle, pcs); // Add the client
                                                           // session's handle
                                                           // to the list.
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

    public void dispatchDemo(int secs)
    {
        long startTime = System.currentTimeMillis();

        while ((System.currentTimeMillis() - startTime) < secs * 1000)
        {
            try
            {
                // Will dispatch from this event queue.
                // if no events, then wait 1000 milliseconds.
                _eventQueue.dispatch(1000);
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
        CommandLine.addOption("acceptSession", "true",
                              "Whether or not to accept the consumer session. Defaults to true");
        CommandLine.addOption("updateInterval", 1, "Update interval.  Defaults to 1 seconds.");
        CommandLine.addOption("updateRate", 2,
                              "Update rate per interval.  Defaults to 2 update /interval.");
        CommandLine.addOption("runTime", 600, "Run time of the application.  Defaults to 600 secs.");
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        StarterProvider_PrivateStream demo = new StarterProvider_PrivateStream();

        // Defaults the run time of the application to 600.
        int secs = CommandLine.intVariable("runTime");
        demo.dispatchDemo(secs);
        demo.cleanup();
    }

}
