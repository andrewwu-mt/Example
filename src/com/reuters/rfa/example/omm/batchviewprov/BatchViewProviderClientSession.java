package com.reuters.rfa.example.omm.batchviewprov;

import java.util.HashMap;
import java.util.Iterator;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
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
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.TimerIntSpec;
import com.reuters.rfa.session.omm.OMMInactiveClientSessionEvent;
import com.reuters.rfa.session.omm.OMMItemCmd;
import com.reuters.rfa.session.omm.OMMSolicitedItemEvent;

/**
 * Client class to handle RFA events. This class processes requests and sends
 * responses. It supports batch and view requests and sends filtered data (in
 * case of view) or full image/update data. View and Batch capability are
 * announced by encoding appropriate flags in attrib info of login response.
 */
public class BatchViewProviderClientSession implements Client
{
    private final StarterProvider_BatchView _providerDemo;
    private OMMEncoder _encoder;
    private OMMPool _pool;
    private String _servicename;
    private Handle _timerHandle;
    protected Handle _clientSessionHandle;
    HashMap<Token, BatchViewItemInfo> _itemReqTable;

    public BatchViewProviderClientSession(StarterProvider_BatchView app, String serviceName)
    {
        _providerDemo = app;
        _pool = app._pool;
        _encoder = _pool.acquireEncoder();
        _servicename = serviceName;
        _itemReqTable = new HashMap<Token, BatchViewItemInfo>();
    }

