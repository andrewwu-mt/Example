package com.reuters.rfa.example.framework.idn;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.framework.sub.NormalizedEvent;
import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.ts1.TS1Exception;
import com.reuters.ts1.TS1Series;

/**
 * Requests TS1 data from the RFA session layer and parses it with the TS1
 * decoder. This class is a {@link com.reuters.rfa.common.Client Client} and
 * requests data from the sub framework. It works with both OMM and MarketData
 * interfaces. All TS1 records are requested as snapshots.
 * 
 * @see com.reuters.ts1.TS1Series
 * @see com.reuters.rfa.example.framework.idn.TS1TimeSeriesClient
 * @see com.reuters.rfa.example.framework.sub.SubAppContext
 */
public class TS1TimeSeries implements Client
{
    private final SubAppContext _appContext;
    Handle _currentHandle;
    boolean _first = true;
    byte[] _permissionData;
    int _pendingCount = 0;
    TS1Series _series;
    TS1TimeSeriesClient _client;
    int _period;
    int _numberOfSamples;
    String _text = "";

    /**
     * @param appContext Application context from the Sub Framwork
     * @param itemName base RIC name of instrument
     * @param period {@link com.reuters.ts1.TS1Constants#DAILY_PERIOD
     *            TS1Constants.DAILY_PERIOD},
     *            {@link com.reuters.ts1.TS1Constants#WEEKLY_PERIOD
     *            TS1Constants.WEEKLY_PERIOD}, or
     *            {@link com.reuters.ts1.TS1Constants#MONTHLY_PERIOD
     *            TS1Constants.MONTHLY_PERIOD}
     * @param numberOfSamples Will get at least this many samples, if they
     *            exist. May get more
     */
    public TS1TimeSeries(SubAppContext appContext, String itemName, int period, int numberOfSamples)
    {
        _period = period;
        _series = TS1Series.createSeries(itemName, period);
        _appContext = appContext;
        _numberOfSamples = numberOfSamples; // will get at least this many
                                            // samples, if they exist. May get
                                            // more
        request(_series.getPrimaryRic());
    }

    public int numberOfSamples()
    {
        return _numberOfSamples;
    }

    public TS1Series series()
    {
        return _series;
    }

    public void setClient(TS1TimeSeriesClient client)
    {
        _client = client;
    }

    public void cancelPendingRequest()
    {
        if (_currentHandle == null)
            return;
        _appContext.unregister(_currentHandle);
        _currentHandle = null;
    }

    private void request(String ric)
    {
        // not storing handle - snapshot will be auto cleaned up
        // after image or closed status
        _currentHandle = _appContext.register(this, _appContext.getServiceName(), ric, false);
        _pendingCount++;
    }

    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_ITEM_EVENT:
            case Event.MARKET_DATA_ITEM_EVENT:
                processItemEvent(event);
                break;
            case Event.COMPLETION_EVENT:
                System.out.println("Received COMPLETION_EVENT for handle " + event.getHandle());
                break;
            default:
                System.out.println("Unhandled event type: " + event.getType());
                break;
        }
    }

    protected void processItemEvent(Event event)
    {
        NormalizedEvent nevent = _appContext.getNormalizedEvent(event);
        int msgType = nevent.getMsgType();
        _text = nevent.getStatusText();
        if (msgType == NormalizedEvent.REFRESH)
        {
            _pendingCount--;
            _currentHandle = null;
            storePermissionData(nevent);

            byte[] rowbytes = getBytes(nevent);
            String itemName = nevent.getItemName();
            processTimeSeries(itemName, rowbytes);
        }
        else if (msgType == NormalizedEvent.STATUS)
        {
            if (_client != null)
            {
                if (nevent.isClosed())
                {
                    _client.processTimeSeriesError(this);
                }
            }
        }

    }

    protected void storePermissionData(NormalizedEvent nevent)
    {
        if (_permissionData != null)
            return; // lock could be combined using DACS Lock API
        byte[] permData = nevent.getPermissionData();
        if (permData == null)
            return;
        _permissionData = new byte[permData.length];
        System.arraycopy(permData, 0, _permissionData, 0, permData.length);
    }

    private void processTimeSeries(String itemName, byte[] rowbytes)
    {
        try
        {
            _series.decode(itemName, rowbytes);
            if (_first)
            {
                _first = false;
                String[] rics = _series.getRics();
                int[] sampleCounts = _series.getSampleCounts();
                int sampleCount = sampleCounts[0];
                // skip primary record
                for (int i = 1; i < rics.length && sampleCount < _numberOfSamples; i++)
                {
                    sampleCount += sampleCounts[i];
                    String ric = rics[i];
                    request(ric);
                }
            }
            if (isComplete())
            {
                _numberOfSamples = Math.min(_numberOfSamples, _series.getNumberOfSamples());
                if (_client != null)
                    _client.processTimeSeriesComplete(this);
            }
        }
        catch (TS1Exception e)
        {
            e.printStackTrace();
        }
    }

    public byte[] permissionData()
    {
        return _permissionData;
    }

    public boolean isComplete()
    {
        return _pendingCount == 0;
    }

    private byte[] getBytes(NormalizedEvent nevent)
    {
        byte[] rowbytes = new byte[64 * 14];
        final short fidROW1_64 = _appContext.getFieldDictionary().getFidDef("ROW64_1").getFieldId(); // 215
        int offset = 0;
        for (short i = 0; i < 14; i++)
        {
            nevent.getFieldBytes((short)(fidROW1_64 + i), rowbytes, offset);
            offset += 64;
        }
        return rowbytes;
    }

    public String text()
    {
        return _text;
    }
}
