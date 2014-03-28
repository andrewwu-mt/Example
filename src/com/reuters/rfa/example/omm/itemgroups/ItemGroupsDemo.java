package com.reuters.rfa.example.omm.itemgroups;

import java.net.InetAddress;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
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
 * ItemGroupsDemo is a simple consumer example. This application demonstrates
 * how to manage item groups.
 * </p>
 * 
 * The ItemGroupsDemo program can connect to an RFA OMMProvider, a P2PS, or an
 * ADS.<br>
 * 
 */

public class ItemGroupsDemo
{
    // RFA objects
    protected Session _session;
    protected EventQueue _eventQueue;
    protected OMMConsumer _ommConsumer;
    protected LoginClient _loginClient;

    protected ItemManager _itemManager;
    protected ItemGroupManager _itemGroupManager;

    protected OMMEncoder _encoder;
    protected OMMPool _pool;

    private boolean _hasOpenedRequest;
    private String _className = "OMMItemGroupsDemo";

    public ItemGroupsDemo()
    {
        System.out.println("*****************************************************************************");
        System.out.println("*          Begin RFA Java ItemGroups Demo Program                           *");
        System.out.println("*****************************************************************************");
    }

    public void init()
    {
        Context.initialize();
        // Create a Session
        _session = Session.acquire(CommandLine.variable("session"));
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

        // Initialize item group manager
        _itemGroupManager = new ItemGroupManager(this);

        // Initialize item manager for item domains
        _itemManager = new ItemManager(this, _itemGroupManager);

        // Create an OMMConsumer event source
        _ommConsumer = (OMMConsumer)_session.createEventSource(EventSource.OMM_CONSUMER,
                                                               "myOMMConsumer", true);

        // Application may choose to download the enumtype.def and
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
            cleanup(1);
        }

        // Send login request
        // Application must send login request first
        _loginClient.sendRequest();

        // request directory
        // we are interested in state and group filter
        _itemGroupManager.sendRequest();
    }

    public void processLogin(boolean success)
    {
        if (success)
        {
            System.out.println(_className + " Login successful...");

            // Now we can send the directory request
            if (!_hasOpenedRequest)
            {
                // send item request only once.
                _itemManager.sendRequest();
                _hasOpenedRequest = true;
            }
        }
        else
        // Failure
        {
            System.out.println(_className + ": Login has been denied / rejected / closed.");
            System.out.print(_className + ": Preparing to clean up and exiting...");
            cleanup(1, false);
        }
    }

    // initialize the timer and dispatch events
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
        System.out.println(Context.string());

        _itemManager.closeRequest();
        _itemGroupManager.cleanup();

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

    public static void addCommandLineOptions()
    {
        CommandLine.addOption("session", "myNamespace::mySession", "Session name to use");
        CommandLine.addOption("serviceName", "DIRECT_FEED", "service to request");
        CommandLine.addOption("itemName", "TRI.N", "List of items to open separated by ','.");
        CommandLine.addOption("mmt", "MARKET_PRICE", "Message Model Type.");
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
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        ItemGroupsDemo demo = new ItemGroupsDemo();
        demo.init();
        demo.run();
        demo.cleanup(0);
    }

}
