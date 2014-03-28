package com.reuters.rfa.example.framework.sub;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * MarketDataServiceInfo represents a single Market Data Service.
 */
public class MarketDataServiceInfo implements ServiceInfo
{
    String _serviceName;
    Map<Object, Object> _elementListCache;

    MarketDataServiceInfo(String serviceName)
    {
        _serviceName = serviceName;
        _elementListCache = new HashMap<Object, Object>();
    }

    public Object get(String elementName)
    {
        return null;
    }

    public String getServiceName()
    {
        return _serviceName;
    }

    public Iterator<Object> iterator()
    {
        return _elementListCache.keySet().iterator();
    }
}
