package com.reuters.rfa.example.omm.hybrid.simple;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.example.omm.hybrid.OMMMsgReencoder;
import com.reuters.rfa.example.omm.hybrid.ProviderServer;
import com.reuters.rfa.example.omm.hybrid.SessionClient;
import com.reuters.rfa.example.omm.hybrid.SessionManager;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;
import com.reuters.rfa.session.omm.OMMSolicitedItemEvent;

/**
 * Manages requests from a single client.
 * 
 * The SimpleSessionClient is responsible for processing requests from the
 * client and responses from the source application.
 * 
 */
public class SimpleSessionClient implements SessionClient
{

    private final Handle _sessionHandle;
    private final Map<Token, ItemInfo> _reqMap; // map between req token and
                                                // ItemInfo
    private Handle _loginHandle;
    private String _loginName;

    private OMMConsumer _consumer;
    private final EventQueue _eventQueue;
    private final SessionManager _parent;

    private final ProviderServer _providerServer;
    private final OMMItemIntSpec _intSpec;

    private final boolean _reencode;
    private boolean _disconnect;

    private static int _instanceNumber = 0;

    private final String _instanceName;

    class ItemInfo
    {
        public Handle handle;
        public String serviceName;
        public String name;
    }

    public SimpleSessionClient(SessionManager sessionManager, Handle sessionHandle)
    {

        _parent = sessionManager;
        _sessionHandle = sessionHandle;

        _eventQueue = _parent.getEventQueue();
        _providerServer = _parent.getProviderServer();
        _intSpec = new OMMItemIntSpec();
        _reqMap = new HashMap<Token, ItemInfo>();
        _disconnect = false;

        _reencode = CommandLine.booleanVariable("useReencoder");

        _instanceName = "[SimpleSessionClient #" + _instanceNumber++ + "]";
        if (_reencode)
        {
            System.out.println(_instanceName + " Will reencode messages");
        }
        else
        {
            System.out.println(_instanceName + " Will not reencode messages");
        }
    }

    public void init()
    {
        System.out.println(_instanceName + " Initializing");
        _consumer = (OMMConsumer)_parent.getSession().createEventSource(EventSource.OMM_CONSUMER,
                                                                        "consumer");
    }

