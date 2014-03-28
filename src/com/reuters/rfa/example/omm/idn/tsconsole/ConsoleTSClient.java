package com.reuters.rfa.example.omm.idn.tsconsole;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.ArrayList;

import com.reuters.rfa.example.framework.idn.RefChainTimeSeriesDefDb;
import com.reuters.rfa.example.framework.idn.TS1TimeSeries;
import com.reuters.rfa.example.framework.idn.TS1TimeSeriesClient;
import com.reuters.ts1.TS1Def;
import com.reuters.ts1.TS1Event;
import com.reuters.ts1.TS1Point;
import com.reuters.ts1.TS1Sample;
import com.reuters.ts1.TS1Series;

/**
 * The TS console client
 */
public class ConsoleTSClient implements TS1TimeSeriesClient
{
    protected ConsoleTSClient(RefChainTimeSeriesDefDb tsdb, TS1TimeSeries ts, int count)
    {
        // _timeSeries = ts;
        ts.setClient(this);
        _tsdb = tsdb;
        _count = count;
        System.out.println("Subscribing to " + ts.series().getBaseName());
    }

    public void processTimeSeriesComplete(TS1TimeSeries series)
    {
        dumpTimeSeries(series.series());
    }

    public void processTimeSeriesError(TS1TimeSeries series)
    {
        System.out.println("series could not be processed: " + series.text());
    }

    private void dumpTimeSeries(TS1Series series)
    {
        DateFormat formatter = new SimpleDateFormat("yyyy MMM dd   HH:mm");
        String[] months = (new DateFormatSymbols()).getShortMonths();

        /*
         * Iterator eventIter = series.eventIterator(); if (eventIter.hasNext())
         * System.out.println("Events"); while (eventIter.hasNext()) { TS1Event
         * event = (TS1Event) eventIter.next();
         * System.out.print(formatter.format(event.getDate().getTime()));
         * System.out.print('\t'); System.out.println(event.toString()); }
         */
        ArrayList<TS1Event> eventList = series.getEventList();
        if (eventList != null)
        {
            if (eventList.iterator().hasNext())
                System.out.println("Events");
            for (Iterator<TS1Event> iter = eventList.iterator(); iter.hasNext();)
            {
                TS1Event event = (TS1Event)iter.next();
                System.out.print(formatter.format(event.getDate().getTime()));
                System.out.print('\t');
                System.out.println(event.toString());
            }
        }

        System.out.println("Samples");
        // int factCount = series.getFactCount();
        System.out.print("DATE\t");
        for (int i = 0; i < series.getFactCount(); i++)
        {
            int fid = series.getFact(i);
            TS1Def def = _tsdb.defDb().getDef(fid);
            if (def != null)
                System.out.print(def.getLongName() + "\t");
        }
        System.out.println();
        Iterator<TS1Sample> sampleIter = series.sampleIterator();
        int k = 0;
        while (sampleIter.hasNext() && (k < _count))
        {
            TS1Sample sample = sampleIter.next();
            Calendar date = sample.getDate();
            System.out.print(date.get(Calendar.YEAR));
            System.out.print(" ");
            System.out.print(months[date.get(Calendar.MONTH)]);
            System.out.print(" ");
            System.out.print(date.get(Calendar.DAY_OF_MONTH));
            TS1Point[] points = sample.getPoints();
            for (int i = 0; i < points.length; i++)
            {
                System.out.print('\t');
                System.out.print(points[i].toString());
            }
            System.out.println();
            k++;
        }
        System.out.println("Sample count: " + k);
    }

    private RefChainTimeSeriesDefDb _tsdb;
    private int _count;
    // private TS1TimeSeries _timeSeries;
}
