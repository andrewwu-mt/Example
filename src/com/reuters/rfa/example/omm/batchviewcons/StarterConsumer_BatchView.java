package com.reuters.rfa.example.omm.batchviewcons;

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
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMConsumer;

/**
 * <p>
 * This is a main class to run StarterConsumer_BatchView application.
 * </p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Initialize and set command line options
 * <li>Create a {@link com.reuters.rfa.session.Session Session} and an
 * {@link com.reuters.rfa.common.EventQueue EventQueue}
 * <li>Create an {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer}
 * event source, an {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder} and an
 * {@link com.reuters.rfa.omm.OMMPool OMMPool}.
 * <li>Create LoginClient and ItemManager to handle login and batch/view item
 * request and response messages.
 * <li>Dispatch events from an EventQueue
 * <li>Cleanup a Session
 * </ul>
 * 
 * @see BatchViewLoginClient
 * @see BatchViewDirectoryClient
 * @see BatchViewItemManager
 * 
 */
public class StarterConsumer_BatchView
{
    // RFA objects
    protected Session _session;
    protected EventQueue _eventQueue;
    protected OMMConsumer _ommConsumer;
    protected BatchViewLoginClient _loginClient;
    protected BatchViewDirectoryClient _directoryClient;
    protected BatchViewItemManager _itemManager;

    protected OMMEncoder _encoder;
    protected OMMPool _pool;

    private boolean _hasOpenedRequest;
    private final String _className = "StarterConsumer_BatchView";

    public StarterConsumer_BatchView()
    {
        System.out.println("*****************************************************************************");
        System.out.println("*          Begin RFA Java StarterConsumer_BatchView Program                 *");
        System.out.println("*****************************************************************************");
    }

    /**
     * Initialize OMM Consumer application and clients
     * 
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

        Context.initialize();
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

        // Create an Event Queue
        _eventQueue = EventQueue.create("myEventQueue");

        // Create a OMMPool.
        _pool = OMMPool.create();

        // Create an OMMEncoder
        _encoder = _pool.acquireEncoder();
        _encoder.initialize(OMMTypes.MSG, 5000);

        // Initialize client for login domain.
        _loginClient = new BatchViewLoginClient(this);

        // Initialize item manager for item domains
        _itemManager = new BatchViewItemManager(this);

        // Initialize directory client for directory domain.
        _directoryClient = new BatchViewDirectoryClient(this);

        // Create an OMMConsumer event source
        _ommConsumer = (OMMConsumer)_session.createEventSource(EventSource.OMM_CONSUMER,
                                                               "myOMMConsumer", true);

        // Application may choose to down-load the enumtype.def and
        // RWFFldDictionary
        // This example program loads the dictionaries from file only.
        String fieldDictionaryFilename = CommandLine.variable("rdmFieldDictionary");
        String enumDictionaryFilename = CommandLine.variable("enumType");
        try
        {
            GenericOMMParser.initializeDictionary(fieldDictionaryFilename, enumDictionaryFilename);
        }
        catch (DictionaryException ex)
        {
            System.out.println("ERROR: Unable to initialize dictionaries.");
            System.out.println(ex.getMessage());
            if (ex.getCause() != null)
                System.err.println(": " + ex.getCause().getMessage());
            cleanup(-1);
            return;
        }

        // Send login request
        // Application must send login request first
        _loginClient.sendRequest();
    }

    /**
     * Processes the result from Login
     * 
     * @param success the boolean to indicate that the server has accepted
     *            the request or not
     */
    public void processLogin(boolean success)
    {
        if (success)
        {
            System.out.println(_className + " Login successful...");
            // Now we can send the directory request
            if (!_hasOpenedRequest)
            {
                _directoryClient.sendRequest();
                _hasOpenedRequest = true;
            }
        }
        else
        // Failure
        {
            System.out.println(_className + ": Login has been denied / rejected / closed.");
            System.out.println(_className + ": Preparing to clean up and exiting...");
            cleanup(1, false);
        }
    }

