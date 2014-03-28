package com.reuters.rfa.example.omm.hybrid;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.session.omm.OMMActiveClientSessionEvent;
import com.reuters.rfa.session.omm.OMMClientSessionIntSpec;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;
import com.reuters.rfa.session.omm.OMMErrorStatus;
import com.reuters.rfa.session.omm.OMMItemCmd;
import com.reuters.rfa.session.omm.OMMListenerEvent;
import com.reuters.rfa.session.omm.OMMListenerIntSpec;
import com.reuters.rfa.session.omm.OMMProvider;

/**
 * Encapsulates {@link com.reuters.rfa.session.omm.OMMProvider OMMProvider}.
 * Once client connects to the ProviderServer, it creates a
 * {@link SessionClient SessionClient} to handle this client.
 * 
 */
public class ProviderServer implements Client
{

    private final HybridDemo _parent;
    private OMMProvider _provider;
    private EventQueue _eventQueue;
    private Handle _listenerHandle;
    private Handle _errorHandle;
    private final String _listenerName;

    private final OMMItemCmd _cmd;
    private SessionManager _sessionManager;

    private boolean _reencode;
    private final String _instanceName;

    public EventQueue getEventQueue()
    {
        return _eventQueue;
    }

    public ProviderServer(HybridDemo manager, String listenerName)
    {
        _parent = manager;

        _cmd = new OMMItemCmd();
        _listenerName = listenerName;

        _reencode = CommandLine.booleanVariable("useReencoder");
        _instanceName = "[ProviderServer #" + manager.getSession().getName() + "]";
    }

    public void init(SessionManager client)
    {
        System.out.println(_instanceName + " Initializing");
        _sessionManager = client;

        _eventQueue = EventQueue.create("providerQueue");
        _parent.getEventQueueGroup().addEventQueue(_eventQueue, null);

        _provider = (OMMProvider)_parent.getSession().createEventSource(EventSource.OMM_PROVIDER,
                                                                        "provider");

        OMMListenerIntSpec listenerIntSpec = new OMMListenerIntSpec();

        listenerIntSpec.setListenerName(_listenerName);
        _listenerHandle = _provider.registerClient(_eventQueue, listenerIntSpec, this, null);

        OMMErrorIntSpec errIntSpec = new OMMErrorIntSpec();
        _errorHandle = _provider.registerClient(_eventQueue, errIntSpec, this, null);

        System.out.println(_instanceName + " Waiting for clients...");
    }

    public void cleanup()
    {
        System.out.println(_instanceName + " Cleaning up");

        _provider.unregisterClient(_errorHandle);
        _provider.unregisterClient(_listenerHandle);

        _provider.destroy();

    }

    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_ACTIVE_CLIENT_SESSION_PUB_EVENT:
                processOMMActiveClientSessionEvent((OMMActiveClientSessionEvent)event);
                break;
            case Event.OMM_LISTENER_EVENT:
                processListenerEvent((OMMListenerEvent)event);
                break;
            case Event.OMM_CMD_ERROR_EVENT:
                processOMMCmdErrorEvent((OMMCmdErrorEvent)event);
                break;
            default:
                break;
        }
    }

    private void processListenerEvent(OMMListenerEvent event)
    {
        System.out.println(_instanceName + " Received OMMListenerEvent");
        System.err.println(event);
    }

    private void processOMMCmdErrorEvent(OMMCmdErrorEvent event)
    {
        System.out.println(_instanceName + " Received OMMCmdErrorEvent");
        OMMErrorStatus status = event.getStatus();
        System.err.println(_instanceName + " Error: " + status.getStatusText());
    }

    private void processOMMActiveClientSessionEvent(OMMActiveClientSessionEvent event)
    {
        System.out.println(_instanceName + " Received OMMActiveClientSessionEvent");

        Handle sessionHandle = event.getClientSessionHandle();
        OMMClientSessionIntSpec intSpec = new OMMClientSessionIntSpec();
        intSpec.setClientSessionHandle(sessionHandle);

        // create a new SessionClient to handle subsequent requests
        SessionClient client = _sessionManager.createSession(sessionHandle);

        _provider.registerClient(_eventQueue, intSpec, client, null);
    }

    /**
     * Submit response to the client
     * 
     * @param msg
     * @param token
     * @param encoderSize
     */
    public void submitResp(OMMMsg msg, Token token, int encoderSize)
    {
        OMMMsg respMsg;
        if (_reencode)
        {
            respMsg = OMMMsgReencoder.getEncodeMsgfrom(msg, encoderSize);
        }
        else
        {
            respMsg = msg;
        }

        _cmd.setMsg(respMsg);
        _cmd.setToken(token);

        int ret = _provider.submit(_cmd, null);
        if (ret == 0)
            System.err.println("Trying to submit for an item with an inactive handle.");
    }

}
