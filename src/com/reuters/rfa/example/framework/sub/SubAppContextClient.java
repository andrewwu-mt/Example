package com.reuters.rfa.example.framework.sub;

/**
 * SubAppContextClient provides the callback mechanism for SubAppContext to
 * notify its client that a service exists and a dictionary is available..
 * 
 * @see SubAppContext
 */
public interface SubAppContextClient
{
    /**
     * The callback method
     */
    void processComplete();
}
