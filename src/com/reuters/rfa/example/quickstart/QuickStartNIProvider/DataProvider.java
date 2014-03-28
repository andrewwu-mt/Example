package com.reuters.rfa.example.quickstart.QuickStartNIProvider;

import java.util.ArrayList;
import java.util.HashMap;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
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
import com.reuters.rfa.omm.OMMQos;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;
import com.reuters.rfa.session.TimerIntSpec;
import com.reuters.rfa.session.omm.OMMItemCmd;

// This class is a Client publishing data to the connected ADH.
// When the application is successfully logged in, it starts
// the update timer. The timer periodically generates item updates.
public class DataProvider implements Client
{
    private Handle _timerHandle;

    private final QSNIProvider _providerDemo;
    private String _serviceName; 
    private ArrayList<String> _itemNamesList;
    int _updateInterval = 2; // arbitrary set
    int _updateRate = 2; // arbitrary set

    HashMap<Token, ItemInfo> _itemReqTable;
    private Token _dirToken;

	protected DataProvider(QSNIProvider app)
    {
		_providerDemo = app;
    }
	
	protected void init(String serviceName, String [] items)
	{
        _serviceName = serviceName;
        _itemNamesList = new ArrayList<String>();
        for (int i = 0; i < items.length; i++)
        {
        	_itemNamesList.add(items[i]);
        }
        
        _itemReqTable = new HashMap<Token, ItemInfo>();
    }

    private Token generateToken()
    {
        return _providerDemo._provider.generateToken();
    }


