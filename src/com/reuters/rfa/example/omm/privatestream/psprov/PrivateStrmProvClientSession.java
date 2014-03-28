package com.reuters.rfa.example.omm.privatestream.psprov;

import java.util.HashMap;
import java.util.Iterator;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.omm.privatestream.common.PSGenericOMMParser;
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

class PrivateStrmProvClientSession implements Client
{
    private final StarterProvider_PrivateStream _providerDemo;
    private OMMEncoder _encoder;
    private OMMPool _pool;
    private String _servicename;
    private Handle _timerHandle;
    protected Handle _clientSessionHandle;
    HashMap<Token, PrivateStrmItemInfo> _itemReqTable;
    boolean _supportPar = false;

    public PrivateStrmProvClientSession(StarterProvider_PrivateStream app, String serviceName)
    {
        _providerDemo = app;
        _pool = app._pool;
        _encoder = _pool.acquireEncoder();
        _servicename = serviceName;
        _itemReqTable = new HashMap<Token, PrivateStrmItemInfo>();
    }

    public void cleanup(boolean shuttingDown)
    {
        if (!shuttingDown && _providerDemo._clientSessions.containsKey(_clientSessionHandle))
        {
            _providerDemo._provider.unregisterClient(_clientSessionHandle);
            _providerDemo._clientSessions.remove(_clientSessionHandle);
        }

        _itemReqTable.clear();
        _supportPar = false;
        unregisterTimer();
        _pool.releaseEncoder(_encoder);
    }

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
                processOMMSolicitedItemEvent((OMMSolicitedItemEvent)event);
                break;
            default:
                System.out.println("Unhandled event type: " + event.getType());
                break;
        }
    }

    // Session was disconnected or closed.
    protected void processInactiveClientSessionEvent(OMMInactiveClientSessionEvent event)
    {
        System.out.println("Received OMM INACTIVE CLIENT SESSION PUB EVENT MSG with handle: "
                + event.getHandle());
        System.out.println("ClientSession from " + event.getClientIPAddress() + "/"
                + event.getClientHostName() + "/" + event.getListenerName()
                + " has become inactive.");

        // event.getHandle() == _clientSessionHandle

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

    private void sendUpdates()
    {
        PrivateStrmItemInfo itemInfo = null;

        Iterator<Token> iter = _itemReqTable.keySet().iterator();
        while (iter.hasNext())
        {
            Token rq = (iter.next());
            itemInfo = (_itemReqTable.get(rq));
            
            // Do not send update for the paused item.
            if (itemInfo == null || itemInfo.isPaused())
                continue;

            for (int i = 0; i < _providerDemo._updateRate; i++)
            {
                // increment the canned data information for this item.
                itemInfo.increment();

                OMMItemCmd cmd = new OMMItemCmd(); // create a new OMMItemCmd
                
                // The size is an estimate. The size MUST be large enough
                // for the entire message. If size is not large enough, RFA will
                // throw out exception related to buffer usage.
                _encoder.initialize(OMMTypes.MSG, 500);
                OMMMsg outmsg = _pool.acquireMsg(); // OMMMsg is a poolable object.
                
                // Set the message type to be an update response.
                outmsg.setMsgType(OMMMsg.MsgType.UPDATE_RESP);
                
                // Set the message model to be MARKET_PRICE.
                // (all other item message models are not supported by this)
                outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);                                                                  // application
                
                // Set the indication flag for data not to be conflated.
                outmsg.setIndicationFlags(OMMMsg.Indication.DO_NOT_CONFLATE);
                
                // Set the response type to be a quote update.
                outmsg.setRespTypeNum(RDMInstrument.Update.QUOTE);
                
                // check if the original request for this item asked for attrib info
                // to be included in the updates.
                if (itemInfo.getAttribInUpdates())
                {
                    outmsg.setAttribInfo(_servicename, itemInfo.getName(),
                                         RDMInstrument.NameType.RIC);

                    // Initialize message encoding with the update response message.
                    // No data is contained in the OMMAttribInfo, so use NO_DATA
                    // for the data type of OMMAttribInfo
                    // There will be data encoded to the update response message
                    // (and we know it is FieldList), so
                    // use FIELD_List for the data type of the update response message.
                    _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);
                }
                else
                    // Since no attribInfo is requested in the updates, don't
                    // set attribInfo into the update response message.
                    // No OMMAttribInfo in the message defaults to NO_DATA for
                    // OMMAttribInfo.
                    // There will be data encoded to the update response message
                    // (and we know it is FieldList), so
                    // use FIELD_List for the data type of the update response
                    // message.
                    _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);

                // Initialize the field list encoding.
                // Specifies that this field list has only standard data (data
                // that is not defined in a DataDefinition)
                // DictionaryId is set to 0. This means the data encoded in this
                // message used dictionary identified by id equal to 0.
                // Field list number is set to 1. This identifies the field list
                // (in case for caching in the application or downstream components).
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
                long longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
                
                // Encode the real32 number with the price and the hint value.
                _encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
                
                // BID
                // Initialize the entry with the field id
                // and data type from RDMFieldDictionary for BID.
                _encoder.encodeFieldEntryInit((short)22, OMMTypes.REAL);
                
                value = itemInfo.getBid();
                longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
                
                // ASK
                // Initialize the entry with the field id
                // and data type from RDMFieldDictionary for ASK.
                _encoder.encodeFieldEntryInit((short)25, OMMTypes.REAL);
                
                value = itemInfo.getAsk();
                longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
                _encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
                
                // ACVOL_1
                // Initialize the entry with the field id
                // and data type from RDMFieldDictionary for ACVOL_1.
                _encoder.encodeFieldEntryInit((short)32, OMMTypes.REAL);
                _encoder.encodeReal(itemInfo.getACVol1(), OMMNumeric.EXPONENT_0);

                _encoder.encodeAggregateComplete(); // Complete the FieldList.
                
                // Set the update response message into OMMItemCmd.
                cmd.setMsg((OMMMsg)_encoder.getEncodedObject());
                
                cmd.setToken(rq); // Set the request token associated with this
                                  // item into the OMMItemCmd.

                // Submit the OMMItemCmd to RFAJ.
                // Note the closure of the submit is null. Any closure set by
                // the application must be long lived memory.
                if (_providerDemo._provider.submit(cmd, null) == 0)
                {
                    System.err.println("Trying to submit for an item with an inactive handle.");
                    iter.remove();
                    break; // break out the for loop to get next request token
                }

                _pool.releaseMsg(outmsg);
            }
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
        
        OMMItemCmd cmd = new OMMItemCmd(); // Create a new OMMItemCmd.

        OMMMsg outmsg = null;
        
        // See if we have an ItemInfo associated with the token.
        PrivateStrmItemInfo itemInfo = _itemReqTable.get(rq);

        // Check to see if the message type if a streaming request.
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                String name = msg.getAttribInfo().getName();
    
                // Check if we have a standard/normal item request of restricted
                // item RES-DS.N and if we do, then send REDIRECT status message
                // (i.e. do "Standard to Private Stream item-based Recall")
                if (!msg.isSet(OMMMsg.Indication.PRIVATE_STREAM) 
                        && !msg.isSet(OMMMsg.Indication.NONSTREAMING)
                        && name.equalsIgnoreCase("RES-DS.N"))
                {
                    // provider sends REDIRECT message to consumer
                    OMMMsg _statusMsg = _pool.acquireMsg();
                    _statusMsg.setMsgModelType(msg.getMsgModelType());
                    _statusMsg.setMsgType(OMMMsg.MsgType.STATUS_RESP);
                    _statusMsg.setState(OMMState.Stream.REDIRECT, OMMState.Data.OK, OMMState.Code.NONE,
                                        "Standard Stream Switch to Private");
                    _statusMsg.setAttribInfo(msg.getAttribInfo());
    
                    _statusMsg.setIndicationFlags(OMMMsg.Indication.PRIVATE_STREAM);
    
                    cmd.setMsg(_statusMsg);
                    cmd.setToken(rq);
                    if (_providerDemo._provider.submit(cmd, null) > 0)
                        System.out.println("\nRedirect Status sent for the following request:  \n"
                                + "\tservice name: " + _servicename + "\titem name: " + name
                                + "\tmessage model type: "
                                + RDMMsgTypes.toString(msg.getMsgModelType()) + "\n");
                    else
                        System.err.println("Trying to submit Redirect Status for an item with an inactive handle.");
    
                    _pool.releaseMsg(_statusMsg);
    
                    // cleanup
                    _pool.releaseEncoder(_encoder);
    
                    return;
                }
    
                boolean refreshRequested = msg.isSet(OMMMsg.Indication.REFRESH);
                
                if (itemInfo == null)
                {
                    refreshRequested = true; // new request needs refresh.
                    itemInfo = new PrivateStrmItemInfo();
                    itemInfo.setName(name);
                    if (msg.isSet(OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES))
                        itemInfo.setAttribInUpdates(true);
                    
                    if (msg.isSet(OMMMsg.Indication.NONSTREAMING))
                    {
                        System.out.println("Received non-streaming item request for "
                                + msg.getAttribInfo( ).getServiceName() + ":" + name);
                    }
                    else
                    {
                        System.out.println("Received item request for "
                                + msg.getAttribInfo().getServiceName() + ":" + name);
                        _itemReqTable.put(rq, itemInfo);
                        itemInfo.setPriorityCount(1);
                        itemInfo.setPriorityClass(1);
                        itemInfo.setHandle(event.getHandle());
                    }
                }
                else
                // Re-issue request
                {
                    System.out.println("Received item reissue for "
                            + msg.getAttribInfo().getServiceName() + ":" + name);
                }

                PSGenericOMMParser.parse(msg);
                
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
                
                if (!refreshRequested)
                    return;
                
                outmsg = _pool.acquireMsg();
                
                // Set the message type to be refresh response.
                outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
                
                // Set the message model type to be the type requested.
                outmsg.setMsgModelType(msg.getMsgModelType());
                
                // Set the item group to be 2. Indicates the item will be in group 2.
                outmsg.setItemGroup(2);
                
                // Set the state of the item stream.
                // Stream state could be OPEN or NONSTREAMING
                if (msg.isSet(OMMMsg.Indication.NONSTREAMING))
                {
                    // Indicate the stream is nonstreaming and the data provided is OK.
                    outmsg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE,
                                    "OK");
                }
                else
                {
                    // Indicate the stream is streaming (Stream.OPEN) and the data provided is OK.
                    outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "OK");
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
                    itemInfo = _itemReqTable.get(rq);
                if (itemInfo != null)
                    System.out.println("Item close request: " + itemInfo.getName());
                _itemReqTable.remove(rq); // removes the reference to the Token
                                          // associated for the item and its ItemInfo.
                if (_itemReqTable.isEmpty())
                    unregisterTimer();
                return;
        default:
            return; // unknown request type
        }

        // This section is similar to sendUpdates() function, with additional
        // fields. Please see sendUpdates for detailed description.
        if (msg.getMsgModelType() == RDMMsgTypes.MARKET_PRICE)
        {
            if (msg.isSet(OMMMsg.Indication.PRIVATE_STREAM))
                outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE
                        | OMMMsg.Indication.PRIVATE_STREAM); // private stream
            else
                // standard/normal stream (non-restricted item)
                outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);

            if(msg.isSet(OMMMsg.Indication.REFRESH))
                outmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
            else
                outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
            outmsg.setAttribInfo(msg.getAttribInfo());
            _encoder.initialize(OMMTypes.MSG, 1000);
            _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);

            _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA | OMMFieldList.HAS_INFO,
                                         (short)0, (short)1, (short)0);
            // RDNDISPLAY
            _encoder.encodeFieldEntryInit((short)2, OMMTypes.UINT);
            _encoder.encodeUInt(100L);
            // RDN_EXCHID
            _encoder.encodeFieldEntryInit((short)4, OMMTypes.ENUM);
            _encoder.encodeEnum(155);
            // DIVIDEND_DATE
            _encoder.encodeFieldEntryInit((short)38, OMMTypes.DATE);
            _encoder.encodeDate(2006, 12, 25);
            // TRDPRC_1
            _encoder.encodeFieldEntryInit((short)6, OMMTypes.REAL);
            double value = itemInfo.getTradePrice1();
            long longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
            
            // Encode the real32 number with the price and the hint value.
            _encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
            
            // BID
            // Initialize the entry with the field id and data type
            // from RDMFieldDictionary for BID.
            _encoder.encodeFieldEntryInit((short)22, OMMTypes.REAL);
            value = itemInfo.getBid();
            longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
            _encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
            
            // ASK
            // Initialize the entry with the field id and data type
            // from RDMFieldDictionary for ASK.
            _encoder.encodeFieldEntryInit((short)25, OMMTypes.REAL);
            value = itemInfo.getAsk();
            longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
            _encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);

            // ACVOL_1
            _encoder.encodeFieldEntryInit((short)32, OMMTypes.REAL);
            _encoder.encodeReal(itemInfo.getACVol1(), OMMNumeric.EXPONENT_0);
            // ASK_TIME
            _encoder.encodeFieldEntryInit((short)267, OMMTypes.TIME);
            _encoder.encodeTime(19, 12, 23, 0);

            _encoder.encodeAggregateComplete();
            cmd.setMsg((OMMMsg)_encoder.getEncodedObject());
            cmd.setToken(event.getRequestToken());

            if (_providerDemo._provider.submit(cmd, null) > 0)
            {
                System.out.println("Reply sent");
            }
            else
            {
                System.err.println("Trying to submit for an item with an inactive handle.");
                _itemReqTable.remove(rq); // removes the reference to the Token
                                          // associated for the item and its ItemInfo.
            }
            if (_itemReqTable.size() == 1)
                registerTimer();
        }
        else
        {
            System.out.println("Request other than MARKET_PRICE");
            System.out.println("Currently, StarterProvider_PrivateStream supports MARKET_PRICE only");
        }
        _pool.releaseMsg(outmsg);
    }

    @SuppressWarnings("deprecation")
    private void processDictionaryRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        String name = "";

        OMMItemCmd cmd = new OMMItemCmd();
        OMMEncoder enc = _providerDemo._pool.acquireEncoder();
        
        // Dictionary tends to be large messages.
        // So 160k bytes is needed here.
        enc.initialize(OMMTypes.MSG, 160000);
        OMMAttribInfo attribInfo = null;

        // OMMAttribInfo will be in the streaming and nonstreaming request.
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
                System.out.println("Dictionary request received");
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
                System.out.println("dictionary close request");
                return;
        }

        OMMMsg outmsg = null;
        byte streamState;
        // Stream state could be OPEN or NONSTREAMING
        if (msg.isSet(OMMMsg.Indication.NONSTREAMING))
            streamState = OMMState.Stream.NONSTREAMING;
        else
            streamState = OMMState.Stream.OPEN;
        if (name.equalsIgnoreCase("rwffld"))
        {
            outmsg = encodeFldDictionary(enc, _providerDemo._rwfDictionary, streamState);
        }
        else
        // name.equalsIgnoreCase("rwfenum")
        {
            outmsg = encodeEnumDictionary(enc, _providerDemo._rwfDictionary, streamState);
        }
        cmd.setMsg(outmsg);
        cmd.setToken(event.getRequestToken());

        int ret = _providerDemo._provider.submit(cmd, null);
        if (ret == 0)
            System.err.println("Trying to submit for an item with an inactive handle.");

        _pool.releaseMsg(outmsg);
        if (attribInfo == null)
            System.out.println("should not happen");
    }

    @SuppressWarnings("deprecation")
    private void processDirectoryRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        OMMAttribInfo at = null;
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                // GenericOMMParser.parse(msg);
                System.out.println("Directory request received");
                // The section below is similar to other message encoding.
                OMMItemCmd cmd = new OMMItemCmd(); // Create a new OMMItemCmd.
                
                // Initialize the encoding to size 1000.
                // Currently it is not resizable.
                _encoder.initialize(OMMTypes.MSG, 1000);
                OMMMsg respmsg = _pool.acquireMsg();
                respmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
                respmsg.setMsgModelType(RDMMsgTypes.DIRECTORY);
    
                byte streamState;
                // Stream state could be OPEN or NONSTREAMING
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
    
                    // Specifies the elementlist has only standard data, no data definitions for
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
                    _encoder.encodeUInt(0L); // Encoding the 0 value as unsigned int.
                    
                    // Specifies entry contains an ARRAY.
                    _encoder.encodeElementEntryInit(RDMService.Info.Capabilities, OMMTypes.ARRAY);
                    
                    // Specifies the ARRAY will have type UINT in all of its entries.
                    // We passed in 0 as the size. This lets the encoder to calculate the
                    // size for each UINT.
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
                    _encoder.encodeUInt(1L);
                    _encoder.encodeElementEntryInit(RDMService.SvcState.AcceptingRequests,
                                                    OMMTypes.UINT);
                    _encoder.encodeUInt(1L);
                    _encoder.encodeElementEntryInit(RDMService.SvcState.Status, OMMTypes.STATE);
                    _encoder.encodeState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
                    _encoder.encodeAggregateComplete(); // Completes the
                                                        // ElementList.
                }
    
                // any type that requires a
                // count needs to be closed.
                // This one is for FilterList.
                _encoder.encodeAggregateComplete();
                
                // any type that requires a
                // count needs to be closed.
                // This one is for Map.
                _encoder.encodeAggregateComplete();
                
                cmd.setMsg((OMMMsg)_encoder.getEncodedObject());
                cmd.setToken(event.getRequestToken());
    
                if (_providerDemo._provider.submit(cmd, null) > 0)
                    System.out.println("Directory reply sent");
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
        }
    }

    @SuppressWarnings("deprecation")
    private void processLoginRequest(OMMSolicitedItemEvent event)
    {
        OMMMsg msg = event.getMsg();

        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                if(msg.isSet(OMMMsg.Indication.NONSTREAMING))
                {
                    System.out.println("ERROR: Received NONSTREAMING request, ignoring");
                    return;
                }
                
                System.out.println("Login request received");
                // GenericOMMParser.parse(msg);
                OMMAttribInfo attribInfo = msg.getAttribInfo();
                String username = null;
                if (attribInfo.has(OMMAttribInfo.HAS_NAME))
                    username = attribInfo.getName();
                System.out.println("username: " + username);
    
                OMMItemCmd cmd = new OMMItemCmd();
                OMMMsg outmsg = _pool.acquireMsg();
    
                // Login request should contain data.
                // However, check to make sure consumer is sending data in attribInfo.
                if (attribInfo.getAttribType() != OMMTypes.NO_DATA)
                {
                    // Data in attribInfo of LOGIN domain has been defined to be ElementList.
                    OMMElementList elementList = (OMMElementList)attribInfo.getAttrib();
                    
                    // ElementList is iterable. Each ElementEntry can be accessed
                    // through the iterator.
                    for (Iterator<?> iter = elementList.iterator(); iter.hasNext();)
                    {
                        OMMElementEntry element = (OMMElementEntry)iter.next();
                        OMMData data = element.getData(); // Get the data from the ElementEntry.
                        System.out.println(element.getName() + ": " + data.toString());
                    }
    
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
                    
                    // Data in attribInfo of LOGIN domain has been defined to be ElementList.
                    OMMElementList origElementList = (OMMElementList)attribInfo.getAttrib();
                    
                    OMMElementEntry parEntry = origElementList.find(RDMUser.Attrib.SupportPauseResume);
                    
                    // Check whether requesting support pause and resume
                    if (parEntry != null && (int)((OMMNumeric)parEntry.getData()).toLong() == 1)
                    {
                        _supportPar = true;
                    }
    
                    cmd.setMsg(outmsg);
                    cmd.setToken(event.getRequestToken());
    
                    if (_providerDemo._provider.submit(cmd, null) == 0)
                    {
                        System.err.println("Trying to submit for an Login response msg with an inactive handle/");
                    }
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
                System.out.println("Logout received");
                cleanup(false);
                return;
        }
    }

    private void unregisterTimer()
    {
        if (_timerHandle != null)
        {
            _providerDemo._provider.unregisterClient(_timerHandle);
            _timerHandle = null;
        }
    }

    private void registerTimer()
    {
        if (_timerHandle == null)
        {
            TimerIntSpec timer = new TimerIntSpec();
            timer.setDelay(_providerDemo._updateInterval * 1000);
            timer.setRepeating(true);
            _timerHandle = _providerDemo._provider.registerClient(_providerDemo._eventQueue, timer,
                                                                  this, null);
        }
    }

    // Encoding of the enum dictionary
    private OMMMsg encodeEnumDictionary(OMMEncoder enc, FieldDictionary dictionary, byte streamState)
    {
        // This is the typical initialization of an response message.
        enc.initialize(OMMTypes.MSG, 250000);
        OMMMsg msg = _pool.acquireMsg();
        msg.clear();
        msg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        msg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        msg.setState(streamState, OMMState.Data.OK, OMMState.Code.NONE, "");
        msg.setItemGroup(1);
        OMMAttribInfo attribInfo = _providerDemo._pool.acquireAttribInfo();
        attribInfo.setServiceName(_servicename);
        attribInfo.setName("RWFEnum");
        attribInfo.setFilter(RDMDictionary.Filter.NORMAL);
        msg.setAttribInfo(attribInfo);
        enc.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.SERIES);
        FieldDictionary.encodeRDMEnumDictionary(dictionary, enc);
        return (OMMMsg)enc.getEncodedObject();
    }

    // Encoding of the RDMFieldDictionary.
    private OMMMsg encodeFldDictionary(OMMEncoder enc, FieldDictionary dictionary, byte streamState)
    {
        // This is the same message initialization as other response messages.
        enc.initialize(OMMTypes.MSG, 250000);
        OMMMsg msg = _pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        msg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        msg.setState(streamState, OMMState.Data.OK, OMMState.Code.NONE, "");
        msg.setItemGroup(1);
        OMMAttribInfo attribInfo = _providerDemo._pool.acquireAttribInfo();
        attribInfo.setServiceName(_servicename);
        attribInfo.setName("RWFFld");
        
        // Specifies all of the normally needed data will be sent.
        attribInfo.setFilter(RDMDictionary.Filter.NORMAL);
        
        msg.setAttribInfo(attribInfo);
        enc.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.SERIES); // Data is Series.
        FieldDictionary.encodeRDMFieldDictionary(dictionary, enc);
        return (OMMMsg)enc.getEncodedObject();
    }
}
