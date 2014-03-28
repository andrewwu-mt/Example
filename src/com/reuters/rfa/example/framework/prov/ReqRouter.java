package com.reuters.rfa.example.framework.prov;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMItemCmd;

/**
 * This class is responsible for routing request messages to the appropriate
 * provider domain manager in order to process those messages.
 * 
 * This class is able to keep various types of provider domain. When it receives
 * request messages, the message is routed to the appropriate domain manager by
 * using the message model type.
 * 
 * If the messages do not match any domain manager, this class do nothing.
 * 
 */
public class ReqRouter implements ReqMsgClient
{
    private final ProvDomainMgr[] _domainMgrs = new ProvDomainMgr[RDMMsgTypes.MAX_VALUE + 1];
    PubAppContext _appContext;

    public ReqRouter(PubAppContext appContext)
    {
        _appContext = appContext;
    }

    /**
     * Adds a domain manager to this ReqRouter.
     * 
     * @param mgr a provider domain manager.
     */
    public void addDomainMgr(ProvDomainMgr mgr)
    {
        _domainMgrs[mgr.getMsgModelType()] = mgr;
    }

    /**
     * Processes a request message by forwording the message to the appropriate
     * provider domain.
     * 
     */
    public void processReqMsg(ClientSessionMgr clientSessionMgr, Token token, OMMMsg msg)
    {
        ProvDomainMgr provDomainMgr = _domainMgrs[msg.getMsgModelType()];
        if (provDomainMgr == null)
        {
            System.err.println("Request received with unknown message model Type: "
                               + RDMMsgTypes.toString(msg.getMsgModelType()));
            sendCloseStatus(token, msg.getMsgModelType(), msg.getAttribInfo());
        }
        else
            provDomainMgr.processReqMsg(clientSessionMgr, token, msg);

    }

    /**
     * Processes re-request message by forwording the message to the appropriate
     * provider domain.
     * 
     */
    public void processReReqMsg(ClientSessionMgr clientSessionMgr, Token token, StreamItem si,
            OMMMsg msg)
    {
        ProvDomainMgr provDomainMgr = _domainMgrs[msg.getMsgModelType()];
        if (provDomainMgr != null)
            provDomainMgr.processReReqMsg(clientSessionMgr, token, si, msg);
        // else ignore
    }

    /**
     * Processes close request message by the appropriate provider domain.
     * 
     */
    public void processCloseReqMsg(ClientSessionMgr clientSessionMgr, Token token,
            StreamItem streamItem)
    {
        ProvDomainMgr provDomainMgr = _domainMgrs[streamItem.getMsgModelType()];
        if (provDomainMgr != null)
            provDomainMgr.processCloseReqMsg(clientSessionMgr, token, streamItem);
        // else ignore

    }

    private void sendCloseStatus(Token token, short messageModelType, OMMAttribInfo attribInfo)
    {
        System.out.println("Send Close Status for the following request:  ");
        System.out.println("\tservice name: " + attribInfo.getServiceName());
        System.out.println("\titem name: " + attribInfo.getName());
        System.out.println("\tmessage model type: " + RDMMsgTypes.toString(messageModelType));
        System.out.println();

        OMMMsg msg = _appContext.getPool().acquireMsg();
        msg.setMsgModelType(messageModelType);
        msg.setMsgType(OMMMsg.MsgType.STATUS_RESP);
        msg.setState(OMMState.Stream.CLOSED, OMMState.Data.OK, OMMState.Code.NOT_FOUND,
                     "Unsupported message model type");

        OMMItemCmd cmd = new OMMItemCmd();
        cmd.setMsg(msg);
        cmd.setToken(token);

        _appContext.getProvider().submit(cmd, null);
    }

    /**
     * Returns a provider domain manager for the message model type.
     * 
     * @param msgModelType a message model type
     * @return a provider domain manager
     */
    public ProvDomainMgr getDomainMgr(short msgModelType)
    {
        return _domainMgrs[msgModelType];
    }

}
