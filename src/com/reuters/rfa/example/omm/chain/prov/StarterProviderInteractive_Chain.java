package com.reuters.rfa.example.omm.chain.prov;

import java.io.File;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.session.omm.OMMProvider;

/**
 * <p>
 * StarterProviderInteractive_Chain is a simple interactive server provider example to
 * demonstrate how to interactively publish Reuters Domain Model chain data
 * using the Open Message Model.
 * </p>
 * 
 * This application is single-threaded server application. A consumer
 * application, either source distributor or RFA OMMConsumer, must connect to
 * the server to request data.
 * 
 * Currently, this application provides Logins, Service Directories,
 * Dictionaries, and Market Price. It can be easily extended to provide other
 * types of data.
 */
public class StarterProviderInteractive_Chain
{
    FieldDictionary _rwfDictionary;

    EventQueue _eventQueue;

    boolean _acceptSession;
    //HashMap _clientSessions;

    OMMProvider _provider;
    OMMPool _pool;
    int _updateInterval, _updateRate;
    String _service;
    File _ricFile;

    
    public StarterProviderInteractive_Chain()
    {
        System.out.println("Initializing StarterProvider Chain Demo ...");
        readConfiguration();
        Context.initialize();
        _pool = OMMPool.create();
        //_clientSessions = new HashMap();
        loadDictionary();

        // Create main window
        ChainPubFrame window = new ChainPubFrame(CommandLine.variable("provSession"), _pool,
                _service, _rwfDictionary, _ricFile);

        while (!window._shutdown)
        {
        }
    }

    private void loadDictionary()
    {
        _rwfDictionary = FieldDictionary.create();
        try
        {
            String rdmDictionary = CommandLine.variable("rdmFieldDictionary");
            String enumType = CommandLine.variable("enumType");
            FieldDictionary.readRDMFieldDictionary(_rwfDictionary, rdmDictionary);
            FieldDictionary.readEnumTypeDef(_rwfDictionary, enumType);
        }
        catch (DictionaryException e)
        {
            System.out.println("Dictionary read error: " + e.getMessage());
            if (e.getCause() != null)
                System.err.println(": " + e.getCause().getMessage());
            System.exit(-1);
        }
    }

    protected void readConfiguration()
    {
        _acceptSession = CommandLine.booleanVariable("acceptSession");
        _updateInterval = CommandLine.intVariable("updateInterval");
        _updateRate = CommandLine.intVariable("updateRate");
        _service = CommandLine.variable("serviceName");
        _ricFile = new File(CommandLine.variable("ricFile"));

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

    static void addCommandLineOptions()
    {
        CommandLine.addOption("debug", false, "enable debug tracing");
        CommandLine.addOption("provSession", "myNamespace::provSession",
                              "Provider session.  Defaults to myNamespace::provSession");
        CommandLine.addOption("listenerName", "",
                              "Unique name that specifies a connection to listen.  Defaults to \"\" ");
        CommandLine.addOption("serviceName", "PROVIDER",
                              "Service name for the SrcDirectory response.  Defaults to DIRECT_FEED");
        CommandLine.addOption("rdmFieldDictionary", "/var/triarch/RDMFieldDictionary",
                              "RDMField dictionary name and location.  Defaults to /var/triarch/RDMFieldDictionary");
        CommandLine.addOption("enumType", "/var/triarch/enumtype.def",
                              "RDMEnum dictionary name and location.  Defaults to /var/triarch/enumtype.def");
        CommandLine.addOption("acceptSession", "true",
                              "Whether or not to accept the consumer session. Defaults to true");
        CommandLine.addOption("updateInterval", 1, "Update interval.  Defaults to 1 seconds.");
        CommandLine.addOption("updateRate", 2,
                              "Update rate per interval.  Defaults to 2 update /interval.");
        CommandLine.addOption("ricFile", "chainRics", "File containing list of chain member.");
    }

    public void cleanup()
    {
        System.out.println("OMMChainProviderDemo terminated...");
        Context.uninitialize();
        System.exit(0);
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        StarterProviderInteractive_Chain demo = new StarterProviderInteractive_Chain();
        demo.cleanup();
    }

}
