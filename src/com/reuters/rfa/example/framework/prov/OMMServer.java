package com.reuters.rfa.example.framework.prov;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.session.omm.OMMActiveClientSessionEvent;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;
import com.reuters.rfa.session.omm.OMMInactiveClientSessionCmd;
import com.reuters.rfa.session.omm.OMMListenerIntSpec;

/**
 * OMMServer provides basic functionalities used before public application is
 * ready to publish OMM data.
 * 
 * <p>
 * <b>This class is responsible for:</b>
 * </p>
 * <ul>
 * <li>OMM listener and OMM error event registration.
 * <li>Basic event processing.
 * <li>Incoming client session management.
 * <li>Unregistration.
 * </ul>
 * 
 */
public class OMMServer implements Client
{
    ServerPubAppContext _appContext;

    public OMMServer(ServerPubAppContext context)
    {
        _appContext = context;
    }

    /**
     * Intialize the resources by registering OMMListenerIntSpec and
     * OMMErrorIntSpec
     * 
     * @see OMMListenerIntSpec
     * @see OMMErrorIntSpec
     */
    public void init()
    {
        OMMListenerIntSpec listenerIntSpec = new OMMListenerIntSpec();

        // listener name is the name of the prov connection you listen on.
        String connection = CommandLine.variable("listenenerName");
        listenerIntSpec.setListenerName(connection);
        _specHandle = _appContext.getProvider().registerClient(_appContext.getEventQueue(),
                                                               listenerIntSpec, this, null);

        OMMErrorIntSpec errIntSpec = new OMMErrorIntSpec();
        _errHandle = _appContext.getProvider().registerClient(_appContext.getEventQueue(),
                                                              errIntSpec, this, null);
    }

    /**
     * Unregisters resources.
     * 
     */
    public void cleanup()
    {
        _appContext.getProvider().unregisterClient(_specHandle);
        _appContext.getProvider().unregisterClient(_errHandle);
    }

    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_ACTIVE_CLIENT_SESSION_PUB_EVENT:
                processOMMActiveClientSessionEvent((OMMActiveClientSessionEvent)event);
                break;
            case Event.OMM_LISTENER_EVENT:
                System.out.println(event);
                break;
            case Event.OMM_CMD_ERROR_EVENT:
                processOMMCmdErrorEvent((OMMCmdErrorEvent)event);
                break;
            default:
                System.err.println("Unhandled event received by ProvServer " + event);
                break;
        }
    }

    void processOMMActiveClientSessionEvent(OMMActiveClientSessionEvent event)
    {
        Handle clientSessionHandle = event.getClientSessionHandle();

        boolean acceptSession = CommandLine.booleanVariable("acceptSessions");
        if (acceptSession)
        {
            ClientSessionMgr csm = new ClientSessionMgr(_appContext);
            csm.accept(clientSessionHandle);
            _appContext.getClientSessions().put(clientSessionHandle, csm);
        }
        else
        {
            OMMInactiveClientSessionCmd cmd = new OMMInactiveClientSessionCmd();
            cmd.setClientSessionHandle(event.getClientSessionHandle());

            _appContext.getProvider().submit(cmd, null);
            System.out.println("Client Session  " + clientSessionHandle + " has been rejected");
        }
    }

    void processOMMCmdErrorEvent(OMMCmdErrorEvent event)
    {
        System.out.println(" -------- OMMCmdErrorEvent -------- ");
        System.out.println("Cmd ID: " + event.getCmdID() + ", " + "Cmd closure: "
                + event.getSubmitClosure() + ", " + "State: " + event.getStatus().getState() + ", "
                + "StatusCode: " + event.getStatus().getStatusCode() + ", " + "StatusText: \""
                + event.getStatus().getStatusText() + "\"");
    }

    Handle _specHandle;
    Handle _errHandle;

}
