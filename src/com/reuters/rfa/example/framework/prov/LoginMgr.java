package com.reuters.rfa.example.framework.prov;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMUser;

/**
 * LoginMgr is a login manager which handles Login request. Authentication was
 * done by LoginAuthenticator which can be set to LoginMgr by
 * {@link LoginMgr#setAuthenticator(LoginAuthenticator)}.
 * 
 * @see LoginAuthenticator
 */

public class LoginMgr implements ProvDomainMgr
{
    PubAppContext _appContext;
    Set<Token> _tokens;
    LoginAuthenticator _authenticator;
    boolean _isSupportPAR;

    public LoginMgr(PubAppContext appContext)
    {
        _appContext = appContext;
        _appContext.addDomainMgr(this);
        _tokens = new HashSet<Token>();
        _isSupportPAR = false;
    }

    /**
     * Set the implementation class of
     * {@link com.reuters.rfa.example.framework.prov.LoginAuthenticator} to this
     * LoginMgr
     * 
     * @param authenticator
     */
    public void setAuthenticator(LoginAuthenticator authenticator)
    {
        _authenticator = authenticator;
    }

    /**
     * Set response LoginMgr SupportPauseResume in (@link
     * com.reuters.rfa.omm.OMMAttribInfo)
     * 
     * @param isSupportPAR
     */
    public void setSupportPAR(boolean isSupportPAR)
    {
        _isSupportPAR = isSupportPAR;
    }

    public OMMPool getPool()
    {
        return _appContext.getPool();
    }

    public void sendRespMsg(Token token, OMMMsg reqMsg, boolean solicited)
    {
        OMMMsg respMsg = _appContext.getPool().acquireMsg();
        respMsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        respMsg.setMsgModelType(RDMMsgTypes.LOGIN);
        
        if(solicited)
            respMsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            respMsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        
        respMsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
        respMsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        respMsg.setAttribInfo(null, reqMsg.getAttribInfo().getName(), reqMsg.getAttribInfo()
                .getNameType());
        OMMEncoder encoder = _appContext.getEncoder();
        encoder.initialize(OMMTypes.MSG, 500);
        encoder.encodeMsgInit(respMsg, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);
        encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        OMMElementList origElementList = (OMMElementList)reqMsg.getAttribInfo().getAttrib();
        for (Iterator<?> iter = origElementList.iterator(); iter.hasNext();)
        {
            OMMElementEntry ee = (OMMElementEntry)iter.next();
            if (!ee.getName().equals(RDMUser.Attrib.SupportPauseResume)) // Encode PAR element separately
            {
                encoder.encodeElementEntryInit(ee.getName(), ee.getDataType());
                encoder.encodeData(ee.getData());
            }
            else
            {
                encoder.encodeElementEntryInit(RDMUser.Attrib.SupportPauseResume, OMMTypes.UINT);
                encoder.encodeUInt((long)(_isSupportPAR ? 1 : 0));
            }
        }
        encoder.encodeAggregateComplete();

        submit(token, (OMMMsg)encoder.getEncodedObject());
        _appContext.getPool().releaseMsg(respMsg);
    }

    public short getMsgModelType()
    {
        return RDMMsgTypes.LOGIN;
    }

    public void processCloseReqMsg(ClientSessionMgr clientSessionMgr, Token token,
            StreamItem streamItem)
    {
        _tokens.remove(token);
    }

    public void processReReqMsg(ClientSessionMgr clientSessionMgr, Token token,
            StreamItem streamItem, OMMMsg msg)
    {
        // reissue - only send refresh resp if requested.
        if (msg.isSet(OMMMsg.Indication.REFRESH))
            sendRespMsg(token, msg, true);
    }

    /**
     * Process login request,if the user is authorized send login response with
     * OK state, otherwise send status with CLOSED state.
     */
    public void processReqMsg(ClientSessionMgr clientSessionMgr, Token token, OMMMsg msg)
    {
        // if no authenticator, auto-accept
        if (_authenticator == null || _authenticator.authenticate(msg.getAttribInfo()))
        {
            if (!msg.isSet(OMMMsg.Indication.NONSTREAMING))
                _tokens.add(token);

            clientSessionMgr.setLoggedIn();
            
            // this is an initial request, always send refresh resp.
            sendRespMsg(token, msg, msg.isSet(OMMMsg.Indication.REFRESH));
        }
        else
        {
            OMMMsg closedStatusMsg = encodeClosedStatus(msg.getAttribInfo(), "Permission denied");
            submit(token, closedStatusMsg);
            _appContext.getPool().releaseMsg(closedStatusMsg);
            clientSessionMgr.forceLogout();
        }
    }

    public String getServiceName()
    {
        return null;
    }

    public void submit(Token token, OMMMsg encmsg)
    {
        _appContext.submit(token, encmsg);
    }

    public OMMMsg encodeClosedStatus(OMMAttribInfo ai, String text)
    {
        OMMMsg closeStatusMsg = getPool().acquireMsg();
        closeStatusMsg.setMsgType(OMMMsg.MsgType.STATUS_RESP);
        closeStatusMsg.setMsgModelType(RDMMsgTypes.LOGIN);
        closeStatusMsg.setAttribInfo(ai);
        closeStatusMsg.setState(OMMState.Stream.CLOSED, OMMState.Data.SUSPECT,
                                OMMState.Code.NOT_FOUND, text);
        return closeStatusMsg;
    }

}
