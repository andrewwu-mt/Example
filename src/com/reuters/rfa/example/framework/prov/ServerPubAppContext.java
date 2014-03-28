package com.reuters.rfa.example.framework.prov;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.example.framework.sub.AppContextMainLoop;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.util.StringHashMap;

/**
 * ServerPubAppContext is a simple implementation of PubAppContext. It manages a
 * client request by using ReqRouter and uses HashMap to manage client sessions.
 * It also uses OMMServer to register OMM listener, manage OMM error and perform
 * simple event processing.
 * 
 */
public class ServerPubAppContext extends PubAppContext
{
    private ReqRouter _reqRouter;
    private Map<Object, Object> _clientSessions;
    private OMMServer _server;

    ServerPubAppContext(AppContextMainLoop mainloop)
    {
        Context.initialize();

        _mainloop = mainloop;
        if (_mainloop == null)
            _mainloop = new AppContextMainLoop(null);

        _reqRouter = new ReqRouter(this);
        _clientSessions = new HashMap<Object, Object>();
        new StringHashMap();

        boolean debug = CommandLine.booleanVariable("debug");
        if (debug)
        {
            // Enable debug logging
            Logger logger = Logger.getLogger("com.reuters.rfa");
            logger.setLevel(Level.FINE);
            Handler[] handlers = logger.getHandlers();

            if (handlers.length == 0)
            {
                Handler handler = new ConsoleHandler();
                handler.setLevel(Level.FINE);
                logger.addHandler(handler);
            }

            for (int index = 0; index < handlers.length; index++)
            {
                handlers[index].setLevel(Level.FINE);
            }
        }

        _pool = OMMPool.create();
        _encoder = _pool.acquireEncoder();
        _encoder.initialize(OMMTypes.MSG, 6144);
    }

    public void init()
    {
        super.init();
        _server = new OMMServer(this);
        _server.init();
    }

    public void cleanup()
    {
        _server.cleanup();
        super.cleanup();
    }

    /**
     * Retuens ReqRouter for routing client request.
     * 
     * @return ReqRouter for routing client request.
     */
    public ReqRouter getReqRouter()
    {
        return _reqRouter;
    }

    /**
     * Returns Map for managing all client sessions
     * 
     * @return Map for managing all client sessions
     */
    public Map<Object, Object> getClientSessions()
    {
        return _clientSessions;
    }

    public void addDomainMgr(ProvDomainMgr mgr)
    {
        if (mgr instanceof AbstractProvDomainMgr)
        {
            // mgr.getMsgModelType() != RDMMsgTypes.DIRECTORY &&
            // mgr.getMsgModelType() != RDMMsgTypes.LOGIN)
            super.addDomainMgr(mgr);
        }
        _reqRouter.addDomainMgr(mgr);
    }

    public ProvDomainMgr getDomainMgr(short msgModelType)
    {
        return _reqRouter.getDomainMgr(msgModelType);
    }
}
