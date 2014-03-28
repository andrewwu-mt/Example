package com.reuters.rfa.example.omm.gui.quotelist;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.JApplet;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.reuters.rfa.config.ConfigDb;
import com.reuters.rfa.example.applet.AppletUtility;
import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.framework.sub.SubAppContextClient;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.gui.JLoggedStatusBar;

/**
 * <p>
 * This is a start point to run QuoteList GUI application.
 * </p>
 * 
 * The class is responsible for the following actions:
 * <ul>
 * <li>Set up and obtain command line options
 * <li>Initializing the GUI
 * <li>Create a {@link SubAppContext}
 * <li>Releasing the Session's resources when closing the GUI
 * </ul>
 * 
 **/
public class QuoteList extends JApplet implements SubAppContextClient
{
    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;

    private String[] appletParam = { "fontSize", "font", "serviceName", "fieldNames", "itemNames",
            "debug", "session", "user", "position", "application" };

    /*
     * Initializes QuoteList application (for Applet)
     */
    public void init()
    {
        CommandLine.setArguments(new String[] {});
        addCommandLineOptions();

        _configDb = new ConfigDb();
        AppletUtility.loadAppletParameter(this, appletParam);
        AppletUtility.loadConfigDbParameter(this, "configDb", _configDb);
    }

    /*
     * for Applet
     */
    public void start()
    {
        // initGUI from AWT thread to avoid deadlocks
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                initGUI();
            }
        });
    }

    /*
     * for Applet
     */
    public void destroy()
    {
        cleanup();
        _configDb = null;
    }

    /*
     * for Application
     */
    public void initFrame()
    {
        initGUI();
        String fontSizeStr = CommandLine.variable("fontSize");
        int fontSize = Integer.parseInt(fontSizeStr);
        final JFrame appFrame = new JFrame(getClass().toString());
        appFrame.getContentPane().add("Center", _quoteListDisplay.component());

        appFrame.addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent we)
            {
                cleanup();
                appFrame.setVisible(false);
                appFrame.dispose();
                System.exit(0);
            }
        });
        appFrame.pack();
        int dim = fontSize * 24;
        appFrame.setSize(new Dimension(dim * 3, dim));
        appFrame.setVisible(true);
    }

    protected void initGUI()
    {
        // initLookAndFeel
        // font
        String fontSizeStr = CommandLine.variable("fontSize");
        int fontSize = Integer.parseInt(fontSizeStr);
        String fontName = CommandLine.variable("font");
        Font font = new Font(fontName, Font.PLAIN, fontSize);
        UIManager.put("List.font", font);
        UIManager.put("Label.font", font);
        UIManager.put("TextField.font", font);
        UIManager.put("TextArea.font", font);
        UIManager.put("ComboBox.font", font);
        UIManager.put("Table.font", font);
        UIManager.put("TableHeader.font", font);
        UIManager.put("Button.font", font);
        UIManager.put("TabbedPane.font", font);
        UIManager.put("ToolTip.font", font);

        // background
        Color c = UIManager.getColor("Panel.background");
        UIManager.put("TextArea.background", c);
        UIManager.put("Table.background", c);
        UIManager.put("TabbedPane.background", c);
        UIManager.put("ScrollPane.background", c);

        // foreground
        UIManager.put("Label.foreground", Color.black);

        _statusBar = new JLoggedStatusBar();

        // create first tab panel
        _fieldSelector = new FieldSelector();

        if (_configDb == null)
        {
            _appContext = SubAppContext.create(_statusBar.printStream());
        }
        else
        {
            _appContext = SubAppContext.create(_statusBar.printStream(), _configDb);
        }

        _appContext.setAutoDictionaryDownload();
        // create second tab panel
        _quoteListDisplay = new QuoteListDisplay(_appContext, _statusBar, _fieldSelector);

        if (_appContext.getFieldDictionary() == null)
        {
            _statusBar.setStatus("Waiting for dictionary");
        }
        String serviceName = CommandLine.variable("serviceName");
        _appContext.addNewService(serviceName);
        setSize(new Dimension(600, 450));
        getContentPane().add(_quoteListDisplay.component(), BorderLayout.CENTER);

        _appContext.setAutoDictionaryDownload();
        _appContext.setCompletionClient(this);
        _appContext.runAwt();
    }

    /*
     * @see
     * com.reuters.rfa.example.framework.sub.SubAppContextClient#processComplete
     * ()
     */
    public void processComplete()
    {
        _statusBar.setStatus("Directory and Dictionary received");
        Vector<String> fields = new Vector<String>();
        _quoteListDisplay.setService(CommandLine.variable("serviceName"));
        String fieldsstr = CommandLine.variable("fieldNames");
        StringTokenizer st = new StringTokenizer(fieldsstr, ",");
        while (st.hasMoreTokens())
        {
            fields.add(st.nextToken().trim());
        }

        if (fields.size() != 0)
        {
            int size = fields.size();
            String[] fieldNames = new String[size];
            for (int i = 0; i < size; i++)
                fieldNames[i] = (String)fields.elementAt(i);

            _fieldSelector.selectFields(fieldNames);

            String symbolstr = CommandLine.variable("itemNames");
            st = new StringTokenizer(symbolstr, ",");
            while (st.hasMoreTokens())
            {
                String ric = st.nextToken().trim();
                if (!ric.equals(""))
                    _quoteListDisplay.addRecord(ric);
            }
        }
        _quoteListDisplay.enableDisplay(true);
    }

    /**
     * Initialize and set the default for the command line options.
     * 
     * @see SubAppContext#addCommandLineOptions()
     * 
     */
    public static void addCommandLineOptions()
    {
        CommandLine.addOption("fontSize", 10, "font size");
        CommandLine.addOption("font", "Dialog", "font name");
        CommandLine.addOption("serviceName", "", "service name to request");
        CommandLine.addOption("fieldNames", "DSPLY_NAME,TRDPRC_1,NETCHNG_1,ACVOL_1",
                              "fields to display");
        CommandLine.addOption("itemNames", "", "List of items to preload separated by ','");
        SubAppContext.addCommandLineOptions();
    }

    /*
     * for Application
     */
    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);

        QuoteList qList = new QuoteList();
        qList.initFrame();
    }

    /**
     * Clean up the connection whenever the Applet is stopped (or goes
     * "off-page" in a browser). The service's connection to its data provider
     * is dropped, thus cleaning up valuable browser resources.
     */
    public void cleanup()
    {
        if (_quoteListDisplay != null)
            _quoteListDisplay.disable();
        _appContext.cleanup();
    }

    // Static data
    protected static String AppId = "QuoteList";

    protected SubAppContext _appContext;
    protected JLoggedStatusBar _statusBar;
    protected FieldSelector _fieldSelector;
    protected QuoteListDisplay _quoteListDisplay;
    protected ConfigDb _configDb;

}
