package com.reuters.rfa.example.omm.multipleConsumers;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.config.ConfigDb;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.session.Session;

/**
 * <li>MultipleConsumers is a multi-threaded console application that uses RFA
 * Java to connect and request items from multiple servers at the same time,
 * from different sessions. <li>Creates n Sessions where n is configurable <li>
 * For each session, creates ConsumerClient. The ConsumerClient creates an
 * OMMConsumer event source & requests Login <li>Maintains a timer to collect
 * and print message statistics every 5 secs <li>Supports input configuration
 * for itemCount, decodeLevel & eventQUsage/session <li>Executes the application
 * for a configurable time period
 * 
 * <pre>
 *   List of ConsumerClients
 *   *--------------------------------------------------------------------------------------* 		  
 *   | ConsumerClient - EventSource) | Session | Connection | Server (Provider Application) |
 *   *--------------------------------------------------------------------------------------*
 *   | ConsumerClient - EventSource) | Session | Connection | Server (Provider Application) |
 *   *--------------------------------------------------------------------------------------* 
 *    :
 *    :
 * 
 * </pre>
 */

public class MultipleConsumers
{
    // Command line :
    // com.reuters.rfa.example.omm.multipleConsumers.MultipleConsumers
    // -rdmFieldDictionary c:\test\dict\RDMFieldDictionary
    // -enumType c:\test\dict\enumtype.def -serviceName DIRECT_FEED
    // -itemCount 50000 -decodeLevel 5
    // -sessionList myTest::localOMMConsumer02,myTest::localOMMConsumer03
    // -sessionCount 2 -runTime 1000 -nullEventQList 1 -autoSession true
    // -autoStartPort 14001
    //
    // JVM options:
    // -server -Xms1024m -Xmx1024m -XX:+AggressiveOpts
    // -XX:+UseBiasedLocking -XX:+UseFastAccessorMethods

    // List of consumers (sessions)
    static ArrayList<ConsumerClient> m_consumerClientList;

    // field dictionary
    static protected FieldDictionary m_Dictionary;

    // statistics timer
    private static StatisticsTimerTask m_statsTimerTask;
    private static Timer m_statsTimer;

    // input configuration
    static int m_sessionCount;
    static private boolean m_bUseEventQ[];
    static private int m_decodeLevel;
    static private int m_itemCount;

    /*
     * Constructor
     */
    public MultipleConsumers()
    {

    }

    /*
     * Called when the main application thread is done when the time is up -
     * stops monitor, stop the consumers on the sessions
     */
    void stopTest()
    {
        // stop monitor
        if (m_statsTimerTask != null)
        {
            m_statsTimerTask.cancel();
            m_statsTimerTask = null;
        }

        // stopTest consumers
        for (int i = 0; i < m_sessionCount; i++)
        {
            if (m_consumerClientList.get(i) != null)
                ((ConsumerClient)m_consumerClientList.get(i)).stopTest();
        }

        // uninitialize RFA
        Context.uninitialize();
    }

