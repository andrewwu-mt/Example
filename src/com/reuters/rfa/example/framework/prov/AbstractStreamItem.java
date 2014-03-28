package com.reuters.rfa.example.framework.prov;

import com.reuters.rfa.common.Token;

/**
 * AbstractStreamItem is abstract class for item streams which be used to send
 * data to a client.
 * 
 */
public abstract class AbstractStreamItem extends StreamItem
{
    /**
     * The domain manager that this stream is associated with.
     * 
     */
    protected ProvDomainMgr _mgr;

    /**
     * The token that is associated with the request for this streams. This
     * token is used for sending response.
     * 
     */
    protected Token _token;

    public AbstractStreamItem(ProvDomainMgr mgr, Token token)
    {
        _mgr = mgr;
        _token = token;
    }

    public void close()
    {
        _token = null;
    }

    public short getMsgModelType()
    {
        return _mgr.getMsgModelType();
    }
}
