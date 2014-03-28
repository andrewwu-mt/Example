package com.reuters.rfa.example.omm.postingConsumer;

import java.net.InetAddress;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.ExampleUtil;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;
import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;

/**
 * <p>
 * This is a main class to run StarterConsumer_Post application.
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
 * <li>Create LoginClient handle Login request/response messages.
 * <li>Create ItemManager to handle item request/response post/ack messages.
 * <li>Dispatch events from an EventQueue
 * <li>Cleanup a Session
 * </ul>
 * 
 * @see PostLoginClient
 * @see PostItemManager
 * 
 */
/*
 * com.reuters.rfa.example.omm.postingConsumer.StarterConsumer_Post -itemName
 * IBM.N,MSFT.O -serviceName DIRECT_FEED -postInputFileName C:\post_input.txt
 * -dumpPost false -openItemStreams true -sendPostAfterItemOpen true
 */
public class StarterConsumer_Post
{
    // RFA objects
    protected Session _session;
    protected EventQueue _eventQueue;
    protected OMMConsumer _ommConsumer;
    protected OMMEncoder _encoder;
    protected OMMPool _pool;

    protected PostLoginClient _loginClient;
    protected DirectoryClient _directoryClient;
    protected PostItemManager _itemManager;

    Handle _errorHandle;

    String INFO_APPNAME;
    String APPNAME = "JPostConsumer ";
    String APP_CONFIGURATION = "JPostConsumer configuration";

    public StarterConsumer_Post()
    {
        System.out.println("*****************************************************************************");
        System.out.println("*           Begin RFA Java StarterConsumer_Post Program                     *");
        System.out.println("*****************************************************************************");

        INFO_APPNAME = "*Info* " + APPNAME + ": ";
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

    public Handle getLoginHandle()
    {
        if (_loginClient != null)
        {
            return _loginClient.getHandle();
        }

        return null;
    }

    public void cleanup(int val)
    {
        cleanup(val, true);
    }

    public void cleanup(int val, boolean doLoginCleanup)
    {
        System.out.println(Context.string());

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

        if( _eventQueue != null )
        	_eventQueue.deactivate();

        if( _session != null )
        	_session.release();
        Context.uninitialize();

        System.out.println(getClass().toString() + " exiting.");
        if (val != 0)
            System.exit(val);
    }

    /**
     * Initialize OMM Consumer application and clients
     */
    public boolean init()
    {
        // dump command line arguments
        ExampleUtil.dumpCommandArgs();

        // read config
        boolean debug = CommandLine.booleanVariable("debug");
        if (debug)
        {
            // Enable debug logging
            Logger logger = Logger.getLogger("com.reuters.rfa");
            logger.setLevel(Level.WARNING);
            Handler[] handlers = logger.getHandlers();

            if (handlers.length == 0)
            {
                Handler handler = new ConsoleHandler();
                handler.setLevel(Level.WARNING);
                logger.addHandler(handler);
            }

            for (int index = 0; index < handlers.length; index++)
                handlers[index].setLevel(Level.WARNING);
        }

        Context.initialize();

        // Create a Session
        String sessionName = CommandLine.variable("session");
        _session = Session.acquire(sessionName);
        if (_session == null)
        {
            System.out.println("Could not acquire session.");
            Context.uninitialize();
            return false;
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
        _loginClient = new PostLoginClient(this);

        // Initialize item manager for item domains
        _itemManager = new PostItemManager(this);

        // Create an OMMConsumer event source
        _ommConsumer = (OMMConsumer)_session.createEventSource(EventSource.OMM_CONSUMER,
                                                               "myOMMConsumer", true);

        // Application may choose to down-load the enumtype.def and RWFFldDictionary
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
            return false;
        }

        OMMErrorIntSpec errIntSpec = new OMMErrorIntSpec();
        _errorHandle = _ommConsumer.registerClient(_eventQueue, errIntSpec, _loginClient, null);

        if (_itemManager.initialize() == false)
            return false;

        // Send login request
        _loginClient.sendRequest();

        return true;
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
            System.out.println(INFO_APPNAME + "Login Granted");
            _directoryClient = new DirectoryClient(this);
            _directoryClient.sendRequest();
        }
        else
        // Failure
        {
            System.out.println("* Login has been denied / rejected / closed.");
            System.out.println("* Preparing to clean up and exiting...");
            cleanup(1, false);
        }
    }