    /*
     * Called at application start - reads & sets up configuration - create
     * sessions & consumerClients - creates OMMConsumer event sources
     * (consumerClient-session-OMMConsumer) - sends login request for each
     * session - starts response dispatcher, if applicable - starts monitor to
     * display response statistics
     */
    boolean startTest()
    {
        // dump command line arguments
        String commandLineString = CommandLine.getConfiguration();
        System.out.println("Input/Default Configuration");
        System.out.println("======================");
        System.out.println(commandLineString);
        System.out.println("======================");

        // item count
        m_itemCount = CommandLine.intVariable("itemCount");

        // setup session count;
        m_consumerClientList = new ArrayList<ConsumerClient>();

        int sessionCount = CommandLine.intVariable("sessionCount");

        boolean bAutoSession = CommandLine.booleanVariable("autoSession");
        if (bAutoSession == false)
        {
            Context.initialize();

            // acquire sessions & create consumer client
            String[] pieces = CommandLine.variable("sessionList").split(",");
            for (int i = 0; i < pieces.length; i++)
            {
                if (sessionCount == m_consumerClientList.size())
                    break;

                Session session = Session.acquire(pieces[i]);
                if (session == null)
                    continue;
                System.out.println("RFA Version: " + Context.getRFAVersionInfo().getProductVersion());

                ConsumerClient consumerClient = new ConsumerClient(session);
                m_consumerClientList.add(consumerClient);
            }

        }
        else
        {
            ConfigDb configDb = new ConfigDb();
            Context.initialize(configDb);

            int autoStartPort = CommandLine.intVariable("autoStartPort");

            // acquire sessions & create consumer client
            for (int i = 0; i < sessionCount; i++)
            {
                // if session is invalid, skip
                String sessionConfigName = "localhost_" + (autoStartPort + i);
                String sessionName = "localhost:" + (autoStartPort + i);

                configDb.addVariable("MultipleConsumers.Sessions." + sessionConfigName
                        + ".connectionList", sessionConfigName);
                configDb.addVariable("MultipleConsumers.Sessions." + sessionConfigName
                        + ".shareConnections", "false");
                configDb.addVariable("MultipleConsumers.Connections." + sessionConfigName
                        + ".connectionType", "RSSL");
                configDb.addVariable("MultipleConsumers.Connections." + sessionConfigName
                        + ".serverList", sessionName);
                if (m_itemCount > 16384)
                {
                    int exp = 1 + (int)(Math.log(m_itemCount) / Math.log(2));
                    int hashTableSize = (int)Math.pow(2, exp);
                    configDb.addVariable("MultiConsPerfTest.Connections." + sessionConfigName
                            + ".initialWatchlistSize", Integer.toString(hashTableSize));
                }

                Session session = Session.acquire("MultipleConsumers::" + sessionConfigName);
                if (session == null)
                    continue;
                System.out.println("RFA Version: " + Context.getRFAVersionInfo().getProductVersion());

                ConsumerClient consumerClient = new ConsumerClient(session);
                m_consumerClientList.add(consumerClient);
            }
        }

        // reset session count, if it exceeds "configured sessions length"
        m_sessionCount = sessionCount;
        if (m_sessionCount > m_consumerClientList.size())
            m_sessionCount = m_consumerClientList.size();

        if (m_sessionCount == 0)
        {
            System.out.println("Unable to establish Sessions; sessionCount = 0");
            return false;
        }

        // setup eventQ usage; default queues are used
        m_bUseEventQ = new boolean[m_sessionCount];
        for (int i = 0; i < m_sessionCount; i++)
            m_bUseEventQ[i] = true;

        // setup eventQ usage based on input
        String eventQueueOptions = CommandLine.variable("nullEventQList");
        if (eventQueueOptions.length() > 0)
        {
            // support ranges e.g. 1-3;
            // this translates to - use null eventQ for sessions 1,2,3
            String[] range = eventQueueOptions.split(",");

            for (int i = 0; i < range.length; i++)
            {
                String[] individualPieces = range[i].split("-");
                int begin = Integer.parseInt(individualPieces[0]);
                int end = begin;
                if (individualPieces.length > 1)
                {
                    end = Integer.parseInt(individualPieces[1]);
                }
                if (end > m_bUseEventQ.length)
                    end = m_bUseEventQ.length;
                if (begin > end)
                    continue;

                if ((begin == 0) && (end == 0))
                    break;

                for (int j = begin; j <= end; j++)
                {
                    m_bUseEventQ[j - 1] = false;
                }
            }
        }

        // decode level
        m_decodeLevel = CommandLine.intVariable("decodeLevel");
        if (m_decodeLevel > 0)
        {
            loadDictionary();
            if (m_decodeLevel > 5) // max level
            {
                m_decodeLevel = 5;
            }
        }

        // for each session-consumer, send login request;
        for (int i = 0; i < m_sessionCount; i++)
        {
            ConsumerClient consumerClient = (ConsumerClient)m_consumerClientList.get(i);
            consumerClient.login(m_bUseEventQ[i], m_itemCount, m_decodeLevel);

            // start response message dispatcher, if available
            consumerClient.startResponseDispatcher();
        }

        // start monitor
        m_statsTimer = new Timer("Statistics Timer");
        m_statsTimerTask = new StatisticsTimerTask();
        m_statsTimer.scheduleAtFixedRate(m_statsTimerTask, 0, 5000);

        return true;
    }

    /*
     * Load dictionary - Called when decode level is greater than 0
     */
    private void loadDictionary()
    {
        String _fieldDictionaryFilename = CommandLine.variable("rdmFieldDictionary");
        String _enumDictionaryFilename = CommandLine.variable("enumType");
        m_Dictionary = FieldDictionary.create();
        try
        {
            FieldDictionary.readRDMFieldDictionary(m_Dictionary, _fieldDictionaryFilename);
            FieldDictionary.readEnumTypeDef(m_Dictionary, _enumDictionaryFilename);
            System.out.println("Dictionary loaded from " + _fieldDictionaryFilename + ", "
                    + _enumDictionaryFilename);
        }
        catch (DictionaryException e)
        {
            System.out.println("Dictionary read error:  " + e.getMessage());
        }
    }

