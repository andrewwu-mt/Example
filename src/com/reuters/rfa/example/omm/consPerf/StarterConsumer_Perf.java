package com.reuters.rfa.example.omm.consPerf;

import java.net.InetAddress;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.example.utility.CommandLine;

/**
 * This is a main class to run StarterConsumer_Perf application.</p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Initialize and set command line options
 * <li>Create a {@link com.reuters.rfa.session.Session Session} and an
 * {@link com.reuters.rfa.common.EventQueue EventQueue}
 * <li>Create an {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer}
 * event source, an {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder} and an
 * {@link com.reuters.rfa.omm.OMMPool OMMPool}.
 * <li>Create RequestManager and ResponseManager to handle Login / item request
 * and response messages.
 * <li>Dispatcher which dispatches events from an response EventQueue, if there
 * is one.
 * <li>Cleanup a Session
 * </ul>
 * 
 * @see RequestManager
 * @see ResponseManager
 * 
 *      The following are the currently available commandline configuration
 *      parameters. -runTime Run time of the test application. Defaults to 600
 *      secs. -session Consumer session. Defaults to myNamespace::mySession
 *      -serviceName Service name to request. Defaults to DIRECT_FEED -itemName
 *      List of items to open separated by ','. Defaults to TRI.N -nullEQ Test
 *      with null EventQueue. Defaults to false -rdmFieldDictionary RDMField
 *      dictionary name and location. Defaults to /var/rdm/RDMFieldDictionary
 *      -enumType RDMEnum dictionary name and location. Defaults to
 *      /var/rdm/enumtype.def -displayInterval Throughput display rate (in
 *      seconds). Defaults to 5 -printData Flag to print update response
 *      -printStatistics Flag to print statistics
 */

public class StarterConsumer_Perf
{
    private static final String _className = "OMMSimpleConsumerPerf";

    Dispatcher _dispatcher = null;

    RequestManager _reqMgr;
    ResponseManager _responseMgr;

    public StarterConsumer_Perf()
    {
        System.out.println("*****************************************************************************");
        System.out.println("*          Begin RFA Java StarterConsumer_Perf Program                      *");
        System.out.println("*****************************************************************************");
    }

    /**
     * Initialize OMM Consumer application and clients
     */
    public void init()
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
                handlers[index].setLevel(Level.FINE);
        }
        // Read configuration and initialize the context
        Context.initialize();
        _responseMgr = new ResponseManager();
        _reqMgr = new RequestManager();

        _responseMgr.init(_reqMgr);
        _reqMgr.init(_responseMgr);

    }

    public void waitForLoginSuccess()
    {
        while (!_responseMgr.isReady())
        {
            System.out.println("Waiting for login response...");
            try
            {
                Thread.sleep(5000);
            }
            catch (InterruptedException ie)
            {
                // sleep interrupted
                // ie.printStackTrace();
            }
        }
    }

    public void run()
    {
        _dispatcher = new Dispatcher(_reqMgr.getResponseQueue());

        // start dispatcher
        _dispatcher.setName(_className + " Controller");
        _dispatcher.start();

        // request login, wait for successful login and request items
        _reqMgr.registerTimer();
        _reqMgr.requestLogin();
        if (!_responseMgr.isReady())
            waitForLoginSuccess();
        _reqMgr.requestItems();

        // stop the test (killing item request and response processing thread)
        // after specified runtime
        int secs = CommandLine.intVariable("runTime");
        long remainingTime = secs * 1000;
        try
        {
            // warning: this may be starved if the other
            // Threads have higher priority
            Thread.sleep(remainingTime);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
            System.exit(1);
        }
        System.out.println("Exiting " + _className + " ...");
        _dispatcher.terminate(_reqMgr);
    }

    /**
     * Initialize and set the default for the command line options
     */
    static void addCommandLineOptions()
    {
        CommandLine.addOption("debug", false, "enable debug tracing");
        CommandLine.addOption("session", "myNamespace::mySession", "Session name to use");
        CommandLine.addOption("serviceName", "DIRECT_FEED", "service to request");
        CommandLine.addOption("itemName", "TRI.N", "List of items to open separated by ','.");
        CommandLine.addOption("rdmFieldDictionary", "/var/triarch/RDMFieldDictionary",
                              "RDMFieldDictionary filename");
        CommandLine.addOption("enumType", "/var/triarch/enumtype.def", "enumtype.def filename");
        CommandLine.addOption("runTime", 600,
                              "How long application should run before exiting (in seconds)");
        CommandLine.addOption("displayInterval", 5, "Throughput display rate (in seconds)");
        CommandLine.addOption("printData", "true", "Display item updates?");
        CommandLine.addOption("printStatistics", "true", "Display statistics at displayInterval?");
        CommandLine.addOption("nullEQ", "false",
                              "test with null EventQueue. In case of null queue, event processing is done in RFA's session layer thread.");
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
    }

    public static void main(String argv[])
    {
        Thread currThread = Thread.currentThread();
        currThread.setName(_className + " Application");

        addCommandLineOptions();
        CommandLine.setArguments(argv);

        StarterConsumer_Perf demo = new StarterConsumer_Perf();
        demo.init();
        demo.run();
    }
}
