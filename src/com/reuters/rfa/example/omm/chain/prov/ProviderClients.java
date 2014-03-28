package com.reuters.rfa.example.omm.chain.prov;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.example.utility.Rounding;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMFilterEntry;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMPriority;
import com.reuters.rfa.omm.OMMQos;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMDictionary;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;
import com.reuters.rfa.session.TimerIntSpec;
import com.reuters.rfa.session.omm.OMMActiveClientSessionEvent;
import com.reuters.rfa.session.omm.OMMClientSessionIntSpec;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;
import com.reuters.rfa.session.omm.OMMInactiveClientSessionEvent;
import com.reuters.rfa.session.omm.OMMItemCmd;
import com.reuters.rfa.session.omm.OMMListenerEvent;
import com.reuters.rfa.session.omm.OMMProvider;
import com.reuters.rfa.session.omm.OMMSolicitedItemEvent;

/**
 * ProviderClients processes and responds upon request. 
 * Consumer must request an item in appropriate model.
 * 
 * ProviderClients will encode and respond with the OMM data in either MARKET_PRICE or SYMBOL_LIST model
 * depending on the request.
 * In case of a normal quote MARKET_PRICE data, after ProviderClients submits a refresh response,
 * it will register a TimerIntSpec to receive repeating EVENT.TIMER_EVENT for sending updates every 2 seconds. 
 * 
 */
public class ProviderClients implements Client
{
    private ChainPubFrame _pubFrame;
    private int _templateLength = 14;
    private ArrayList<String> _ricList;
    private static HashMap<String, HashMap<Short, String>> _chainRic;
    private OMMProvider _provider;
    private String _serviceName, _suffixRecord;

    private OMMEncoder _encoder;
    private OMMPool _pool;
    private EventQueue _eventQueue;
    private Handle _timerHandle;
    HashMap<Token, ItemObj> _itemReqTable;

    private int _updateRate;

    ProviderClients(ChainPubFrame frame)
    {
        _pubFrame = frame;
        _pool = frame.getPool();
        _provider = frame.getProvider();
        _eventQueue = frame.getEventQueue();
        _serviceName = frame.getServiceName();
        _suffixRecord = frame.getSuffixRecord();
        // _clientSessions = new HashMap();
        _updateRate = 1;

        _encoder = _pool.acquireEncoder();
        _itemReqTable = new HashMap<Token, ItemObj>();
        _chainRic = new HashMap<String, HashMap<Short, String>>();
    }

    private int _headerNum; // A number of chain headers (used for MARKET_PRICE)
    private int _refCount;  // REF_COUNT (used for MARKET_PRICE)
    private int _lastRefCount; // REF_COUNT of the last chain header (used for MARKET_PRICE)
    private int _totalLinks;   // Total number of underlying RICs
    private int _maxLinkCount = 20; // The maximum number of underlying RICs per
                                    // refresh (used for SYMBOL_LIST)
    private int _respNum; // A number of refresh response message for SYMBOL_LIST

    /*
     * To calculate a number of chain headers for chain in MARKET_PRICE model
     * and a number of refresh response fragments for SYMBOL_LIST model.
     */
    protected void initChain(ArrayList<String> ricList, String tmp)
    {
        _ricList = ricList;
        _totalLinks = _ricList.size();
        // For MARKET_PRICE
        // Calculate a number of chain headers used
        if (tmp.equalsIgnoreCase("LINK_A"))
            _templateLength = ChainPubFrame.LINK_A_SIZE;
        else if (tmp.equalsIgnoreCase("LONGLINK"))
            _templateLength = ChainPubFrame.LONGLINK_SIZE;

        _headerNum = 1;
        _lastRefCount = 0;
        _refCount = 0;
        _headerNum = _totalLinks / 14 + 1;
        _lastRefCount = _totalLinks % 14;

        String prevLR = null;
        String nextLR = null;

        // Prepare chain header RICs and their fid-value pairs
        int ptr = 0;
        for (int j = 0; j < _headerNum; j++)
        {
            String _tmpRic = j + "#" + _suffixRecord;

            if (j == _headerNum - 1)
            {// Last page
                _refCount = _lastRefCount;
                nextLR = null;
                add2ChainList(_tmpRic, _refCount, prevLR, nextLR, ptr, _ricList.size() - 1);
            }
            else
            {
                nextLR = (j + 1) + "#" + _suffixRecord;
                _refCount = 14;
                add2ChainList(_tmpRic, _refCount, prevLR, nextLR, ptr, ptr + 14 + 1);
                ptr += 14;
            }
            prevLR = _tmpRic;
        }

        // For SYMBOL_LIST
        // Calculate a number of refresh response message
        _respNum = _totalLinks / _maxLinkCount + 1;
    }

