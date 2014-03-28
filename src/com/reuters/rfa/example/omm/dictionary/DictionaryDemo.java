package com.reuters.rfa.example.omm.dictionary;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMConsumer;

/**
 * <p>
 * This is a main class to run DictionaryDemo application.
 * </p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Initialize and set command line options
 * <li>Create a {@link com.reuters.rfa.session.Session Session} and an
 * {@link com.reuters.rfa.common.EventQueue EventQueue}
 * <li>Create an {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer}
 * event source, an {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder} and an
 * {@link com.reuters.rfa.omm.OMMPool OMMPool}
 * <li>Create LoginClient to handle Login request and response messages
 * <li>Create DirectoryClient to discover list of dictionary name
 * <li>Create DictionaryClient to handle requests and process data dictionary
 * responses from network</li>
 * <li>Read local dictionaries from files if it needs
 * <li>Dispatch events from an EventQueue
 * <li>Cleanup a Session
 * </ul>
 * 
 * @see LoginClient
 * @see DirectoryClient
 * @see DictionaryClient
 * 
 */

public class DictionaryDemo
{

    // RFA objects
    protected Session _session;
    protected EventQueue _eventQueue;
    protected OMMConsumer _ommConsumer;
    protected OMMEncoder _encoder;
    protected OMMPool _pool;

    protected LoginClient _loginClient;
    protected DirectoryClient _directoryClient;

    private boolean _hasOpenedRequest;

    protected FieldDictionary _localDictionary;

    private String _className = "DictionaryDemo";

    public DictionaryDemo()
    {
        System.out.println("*****************************************************************************");
        System.out.println("*               Begin RFA Java Dictionary Program                           *");
        System.out.println("*****************************************************************************");
    }

    /**
     * Initialize the application and clients
     * 
     */
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
        _directoryClient = new DirectoryClient(this);

        if (_loginClient == null || _directoryClient == null)
        {
            System.out.println("ERROR:" + _className
                    + " failed to create LoginClient / DirectoryClient");
            cleanup(-1);
        }

        // Create an OMMConsumer event source
        _ommConsumer = (OMMConsumer)_session.createEventSource(EventSource.OMM_CONSUMER,
                                                               "myOMMConsumer", true);

        // Load dictionary from files in to FieldDictionary, if the files have
        // been specified.
        String fieldDictionaryFilename = CommandLine.variable("rdmFieldDictionary");
        String enumDictionaryFilename = CommandLine.variable("enumType");
        if ((fieldDictionaryFilename.length() > 0) && (enumDictionaryFilename.length() > 0))
        {
            try
            {
                _localDictionary = FieldDictionary.create();
                FieldDictionary.readRDMFieldDictionary(_localDictionary, fieldDictionaryFilename);
                System.out.println(_className + ": " + fieldDictionaryFilename + " is loaded.");
                FieldDictionary.readEnumTypeDef(_localDictionary, enumDictionaryFilename);
                System.out.println(_className + ": " + enumDictionaryFilename + " is loaded.");
                printLocalDictionaryInfo();
            }
            catch (DictionaryException ex)
            {
                _localDictionary = null;
                System.out.println("ERROR: " + _className
                        + " unable to initialize dictionary from file.");
                System.out.println(ex.getMessage());
                if (ex.getCause() != null)
                    System.err.println(": " + ex.getCause().getMessage());
                System.out.println(_className
                        + " will continue download dictionary from server soon.");
            }
        }

