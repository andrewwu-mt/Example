package com.reuters.rfa.example.framework.prov;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;

/**
 * This class provides a skeletal implementation of the ProvDomainMgr interface.
 * 
 * This class used the PubAppContext for basic functionalities of the provider
 * domain such as message submitting, dictionary accessing and service
 * initializing.
 * 
 * @see com.reuters.rfa.example.framework.prov.PubAppContext
 */
public abstract class AbstractProvDomainMgr implements ProvDomainMgr
{
    final short _msgModelType;
    final protected String _serviceName;
    final protected PubAppContext _pubContext;

    public AbstractProvDomainMgr(PubAppContext context, short msgModeltype, String serviceName)
    {
        _pubContext = context;
        _msgModelType = msgModeltype;
        _serviceName = serviceName;
    }

    public String getServiceName()
    {
        return _serviceName;
    }

    public short getMsgModelType()
    {
        return _msgModelType;
    }

    public OMMPool getPool()
    {
        return _pubContext.getPool();
    }

    public void submit(Token token, OMMMsg msg)
    {
        if (msg.isFinal())
            remove(token);
        _pubContext.submit(token, msg);
    }

    public void addDictionariesUsed(ServiceInfo si)
    {
    }

    /**
     * Encodes a close status message.
     * 
     * @param ai OMMAttribInfo to send with close message
     * @param text see {@link OMMState#getText()}
     * @return an encoded closed status message.
     */
    public OMMMsg encodeClosedStatus(OMMAttribInfo ai, String text)
    {
        OMMMsg closeStatusMsg = getPool().acquireMsg();
        closeStatusMsg.setMsgType(OMMMsg.MsgType.STATUS_RESP);
        closeStatusMsg.setMsgModelType(_msgModelType);
        closeStatusMsg.setAttribInfo(ai);
        closeStatusMsg.setState(OMMState.Stream.CLOSED, OMMState.Data.SUSPECT,
                                OMMState.Code.NOT_FOUND, text);
        return closeStatusMsg;
    }

    /**
     * Returns a field dictionary currently in use for this domain.
     * 
     * @return a field dictionary currently in use for this domain.
     */
    public FieldDictionary fieldDictionary()
    {
        return _pubContext.getFieldDictionary();
    }

    /**
     * Removes the item stream associated with this token.
     * 
     * @param token
     * @return removed stream item.
     */
    public StreamItem remove(Token token)
    {
        return null;
    }

    /**
     * Creates a stream item to handle client request.
     * 
     * @param token client request token.
     * @param msg request message.
     * @return stream item
     */
    public abstract StreamItem createStreamItem(Token token, OMMMsg msg);

    /**
     * Indicates that the service of this domain is ready to be used.
     * 
     */
    public void indicateServiceInitialized()
    {
        DirectoryMgr dirmgr = (DirectoryMgr)_pubContext.getDomainMgr(RDMMsgTypes.DIRECTORY);
        ServiceInfo si = dirmgr.getServiceInfo(_serviceName);
        si.setState(RDMService.State.UP, RDMService.State.UP);
        System.out.println("Service is up");
    }

    /**
     * Returns the current used PubAppContext of this domain.
     * 
     * @return the current used PubAppContext of this domain.
     */
    public PubAppContext pubAppContext()
    {
        return _pubContext;
    }
}
