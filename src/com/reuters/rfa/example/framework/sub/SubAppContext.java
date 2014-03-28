package com.reuters.rfa.example.framework.sub;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.config.ConfigDb;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.session.Session;

/**
 * SubAppContext is an utility object which performs several operations that a
 * subscriber application must perform before it can subscribe to items. Based
 * on the -type parameter, it creates either an OMM or MarketData application
 * context.
 * <p>
 * In order to use SubAppContext, at startup {@link #addCommandLineOptions()}
 * must be called to setup command line options.
 * 
 * <pre>
 * public static void main(String[] args)
 * {
 * 
 *     // setup the command line, this line is required
 *     SubAppContext.addCommandLineOptions();
 *     CommandLine.setArguments(args);
 * 
 *     // create a new instance depending on -type
 *     AppContextMainLoop mainLoop = new AppContextMainLoop(null);
 *     SubAppContext appContext = SubAppContext.create(mainLoop);
 * 
 *     // register interests
 *     Handle handle = appContext.register(client, serviceName, itemName, true);
 * 
 *     // dispatching event mainloop
 *     // dispatch until timeout has expired if -runTime is configured. if not,
 *     // dispatch infinitely
 *     mainLoop.run();
 * 
 *     // unregister interests
 *     appContext.unregister(handle);
 * 
 *     // cleaning up
 *     appContext.cleanup();
 * }
 * </pre>
 * 
 * </p>
 * The application uses register() or registerSync() to specify an interest and
 * use unregister() to unregister the interest.
 * <p>
 * The application may choose to implement and register
 * {@link SubAppContextClient}. The SubAppContext will notify its client when
 * <p>
 * If serviceName is specified,
 * <ul>
 * <li>service with serviceName is discovered regardless of its state</li>
 * <li>there is no pending dictionary</li>
 * </ul>
 * </p>
 * <p>
 * If serviceName is not specified,
 * <ul>
 * <li>any service is discovered</li>
 * <li>there is no pending dictionary</li>
 * </ul>
 * </p>
 * </p>
 * <p>
 * The application may choose to implement and register {@link DirectoryClient}.
 * The SubAppContext will notify the application when related event for
 * {@link ServiceInfo} arrives.
 * </p>
 * 
 * @see com.reuters.rfa.example.utility.CommandLine
 * @see MarketDataSubAppContext
 * @see OMMSubAppContext
 */
