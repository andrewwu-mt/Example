package com.reuters.rfa.example.omm.domainServer;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.example.framework.prov.ProvDomainMgr;
import com.reuters.rfa.example.framework.prov.StreamItem;

/**
 * DataStreamItem is abstract class for item streams which be used to send
 * refresh and update data to a client.
 * 
 */
public abstract class DataStreamItem extends StreamItem
{

    protected ProvDomainMgr _mgr;
    protected Token _token;
    private boolean _encodeDataDef;

    /**
     * Sends the first response data.
     * 
     * @param solicited boolean representing if the refresh is
     * solicited (or unsolicited).
     * 
     */
    public abstract void sendRefresh(boolean solicited);

    /**
     * Sends the data that be changed.
     * 
     * @return <code>true</code> if this is the last update data;
     *         <code>false</code> otherwise.
     */
    public abstract boolean sendUpdate();

    public DataStreamItem(ProvDomainMgr mgr, Token token)
    {
        _mgr = mgr;
        _token = token;
    }

    public void close()
    {
        _token = null;
    }

    /**
     * Returns <code>true</code> if this stream is closed.
     * 
     * @return <code>true</code> if this stream is closed.
     */
    public boolean isClosed()
    {
        return (_token == null) ? true : false;
    }

    public short getMsgModelType()
    {
        return _mgr.getMsgModelType();
    }

    /**
     * Returns <code>true</code> if DataDefinitions is encoded in a refresh
     * message.
     * 
     * @return <code>true</code> if DataDefinitions is encoded in a refresh
     *         message; <code>false</code> otherwise.
     */
    public boolean isEncodeDataDef()
    {
        return _encodeDataDef;
    }

    /**
     * Sets whether or not DataDefinitions is encoded in a refresh message.
     * 
     * @param encodeDataDef
     */
    public void setEncodeDataDef(boolean encodeDataDef)
    {
        _encodeDataDef = encodeDataDef;
    }
}
