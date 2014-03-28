package com.reuters.rfa.example.omm.idn.newsviewer;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BoxLayout;
import javax.swing.JFrame;

import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.framework.sub.SubAppContextClient;
import com.reuters.rfa.example.utility.CommandLine;

/**
 * <p>
 * This is a start point to run the NewsViewer application.
 * </p>
 * 
 * The class is responsible for the following actions:
 * <ul>
 * <li>Set up and obtain command line options
 * <li>Initialize the GUI
 * <li>Create the {@link SubAppContext}
 * <li>Release the Session's resources when closing the GUI
 * </ul>
 * 
 **/
public class NewsViewer implements SubAppContextClient
{
    // Configuration
    protected String _serviceName;
    protected String _itemName;

    // RFA objects
    protected Handle _itemHandle;
    protected String _fontname;

    protected SubAppContext _appContext;
    FontMetrics fontM;
    NewsHeadlineViewer _newsHeadlineViewer;
    JFrame _frame;

    public NewsViewer()
    {
        // Read options from the command line
        _fontname = CommandLine.variable("fontName");
        _serviceName = CommandLine.variable("serviceName");
        _itemName = CommandLine.variable("itemName");
        _appContext = SubAppContext.createOMM(System.out);
        _appContext.setAutoDictionaryDownload();
        _appContext.setCompletionClient(this);
    }

    /**
     * Initializes GUI components
     * 
     */
    public void init()
    {
        _frame = new JFrame("NewsViewer");
        _frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        _frame.addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent we)
            {
                cleanup();
                System.exit(0);
            }
        });

        Font font = new Font(_fontname, Font.PLAIN, 12);
        fontM = _frame.getFontMetrics(font);
        _frame.setFont(font);
        _frame.getContentPane().setLayout(new BoxLayout(_frame.getContentPane(), BoxLayout.Y_AXIS));

        NewsStoryViewer newsStoryViewer = new NewsStoryViewer(_appContext, _serviceName, font);
        _newsHeadlineViewer = new NewsHeadlineViewer(newsStoryViewer, font);

        _frame.getContentPane().add(_newsHeadlineViewer._filterSelector.component());
        _frame.getContentPane().add(_newsHeadlineViewer.component());
        _frame.getContentPane().add(newsStoryViewer.component());
        _frame.pack();
        _frame.setVisible(true);
    }

    public void processComplete()
    {
        // MR rfaj0838: Make GUI visible at start up.
        // _frame.setVisible(true);

        System.out.println("Subscribing to " + _itemName);
        NewsHeadlineClient myClient = new NewsHeadlineClient(_newsHeadlineViewer);
        _appContext.register(myClient, _serviceName, _itemName, true);

    }

    public void cleanup()
    {
        _appContext.cleanup();
    }

    public void run()
    {
        _appContext.runAwt();
    }

    public static void addCommandLineOptions()
    {
        SubAppContext.addCommandLineOptions();
        CommandLine.changeDefault("serviceName", "IDN_RDF");
        CommandLine.addOption("itemName", "N2_UBMS", "news item name to request");
        CommandLine.addOption("fontName", "Arial Unicode MS", "Font to use");
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);
        NewsViewer demo = new NewsViewer();

        demo.init();
        demo.run();
    }
}
