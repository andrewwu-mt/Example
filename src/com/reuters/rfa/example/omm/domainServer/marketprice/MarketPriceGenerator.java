package com.reuters.rfa.example.omm.domainServer.marketprice;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.example.framework.prov.ProvDomainMgr;
import com.reuters.rfa.example.omm.domainServer.DataGenerator;
import com.reuters.rfa.example.omm.domainServer.DataStreamItem;
import com.reuters.rfa.omm.OMMMsg;

/**
 * MarketPriceGenerator provides a simple algorithm to generate data for Market
 * Price domain. It can be used to create MarketPriceStreamItems.
 * 
 * <p>
 * <b>How to generate response data.</b>
 * </p>
 * 
 * The bid, ask, trade price and accumulated volume are generated and updated
 * randomly to generate more realistic data.
 * 
 * @see MarketPriceStreamItem
 */
public class MarketPriceGenerator implements DataGenerator
{

    private List<Entry> _entries;
    private int _basePrice;
    private int _priceRange;
    private Random _random;

    /**
     * Represents a single entry
     */
    public static class Entry implements Cloneable
    {
        public int trdPrice;
        public int bid;
        public int ask;
        public int acvol;

        public Object clone()
        {
            Entry newEntry = new Entry();
            newEntry.trdPrice = trdPrice;
            newEntry.bid = bid;
            newEntry.ask = ask;
            newEntry.acvol = acvol;
            return newEntry;
        }
    }

    public MarketPriceGenerator()
    {
        _entries = new ArrayList<Entry>(1);
        _random = new Random();
        _basePrice = (_random.nextInt(100) + 50) * 100;
        _priceRange = (_basePrice * 5) / 1000;

        Entry entry = new Entry();
        entry.trdPrice = _basePrice;
        entry.bid = _basePrice - _priceRange;
        entry.ask = _basePrice + _priceRange;
        entry.acvol = 100;

        _entries.add(entry);
    }

    public DataStreamItem createStreamItem(ProvDomainMgr mgr, Token token, OMMMsg msg)
    {
        MarketPriceStreamItem streamItem = new MarketPriceStreamItem(this, mgr, token, msg);
        streamItem.setEncodeDataDef(false);
        return streamItem;
    }

    public Object[] getInitialEntries()
    {
        return _entries.toArray();
    }

    public Object[] getNextEntries()
    {
        return _entries.toArray();
    }

    public void generateUpdatedEntries()
    {
        Entry entry = (Entry)_entries.remove(0);

        if ((entry.trdPrice >= 20000) || (entry.trdPrice <= 4000))
        {
            // reset prices
            entry.trdPrice = _basePrice;
            entry.bid = _basePrice - _priceRange;
            entry.ask = _basePrice + _priceRange;
        }
        else
        {
            int action = _random.nextInt(100);
            if (action >= 40)
            {
                entry.trdPrice += _priceRange;
                entry.bid += _priceRange;
                entry.ask += _priceRange;
            }
            else
            {
                entry.trdPrice -= _priceRange;
                entry.bid -= _priceRange;
                entry.ask -= _priceRange;
            }
        }

        if (entry.acvol < 2000)
            entry.acvol += _random.nextInt(100);
        else
            entry.acvol = 100;

        _entries.add(entry);
    }
}
