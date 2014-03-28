package com.reuters.rfa.example.omm.privatestream.pscons;

import java.net.InetAddress;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.omm.privatestream.common.PSGenericOMMParser;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMConsumer;

/**
 * <p>
 * This is a main class to run StarterConsumer_PrivateStream application.
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
 * <li>Create LoginClient and ItemManager to handle Login / item request and
 * response messages.
 * <li>Dispatch events from an EventQueue
 * <li>Cleanup a Session
 * </ul>
 * 
 * 
 * <b>Please note the we highly recommend that a consumer application using
 * private streams always request a Directory and make private stream requests
 * after the service of interest is up. Otherwise a private stream request may
 * be immediately closed, as it was requested to a disconnected or down
 * service</b>
 * 
 * 
 * This consumer application also demonstrates how to handle a switch from a
 * standard/public request to private stream (Private Stream item-based recall).
 * This is where the provider application does not accept the stream as
 * standard/public and suggests the stream to be private, while it was requested
 * as standard/public. In this example, we use an RES-DS.N (Restricted Data Set)
 * market price item request to show this feature. The Closed Redirect status
 * with Private Stream flag set mechanism will be utilized for this. This
 * Redirect message will flow to the client consumer application. The consumer
 * client (PrivateStrmItemManager) will then need to re-request the same item
 * but as a private stream. The Redirect message will contain the info for the
 * client to re-request.
 * 
 * @see PrivateStrmLoginClient
 * @see PrivateStrmItemManager
 * @see PrivateStrmDirectoryClient
 * 
 */

public class StarterConsumer_PrivateStream
{

    // RFA objects
    protected Session _session;
    protected EventQueue _eventQueue;
    protected OMMConsumer _ommConsumer;
    protected PrivateStrmLoginClient _loginClient;
    protected PrivateStrmItemManager _itemManager;
    protected PrivateStrmDirectoryClient _directoryClient;
    protected OMMEncoder _encoder;
    protected OMMPool _pool;

    private boolean _hasOpenedRequest;
    private boolean _hasStandardStreamOpenedRequest;
    private final String _className = "StarterConsumer_PrivateStream";

    /**
     * 
     */
    public StarterConsumer_PrivateStream()
    {
        System.out.println("*****************************************************************************");
        System.out.println("*              Begin RFA Java StarterConsumer_PrivateStream Program         *");
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
        _loginClient = new PrivateStrmLoginClient(this);

        // Initialize item manager for item domains
        _itemManager = new PrivateStrmItemManager(this);

        // Initialize directory client for directory domain.
        _directoryClient = new PrivateStrmDirectoryClient(this);

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
            PSGenericOMMParser.initializeDictionary(fieldDictionaryFilename, enumDictionaryFilename);
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
     * @param success the boolean to indicate that the server has accepted the
     *            request or not
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
        System.out.println(runTime + " seconds elapsed, " + getClass().toString()
                + " cleaning up...");
    }

    /**
     * @return EventQueue
     */
    public EventQueue getEventQueue()
    {
        return _eventQueue;
    }

    /**
     * @return OMMConsumer
     */
    public OMMConsumer getOMMConsumer()
    {
        return _ommConsumer;
    }

    /**
     * @return OMMEncoder
     */
    public OMMEncoder getEncoder()
    {
        return _encoder;
    }

    /**
     * @return OMMPool
     */
    public OMMPool getPool()
    {
        return _pool;
    }

    /**
     * @param val : int
     */
    public void cleanup(int val)
    {
        cleanup(val, true);
    }

    /**
     * @param val val
     * @param doLoginCleanup true or false
     */
    public void cleanup(int val, boolean doLoginCleanup)
    {
        System.out.println(Context.string());

        _hasStandardStreamOpenedRequest = false;

        // unregister all items
        if (_itemManager != null)
            _itemManager.closeRequest();

        // unregister login
        // Allow conditional login unregister.
        // loginCleanup flag is set to false if cleanup is called after login failure.
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

    /**
     * Initialize and set the default for the command line options
     */
    static void addCommandLineOptions()
    {
        CommandLine.addOption("debug", false, "enable debug tracing");
        CommandLine.addOption("session", "myNamespace::mySession", "Session name to use");
        CommandLine.addOption("serviceName", "DIRECT_FEED", "service to request");
        CommandLine.addOption("itemName", "TRI.N", "List of items to open separated by ','.");
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
    }

    /**
     * @param argv cmd line args
     */
    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        StarterConsumer_PrivateStream demo = new StarterConsumer_PrivateStream();
        demo.init();
        demo.run();
        demo.cleanup(0);
    }

    /**
     * Checks if service specified is available. If so, it sends item request
     * using {@link PrivateStrmItemManager#sendRequest()}
     */
    public void processDirectoryInfo()
    {
        String serviceName = CommandLine.variable("serviceName");

        if (_directoryClient.getServiceList().contains(serviceName))
        {
            // do one or more initial requests that are all private stream
            // (corresponding item names are all from command line option
            // "itemName")
            _itemManager.sendRequest();

            // do one standard stream request for non-restricted item IBM.N
            // doing this check to avoid re-requesting item during
            // standard stream recovery by RFA
            if (!_hasStandardStreamOpenedRequest) 
            {
                OMMAttribInfo attribInfo = _pool.acquireAttribInfo();
                ;
                attribInfo.setName("IBM.N");
                attribInfo.setServiceName(serviceName);
                attribInfo.setNameType(RDMInstrument.NameType.RIC);
                
                // initial request is standard stream
                _itemManager.sendRequest(false, attribInfo);
                
                _hasStandardStreamOpenedRequest = true;
            }

            // do one standard stream request for restricted item RES-DS.N to
            // illustrate Private Stream item-based recall
            OMMAttribInfo attribInfo = _pool.acquireAttribInfo();
            ;
            attribInfo.setName("RES-DS.N");
            attribInfo.setServiceName(serviceName);
            attribInfo.setNameType(RDMInstrument.NameType.RIC);
            
            // initial request is standard stream
            _itemManager.sendRequest(false, attribInfo);
        }
        else
        {
            System.out.println(_className + ": Service '" + serviceName + "' is not available.");
        }
    }

}