    /*
     * Add Command Line options - Called at startup to setup command line
     * options
     */
    private static void addCommandLineOptions()
    {
        String username = "guest";
        try
        {
            username = System.getProperty("user.name");
        }
        catch (Exception e)
        {
        }
        CommandLine.addOption("user", username, "DACS username for Login");

        String defaultPosition = "1.1.1.1/net";
        try
        {
            defaultPosition = InetAddress.getLocalHost().getHostAddress() + "/"
                    + InetAddress.getLocalHost().getHostName();
        }
        catch (Exception e)
        {
        }
        CommandLine.addOption("position", defaultPosition, "DACS position for login for Login");

        CommandLine.addOption("application", "256", "DACS application ID for Login");

        CommandLine.addOption("rdmFieldDictionary", "/var/reuters/RDMFieldDictionary",
                              "RDM Field dictionary name and location");
        CommandLine.addOption("enumType", "/var/reuters/enumtype.def",
                              "Enum dictionary name and location");
        CommandLine.addOption("serviceName", "DIRECT_FEED", "Service used for requests");

        CommandLine.addOption("itemCount", 50000,
                              "The number of items to request from each session");
        CommandLine.addOption("decodeLevel", 0,
                           "Decode every update (0=no, 1=iterate, 2=fiddef, 3=data, 4=field content except strings, 5=decode all");

        CommandLine.addOption("sessionList", "localhost:14002,localhost:14003",
                              "comma separated list of hosts");
        CommandLine.addOption("sessionCount", 0, "no of sessions to establish");
        CommandLine.addOption("autoSession", true,
                              "configure sessions at runtime; this avoids config DB");
        CommandLine.addOption("autoStartPort", 14001,
                           "The starting port for automatic sessions; valid only if autoSession is TRUE");

        CommandLine.addOption("nullEventQList", "",
                              "comma separated list of null event Q usage for the sessions");

        CommandLine.addOption("mmt", "MARKET_PRICE", "message model type");
        CommandLine.addOption("runTime", "600", "Run time (secs) of the application");
    }

    /*
     * class StatisticsTimerTask - called every 5 secs to dump each response
     * statistics for each consumer
     */
    static class StatisticsTimerTask extends TimerTask
    {
        int m_statsCounter = 0;
        StringBuilder m_dumpString = new StringBuilder(100);
        boolean m_bUpdatesReadyOnAllClients = false;

        public void run()
        {
            if (m_bUpdatesReadyOnAllClients == false)
            {
                m_bUpdatesReadyOnAllClients = true;
                for (int i = 1; i <= m_sessionCount; i++)
                {
                    m_bUpdatesReadyOnAllClients = ((ConsumerClient)m_consumerClientList.get(i - 1)).m_itemClient
                            .allItemsReceived(i - 1) & m_bUpdatesReadyOnAllClients;
                }
            }

            ++m_statsCounter;

            if (m_bUpdatesReadyOnAllClients == false)
                System.out.println();

            for (int i = 1; i <= m_sessionCount; i++)
            {
                if (m_bUpdatesReadyOnAllClients == false)
                {
                    // dump LoginStatus OR GeneralStats OR Updates on a single
                    // line
                    ((ConsumerClient)m_consumerClientList.get(i - 1)).m_itemClient
                            .printStats(m_statsCounter, m_dumpString);
                }
                else
                {
                    // - dump Updates OR GeneralStats
                    //
                    // - updates are consolidated for all consumers & printed on
                    // a single line;
                    // this needs the foll. info -
                    // "is this the 1st session","is this the last session"
                    m_dumpString = ((ConsumerClient)m_consumerClientList.get(i - 1)).m_itemClient
                            .printStats(m_statsCounter, m_dumpString, i == 1, i == m_sessionCount);
                    if (m_dumpString.length() == 0)
                        continue;
                }
            }

            if (m_bUpdatesReadyOnAllClients == false)
                System.out.println();
        }
    }

    /*
     * Main
     */
    public static void main(String[] args)
    {
        // setup command line options
        addCommandLineOptions();
        CommandLine.setArguments(args);

        // setup test application
        MultipleConsumers testApplication = new MultipleConsumers();
        String pieces[] = testApplication.getClass().getName().split("\\.");
        String myName = pieces[pieces.length - 1];

        // setup thread name
        Thread t = Thread.currentThread();
        t.setName(myName);

        // start test failed
        if (testApplication.startTest() == false)
        {
            // stop test
            testApplication.stopTest();

            // exit
            System.out.println("Bye.... End of Tests....");
            System.exit(0);

        }

        // run thread until runtime
        int secs = CommandLine.intVariable("runTime");
        long remainingTime = secs * 1000;
        try
        {
            Thread.sleep(remainingTime);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
            System.exit(0);
        }

        // stop test
        testApplication.stopTest();

        // exit
        System.out.println("Bye.... End of Tests....");
        System.exit(0);
    }
}
// //////////////////////////////////////////////////////////////////////////////
// / End of file
// //////////////////////////////////////////////////////////////////////////////