    /**
     * Checks if service specified is available. If so, it sends item request
     * using {@link BatchViewItemManager#sendRequest()}
     */
    public void processDirectoryInfo()
    {
        String serviceName = CommandLine.variable("serviceName");

        if (_directoryClient.getServiceList().contains(serviceName))
        {
            _itemManager.sendRequest();
        }
        else
        {
            System.out.println(_className + ": Service '" + serviceName + "' is not available.");
        }
    }

    /**
     * Initialize the timer and dispatch events
     */
    public void run()
    {
        int runTime = CommandLine.intVariable("runTime");
        // save start time to measure run time
        long startTime = System.currentTimeMillis();

        while ((System.currentTimeMillis() - startTime) < runTime * 1000)
            try
            {
                _eventQueue.dispatch(1000);
            }
            catch (DispatchException de)
            {
                System.out.println("EventQueue has been deactivated.");
                return;
            }
        System.out.println(runTime + " seconds elapsed, " + getClass().toString() + " cleaning up...");
    }

    public EventQueue getEventQueue()
    {
        return _eventQueue;
    }

    public OMMConsumer getOMMConsumer()
    {
        return _ommConsumer;
    }

    public OMMEncoder getEncoder()
    {
        return _encoder;
    }

    public OMMPool getPool()
    {
        return _pool;
    }

    public void cleanup(int val)
    {
        cleanup(val, true);
    }

    public void cleanup(int val, boolean doLoginCleanup)
    {
        System.out.println(Context.string());

        // unregister the reissue timer (if applicable)
        _itemManager.unregisterReissueTimer();

        // unregister all items
        if (_itemManager != null)
            _itemManager.closeRequest();

        // unregister login
        // Allow conditional login unregister.
        // loginCleanup flag is set to false if cleanup is called after login
        // failure.
        if (_loginClient != null && doLoginCleanup)
            _loginClient.closeRequest();

        if (_ommConsumer != null)
            _ommConsumer.destroy();

        _eventQueue.deactivate();
        _session.release();
        Context.uninitialize();

        System.out.println(getClass().toString() + " exiting.");
        if (val != 0)
            System.exit(val);
    }

    public Handle getLoginHandle()
    {
        if (_loginClient != null)
        {
            return _loginClient.getHandle();
        }

        return null;
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
        CommandLine.addOption("mmt", "MARKET_PRICE", "Message Model Type");
        CommandLine.addOption("attribInfoInUpdates", false,
                              "Ask provider to send OMMAttribInfo with update and status messages");
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
        CommandLine.addOption("rdmFieldDictionary", "/var/triarch/RDMFieldDictionary",
                              "RDMFieldDictionary filename");
        CommandLine.addOption("enumType", "/var/triarch/enumtype.def", "enumtype.def filename");
        CommandLine.addOption("runTime", 600,
                              "How long application should run before exiting (in seconds)");
        CommandLine.addOption("sendView", true, "Send view request?");
        CommandLine.addOption("viewType", 1,
                              "View type: 1 for field list view, 2 for element list view");
        CommandLine.addOption("viewData", "22",
                              "Comma separated list of fids or element names you want to receive from market data");
        CommandLine.addOption("sendReissue", true, "Send Reissue requests?");
        CommandLine.addOption("reissueInterval", 15, "Interval between Reissues (in seconds)");
        CommandLine.addOption("reissueWithPAR", true,
                              "Reissue with Pause and Resume, alternating each reissue");
        CommandLine.addOption("reissueWithPriority", true,
                              "Reissue with different Priority, each reissue");
        CommandLine.addOption("initialRequestPaused", false,
                              "Initial batch request has PAUSE_REQ indication?");
        CommandLine.addOption("ricFile", "false", "Ric file");
        CommandLine.addOption("maxCount", "-1", "Max number of item to request from ric file");
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        StarterConsumer_BatchView demo = new StarterConsumer_BatchView();
        demo.init();
        demo.run();
        demo.cleanup(0);
    }

}
