package com.reuters.rfa.example.quickstart.QuickStartProvider;

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
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMActiveClientSessionEvent;
import com.reuters.rfa.session.omm.OMMClientSessionIntSpec;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;
import com.reuters.rfa.session.omm.OMMListenerEvent;
import com.reuters.rfa.session.omm.OMMListenerIntSpec;
import com.reuters.rfa.session.omm.OMMProvider;

// This is a main class to run QuickStartProvider application.
// The purpose of this application is to demonstrate an RFA OMM Provider
// accepting connection from OMM Consumer, processing login request, directory
// request and item requests.
// This application is single-threaded server application.  A consumer application,
// either source distributor or RFA OMMConsumer, must connect to the server to 
// request data.
//
// After the initialization the application will periodically dispatch
// events from the event queue. 
//
// The program follows the sequence:
// 1. Creates an instance of this application and does
//    startup and initialization (method QSProviderDemo(), constructor)
// 2. dispatch and process events (method dispatchDemo())
// 3. Shutdown and cleanup (method cleanup) This method is called when error is detected or
//    the run time expired.
//
// The application keeps the following members:
// Session _session - 			a session serving this application
// EventQueue _eventQueue - 	an event queue created by this application
// 								to use for passing events from RFA to application
// OMMProvider _provider - 		an instance of event source representing this application
//								in the session
// Handle _csListenerIntSpecHandle - a handle returned by RFA on registering the provider
//								to the listening interest. Application uses this handle to 
//								identify this client
// Handle _errIntSpecHandle - 	a handle returned by RFA on registering the client
//								to the error interest. Application uses this handle to 
//								identify this client
// FieldDictionary _rwfDictionary - dictionaries
// HashMap _clientSessions		- a collection to hold accepted client sessions 
// OMMPool _pool - 				a memory pool, managed by RFA, allocated by application,
//								to use for application created message objects
// String _serviceName - 		a string representing name of a service that this provider 
//								application supports. The name is hard coded to "DIRECT_FEED".
public class QSProvider implements Client
{
	private Session _session;
	EventQueue _eventQueue;
	OMMProvider _provider;
	FieldDictionary _rwfDictionary;
	private final Handle _csListenerIntSpecHandle;
	private final Handle _errIntSpecHandle;

	HashMap<Handle, ProviderClientSession> _clientSessions = new HashMap<Handle, ProviderClientSession>();
    OMMPool _pool;
	String _serviceName = "DIRECT_FEED";
	
    // This is a constructor that includes initialization
    // As shown in QuickStartGuide, the initialization includes the following steps:
    // 1. Initialize context
    // 2. Create event queue
    // 3. Initialize logger
    // 4. Acquire session
    // 5. Create event source
    // 6. Load dictionaries
	// 7. Register for events
    // It also instantiates application specific objects: memory pool, service name.
	public QSProvider()
	{
        System.out.println("*****************************************************************************");
        System.out.println("*          Begin RFA Java Quick Start Provider Demo Program                 *");
        System.out.println("*****************************************************************************");
		System.out.println ("Initializing OMM Provider Demo ");

	    // 1. Initialize context
		Context.initialize();

	    // 2. Create event queue
		_eventQueue = EventQueue.create("OMMProvider Server EventQueue");

	    // 3. Initialize logger
		setLogger();
		
	    // 4. Acquire session
		ConfigUtil.useDeprecatedRequestMsgs("myNamespace::provSession", false);
        _session = Session.acquire("myNamespace::provSession");
        if (_session == null)
        {
        	System.out.println("Could not acquire session.");
        	System.exit(1);
        }

	    // 5. Create event source
		_provider = (OMMProvider) _session.createEventSource(EventSource.OMM_PROVIDER, "OMMProvider Server");

	    // 6. Load dictionaries
		loadDictionary();

		// 7. Register for events
		// 7a. Register for listener events
		// OMMListenerIntSpec is used to register interest for any incoming sessions specified on the 
		// port provided by the configuration.
		OMMListenerIntSpec listenerIntSpec = new OMMListenerIntSpec();
		listenerIntSpec.setListenerName("");
		_csListenerIntSpecHandle = _provider.registerClient(_eventQueue, listenerIntSpec, this, null);

		// 7b. Register for error events
		// OMMErrorIntSpec is used to register interest for any error events during the publishing cycle.
		OMMErrorIntSpec errIntSpec = new OMMErrorIntSpec();
		_errIntSpecHandle = _provider.registerClient(_eventQueue, errIntSpec, this, null);

        //Create a OMMPool.
		_pool = OMMPool.create();

		System.out.println ("Initialization complete, waiting for client sessions");
	}

    // This is a private method that loads directories.
    private void loadDictionary()
    {
        _rwfDictionary = FieldDictionary.create();
    	String fieldDictionaryFilename = "../etc/RDM/RDMFieldDictionary";
    	String enumDictionaryFilename = "../etc/RDM/enumtype.def";
		try 
		{
			FieldDictionary.readRDMFieldDictionary(_rwfDictionary, fieldDictionaryFilename);
            FieldDictionary.readEnumTypeDef(_rwfDictionary, enumDictionaryFilename);
		} 
		catch (DictionaryException e) 
		{
			System.out.println("Dictionary read error: " + e.getMessage());
            if(e.getCause() != null)
                System.err.println(": " + e.getCause().getMessage());
			System.exit(-1);
		}
    }