    public void cleanup()
    {
        System.out.println(_instanceName + " Cleaning up");

        if (_loginHandle != null)
        {
            // cleanup every item requested
            Iterator<ItemInfo> iter = _reqMap.values().iterator();
            while (iter.hasNext())
            {
                ItemInfo itemInfo = iter.next();
                _consumer.unregisterClient(itemInfo.handle);
            }
        }

        _reqMap.clear();
        _consumer.destroy();
    }

    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_ITEM_EVENT: // from OMMConsumer
                // if we receive the disconnect from client, the SessionClient
                // is shutting down.
                if (!_disconnect)
                {
                    processOMMItemEvent((OMMItemEvent)event);
                }
                break;
            case Event.OMM_SOLICITED_ITEM_EVENT: // from OMMProvider client session
                processOMMSolicitedItemEvent((OMMSolicitedItemEvent)event);
                break;
            case Event.OMM_INACTIVE_CLIENT_SESSION_PUB_EVENT: // from OMMProvider client session
                System.out.println(_instanceName + " Received OMMInActiveClientSessionPubEvent");
                _disconnect = true;
                _parent.destroySession(_sessionHandle);
                break;
            default:
                break;
        }
    }

    private void processOMMItemEvent(OMMItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        switch (msg.getMsgModelType())
        {
            case RDMMsgTypes.LOGIN:
                processLoginResp(event);
                break;
            default:
                processResp(event);
                break;
        }
    }

    private void processOMMSolicitedItemEvent(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();

        switch (msg.getMsgModelType())
        {
            case RDMMsgTypes.LOGIN:
                processLoginRequest(event);
                break;
            default:
                processRequest(event);
                break;
        }
    }

    @SuppressWarnings("deprecation")
    public void processLoginRequest(OMMSolicitedItemEvent event)
    {

        OMMMsg msg = event.getMsg();
        Token token = event.getRequestToken();
        byte msgType = msg.getMsgType();
        System.out.println(_instanceName + " Received Login " + OMMMsg.MsgType.toString(msgType));

        switch (msgType)
        {
            case OMMMsg.MsgType.REQUEST:
            {
                _loginName = msg.getAttribInfo().getName();
    
                OMMMsg reqMsg;
                if (_reencode)
                    reqMsg = OMMMsgReencoder.getEncodeMsgfrom(msg, 2000);
                else
                    reqMsg = msg;
    
                _intSpec.setMsg(reqMsg);
    
                System.out.println(_instanceName + " Sending \"" + _loginName + "\" login request");
                _loginHandle = _consumer.registerClient(_eventQueue, _intSpec, this, token);
                return;
            }
            case OMMMsg.MsgType.STREAMING_REQ:
            case OMMMsg.MsgType.NONSTREAMING_REQ:
            case OMMMsg.MsgType.PRIORITY_REQ:
                System.out.println("Received deprecated message type of "
                        + OMMMsg.MsgType.toString(msg.getMsgType())
                        + ", not supported. ");
                return;
            case OMMMsg.MsgType.CLOSE_REQ:
                System.out.println(_instanceName + " Logging out \"" + _loginName + "\"");
                _consumer.unregisterClient(_loginHandle);
                _loginHandle = null;
                return;
            case OMMMsg.MsgType.GENERIC:
                System.out.println("Received generic message type, not supported. ");
                return;
            default:
                System.out.println("ERROR: Received unexpected message type. " + msgType);
                return;
        }
    }

    @SuppressWarnings("deprecation")
    public void processRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        int msgType = msg.getMsgType();
        Token token = event.getRequestToken();

        switch (msgType)
        {
            case OMMMsg.MsgType.REQUEST:
            {
                OMMMsg reqMsg;
                if (_reencode)
                    reqMsg = OMMMsgReencoder.getEncodeMsgfrom(msg, 1000);
                else
                    reqMsg = msg;
    
                _intSpec.setMsg(reqMsg);
    
                // First check if we have this token in our watchlist.
                // if yes we call reissueClient
    
                ItemInfo itemInfo = (ItemInfo)_reqMap.get(token);
                if (itemInfo != null)
                {
                    _consumer.reissueClient(itemInfo.handle, _intSpec);
                }
                else
                {
                    Handle handle = _consumer.registerClient(_eventQueue, _intSpec, this, token);
                    itemInfo = new ItemInfo();
                    itemInfo.handle = handle;
    
                    System.out.print(_instanceName + " Received open request for "
                            + RDMMsgTypes.toString(msg.getMsgModelType()) + " ");
                    if (msg.has(OMMMsg.HAS_ATTRIB_INFO))
                    {
                        OMMAttribInfo attribInfo = msg.getAttribInfo();
                        if (attribInfo.has(OMMAttribInfo.HAS_SERVICE_NAME))
                        {
                            System.out.print(" Service: " + attribInfo.getServiceName());
                            itemInfo.serviceName = attribInfo.getServiceName();
                        }
                        if (attribInfo.has(OMMAttribInfo.HAS_NAME))
                        {
                            System.out.print(" Name: " + attribInfo.getName());
                            itemInfo.name = attribInfo.getName();
                        }
                    }
                    System.out.println();
                    _reqMap.put(event.getRequestToken(), itemInfo);
                }
                return;
            }
            case OMMMsg.MsgType.STREAMING_REQ:
            case OMMMsg.MsgType.NONSTREAMING_REQ:
            case OMMMsg.MsgType.PRIORITY_REQ:
                System.out.println("Received deprecated message type of "
                        + OMMMsg.MsgType.toString(msg.getMsgType())
                        + ", not supported. ");
                return;
            case OMMMsg.MsgType.CLOSE_REQ:
            {
                ItemInfo itemInfo = (ItemInfo)_reqMap.remove(token);
                if (itemInfo != null)
                {
                    _consumer.unregisterClient(itemInfo.handle);
    
                    // log close req
                    System.out.print(_instanceName + " Received close request for "
                            + RDMMsgTypes.toString(msg.getMsgModelType()) + " ");
                    if (itemInfo.serviceName != null)
                        System.out.print(" Service: " + itemInfo.serviceName);
                    if (itemInfo.name != null)
                        System.out.print(" Name: " + itemInfo.name);
    
                    System.out.println();
                }
                return;
            }
            case OMMMsg.MsgType.GENERIC:
                System.out.println("Received generic message type, not supported. ");
                return;
            case OMMMsg.MsgType.POST:
                System.out.println("Received post message type, not supported. ");
                return;
            default:
                System.out.println("ERROR: Received unexpected message type. " + msgType);
                return;
        }
    }

    private void processLoginResp(OMMItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        Token token = (Token)event.getClosure();

        _providerServer.submitResp(msg, token, 2000);

        // stream is closed, destroy itself.
        if (msg.isFinal())
        {
            System.out.println(_instanceName + " Received stream closed. Destroying itself");
            _parent.destroySession(_sessionHandle);
        }

    }

    private void processResp(OMMItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        Token token = (Token)event.getClosure();

        if (msg.isFinal())
        {
            ItemInfo itemInfo = (ItemInfo)_reqMap.remove(token);
            System.out
                    .println(_instanceName + " Received stream closed. Remove " + itemInfo.handle);
        }

        boolean growEncoderFlag = false;
        int encoderSize;
        switch (msg.getMsgModelType())
        {
            case RDMMsgTypes.DICTIONARY:
                encoderSize = 275000;
                break;
            default:
                encoderSize = 10000;
                break;
        }

        do
        {
            try
            {
                _providerServer.submitResp(msg, token, encoderSize);
                growEncoderFlag = false;
            }
            catch (IndexOutOfBoundsException e)
            {
                // Not enough room, need to grow the encoderSize and try submit
                // again.
                growEncoderFlag = true;
                encoderSize = (int)(encoderSize * 1.1);
            }
        }
        while (growEncoderFlag);
    }
}
