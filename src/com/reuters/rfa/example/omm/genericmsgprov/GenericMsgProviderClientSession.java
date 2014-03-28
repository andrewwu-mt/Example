package com.reuters.rfa.example.omm.genericmsgprov;

import java.util.Iterator;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMFilterEntry;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMQos;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;
import com.reuters.rfa.session.TimerIntSpec;
import com.reuters.rfa.session.omm.OMMInactiveClientSessionEvent;
import com.reuters.rfa.session.omm.OMMItemCmd;
import com.reuters.rfa.session.omm.OMMSolicitedItemEvent;

public class GenericMsgProviderClientSession implements Client
{
    private final StarterProvider_GenericMsg _providerDemo;
    private OMMEncoder _encoder;
    private OMMPool _pool;
    private String _servicename;
    private Handle _timerHandle;
    private Token _currentReqToken = null;
    private String _currentItemName;
    private int _genericMsgsSent = 0;
    private static final short GENERIC_DOMAIN_MODEL = RDMMsgTypes.MAX_RESERVED + 1;

    public GenericMsgProviderClientSession(StarterProvider_GenericMsg app, String serviceName)
    {
        _providerDemo = app;
        _pool = app._pool;
        _encoder = _pool.acquireEncoder();
        _servicename = serviceName;
    }

