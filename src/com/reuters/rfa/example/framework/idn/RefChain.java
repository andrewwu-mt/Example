package com.reuters.rfa.example.framework.idn;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.example.framework.sub.NormalizedEvent;
import com.reuters.rfa.example.framework.sub.SubAppContext;

/**
 * Abstract class for processing a chain of QQ page record reference
 * information. This class is a {@link com.reuters.rfa.common.Client Client} of
 * multiple subscriptions from the sub framework. All page records are requested
 * as snapshots.
 * <p>
 * QQ reference page records include information about TS1 definitions (QQCN),
 * time zones (QQZR), index RICs (QQCR), exchanges (QQVD), currencies (QQPA),
 * and continuations & futures (QSPA).
 * <p>
 * Subclasses must override {@link #addRef(byte[])}.
 * 
 * @see com.reuters.rfa.example.framework.sub.SubAppContext
 * @see com.reuters.rfa.example.framework.idn.RefChainClient
 */
public abstract class RefChain implements Client
{
    protected RefChainClient _client;
    private String[] _rics;
    private int _pendingCount = 0;
    private SubAppContext _appContext;
    private short ROW3_64;

    public RefChain(SubAppContext context, String[] initRics)
    {
        _appContext = context;
        _rics = new String[initRics.length];
        System.arraycopy(initRics, 0, _rics, 0, initRics.length);
        ROW3_64 = _appContext.getFieldDictionary().getFidDef("ROW64_3").getFieldId(); // 217
        for (int i = 0; i < _rics.length; i++)
            request(_rics[i]);
    }

    abstract public void addRef(byte[] bytes);

    public void setClient(RefChainClient client)
    {
        _client = client;
    }

    public boolean isComplete()
    {
        return _pendingCount == 0;
    }

    private void checkNextRecord(String next)
    {
        String nextRic = next.substring(1).trim(); // strip '+'
        if (nextRic.length() == 0)
            return;
        int j;
        for (j = 0; j < _rics.length; j++)
        {
            if (_rics[j].equals(nextRic))
                break;
        }
        if (j == _rics.length)
        {
            String[] newRicList = new String[_rics.length + 1];
            System.arraycopy(_rics, 0, newRicList, 0, _rics.length);
            newRicList[_rics.length] = nextRic;
            _rics = newRicList;
            request(nextRic);
        }
    }

    private void request(String ric)
    {
        // not storing handle - snapshot will be auto cleaned up after image
        // or closed status
        _appContext.register(this, _appContext.getServiceName(), ric, false);
        _pendingCount++;
    }

    private void addRefs(NormalizedEvent nevent)
    {
        for (int i = 0; i < 9; i++)
        {
            String data = nevent.getFieldString((short)(ROW3_64 + i)).trim();
            while (data.endsWith("+"))
            {
                i++;
                data = data.substring(0, data.length() - 1).trim() + " "; // remove
                                                                          // '+'
                data = data + nevent.getFieldString((short)(ROW3_64 + i)).trim();
            }
            byte[] bytes = data.getBytes();
            if (bytes.length > 0)
                addRef(bytes);
        }
        String next = nevent.getFieldString((short)(ROW3_64 + 9)); // ROW64_12
        checkNextRecord(next);
    }

    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.MARKET_DATA_ITEM_EVENT:
            case Event.OMM_ITEM_EVENT:
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

    private void processItemEvent(Event event)
    {
        NormalizedEvent nevent = _appContext.getNormalizedEvent(event);
        int msgType = nevent.getMsgType();
        if (msgType == NormalizedEvent.REFRESH)
        {
            _pendingCount--;
            addRefs(nevent);
            if (_pendingCount == 0 && _client != null)
                _client.processRefChainComplete(this);
        }
        else if (msgType == NormalizedEvent.STATUS)
        {
            if (nevent.isClosed())
            {
                if (_client != null)
                    _client.processRefChainError(this,
                                                 nevent.getItemName() + ": " + nevent.getStatusText());
            }
        }
    }

}
