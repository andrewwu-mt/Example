package com.reuters.rfa.example.framework.prov;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.omm.OMMMsg;

/**
 * The interface for client callback that is responsible for processing request
 * message. This client used to handle such messages as request, re-request and
 * close messages.
 * 
 */
public interface ReqMsgClient
{
    /**
     * The callback method for processing a request message.
     * 
     * @param clientSessionMgr a client session manager.
     * @param token the client request token.
     * @param msg the request message.
     */
    void processReqMsg(ClientSessionMgr clientSessionMgr, Token token, OMMMsg msg);

    /**
     * The callback method for processing a request message after the first
     * processed message.
     * 
     * @param clientSessionMgr a client session manager.
     * @param token the client request token.
     * @param si the stream item of the first request message.
     * @param msg the re-request message.
     */
    void processReReqMsg(ClientSessionMgr clientSessionMgr, Token token, StreamItem si, OMMMsg msg);

    /**
     * The callback method for processing a close request.
     * 
     * @param clientSessionMgr a client session manager.
     * @param token the client request token.
     * @param si the stream item of the first request message.
     */
    void processCloseReqMsg(ClientSessionMgr clientSessionMgr, Token token, StreamItem si);

}
