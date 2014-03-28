package com.reuters.rfa.example.omm.postingProvider;

import java.util.HashMap;
import java.util.Iterator;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.PublisherPrincipalIdentity;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.omm.hybrid.OMMMsgReencoder;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.ExampleUtil;
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
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.TimerIntSpec;
import com.reuters.rfa.session.omm.OMMInactiveClientSessionEvent;
import com.reuters.rfa.session.omm.OMMItemCmd;
import com.reuters.rfa.session.omm.OMMSolicitedItemEvent;

/**
 * Client class to handle RFA events, processes requests, send responses
 * process posts, send acks and re-send OMM Messages received via posts.
 * 
 */
public class PostProviderClientSession implements Client
{
    private final StarterProvider_Post _appPostProviderDemo;
    private OMMEncoder _encoder;
    private OMMPool _pool;
    protected Handle _clientSessionHandle;

    private Handle _timerHandle;

    HashMap<Token, ItemInfo> _itemReqTable;
    Token _loginRequestToken;

    int _postMessageCount;
    int _itemRequestCount;

    private String _servicename;
    private String _clientName;
    boolean _supportPar = false;

    OMMMsg _ommAckMsg = null;
    OMMEncoder _ackEncoder = null;
    OMMState _ackState;
    OMMItemCmd _submitCmd;
    PublisherPrincipalIdentity _ppi;

    String INFO_APPNAME;
    String APPNAME;

    int _solicitedItemEventCount;

    /**
     * Constructor
     */
    public PostProviderClientSession(StarterProvider_Post app, String serviceName, String clientName)
    {
        _appPostProviderDemo = app;
        _pool = app._pool;
        _encoder = _pool.acquireEncoder();

        _servicename = serviceName;
        _clientName = clientName;
        _itemReqTable = new HashMap<Token, ItemInfo>();

        _ppi = new PublisherPrincipalIdentity();
        _ppi.setPublisherAddress(0x12345678);
        _ppi.setPublisherId(1987);

        _ommAckMsg = _pool.acquireMsg();
        _ackEncoder = _pool.acquireEncoder();
        _ackState = _pool.acquireState();
        _submitCmd = new OMMItemCmd();

        INFO_APPNAME = _appPostProviderDemo.INFO_APPNAME;
        APPNAME = _appPostProviderDemo.APPNAME;
    }

    /**
     * Cleanup
     */
    public void cleanup(boolean shuttingDown)
    {
        if (!shuttingDown && _appPostProviderDemo._clientSessions.containsKey(_clientSessionHandle))
        {
            _appPostProviderDemo._provider.unregisterClient(_clientSessionHandle);
            _appPostProviderDemo._clientSessions.remove(_clientSessionHandle);
        }

        _itemReqTable.clear();
        _supportPar = false;
        unregisterTimer();

        if (_encoder != null)
        {
            _pool.releaseEncoder(_encoder);
            _encoder = null;
        }

        if (_ackEncoder != null)
        {
            _pool.releaseEncoder(_ackEncoder);
            _ackEncoder = null;
        }

        if (_ommAckMsg != null)
        {
            _pool.releaseMsg(_ommAckMsg);
            _ommAckMsg = null;
        }
    }

