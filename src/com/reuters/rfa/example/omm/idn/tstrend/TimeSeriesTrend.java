package com.reuters.rfa.example.omm.idn.tstrend;

import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.utility.CommandLine;

/**
 * This is a main class to run TimeSeriesTrend applicaiton which draws a
 * graphical representation of the data within a time series. The date is
 * represented along the X axis while the value of a given field is represented
 * on along the Y axis.
 * 
 */
public class TimeSeriesTrend extends Applet
{
    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;

    // Standard Applet Methods
    /**
     * This method is used to perform Applet initialization. Applet parameters
     * are obtained and GUI initialization is performed.
     */
    public void init()
    {
        _appContext = SubAppContext.createOMM(System.out);
        _serviceName = CommandLine.variable("serviceName");
        initGUI();
    }

    /**
     * Disable the TimeSeriesService whenever the Applet is stopped (or goes
     * " off-page" in a browser). The service's connection to its data provider
     * is dropped, thus cleaning up valuable browser resources.
     */
    public void stop()
    {
        _panel.cleanup();
        _appContext.cleanup();
    }

    // Other Initialization
    protected void initGUI()
    {
        // Set up the GUI components.
        //
        setLayout(new BorderLayout());
        setBackground(Color.lightGray);
        _panel = new TSTrendPanel(_appContext);
        add("Center", _panel);
    }

    public void run()
    {
        _appContext.runAwt();
    }

    // Attributes
    public Dimension getPreferredSize()
    {
        return new Dimension(750, 600);
    }

    public static void addCommandLineOptions()
    {
        SubAppContext.addCommandLineOptions();
        CommandLine.changeDefault("serviceName", "IDN_RDF");
    }

    public static void main(String[] argv)
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);
        final TimeSeriesTrend tst = new TimeSeriesTrend();
        tst.init();

        // This frame is needed to run the Applet as an application.
        //
        final Frame appFrame = new Frame("TimeSeriesTrend");
        appFrame.addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent we)
            {
                tst._active = false;
                tst.stop();
                appFrame.setVisible(false);
                appFrame.dispose();
                System.exit(0);
            }
        });
        appFrame.add("Center", tst);
        appFrame.setVisible(true);
        appFrame.pack();
        tst.start();
        tst.run();
    }

    volatile boolean _active;
    protected String _serviceName;
    SubAppContext _appContext;
    protected TSTrendPanel _panel;

}
