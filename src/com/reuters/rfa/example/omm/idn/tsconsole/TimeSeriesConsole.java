package com.reuters.rfa.example.omm.idn.tsconsole;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import com.reuters.rfa.example.framework.idn.RefChainTimeSeriesDefDb;
import com.reuters.rfa.example.framework.idn.TS1TimeSeries;
import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.framework.sub.SubAppContextClient;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.ts1.TS1Constants;

/**
 * The TS console application
 */
public class TimeSeriesConsole implements SubAppContextClient
{
    // Configuration
    protected String _serviceName;
    protected LinkedList<String> _itemNames;
    protected SubAppContext _appContext;
    protected RefChainTimeSeriesDefDb _tsdb;

    public TimeSeriesConsole()
    {
        // Read options from the command line
        _serviceName = CommandLine.variable("serviceName");
        String itemNames = CommandLine.variable("itemName");
        StringTokenizer st = new StringTokenizer(itemNames, ",");
        _itemNames = new LinkedList<String>();
        while (st.hasMoreTokens())
            _itemNames.add(st.nextToken().trim());

        _appContext = SubAppContext.createOMM(System.out);
        _appContext.setAutoDictionaryDownload();
        _appContext.setCompletionClient(this);
    }

    public void processComplete()
    {
        _tsdb = new RefChainTimeSeriesDefDb(_appContext);
        int count = CommandLine.intVariable("count");
        if (count <= 0)
            count = Integer.MAX_VALUE;
        Iterator<String> iter = _itemNames.iterator();
        while (iter.hasNext())
        {
            String itemName = (String)iter.next();
            // Create an object to receive event callbacks
            TS1TimeSeries timeseries = new TS1TimeSeries(_appContext, itemName,
                    TS1Constants.DAILY_PERIOD, count);
            new ConsoleTSClient(_tsdb, timeseries, count);
        }
    }

    public void run()
    {
        _appContext.run();
    }

    public void cleanup()
    {
        _appContext.cleanup();
    }

    public static void addCommandLineOptions()
    {
        SubAppContext.addCommandLineOptions();
        CommandLine.changeDefault("serviceName", "IDN_RDF");
        CommandLine.addOption("itemName", "JPY=", "item name to request");
        CommandLine.addOption("count", "-1", "count");
    }

    public static void main(String argv[])
    {
        addCommandLineOptions();
        CommandLine.setArguments(argv);
        TimeSeriesConsole demo = new TimeSeriesConsole();
        demo.run();
        demo.cleanup();
    }

}
