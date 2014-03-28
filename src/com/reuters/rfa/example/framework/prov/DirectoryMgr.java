package com.reuters.rfa.example.framework.prov;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;

/**
 * DirectoryMgr is a domain manager which handles Directory domain. This class
 * keeps service information for each service name in HashMap. It can encodes
 * Directory response message from service information and sends the encoded
 * message to a client that request for Directory.
 * 
 * @see ServiceInfo
 */
public class DirectoryMgr implements ProvDomainMgr
{
    private PubAppContext _appContext;
    private Set<Token> _tokens;
    private Map<String, ServiceInfo> _services;
    private String _vendor;
    private boolean _isSource;

    protected DirectoryMgr(PubAppContext appContext, String vendor, boolean isSource)
    {
        _appContext = appContext;
        _appContext.addDomainMgr(this);
        _services = new HashMap<String, ServiceInfo>();
        _vendor = vendor;
        _isSource = isSource;
        _tokens = new HashSet<Token>();
    }

    /**
     * Adds service information for this service name.
     * 
     * @param serviceName
     * @return ServiceInfo added ServiceInfo
     */
    public ServiceInfo addServiceInfo(String serviceName)
    {
        ServiceInfo si = (ServiceInfo)_services.get(serviceName);
        if (si == null)
        {
            si = new ServiceInfo(serviceName, _vendor, _isSource);
            _services.put(serviceName, si);
        }
        return si;
    }

    /**
     * Returns service information for this service name.
     * 
     * @param serviceName
     * @return ServiceInfo service information for this service name.
     */
    public ServiceInfo getServiceInfo(String serviceName)
    {
        return (ServiceInfo)_services.get(serviceName);
    }

    public OMMPool getPool()
    {
        return _appContext.getPool();
    }

    /**
     * Encodes and Sends Directory response message.
     * 
     * @param token a request token.
     * @param msg a request message.
     */
    public void sendRespMsg(Token token, OMMMsg msg, boolean solicited)
    {
        System.out.println("Directory request received");
        OMMEncoder enc = _appContext.getEncoder();

        enc.initialize(OMMTypes.MSG, 6000);
        OMMMsg respmsg = _appContext.getPool().acquireMsg();
        respmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        respmsg.setMsgModelType(RDMMsgTypes.DIRECTORY);
        respmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
        respmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        
        if (solicited)
            respmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            respmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        
        respmsg.setItemGroup(1);
        OMMAttribInfo attribInfo = _appContext.getPool().acquireAttribInfo();

        // Specifies what type of information is provided.
        // We will encode what the information that is being requested (only
        // INFO, STATE, and GROUP is supported in the application).
        OMMAttribInfo at = msg.getAttribInfo();
        if (at.has(OMMAttribInfo.HAS_FILTER))
        {
            attribInfo.setFilter(at.getFilter()); // Set the filter information
                                                  // to what was requested.
        }

        respmsg.setAttribInfo(attribInfo); // Set the attribInfo into the message.

        // Initialize the response message encoding that contains no data in
        // attribInfo, and MAP data in the message.
        enc.encodeMsgInit(respmsg, OMMTypes.NO_DATA, OMMTypes.MAP);

        // Map encoding initialization.
        // Specifies the flag for the map, the data type of the key as
        // ascii_string, as defined by RDMUsageGuide.
        // the data type of the map entries is FilterList, the total count hint,
        // and the dictionary id as 0
        enc.encodeMapInit(OMMMap.HAS_TOTAL_COUNT_HINT, OMMTypes.ASCII_STRING, OMMTypes.FILTER_LIST,
                          1, (short)0);

        for (Iterator<ServiceInfo> iter = _services.values().iterator(); iter.hasNext();)
        {
            ServiceInfo si = (ServiceInfo)iter.next();
            si.encodeRefresh(_appContext.getEncoder(), at.getFilter());
        }

        enc.encodeAggregateComplete(); // any type that requires a count needs
                                       // to be closed. This one is for Map.
        submit(token, (OMMMsg)_appContext.getEncoder().getEncodedObject());

        _appContext.getPool().releaseMsg(respmsg);
        _appContext.getPool().releaseAttribInfo(attribInfo);
    }

    public short getMsgModelType()
    {
        return RDMMsgTypes.DIRECTORY;
    }

    public void processCloseReqMsg(ClientSessionMgr clientSessionMgr, Token token,
            StreamItem streamItem)
    {
        _tokens.remove(token);
    }

    public void processReReqMsg(ClientSessionMgr clientSessionMgr, Token token,
            StreamItem streamItem, OMMMsg msg)
    {
        // reissue, only send refresh resp if requested.
        if (msg.isSet(OMMMsg.Indication.REFRESH))
            sendRespMsg(token, msg, true);
    }

    public void processReqMsg(ClientSessionMgr clientSessionMgr, Token token, OMMMsg msg)
    {
        if (!msg.isSet(OMMMsg.Indication.NONSTREAMING))
            _tokens.add(token);
        // initial request, always send refresh resp
        sendRespMsg(token, msg, msg.isSet(OMMMsg.Indication.REFRESH));
    }

    public String getServiceName()
    {
        return null;
    }

    public void submit(Token token, OMMMsg encmsg)
    {
        _appContext.submit(token, encmsg);
    }

    /**
     * Encodes a close status message.
     * 
     * @param ai OMMAttribInfo to send with close message
     * @param text see {@link OMMState#getText()}
     * @return encoded OMM message.
     */
    public OMMMsg encodeClosedStatus(OMMAttribInfo ai, String text)
    {
        OMMMsg closeStatusMsg = getPool().acquireMsg();
        closeStatusMsg.setMsgType(OMMMsg.MsgType.STATUS_RESP);
        closeStatusMsg.setMsgModelType(RDMMsgTypes.DIRECTORY);
        closeStatusMsg.setAttribInfo(ai);
        closeStatusMsg.setState(OMMState.Stream.CLOSED, OMMState.Data.SUSPECT,
                                OMMState.Code.NOT_FOUND, text);
        return closeStatusMsg;
    }

}
