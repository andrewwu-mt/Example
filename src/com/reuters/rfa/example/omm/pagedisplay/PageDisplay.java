package com.reuters.rfa.example.omm.pagedisplay;

import java.awt.FlowLayout;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.utility.CommandLine;

/**
 * This is a main class to start GUI application. It uses {@link PagePanel} to
 * build GUI display. It also creates
 * {@link com.reuters.rfa.example.framework.sub.SubAppContext SubAppContext} to
 * initialize RFA.
 */
public class PageDisplay
{
    protected PagePanel _panel;
    protected String _serviceName;
    protected SubAppContext _subAppContext;
    protected JTextField _symbolField;

    /**
     * Context and GUI initialization
     */
    public void init()
    {
        short rows = (short)CommandLine.intVariable("rows");
        short cols = (short)CommandLine.intVariable("cols");
        int fontSize = CommandLine.intVariable("fontSize");
        _serviceName = CommandLine.variable("serviceName");
        _panel = new PagePanel(_serviceName, rows, cols, fontSize);

        final JFrame appFrame = new JFrame("PageDisplay");
        appFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Panel toolbar = new Panel();
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT));

        _symbolField = new JTextField(10);
        toolbar.add(new JLabel("Enter Symbol:"));
        toolbar.add(_symbolField);
        _symbolField.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent ae)
            {
                if (_symbolField.getText().equals(""))
                    return;
                _panel.newSymbolEntered(_symbolField.getText());
            }
        });

        appFrame.getContentPane().add("North", toolbar);
        appFrame.getContentPane().add("Center", _panel._panel);
        appFrame.setVisible(true);
        appFrame.pack();

        try
        {
            _subAppContext = SubAppContext.createOMM(_panel._statusBar.printStream());
        }
        catch (RuntimeException rex)
        {
            System.out.println("ERROR: Could not create context. Connection type: "
                    + CommandLine.variable("type"));
            System.out.println("type must be OMM. Program exiting...");
            System.exit(1);
        }
        _panel._page._appContext = _subAppContext;
        _subAppContext.runAwt();
    }

    /**
     * Initialize and set the default for the command line options.
     * 
     * @see SubAppContext#addCommandLineOptions()
     * 
     */
    public static void addCommandLineOptions()
    {
        CommandLine.addOption("fontSize", 12, "font size");
        CommandLine.addOption("rows", 25, "rows in page");
        CommandLine.addOption("cols", 80, "columns in page");
        CommandLine.addOption("serviceName", "BRIDGE_PAGES", "service name to request");
        SubAppContext.addCommandLineOptions();
        CommandLine.changeDefault("serviceName", "BRIDGE_PAGES"); // To override default
                                                                  // value from SubAppContext
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);
        final PageDisplay pd = new PageDisplay();
        pd.init();
    }

}
