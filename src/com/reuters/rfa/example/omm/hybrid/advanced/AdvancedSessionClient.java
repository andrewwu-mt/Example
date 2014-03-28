package com.reuters.rfa.example.omm.hybrid.advanced;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.omm.hybrid.DictionaryClient;
import com.reuters.rfa.example.omm.hybrid.DictionaryManager;
import com.reuters.rfa.example.omm.hybrid.OMMMsgReencoder;
import com.reuters.rfa.example.omm.hybrid.ProviderServer;
import com.reuters.rfa.example.omm.hybrid.RequestClient;
import com.reuters.rfa.example.omm.hybrid.SessionClient;
import com.reuters.rfa.example.omm.hybrid.advanced.ItemGroupManager.HandleEntry;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMItemGroup;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;
import com.reuters.rfa.session.omm.OMMSolicitedItemEvent;

/**
 * Manages requests from a single client.
 * 
 * The AdvancedSessionClient is responsible for processing requests from the
 * client and responses from the source application.
 * 
 */
public class AdvancedSessionClient implements SessionClient, RequestClient, DictionaryClient
{

    private final Handle _sessionHandle;

    private Handle _loginHandle;
    private String _loginName;

    private OMMConsumer _consumer;
    private final EventQueue _eventQueue;
    private final AdvancedSessionManager _parent;

    private final ProviderServer _providerServer;
    private final OMMItemIntSpec _intSpec;

    private final boolean _reencode;
    private boolean _disconnect;

    private final Map<Token, Handle> _reqMap; // key=token value=handle

    private final Map<Token, DictionaryRequestClient> _dictionaryClients; // key=token,
                                                                          // value=DictionaryClient
    private final Map<String, List<PendingRequest>> _pendingRequests; // key=serviceName,
                                                                      // value=list
                                                                      // of
                                                                      // PendingRequests
    private final DirectoryClient _directoryClient;

    ItemGroupManager _itemGroupManager;
    DictionaryManager _dictionaryManager;

    OMMPool _pool;
    OMMEncoder _encoder;
    private final OMMMsg _responseMsg;
    private final OMMState _responseState;

    static int numberOfInstances = 0;

    private final String _instanceName;
    int _instanceNumber;

    class PendingRequest
    {
        OMMMsg requestMsg;
        Token requestToken;
    }

    public AdvancedSessionClient(AdvancedSessionManager sessionManager, Handle sessionHandle)
    {
        _parent = sessionManager;
        _sessionHandle = sessionHandle;
        _dictionaryManager = _parent.getDictionaryManager();
        _eventQueue = _parent.getEventQueue();
        _providerServer = _parent.getProviderServer();
        _intSpec = new OMMItemIntSpec();
        _reqMap = new HashMap<Token, Handle>();
        _pendingRequests = new HashMap<String, List<PendingRequest>>();
        _dictionaryClients = new HashMap<Token, DictionaryRequestClient>();
        // _groupManager = new ItemGroupManager();

        _reencode = CommandLine.booleanVariable("useReencoder");
        _disconnect = false;

        _pool = OMMPool.create();
        _encoder = _pool.acquireEncoder();
        _encoder.initialize(OMMTypes.MSG, 1000);

        _responseMsg = _pool.acquireMsg();
        _responseState = _pool.acquireState();

        _itemGroupManager = new ItemGroupManager(this);
        _directoryClient = new DirectoryClient(this);

        _instanceNumber = numberOfInstances++;
        _instanceName = "[AdvancedSessionClient #" + _instanceNumber + "]";

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
            _directoryClient.closeRequest();
            // cleanup every item requested
            Iterator<Handle> iter = _reqMap.values().iterator();
            while (iter.hasNext())
            {
                Handle handle = (Handle)iter.next();
                _consumer.unregisterClient(handle);
            }
        }