    public void cleanup(boolean shuttingDown)
    {
        if (!shuttingDown && _providerDemo._clientSessions.containsKey(_clientSessionHandle))
        {
            _providerDemo._provider.unregisterClient(_clientSessionHandle);
            _providerDemo._clientSessions.remove(_clientSessionHandle);
        }

        _itemReqTable.clear();
        unregisterTimer();
        if (_encoder != null)
        {
            _pool.releaseEncoder(_encoder);
            _encoder = null;
        }
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
            case RDMMsgTypes.LOGIN: // Reuters defined domain message model - LOGIN
                processLoginRequest(event);
                break;
            case RDMMsgTypes.DIRECTORY:
                processDirectoryRequest(event); // Reuters defined domain message model - DIRECTORY
                break;
            case RDMMsgTypes.DICTIONARY:
                processDictionaryRequest(event);// Reuters defined domain message model - DICTIONARY
                break;
            default: // All other reuters defined domain message model or customer's
                     // domain message model are considered items.
                processItemRequest(event);
        }
    }

    private void sendUpdates()
    {
        BatchViewItemInfo itemInfo = null;

        Iterator<Token> iter = _itemReqTable.keySet().iterator();
        while (iter.hasNext())
        {
            Token rq = iter.next();
            itemInfo = _itemReqTable.get(rq);
            if (itemInfo == null || itemInfo.isPaused())
                continue;

            for (int i = 0; i < _providerDemo._updateRate; i++)
            {
                itemInfo.increment(); // increment the canned data information
                                      // for this item.

                OMMItemCmd cmd = new OMMItemCmd(); // create a new OMMItemCmd
                _encoder.initialize(OMMTypes.MSG, 500); // The size is an estimate. The size
                                                        // MUST be large enough for the entire message.
                                                        // If size is not large enough, RFA will
                                                        // throw out exception related to buffer usage.
                OMMMsg outmsg = _pool.acquireMsg(); // OMMMsg is a poolable object.
                
                // Set the message type to be an update response.
                outmsg.setMsgType(OMMMsg.MsgType.UPDATE_RESP);
                
                // Set the message model to be MARKET_PRICE.
                // (all other item message models are not supported by this application)
                outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
                
                // Set the indication flag for data not to be conflated.
                outmsg.setIndicationFlags(OMMMsg.Indication.DO_NOT_CONFLATE);
                
                // Set the response type to be a quote update.
                outmsg.setRespTypeNum(RDMInstrument.Update.QUOTE);

                Handle hd = itemInfo.getHandle();

                // pass version info
                if (hd != null)
                {
                    outmsg.setAssociatedMetaInfo(hd);
                }

                // check if the original request for this item asked for attrib info to
                // be included in the updates.
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
                    // No OMMAttribInfo in the message defaults to NO_DATA for OMMAttribInfo.
                    // There will be data encoded to the update response message
                    // (and we know it is FieldList), so
                    // use FIELD_List for the data type of the update response message.
                    _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);

                // Initialize the field list encoding.
                // Specifies that this field list has only standard data (data
                // that is not defined in a DataDefinition)
                // DictionaryId is set to 0. This means the data encoded in this
                // message used dictionary identified by id equal to 0.
                // Field list number is set to 1. This identifies the field list
                // (in case for caching in the application or downstream components).
                // Data definition id is set to 0 since standard data does not use data definition.
                // Large data is set to false. This is used since updates in
                // general are not large in size. (If unsure, use true)
                _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)1, (short)0);

                encodeMarketPriceUpdate(_encoder, itemInfo);

                _encoder.encodeAggregateComplete(); // Complete the FieldList.
                cmd.setMsg((OMMMsg)_encoder.getEncodedObject()); // Set the update response message into OMMItemCmd.
                cmd.setToken(rq); // Set the request token associated with this item into the OMMItemCmd.

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
        Token rq = event.getRequestToken(); // Token is associated for each unique request.
        OMMItemCmd cmd = new OMMItemCmd(); // Create a new OMMItemCmd.

        OMMMsg outmsg = null;
        // See if we have an ItemInfo associated with the token.
        BatchViewItemInfo itemInfo = _itemReqTable.get(rq);
        
        Handle hd = event.getHandle();
        // GenericOMMParser.parse(msg);
        
        // Check to see if the message type if a request.
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                String name = msg.getAttribInfo().getName();
                boolean refreshRequested = msg.isSet(OMMMsg.Indication.REFRESH);
                        
                if (itemInfo == null)
                {
                    // New request. Send a REFRESH_RESP regardless of REFRESH indication flag.
                    refreshRequested = true;
                    
                    itemInfo = new BatchViewItemInfo();
                    itemInfo.setName(name);
                    if (msg.isSet(OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES))
                        itemInfo.setAttribInUpdates(true);
                    
                    if(msg.isSet(OMMMsg.Indication.NONSTREAMING))
                    {
                        // NON-STREAMING request.
                        System.out.println();
                        System.out.println("Received NONSTREAMING item request for "
                                + msg.getAttribInfo().getServiceName() + ":" + name);
                    }
                    else
                    {
                        // STREAMING request.
                        System.out.println();
                        System.out.println("Received item request for "
                                + msg.getAttribInfo().getServiceName() + ":" + name);
    
                        itemInfo.setHandle(event.getHandle());
                        _itemReqTable.put(rq, itemInfo);
                        itemInfo.setPriorityCount(1);
                        itemInfo.setPriorityClass(1);
                        if(msg.isSet(OMMMsg.Indication.PAUSE_REQ))
                        {
                            itemInfo.setPaused(true); // request initially paused.
                        }
                    }
                }
                else
                {
                    // Existing request.
                    if(msg.isSet(OMMMsg.Indication.NONSTREAMING))
                    {
                        System.out.println("ERROR: Received NON-STREAMING request on a reissue, ignoring");
                        GenericOMMParser.parse(msg);
                        return;
                    }
                    System.out.println("Received item reissue for "
                            + msg.getAttribInfo().getServiceName() + ":" + name);

                    if(msg.isSet(OMMMsg.Indication.PAUSE_REQ))
                        itemInfo.setPaused(true);   //pause request.
                    else
                        itemInfo.setPaused(false);   //resume request (implicit).
                }
                GenericOMMParser.parse(msg);
                
                if (msg.has(OMMMsg.HAS_PRIORITY)) // Check if the streaming request has priority.
                {
                    OMMPriority priority = msg.getPriority();
    
                    if (priority != null)
                    {
                        itemInfo.setPriorityClass(priority.getPriorityClass());
                        itemInfo.setPriorityCount(priority.getCount());
                    }
                    else
                    // set the default priority class and count as 1.
                    {
                        itemInfo.setPriorityClass(1);
                        itemInfo.setPriorityCount(1);
                    }
                }
                
                boolean viewChanged = itemInfo.checkForView(msg);
                
                if(!refreshRequested && !viewChanged)
                    return; // no refresh requested, no view change, we're finished.
                
                outmsg = _pool.acquireMsg();
                
                // Set the message type to be a refresh response.
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
                
                // Set the item group to be 2. Indicates the item will be in group 2.
                outmsg.setItemGroup(2); 
                
                if(msg.isSet(OMMMsg.Indication.NONSTREAMING))
                {
                    // Set the state of the item stream. 
                    // Stream state could be OPEN or NONSTREAMING
                    // Indicate the stream is nonstreaming and the data provided is OK.
                    outmsg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE,
                                    "OK");
                }
                else
                {
                    // Set the state of the item stream.
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
                        + ", not supported.");
                return;
            case OMMMsg.MsgType.CLOSE_REQ:
            {
                itemInfo = _itemReqTable.get(rq);
                if (itemInfo != null)
                    System.out.println("Item close request: " + itemInfo.getName());
                
                // remove the reference to the Token associated for the item and its ItemInfo.
                _itemReqTable.remove(rq);
                
                if (_itemReqTable.isEmpty())
                    unregisterTimer();
                return;
            }
            case OMMMsg.MsgType.GENERIC:
                System.out.println("Received generic message type, not supported. ");
                return;
            case OMMMsg.MsgType.POST:
                System.out.println("Received post message type, not supported. ");
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
            _encoder.initialize(OMMTypes.MSG, 1000);
            _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);

            _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA | OMMFieldList.HAS_INFO,
                                         (short)0, (short)1, (short)0);
            
            encodeMarketPriceImage(_encoder, itemInfo);

            _encoder.encodeAggregateComplete();
            cmd.setMsg((OMMMsg)_encoder.getEncodedObject());
            cmd.setToken(event.getRequestToken());

            if (_providerDemo._provider.submit(cmd, null) > 0)
            {
                System.out.println("Reply sent");
                System.out.println();
            }
            else
            {
                System.err.println("Trying to submit for an item with an inactive handle.");
                
                // remove the reference to the Token associated for the item and its ItemInfo.
                _itemReqTable.remove(rq);
            }
            if (_itemReqTable.size() == 1)
                registerTimer();
        }
        else
        {
            System.out.println("Request other than MARKET_PRICE");
            System.out.println("Currently, StarterProvider_Interactive supports MARKET_PRICE only");
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
        enc.initialize(OMMTypes.MSG, 160000);// Dictionary tends to be large
                                             // messages. So 160k bytes is needed here.
        OMMAttribInfo attribInfo = null;

        // OMMAttribInfo will be in the streaming and nonstreaming request.
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                System.out.println("Dictionary request received");
                attribInfo = msg.getAttribInfo();
                name = attribInfo.getName();
                System.out.println("dictionary name: " + name);
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
                // RFA internally will clean up the item.
                // Application has placed the directory on its item info lookup
                // table, so no cleanup is needed here.
                System.out.println("dictionary close request");
                return;
            case OMMMsg.MsgType.GENERIC:
                System.out.println("Received generic message type, not supported. ");
                return;
            default:
                System.out.println("ERROR: Received unexpected message type. " + msg.getMsgType());
                return;
        }
        
        // send a response regardless of OMMMsg.Indication.REFRESH.
        OMMMsg outmsg = null;
        byte streamState;
        if (msg.isSet(OMMMsg.Indication.NONSTREAMING))
            streamState = OMMState.Stream.NONSTREAMING; // Stream state could be
                                                        // OPEN or NONSTREAMING
        else
            streamState = OMMState.Stream.OPEN;

        Handle hd = event.getHandle();
        if (name.equalsIgnoreCase("rwffld"))
        {
            outmsg = encodeFldDictionary(enc, _providerDemo._rwfDictionary, streamState, hd,
                                         msg.isSet(OMMMsg.Indication.REFRESH));
        }
        else
        // name.equalsIgnoreCase("rwfenum")
        {
            outmsg = encodeEnumDictionary(enc, _providerDemo._rwfDictionary, streamState, hd,
                                          msg.isSet(OMMMsg.Indication.REFRESH));
        }
        cmd.setMsg(outmsg);
        cmd.setToken(event.getRequestToken());

        int ret = _providerDemo._provider.submit(cmd, null);
        if (ret == 0)
            System.err.println("Trying to submit for an item with an inactive handle.");

        _pool.releaseMsg(outmsg);
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
                System.out.println("Directory request received");
                // The section below is similar to other message encoding.
                OMMItemCmd cmd = new OMMItemCmd(); // Create a new OMMItemCmd.
                
                // Initialize the encoding to size 1000.
                // Currently it is not resizable.
                _encoder.initialize(OMMTypes.MSG, 1000);
                
                // send a response regardless of OMMMsg.Indication.REFRESH.            
                OMMMsg respmsg = _pool.acquireMsg();
                respmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
                respmsg.setMsgModelType(RDMMsgTypes.DIRECTORY);
    
                Handle hd = event.getHandle();
    
                // pass version info
                if (hd != null)
                {
                    respmsg.setAssociatedMetaInfo(hd);
                }
    
                // Stream state could be OPEN or NONSTREAMING
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
                respmsg.setAttribInfo(outAttribInfo); // Set the attribInfo into the message.
    
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
                    _encoder.encodeString(_servicename, OMMTypes.ASCII_STRING);
                    _encoder.encodeElementEntryInit(RDMService.Info.Vendor, OMMTypes.ASCII_STRING);
                    _encoder.encodeString("Reuters", OMMTypes.ASCII_STRING);
                    _encoder.encodeElementEntryInit(RDMService.Info.IsSource, OMMTypes.UINT);
                    _encoder.encodeUInt(0L); // Encoding the 0 value as unsigned int.
                    
                    // Specifies entry contains an ARRAY.
                    _encoder.encodeElementEntryInit(RDMService.Info.Capabilities, OMMTypes.ARRAY);
                    
                    // Specifies the ARRAY will have type UINT in all of its entries.
                    // We could have specified the 8 bytes as the size
                    // for UINT since all UINT are same size.
                    // We passed in 0 as the size. This lets the encoder
                    // to calculate the size for each UINT.
                    _encoder.encodeArrayInit(OMMTypes.UINT, 0);
                    
                    // Must be called, even though no parameter is passed in.
                    _encoder.encodeArrayEntryInit();
                    
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
                    _encoder.encodeAggregateComplete(); // Completes the ElementList.
                }
    
                _encoder.encodeAggregateComplete(); // any type that requires a count needs to be closed.
                                                    // This one is for FilterList.
                _encoder.encodeAggregateComplete(); // any type that requires a count needs to be closed.
                                                    // This one is for Map.
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
                        + ", not supported.");
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
                    System.out.println("ERROR: Received NON-STREAMING request, ignoring");
                    return;
                }
                
                System.out.println("Login request received");
                OMMAttribInfo attribInfo = msg.getAttribInfo();
                String username = null;
                if (attribInfo.has(OMMAttribInfo.HAS_NAME))
                    username = attribInfo.getName();
                System.out.println("username: " + username);
    
                // send a response regardless of OMMMsg.Indication.REFRESH.
                OMMItemCmd cmd = new OMMItemCmd();
                OMMMsg outmsg = _pool.acquireMsg();
    
                // Login request should contain data.
                // However, check to make sure consumer is sending data in attribInfo.
                if (attribInfo.getAttribType() != OMMTypes.NO_DATA)
                {
                    outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
                    outmsg.setMsgModelType(RDMMsgTypes.LOGIN);
                    outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
                    outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE,
                                    "login accepted");
                    if (msg.isSet(OMMMsg.Indication.REFRESH))
                        outmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
                    else
                        outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
                    OMMAttribInfo outAttribInfo = _pool.acquireAttribInfo();
    
                    if (attribInfo.has(OMMAttribInfo.HAS_NAME))
                    {
                        outAttribInfo.setName(attribInfo.getName());
                    }
    
                    outAttribInfo.setNameType(attribInfo.getNameType());
    
                    outmsg.setAttribInfo(outAttribInfo);
                    _pool.releaseAttribInfo(outAttribInfo);
                    _encoder.initialize(OMMTypes.MSG, 500);
                    _encoder.encodeMsgInit(outmsg, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);
                    _encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
    
                    // Data in attribInfo of LOGIN domain has been defined to be ElementList.
                    OMMElementList elementList = (OMMElementList)attribInfo.getAttrib();
                    
                    // ElementList is iterable. Each ElementEntry can be accessed through the iterator.
                    for (Iterator<?> iter = elementList.iterator(); iter.hasNext();)
                    {
                        OMMElementEntry element = (OMMElementEntry)iter.next();
                        OMMData data = element.getData(); // Get the data from the
                                                          // ElementEntry.
                        _encoder.encodeElementEntryInit(element.getName(), element.getDataType());
                        _encoder.encodeData(data);
                    }
                    _encoder.encodeElementEntryInit(RDMUser.Attrib.SupportViewRequests, OMMTypes.UINT);
                    _encoder.encodeUInt(1);
                    _encoder.encodeElementEntryInit(RDMUser.Attrib.SupportBatchRequests, OMMTypes.UINT);
                    _encoder.encodeUInt(1); // 0x01 Batch Requests
                    _encoder.encodeElementEntryInit(RDMUser.Attrib.SupportOptimizedPauseResume, OMMTypes.UINT);
                    _encoder.encodeUInt(1);
                    _encoder.encodeAggregateComplete();
    
                    // Get the encoded message from the encoder
                    OMMMsg encMsg = (OMMMsg)_encoder.getEncodedObject();
                    
                    // Data in attribInfo of LOGIN domain has been defined to be ElementList.
                    OMMElementList elementList2 = (OMMElementList)encMsg.getAttribInfo().getAttrib();
                    
                    // ElementList is iterable. Each ElementEntry can be accessed through the iterator.
                    for (Iterator<?> iter = elementList2.iterator(); iter.hasNext();)
                    {
                        OMMElementEntry element = (OMMElementEntry)iter.next();
                        OMMData data = element.getData(); // Get the data from the ElementEntry.
                        System.out.println(element.getName() + ": " + data.toString());
                    }
                    cmd.setMsg(encMsg);
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
                // have to send closes for individual items if the application is also closing the login.
                // Current, the application does not have the association between
                // the login handle and the Token for that login.
                System.out.println("Logout received");
                cleanup(false);
                return;
            case OMMMsg.MsgType.GENERIC:
                System.out.println("Received generic message type, not supported. ");
                return;
            default:
                System.out.println("ERROR: Received unexpected message type. " + msg.getMsgType());
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
    private OMMMsg encodeEnumDictionary(OMMEncoder enc, FieldDictionary dictionary,
            byte streamState, Handle handle, boolean solicited)
    {
        // This is the typical initialization of an response message.
        enc.initialize(OMMTypes.MSG, 250000);
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
    private OMMMsg encodeFldDictionary(OMMEncoder enc, FieldDictionary dictionary,
            byte streamState, Handle handle, boolean solicited)
    {
        // calculate field dictionary size = numberOfFids * 60;
     // number_of_fids
        // *
        // 60(approximate
        // size per
        // row)
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

    private void encodeMarketPriceImage(OMMEncoder encoder, BatchViewItemInfo itemInfo)
    {
        
        for (short fid : itemInfo.getFidsToSend())
        {
            switch (fid)
            {
                case 2:
                    // RDNDISPLAY
                    encoder.encodeFieldEntryInit((short)2, OMMTypes.INT);
                    encoder.encodeInt(100);
                    break;
                case 4:
                    // RDN_EXCHID
                    encoder.encodeFieldEntryInit((short)4, OMMTypes.ENUM);
                    encoder.encodeEnum(155);
                    break;
                case 38:
                    // DIVIDEND_DATE
                    encoder.encodeFieldEntryInit((short)38, OMMTypes.DATE);
                    encoder.encodeDate(2006, 12, 25);
                    break;
                case 6:
                    // TRDPRC_1
                    encoder.encodeFieldEntryInit((short)6, OMMTypes.REAL);
                    double value = itemInfo.getTradePrice1();
                    long longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
                    
                    // Encode the real number with the price and the hint value.
                    encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
                    
                    break;
                case 22:
                    // BID
                    // Initialize the entry with the field id and data/ type from/ RDMFieldDictionary for BID.
                    encoder.encodeFieldEntryInit((short)22, OMMTypes.REAL);
                    value = itemInfo.getBid();
                    longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
                    encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
                    break;
                case 25:
                    // ASK
                    // Initialize the entry with the field id and data type from RDMFieldDictionary for
                    // ASK.
                    encoder.encodeFieldEntryInit((short)25, OMMTypes.REAL);
                    value = itemInfo.getAsk();
                    longValue = Rounding.roundDouble2Long((float)value, OMMNumeric.EXPONENT_NEG4);
                    encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
                    break;
                case 32:
                    // ACVOL_1
                    encoder.encodeFieldEntryInit((short)32, OMMTypes.REAL);
                    encoder.encodeReal(itemInfo.getACVol1(), OMMNumeric.EXPONENT_0);
                    break;
                case 267:
                    // ASK_TIME
                    encoder.encodeFieldEntryInit((short)267, OMMTypes.TIME);
                    encoder.encodeTime(19, 12, 23, 0);
                    break;
                default:
                    System.out.println("Consumer requested unsupported FID = " + fid);
            }
        }
    }

    private void encodeMarketPriceUpdate(OMMEncoder encoder, BatchViewItemInfo itemInfo)
    {
        for (short fid : itemInfo.getFidsToSend())
        {
            switch (fid)
            {
                case 6:
                    // TRDPRC_1
                    // Initialize the entry with the field id and data type from RDMFieldDictionary for TRDPRC_1.
                    encoder.encodeFieldEntryInit((short)6, OMMTypes.REAL);
                    double value = itemInfo.getTradePrice1();
                    long longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
                    
                    // Encode the real number with the price and the hint value.
                    encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
                    break;
                case 22:
                    // BID
                    // Initialize the entry with the field id and data type from RDMFieldDictionary for BID.
                    encoder.encodeFieldEntryInit((short)22, OMMTypes.REAL);
                    
                    value = itemInfo.getBid();
                    longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
                    encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
                    break;
                case 25:
                    // ASK
                    // Initialize the entry with the field id and data type from RDMFieldDictionary for ASK.
                    encoder.encodeFieldEntryInit((short)25, OMMTypes.REAL);
                    
                    value = itemInfo.getAsk();
                    longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
                    encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
                    break;
                case 32:
                    // ACVOL_1
                    // Initialize the entry with the field id and data type from RDMFieldDictionary for ACVOL_1.
                    encoder.encodeFieldEntryInit((short)32, OMMTypes.REAL);
                    
                    encoder.encodeReal(itemInfo.getACVol1(), OMMNumeric.EXPONENT_0);
                    break;
            }
        }
    }
}
