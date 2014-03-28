package com.reuters.rfa.example.framework.sub;

/**
 * DirectoryClient is a callback client that provides a handler defined by an
 * application to process related {@link ServiceInfo}
 */
public interface DirectoryClient
{
    /**
     * The callback method when a new service is discovered.
     * 
     * @param serviceInfo A new service
     */
    void processNewService(ServiceInfo serviceInfo);

    /**
     * The callback method when the service is updated.
     * 
     * @param serviceInfo A service to be updated
     */
    void processServiceUpdated(ServiceInfo serviceInfo);

    /**
     * TheA callback method when the service is removed.
     * 
     * @param serviceInfo A service to be removed
     */
    void processServiceRemoved(ServiceInfo serviceInfo);
}
