package com.reuters.rfa.example.omm.domainServer;

import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.omm.OMMAttribInfo;

/**
 * ItemInfo is a class that contains all the relevant information
 * regarding an item.
 * 
 * It is used as the source of canned data for the DataStreamItem. The
 * OrderEntry created in this class depend on the type of DataGenerator.
 * 
 * @see com.reuters.rfa.example.omm.domainServer.DataStreamItem
 * @see com.reuters.rfa.example.omm.domainServer.DataGenerator
 */
public class ItemInfo
{

    String _name;
    boolean _attribInUpdates;
    int _priorityCount = 0;
    int _priorityClass = 0;

    Handle _handle;
    Token _reqToken;
    OMMAttribInfo _copiedAttribInfo;
    DataGenerator _generator;
    boolean _streamingRequest;

    private Object[] _initialEntries;
    // keep tracks of where we are (used for refresh response only)
    private int _currentPosition = 0;

    // A maximum number entries in a single message
    // (used for refresh response only)
    private int _maxNumberOfEntries;
    private boolean _isRefreshCompleted = false;
    private boolean _isFirstPart = false;

    public ItemInfo(DataGenerator itemGenrator)
    {
        this(itemGenrator, 10);
    }

    public ItemInfo(DataGenerator itemGenrator, int maxEntries)
    {
        _generator = itemGenrator;
        _maxNumberOfEntries = maxEntries;
    }

    public Object[] getNextInitialEntries()
    {

        if (_initialEntries == null || isRefreshCompleted())
        {
            _currentPosition = 0;
            _initialEntries = _generator.getInitialEntries();
            _isRefreshCompleted = false;
            _isFirstPart = true;
        }
        else
        {
            _isFirstPart = false;
        }

        int itemLeft = _initialEntries.length - _currentPosition;
        int length = _maxNumberOfEntries;

        if (itemLeft <= _maxNumberOfEntries)
        {
            _isRefreshCompleted = true;
            length = itemLeft;
        }

        Object[] objs = new Object[length];
        System.arraycopy(_initialEntries, _currentPosition, objs, 0, length);
        _currentPosition = _currentPosition + length;

        return objs;
    }

    public Object[] getNextEntries()
    {
        return _generator.getNextEntries();
    }

    public void reset()
    {
        _currentPosition = 0;
        _isFirstPart = false;
        _isRefreshCompleted = false;
    }

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

    public void setOMMAttribInfo(OMMAttribInfo copiedAttribInfo)
    {
        _copiedAttribInfo = copiedAttribInfo;
    }

    public OMMAttribInfo getOMMAttribInfo()
    {
        return _copiedAttribInfo;
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

    public void setRequestToken(Token token)
    {
        _reqToken = token;
    }

    public Token getRequestToken()
    {
        return _reqToken;
    }

    public void setStreaming(boolean streaming)
    {
        _streamingRequest = streaming;
    }

    public boolean isStreamingRequest()
    {
        return _streamingRequest;
    }

    public boolean isRefreshCompleted()
    {
        return _isRefreshCompleted;
    }

    public boolean isFirstPart()
    {
        return _isFirstPart;
    }
}