        // Send login request
        // Application must send login request first
        _loginClient.sendRequest();
    }

    public void printLocalDictionaryInfo()
    {
        if (_localDictionary == null)
        {
            System.out.println(_className + ": Local dictionary is not available.");
            return;
        }

        System.out.println(_className + ": Local dictionary from files: ");
        String value = null;
        StringBuilder str = new StringBuilder();
        str.append("Dictionary Id=");
        str.append(_localDictionary.getDictId());

        str.append("\nField Dictionary:");
        str.append("\n	Version=");
        str.append(_localDictionary.getFieldProperty("Version"));

        value = _localDictionary.getFieldProperty("TemplateRelease");
        if (value != null)
        {
            str.append("\n	TemplateRelease=");
            str.append(value);
        }

        value = _localDictionary.getFieldProperty("Date");
        if (value != null)
        {
            str.append("\n	Date=");
            str.append(value);
        }

        value = _localDictionary.getFieldProperty("Description");
        if (value != null)
        {
            str.append("\n	Description=");
            str.append(value);
        }

        str.append("\nEnumeration Dictionary:");
        str.append("\n	Version=");
        str.append(_localDictionary.getEnumProperty("Version"));

        value = _localDictionary.getEnumProperty("TemplateRelease");
        if (value != null)
        {
            str.append("\n	TemplateRelease=");
            str.append(value);
        }

        value = _localDictionary.getEnumProperty("Date");
        if (value != null)
        {
            str.append("\n	Date=");
            str.append(value);
        }

        value = _localDictionary.getEnumProperty("Description");
        if (value != null)
        {
            str.append("\n	Description=");
            str.append(value);
        }
        System.out.println(str.toString());

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
                // send directory request of directory info. Send request only
                // once.
                _directoryClient.sendRequest();
                _hasOpenedRequest = true;
            }
        }
        else
        // Failure
        {
            System.out.println(_className + ": Login has been denied / rejected / closed.");
            System.out.print(_className + ": Preparing to clean up and exiting...");
            cleanup(1);
        }
    }

    /**
     * Processes direcotory information. Download dictionaries from the list in
     * DictionariesPrivided. <br>
     * If local dicitionary from files is existed, download dictoinary info from
     * server. <br>
     * If it's not, download full dictionaries from server.
     */
    public void processDirectoryInfo()
    {

        Iterator<Map.Entry<String, ArrayList<String>>> iter = _directoryClient.getServiceMap()
                .entrySet().iterator();
        Map.Entry<String, ArrayList<String>> entry;
        String dictionaryName;
        DictionaryClient dictionaryClient;

        // Download dictionary from the first service that available.
        if (iter.hasNext())
        {
            entry = iter.next();
            String serviceName = entry.getKey();
            ArrayList<String> dictionaryNames = entry.getValue();
            dictionaryClient = new DictionaryClient(this, serviceName);
            for (Iterator<String> arrayIter = dictionaryNames.iterator(); arrayIter.hasNext();)
            {
                dictionaryName = arrayIter.next();
                if (_localDictionary != null)
                {
                    System.out.println(_className + ": " + dictionaryName
                                    + " on local is available, downloading data dictionary info from server to check version...");
                    dictionaryClient.openDictionaryInfo(dictionaryName);
                }
                else
                {
                    // No local dictionary, downloading full data dictionary
                    // from server..."
                    dictionaryClient.openFullDictionary(dictionaryName);
                }
            }
        }
        else
        {
            System.out.println(_className
                    + ": No service is available to download data dictionaries");
            System.out.println(_className + ": Prepare to clean up and exit...");
            cleanup(1);
        }
    }

    /**
     * Returns local dictioanary from files
     */
    public FieldDictionary getLocalDictionary()
    {
        return _localDictionary;
    }

    /**
     * Dispatch events
     */
    public void run()
    {
        while (true)
            try
            {
                _eventQueue.dispatch(1000);
            }
            catch (DispatchException de)
            {
                System.out.println("EventQueue has been deactivated.");
                return;
            }
    }

    public void cleanup(int val)
    {
        System.out.println(Context.string());

        // OMMConsumer.destroy will unregister all clients
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

    public static void addCommandLineOptions()
    {
        CommandLine.addOption("session", "myNamespace::mySession", "Session name to use");
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
        CommandLine.addOption("rdmFieldDictionary", "",
                              "Optionallly specify RDM FieldDictionary path and filename");
        CommandLine.addOption("enumType", "", "Optionallly specify enumtype.def path and filename");
        CommandLine.addOption("dumpFiles", "false",
                           "Dump output of dictionaries from network into files, <dictionaryName>_network, under working directory");
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        DictionaryDemo demo = new DictionaryDemo();
        demo.init();
        demo.run();

        // Need to call clean up here, the program will exit when it finished
        // printing data dictionary.
    }

}