    /*
     * @see
     * com.reuters.rfa.common.Client#processEvent(com.reuters.rfa.common.Event)
     */
    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_ACTIVE_CLIENT_SESSION_PUB_EVENT: // for listen handle
                processActiveClientSessionEvent((OMMActiveClientSessionEvent)event);
                break;
            case Event.OMM_LISTENER_EVENT:
                OMMListenerEvent listenerEvent = (OMMListenerEvent)event;
                System.out.print("Received OMM LISTENER EVENT: " + listenerEvent.getListenerName());
                System.out.println("  " + listenerEvent.getStatus().toString());
                _pubFrame.textArea.append("Received OMM LISTENER EVENT: "
                        + listenerEvent.getListenerName());
                _pubFrame.textArea.append("  " + listenerEvent.getStatus().toString() + "\n");
                break;
            case Event.OMM_CMD_ERROR_EVENT: // for cmd error handle
                processOMMCmdErrorEvent((OMMCmdErrorEvent)event);
                break;

            // For active client session handle
            case Event.TIMER_EVENT:
                sendUpdates();
                break;
            case Event.OMM_INACTIVE_CLIENT_SESSION_PUB_EVENT:
                processInactiveClientSessionEvent((OMMInactiveClientSessionEvent)event);
                break;
            case Event.OMM_SOLICITED_ITEM_EVENT:
                processOMMSolicitedItemEvent((OMMSolicitedItemEvent)event);
                break;

