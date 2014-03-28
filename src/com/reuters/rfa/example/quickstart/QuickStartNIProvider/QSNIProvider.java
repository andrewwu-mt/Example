package com.reuters.rfa.example.quickstart.QuickStartNIProvider;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMProvider;

// This is a main class to run QuickStartNIProvider application.
// The purpose of this application is to demonstrate an RFA OMM Non Interactive
// Provider connecting to ADH, logging and publishing data periodically. 
// 
// The program creates an instance of this application and invokes the following steps:
// 1. Startup and initialization (method init())
// 2. Login (method login())
// 3. dispatch events (method dispatchDemo())
// 5. Shutdown and cleanup (method cleanup) This method is called when error is detected or
//  the run time expired.
//
// The application keeps the following members:
// Session _session - a session serving this application
// EventQueue _eventQueue - an event queue created by this application
// 							to use for passing events from RFA to application
// OMMProvider _provider - an instance of event source representing this application
//							in the session
// LoginClient _loginClient - an instance of client class responsible for
// 							handling the login messages
// DataProvider _dataProvider - an instance of client class responsible for
// 							publishing item data
// OMMPool _pool - a memory pool, managed by RFA, allocated by application,
// 							to use for application created message objects
// OMMEncoder _encoder - an instance of encoder allocated by the application
// from the _pool. Application uses this encoder to
// encode messages. An encoder does not have to be 
// a member of the application, instead, can be used as 
// a local variable. The recommended way is to have 
// encoder as a member.

public class QSNIProvider
{
	Session _session;
	EventQueue _eventQueue;
	OMMProvider _provider;
    LoginClient _loginClient;
    DataProvider _dataProvider;

    OMMPool _pool;
    protected OMMEncoder _encoder;

	public QSNIProvider()
	{
		System.out.println ("Initializing OMM ProviderNI Demo ");
	}

    // This method is responsible for initialization
    // As shown in QuickStartGuide, the initialization includes the following steps:
    // 1. Initialize context
    // 2. Create event queue
    // 3. Initialize logger
    // 4. Acquire session
    // 5. Create event source
    // 6. Initialize Data provider Client
    // It also instantiates application specific objects: memory pool, encoder.
    public void init()
    {
    	// 1. Initialize context
        Context.initialize();
        
        // 2. Create an Event Queue
		_eventQueue = EventQueue.create("OMMNonInteractiveProvider EventQueue");
        
        // 3. Initialize logger
        // The application utilizes logger from RFA. User sets the level 
        // to the desired value. The default is "Info".
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

        // 4. Acquire a Session
        _session = Session.acquire( "myNamespace::NIProvSession" );
        if ( _session == null )
        {
            System.out.println( "Could not acquire session." );
            Context.uninitialize();
            System.exit(1);
        }

        // 5. Create an OMMConsumer event source
		_provider = (OMMProvider) _session.createEventSource(EventSource.OMM_PROVIDER, "OMMProvider");

		// 6. Initialize Data provider Client
		_dataProvider = new DataProvider(this);
		String [] items = {"TRI.N", "MSFT.O"}; // arbitrary set items to be published
		_dataProvider.init("DIRECT_FEED", items); // arbitrary set service name
		
        //Create a OMMPool.
        _pool = OMMPool.create();

        //Create an OMMEncoder
        _encoder = _pool.acquireEncoder();

        System.out.println ("Initialization complete, waiting for login response");
    }
    
    // This method utilizes the LoginClient class to send login request 
    public void login()
    {
        //Initialize client for login domain.
        _loginClient = new LoginClient(this);

    	//Send login request
    	_loginClient.sendRequest();
    }

    // This method is called by _loginClient upon receiving login response.
    public void processLogin(boolean loggedIn)
    {
        if (loggedIn )
        {
            System.out.println("Login successful");
            _dataProvider.processLoginSuccessful();
        }
        else //Failure
        {
            System.out.println("Login is suspect.  Stop publishing until relogin successful.");
            _dataProvider.processLoginSuspect();
        }
    }

	// This method dispatches events
    public void dispatchDemo()
	{
    	// The run time is set arbitrary to 120 seconds
    	int runTime = 120;
        // save start time to measure run time
        long startTime = System.currentTimeMillis();

		while ((System.currentTimeMillis() - startTime) < runTime * 1000)
		{
			try
			{
				_eventQueue.dispatch(1000);  // Will dispatch from this event queue.  if no events, then wait 1000 milliseconds.
			}
			catch (DispatchException de)
			{
				System.out.println("Queue deactivated");
				System.exit(1);
			}
		}
		System.out.println(Context.string());
		System.out.println( runTime + " seconds elapsed, " + getClass().toString() + " exiting");
	}

    // This method cleans up resources when the application exits
    // As shown in QuickStartGuide, the shutdown and cleanup includes the following steps:
    // 1. Cleanup Data provider Client
    // 2. Unregister login
    // 3. Destroy event source
    // 4. Deactivate event queue
    // 5. Destroy event queue
    // 6. Release session
    // 7. Uninitialize context
	protected synchronized void cleanup()
	{
		System.out.println("Cleaning up resources");
		
	    // 1. Cleanup Data provider Client
		_dataProvider.cleanup();
		
	    // 2. Unregister login
        if (_loginClient != null)
            _loginClient.closeRequest();
        
        // 3. Destroy event source
		_provider.destroy();
		
	    // 4. Deactivate event queue
		_eventQueue.deactivate();
		
	    // 5. Destroy event queue
		_eventQueue.destroy();
		
	    // 6. Release session
		_session.release();
		
	    // 7. Uninitialize context
		Context.uninitialize();
		
		System.exit(0);
	}

    public OMMEncoder getEncoder()
    {
        return _encoder;
    }

    public OMMPool getPool()
    {
        return _pool;
    }

    // This is a main method of the QuickStartNIProvider application.
    // Refer to QuickStartGuide for the RFA OMM Non-Interactive Provider
    // application life cycle.
    // The steps shown in the guide are as follows:
    // Startup and initialization
    // Login
    // Dispatch events
    // Shutdown and cleanup
	public static void main(String argv[])
	{
    	// Create a demo instance
		QSNIProvider demo = new QSNIProvider();

    	// Startup and initialization
        demo.init();
        
        // Login
        demo.login();
        
        //Dispatch events
		demo.dispatchDemo();
        
        // Shutdown and cleanup
		demo.cleanup();
	}
}
