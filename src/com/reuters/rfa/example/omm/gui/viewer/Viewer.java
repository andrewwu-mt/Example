package com.reuters.rfa.example.omm.gui.viewer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.UIManager;

import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.utility.CommandLine;

/**
 * Main class for the Viewer GUI example. This class is responsible for:
 * <ul>
 * <li>Declaring the {@linkplain com.reuters.rfa.example.utility.CommandLine
 * command line options}
 * <li>Creating the {@link com.reuters.rfa.example.framework.sub.SubAppContext
 * SubAppContext}
 * <li>Creating the {@link ViewerPanel}
 * </ul>
 * 
 */
public class Viewer
{
    public void init()
    {
        _appContext = SubAppContext.create(System.out);
        _appContext.setAutoDictionaryDownload();
        initGUI();
    }

    protected void initGUI()
    {
        // initLookAndFeel
        // font
        int fontSize = CommandLine.intVariable("fontSize");
        String fontName = CommandLine.variable("font");
        Font font = new Font(fontName, Font.PLAIN, fontSize);
        UIManager.put("List.font", font);
        UIManager.put("Label.font", font);
        UIManager.put("TextField.font", font);
        UIManager.put("TextArea.font", font);
        UIManager.put("ComboBox.font", font);
        UIManager.put("CheckBox.font", font);
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

        // create second tab panel
        _display = new ViewerPanel(_appContext);
        _appContext.setCompletionClient(_display);

        final JFrame appFrame = new JFrame(getClass().toString());
        appFrame.getContentPane().add("Center", _display.component());

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
        int dim = fontSize * 36;
        appFrame.setSize(new Dimension(dim * 2, dim));
        appFrame.setVisible(true);

    }

    void run()
    {
        _appContext.runAwt();
    }

    public static void addCommandLineOptions()
    {
        CommandLine.addOption("fontSize", 10, "font size");
        CommandLine.addOption("font", "Dialog", "font name");
        CommandLine.addOption("sort", "false",
                              "support sorting with JDK 1.6 or later (does not perform well for large tables)");
        SubAppContext.addCommandLineOptions();
        CommandLine.changeDefault("type", "OMM");
        CommandLine.changeDefault("fileDictionary", true);
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);
        Viewer tableViewer = new Viewer();
        tableViewer.init();
        tableViewer.run();
    }

    /**
     * Clean up the connection whenever the Applet is stopped (or goes
     * "off-page" in a browser). The service's connection to its data provider
     * is dropped, thus cleaning up valuable browser resources.
     **/
    public void cleanup()
    {
        if (_display != null)
            _display.disable();
    }

    // Static data
    protected static String AppId = "Viewer";

    // Data
    protected SubAppContext _appContext;
    protected ViewerPanel _display;
}
