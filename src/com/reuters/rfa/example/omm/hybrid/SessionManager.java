package com.reuters.rfa.example.omm.hybrid;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.session.Session;

/**
 * Manages {@link SessionClient SessionClient}
 * 
 */
public abstract class SessionManager
{

    private final HybridDemo _parent;
    private ProviderServer _providerServer;
    
    // map between ClientSessionHandle and SessionClient
    private final Map<Handle, SessionClient> _sessionClients;
    
    private EventQueue _consumerEventQueue;
    private final Session _session;

    public ProviderServer getProviderServer()
    {
        return _providerServer;
    }

    public EventQueue getEventQueue()
    {
        return _consumerEventQueue;
    }

    public Session getSession()
    {
        return _session;
    }

    protected final String _instanceName;

    public SessionManager(HybridDemo manager)
    {

        _parent = manager;
        _sessionClients = new HashMap<Handle, SessionClient>();
        _session = _parent.getSession();
        _instanceName = "[SessionManager]";
    }

    protected abstract SessionClient createSessionClient(Handle sessionHandle);

    public void init(ProviderServer providerServer)
    {
        System.out.println(_instanceName + " Initializing");
        _providerServer = providerServer;

        _consumerEventQueue = EventQueue.create("consumerQueue");
        _parent.getEventQueueGroup().addEventQueue(_consumerEventQueue, null);

    }

    /**
     * Called by (@link ProviderServer ProviderServer} to create an relegated
     * SessionClient
     * 
     * @param sessionHandle
     */
    public SessionClient createSession(Handle sessionHandle)
    {
        System.out.println(_instanceName + " Creating new SessionClient: " + sessionHandle);

        SessionClient client = createSessionClient(sessionHandle);
        client.init();
        _sessionClients.put(sessionHandle, client);
        return client;
    }

    public void destroySession(Handle sessionHandle)
    {
        System.out.println(_instanceName + " Destroying SessionClient: " + sessionHandle);

        SessionClient client = _sessionClients.remove(sessionHandle);
        if (client != null)
        {
            client.cleanup();
        }
        else
        {
            System.out.println(_instanceName + " Unable to find SessionClient");
        }
    }

    public void cleanup()
    {
        System.out.println(_instanceName + " Cleaning up");

        _consumerEventQueue.deactivate();

        Iterator<SessionClient> iter = _sessionClients.values().iterator();
        while (iter.hasNext())
        {
            SessionClient client = (SessionClient)iter.next();
            client.cleanup();
        }

    }
}