public abstract class SubAppContext implements Client
{
    static public void addCommandLineOptions()
    {
        CommandLine.addOption("debug", false, "enable debug tracing");
        CommandLine.addOption("serviceName", "IDN_RDF", "service to request");
        CommandLine.addOption("session", "myNS::RSSLSession", "Session name to use");
        AppContextMainLoop.addCommandLineOptions();
        String username = "rfa";
        try
        {
            //username = System.getProperty("user.name");
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
        CommandLine.addOption("fileDictionary", false, "load dictionary from file");
        CommandLine.addOption("enumType", "enumtype.def", "enumtype.def filename");
        CommandLine.addOption("type", "OMM", "type of sub connection (OMM | MarketData)");
        CommandLine.appendHelpString("\n-type OMM\n");
        CommandLine.appendHelpString("=========================================\n");
        OMMSubAppContext.addCommandLineOptions();
        CommandLine.appendHelpString("\n-type MarketData\n");
        CommandLine.appendHelpString("=========================================\n");
        MarketDataSubAppContext.addCommandLineOptions();
    }

    /**
     * @return The SubAppContext depending on the -type command line parameter
     */
    static public SubAppContext create(AppContextMainLoop mainloop)
    {
        String type = CommandLine.variable("type");
        if (type.equals("OMM"))
            return createOMM(mainloop);
        else if (type.equals("MarketData"))
            return createMarketData(mainloop);
        throw new RuntimeException("Unknown context type: " + type);
    }

    /**
     * @return The SubAppContext depending on the -type command line parameter,
     *         which has an internal AppContextMainLoop
     */
    static public SubAppContext create(PrintStream ps)
    {
        String type = CommandLine.variable("type");
        if (type.equals("OMM"))
            return createOMM(ps);
        else if (type.equals("MarketData"))
            return createMarketData(ps);
        throw new RuntimeException("Unknown context type: " + type);
    }

    /**
     * Create SubAppContext depending on the -type command line parameter and
     * use ConfigDb to initial configuration.
     * 
     * @return The SubAppContext depending on the -type command line parameter,
     *         which has an internal AppContextMainLoop
     */
    static public SubAppContext create(PrintStream ps, ConfigDb configDb)
    {
        String type = CommandLine.variable("type");
        if (type.equals("OMM"))
            return createOMM(ps, configDb);
        else if (type.equals("MarketData"))
            return createMarketData(ps);
        throw new RuntimeException("Unknown context type: " + type);
    }

    /**
     * @return The OMMSubAppContext
     */
    static public SubAppContext createOMM(AppContextMainLoop mainloop)
    {
        return new OMMSubAppContext(mainloop);
    }

    /**
     * @return The OMMSubAppContext
     */
    static public SubAppContext createOMM(AppContextMainLoop mainloop, ConfigDb configDb)
    {
        return new OMMSubAppContext(mainloop, configDb);
    }

    /**
     * @return The OMMSubAppContext which has an internal AppContextMainLoop
     */
    static public SubAppContext createOMM(PrintStream ps)
    {
        SubAppContext context = createOMM(new AppContextMainLoop(ps));
        context._ownsMainLoop = true;
        return context;
    }

    static public SubAppContext createOMM(PrintStream ps, ConfigDb configDb)
    {
        SubAppContext context = createOMM(new AppContextMainLoop(ps), configDb);
        context._ownsMainLoop = true;
        return context;
    }

    /**
     * @return The MarketDataSubAppContext
     */
    static public SubAppContext createMarketData(AppContextMainLoop mainloop)
    {
        return new MarketDataSubAppContext(mainloop);
    }

    /**
     * @return The MarketDataSubAppContext which has an internal
     *         AppContextMainLoop
     */
    static public SubAppContext createMarketData(PrintStream ps)
    {
        SubAppContext context = createMarketData(new AppContextMainLoop(ps));
        context._ownsMainLoop = true;
        return context;
    }

    /*
     * Constructor: initializes the context and sets up the logger. Initializes
     * variables using command line parameters
     */
    protected SubAppContext(AppContextMainLoop mainLoop)
    {
        Context.initialize();
        
        
        try {
            Preferences.importPreferences(new DataInputStream(new FileInputStream("FeedConfig.xml")));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (InvalidPreferencesFormatException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        
        
        
        _eventQueue = mainLoop.getEventQueue();
        _mainLoop = mainLoop;
        _printStream = mainLoop.getPrintStream();
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

        _serviceName = CommandLine.variable("serviceName");
        _runSyncs = new HashMap<Handle, RunSync>();
    }

    protected SubAppContext(AppContextMainLoop mainLoop, ConfigDb configDb)
    {
        Context.initialize(configDb);
        _eventQueue = mainLoop.getEventQueue();
        _mainLoop = mainLoop;
        _printStream = mainLoop.getPrintStream();
        boolean debug = CommandLine.booleanVariable("debug");
        if (debug)
        {
            // Enable debug logging
            Logger logger = Logger.getLogger("com.reuters.rfa");
            logger.setLevel(Level.FINE);
            Handler[] handlers = logger.getHandlers();

            if (handlers.length <= 0)
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

        _serviceName = CommandLine.variable("serviceName");
        _runSyncs = new HashMap<Handle, RunSync>();
    }

    /**
     * Set SubAppContext to automatically download dictionary. For MarketData,
     * it requires full Marketfeed dictionary. <br>
     * If set, it must be set before {@link AppContextMainLoop#run()
     * AppContextMainLoop.run()} is called.
     */
    public void setAutoDictionaryDownload()
    {
        _autoDictionaryDownload = true;
    }

    /**
     * If set, it must be set before {@link AppContextMainLoop#run()
     * AppContextMainLoop.run()} is called.
     * 
     * @param client
     */
    public void setCompletionClient(SubAppContextClient client)
    {
        _client = client;
    }

    /**
     * If set, it must be set before {@link AppContextMainLoop#run()
     * AppContextMainLoop.run()} is called.
     * 
     * @param client
     */
    public void setDirectoryClient(DirectoryClient client)
    {
        _directoryClient = client;
    }

    /**
     * @return configured service name
     */
    public String getServiceName()
    {
        return _serviceName;
    }

    /**
     * Create the session. Exit, if unable to create session.
     */
    public void createSession()
    {
        String sessionName = CommandLine.variable("session");

        _session = Session.acquire(sessionName);
        if (_session == null)
        {
            _printStream.println("Could not acquire session: " + sessionName);
            System.exit(1);
        }
        else
            _printStream.println("Successfully acquired session: " + sessionName);
    }

    /**
     * Register for an interest.
     * 
     * @param client The callback client
     * @param serviceName The service to request. If null, uses the configured
     *            service
     * @param itemName
     * @param streaming Is this a streaming request
     * @return The handle for this request
     */
    public Handle register(Client client, String serviceName, String itemName, boolean streaming)
    {
        if ((itemName == null || itemName.length() == 0))
            throw new RuntimeException("itemName must be set");

        boolean isServiceNameSet = (serviceName != null && serviceName.length() > 0);

        if (isServiceNameSet)
        {
            return register(client, _eventQueue, serviceName, itemName, streaming);
        }
        else if (_serviceName.length() > 0)
        {
            return register(client, _eventQueue, _serviceName, itemName, streaming);
        }
        else
        {
            throw new RuntimeException("serviceName must be set");
        }
    }

    /**
     * Register for an interest and wait for the response. registerSync will
     * block until {@link #setSyncReceived(Handle) setSyncReceived()} is called.
     */
    public boolean registerSync(Client client, String serviceName, String itemName)
    {
        RunSync rs = new RunSync();
        Handle handle = register(client, rs._syncEventQueue, serviceName, itemName, false);
        _runSyncs.put(handle, rs);
        boolean ret = rs.run(_mainLoop.getRunTime());
        _runSyncs.remove(handle);
        return ret;
    }

    /**
     * Must be called in the application to notify SubAppContext to exit from
     * {@link #registerSync(Client, String, String) registerSync()}.
     * 
     * @param handle The handle received from registerSync()
     */
    public void setSyncReceived(Handle handle)
    {
        RunSync rs = (RunSync)_runSyncs.get(handle);
        if (rs != null)
        {
            rs.setSyncReceived();
        }
        else
        {
            _printStream.println("The handle does not exist.");
        }
    }

    protected abstract Handle register(Client client, EventQueue queue, String serviceName,
            String itemName, boolean streaming);

    /**
     * Unregister an interest
     * 
     * @param handle The handle received from
     *            {@link #register(Client, String, String, boolean) register()}
     */
    public abstract void unregister(Handle handle);

    /**
     * Cleanup the SubAppContext. The application should call this method when
     * the SubAppContext is not needed anymore.
     */
    public void cleanup()
    {
        if (_ownsMainLoop)
            _mainLoop.cleanup();
    }

    /**
     * @return {@link com.reuters.rfa.dictionary.FieldDictionary
     *         FieldDictionary} read from files or downloaded from the network
     */
    public abstract FieldDictionary getFieldDictionary();

    /**
     * @return map in which the key is {@link FidDef#getName()} and the value is
     *         {@link FidDef}
     */
    public abstract Map<String, FidDef> getDictionary();

    public abstract void addNewService(String serviceName);

    public void run()
    {
        _mainLoop.run();
    }

    public void runAwt()
    {
        _mainLoop.runAwt();
    }

    public abstract NormalizedEvent getNormalizedEvent(Event event);

    boolean _autoDictionaryDownload;
    Session _session;
    String _serviceName;
    Map<Handle, RunSync> _runSyncs;

    // callback client
    SubAppContextClient _client;
    boolean _isComplete;
    DirectoryClient _directoryClient;
    PrintStream _printStream;
    AppContextMainLoop _mainLoop;
    boolean _ownsMainLoop;
    EventQueue _eventQueue;
}