    public void cleanup()
    {
        unregisterTimer();
    }

    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.TIMER_EVENT:
                sendGenericMsg(_currentReqToken);
                break;
            case Event.OMM_INACTIVE_CLIENT_SESSION_PUB_EVENT:
                // for client session handle
                processInactiveClientSessionEvent((OMMInactiveClientSessionEvent)event);
                break;
            case Event.OMM_SOLICITED_ITEM_EVENT:
                // for client session handle
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
                + event.getClientHostName() + " has become inactive.");

        cleanup();
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
            case GENERIC_DOMAIN_MODEL:
                // OMM model type used for this set of applications
                processItemRequest(event);
                break;
            default:
                System.out.println("Received Unknown Msg Model Type: "
                        + RDMMsgTypes.toString(msg.getMsgModelType()));
                break;
        }
    }

    /**
     * Currently, this method will automatically accept the incoming item
     * request.
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

        if (msg.getMsgModelType() != GENERIC_DOMAIN_MODEL)
        {
            System.out.println("Request other than GENERIC_DOMAIN_MODEL");
            System.out
                    .println("Currently, StarterProvider_GenericMsg supports GENERIC_DOMAIN_MODEL only");
            return;
        }

        // Check to see if the message type if a streaming request.
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
                if (msg.isSet(OMMMsg.Indication.NONSTREAMING))
                {
                    System.out.println("Currently, StarterProvider_GenericMsg does not support non-streaming item requests");
                    return;
                }
                
                _currentItemName = msg.getAttribInfo().getName();
    
                System.out.println("Received item request for " + msg.getAttribInfo().getServiceName()
                        + ":" + _currentItemName);
                GenericOMMParser.parse(msg);
    
                _currentReqToken = rq;
    
                outmsg = _pool.acquireMsg();
                
                // Set the message type to be refresh response.
                outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
                
                // Set the message model type to be the type requested.
                outmsg.setMsgModelType(GENERIC_DOMAIN_MODEL);
                
                // Indicates this  message will be the full refresh;
                // or this is the last refresh in the multi-part refresh.
                outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
    
                Handle hd = event.getHandle();
    
                if (hd != null)
                {
                    outmsg.setAssociatedMetaInfo(hd);
                }
                outmsg.setItemGroup(2); // Set the item group to be 2. Indicates the
                                        // item will be in group 2.
                // Set the state of the item stream.
                // Indicate the stream is streaming (Stream.OPEN) and the data provided is OK.
                outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "OK");
                if(msg.isSet(OMMMsg.Indication.REFRESH))
                    outmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
                else
                    outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
                outmsg.setAttribInfo(msg.getAttribInfo());
                _encoder.initialize(OMMTypes.MSG, 1000);
                _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);
    
                cmd.setMsg((OMMMsg)_encoder.getEncodedObject());
                cmd.setToken(event.getRequestToken());
    
                if (_providerDemo._provider.submit(cmd, null) > 0)
                {
                    System.out.println("Reply sent");
                }
                else
                {
                    System.err.println("Trying to submit for an item with an inactive handle.");
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
                processCloseItemReq();
                return;
            case OMMMsg.MsgType.GENERIC:
                System.out.println();
                System.out.println("Received Generic msg for : " + _currentItemName);
                GenericOMMParser.parse(msg);
                registerTimer(); // we have received our first generic msg from the
                                 // consumer, pause 1 sec then send one back,
                                 // consumer will respond to each one sent
                return;
            default:
                System.out.println("ERROR: Received unexpected message type. " + msg.getMsgType());
                return;
        }
    }

    private void processCloseItemReq()
    {
        System.out.println("Item close request: " + _currentItemName);
        unregisterTimer();
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
                
                // Initialize the encoding
                // to size 1000.
                // Currently it is not
                // resizable.
                _encoder.initialize(OMMTypes.MSG, 1000);
                
                OMMMsg respmsg = _pool.acquireMsg();
                respmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
                respmsg.setMsgModelType(RDMMsgTypes.DIRECTORY);
    
                Handle hd = event.getHandle();
    
                // set negotiated version info to OMMMsg
                if (hd != null)
                {
                    respmsg.setAssociatedMetaInfo(hd);
                }
    
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
                    _encoder.encodeUInt(0L); // Encoding the 0 value as unsigned int.
                    
                    // Specifies entry contains an ARRAY.
                    _encoder.encodeElementEntryInit(RDMService.Info.Capabilities, OMMTypes.ARRAY);
                    
                    // Specifies the ARRAY will have type UINT in all of its entries.
                    // We could have specified the 8 bytes as the size for UINT since all UINT are same size.
                    // We passed in 0 as the size. This lets the encoder to calculate the size for each UINT.
                    _encoder.encodeArrayInit(OMMTypes.UINT, 0);
                    
                    _encoder.encodeArrayEntryInit(); // Must be called, even though no parameter is passed in.
                    
                    _encoder.encodeUInt((long)RDMMsgTypes.DICTIONARY);
                    _encoder.encodeArrayEntryInit();
                    _encoder.encodeUInt((long)GENERIC_DOMAIN_MODEL);
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
                OMMAttribInfo attribInfo = msg.getAttribInfo();
                String username = null;
                if (attribInfo.has(OMMAttribInfo.HAS_NAME))
                    username = attribInfo.getName();
                System.out.println("username: " + username);
    
                OMMItemCmd cmd = new OMMItemCmd();
                OMMMsg outmsg = _pool.acquireMsg();
    
                // Login request should contain data. However, check to make
                // sure consumer is sending data in attribInfo.
                if (attribInfo.getAttribType() != OMMTypes.NO_DATA)
                {
                    // Data in attribInfo of LOGIN domain has been
                    // defined to be ElementList.
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
                    
                    if (msg.isSet(OMMMsg.Indication.NONSTREAMING))
                        outmsg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE,
                                        "login accepted");
                    else
                        outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE,
                                        "login accepted");
                    
                    if (msg.isSet(OMMMsg.Indication.REFRESH))
                        outmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
                    else
                        outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
                    
                    outmsg.setAttribInfo(attribInfo);
                    // OMMElementList origElementList =
                    // (OMMElementList)attribInfo.getAttrib(); // Data in attribInfo
                    // of LOGIN domain has been defined to be ElementList.
    
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
                cleanup();
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
            timer.setDelay(_providerDemo._submitInterval * 1000);
            timer.setRepeating(true);
            _timerHandle = _providerDemo._provider.registerClient(_providerDemo._eventQueue, timer,
                                                                  this, null);
        }
    }

    private void sendGenericMsg(Token token)
    {
        if (_currentReqToken == null)
            return;

        System.out.println("SENDING PROVIDER TO CONSUMER GENERIC MESSAGE: " + token);
        OMMMsg msg = _pool.acquireMsg();
        msg.setMsgModelType((short)GENERIC_DOMAIN_MODEL);
        msg.setMsgType(OMMMsg.MsgType.GENERIC);
        msg.setSeqNum(_genericMsgsSent + 1);
        msg.setIndicationFlags(OMMMsg.Indication.GENERIC_COMPLETE);

        Handle hd = token.getHandle();

        msg.setAssociatedMetaInfo(hd);

        /* put payload in */
        OMMEncoder encoder = _pool.acquireEncoder();
        encoder.initialize(OMMTypes.MSG, 6000);
        encoder.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.ELEMENT_LIST);
        encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        encoder.encodeElementEntryInit("Element1", OMMTypes.ASCII_STRING);
        encoder.encodeString("This", OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit("Element2", OMMTypes.ASCII_STRING);
        encoder.encodeString("is", OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit("Element3", OMMTypes.ASCII_STRING);
        encoder.encodeString("Generic Message", OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit("Element4", OMMTypes.UINT);
        encoder.encodeUInt((long)_genericMsgsSent + 1);
        encoder.encodeElementEntryInit("Element5", OMMTypes.ASCII_STRING);
        encoder.encodeString("From Provider to Consumer", OMMTypes.ASCII_STRING);
        encoder.encodeAggregateComplete(); // ElementList

        OMMItemCmd cmd = new OMMItemCmd();
        cmd.setMsg((OMMMsg)encoder.getEncodedObject());
        cmd.setToken(token);

        int ret = _providerDemo._provider.submit(cmd, null);
        if (ret == 0)
        {
            System.err.println("Trying to submit for an item with an inactive handle.");
            _providerDemo.cleanup();
            return;
        }
        _pool.releaseEncoder(encoder);
        _genericMsgsSent++;

        System.out.println("SENT PROVIDER TO CONSUMER GENERIC MESSAGE: " + token);
    }

}
