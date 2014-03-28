package com.reuters.rfa.example.framework.idn;

/**
 * Client interface for {@link TS1TimeSeries} callbacks.
 * 
 */
public interface TS1TimeSeriesClient
{
    void processTimeSeriesComplete(TS1TimeSeries series);

    void processTimeSeriesError(TS1TimeSeries series);

}
