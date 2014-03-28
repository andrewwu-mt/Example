package com.reuters.rfa.example.omm.chain.cons;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.example.utility.CommandLine;

/**
 * <p>
 * This is a main class to run StarterConsumer_Chain application.
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
 * @see LoginClient
 * @see ItemManager
 * 
 */

public class StarterConsumer_Chain
{
    public StarterConsumer_Chain()
    {

    }

    /**
     * Initialize OMM Consumer application and clients
     */
    public void init(boolean isChain)
    {
        boolean _isChain = isChain;
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
        int i = 1;
        // Create GUI Frame
        ChainConsFrame _OMMConsumerChainFrame = new ChainConsFrame(_isChain, this);
        _OMMConsumerChainFrame.setTitle("StarterConsumer_Chain");
        _OMMConsumerChainFrame.setSize(550, 350);
        _OMMConsumerChainFrame.setVisible(true);
        _OMMConsumerChainFrame.setLocation(150 + i * 100, 150 + i * 100);
        _OMMConsumerChainFrame.addWindowListener(new myWindowListener());
    }

    class myWindowListener extends WindowAdapter
    {
        public void windowClosing(WindowEvent e)
        {
            if (ChainConsFrame._frameCount > 0)
            {
                ChainConsFrame._frameCount--;
            }
            if (ChainConsFrame._frameCount == 0)
            {
                cleanUp();
                System.exit(0);
            }
        }
    }

    protected void cleanUp()
    {
        Context.uninitialize();
    }

    /**
     * Initialize and set the default for the command line options
     */
    static void addCommandLineOptions()
    {
        CommandLine.addOption("debug", false, "enable debug tracing");
        CommandLine.addOption("session", "myNamespace::mySession", "Session name to use");
        CommandLine.addOption("serviceName", "IDN_RDF", "service to request");
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
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        StarterConsumer_Chain demo = new StarterConsumer_Chain();
        demo.init(true);
    }
}
