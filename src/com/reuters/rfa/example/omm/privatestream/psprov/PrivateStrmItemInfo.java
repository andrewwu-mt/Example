package com.reuters.rfa.example.omm.privatestream.psprov;

import com.reuters.rfa.common.Handle;

/**
 * PrivateStrmItemInfo is a class that contains all the relevant information
 * regarding to an item. It is used as the source of canned data for
 * StarterProvider_PrivateStream.
 * 
 */
public class PrivateStrmItemInfo
{
    String _name;

    double _trdPrice = 10.0000;
    double _bid = 9.8000;
    double _ask = 10.2000;
    long _acvol = 100;

    boolean _isPaused = false;
    boolean _attribInUpdates = false;
    int _priorityCount = 0;
    int _priorityClass = 0;
    Handle _handle;

    public void setName(String name)
    {
        _name = name;
    }

    public String getName()
    {
        return _name;
    }

    public void setAttribInUpdates(boolean b)
    {
        _attribInUpdates = b;
    }

    public boolean getAttribInUpdates()
    {
        return _attribInUpdates;
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

    public int getPriorityCount()
    {
        return _priorityCount;
    }

    public void setPriorityCount(int priorityCount)
    {
        _priorityCount = priorityCount;
    }

    public int getPriortyClass()
    {
        return _priorityClass;
    }

    public void setPriorityClass(int priorityClass)
    {
        _priorityClass = priorityClass;
    }

    public void setHandle(Handle handle)
    {
        _handle = handle;
    }

    public Handle getHandle()
    {
        return _handle;
    }

    public void setPaused(boolean pause)
    {
        _isPaused = pause;
    }

    public boolean isPaused()
    {
        return _isPaused;
    }
}
