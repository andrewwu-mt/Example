package com.reuters.rfa.example.framework.prov;

import java.util.HashMap;
import java.util.Map;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMClientSessionIntSpec;
import com.reuters.rfa.session.omm.OMMInactiveClientSessionEvent;
import com.reuters.rfa.session.omm.OMMSolicitedItemEvent;

/**
 * ClientSessionMgr is an event processing client for handling request messages
 * such as item and inactive client session request for a single client. This
 * class is also responsible for keeping the stream item for each item request
 * in order to handle the repeated item request as a re-request message.
 * 
 */
public class ClientSessionMgr implements Client
{
    ServerPubAppContext _appContext;
    Handle _clientSessionHandle;
    boolean _loggedIn;
    Token _loginToken;
    Map<Token, StreamItem> _clientSessionStreams;
    Handle _handle;

    public ClientSessionMgr(ServerPubAppContext context)
    {
        _appContext = context;
        _clientSessionStreams = new HashMap<Token, StreamItem>();
    }

    public Handle getHandle()
    {
        return _handle;
    }

    /**
     * Accepts a client request.
     * 
     * @param clientSessionHandle a handle of client session.
     */
    public void accept(Handle clientSessionHandle)
    {
        _handle = clientSessionHandle;
        OMMClientSessionIntSpec intSpec = new OMMClientSessionIntSpec();
        intSpec.setClientSessionHandle(clientSessionHandle);

        _clientSessionHandle = _appContext.getProvider().registerClient(_appContext.getEventQueue(), intSpec, this, null);
        if (_clientSessionHandle != clientSessionHandle)
            System.out.println("Warning: accept of " + clientSessionHandle + " failed!");
        System.out.println("Client Session with handle " + _clientSessionHandle + " has been accepted");
    }

    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_INACTIVE_CLIENT_SESSION_PUB_EVENT:
                processOMMInactiveClientSessionEvent((OMMInactiveClientSessionEvent)event);
                break;
            case Event.OMM_SOLICITED_ITEM_EVENT:
                processOMMSolicitedItemEvent((OMMSolicitedItemEvent)event);
                break;
            default:
                System.out.print("Unhandled event received by RDMProvServer");
                break;
        }
    }

    private void processOMMSolicitedItemEvent(OMMSolicitedItemEvent event)
    {
        OMMMsg reqMsg = event.getMsg();
        Token token = event.getRequestToken();

        if (reqMsg.getMsgModelType() == RDMMsgTypes.LOGIN)
        {
            _loginToken = token;
        }

        if (reqMsg.getMsgType() == OMMMsg.MsgType.CLOSE_REQ)
            processClose(token, reqMsg);
        else
            processRequest(token, reqMsg);
    }

    private void processClose(Token token, OMMMsg reqMsg)
    {
        StreamItem streamItem = (StreamItem)_clientSessionStreams.get(token);
        if (streamItem != null)
        {
            System.out.println("Received Close Request of "
                    + RDMMsgTypes.toString(reqMsg.getMsgModelType())
                    + " for Client Session Handle " + _clientSessionHandle);

            streamItem.close();
            _clientSessionStreams.remove(token);
            _appContext.getReqRouter().processCloseReqMsg(this, token, streamItem);
        }
    }

    private void processRequest(Token token, OMMMsg reqMsg)
    {
        StreamItem si = (StreamItem)_clientSessionStreams.get(token);
        if (si == null)
        {
            _appContext.getReqRouter().processReqMsg(this, token, reqMsg);
        }
        else
            _appContext.getReqRouter().processReReqMsg(this, token, si, reqMsg);
    }

    private void processOMMInactiveClientSessionEvent(OMMInactiveClientSessionEvent event)
    {
        // Get client session handle
        System.out.println("Client Session  " + event.getHandle()
                + " has been closed via OMMInactiveClientSessionEvent");

        if (_loginToken != null)
        {
            OMMMsg reqMsg = _appContext.getPool().acquireMsg();
            reqMsg.setMsgModelType(RDMMsgTypes.LOGIN);
            reqMsg.setMsgType(OMMMsg.MsgType.CLOSE_REQ);
            _loggedIn = false;
            StreamItem loginSI = (StreamItem)_clientSessionStreams.get(_loginToken);
            if (loginSI != null)
            {
                _appContext.getReqRouter().processCloseReqMsg(this, _loginToken, loginSI);
            }
            _loginToken = null;
        }

        _appContext.getClientSessions().remove(_clientSessionHandle);
    }

    /**
     * Returns a map that contains request token and StreamItem
     * 
     * @return Map a map that contains request token and StreamItem.
     */
    public Map<Token, StreamItem> getClientSessionStreams()
    {
        return _clientSessionStreams;
    }

    public void setLoggedIn()
    {
        _loggedIn = true;
    }

    void forceLogout()
    {
        _loggedIn = false;
        _loginToken = null;
        _clientSessionStreams.clear();
    }

}
