package com.reuters.rfa.example.framework.sub;

import java.util.Iterator;

/**
 * ServiceInfo is an interface that represents a generic service.
 */
public interface ServiceInfo
{
    /**
     * @return a name of this service
     */
    public String getServiceName();

    /**
     * @param key
     * @return a value for this key or null if the key does not exist
     */
    public Object get(String key);

    /**
     * @return iterator for attributes stored for this ServiceInfo
     */
    public Iterator<?> iterator();
}