            default:
                System.out.println("OMMPeerProviderDemo: unhandled event type: " + event.getType());
                _pubFrame.textArea.append("OMMPeerProviderDemo: unhandled event type: "
                        + event.getType());
                break;
        }
    }

    /*
     * Send updates of underlying RICs
     * Note: For underlying RICs, this app supports only MARKET_PRICE model.
     */
    private void sendUpdates()
    {
        ItemObj itemObj = null;

        Iterator<Token> iter = _itemReqTable.keySet().iterator();
        while (iter.hasNext())
        {
            Token rq = (Token)(iter.next());
            itemObj = (ItemObj)(_itemReqTable.get(rq));
            if (itemObj == null)
                continue;

            for (int i = 0; i < _updateRate; i++)
            {
                itemObj.increment();
                OMMItemCmd cmd = new OMMItemCmd();
                _encoder.initialize(OMMTypes.MSG, 500);

                OMMMsg outmsg = _pool.acquireMsg();
                outmsg.setMsgType(OMMMsg.MsgType.UPDATE_RESP);
                outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
                outmsg.setIndicationFlags(OMMMsg.Indication.DO_NOT_CONFLATE);
                outmsg.setRespTypeNum(RDMInstrument.Update.QUOTE);

                if (itemObj.getAttribInUpdates())
                {
                    outmsg.setAttribInfo(_serviceName, itemObj.getName(),
                                         RDMInstrument.NameType.RIC);
                    _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);
                }
                else
                    _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);

                _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)1,
                                             (short)0);

                _encoder.encodeFieldEntryInit((short)6, OMMTypes.REAL);
                double value = itemObj.getTradePrice1();
                int intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeFieldEntryInit((short)22, OMMTypes.REAL);
                value = itemObj.getBid();
                intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeFieldEntryInit((short)25, OMMTypes.REAL);
                value = itemObj.getAsk();
                intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeFieldEntryInit((short)32, OMMTypes.REAL);
                _encoder.encodeReal(itemObj.getACVol1(), OMMNumeric.EXPONENT_0);

                _encoder.encodeAggregateComplete();
                cmd.setMsg((OMMMsg)_encoder.getEncodedObject());
                cmd.setToken(rq);

                if (_provider.submit(cmd, null) == 0)
                {
                    System.err.println("Trying to submit for an item with an inactive handle.");
                    iter.remove();
                    break;
                }

                _pool.releaseMsg(outmsg);
            }
        }
    }

    private void processActiveClientSessionEvent(OMMActiveClientSessionEvent event)
    {
        System.out.println("Receive OMMActiveClientSessionEvent from client position : "
                + event.getClientIPAddress() + "/" + event.getClientHostName());
        _pubFrame.textArea.append("\nReceive OMMActiveClientSessionEvent from client position : "
                + event.getClientIPAddress() + "/" + event.getClientHostName() + "\n");

        Handle handle = event.getClientSessionHandle();
        OMMClientSessionIntSpec intSpec = new OMMClientSessionIntSpec();
        intSpec.setClientSessionHandle(handle);

        Handle clientSessionHandle = _provider.registerClient(_eventQueue, intSpec, this, null);
    }

    private void processOMMCmdErrorEvent(OMMCmdErrorEvent event)
    {
        System.out.println("Received OMMCmd ERROR EVENT for id: " + event.getCmdID() + "  "
                + event.getStatus().getStatusText());
        _pubFrame.textArea.append("Received OMMCmd ERROR EVENT for id: " + event.getCmdID() + "  "
                + event.getStatus().getStatusText() + "\n");
    }

    // Session was disconnected or closed.
    private void processInactiveClientSessionEvent(OMMInactiveClientSessionEvent event)
    {
        System.out.println("Received OMM INACTIVE CLIENT SESSION PUB EVENT MSG with handle: "
                + event.getHandle());
        System.out.println("ClientSession from " + event.getClientIPAddress() + "/"
                + event.getClientHostName() + " has become inactive.");

        _pubFrame.textArea
                .append("\nReceived OMM INACTIVE CLIENT SESSION PUB EVENT MSG with handle: "
                        + event.getHandle() + "\n");
        _pubFrame.textArea.append("ClientSession from " + event.getClientIPAddress() + "/"
                + event.getClientHostName() + " has become inactive.\n");

        // cleanup(false);
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
                processItemRequest(event);
        }
    }

    @SuppressWarnings("deprecation")
    private void processLoginRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();

        switch(msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                if(msg.isSet(OMMMsg.Indication.NONSTREAMING))
                {
                    System.out.println("ERROR: Received NONSTREAMING request, ignoring");
                    return;
                }
                
                System.out.println("Login request received");
                _pubFrame.textArea.append("Login request received\n");
                OMMAttribInfo attribInfo = msg.getAttribInfo();
                String username = null;
                if (attribInfo.has(OMMAttribInfo.HAS_NAME))
                    username = attribInfo.getName();
                System.out.println("username: " + username);
                _pubFrame.textArea.append("username: " + username + "\n");
                
                // Login request should contain data.
                // However, check to make sure consumer is sending data in attribInfo.
                if (attribInfo.getAttribType() != OMMTypes.NO_DATA) 
                {
                    // Data in attribInfo of LOGIN domain has been defined to be ElementList.
                    OMMElementList elementList = (OMMElementList)attribInfo.getAttrib();
    
                    for (@SuppressWarnings("unchecked")
					Iterator<OMMElementEntry> iter = elementList.iterator(); iter.hasNext();)
                    {
                        OMMElementEntry element = (OMMElementEntry)iter.next();
                        OMMData data = element.getData();
                        System.out.println(element.getName() + ": " + data.toString());
                        _pubFrame.textArea.append(element.getName() + ": " + data.toString() + "\n");
                    }
                }

                // send a response regardless of OMMMsg.Indication.REFRESH.
                OMMItemCmd cmd = new OMMItemCmd();
    
                OMMMsg outmsg = _pool.acquireMsg();
                outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
                outmsg.setMsgModelType(RDMMsgTypes.LOGIN);
                outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
                outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE,
                                "login accepted");
                if (msg.isSet(OMMMsg.Indication.REFRESH))
                    outmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
                else
                    outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
                outmsg.setAttribInfo(attribInfo);
    
                cmd.setMsg(outmsg);
                cmd.setToken(event.getRequestToken());
    
                if (_provider.submit(cmd, null) == 0)
                {
                    System.err.println("Trying to submit for an Login response msg with an inactive handle/");
                }
    
                _pool.releaseMsg(outmsg);
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
                // Closing the Login stream should cause items associated with that
                System.out.println("Logout received");
                _pubFrame.textArea.append("Logout received\n");
                // cleanup(false);
                return;
        }
    }

    @SuppressWarnings("deprecation")
    private void processDirectoryRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        OMMAttribInfo at = null;
        switch(msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                System.out.println("Directory request received");
                _pubFrame.textArea.append("Directory request received\n");
    
                // send a response regardless of OMMMsg.Indication.REFRESH.
                OMMItemCmd cmd = new OMMItemCmd();
                _encoder.initialize(OMMTypes.MSG, 1000);
                OMMMsg respmsg = _pool.acquireMsg();
                respmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
                respmsg.setMsgModelType(RDMMsgTypes.DIRECTORY);
    
                if (msg.isSet(OMMMsg.Indication.NONSTREAMING))
                    respmsg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE, "");
                else
                    respmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");

                if (msg.isSet(OMMMsg.Indication.REFRESH))
                    respmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
                else
                    respmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);

                respmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
                respmsg.setItemGroup(1);
                OMMAttribInfo outAttribInfo = _pool.acquireAttribInfo();
    
                if (msg.has(OMMMsg.HAS_ATTRIB_INFO))
                {
                    at = msg.getAttribInfo();
                    if (at.has(OMMAttribInfo.HAS_FILTER))
                    {
                        outAttribInfo.setFilter(at.getFilter());
                    }
                }
                respmsg.setAttribInfo(outAttribInfo);
    
                _encoder.encodeMsgInit(respmsg, OMMTypes.NO_DATA, OMMTypes.MAP);
                _encoder.encodeMapInit(OMMMap.HAS_TOTAL_COUNT_HINT, OMMTypes.ASCII_STRING,
                                       OMMTypes.FILTER_LIST, 1, (short)0);
                _encoder.encodeMapEntryInit(0, OMMMapEntry.Action.ADD, null);
                _encoder.encodeString(_serviceName, OMMTypes.ASCII_STRING);
    
                _encoder.encodeFilterListInit(0, OMMTypes.ELEMENT_LIST, 2);
    
                if ((outAttribInfo.getFilter() & RDMService.Filter.INFO) != 0)
                {
                    _encoder.encodeFilterEntryInit(OMMFilterEntry.HAS_DATA_FORMAT,
                                                   OMMFilterEntry.Action.SET, RDMService.FilterId.INFO,
                                                   OMMTypes.ELEMENT_LIST, null);
    
                    _encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
                    _encoder.encodeElementEntryInit(RDMService.Info.Name, OMMTypes.ASCII_STRING);
                    _encoder.encodeString(_serviceName, OMMTypes.ASCII_STRING);
                    _encoder.encodeElementEntryInit(RDMService.Info.Vendor, OMMTypes.ASCII_STRING);
                    _encoder.encodeString("Reuters", OMMTypes.ASCII_STRING);
                    _encoder.encodeElementEntryInit(RDMService.Info.IsSource, OMMTypes.UINT);
                    _encoder.encodeUInt(0);
                    _encoder.encodeElementEntryInit(RDMService.Info.Capabilities, OMMTypes.ARRAY);
                    _encoder.encodeArrayInit(OMMTypes.UINT, 0);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeUInt(RDMMsgTypes.DICTIONARY);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeUInt(RDMMsgTypes.MARKET_PRICE);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeUInt(RDMMsgTypes.SYMBOL_LIST);
                    _encoder.encodeAggregateComplete();
                    _encoder.encodeElementEntryInit(RDMService.Info.DictionariesProvided,
                                                    OMMTypes.ARRAY);
                    _encoder.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeString("RWFFld", OMMTypes.ASCII_STRING);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeString("RWFEnum", OMMTypes.ASCII_STRING);
                    _encoder.encodeAggregateComplete();
                    _encoder.encodeElementEntryInit(RDMService.Info.DictionariesUsed, OMMTypes.ARRAY);
                    _encoder.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeString("RWFFld", OMMTypes.ASCII_STRING);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeString("RWFEnum", OMMTypes.ASCII_STRING);
                    _encoder.encodeAggregateComplete();
                    _encoder.encodeElementEntryInit(RDMService.Info.QoS, OMMTypes.ARRAY);
                    _encoder.encodeArrayInit(OMMTypes.QOS, 0);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeQos(OMMQos.QOS_REALTIME_TICK_BY_TICK);
                    _encoder.encodeAggregateComplete();
                    _encoder.encodeAggregateComplete();
                }
    
                if ((outAttribInfo.getFilter() & RDMService.Filter.STATE) != 0)
                {
                    _encoder.encodeFilterEntryInit(OMMFilterEntry.HAS_DATA_FORMAT,
                                                   OMMFilterEntry.Action.UPDATE,
                                                   RDMService.FilterId.STATE, OMMTypes.ELEMENT_LIST,
                                                   null);
                    _encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
                    _encoder.encodeElementEntryInit(RDMService.SvcState.ServiceState, OMMTypes.UINT);
                    _encoder.encodeUInt(1);
                    _encoder.encodeElementEntryInit(RDMService.SvcState.AcceptingRequests,
                                                    OMMTypes.UINT);
                    _encoder.encodeUInt(1);
                    _encoder.encodeElementEntryInit(RDMService.SvcState.Status, OMMTypes.STATE);
                    _encoder.encodeState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
                    _encoder.encodeAggregateComplete();
                }
    
                _encoder.encodeAggregateComplete();
                _encoder.encodeAggregateComplete();
                cmd.setMsg((OMMMsg)_encoder.getEncodedObject());
                cmd.setToken(event.getRequestToken());
    
                if (_provider.submit(cmd, null) > 0)
                {
                    System.out.println("Directory reply sent");
                    _pubFrame.textArea.append("Directory reply sent\n");
                }
                else
                    System.err.println("Trying to submit for an item with an inactive handle.");
    
                _pool.releaseMsg(respmsg);
                _pool.releaseAttribInfo(outAttribInfo);
                return;
            }
            case OMMMsg.MsgType.STREAMING_REQ:
            case OMMMsg.MsgType.NONSTREAMING_REQ:
            case OMMMsg.MsgType.PRIORITY_REQ:
                System.out.println("Received deprecated message type of "
                        + OMMMsg.MsgType.toString(msg.getMsgType())
                        + ", not supported.");
                return;
            case OMMMsg.MsgType.CLOSE_REQ:
                System.out.println("Directory close request");
                _pubFrame.textArea.append("Directory close request\n");
                return;
        }
    }

    @SuppressWarnings("deprecation")
    private void processDictionaryRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        String name = "";

        OMMItemCmd cmd = new OMMItemCmd();
        OMMEncoder enc = _pool.acquireEncoder();
        enc.initialize(OMMTypes.MSG, 160000);
        OMMAttribInfo attribInfo = null;

        switch(msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                System.out.println("Dictionary request received");
                _pubFrame.textArea.append("Dictionary request received\n");
                attribInfo = msg.getAttribInfo();
                name = attribInfo.getName();
                System.out.println("dictionary name: " + name);
                _pubFrame.textArea.append("dictionary name: " + name + "\n");
                break;
            }
            case OMMMsg.MsgType.STREAMING_REQ:
            case OMMMsg.MsgType.NONSTREAMING_REQ:
            case OMMMsg.MsgType.PRIORITY_REQ:
                System.out.println("Received deprecated message type of "
                        + OMMMsg.MsgType.toString(msg.getMsgType())
                        + ", not supported.");
                return;
            case OMMMsg.MsgType.CLOSE_REQ:
                System.out.println("dictionary close request");
                _pubFrame.textArea.append("dictionary close request");
                return;
        }

        // send a response regardless of OMMMsg.Indication.REFRESH.
        OMMMsg outmsg = null;
        byte streamState;
        if (msg.isSet(OMMMsg.Indication.NONSTREAMING))
            streamState = OMMState.Stream.NONSTREAMING;
        else
            streamState = OMMState.Stream.OPEN;
        
        if (name.equalsIgnoreCase("rwffld"))
        {
            outmsg = encodeFldDictionary(enc, _pubFrame.getFieldDict(), streamState,
                                         msg.isSet(OMMMsg.Indication.REFRESH));
        }
        else
        // name.equalsIgnoreCase("rwfenum")
        {
            outmsg = encodeEnumDictionary(enc, _pubFrame.getFieldDict(), streamState,
                                          msg.isSet(OMMMsg.Indication.REFRESH));
        }
        cmd.setMsg(outmsg);
        cmd.setToken(event.getRequestToken());

        int ret = _provider.submit(cmd, null);
        if (ret == 0)
            System.err.println("Trying to submit for an item with an inactive handle.");

        _pool.releaseMsg(outmsg);

    }

    // Encoding of the enum dictionary
    private OMMMsg encodeEnumDictionary(OMMEncoder enc, FieldDictionary dictionary, 
            byte streamState, boolean solicited)
    {
        // This is the typical initialization of an response message.
        enc.initialize(OMMTypes.MSG, 200000);
        OMMMsg msg = _pool.acquireMsg();
        msg.clear();
        msg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        
        if (solicited)
            msg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            msg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        
        msg.setState(streamState, OMMState.Data.OK, OMMState.Code.NONE, "");
        msg.setItemGroup(1);
        OMMAttribInfo attribInfo = _pool.acquireAttribInfo();
        attribInfo.setServiceName(_serviceName);
        attribInfo.setName("RWFEnum");
        attribInfo.setFilter(RDMDictionary.Filter.NORMAL);
        msg.setAttribInfo(attribInfo);
        enc.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.SERIES);
        FieldDictionary.encodeRDMEnumDictionary(dictionary, enc);
        return (OMMMsg)enc.getEncodedObject();
    }

    // Encoding of the RDMFieldDictionary.
    private OMMMsg encodeFldDictionary(OMMEncoder enc, FieldDictionary dictionary,
            byte streamState, boolean solicited)
    {
        // This is the same message initialization as other response messages.
        enc.initialize(OMMTypes.MSG, 200000);
        OMMMsg msg = _pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);

        if (solicited)
            msg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            msg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        
        msg.setState(streamState, OMMState.Data.OK, OMMState.Code.NONE, "");
        msg.setItemGroup(1);
        OMMAttribInfo attribInfo = _pool.acquireAttribInfo();
        attribInfo.setServiceName(_serviceName);
        attribInfo.setName("RWFFld");
        attribInfo.setFilter(RDMDictionary.Filter.NORMAL);
        msg.setAttribInfo(attribInfo);
        enc.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.SERIES);
        FieldDictionary.encodeRDMFieldDictionary(dictionary, enc);
        return (OMMMsg)enc.getEncodedObject();
    }

    @SuppressWarnings("deprecation")
    private void processItemRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        Token rq = event.getRequestToken();
        // OMMItemCmd cmd = new OMMItemCmd();

        OMMMsg outmsg = null;
        GenericOMMParser.parse(msg);
        ItemObj itemObj = (ItemObj)_itemReqTable.get(rq);
        boolean refreshRequested = msg.isSet(OMMMsg.Indication.REFRESH);
        
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                if (itemObj == null)
                {
                    refreshRequested = true; // new items need refresh.
                    itemObj = new ItemObj();
                    String itemName = msg.getAttribInfo().getName();
                    itemObj.setName(itemName);
                    itemObj.setHandle(event.getHandle());
                    if (msg.isSet(OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES))
                        itemObj.setAttribInUpdates(true);
                    itemObj.setPriorityCount(1);
                    itemObj.setPriorityClass(1);
                    if(msg.isSet(OMMMsg.Indication.NONSTREAMING))
                    {
                        System.out.println("Received non-streaming item request for "
                                + msg.getAttribInfo().getServiceName() + ":" + itemName);
                        _pubFrame.textArea.append("Received non-streaming item request for "
                                + msg.getAttribInfo().getServiceName() + ":" + itemName + "\n");
                    }
                    else
                    {
                        System.out.println("Received item request for "
                                + msg.getAttribInfo().getServiceName() + ":" + itemName);
                        _pubFrame.textArea.append("Received item request for "
                                + msg.getAttribInfo().getServiceName() + ":" + itemName + "\n");
                    }
                }
                if (msg.has(OMMMsg.HAS_PRIORITY))
                {
                    OMMPriority priority = msg.getPriority();
                    itemObj.setPriorityClass(priority.getPriorityClass());
                    itemObj.setPriorityCount(priority.getCount());
                }
                break;
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
                itemObj = (ItemObj)_itemReqTable.get(rq);
                if (itemObj != null)
                {
                    System.out.println("Item close request: " + itemObj.getName());
                    _pubFrame.textArea.append("Item close request: " + itemObj.getName() + "\n");
                }
                _itemReqTable.remove(rq);
                if (_itemReqTable.isEmpty())
                    unregisterTimer();
                return;
            }
            default:
                return;
        }
        
        if(!refreshRequested)
            return;
        
        outmsg = _pool.acquireMsg();
        outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        outmsg.setMsgModelType(msg.getMsgModelType());
        outmsg.setItemGroup(2);

        if (msg.isSet(OMMMsg.Indication.REFRESH))
            outmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);

        if (msg.isSet(OMMMsg.Indication.NONSTREAMING))
            outmsg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE,
                            "OK");
        else
            outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "OK");

        String itemName = msg.getAttribInfo().getName();
        if (msg.getMsgModelType() == RDMMsgTypes.MARKET_PRICE)
        {

            outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
            outmsg.setAttribInfo(msg.getAttribInfo());

            _encoder.initialize(OMMTypes.MSG, 1000);
            _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);
            _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA | OMMFieldList.HAS_INFO,
                                         (short)0, (short)1, (short)0);

            if (_chainRic.containsKey(itemName))
            { // Encode chain in MARKET_PRICE
                HashMap<Short, String> fidVal = (HashMap<Short, String>)_chainRic.get(itemName);
                List<Short> keys = new ArrayList<Short>(fidVal.keySet());
                Collections.sort(keys); // Sort fields
                Iterator<Short> it = keys.iterator();

                while (it.hasNext())
                {
                    Short fid = (Short)it.next();
                    // Encode UINT32 for RDNDISPLAY(2), REF_COUNT(239),
                    // PREF_DISP(1080) fields.
                    if (fid.intValue() == 2 || fid.intValue() == 239 || fid.intValue() == 1080)
                    {
                        String val = (String)fidVal.get(fid);
                        _encoder.encodeFieldEntryInit(fid, OMMTypes.UINT);
                        if (val.equals(""))
                            _encoder.encodeBlank();
                        else
                            _encoder.encodeUInt(new Long(val).longValue());
                    }
                    else
                    { // Encode String for link fields.
                        _encoder.encodeFieldEntryInit(fid, OMMTypes.ASCII_STRING);
                        _encoder.encodeString((String)fidVal.get(fid), OMMTypes.ASCII_STRING);
                    }
                }

            }
            else
            { // For other RICs, we treat them as a ordinary MARKET_PRICE

                _encoder.encodeFieldEntryInit((short)2, OMMTypes.UINT); // RDNDISPLAY
                _encoder.encodeUInt(100);
                _encoder.encodeFieldEntryInit((short)4, OMMTypes.ENUM); // RDN_EXCHID
                _encoder.encodeEnum(155);
                _encoder.encodeFieldEntryInit((short)38, OMMTypes.DATE); // DIVPAYDATE
                _encoder.encodeDate(2006, 12, 25);
                _encoder.encodeFieldEntryInit((short)6, OMMTypes.REAL); // TRDPRC_1
                double value = itemObj.getTradePrice1();
                int intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeFieldEntryInit((short)22, OMMTypes.REAL); // BID
                value = itemObj.getBid();
                intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeFieldEntryInit((short)25, OMMTypes.REAL); // ASK
                value = itemObj.getAsk();
                intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeFieldEntryInit((short)32, OMMTypes.REAL); // ACVOL_1
                _encoder.encodeReal(itemObj.getACVol1(), OMMNumeric.EXPONENT_0);
                _encoder.encodeFieldEntryInit((short)267, OMMTypes.TIME); // ASK_TIME
                _encoder.encodeTime(19, 12, 23, 0);

                _itemReqTable.put(rq, itemObj);
            }

            _encoder.encodeAggregateComplete();
            submitOMMCmd((OMMMsg)_encoder.getEncodedObject(), rq);
            if (_itemReqTable.size() == 1)
                registerTimer();

        }
        else if (msg.getMsgModelType() == RDMMsgTypes.SYMBOL_LIST)
        {
            // Encode SYMBOL_LIST
            if (!itemName.equals("0#" + _suffixRecord))
            {
                outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
                outmsg.setAttribInfo(msg.getAttribInfo());
                outmsg.setState(OMMState.Stream.CLOSED, OMMState.Data.SUSPECT, OMMState.Code.NONE,
                                "Not In Cache");
                submitOMMCmd(outmsg, rq);

            }
            else
            {

                int ptr = 0;
                for (int m = 0; m < _respNum; m++)
                {
                    if (m == _respNum - 1) // Last fragment
                        outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);

                    outmsg.setAttribInfo(msg.getAttribInfo());
                    _encoder.initialize(OMMTypes.MSG, 1000);
                    _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.MAP);

                    int flags = 0;
                    flags = OMMMap.HAS_DATA_DEFINITIONS | OMMMap.HAS_PERMISSION_DATA_PER_ENTRY
                            | OMMMap.HAS_TOTAL_COUNT_HINT;
                    // Map Header
                    _encoder.encodeMapInit(flags, OMMTypes.ASCII_STRING, OMMTypes.FIELD_LIST, 20,
                                           (short)0);
                    // Data Definition of FieldList
                    _encoder.encodeDataDefsInit();
                    _encoder.encodeFieldListDefInit((short)1); // Data
                                                               // Definition 1
                    _encoder.encodeFieldEntryDef((short)3422, OMMTypes.RMTES_STRING); // PROV_SYMB
                    _encoder.encodeFieldEntryDef((short)1, OMMTypes.INT); // PROD_PERM
                    _encoder.encodeListDefComplete(); // End of Definition 1
                    _encoder.encodeDataDefsComplete(); // End of Data
                                                       // Definitions
                    // Map Entries
                    for (int i = ptr; i < _maxLinkCount + ptr && i < _totalLinks; i++)
                    {
                        _encoder.encodeMapEntryInit(0, OMMMapEntry.Action.ADD, null);
                        // Key
                        _encoder.encodeString((String)_ricList.get(i), OMMTypes.ASCII_STRING);
                        // Value
                        _encoder.encodeFieldListInit(OMMFieldList.HAS_DEFINED_DATA
                                | OMMFieldList.HAS_DATA_DEF_ID, (short)0, (short)0, (short)1);
                        // encode defined data for definition 1
                        _encoder.encodeString((String)_ricList.get(i), OMMTypes.RMTES_STRING);
                        _encoder.encodeInt(530);
                    }
                    ptr += _maxLinkCount;

                    _encoder.encodeAggregateComplete(); // MAP
                    submitOMMCmd((OMMMsg)_encoder.getEncodedObject(), rq);
                }
            }
            _pubFrame.textArea.append(itemName + " responded\n");
        }
        else
        {
            // System.out.println("Request other than MARKET_PRICE and SYMBOL_LIST");
            _pubFrame.textArea.append("Request other than MARKET_PRICE and SYMBOL_LIST");
            _pubFrame.textArea.append("Currently, StarterProviderInteractive_Chain supports MARKET_PRICE and SYMBOL_LIST only");
        }
        _pool.releaseMsg(outmsg);
    }

    OMMItemCmd cmd = new OMMItemCmd();

    private void submitOMMCmd(OMMMsg msg, Token token)
    {
        cmd.setMsg(msg);
        cmd.setToken(token);

        if (_provider.submit(cmd, null) > 0)
        {
            System.out.println("Reply sent");
        }
        else
        {
            System.err.println("Trying to submit for an item with an inactive handle.");
            _itemReqTable.remove(token);
        }
    }

    private void registerTimer()
    {
        if (_timerHandle == null)
        {
            TimerIntSpec timer = new TimerIntSpec();
            timer.setDelay(2000);
            timer.setRepeating(true);
            _timerHandle = _provider.registerClient(_eventQueue, timer, this, null);
        }
    }

    private void unregisterTimer()
    {
        if (_timerHandle != null)
        {
            _provider.unregisterClient(_timerHandle);
            _timerHandle = null;
        }
    }

    /*
     * Add chain headers and their fid-value pairs into (Hashmap)_chainRic.
     */
    private void add2ChainList(String ric, int ref, String prev, String next, int start, int end)
    {
        HashMap<Short, String> fidVal = new HashMap<Short, String>();
        fidVal.put(new Short((short)239), ref + ""); // REF_COUNT
        fidVal.put(new Short((short)3), ""); // DSPLY_NAME
        fidVal.put(new Short((short)2), "153"); // RDNDISPLAY
        fidVal.put(new Short((short)1080), ""); // PREF_DISP
        fidVal.put(new Short((short)1081), ""); // PREF_LINK

        if (_templateLength == 14)
        {
            fidVal.put(new Short((short)237), prev == null ? "" : prev); // PREF_LR
            fidVal.put(new Short((short)238), next == null ? "" : next); // NEXT_LR

            for (int k = 0; k < 14; k++)
            {
                if (k < end - start + 1)
                {
                    fidVal.put(new Short((short)(240 + k)), (String)_ricList.get(k + start)); // LINK_x
                }
                else
                    fidVal.put(new Short((short)(240 + k)), "");
            }
        }
        else if (_templateLength == 21)
        {
            fidVal.put(new Short((short)814), prev == null ? "" : prev); // LONGPREVLR
            fidVal.put(new Short((short)815), next == null ? "" : next); // LONGNEXTLR

            for (int k = 0; k < 14; k++)
            {
                if (k < end - start + 1)
                {
                    fidVal.put(new Short((short)(800 + k)), (String)_ricList.get(k + start)); // LONGLINKx
                }
                else
                    fidVal.put(new Short((short)(800 + k)), "");
            }
        }

        _chainRic.put(ric, fidVal);
    }
}