	// Application code to send updates with a TimerTask.
	public void processEvent(Event event)
	{
	    if (event.getType() == Event.TIMER_EVENT)
	        sendUpdates();
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
            timer.setDelay(_updateInterval*1000);
            timer.setRepeating(true);
            _timerHandle = _providerDemo._provider.registerClient(_providerDemo._eventQueue, timer, this, null);
        }
    }

    public void processLoginSuspect()
    {
    	// stop publishing
        cleanup();
    }
    
 	private void sendDirectoryImage()
	{
	    if (_dirToken == null)
	    {
	        _dirToken = generateToken();
	    }
		System.out.println("Sending Directory image");
        	
		// Create a new OMMItemCmd.
		OMMItemCmd cmd = new OMMItemCmd();	
		
		cmd.setToken(_dirToken);

		// Encode directory response message and set it into OMMItemCmd.
		OMMMsg refreshMsg = encodeDirectoryImage();
		cmd.setMsg(refreshMsg);  

		if (_providerDemo._provider.submit(cmd, null) > 0)
		    System.out.println("Directory reply sent");
		else
		    System.err.println("Trying to submit for an item with an inactive handle.");
	}

 	private void sendImages()
    {
    	for (String itemName : _itemNamesList)
    	{
    		sendImage(itemName);
    	}
    }

	private void sendUpdates()
	{
		System.out.println("Updating " + _itemReqTable.size() + " items");
		
		for ( Token token : _itemReqTable.keySet())
		{
			ItemInfo itemInfo = (ItemInfo)(_itemReqTable.get(token));
			if (itemInfo == null)
				continue;

			for (int i=0; i < _updateRate; i++)
			{
				// increment the canned data information for this item.
				itemInfo.increment();	

				// create a new OMMItemCmd
				OMMItemCmd cmd = new OMMItemCmd(); 

				// Set the token associated with this item into the OMMItemCmd.
				cmd.setToken(token);	
				
				// Encode update message and set it into OMMItemCmd.
				OMMMsg updateMsg = encodeUpdateMsg(itemInfo);
				cmd.setMsg(updateMsg);  

				// Submit the OMMItemCmd to RFAJ.
		        if (_providerDemo._provider.submit(cmd, null) ==  0)
		        {
		        	System.err.println("Trying to submit update msg with inactive handle");
		        }
		        else
		        {
		            System.out.println("Update message sent");
		        }
			}
		}
	}

	private void sendImage(String name)
	{
		Token token = generateToken();
		ItemInfo itemInfo = new ItemInfo();
		itemInfo.setToken(token);
        itemInfo.setName(name);
        _itemReqTable.put(token, itemInfo);

		// create a new OMMItemCmd
		OMMItemCmd cmd = new OMMItemCmd(); 

		// Set the request token associated with this item into the OMMItemCmd.
		cmd.setToken(token);	
		
		// Encode refresh message and set it into OMMItemCmd.
		OMMMsg refreshMsg = encodeRefreshMsg(itemInfo, name);
		cmd.setMsg(refreshMsg);  

		// Submit the OMMItemCmd to RFAJ.
        if (_providerDemo._provider.submit(cmd, null) ==  0)
        {
        	System.err.println("Trying to submit refresh msg with inactive handle");
        }
        else
        {
            System.out.println("Refresh reply sent");
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

	//------------------------------------------------------------------------------------
	// the methods below handle encoding of OMM messages
	//------------------------------------------------------------------------------------
	
	private OMMMsg encodeUpdateMsg(ItemInfo itemInfo)
	{
		OMMEncoder encoder = _providerDemo.getEncoder();
		// set the encoder to encode an OMM message
		// The size is an estimate.  The size MUST be large enough for the entire message.
		// If size is not large enough, RFA will throw out exception related to buffer usage.
		encoder.initialize(OMMTypes.MSG, 500);

		// allocate memory from memory pool for the message to encode
		OMMMsg outmsg = _providerDemo.getPool().acquireMsg();

  		// Set the message type to be an update response.
		outmsg.setMsgType(OMMMsg.MsgType.UPDATE_RESP);
		
		// Set the message model to be MARKET_PRICE.  
		outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
		
		// Set the indication flag for data not to be conflated.
		outmsg.setIndicationFlags(OMMMsg.Indication.DO_NOT_CONFLATE);
		
		// Set the response type to be a quote update.
		outmsg.setRespTypeNum(RDMInstrument.Update.QUOTE);	

		// Initialize message encoding with the update response message.
		// No data is contained in the OMMAttribInfo, so use NO_DATA for the data type of OMMAttribInfo
		// There will be data encoded in the update response message (and we know it is FieldList), so
		// use FIELD_List for the data type of the update response message.
		encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);

		// Initialize the field list encoding.
		// Specifies that this field list has only standard data (data that is not defined in a DataDefinition)
		// DictionaryId is set to 0.  This means the data encoded in this message used dictionary identified by id equal to 0.
		// Field list number is set to 1.  This identifies the field list (in case for caching in the application or downstream components).
		// Data definition id is set to 0 since standard data does not use data definition.
		// Large data is set to false.  This is used since updates in general are not large in size.  (If unsure, use true)
		encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)1, (short)0);

		// TRDPRC_1
		// Initialize the entry with the field id and data type from RDMFieldDictionary for TRDPRC_1.
		encoder.encodeFieldEntryInit( (short)6, OMMTypes.REAL);  
		double value = itemInfo.getTradePrice1();
		long longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		// Encode the real number with the price and the hint value.
		encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);	
		//	BID
		// Initialize the entry with the field id and data type from RDMFieldDictionary for BID.
		encoder.encodeFieldEntryInit( (short)22, OMMTypes.REAL);	 
		value = itemInfo.getBid();
		longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
		//	ASK
		// Initialize the entry with the field id and data type from RDMFieldDictionary for ASK.
		encoder.encodeFieldEntryInit( (short)25, OMMTypes.REAL);	 
		value = itemInfo.getAsk();
		longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
		// 	ACVOL_1
		// Initialize the entry with the field id and data type from RDMFieldDictionary for ACVOL_1.
		encoder.encodeFieldEntryInit( (short)32, OMMTypes.REAL);  
		encoder.encodeReal(itemInfo.getACVol1(), OMMNumeric.EXPONENT_0);

		// Complete the FieldList.
		encoder.encodeAggregateComplete();
		
		// release the message to the pool
        _providerDemo.getPool().releaseMsg(outmsg);
        
        // return encoded message
		return (OMMMsg)encoder.getEncodedObject(); 
	}

	OMMMsg encodeRefreshMsg(ItemInfo itemInfo, String name)
	{
		OMMEncoder encoder = _providerDemo.getEncoder();
		// set the encoder to encode an OMM message
		// The size is an estimate.  The size MUST be large enough for the entire message.
		// If size is not large enough, RFA will throw out exception related to buffer usage.
		encoder.initialize(OMMTypes.MSG, 1000);
        OMMMsg outmsg = _providerDemo.getPool().acquireMsg();
        outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);		
        outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);	
        outmsg.setItemGroup(2);	
        // Set the state of the item stream.
        outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "OK");

        outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        outmsg.setAttribInfo(_serviceName, name, RDMInstrument.NameType.RIC);
        encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);

        encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA | OMMFieldList.HAS_INFO, (short)0, (short)1, (short)0);
        // 	RDNDISPLAY
        encoder.encodeFieldEntryInit( (short)2, OMMTypes.UINT);
        encoder.encodeUInt(64L);
        //	RDN_EXCHID
        encoder.encodeFieldEntryInit( (short)4, OMMTypes.ENUM);
        encoder.encodeEnum(2); //NYS
        //	DIVIDEND_DATE
        encoder.encodeFieldEntryInit( (short)38, OMMTypes.DATE);
        encoder.encodeDate(2008, 12, 25);
		//	TRDPRC_1
     // Initialize the entry with the field id and data type from RDMFieldDictionary for TRDPRC_1
		encoder.encodeFieldEntryInit( (short)6, OMMTypes.REAL);  
		double value = itemInfo.getTradePrice1();
		long longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		// Encode the real number with the price and the hint value.
		encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);	
		//	BID
		// Initialize the entry with the field id and data type from RDMFieldDictionary for BID.
		encoder.encodeFieldEntryInit( (short)22, OMMTypes.REAL);	 
		value = itemInfo.getBid();
		longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
		//	ASK
		// Initialize the entry with the field id and data type from RDMFieldDictionary for ASK.
		encoder.encodeFieldEntryInit( (short)25, OMMTypes.REAL);	 
		value = itemInfo.getAsk();
		longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
        // 	ACVOL_1
        encoder.encodeFieldEntryInit( (short)32, OMMTypes.REAL);
        encoder.encodeReal(itemInfo.getACVol1(), OMMNumeric.EXPONENT_0);
        // ASK_TIME
        encoder.encodeFieldEntryInit( (short)267, OMMTypes.TIME);
        encoder.encodeTime(19, 12, 23, 0);
        encoder.encodeAggregateComplete();
        
		// release the message to the pool
        _providerDemo.getPool().releaseMsg(outmsg);
        
        // return encoded message        
        return (OMMMsg)encoder.getEncodedObject();
	}
	
	OMMMsg encodeDirectoryImage()
	{
		OMMEncoder encoder = _providerDemo.getEncoder();
		encoder.initialize(OMMTypes.MSG, 1000);	// Initialize the encoding to size 1000. 
		OMMMsg outmsg = _providerDemo.getPool().acquireMsg();
		outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
		outmsg.setMsgModelType(RDMMsgTypes.DIRECTORY);
		outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
		outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
		outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
		outmsg.setItemGroup(1);
		OMMAttribInfo outAttribInfo = _providerDemo.getPool().acquireAttribInfo();

		// Specifies what type of information is provided.
		// We will encode what the information that is being requested (only INFO, STATE, and GROUP is supported in the application).
		outAttribInfo.setFilter(RDMService.Filter.INFO | RDMService.Filter.STATE);
		outmsg.setAttribInfo(outAttribInfo);	// Set the attribInfo into the message.

		// Initialize the response message encoding that contains no data in attribInfo, and MAP data in the message.
		encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.MAP);

		// Map encoding initialization.
		// Specifies the flag for the map, the data type of the key as ascii_string, as defined by RDMUsageGuide.
		// the data type of the map entries is FilterList, the total count hint, and the dictionary id as 0
		encoder.encodeMapInit( OMMMap.HAS_TOTAL_COUNT_HINT, OMMTypes.ASCII_STRING, OMMTypes.FILTER_LIST, 1, (short)0);

		// MapEntry:  Each service is associated with a map entry with the service name has the key.
		encoder.encodeMapEntryInit( 0, OMMMapEntry.Action.ADD, null);
		encoder.encodeString(_serviceName, OMMTypes.ASCII_STRING);

		// Filter list encoding initialization.
		// Specifies the flag is 0, the data type in the filter entry as element list, and total count hint of 2 entries.
		encoder.encodeFilterListInit( 0, OMMTypes.ELEMENT_LIST, 2);

		// Only encode the service info if the application had asked for the information.
		if ( (outAttribInfo.getFilter() & RDMService.Filter.INFO) !=0 )
		{
		    // Specifies the filter entry has data, action is SET, the filter id is the filter information, and data type is elementlist.  No permission data is provided.
		    encoder.encodeFilterEntryInit( OMMFilterEntry.HAS_DATA_FORMAT, OMMFilterEntry.Action.SET, RDMService.FilterId.INFO, OMMTypes.ELEMENT_LIST, null);

		    // Specifies the elementlist has only standard data, no data definitions for
		    // DefinedData (since we don't have that here), and false for largedata (we assume the data here won't be big)
		    encoder.encodeElementListInit( OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
		    encoder.encodeElementEntryInit("Name", OMMTypes.ASCII_STRING);
		    encoder.encodeString(_serviceName, OMMTypes.ASCII_STRING); 
		    encoder.encodeElementEntryInit("Vendor", OMMTypes.ASCII_STRING);
		    encoder.encodeString("Reuters", OMMTypes.ASCII_STRING); 
		    encoder.encodeElementEntryInit("IsSource", OMMTypes.UINT);
		    encoder.encodeUInt(0L);	
		    encoder.encodeElementEntryInit("Capabilities", OMMTypes.ARRAY); 
		    encoder.encodeArrayInit(OMMTypes.UINT, 0); 
		    encoder.encodeArrayEntryInit(); 
		    encoder.encodeUInt((long)RDMMsgTypes.DICTIONARY);
		    encoder.encodeArrayEntryInit();
		    encoder.encodeUInt((long)RDMMsgTypes.MARKET_PRICE);
		    encoder.encodeAggregateComplete();	// Completes the Array.
		    encoder.encodeElementEntryInit("DictionariesProvided", OMMTypes.ARRAY); 
		    encoder.encodeArrayInit(OMMTypes.ASCII_STRING, 0); 
		    encoder.encodeAggregateComplete(); // Completes the Array.
		    encoder.encodeElementEntryInit("DictionariesUsed", OMMTypes.ARRAY);
		    encoder.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
		    encoder.encodeArrayEntryInit();
		    encoder.encodeString("RWFFld", OMMTypes.ASCII_STRING);
		    encoder.encodeArrayEntryInit();
		    encoder.encodeString("RWFEnum", OMMTypes.ASCII_STRING);
		    encoder.encodeAggregateComplete(); // Completes the Array.
		    encoder.encodeElementEntryInit("QoS", OMMTypes.ARRAY); 
		    encoder.encodeArrayInit(OMMTypes.QOS, 0);
		    encoder.encodeArrayEntryInit();
		    encoder.encodeQos(OMMQos.QOS_REALTIME_TICK_BY_TICK);
		    encoder.encodeAggregateComplete();	// Completes the Array.
		    encoder.encodeAggregateComplete();	// Completes the ElementList
		}

		// Only encode the service state information if the application had asked for the information.
		if ( (outAttribInfo.getFilter() & RDMService.Filter.STATE) !=0 )
		{
		    encoder.encodeFilterEntryInit( OMMFilterEntry.HAS_DATA_FORMAT, OMMFilterEntry.Action.UPDATE, RDMService.FilterId.STATE, OMMTypes.ELEMENT_LIST, null);
		    encoder.encodeElementListInit( OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
		    encoder.encodeElementEntryInit("ServiceState", OMMTypes.UINT);
		    encoder.encodeUInt(1L);
		    encoder.encodeElementEntryInit("AcceptingRequests", OMMTypes.UINT);
		    encoder.encodeUInt(1L);
		    encoder.encodeElementEntryInit("Status", OMMTypes.STATE);
		    encoder.encodeState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
		    encoder.encodeAggregateComplete(); // Completes the ElementList.
		}

		encoder.encodeAggregateComplete(); // Completes the FilterList.
		encoder.encodeAggregateComplete();  // Completes the Map.
        
		// release the message to the pool
        _providerDemo.getPool().releaseMsg(outmsg);
        
        // return encoded message        
        return (OMMMsg)encoder.getEncodedObject();
	}
}