package com.reuters.rfa.example.omm.provni;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.ExampleUtil;
import com.reuters.rfa.example.utility.Rounding;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMFilterEntry;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMQos;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;
import com.reuters.rfa.session.TimerIntSpec;
import com.reuters.rfa.session.omm.OMMItemCmd;

/**
 * Client Class to provide data
 */
public class DataProvider implements Client
{
    private Handle _timerHandle;

    private final StarterProvider_NonInteractive _providerDemo;
    private OMMEncoder _encoder;
    private OMMPool _pool;
    private OMMAttribInfo _ommattrib;
    private OMMItemCmd _cmd;
    private String _servicename;
    private Token _dirToken;
    private LinkedList<String> _itemNamesList;
    HashMap<Token, ItemInfo> _itemReqTable;
    int _updateInterval, _updateRate;
    boolean _bSendStatus; // send status message or not
    
    // service Info on updates	
    boolean _bSetServiceNameOnUpdates;
    boolean _bSetServiceIDOnUpdates;
    int _bUpdateServiceID;
	String _bUpdateServiceName;
		
    // permission expression (DACS lock)
    byte[] _refreshDacsLock;
    byte[] _updateDacsLock;
    byte[] _statusDacsLock;
    
    public DataProvider(StarterProvider_NonInteractive app)
    {
        _providerDemo = app;
        /**
         * Create an OMMEncoder with type RWF and initial size of 2000 bytes.
         * The buffer size held on by the encoder is resizeable.
         * 
         * Create an OMMPool for creating OMM types.
         * 
         * @see com.reuters.rfa.omm.OMMPool
         * @see com.reuters.rfa.omm.OMMEncoder
         */
        _itemReqTable = new HashMap<Token, ItemInfo>();
        _pool = app._pool;
        _encoder = _pool.acquireEncoder();
        _encoder.initialize(OMMTypes.MSG, 2000);
        
        _ommattrib = _pool.acquireAttribInfo();
        
        _cmd = new OMMItemCmd();

        _servicename = CommandLine.variable("serviceName");
        String itemNames = CommandLine.variable("itemName");
        _updateInterval = CommandLine.intVariable("updateInterval");
        _updateRate = CommandLine.intVariable("updateRate");
        
        // configure use of serviceInfo
        String serviceInfoOnUpdates = CommandLine.variable("serviceInfoOnUpdates");
        if( serviceInfoOnUpdates == null || serviceInfoOnUpdates.length() == 0 )
        {
        	_bSetServiceNameOnUpdates  = false;
        	_bSetServiceIDOnUpdates  = false;
        }
        else
        {
        	if( ExampleUtil.isNumeric(serviceInfoOnUpdates) )
        	{
        		_bUpdateServiceID = (int) ExampleUtil.convertStringToNumeric(serviceInfoOnUpdates);
        		_bSetServiceIDOnUpdates  = true;
        	}
        	else
        	{
        		_bUpdateServiceName = serviceInfoOnUpdates;
        		_bSetServiceNameOnUpdates  = true;
        	}
        }
        
        // send status message
        _bSendStatus = CommandLine.booleanVariable("sendStatus");

        // DACS lock
        int dacsServiceID = CommandLine.intVariable("dacsServiceId");
        
        String PEList = CommandLine.variable("refreshPE");
        _refreshDacsLock = ExampleUtil.generatePELock( dacsServiceID, PEList );
                
        PEList = CommandLine.variable("updatePE");
        _updateDacsLock = ExampleUtil.generatePELock( dacsServiceID, PEList );
        
        PEList = CommandLine.variable("statusPE");
        _statusDacsLock = ExampleUtil.generatePELock( dacsServiceID, PEList );

        // Note: "," is a valid character for RIC name.
        // This application need to be modified if RIC names have ",".
        StringTokenizer st = new StringTokenizer(itemNames, ",");
        _itemNamesList = new LinkedList<String>();
        while (st.hasMoreTokens())
            _itemNamesList.add(st.nextToken().trim());

    }