        _consumer.destroy();
        _itemGroupManager.cleanup();
        _pool.releaseMsg(_responseMsg);
        _pool.releaseState(_responseState);
    }

    public OMMConsumer getConsumer()
    {
        return _consumer;
    }

    public EventQueue getEventQueue()
    {
        return _eventQueue;
    }

    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_ITEM_EVENT: // from OMMConsumer
                // if we receive the disconnect from client, the SessionClient
                // is
                // shutting down.
                if (!_disconnect)
                {
                    processOMMItemEvent((OMMItemEvent)event);
                }
                break;
            case Event.OMM_SOLICITED_ITEM_EVENT: // from OMMProvider client
                                                 // session
                processOMMSolicitedItemEvent((OMMSolicitedItemEvent)event);
                break;
            case Event.OMM_INACTIVE_CLIENT_SESSION_PUB_EVENT: // from
                                                              // OMMProvider
                                                              // client session
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
            case RDMMsgTypes.DIRECTORY:
                processDirectoryResp(event);
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
            case RDMMsgTypes.DIRECTORY:
                processDirectoryRequest(event);
                break;
            case RDMMsgTypes.DICTIONARY:
                processDictionaryRequest(event);
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
                if(msg.isSet(OMMMsg.Indication.NONSTREAMING))
                {
                    System.out.println("ERROR: Received NONSTREAMING request, ignoring");
                    return;
                }
                
                _loginName = msg.getAttribInfo().getName();
    
                OMMMsg reqMsg;
                if (_reencode)
                    reqMsg = OMMMsgReencoder.getEncodeMsgfrom(msg, 2000);
                else
                    reqMsg = msg;
    
                _intSpec.setMsg(reqMsg);
    
                System.out.println(_instanceName + " Sending \"" + _loginName + "\" login request");
                _loginHandle = _consumer.registerClient(_eventQueue, _intSpec, this, token);
                _directoryClient.sendRequest();
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
    private void processDirectoryRequest(OMMSolicitedItemEvent event)
    {
        System.out.println(_instanceName + " Received Directory request");

        OMMMsg msg = event.getMsg();
        Token token = event.getRequestToken();
        int msgType = msg.getMsgType();

        switch (msgType)
        {
            case OMMMsg.MsgType.REQUEST:
                makeRequest(msg, token);
                return;
            case OMMMsg.MsgType.STREAMING_REQ:
            case OMMMsg.MsgType.NONSTREAMING_REQ:
            case OMMMsg.MsgType.PRIORITY_REQ:
                System.out.println("Received deprecated message type of "
                        + OMMMsg.MsgType.toString(msg.getMsgType())
                        + ", not supported. ");
                return;
            case OMMMsg.MsgType.CLOSE_REQ:
                closeRequest(token);
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
    private void processDictionaryRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        int msgType = msg.getMsgType();
        Token token = event.getRequestToken();

        switch (msgType)
        {
            case OMMMsg.MsgType.REQUEST:
            {
                DictionaryRequestClient dictClient = new DictionaryRequestClient(token);
    
                _parent.getDictionaryManager().addDictionaryClient(this, dictClient, msg);
                _dictionaryClients.put(token, dictClient);
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
                DictionaryRequestClient dictClient = (DictionaryRequestClient)_dictionaryClients
                        .remove(token);
                if (dictClient != null)
                {
                    _parent.getDictionaryManager().removeDictionaryClient(dictClient);
                }
                return;
            }
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
    
                OMMAttribInfo attribInfo = msg.getAttribInfo();
                String serviceName = attribInfo.getServiceName();
                // if we don't have dictionary for this service,
                // we cannot make a request
                // we need to wait for the dictionary
                if (!_parent.getDictionaryManager().hasDictionary(serviceName))
                {
                    System.out.println(_instanceName + " Dictionary for " + serviceName
                            + " is not yet available. Request is pending.");
    
                    boolean found = _parent.getDictionaryManager()
                            .requestDictionaryForService(this, this, serviceName);
                    if (found)
                    {
                        PendingRequest pendingRequest = new PendingRequest();
                        pendingRequest.requestMsg = _pool.acquireMsg();
                        pendingRequest.requestMsg.initFrom(msg);
                        pendingRequest.requestToken = token;
                        List<PendingRequest> pendingList = _pendingRequests.get(serviceName);
                        if (pendingList == null)
                        {
                            pendingList = new ArrayList<PendingRequest>();
                            pendingList.add(pendingRequest);
                            _pendingRequests.put(serviceName, pendingList);
                        }
                        else
                        {
                            pendingList.add(pendingRequest);
                        }
                    }
                    else
                    {
                        System.out.println(_instanceName
                                        + " Cannot find any service providing dictionary needed. DIRECTORY response might not arrive yet.");
                        makeRequest(msg, token);
                    }
                    return;
                }
    
                makeRequest(msg, token);
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
                closeRequest(token);
                return;
         }
    }

    private void makeRequest(OMMMsg msg, Token token)
    {
        OMMMsg reqMsg;
        if (_reencode)
            reqMsg = OMMMsgReencoder.getEncodeMsgfrom(msg, 1000);
        else
            reqMsg = msg;

        _intSpec.setMsg(reqMsg);

        // First check if we have this token in our watchlist.
        // if yes we call reissueClient
        Handle handle = (Handle)_reqMap.get(token);
        if (handle != null)
        {
            _consumer.reissueClient(handle, _intSpec);
        }
        else
        {

            handle = _consumer.registerClient(_eventQueue, _intSpec, this, token);

            _itemGroupManager.addItem(reqMsg, handle, token);

            OMMAttribInfo attribInfo = msg.getAttribInfo();

            if (attribInfo.has(OMMAttribInfo.HAS_SERVICE_NAME)
                    && attribInfo.has(OMMAttribInfo.HAS_NAME))
                System.out.println(_instanceName + " Request ("
                        + RDMMsgTypes.toString(msg.getMsgModelType()) + ") "
                        + attribInfo.getServiceName() + ":" + attribInfo.getName());
            else
                System.out.println(_instanceName + " Request ("
                        + RDMMsgTypes.toString(msg.getMsgModelType()) + ")");

            _reqMap.put(token, handle);

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

    private void processDirectoryResp(OMMItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        Token token = (Token)event.getClosure();

        Handle handle = (Handle)_reqMap.get(token);
        if (msg.isFinal())
        {
            _reqMap.remove(handle);
        }

        _providerServer.submitResp(msg, token, (int)(msg.getEncodedLength() * 1.20));
    }

    private void processResp(OMMItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        Token token = (Token)event.getClosure();

        // we need to set dictionary in case we reencode FieldList
        Handle handle = (Handle)_reqMap.get(token);

        // this can happen, when the dictionary stream is close or dictionary
        // has been changed
        // we have still have response msgs in the queue, we will discard this
        // response
        if (handle == null)
            return;

        HandleEntry he = _itemGroupManager.getHandleEntry(handle);

        // item is not in the watchlist
        if (he == null)
            return;

        FieldDictionary dictionary = _parent.getDictionaryManager().getDictionary(he.serviceName);
        OMMMsgReencoder.setLocalDictionary(dictionary);

        // Refresh will always provide group id
        // Status response can contain group id
        if ((msg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
                || (msg.getMsgType() == OMMMsg.MsgType.STATUS_RESP && msg
                        .has(OMMMsg.HAS_ITEM_GROUP)))
        {
            OMMItemGroup group = msg.getItemGroup();
            Handle itemHandle = event.getHandle();
            _itemGroupManager.applyGroup(itemHandle, group);
        }
        else if (msg.getMsgType() == OMMMsg.MsgType.GENERIC)
        {
            System.out.println("Received generic message type, not supported. ");
        }

        if (msg.isFinal())
        {
            _itemGroupManager.removeItem(handle);
            System.out.println(_instanceName + " Received stream closed. Remove " + handle);
        }

        boolean growEncoderFlag = false;
        int encoderSize = msg.getEncodedLength() + 100;

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

    public void processDictionaryComplete(FieldDictionary dictionary, String serviceName)
    {

        // when we have dictionary for this service, flush the pending requests
        List<PendingRequest> pendingList = (List<PendingRequest>)_pendingRequests.get(serviceName);
        if (pendingList != null)
        {
            Iterator<PendingRequest> iter = pendingList.iterator();
            while (iter.hasNext())
            {
                PendingRequest pendingRequest = (PendingRequest)iter.next();
                makeRequest(pendingRequest.requestMsg, pendingRequest.requestToken);
            }
        }
    }

    public void processDictionaryResponse(OMMMsg responseMsg)
    {
        // this function won't get called.
    }

    private void closeRequest(Token token)
    {
        Handle handle = (Handle)_reqMap.remove(token);
        if (handle != null)
        {
            HandleEntry he = _itemGroupManager.getHandleEntry(handle);
            if (he != null)
                System.out.println(_instanceName + " Closing " + he.serviceName + ":" + he.itemName);
            _consumer.unregisterClient(handle);

            _itemGroupManager.removeItem(handle);
        }
    }

    public void submitItemStatus(HandleEntry entry, Token token, OMMState state)
    {
        _responseMsg.clear();
        _responseMsg.setMsgType(OMMMsg.MsgType.STATUS_RESP);
        _responseMsg.setMsgModelType(entry.msgModelType);

        _responseMsg.setState(state);
        _responseMsg.setAttribInfo(entry.attribInfo);

        _providerServer.submitResp(_responseMsg, token, 1000);
    }

    class DictionaryRequestClient implements DictionaryClient
    {
        Token _reqToken;

        public DictionaryRequestClient(Token token)
        {
            _reqToken = token;
        }

        public void processDictionaryComplete(FieldDictionary dictionary, String serviceName)
        {
            // this case will never happen
        }

        public void processDictionaryResponse(OMMMsg responseMsg)
        {

            boolean growEncoderFlag = false;
            int encoderSize = responseMsg.getEncodedLength() + 20000;

            do
            {
                try
                {
                    _providerServer.submitResp(responseMsg, _reqToken, encoderSize);
                    growEncoderFlag = false;
                }
                catch (IndexOutOfBoundsException e)
                {
                    // Not enough room, need to grow the encoderSize and try
                    // submit again.
                    growEncoderFlag = true;
                    encoderSize = (int)(encoderSize * 1.1);
                }
            }
            while (growEncoderFlag);
        }
    }
}