    // This is a private method that initializes logger.
    // The application utilizes logger from RFA. User sets the level 
    // to the desired value. The default is "Info".
	private void setLogger()
	{
		//Enable debug logging
		Logger logger = Logger.getLogger("com.reuters.rfa");
		Level level = Level.INFO;
		logger.setLevel(level);
		Handler[] handlers = logger.getHandlers();

		if(handlers.length == 0) 
		{
			Handler handler = new ConsoleHandler();
			handler.setLevel(level); 
			logger.addHandler(handler);            	
		}

		for( int index = 0; index < handlers.length; index++ )
		{
			handlers[index].setLevel(level);
		}
	}

    // This is a Client method. When an event for this client is dispatched,
    // this method gets called.
	public void processEvent(Event event)
	{
	    switch (event.getType())
	    {
	    case Event.OMM_ACTIVE_CLIENT_SESSION_PUB_EVENT:
	        processActiveClientSessionEvent((OMMActiveClientSessionEvent)event);
	        break;
	    case Event.OMM_LISTENER_EVENT:
	        OMMListenerEvent listenerEvent = (OMMListenerEvent)event;
	        System.out.print("Received OMM LISTENER EVENT: " + listenerEvent.getListenerName());
	        System.out.println("  " + listenerEvent.getStatus().toString());
	        break;
        case Event.OMM_CMD_ERROR_EVENT :
            processOMMCmdErrorEvent((OMMCmdErrorEvent) event);
            break;
	    default :
	        System.out.println("OMMProviderDemo: unhandled event type: " + event.getType());
	    break;
	    }
	}

    // This is a private method that handles event of the type OMM_CMD_ERROR_EVENT.
	// In case of any error that occurs along the way of publishing the data to the network,
    // OMMCmdErrorEvent will be sent back to the provider application.
    private void processOMMCmdErrorEvent(OMMCmdErrorEvent errorEvent)
    {
        System.out.println(
            "Received OMMCmd ERROR EVENT for id: "
                + errorEvent.getCmdID() + "  "
                + errorEvent.getStatus().getStatusText());
    }

    // This is a private method that handles event of the type OMM_ACTIVE_CLIENT_SESSION_PUB_EVENT.
	private void processActiveClientSessionEvent(OMMActiveClientSessionEvent event)
	{
		System.out.println("Receive OMMActiveClientSessionEvent from client position : " +
				event.getClientIPAddress()+"/"+event.getClientHostName() + "/" + event.getListenerName());

		System.out.println("Pub session accepted.");

		// Accepting a session through the registerClient interface.
		// This will return a client session handle.
		Handle handle = event.getClientSessionHandle();
		OMMClientSessionIntSpec intSpec = new OMMClientSessionIntSpec();
		intSpec.setClientSessionHandle(handle);
		ProviderClientSession pcs = new ProviderClientSession(this);
		Handle clientSessionHandle = _provider.registerClient(_eventQueue, intSpec, pcs, null);
		pcs._clientSessionHandle = clientSessionHandle;  
		// Add the client session's handle to the list.
		_clientSessions.put(clientSessionHandle, pcs); 
	}

	// This method dispatches events
	public void dispatchDemo()
	{
		// Defaults the run time of the application to 120 seconds.
		int secs = 120;
		long startTime = System.currentTimeMillis();

		while ((System.currentTimeMillis() - startTime) < secs * 1000)
		{
			try
			{
				// Will dispatch events from event queue.  
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
		System.out.println( secs + " seconds elapsed, " + getClass().toString() + " exiting");
	}

    // This method cleans up resources when the application exits
    // As shown in QuickStartGuide, the shutdown and cleanup includes the following steps:
    // 1. Deactivate event queue
    // 2. Unregister event interest
    // 3. Destroy event source
    // 4. Release session
    // 5. Destroy event queue
    // 6. Uninitialize context
	protected synchronized void cleanup()
	{
		System.out.println("Cleaning up resources");

		// 1. Deactivate event queue
		_eventQueue.deactivate();

	    // 2. Unregister event interest
		_provider.unregisterClient(_errIntSpecHandle);
		_provider.unregisterClient(_csListenerIntSpecHandle);

	    // 3. Destroy event source
		_provider.destroy();

	    // 4. Release session
		_session.release();

	    // 5. Destroy event queue
		_eventQueue.destroy();

	    // 6. Uninitialize context
		Context.uninitialize();

		System.exit(0);
	}
	
	protected OMMPool getPool()
	{
		return _pool;
	}

	protected String getServiceName()
	{
		return _serviceName;
	}

	// This is a main method of the QuickStartProvider application.
    // Refer to QuickStartGuide for the RFA OMM Provider application life cycle.
    // The steps shown in the guide are as follows:
    // Startup and initialization
    // Dispatch and process events
    // Shutdown and cleanup
	public static void main(String argv[])
	{
    	// Create a demo instance
    	// Startup and initialization is done in constructor
		QSProvider demo = new QSProvider();

		// serviceName can be passed via command line.
		if ((argv != null) && (argv.length > 0))
        {
            demo._serviceName = argv[0];
        }
		
	    // Dispatch and process events
		// Events are processed in method processEvent(Event e)
		// when received
		demo.dispatchDemo();

	    // Shutdown and cleanup
		demo.cleanup();
	}
}