    private void sendStatus()
    {
    	OMMMsg outmsg = _pool.acquireMsg(); // OMMMsg is a poolable object.
        outmsg.setMsgType(OMMMsg.MsgType.STATUS_RESP);
        outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);

        // if configured, set permission expression
        if( _statusDacsLock != null )
        	outmsg.setPermissionData( _statusDacsLock );
    	
        ItemInfo itemInfo = null;
        Iterator<Token> iter = _itemReqTable.keySet().iterator();
        while (iter.hasNext())
        {
            Token rq = (Token)(iter.next());
            itemInfo = (ItemInfo)(_itemReqTable.get(rq));
            if (itemInfo == null)
                continue;

            outmsg.setAttribInfo(_servicename, itemInfo._name,(short) 1 );
            
            _cmd.setMsg((OMMMsg) outmsg );
            _cmd.setToken(rq); 

            if (_providerDemo._provider.submit(_cmd, null) == 0)
            {
            	System.err.println("Trying to submit status for an item with an inactive handle.");
            	iter.remove();
            	break; // break out the for loop to get next request token
            }
            else
            {
            	System.out.println("Send Status "+itemInfo.updateString());
            }
        }
        _pool.releaseMsg(outmsg);
        
    }
    
    private void sendUpdates()
    {
        ItemInfo itemInfo = null;
        // System.out.println("Updating " + _itemReqTable.size() + " items");
        Iterator<Token> iter = _itemReqTable.keySet().iterator();
        while (iter.hasNext())
        {
            Token rq = (Token)(iter.next());
            itemInfo = (ItemInfo)(_itemReqTable.get(rq));
            if (itemInfo == null)
                continue;

            for (int i = 0; i < _updateRate; i++)
            {
                itemInfo.increment(); // increment the canned data information
                                      // for this item.
                itemInfo.incrementUpdateCount();

                // The size is an estimate. The size MUST be large enough
                // for the entire message.
                // If size is not large enough, RFA will throw out exception
                // related to buffer usage.
                _encoder.initialize(OMMTypes.MSG, 500);
                
                OMMMsg outmsg = _pool.acquireMsg(); // OMMMsg is a poolable object.
                
                // Set the message type to be an update response.
                outmsg.setMsgType(OMMMsg.MsgType.UPDATE_RESP);
                
                // Set the message model to be MARKET_PRICE.
                // (all other item message models are not supported by this application
                outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
                
                // Set the indication flag for data not to be conflated.
                outmsg.setIndicationFlags(OMMMsg.Indication.DO_NOT_CONFLATE);
                
                // Set the response type to be a quote update.
                outmsg.setRespTypeNum(RDMInstrument.Update.QUOTE);

                // set serviceinfo, if configured
                if( _bSetServiceIDOnUpdates  )
                {
                	_ommattrib.clear();

                	_ommattrib.setServiceID( _bUpdateServiceID );
                	_ommattrib.setName( itemInfo._name );
                	_ommattrib.setNameType( RDMInstrument.NameType.RIC );

                	outmsg.setAttribInfo( _ommattrib );
                }
                else if( _bSetServiceNameOnUpdates == true )
                {
                	outmsg.setAttribInfo(_bUpdateServiceName, itemInfo._name, RDMInstrument.NameType.RIC);
                }
                
                // if configured, set permission expression                
                if( _updateDacsLock != null )
                	outmsg.setPermissionData( _updateDacsLock );
                
                // Since no attribInfo is requested in the updates, don't set
                // attribInfo into the update response message.
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
                // Data definition id is set to 0 since standard data does not
                // use data definition.
                // Large data is set to false. This is used since updates in
                // general are not large in size. (If unsure, use true)
                _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)1,
                                             (short)0);

                // TRDPRC_1
                // Initialize the entry with the field id and data type
                // from RDMFieldDictionary for TRDPRC_1.
                _encoder.encodeFieldEntryInit((short)6, OMMTypes.REAL);
                
                double value = itemInfo.getTradePrice1();
                long longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
                
                // Encode the real number with the price and the hint value.
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
                // Initialize the entry with the field id and data type
                // from RDMFieldDictionary for ACVOL_1.
                _encoder.encodeFieldEntryInit((short)32, OMMTypes.REAL);
                
                _encoder.encodeReal(itemInfo.getACVol1(), OMMNumeric.EXPONENT_0);

                _encoder.encodeAggregateComplete(); // Complete the FieldList.
                
                // Set the update response message into OMMItemCmd.
                _cmd.setMsg((OMMMsg)_encoder.getEncodedObject());
                
                _cmd.setToken(rq); // Set the request token associated with this
                                   // item into the OMMItemCmd.

                // Submit the OMMItemCmd to RFAJ.
                // Note the closure of the submit is null. Any closure set by
                // the application must be long lived memory.
                if (_providerDemo._provider.submit(_cmd, null) == 0)
                {
                    System.err.println("Trying to submit for an item with an inactive handle.");
                    iter.remove();
                    break; // break out the for loop to get next request token
                }
                else
                {
                    System.out.println(itemInfo.updateString());
                }

                _pool.releaseMsg(outmsg);
            }
        }
    }

    private void sendImage(String name)
    {
        Token rq = generateToken();

        // Initialize the encoder to contain a buffer of 1000 bytes.
        _encoder.initialize(OMMTypes.MSG, 1000);
        
        ItemInfo itemInfo = new ItemInfo();
        itemInfo.setToken(rq);
        itemInfo.setName(name);
        _itemReqTable.put(rq, itemInfo);

        OMMMsg outmsg = _pool.acquireMsg();
        
        // Set the message type to be refresh response.
        outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        
        // Set the message model type to be the type requested.
        outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
        
        // Indicates this message will be the full refresh
        // or this is the last refresh in the multi-part refresh.
        outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        
        // Set the item group to be 2. Indicates the item will be in group 2.
        outmsg.setItemGroup(2);
        
        // Set the state of the item stream.
        // Indicate the stream is streaming (Stream.OPEN) and the data provided is OK.
        outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "OK");

        // This section is similar to sendUpdates() function, with additional
        // fields. Please see sendUpdates for detailed description.
        outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        
        // if configured, set permission expression
        if( _refreshDacsLock != null )
        	outmsg.setPermissionData(_refreshDacsLock);
        
        outmsg.setAttribInfo(_servicename, name, RDMInstrument.NameType.RIC);
        _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);

        _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA | OMMFieldList.HAS_INFO,
                                     (short)0, (short)1, (short)0);
        // RDNDISPLAY
        _encoder.encodeFieldEntryInit((short)2, OMMTypes.UINT);
        _encoder.encodeUInt(64L);
        // RDN_EXCHID
        _encoder.encodeFieldEntryInit((short)4, OMMTypes.ENUM);
        _encoder.encodeEnum(2); // NYS
        // DIVIDEND_DATE
        _encoder.encodeFieldEntryInit((short)38, OMMTypes.DATE);
        _encoder.encodeDate(2008, 12, 25);
        
        // TRDPRC_1
        // Initialize the entry with the field id and data
        // type from RDMFieldDictionary for TRDPRC_1.
        _encoder.encodeFieldEntryInit((short)6, OMMTypes.REAL);
        
        double value = itemInfo.getTradePrice1();
        long longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
        
        // Encode the real numbe with the price and the hint value.
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
        _cmd.setMsg((OMMMsg)_encoder.getEncodedObject());
        _cmd.setToken(itemInfo.getToken());

        if (_providerDemo._provider.submit(_cmd, null) > 0)
        {
            System.out.println("Sent Image for "+itemInfo._name);
        }
        else
        {
            System.err.println("Trying to submit for an item with an inactive handle.");
            
            // removes the reference to the Token associated for the item and its ItemInfo.
            _itemReqTable.remove(itemInfo.getToken());
        }
        _pool.releaseMsg(outmsg);
    }

    private void sendDirectoryImage()
    {
        if (_dirToken == null)
        {
            _dirToken = generateToken();
        }
        System.out.println("Sending Directory image");
        // The section below is similar to other message encoding.
        OMMItemCmd cmd = new OMMItemCmd(); // Create a new OMMItemCmd.
        
        // Initialize the encoding to size 1000.
        _encoder.initialize(OMMTypes.MSG, 1000);
        
        OMMMsg respmsg = _pool.acquireMsg();
        respmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        respmsg.setMsgModelType(RDMMsgTypes.DIRECTORY);
        respmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
        respmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        respmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        respmsg.setItemGroup(1);
        OMMAttribInfo outAttribInfo = _pool.acquireAttribInfo();

        // Specifies what type of information is provided.
        // We will encode what the information that is being requested (only
        // INFO, STATE, and GROUP is supported in the application).
        outAttribInfo.setFilter(RDMService.Filter.INFO | RDMService.Filter.STATE);
        respmsg.setAttribInfo(outAttribInfo); // Set the attribInfo into the message.

        // Initialize the response message encoding that contains no data in
        // attribInfo, and MAP data in the message.
        _encoder.encodeMsgInit(respmsg, OMMTypes.NO_DATA, OMMTypes.MAP);

        // Map encoding initialization.
        // Specifies the flag for the map, the data type of the key as
        // ascii_string, as defined by RDMUsageGuide.
        // the data type of the map entries is FilterList, the total count hint,
        // and the dictionary id as 0
        _encoder.encodeMapInit(OMMMap.HAS_TOTAL_COUNT_HINT, OMMTypes.ASCII_STRING,
                               OMMTypes.FILTER_LIST, 1, (short)0);

        // MapEntry: Each service is associated with a map entry with the
        // service name has the key.
        _encoder.encodeMapEntryInit(0, OMMMapEntry.Action.ADD, null);
        _encoder.encodeString(_servicename, OMMTypes.ASCII_STRING);

        // Filter list encoding initialization.
        // Specifies the flag is 0, the data type in the filter entry as element
        // list, and total count hint of 2 entries.
        _encoder.encodeFilterListInit(0, OMMTypes.ELEMENT_LIST, 2);

        // Only encode the service info if the application had asked for the information.
        if ((outAttribInfo.getFilter() & RDMService.Filter.INFO) != 0)
        {
            // Specifies the filter entry has data, action is SET, the filter id
            // is the filter information, and data type is elementlist. No
            // permission data is provided.
            _encoder.encodeFilterEntryInit(OMMFilterEntry.HAS_DATA_FORMAT,
                                           OMMFilterEntry.Action.SET, RDMService.FilterId.INFO,
                                           OMMTypes.ELEMENT_LIST, null);

            // Specifies the elementlist has only standard data, no data definitions for
            // DefinedData (since we don't have that here), and false for
            // largedata (we assume the data here won't be big)
            _encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
            _encoder.encodeElementEntryInit("Name", OMMTypes.ASCII_STRING);
            
            // Encoding the string value servicename as an ASCIIstring.
            _encoder.encodeString(_servicename, OMMTypes.ASCII_STRING);
            
            _encoder.encodeElementEntryInit("Vendor", OMMTypes.ASCII_STRING);
            
            // Encoding the string value "Reuters" as an ASCII string.
            _encoder.encodeString("Reuters", OMMTypes.ASCII_STRING);
            
            _encoder.encodeElementEntryInit("IsSource", OMMTypes.UINT);
            _encoder.encodeUInt(0L); // Encoding the 0 value as unsigned int.
            
            // Specifies entry contains an ARRAY.
            _encoder.encodeElementEntryInit("Capabilities", OMMTypes.ARRAY);
            
            // Specifies the ARRAY will have type UINT in all of its entries.
            _encoder.encodeArrayInit(OMMTypes.UINT, 0);
            
            // We passed in 0 as the size. This lets the encoder to calculate
            // the size for each UINT.
            _encoder.encodeArrayEntryInit(); // Must be called, even though no parameter is passed in.
            _encoder.encodeUInt((long)RDMMsgTypes.DICTIONARY);
            _encoder.encodeArrayEntryInit();
            _encoder.encodeUInt((long)RDMMsgTypes.MARKET_PRICE);
            _encoder.encodeAggregateComplete(); // Completes the Array.
            
            // Specifies an ARRAY in the element entry.
            _encoder.encodeElementEntryInit("DictionariesProvided", OMMTypes.ARRAY);
            
            // This array will contain ASCII_STRING data types in its entries.
            // Since size of each string is different, 0 is passed in.
            _encoder.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
            
            _encoder.encodeAggregateComplete(); // Completes the Array.
            _encoder.encodeElementEntryInit("DictionariesUsed", OMMTypes.ARRAY);
            _encoder.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
            _encoder.encodeArrayEntryInit();
            _encoder.encodeString("RWFFld", OMMTypes.ASCII_STRING);
            _encoder.encodeArrayEntryInit();
            _encoder.encodeString("RWFEnum", OMMTypes.ASCII_STRING);
            _encoder.encodeAggregateComplete(); // Completes the Array.
            
            // This array entries data types are Qos.
            _encoder.encodeElementEntryInit("QoS", OMMTypes.ARRAY);
            
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
                                           OMMFilterEntry.Action.UPDATE, RDMService.FilterId.STATE,
                                           OMMTypes.ELEMENT_LIST, null);
            _encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
            _encoder.encodeElementEntryInit("ServiceState", OMMTypes.UINT);
            _encoder.encodeUInt(1L);
            _encoder.encodeElementEntryInit("AcceptingRequests", OMMTypes.UINT);
            _encoder.encodeUInt(1L);
            _encoder.encodeElementEntryInit("Status", OMMTypes.STATE);
            _encoder.encodeState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
            _encoder.encodeAggregateComplete(); // Completes the ElementList.
        }

        // any type that requires a count needs to be closed.
        // This one is for FilterList.
        _encoder.encodeAggregateComplete();
        
        // any type that requires a count needs to be closed.
        // This one is for Map.
        _encoder.encodeAggregateComplete();
        
        cmd.setMsg((OMMMsg)_encoder.getEncodedObject());
        cmd.setToken(_dirToken);

        if (_providerDemo._provider.submit(cmd, null) > 0)
            System.out.println("Directory reply sent");
        else
            System.err.println("Trying to submit for an item with an inactive handle.");

        _pool.releaseMsg(respmsg);
        _pool.releaseAttribInfo(outAttribInfo);

    }

    private Token generateToken()
    {
        return _providerDemo._provider.generateToken();
    }

    // Application code to send updates with a TimerTask.
    public void processEvent(Event event)
    {
        if (event.getType() == Event.TIMER_EVENT)
        {
            sendUpdates();
            if( _bSendStatus == true )
            	sendStatus();
        }
        else
            System.err.println("Unexpected event: " + event);
    }

    public void processLoginSuccessful()
    {
        sendDirectoryImage();
        sendImages();
        // This is example application code to start the update loop.
        if (_timerHandle == null)
        {
            sendUpdates();
            TimerIntSpec timer = new TimerIntSpec();
            timer.setDelay(_updateInterval * 1000);
            timer.setRepeating(true);
            _timerHandle = _providerDemo._provider.registerClient(_providerDemo._eventQueue, timer,
                                                                  this, null);
        }
    }

    public void processLoginSuspect()
    {
        // stop publishing
        cleanup();
    }

    private void sendImages()
    {
        for (Iterator<String> iter = _itemNamesList.iterator(); iter.hasNext();)
        {
            sendImage((String)iter.next());
        }
    }

    public void cleanup()
    {
        if (_timerHandle != null)
        {
            _providerDemo._provider.unregisterClient(_timerHandle);
            _timerHandle = null;
        }
        _itemReqTable.clear();
    }

}
