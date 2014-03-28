package com.reuters.rfa.example.omm.domainServer;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.example.framework.prov.AbstractProvDomainMgr;
import com.reuters.rfa.example.framework.prov.ClientSessionMgr;
import com.reuters.rfa.example.framework.prov.PubAppContext;
import com.reuters.rfa.example.framework.prov.ServiceInfo;
import com.reuters.rfa.example.framework.prov.StreamItem;
import com.reuters.rfa.omm.OMMMsg;

/**
 * RDMProvDomainMgr is a provider which provides data
 * for each Reuters Domain Model.
 * 
 * This class is responsible for creating stream items and processing request
 * message from clients. The response data is depend on the type of
 * DataGenerator for each Reuters Domain Model.
 * 
 * @see com.reuters.rfa.example.omm.domainServer.DataGenerator
 * @see com.reuters.rfa.example.omm.domainServer.DataStreamItem
 */
public class RDMProvDomainMgr extends AbstractProvDomainMgr
{

    Map<Token, StreamItem> _pendingStreamItems = new Hashtable<Token, StreamItem>();
    Client _timerTask;
    Handle _timerHandle;
    int _updateInterval;
    boolean _updating;
    DataGenerator _dataGenerator;

    public RDMProvDomainMgr(PubAppContext context, DataGenerator dataGenerator,
            short messageModelType, String serviceName, int updateInterval)
    {

        super(context, messageModelType, serviceName);
        _updateInterval = updateInterval;
        _dataGenerator = dataGenerator;
        _pubContext.addDomainMgr(this);

        _timerTask = new Client()
        {
            public void processEvent(Event event)
            {
                sendUpdates();
            }
        };
    }

    public void addDictionariesUsed(ServiceInfo si)
    {
        si.addDictionaryUsed("RWFFld");
        si.addDictionaryUsed("RWFEnum");
    }

    public StreamItem createStreamItem(Token token, OMMMsg msg)
    {

        StreamItem si = (StreamItem)_pendingStreamItems.get(token);
        if (si == null)
        {
            si = _dataGenerator.createStreamItem(this, token, msg);
            synchronized (this)
            {
                _pendingStreamItems.put(token, si);
            }
        }
        return si;
    }

    public synchronized void processReqMsg(ClientSessionMgr clientSessionMgr, Token token,
            OMMMsg msg)
    {

        Map<Token, StreamItem> clientSessionStreams = clientSessionMgr.getClientSessionStreams();
        StreamItem streamItem = (StreamItem)clientSessionStreams.get(token);

        if (streamItem == null)
        {
            streamItem = createStreamItem(token, msg);
            if (msg.getMsgType() == OMMMsg.MsgType.REQUEST
                    && !msg.isSet(OMMMsg.Indication.NONSTREAMING))
            {
                clientSessionStreams.put(token, streamItem);
            }
        }

        if  (msg.isSet(OMMMsg.Indication.REFRESH))
            ((DataStreamItem)streamItem).sendRefresh(msg.isSet(OMMMsg.Indication.REFRESH));

        if (_updateInterval > 0 && !_updating && (!msg.isSet(OMMMsg.Indication.NONSTREAMING)))
        {
            startUpdating();
            _updating = true;
        }
    }

    public synchronized void processReReqMsg(ClientSessionMgr clientSessionMgr, Token token,
            StreamItem si, OMMMsg msg)
    {
        ((DataStreamItem)si).sendRefresh(msg.isSet(OMMMsg.Indication.REFRESH));
    }

    public synchronized void processCloseReqMsg(ClientSessionMgr clientSessionMgr, Token token,
            StreamItem si)
    {
        DataStreamItem streamItem = (DataStreamItem)_pendingStreamItems.remove(token);
        if (streamItem != null && !streamItem.isClosed())
        {
            streamItem.close();
        }
    }

    /**
     * Starts sending update data for all pending streamitems.
     * 
     */
    public void startUpdating()
    {
        _timerHandle = _pubContext.registerTimerTask(_timerTask, _updateInterval * 1000, true);
    }

    /**
     * Stops sending update data for all pending streamitems.
     * 
     */
    public synchronized void stopUpdating()
    {
        _pubContext.unregisterTimer(_timerHandle);
    }

    /*
     * Sends update data for all pending streamitems.
     */
    private synchronized void sendUpdates()
    {

        _dataGenerator.generateUpdatedEntries();

        Set<Token> deletedTokens = new HashSet<Token>();

        Iterator<Token> iter = _pendingStreamItems.keySet().iterator();
        while (iter.hasNext())
        {
            Token rq = (Token)(iter.next());
            DataStreamItem streamItem = (DataStreamItem)_pendingStreamItems.get(rq);
            if (streamItem == null)
            {
                continue;
            }
            if (streamItem.isClosed() || streamItem.sendUpdate())
            {
                deletedTokens.add(rq);
            }
        }

        iter = deletedTokens.iterator();
        while (iter.hasNext())
        {
            Token tk = (Token)iter.next();
            DataStreamItem streamItem = (DataStreamItem)_pendingStreamItems.remove(tk);
            if (streamItem != null && !streamItem.isClosed())
            {
                streamItem.close();
            }
        }
    }
}
