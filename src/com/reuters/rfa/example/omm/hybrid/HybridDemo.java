package com.reuters.rfa.example.omm.hybrid;

import java.util.Timer;
import java.util.TimerTask;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DeactivatedException;
import com.reuters.rfa.common.DispatchQueueInGroupException;
import com.reuters.rfa.common.EventQueueGroup;
import com.reuters.rfa.config.ConfigUtil;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.session.Session;

/**
 * Provides hybrid application framework.
 * 
 * The hybrid application framework is
 * <ul>
 * <li>Single-thread
 * <li>One {@link com.reuters.rfa.session.Session Session}
 * <li>One {@link ProviderServer ProviderServer} and {@link SessionManager
 * SessionManager}
 * </ul>
 * 
 */
public abstract class HybridDemo
{

    protected ProviderServer _providerServer;
    protected SessionManager _sessionManager;

    protected final EventQueueGroup _eventQueueGroup;

    public EventQueueGroup getEventQueueGroup()
    {
        return _eventQueueGroup;
    }

    protected Session _session;

    public Session getSession()
    {
        return _session;
    }

    protected volatile boolean _isTimeout;
    protected final Timer _timer;
    private final String _instanceName;

    protected HybridDemo(String instanceName)
    {

        _eventQueueGroup = EventQueueGroup.create("group");
        _timer = new Timer(true);
        _isTimeout = false;
        _instanceName = "[" + instanceName + "]";

    }

    protected abstract ProviderServer createProviderServer(String listenerName);

    protected abstract SessionManager createSessionManager();

    /**
     * Initialize {@link SessionManager SessionManager} and
     * {@link ProviderServer ProviderServer}
     * 
     */
    public boolean init()
    {
        System.out.println(_instanceName + " Initializing");

        String sessionName = CommandLine.variable("session");

        int runTime = CommandLine.intVariable("runTime");
        _timer.schedule(new TimerTask()
        {
            public void run()
            {
                _isTimeout = true;
            }
        }, runTime * 1000);

        Context.initialize();
        
        // prior to acquiring the session, update the provider connection
        // to use the request message type (OMMMsg.MsgType.REQUEST) rather 
        // than the deprecated request message types (see OMMMsg.MsgType).
        ConfigUtil.useDeprecatedRequestMsgs(sessionName, false);
        
        _session = Session.acquire(sessionName);

        if (_session == null)
        {
            System.out.println("Initialization failed!");
            return false;
        }
        System.out.println("RFA Version: " + Context.getRFAVersionInfo().getProductVersion());

        _sessionManager = createSessionManager();
        _providerServer = createProviderServer(null);
        _sessionManager.init(_providerServer);
        _providerServer.init(_sessionManager);

        return true;
    }

    /**
     * 
     * Cleanup {@link SessionManager SessionManager} and {@link ProviderServer
     * ProviderServer}
     * 
     */
    public void cleanup()
    {
        System.out.println(_instanceName + " Cleaning up");

        _eventQueueGroup.deactivate();

        // in case, the session cannot be acquired
        if (_providerServer != null)
            _providerServer.cleanup();

        if (_sessionManager != null)
            _sessionManager.cleanup();

        if (_session != null)
            _session.release();

        Context.uninitialize();
    }

    /**
     * 
     * Dispatch events from the queue
     * 
     */
    public void run()
    {
        while (!_isTimeout)
        {
            try
            {
                _eventQueueGroup.dispatch(1000);
            }
            catch (DeactivatedException e)
            {
                e.printStackTrace();
            }
            catch (DispatchQueueInGroupException e)
            {
                e.printStackTrace();
            }
        }
    }

}
