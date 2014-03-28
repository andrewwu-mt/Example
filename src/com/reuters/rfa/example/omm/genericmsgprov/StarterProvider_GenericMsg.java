package com.reuters.rfa.example.omm.genericmsgprov;

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
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMActiveClientSessionEvent;
import com.reuters.rfa.session.omm.OMMClientSessionIntSpec;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;
import com.reuters.rfa.session.omm.OMMListenerEvent;
import com.reuters.rfa.session.omm.OMMListenerIntSpec;
import com.reuters.rfa.session.omm.OMMProvider;

/**
 * 
 * <p>
 * StarterProvider_GenericMsg is a simple interactive server provider example to
 * demonstrate how to interactively publish and receive generic messages using
 * the Open Message Model.
 * </p>
 * 
 * This application is single-threaded server application.
 * OMMGenericMsgConsumerDemo must connect to this server to request data.
 * 
 * Currently, this application provides a Login, Directory, and generic messages
 * on an items stream. It also receives and process generic messages from the
 * consuming application. It can be easily extended to provide other types of
 * data.
 * 
 */
public class StarterProvider_GenericMsg implements Client
{
    private Session _session;
    private final Handle _csListenerIntSpecHandle, _errIntSpecHandle;
    EventQueue _eventQueue;
    boolean _acceptSession;
    Handle _clientSessionHandle = null;
    GenericMsgProviderClientSession _clientSession = null;
    OMMProvider _provider;
    OMMPool _pool;
    int _submitInterval;
    String _service = "DIRECT_FEED";

    /**
	 *
	 */
    public StarterProvider_GenericMsg()
    {
        System.out.println("*********************************************************************");
        System.out.println("*        Begin RFA Java StarterProvider_GenericMsg Program          *");
        System.out.println("*********************************************************************");

        readConfiguration();
        Context.initialize();
        _pool = OMMPool.create();

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

    protected void readConfiguration()
    {
        _submitInterval = CommandLine.intVariable("submitInterval");

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
                System.out.println("StarterProvider_GenericMsg: unhandled event type: "
                        + event.getType());
                break;
        }
    }

    // Publication error event. In case of any error that occurs along the way of publishing
    // the data to the network, OMMCmdErrorEvent will be sent back to the provider application.
    protected void processOMMCmdErrorEvent(OMMCmdErrorEvent errorEvent)
    {
        System.out.println("Received OMMCmd ERROR EVENT for id: " + errorEvent.getCmdID() + "  "
                + errorEvent.getStatus().getStatusText());
    }

    private void processActiveClientSessionEvent(OMMActiveClientSessionEvent event)
    {
        System.out.println("Receive OMMActiveClientSessionEvent from client position : "
                + event.getClientIPAddress() + "/" + event.getClientHostName());

        System.out.println("Pub session accepted.");

        // Accepting a session through the registerClient interface.
        // This will return a client session handle, which indicates to the
        // application which connection this handle is referring.
        Handle handle = event.getClientSessionHandle();
        OMMClientSessionIntSpec intSpec = new OMMClientSessionIntSpec();
        intSpec.setClientSessionHandle(handle);
        _clientSession = new GenericMsgProviderClientSession(this, _service);
        _clientSessionHandle = _provider.registerClient(_eventQueue, intSpec, _clientSession, null);
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
        CommandLine.addOption("provSession", "RSSLNamespace::localProviderSession",
                              "Provider session.  Defaults to RSSLNamespace::localProviderSession");
        CommandLine.addOption("submitInterval", 1, "Update interval.  Defaults to 1 seconds.");
        CommandLine.addOption("runTime", 600,
                              "Run time of the application.  Defaults to 600 secs.");
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        StarterProvider_GenericMsg demo = new StarterProvider_GenericMsg();

        // Defaults the run time of the application to 600.
        int secs = CommandLine.intVariable("runTime");
        demo.dispatchDemo(secs);
        demo.cleanup();
    }

}