    public void processDirectoryInfo()
    {

        // if configured, send item requests
        if (_itemManager._bSendItemRequest_ip == true)
        {
            System.out.println(INFO_APPNAME + "is opening Item Streams, based on "
                    + APP_CONFIGURATION);
            _itemManager.sendItemRequests();
        }
        else
        {
            System.out.println(INFO_APPNAME + "is NOT opening Item Streams, based on "
                    + APP_CONFIGURATION);
        }

        // based on configuration, send posts on startup after login is granted
        if (_itemManager._bSendPostAfterItemOpen_ip == false)
        {
            System.out.println(INFO_APPNAME + "Login is Granted; Starting to do Posts, based on "
                    + APP_CONFIGURATION);
            // slow down for service to come up
            ExampleUtil.slowDown(1000);

            _itemManager.sendPosts();
        }
        else
        {
            System.out.println(INFO_APPNAME + "Login is Granted; Will do Posts after "
                    + _itemManager._itemOpenCount + " items are opened, based on "
                    + APP_CONFIGURATION);
        }
    }

    public void closeLogin()
    {

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
     * process Cmd Error Event received when unable to post
     */
    public void processCmdErrorEvent(String name, Event event)
    {
        OMMCmdErrorEvent cmdErrEvent = ((OMMCmdErrorEvent)event);

        System.out.println("\nDump Event.OMM_CMD_ERROR_EVENT");
        System.out.println(".................................");
        System.out.println("*-Error! Unable to post");
        System.out.println("*-Handle:" + event.getHandle());
        System.out.println("*-Id: " + cmdErrEvent.getCmdID());
        System.out.println("*-Text: " + cmdErrEvent.getStatus().getStatusText());
        System.out.println("*-Code: " + cmdErrEvent.getStatus().getStatusCode().toString());
        System.out.println();

        return;
    }

    /**
     * process Ack received in reponse to a post
     */
    void processAckResponse(String name, OMMMsg respMsg)
    {
        if (respMsg.getMsgType() != OMMMsg.MsgType.ACK_RESP)
            return;

        System.out.println("\nDump Ack Response");
        System.out.println(".......................");

        /* get post id */
        System.out.println("- PostId = " + respMsg.getId());

        /* get sequence number if available */
        if (respMsg.has(OMMMsg.HAS_SEQ_NUM))
            System.out.println("- SeqNo = " + respMsg.getSeqNum());

        /* check for state information availability */
        if (!respMsg.has(OMMMsg.HAS_STATE))
        {
            System.out.println("- State not available");
            System.out.println("- * Positive ack *");
            return;
        }

        /* get state information availability */
        OMMState status = respMsg.getState();
        if (status == null)
        {
            System.out.println("- State not available *");
            System.out.println("- * Positive ack *");
            return;
        }

        /* code must be NackCode */
        if (status.isNackCode() == true)
            System.out.println("- Status Code is NACK code");
        else
            System.out.println("- Status Code is Status code");

        /* print Nack code */
        System.out.println("- Status Code = " + OMMState.NackCode.toString(status.getCode()));

        /* state information available; check if positive ack or negative ack */
        if (status.getCode() == OMMState.NackCode.NONE)
            System.out.println("- * Positive ack *");
        else
            System.out.println("- * Negative Ack *");

        /* print status text */
        if (status.getText() == null)
            System.out.println("- Status Text not available");
        else if (status.getText().length() == 0)
            System.out.println("- Status Text = \"\" ");
        else
            System.out.println("- Status Text = " + status.getText());

        /* get attrib info if available */
        if (respMsg.has(OMMMsg.HAS_ATTRIB_INFO))
            System.out.println("- Attrib Info available");

        /* get payload if available */
        if (respMsg.getDataType() != OMMTypes.NO_DATA)
            System.out.println("- Payload available");

        System.out.println();
    }

    /**
     * process status response
     */
    void processStatusResponse(String name, OMMMsg respMsg)
    {
        if (respMsg.getMsgType() != OMMMsg.MsgType.STATUS_RESP)
            return;

        System.out.println("\nDump Status Response");
        System.out.println(".......................");
        System.out.println("- Stream State: "
                + OMMState.Stream.toString(respMsg.getState().getStreamState()));
        System.out.println("- Data State: "
                + OMMState.Data.toString(respMsg.getState().getDataState()));
    }

    /**
     * Initialize and set the default for the command line options
     */
    static void addCommandLineOptions()
    {
        CommandLine.addOption("debug", false, "enable debug tracing");
        CommandLine.addOption("session", "myNamespace::mySession", "Session name to use");
        CommandLine.addOption("serviceName", "DIRECT_FEED", "service to request");
        CommandLine.addOption("itemName", "IBM.N,TRI.N", "List of items to open separated by ','.");
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

        CommandLine.addOption("openItemStreams", true, "Send Item requests");
        CommandLine.addOption("postInputFileName", "/postInput.txt",
                              "The input file with items to be posted");
        CommandLine.addOption("dumpPost", false,
                              "Dump the post information read from the file, to the console ");
        CommandLine.addOption("sendPostAfterItemOpen",
                           true,
                           "Send OnStream/OffStream Posts after all the items are open; Otherwise it is sent when login is granted");
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        StarterConsumer_Post demo = new StarterConsumer_Post();
        if (demo.init() == true)
            demo.run();
        demo.cleanup(0);
    }
}
