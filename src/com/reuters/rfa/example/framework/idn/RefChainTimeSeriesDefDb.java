package com.reuters.rfa.example.framework.idn;

import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.ts1.TS1DefDb;

/**
 * Implementation of RefChain request data from RFA and populate a
 * {@link com.reuters.ts1.TS1DefDb TS1DefDb} using the sub framework.
 * 
 * @see com.reuters.ts1.TS1DefDb
 * @see com.reuters.rfa.example.framework.sub.SubAppContext
 */
public class RefChainTimeSeriesDefDb extends RefChain
{
    TS1DefDb _defDb;

    public RefChainTimeSeriesDefDb(SubAppContext appContext)
    {
        super(appContext, TS1DefDb.getTs1DbRics());
        _defDb = new TS1DefDb();
    }

    public TS1DefDb defDb()
    {
        return _defDb;
    }

    public void addRef(byte[] bytes)
    {
        _defDb.add(bytes);
    }

}