    /**
     * Application implemented method to process events delivered by RFA
     */
    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.TIMER_EVENT:
                sendUpdates();
                break;
            case Event.OMM_INACTIVE_CLIENT_SESSION_PUB_EVENT: // for client session handle
                processInactiveClientSessionEvent((OMMInactiveClientSessionEvent)event);
                break;
            case Event.OMM_SOLICITED_ITEM_EVENT: // for client session handle
                System.out.format("%n//......................................................................//%n");
                System.out.format("// %s Start - processOMMSolicitedItemEvent() - %d -%n", APPNAME,
                                  ++_solicitedItemEventCount);
                processOMMSolicitedItemEvent((OMMSolicitedItemEvent)event);
                System.out.format("%n// %s End - processOMMSolicitedItemEvent() - %d -%n", APPNAME,
                                  _solicitedItemEventCount);
                System.out.format("//////////////////////////////////////////////////////////////////////////%n");
                break;
            default:
                System.out.println("Unhandled event type: " + event.getType());
                break;
        }
    }

    /**
     * Session was disconnected or closed.
     */
    protected void processInactiveClientSessionEvent(OMMInactiveClientSessionEvent event)
    {
        System.out.println("Received OMM INACTIVE CLIENT SESSION PUB EVENT MSG with handle: "
                + event.getHandle());
        System.out.println("ClientSession from " + event.getClientIPAddress() + "/"
                + event.getClientHostName() + "/" + event.getListenerName()
                + " has become inactive.");

        cleanup(false);
    }

    /**
     * This event is for processing of {@linkplain OMMSolicitedItemEvent}. Each
     * event is associated with a particular Reuters defined domain or customer
     * defined domains.
     * 
     * @param event
     */
    protected void processOMMSolicitedItemEvent(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();

        switch (msg.getMsgModelType())
        {
            // Reuters defined domain message model - LOGIN
            case RDMMsgTypes.LOGIN:
                processLoginRequest(event);
                break;
            case RDMMsgTypes.DIRECTORY:
                // Reuters defined domain message model - DIRECTORY
                processDirectoryRequest(event);
                break;
            case RDMMsgTypes.DICTIONARY:
                // Reuters defined domain message model - DICTIONARY
                processDictionaryRequest(event);
                break;
            default: // All other reuters defined domain message model or
                     // customer's domain message model are considered items.
                processItemRequest(event);
        }
    }

    /**
     * Currently, this method will automatically accept all incoming item
     * requests.
     * 
     * @param event Event that contains the incoming item request.
     * @see com.reuters.rfa.session.omm.OMMSolicitedItemEvent
     */
    @SuppressWarnings("deprecation")
    private void processItemRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        
        // Token is associated for each unique request.
        Token rq = event.getRequestToken();
        
        OMMMsg outmsg = null;
        // See if we have an ItemInfo associated with the token.
        ItemInfo itemInfo = (ItemInfo)_itemReqTable.get(rq);

        Handle hd = event.getHandle();

        // process post messages
        if (msg.getMsgType() == OMMMsg.MsgType.POST)
        {
            processPostMessage(itemInfo, event.getRequestToken(), msg);
            return;
        }

        boolean refreshRequested = msg.isSet(OMMMsg.Indication.REFRESH);
        // Check to see if the message type if a streaming request.
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                String name = msg.getAttribInfo().getName();
                if (itemInfo == null)
                {
                    refreshRequested = true; // new item needs refresh.
                    itemInfo = new ItemInfo();
                    itemInfo.setName(name);
                    if (msg.isSet(OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES))
                        itemInfo.setAttribInUpdates(true);
                    
                    if(msg.isSet(OMMMsg.Indication.NONSTREAMING))
                    {
                        dumpInfo("Received non-streaming item request for "
                                + msg.getAttribInfo().getServiceName() + ":" + name);
                    }
                    else
                    {
                        dumpInfo("Received item request " + ++_itemRequestCount + " for "
                                + msg.getAttribInfo().getServiceName() + ":" + name);
                        itemInfo.setHandle(event.getHandle());
                        itemInfo._requestToken = rq;
                        _itemReqTable.put(rq, itemInfo);
                        itemInfo.setPriorityCount(1);
                        itemInfo.setPriorityClass(1);
                    }
                }
                else
                // Re-issue request
                {
                    dumpInfo(INFO_APPNAME + "Received item reissue for "
                            + msg.getAttribInfo().getServiceName() + ":" + name);
                }

                dumpOMMMsg(msg, "Message Dump");
                
                if (_supportPar)
                {
                    if (msg.isSet(OMMMsg.Indication.PAUSE_REQ))
                        itemInfo.setPaused(true); // pause request.
                    else
                        itemInfo.setPaused(false); // not paused.
                }
                
                if (msg.has(OMMMsg.HAS_PRIORITY)) // Check if the streaming request
                                                  // has priority.
                {
                    OMMPriority priority = msg.getPriority();
                    itemInfo.setPriorityClass(priority.getPriorityClass());
                    itemInfo.setPriorityCount(priority.getCount());
                }
                
                if(!refreshRequested)
                    return;
                
                outmsg = _pool.acquireMsg();
                
                // Set the message type to be refresh response.
                outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
                
                // Set the message model type to be the type requested.
                outmsg.setMsgModelType(msg.getMsgModelType()); 
                
                // pass version info
                if (hd != null)
                {
                    outmsg.setAssociatedMetaInfo(hd);
                }
    
                // Indicates this message will be the full refresh;
                // or this is the last refresh in the multi-part refresh.
                outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
                // outmsg.setItemGroup(2); // Set the item group to be 2. Indicates
                // the item will be in group 2.
                
                // Set the state of the item stream. Stream state could be
                // OPEN or NONSTREAMING.
                if(!msg.isSet(OMMMsg.Indication.NONSTREAMING))
                {
                    // Indicate the stream is streaming (Stream.OPEN) and the data
                    // provided is OK.
                    outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "OK");
                }
                else
                {
                    // Indicate the stream is nonstreaming and the data provided is OK.
                    outmsg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE,
                                    "OK");
                    outmsg.setItemGroup(2);
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
                itemInfo = (ItemInfo)_itemReqTable.get(rq);
                if (itemInfo != null)
                    dumpInfo(INFO_APPNAME + "Item close request: " + itemInfo.getName());
                _itemReqTable.remove(rq); // removes the reference to the Token
                                          // associated for the item and its ItemInfo.
                if (_itemReqTable.isEmpty())
                    unregisterTimer();
                return;
            case OMMMsg.MsgType.GENERIC:
                System.out.println("Received generic message type, not supported. ");
                return;
            default:
                System.out.println("ERROR: Received unexpected message type. " + msg.getMsgType());
                return;
        }
        
        // This section is similar to sendUpdates() function, with additional
        // fields. Please see sendUpdates for detailed description.
        if (msg.getMsgModelType() == RDMMsgTypes.MARKET_PRICE)
        {
            outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
            if(msg.isSet(OMMMsg.Indication.REFRESH))
                outmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
            else
                outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
            outmsg.setAttribInfo(msg.getAttribInfo());
            outmsg.setSeqNum(34);

            if (_appPostProviderDemo._bSendPublisherInfo_ip == true)
            {
                _ppi.setPublisherAddress(0x12341234);
                _ppi.setPublisherId(2010);

                outmsg.setPrincipalIdentity(_ppi);
            }

            _encoder.initialize(OMMTypes.MSG, 1000);

            short attribDataType = OMMTypes.NO_DATA;
            if (msg.getAttribInfo() != null)
                attribDataType = msg.getAttribInfo().getAttribType();

            _encoder.encodeMsgInit(outmsg, attribDataType, OMMTypes.FIELD_LIST);

            _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA | OMMFieldList.HAS_INFO,
                                         (short)0, (short)1, (short)0);
            // RDNDISPLAY
            _encoder.encodeFieldEntryInit((short)2, OMMTypes.UINT);
            _encoder.encodeUInt(100);
            // RDN_EXCHID
            _encoder.encodeFieldEntryInit((short)4, OMMTypes.ENUM);
            _encoder.encodeEnum(155);
            // DIVIDEND_DATE
            _encoder.encodeFieldEntryInit((short)38, OMMTypes.DATE);
            _encoder.encodeDate(2006, 12, 25);
            // TRDPRC_1
            _encoder.encodeFieldEntryInit((short)6, OMMTypes.REAL);
            double value = itemInfo.getTradePrice1();
            int intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
            
            // Encode the real number with the price and the hint value.
            _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);
            
            // BID
            // Initialize the entry with the field id
            // and data type from RDMFieldDictionary for BID.
            _encoder.encodeFieldEntryInit((short)22, OMMTypes.REAL);
            
            value = itemInfo.getBid();
            intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
            _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);
            
            // ASK
            // Initialize the entry with the field id
            // and data type from RDMFieldDictionary for ASK.
            _encoder.encodeFieldEntryInit((short)25, OMMTypes.REAL);
            
            value = itemInfo.getAsk();
            intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
            _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);

            // ACVOL_1
            _encoder.encodeFieldEntryInit((short)32, OMMTypes.REAL);
            _encoder.encodeReal(itemInfo.getACVol1(), OMMNumeric.EXPONENT_0);
            // ASK_TIME
            _encoder.encodeFieldEntryInit((short)267, OMMTypes.TIME);
            _encoder.encodeTime(19, 12, 23, 0);

            _encoder.encodeAggregateComplete();
            _submitCmd.setMsg((OMMMsg)_encoder.getEncodedObject());
            _submitCmd.setToken(event.getRequestToken());

            if (_appPostProviderDemo._provider.submit(_submitCmd, null) > 0)
            {
                System.out.println(INFO_APPNAME + " sent reply");
            }
            else
            {
                System.err.println("Trying to submit for an item with an inactive handle.");
                _itemReqTable.remove(rq); // removes the reference to the Token
                                          // associated for the item and its ItemInfo.
            }

            // send updates if configured
            if (_appPostProviderDemo._bSendUpdates_ip == true)
            {
                if (_itemReqTable.size() == 1)
                    registerTimer();
            }
        }
        else
        {
            System.out.println("Request other than MARKET_PRICE");
            System.out.println("Currently, StarterProvider_Post supports MARKET_PRICE only");
        }
        _pool.releaseMsg(outmsg);
    }

    /*
     * send updates to open items; this is configurable
     */
    private void sendUpdates()
    {
        ItemInfo itemInfo = null;

        Iterator<Token> iter = _itemReqTable.keySet().iterator();
        while (iter.hasNext())
        {
            Token rq = (Token)(iter.next());
            itemInfo = (ItemInfo)(_itemReqTable.get(rq));
            // Do not send update for the paused item.
            if (itemInfo == null || itemInfo.isPaused())
                continue;

            for (int i = 0; i < _appPostProviderDemo._updateRate_ip; i++)
            {
                // increment the canned data information for this item.
                itemInfo.increment();

                // The size is an
                // estimate. The size
                // MUST be large enough
                // for the entire
                // message.
                _encoder.initialize(OMMTypes.MSG, 500);
                // If size is not large enough, RFA will throw out exception
                // related to buffer usage.
                
                // OMMMsg is a poolable object.
                OMMMsg outmsg = _pool.acquireMsg();
                
                // Set the message type to be an update response.
                outmsg.setMsgType(OMMMsg.MsgType.UPDATE_RESP);
                
                // Set the message model to be MARKET_PRICE.
                // (all other item message models are not supported by this application)
                outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
                
                // Set the indication flag for data not to be conflated.
                outmsg.setIndicationFlags(OMMMsg.Indication.DO_NOT_CONFLATE);
                
                // Set the response type to be a quote update.
                outmsg.setRespTypeNum(RDMInstrument.Update.QUOTE);

                if (_appPostProviderDemo._bSendPublisherInfo_ip == true)
                {
                    _ppi.setPublisherAddress(0xAABBCCDD);
                    _ppi.setPublisherId(1990);

                    outmsg.setPrincipalIdentity(_ppi);
                }

                Handle hd = itemInfo.getHandle();

                // pass version info
                if (hd != null)
                {
                    outmsg.setAssociatedMetaInfo(hd);
                }

                if (itemInfo.getAttribInUpdates()) // check if the original
                                                   // request for this item
                                                   // asked for attrib info to
                                                   // be included in the
                                                   // updates.
                {
                    outmsg.setAttribInfo(_servicename, itemInfo.getName(),
                                         RDMInstrument.NameType.RIC);

                    // Initialize message encoding with the update response message.
                    // No data is contained in the OMMAttribInfo, so use NO_DATA
                    // for the data type of OMMAttribInfo
                    // There will be data encoded to the update response message
                    // (and we know it is FieldList), so use FIELD_List
                    // for the data type of the update response message.
                    _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);
                }
                else
                {
                    // Since no attribInfo is requested in the updates, don't
                    // set attribInfo into the update response message.
                    // No OMMAttribInfo in the message defaults to NO_DATA for
                    // OMMAttribInfo.
                    // There will be data encoded to the update response message
                    // (and we know it is FieldList), so use FIELD_List
                    // for the data type of the update response message.
                    _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);
                }

                // Initialize the field list encoding.
                // Specifies that this field list has only standard data (data
                // that is not defined in a DataDefinition)
                // DictionaryId is set to 0. This means the data encoded in this
                // message used dictionary identified by id equal to 0.
                // Field list number is set to 1. This identifies the field list
                // (in case for caching in the application or downstream
                // components).
                // Data definition id is set to 0 since standard data does not
                // use data definition.
                // Large data is set to false. This is used since updates in
                // general are not large in size. (If unsure, use true)
                _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)1,
                                             (short)0);

                // TRDPRC_1
                // Initialize the entry with the field id
                // and data type from RDMFieldDictionary for TRDPRC_1.
                _encoder.encodeFieldEntryInit((short)6, OMMTypes.REAL);
                
                double value = itemInfo.getTradePrice1();
                int intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
                
                // Encode the real number with the price and the hint value.
                _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);
                
                // BID
                // Initialize the entry with the field id
                // and data type from RDMFieldDictionary for BID.
                _encoder.encodeFieldEntryInit((short)22, OMMTypes.REAL);
                
                value = itemInfo.getBid();
                intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);
                
                // ASK
                // Initialize the entry with the field id
                // and data type from RDMFieldDictionary for ASK.
                _encoder.encodeFieldEntryInit((short)25, OMMTypes.REAL);
                
                value = itemInfo.getAsk();
                intValue = Rounding.roundFloat2Int((float)value, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeReal(intValue, OMMNumeric.EXPONENT_NEG4);
                
                // ACVOL_1
                // Initialize the entry with the field id
                // and data type from RDMFieldDictionary for ACVOL_1.
                _encoder.encodeFieldEntryInit((short)32, OMMTypes.REAL);
                
                _encoder.encodeReal(itemInfo.getACVol1(), OMMNumeric.EXPONENT_0);

                _encoder.encodeAggregateComplete(); // Complete the FieldList.
                
                // Set the update response message into OMMItemCmd.
                _submitCmd.setMsg((OMMMsg)_encoder.getEncodedObject());
                
                _submitCmd.setToken(rq); // Set the request token associated
                                         // with this item into the OMMItemCmd.

                // Submit the OMMItemCmd to RFAJ.
                // Note the closure of the submit is null. Any closure set by
                // the application must be long lived memory.
                if (_appPostProviderDemo._provider.submit(_submitCmd, null) == 0)
                {
                    System.err.println("Trying to submit for an item with an inactive handle.");
                    iter.remove();
                    break; // break out the for loop to get next request token
                }

                _pool.releaseMsg(outmsg);
            }
        }
    }

    /*
     * handle dictionary request
     */
    @SuppressWarnings("deprecation")
    private void processDictionaryRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        String name = "";

        // OMMItemCmd cmd = new OMMItemCmd();
        OMMEncoder enc = _appPostProviderDemo._pool.acquireEncoder();
        // Dictionary tends to be large messages. So 160k bytes is needed here.
        enc.initialize(OMMTypes.MSG, 160000);
        
        OMMAttribInfo attribInfo = null;

        // OMMAttribInfo will be in the streaming and nonstreaming request.
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
                System.out.println(INFO_APPNAME + "Dictionary request received");
                attribInfo = msg.getAttribInfo();
                name = attribInfo.getName();
                System.out.println("dictionary name: " + name);
                break;
            case OMMMsg.MsgType.STREAMING_REQ:
            case OMMMsg.MsgType.NONSTREAMING_REQ:
            case OMMMsg.MsgType.PRIORITY_REQ:
                System.out.println("Received deprecated message type of "
                        + OMMMsg.MsgType.toString(msg.getMsgType())
                        + ", not supported. ");
                return;
            case OMMMsg.MsgType.CLOSE_REQ:
                // RFA internally will clean up the item.
                // Application has placed the directory on its item info lookup
                // table, so no cleanup is needed here.
                System.out.println(INFO_APPNAME + "dictionary close request");
                return;
            case OMMMsg.MsgType.GENERIC:
                System.out.println("Received generic message type, not supported. ");
                return;
           default:
            System.out.println("ERROR: Received unexpected message type. " + msg.getMsgType());
            return;
        }
        
        OMMMsg outmsg = null;
        byte streamState;
        if (msg.isSet(OMMMsg.Indication.NONSTREAMING))
            streamState = OMMState.Stream.NONSTREAMING; // Stream state could be
                                                        // OPEN or NONSTREAMING.
        else
            streamState = OMMState.Stream.OPEN;

        Handle hd = event.getHandle();
        if (name.equalsIgnoreCase("rwffld"))
        {
            outmsg = encodeFldDictionary(enc, _appPostProviderDemo._rwfDictionary, streamState, hd,
                                         msg.isSet(OMMMsg.Indication.REFRESH));
        }
        else
        // name.equalsIgnoreCase("rwfenum")
        {
            outmsg = encodeEnumDictionary(enc, _appPostProviderDemo._rwfDictionary, streamState, hd,
                                          msg.isSet(OMMMsg.Indication.REFRESH));
        }
        _submitCmd.setMsg(outmsg);
        _submitCmd.setToken(event.getRequestToken());

        int ret = _appPostProviderDemo._provider.submit(_submitCmd, null);
        if (ret == 0)
            System.err.println("Trying to submit for an item with an inactive handle.");

        _pool.releaseMsg(outmsg);
    }

    /*
     * handle directory request
     */
    @SuppressWarnings("deprecation")
    private void processDirectoryRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        OMMAttribInfo at = null;
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                dumpInfo("Directory request received");
    
                // Initialize the encoding to size 1000.
                // Currently it is not resizable.
                _encoder.initialize(OMMTypes.MSG, 1000);
                
                OMMMsg respmsg = _pool.acquireMsg();
                respmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
                respmsg.setMsgModelType(RDMMsgTypes.DIRECTORY);
    
                Handle hd = event.getHandle();
    
                // pass version info
                if (hd != null)
                {
                    respmsg.setAssociatedMetaInfo(hd);
                }
    
                byte streamState; // Stream state could be OPEN or NONSTREAMING
                if (msg.isSet(OMMMsg.Indication.NONSTREAMING))
                    streamState = OMMState.Stream.NONSTREAMING; 
                else
                    streamState = OMMState.Stream.OPEN;
                respmsg.setState(streamState, OMMState.Data.OK, OMMState.Code.NONE, "");
    
                respmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
                if (msg.isSet(OMMMsg.Indication.REFRESH))
                    respmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
                else
                    respmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
                respmsg.setItemGroup(1);
                OMMAttribInfo outAttribInfo = _pool.acquireAttribInfo();
    
                // Specifies what type of information is provided.
                // We will encode what the information that is being requested (only
                // INFO, STATE, and GROUP is supported in the application).
                if (msg.has(OMMMsg.HAS_ATTRIB_INFO))
                {
                    at = msg.getAttribInfo();
                    if (at.has(OMMAttribInfo.HAS_FILTER))
                    {
                        // Set the filter information to what was requested.
                        outAttribInfo.setFilter(at.getFilter());
                    }
                }
                // Set the attribInfo into the message.
                respmsg.setAttribInfo(outAttribInfo);
    
                // Initialize the response message encoding that contains no data in
                // attribInfo, and MAP data in the message.
                _encoder.encodeMsgInit(respmsg, OMMTypes.NO_DATA, OMMTypes.MAP);
    
                // Map encoding initialization.
                // Specifies the flag for the map, the data type of the key as
                // ascii_string, as defined by RDMUsageGuide.
                // the data type of the map entries is FilterList, the total count
                // hint, and the dictionary id as 0
                _encoder.encodeMapInit(OMMMap.HAS_TOTAL_COUNT_HINT, OMMTypes.ASCII_STRING,
                                       OMMTypes.FILTER_LIST, 1, (short)0);
    
                // service name - start
                // MapEntry: Each service is associated with a map entry with the
                // service name has the key.
                _encoder.encodeMapEntryInit(0, OMMMapEntry.Action.ADD, null);
                _encoder.encodeString(_servicename, OMMTypes.ASCII_STRING);
    
                // Filter list encoding initialization.
                // Specifies the flag is 0, the data type in the filter entry as
                // element list, and total count hint of 2 entries.
                _encoder.encodeFilterListInit(0, OMMTypes.ELEMENT_LIST, 2);
    
                // Only encode the service info if the application had asked for the
                // information.
                if ((outAttribInfo.getFilter() & RDMService.Filter.INFO) != 0)
                {
                    // Specifies the filter entry has data, action is SET, the
                    // filter id is the filter information, and data type is
                    // elementlist. No permission data is provided.
                    _encoder.encodeFilterEntryInit(OMMFilterEntry.HAS_DATA_FORMAT,
                                                   OMMFilterEntry.Action.SET, RDMService.FilterId.INFO,
                                                   OMMTypes.ELEMENT_LIST, null);
    
                    // Specifies the elementlist has only standard data, no data
                    // definitions for
                    // DefinedData (since we don't have that here), and false for
                    // largedata (we assume the data here won't be big)
                    _encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
                    _encoder.encodeElementEntryInit(RDMService.Info.Name, OMMTypes.ASCII_STRING);
                    
                    // Encoding the string value servicename as an ASCII string.
                    _encoder.encodeString(_servicename, OMMTypes.ASCII_STRING);
                    _encoder.encodeElementEntryInit(RDMService.Info.Vendor, OMMTypes.ASCII_STRING);
                    
                    // Encoding the string value "Reuters" as an ASCII string.
                    _encoder.encodeString("Reuters", OMMTypes.ASCII_STRING);
                    _encoder.encodeElementEntryInit(RDMService.Info.IsSource, OMMTypes.UINT);
                    _encoder.encodeUInt(0); // Encoding the 0 value as unsigned int.
                    
                    // Specifies entry contains an ARRAY.
                    _encoder.encodeElementEntryInit(RDMService.Info.Capabilities, OMMTypes.ARRAY);
                    
                    // Specifies the ARRAY will have type UINT in all of its entries.
                    // We could have specified the 8 bytes as the size
                    // for UINT since all UINT are same size.
                    // We passed in 0 as the size. This lets the encoder to calculate
                    // the size for each UINT.
                    _encoder.encodeArrayInit(OMMTypes.UINT, 0);
                    
                    _encoder.encodeArrayEntryInit(); // Must be called, even though
                                                     // no parameter is passed in.
                    _encoder.encodeUInt(RDMMsgTypes.DICTIONARY);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeUInt(RDMMsgTypes.MARKET_PRICE);
                    _encoder.encodeAggregateComplete(); // Completes the Array.
                    
                    // Specifies an ARRAY in the element entry.
                    _encoder.encodeElementEntryInit(RDMService.Info.DictionariesProvided,
                                                    OMMTypes.ARRAY);                
                    
                    // This array will contain ASCII_STRING data types in its entries.
                    // Since size of each string is different, 0 is passed in.
                    _encoder.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeString("RWFFld", OMMTypes.ASCII_STRING);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeString("RWFEnum", OMMTypes.ASCII_STRING);
                    _encoder.encodeAggregateComplete(); // Completes the Array.
                    _encoder.encodeElementEntryInit(RDMService.Info.DictionariesUsed, OMMTypes.ARRAY);
                    _encoder.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeString("RWFFld", OMMTypes.ASCII_STRING);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeString("RWFEnum", OMMTypes.ASCII_STRING);
                    _encoder.encodeAggregateComplete(); // Completes the Array.
                    
                    // This array entries data types are Qos.
                    _encoder.encodeElementEntryInit(RDMService.Info.QoS, OMMTypes.ARRAY);   
                    
                    _encoder.encodeArrayInit(OMMTypes.QOS, 0);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeQos(OMMQos.QOS_REALTIME_TICK_BY_TICK);
                    _encoder.encodeAggregateComplete(); // Completes the Array.
                    _encoder.encodeAggregateComplete(); // Completes the ElementList
                }
    
                // Only encode the service state information if the application had
                // asked for the information.
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
                    _encoder.encodeAggregateComplete(); // Completes the
                                                        // ElementList.
                }
                
                // any type that requires a count needs to be closed.
                // This one is for FilterList.
                _encoder.encodeAggregateComplete();
                
                // service name - end
    
                // any type that requires a count needs to be closed.
                // This one is for Map.
                _encoder.encodeAggregateComplete();
    
                _submitCmd.setMsg((OMMMsg)_encoder.getEncodedObject());
                _submitCmd.setToken(event.getRequestToken());
    
                if (_appPostProviderDemo._provider.submit(_submitCmd, null) > 0)
                    dumpInfo("Directory reply sent");
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
                        + ", not supported. ");
                return;
            case OMMMsg.MsgType.CLOSE_REQ:
                // RFA internally will clean up the item.
                // Application has placed the directory on its item info lookup
                // table, so no cleanup is needed here.
                System.out.println("Directory close request");
                return;
            case OMMMsg.MsgType.GENERIC:
                System.out.println("Received generic message type, not supported. ");
                return;
            default:
                System.out.println("ERROR: Received unexpected message type. " + msg.getMsgType());
                return;
        }
    }

    /*
     * process login request; on granting login, include posting support
     */
    @SuppressWarnings("deprecation")
    private void processLoginRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();

        dumpOMMMsg(msg, "Message Dump");

        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                if(msg.isSet(OMMMsg.Indication.NONSTREAMING))
                {
                    System.out.println("ERROR: Received NONSTREAMING request, ignoring");
                    return;
                }
                
                _loginRequestToken = event.getRequestToken();
    
                dumpInfo("Login request received");
    
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
                OMMAttribInfo requestAttribInfo = msg.getAttribInfo();
                if (requestAttribInfo.has(OMMAttribInfo.HAS_NAME))
                    System.out.println("username: " + requestAttribInfo.getName());
    
                ExampleUtil.dumpAttribDataElements(requestAttribInfo);
    
                OMMAttribInfo responseAttribInfo = _pool.acquireAttribInfo();
                try
                {
                    ExampleUtil.duplicateAttribInfoHeader(requestAttribInfo, responseAttribInfo,false);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
    
                outmsg.setAttribInfo(responseAttribInfo);
                _pool.releaseAttribInfo(responseAttribInfo);
    
                OMMMsg respMsg = outmsg;
                if (requestAttribInfo.getAttribType() != OMMTypes.NO_DATA)
                {
                    // Initialize the encoding to size 1000.
                    // Currently it is not resizable.
                    _encoder.initialize(OMMTypes.MSG, 1000);
                    _encoder.encodeMsgInit(outmsg, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);
    
                    // copy request attribInfo data elements to response attribInfo data
                    OMMElementList sel = (OMMElementList)requestAttribInfo.getAttrib();
                    _encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, sel.getListNum(),
                                                   sel.getDataDefId());
                    for (Iterator<?> iter = sel.iterator(); iter.hasNext();)
                    {
                        OMMElementEntry ee = (OMMElementEntry)iter.next();
                        _encoder.encodeElementEntryInit(ee.getName(), ee.getDataType());
                        _encoder.encodeData(ee.getData());
                    }
                    boolean bSupportPost = CommandLine.booleanVariable("supportPost");
                    if (bSupportPost == true)
                    {
                        // add the "SupportOMMPost" elements
                        _encoder.encodeElementEntryInit(RDMUser.Attrib.SupportOMMPost, OMMTypes.UINT);
                        _encoder.encodeUInt(1);
    
                        dumpInfo("OMMPosting supported");
                    }
                    else
                    {
                        dumpInfo("OMMPosting NOT supported");
                    }
                    _encoder.encodeAggregateComplete();
                    respMsg = (OMMMsg)_encoder.getEncodedObject();
                }
    
                _submitCmd.setMsg(respMsg);
    
                _submitCmd.setToken(event.getRequestToken());
    
                if (_appPostProviderDemo._provider.submit(_submitCmd, null) == 0)
                {
                    System.err
                            .println("Trying to submit for an Login response msg with an inactive handle/");
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
                // login to be closed. Consumer application does not
                // have to send closes for individual items if the application is
                // also closing the login.
                // Current, the application does not have the association between
                // the login handle and the Token for that login.
                dumpInfo("Logout received");
    
                cleanup(false);
                return;
            case OMMMsg.MsgType.GENERIC:
                dumpInfo("Received generic message type, not supported.");
                return;
            default:
                dumpInfo("Error! Received unexpected message type. " + msg.getMsgType());
                return;
        }
    }

    /*
     * unregister updates timer
     */
    private void unregisterTimer()
    {
        if (_timerHandle != null)
        {
            _appPostProviderDemo._provider.unregisterClient(_timerHandle);
            _timerHandle = null;
        }
    }

    /*
     * unregister updates timer
     */
    private void registerTimer()
    {
        if (_timerHandle == null)
        {
            TimerIntSpec timer = new TimerIntSpec();
            timer.setDelay(_appPostProviderDemo._updateInterval_ip * 1000);
            timer.setRepeating(true);
            _timerHandle = _appPostProviderDemo._provider
                    .registerClient(_appPostProviderDemo._eventQueue, timer, this, null);
        }
    }

    /*
     * Encoding of the enum dictionary
     */
    private OMMMsg encodeEnumDictionary(OMMEncoder enc, FieldDictionary dictionary,
            byte streamState, Handle handle, boolean solicited)
    {
        // This is the typical initialization of an response message.
        enc.initialize(OMMTypes.MSG, 200000);
        OMMMsg msg = _pool.acquireMsg();
        msg.clear();
        msg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
        if (handle != null)
        {
            msg.setAssociatedMetaInfo(handle);
        }
        
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        
        if (solicited)
            msg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            msg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        
        msg.setState(streamState, OMMState.Data.OK, OMMState.Code.NONE, "");
        msg.setItemGroup(1);
        OMMAttribInfo attribInfo = _appPostProviderDemo._pool.acquireAttribInfo();
        attribInfo.setServiceName(_servicename);
        attribInfo.setName("RWFEnum");
        attribInfo.setFilter(RDMDictionary.Filter.NORMAL);
        msg.setAttribInfo(attribInfo);
        enc.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.SERIES);
        FieldDictionary.encodeRDMEnumDictionary(dictionary, enc);
        return (OMMMsg)enc.getEncodedObject();
    }

    /*
     * Encoding of the RDMFieldDictionary.
     */
    private OMMMsg encodeFldDictionary(OMMEncoder enc, FieldDictionary dictionary,
            byte streamState, Handle handle, boolean solicited)
    {
        // calculate field dictionary size = numberOfFids * 60;
        // number_of_fids * 60 (approximate size per row)
        int encoderSizeForFieldDictionary = dictionary.size() * 60;
        
        enc.initialize(OMMTypes.MSG, encoderSizeForFieldDictionary);
        OMMMsg msg = _pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        msg.setMsgModelType(RDMMsgTypes.DICTIONARY);

        if (handle != null)
        {
            msg.setAssociatedMetaInfo(handle);
        }
        
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        
        if (solicited)
            msg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            msg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        
        msg.setState(streamState, OMMState.Data.OK, OMMState.Code.NONE, "");
        msg.setItemGroup(1);
        OMMAttribInfo attribInfo = _appPostProviderDemo._pool.acquireAttribInfo();
        attribInfo.setServiceName(_servicename);
        attribInfo.setName("RWFFld");
        
        // Specifies all of the normally needed data will be sent.
        attribInfo.setFilter(RDMDictionary.Filter.NORMAL);
        
        msg.setAttribInfo(attribInfo);
        enc.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.SERIES); // Data is Series.
        FieldDictionary.encodeRDMFieldDictionary(dictionary, enc);
        return (OMMMsg)enc.getEncodedObject();
    }

    /*
     * process post message
     */
    @SuppressWarnings("unused")
    // Unused is suppressed because this shows how to access data
    private void processPostMessage(ItemInfo itemInfo, Token messageToken, OMMMsg ommMsg)
    {
        String itemName = null;

        /* login stream (off stream posting) */
        if (messageToken == _loginRequestToken)
        {
            itemName = ommMsg.getAttribInfo().getName();
            System.out.format("%s received post message %d * login stream - item %s *\n",
                              INFO_APPNAME, ++_postMessageCount, itemName);
        }
        else
        /* item stream (on stream posting) */
        {

            itemName = itemInfo._name;
            System.out.format("%s received post message %d * item stream - item %s \n",
                              INFO_APPNAME, ++_postMessageCount, itemName);

        }
        /* ack response required */
        if (ommMsg.isSet(OMMMsg.Indication.NEED_ACK))
            System.out.println("Ack response = Required");
        else
            System.out.println("Ack response = Not Required");

        /* post id available */
        int postId = 0;
        if (ommMsg.has(OMMMsg.HAS_ID))
        {
            postId = (int)ommMsg.getId();
            System.out.println("Post Id is " + postId);
        }

        /* sequence no available */
        if (ommMsg.has(OMMMsg.HAS_SEQ_NUM))
        {
            int sequenceNumber = (int)ommMsg.getSeqNum();
            System.out.println("Sequence Number is " + sequenceNumber);
        }

        /* get publisher details */
        PublisherPrincipalIdentity pi = (PublisherPrincipalIdentity)ommMsg.getPrincipalIdentity();
        System.out.println("Publisher Address: 0x" + Long.toHexString(pi.getPublisherAddress()));
        System.out.println("Publisher Id: " + pi.getPublisherId());

        /* get payload type */
        short payloadDataType = ommMsg.getDataType();

        /* process payload */
        switch (payloadDataType)
        {
        /* payload is a data */
            case OMMTypes.FIELD_LIST:
            {
                OMMData data = ommMsg.getPayload();

                System.out.println("Post Payload is OMMTypes.FIELD_LIST");
                break;
            }
            /* payload is a OMMMsg */
            case OMMTypes.MSG:
            {
                OMMMsg msg = (OMMMsg)ommMsg.getPayload();

                System.out.println("Post Payload is OMMTypes.MSG");
                break;
            }
            /* no payload */
            case OMMTypes.NO_DATA:
            {
                System.out.println("No payload ");
                break;
            }
            /* other type */
            default:
            {
                System.out.println("Payload is " + OMMTypes.toString(payloadDataType));
                break;
            }
        }

        /* dump the message to the console */
        System.out.println();
        dumpOMMMsg(ommMsg, "Post Message Dump "+_postMessageCount);
        System.out.println();

        // *********************//
        // Ack msgs to consApp
        // *********************//

        /* ack response not required by post */
        if (!ommMsg.isSet(OMMMsg.Indication.NEED_ACK))
        {
            String logText = "ConsApp is not interested in Ack Message";
            System.out
                    .println(INFO_APPNAME + "is not sending ack msg to consApp, since " + logText);
            return;
        }

        /* ack response is required by post */
        Object value = _appPostProviderDemo._ackList_ip.get(postId);

        /*
         * provider application supports sending ack response to consumer
         * application
         */
        if (value != null)
        {
            sendAckMessage(messageToken, ommMsg);
        }
        else
        {
            String logText = "Ack Id " + postId + " is not configured in " + APPNAME;
            System.out.println(INFO_APPNAME + "is not sending ack msg to consumer client, since "
                    + logText);
        }

        // *********************//
        // Auto-fanning posts to consApp
        // *********************//

        /*
         * provider application does not want to re-send post payload msg to
         * consumer application;
         */
        if (_appPostProviderDemo._bForwardPostPayloadMsg_ip == false)
        {

            if (ommMsg.getDataType() == OMMTypes.MSG)
            {
                System.out.println(INFO_APPNAME
                                + "PostPayload=OMMTypes.MSG will not fanned out to consumer client(s), since config forwardPostPayloadMsg=false");
            }
            else
            {
                System.out.format("%s PostPayload=OMMTypes.%s will not fanned out to consumer client(s), since config forwardPostPayloadMsg=false and fanout of postPayload=%s is not supported\n",
                                INFO_APPNAME, OMMTypes.toString(payloadDataType),
                                OMMTypes.toString(payloadDataType));
            }
            return;
        }

        /*
         * provider application wants to re-send post payload msg to consumer
         * application; Only payload OMMTypes.MSG is supported;
         */
        if (ommMsg.getDataType() != OMMTypes.MSG) // OMM Data
        {
            System.out.format("%s Fanout of postPayload=OMMTypes.%s to consumer client(s) is not supported,though config forwardPostPayloadMsg=true\n",
                            INFO_APPNAME, OMMTypes.toString(payloadDataType));
            return;
        }

        /*
         * provider application wants to send post payload msg to consumer
         * application(s);
         */
        OMMMsg ommPayloadMsg = (OMMMsg)ommMsg.getPayload();
        String txt = null;
        if (messageToken == _loginRequestToken)
        {
            txt = String
                    .format("The PostPayloadOMMsg OMMMsg.%s for *Item-%s* received on *Login Stream*%n will be re-encoded and fanned out to consumer client(s)",
                            OMMMsg.MsgType.toString((byte)ommPayloadMsg.getMsgType()), itemName);
        }
        else
        {
            txt = String
                    .format("The PostPayloadOMMsg OMMMsg.%s for *Item-%s* received on *Item Stream*%n will be re-encoded and fanned out to consumer client(s)",
                            OMMMsg.MsgType.toString((byte)ommPayloadMsg.getMsgType()), itemName);
        }

        dumpInfo(txt);
        dumpOMMMsg(ommPayloadMsg, "Received Post Payload OMMMsg Dump");

        OMMMsg reEncodedPostPayloadOMMMsg = reEncodePostPayloadOMMMsg(itemName, ommMsg);

        dumpOMMMsg(reEncodedPostPayloadOMMMsg, "Re-encoded OMMMsg Dump");

        _appPostProviderDemo.fanoutItemOMMMsg2AllClients(reEncodedPostPayloadOMMMsg, itemName);
    }

    /*
     * send ack message
     */
    private void sendAckMessage(Token messageToken, OMMMsg ommMsg)
    {
        OMMMsg sendMessage = null;
        String postidTxt = "Not Available";
        String sequencenoTxt = "Not Available";
        String ackTypeTxt = "Negative Ack";

        _ommAckMsg.clear();

        /* set the message type */
        _ommAckMsg.setMsgType(OMMMsg.MsgType.ACK_RESP);

        /* set the message model type */
        _ommAckMsg.setMsgModelType(ommMsg.getMsgModelType());

        /*
         * set the post id; the post id must be available on a post message if
         * ack is desired
         */
        if (ommMsg.has(OMMMsg.HAS_ID))
        {
            _ommAckMsg.setId(ommMsg.getId());
            postidTxt = Integer.toString((int)ommMsg.getId());
        }

        /* set the sequence number if available on the post message */
        if (ommMsg.has(OMMMsg.HAS_SEQ_NUM))
        {
            _ommAckMsg.setSeqNum(ommMsg.getSeqNum());
            sequencenoTxt = Integer.toString((int)ommMsg.getSeqNum());
        }

        /* set state indirectly using OMMState object */
        if (_appPostProviderDemo._bSetStateUsingOMMState_ip == true)
        {
            _ackState.clear();

            /*
             * NACK code : if desired, set the NACK code; if not required, set
             * the NACK code to OMMState.NackCode.NONE Text : if desired, set
             * the text if not required, set the text to null Stream State : set
             * to OMMState.Stream.UNSPECIFIED Data State : set to
             * OMMState.Data.NO_CHANGE
             */
            _ackState.setStreamState(OMMState.Stream.UNSPECIFIED);
            _ackState.setDataState(OMMState.Data.NO_CHANGE);

            if (_appPostProviderDemo._bSetStatusCode_ip == true)
            {
                if (_appPostProviderDemo._bPositiveAck_ip == true)
                {
                    _ackState.setCode(OMMState.NackCode.NONE);
                }
                else
                {
                    _ackState.setCode(OMMState.NackCode.NACK_ACCESS_DENIED);
                }
            }

            if (_appPostProviderDemo._bSetStatusText_ip == true)
            {
                if (_appPostProviderDemo._bPositiveAck_ip == true)
                {
                    _ackState.setText("All is well");
                }
                else
                {
                    _ackState.setText("Access Denied");
                }
            }

            _ommAckMsg.setState(_ackState);
        }
        /* set state directly on the ack message */
        else
        {

            short ackCode = OMMState.NackCode.NONE;

            if (_appPostProviderDemo._bSetStatusCode_ip == true)
            {
                if (_appPostProviderDemo._bPositiveAck_ip == true)
                {
                    ackCode = OMMState.NackCode.NONE;
                }
                else
                {
                    ackCode = OMMState.NackCode.NACK_SOURCE_DOWN;
                }
            }
            else
            {
                ackCode = OMMState.NackCode.NONE;
            }

            String ackText = null;
            if (_appPostProviderDemo._bSetStatusText_ip == true)
            {
                if (_appPostProviderDemo._bPositiveAck_ip == true)
                {
                    ackText = "All is well";
                }
                else
                {
                    ackText = "Source is down";
                }
            }
            else
            {
                ackText = null;
            }

            /*
             * NACK code : if desired, set the NACK code; if not required, set
             * the NACK code to OMMState.NackCode.NONE Text : if desired, set
             * the text if not required, set the text to null Stream State : set
             * to OMMState.Stream.UNSPECIFIED Data State : set to
             * OMMState.Data.NO_CHANGE
             */
            _ommAckMsg.setState(OMMState.Stream.UNSPECIFIED, OMMState.Data.NO_CHANGE, ackCode,
                                ackText);
        }

        /* set the attrib info */
        if (ommMsg.has(OMMMsg.HAS_ATTRIB_INFO))
        {
            OMMAttribInfo ai = ommMsg.getAttribInfo();

            _ommAckMsg.setAttribInfo(ai.getServiceName(), ai.getName(), ai.getNameType());
        }

        sendMessage = _ommAckMsg;

        /* encode attrib and/or payload if configured */
        if ((_appPostProviderDemo._bAckEncodeAttrib_ip == true)
                || (_appPostProviderDemo._bAckEncodePayload_ip == true))
        {
            short attribDataType = OMMTypes.NO_DATA;
            short payloadDataType = OMMTypes.NO_DATA;

            if (_appPostProviderDemo._bAckEncodeAttrib_ip == true)
                attribDataType = OMMTypes.ELEMENT_LIST;

            if (_appPostProviderDemo._bAckEncodePayload_ip == true)
                payloadDataType = OMMTypes.FIELD_LIST;

            /* initialize encoder */
            _ackEncoder.initialize(OMMTypes.MSG, 1000);

            /* initialize the encoder with the msg header */
            _ackEncoder.encodeMsgInit(_ommAckMsg, attribDataType, payloadDataType);

            /* encode attrib data */
            if (_appPostProviderDemo._bAckEncodeAttrib_ip == true)
            {
                /* - AttribData : Element List - Start - */
                _ackEncoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0,
                                                  (short)0);
                _ackEncoder.encodeElementEntryInit("PostAttrib1", OMMTypes.ASCII_STRING);
                _ackEncoder.encodeString("Attrib-Data1", OMMTypes.ASCII_STRING);
                _ackEncoder.encodeAggregateComplete();
                /* - AttribData : Element List - End - */
            }

            /* encode payload data */
            if (_appPostProviderDemo._bAckEncodePayload_ip == true)
            {
                /* - Payload : Field List - Start - */
                _ackEncoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)1,
                                                (short)0);
                _ackEncoder.encodeFieldEntryInit((short)32, OMMTypes.REAL);
                _ackEncoder.encodeReal(400, OMMNumeric.EXPONENT_0);
                _ackEncoder.encodeAggregateComplete();
                /* - Payload : Field List - End - */
            }

            /* get the encoded ack message from the encoder */
            sendMessage = (OMMMsg)_ackEncoder.getEncodedObject();
        }

        /* set the ack message on the command */
        _submitCmd.setMsg(sendMessage);

        /* set the token on the command */
        _submitCmd.setToken(messageToken);

        if (_appPostProviderDemo._bPositiveAck_ip == true)
            ackTypeTxt = "Positive Ack";

        System.out.println(INFO_APPNAME + "Sending " + ackTypeTxt + "; PostId=" + postidTxt
                + "; SequenceNo=" + sequencenoTxt);

        /* submit the command on the OMM Provider event source */
        _appPostProviderDemo._provider.submit(_submitCmd, null);
    }

    /*
     * re-encode post payload message (update/refresh/state) set publisher info
     * if configured; set service name explicitly
     */
    OMMMsg reEncodePostPayloadOMMMsg(String itemName, OMMMsg ommPostMsg)
    {
        OMMMsg ommPayloadMsg = (OMMMsg)ommPostMsg.getPayload();

        short messageType = ommPayloadMsg.getMsgType();

        PublisherPrincipalIdentity pi = null;

        OMMMsg reEncodedMessage = null;

        // if configured, encode publisher info
        if (_appPostProviderDemo._bSendPublisherInfo_ip == true)
        {
            pi = (PublisherPrincipalIdentity)ommPostMsg.getPrincipalIdentity();
            OMMMsgReencoder.setEncodePublisherInfo(true, pi);
        }

        // the service name from the post payload message would be NULL;
        // set a valid service name on re-encoding
        if (ommPayloadMsg.has(OMMMsg.HAS_ATTRIB_INFO))
        {
            OMMAttribInfo payloadMsgAI = ommPayloadMsg.getAttribInfo();
            String serviceName = null;

            // get service name from post payload msg
            if (payloadMsgAI.has(OMMAttribInfo.HAS_SERVICE_NAME))
            {
                serviceName = payloadMsgAI.getServiceName();
            }

            // if service name not available on post payload msg,
            // get service name from post msg
            if (serviceName == null)
            {
                serviceName = ExampleUtil.getServiceNameFromOMMMsg(ommPostMsg);
            }

            // if service name not available on post payload msg OR
            // post msg, get it from the application
            if (serviceName == null)
            {
                serviceName = _appPostProviderDemo._serviceName_ip;
            }
            OMMMsgReencoder.setServiceName(true, serviceName);
        }

        // re-encode message(refresh / update / status) from post payload msg;
        // the service name must be available (not null)
        if (messageType == OMMMsg.MsgType.REFRESH_RESP)
        {
            // the response type must be changed from Solicited to Unsolicited;
            reEncodedMessage = OMMMsgReencoder.changeResponseTypeToUnsolicited(ommPayloadMsg, 1000);
        }
        else if (messageType == OMMMsg.MsgType.UPDATE_RESP)
        {
            reEncodedMessage = OMMMsgReencoder.getEncodeMsgfrom(ommPayloadMsg, 1000);
        }
        else if (messageType == OMMMsg.MsgType.STATUS_RESP)
        {
            reEncodedMessage = OMMMsgReencoder.getEncodeMsgfrom(ommPayloadMsg, 1000);
        }

        OMMMsgReencoder.setEncodePublisherInfo(false, null);
        OMMMsgReencoder.setServiceName(false, null);

        return reEncodedMessage;
    }

    /*
     * fanout the OMMMsg to the consumer client
     */
    void sendOMMMsg2Client(OMMMsg ommMsg, String itemName)
    {
        // fan the re-encoded msg to the consumer application(s)
        ItemInfo itemInfo = null;

        Iterator<Token> iter = _itemReqTable.keySet().iterator();
        while (iter.hasNext())
        {
            Token rq = (Token)(iter.next());
            itemInfo = (ItemInfo)(_itemReqTable.get(rq));

            if (itemInfo == null || itemInfo.isPaused()) // Do not send update
                                                         // for the paused
                                                         // item.
                continue;

            if (!itemInfo._name.equalsIgnoreCase(itemName))
                continue;

            _submitCmd.setMsg(ommMsg);
            _submitCmd.setToken(itemInfo._requestToken);

            System.out.println(INFO_APPNAME + "fanning out "
                    + OMMMsg.MsgType.toString(ommMsg.getMsgType())
                    + " message to consumer client " + _clientName);
            _appPostProviderDemo._provider.submit(_submitCmd, null);
        }

    }

    void dumpInfo(String str)
    {
        System.out.format("%s %s %n", INFO_APPNAME, str);
    }

    void dumpOMMMsg(OMMMsg ommMsg, String tag)
    {
        System.out.println(tag);
        for (int i = -2; i < tag.length(); i++)
            System.out.print("-");

        System.out.println();
        GenericOMMParser.parse(ommMsg);
        System.out.println();
    }
}
