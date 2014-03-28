package com.reuters.rfa.example.omm.sourcemirroringcons;

import java.net.InetAddress;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMConsumer;

/**
 * <p>
 * This is a main class to run StarterConsumer_SourceMirroring application.
 * </p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Initialize and set command line options
 * <li>Create a {@link com.reuters.rfa.session.Session Session}
 * <li>and an {@link com.reuters.rfa.common.EventQueue EventQueue}
 * <li>Create an {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer}
 * event source,
 * <li>an {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder} and an
 * <li>{@link com.reuters.rfa.omm.OMMPool OMMPool}.
 * <li>Create LoginClient and DirectoryClient to handle Login / directory
 * request, response and generic messages.
 * <li>Dispatch events from an EventQueue
 * <li>Create GenericItemManager to handle sending/receiving generic item
 * messages
 * <li>Create Generic message with Consumer Status Mode
 * <li>Create Generic message to start the generic message chain with provider
 * <li>Cleanup a Session
 * </ul>
 * 
 * @see LoginClient
 * @see DirectoryClient
 * @see GenericItemManager
 * 
 */

public class StarterConsumer_SourceMirroring
{
    // RFA objects
    protected Session _session;
    protected EventQueue _eventQueue;
    protected OMMConsumer _ommConsumer;
    protected LoginClient _loginClient;
    protected DirectoryClient _directoryClient;
    protected GenericItemManager _itemManager;

    protected OMMEncoder _encoder;
    protected OMMPool _pool;

    private boolean _hasOpenedRequest = false;
    private String _className = "StarterConsumer_SourceMirroring";
    protected String _serviceName;
    protected int _sourceMirroringMode;

    public StarterConsumer_SourceMirroring()
    {
        System.out.println("*****************************************************************************");
        System.out.println("*          Begin RFA Java StarterConsumer_SourceMirroring Program           *");
        System.out.println("*****************************************************************************");
    }

    /**
     * Initialize Consumer application and clients
     * 
     */
    public void init()
    {
        _serviceName = CommandLine.variable("serviceName");
        _sourceMirroringMode = CommandLine.intVariable("sourceMirroringMode");
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
        _loginClient = new LoginClient(this);

        // Initialize client for directory domain.
        _directoryClient = new DirectoryClient(this, _serviceName);

        // Initialize item manager
        _itemManager = new GenericItemManager(this);

        // Create an OMMConsumer event source
        _ommConsumer = (OMMConsumer)_session.createEventSource(EventSource.OMM_CONSUMER,
                                                               "myOMMConsumer", true);

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

    public void processDirectory()
    {
        System.out.println(_className + " Received Directory Resp");
        // Check if the consumer status should be sent back
        if (_directoryClient.getAcceptingConsumerStatus(_serviceName))
        {
            _directoryClient.sendGenericMsgWithStatusMode();
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
        System.out.println("Cleaning up resources....");
        System.out.println(Context.string());

        // unregister directory client
        if (_directoryClient != null)
            _directoryClient.cleanup();

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
        {
            _ommConsumer.destroy();
        }

        _eventQueue.deactivate();
        _session.release();
        Context.uninitialize();

        System.out.println(getClass().toString() + " exiting.");
        if (val != 0)
        {
            System.exit(val);
        }
    }

    /**
     * Initialize and set the default for the command line options
     */
    static void addCommandLineOptions()
    {
        CommandLine.addOption("debug", false, "enable debug tracing");
        CommandLine.addOption("session", "RSSLNamespace::localConsumerSession",
                              "Session name to use");
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
        CommandLine.addOption("runTime", 600,
                              "How long application should run before exiting (in seconds)");
        CommandLine.addOption("serviceName", "DIRECT_FEED", "mirroringProv");
        // to test the source mirroring feature add option sourceMirroringMode
        // set to 0, 1 or 2
        CommandLine.addOption("sourceMirroringMode", -1, "Mode of this consumer");
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        StarterConsumer_SourceMirroring demo = new StarterConsumer_SourceMirroring();
        demo.init();
        demo.run();
        demo.cleanup(0);
    }
}
