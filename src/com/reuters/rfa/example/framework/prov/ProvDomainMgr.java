package com.reuters.rfa.example.framework.prov;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;

/**
 * The interface for domain manager which provides callback methods to process
 * client requests and provides method to send response message.
 * 
 * The request and response messages depend on a message model type supporting
 * by this domain manager.
 */
public interface ProvDomainMgr extends ReqMsgClient
{

    /**
     * Returns the message model type that this domain manager provides. The
     * return value is one of the types defined in
     * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes}.
     * 
     * @return the message model type that this domain manager provides.
     */
    short getMsgModelType();

    /**
     * Returns the name of service that this domain manager provides.
     * 
     * @return the name of service that this domain manager provides.
     */
    String getServiceName();

    /**
     * Returns an OMMPool used by this domain manager.
     * 
     * @return an OMMPool used by this domain manager.
     */
    OMMPool getPool();

    /**
     * Sends an OMM message to a client.
     * 
     * @param token the client request token
     * @param encmsg the OMM message to submit
     */
    void submit(Token token, OMMMsg encmsg);

}
