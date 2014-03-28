package com.reuters.rfa.example.omm.provni;

import com.reuters.rfa.common.Token;

/**
 * ItemInfo is a class that contains all the relevant information regarding to
 * an item. It is used as the source of canned data for the OMMProviderNIDemo.
 * 
 */
public class ItemInfo
{
    String _name;
    String _updatePrefix;

    double _trdPrice = 10.0000;
    double _bid = 9.8000;
    double _ask = 10.2000;
    long _acvol = 100;
    Token _token;

    public void setToken(Token t)
    {
        _token = t;
    }

    public Token getToken()
    {
        return _token;
    }

    public void setName(String name)
    {
        _name = name;
        _updatePrefix = String.format("Item = %s, update = ", _name);
    }

    public String getName()
    {
        return _name;
    }

    public double getTradePrice1()
    {
        return _trdPrice;
    }

    public double getBid()
    {
        return _bid;
    }

    public double getAsk()
    {
        return _ask;
    }

    public long getACVol1()
    {
        return _acvol;
    }

    public int _updateCount = 0;

    public int incrementUpdateCount()
    {
        _updateCount++;
        return _updateCount;
    }

    public String updateString()
    {
        String s = String.format("%s%d", _updatePrefix, _updateCount);
        return s;
    }

    public void increment()
    {
        if ((_trdPrice >= 100.0000) || (_bid >= 100.0000) || (_ask >= 100.0000))
        {
            // reset prices
            _trdPrice = 10.0000;
            _bid = 9.8000;
            _ask = 10.2000;
        }
        else
        {
            _trdPrice += 0.0500; // 0.0500
            _bid += 0.0500; // 0.0500
            _ask += 0.0500; // 0.0500
        }

        if (_acvol < 1000000)
            _acvol += 50;
        else
            _acvol = 100;
    }

}
