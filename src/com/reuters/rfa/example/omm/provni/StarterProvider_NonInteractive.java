package com.reuters.rfa.example.omm.provni;

import java.net.InetAddress;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;
import com.reuters.rfa.session.omm.OMMProvider;

/**
 * <p>
 * This is a main class to run StarterProvider_NonInteractive application.
 * </p>
 * 
 * <p>
 * StarterProvider_NonInteractive is a simple non-interactive provider
 * example to demonstrate how to non-interactively publish Reuters Domain Model
 * data using the Open Message Model. This application is single-threaded client
 * application. The applications connects and logs in to a source distributor or
 * P2PS POP. A TimerIntSpec is used to wake up the dispatch thread so it can
 * send updates.
 * 
 * Currently, the StarterProvider_NonInteractive application provides Service
 * Directories and Market Price. It can be easily extended to provide other
 * message model types.
 * </p>
 * 
 */
public class StarterProvider_NonInteractive
{
    Session _session;
    private final Handle _errIntSpecHandle;
    final EventQueue _eventQueue;

    final OMMProvider _provider;
    final OMMPool _pool;
    LoginClient _loginClient;
    DataProvider _dataProvider;

    /**
	 *
	 */
    public StarterProvider_NonInteractive()
    {
        System.out.println("*****************************************************************************");
        System.out.println("*          Begin RFA Java StarterProvider_NonInteractive Program               *");
        System.out.println("*****************************************************************************");
        System.out.println("Initializing StarterProvider_NonInteractive ...");
        readConfiguration();
        Context.initialize();
        _pool = OMMPool.create();

        createSession();
        System.out.println("RFA Version: " + Context.getRFAVersionInfo().getProductVersion());

        /**
         * createEventSource is the interface for creating OMMProvider event
         * source.
         */
        _provider = (OMMProvider)_session
                .createEventSource(EventSource.OMM_PROVIDER, "OMMProvider");

        _loginClient = new LoginClient(this);

        /**
         * creates the client
         */
        _dataProvider = new DataProvider(this);
        _eventQueue = EventQueue.create("OMMProvider EventQueue");

        // Send login request
        // Application must send login request first
        _loginClient.sendRequest();

        /**
         * OMMErrorIntSpec is used to register interest for any error events
         * during the publishing cycle.
         */
        OMMErrorIntSpec errIntSpec = new OMMErrorIntSpec();
        _errIntSpecHandle = _provider.registerClient(_eventQueue, errIntSpec, _loginClient, null);

        System.out.println("Initialization complete, waiting for login response");
    }

    protected void readConfiguration()
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

    protected void createSession()
    {
        String sessionName = CommandLine.variable("provSession");

        /**
         * Acquire the RFA session.
         */
        _session = Session.acquire(sessionName);

        if (_session == null)
        {
            System.out.println("Could not acquire session.");
            System.exit(1);
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
        _dataProvider.cleanup();
        _provider.unregisterClient(_errIntSpecHandle);
        // unregister login
        if (_loginClient != null)
            _loginClient.closeRequest();
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
        CommandLine.addOption("itemName", "TRI.N", "List of items to open separated by ','.");
        CommandLine.addOption("serviceName", "DIRECT_FEED",
                              "Service name for the SrcDirectory response.  Defaults to DIRECT_FEED");
        
        CommandLine.addOption("updateInterval", 2, "Update interval.  Defaults to 2 seconds.");
        CommandLine.addOption("updateRate", 2, "Update rate per interval.  Defaults to 2/interval.");
        String username = "guest";
        try
        {
            username = System.getProperty("user.name");
        }
        catch (Exception e)
        {
        }
        CommandLine.addOption("user", username, "DACS username for login");
        String defaultPosition = "1.1.1.1/net";
        try
        {
            defaultPosition = InetAddress.getLocalHost().getHostAddress() + "/"
                    + InetAddress.getLocalHost().getHostName();
        }
        catch (Exception e)
        {
        }
        CommandLine.addOption("position", defaultPosition, "DACS position for login");
        CommandLine.addOption("application", "256", "DACS application ID for login");
        CommandLine.addOption("runTime", 600, "Run time of the application.  Defaults to 600 secs.");
        
        CommandLine.addOption("sendStatus", "false", "Send Status Messages.");
        CommandLine.addOption("serviceInfoOnUpdates", "", "ServiceID or ServiceName for updates e.g: 1, DIRECT_FEED.");
        
        CommandLine.addOption("refreshPE","", "Comma separated PEs for refresh messages.e.g.6566,451");
        CommandLine.addOption("updatePE","", "Comma separated PEs for update messages.e.g.6566,451");
        CommandLine.addOption("statusPE","", "Comma separated PEs for status messages.e.g.6566,451");
        CommandLine.addOption("dacsServiceId",1, "The serviceId to be used with PEs to generate DACS locks");
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        StarterProvider_NonInteractive demo = new StarterProvider_NonInteractive();

        // Defaults the run time of the application to 600.
        int secs = CommandLine.intVariable("runTime");
        demo.dispatchDemo(secs);
        demo.cleanup();
    }

    public void processLogin(boolean loggedIn)
    {
        if (loggedIn)
        {
            System.out.println("Login successful...");
            _dataProvider.processLoginSuccessful();
        }
        else
        // Failure
        {
            System.out.println("Login is suspect.  Stop publishing until relogin successful.");
            _dataProvider.processLoginSuspect();
        }
    }

}
