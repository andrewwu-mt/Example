package com.reuters.rfa.example.quickstart.QuickStartProvider;

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

// Client class to handle RFA events. 
// This class processes requests and sends responses
public class ProviderClientSession implements Client
{
	// reference to QSProviderDemo object
	private final QSProvider _providerDemo;
	// instance of encoder
    private OMMEncoder _encoder;
    // a handle associated with the update timer. The update timer periodically 
    // generates timer event, which handles updates
    private Handle _timerHandle;
    // this is a handle associated with this Client
   	protected Handle _clientSessionHandle;
   	// collection of items receiving updates
    HashMap<Token, ItemInfo> _itemReqTable;    
    
    // constructor
	public ProviderClientSession(QSProvider app)
    {
		_providerDemo = app;
        _encoder = _providerDemo.getPool().acquireEncoder();
        _itemReqTable = new HashMap<Token, ItemInfo>();       
    }

	public void cleanup()
	{
		if (_clientSessionHandle != null)
		{
    		_providerDemo._provider.unregisterClient(_clientSessionHandle); 			
		}

		if (_providerDemo._clientSessions.containsKey(_clientSessionHandle))
        {
        	_providerDemo._clientSessions.remove(_clientSessionHandle);
        }

        _itemReqTable.clear();
        unregisterTimer();
        if (_encoder != null)
        {
        	_providerDemo.getPool().releaseEncoder(_encoder);
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
			case Event.OMM_INACTIVE_CLIENT_SESSION_PUB_EVENT: 
				processInactiveClientSessionEvent((OMMInactiveClientSessionEvent)event);
				break;
			case Event.OMM_SOLICITED_ITEM_EVENT : 
				processOMMSolicitedItemEvent((OMMSolicitedItemEvent)event);
				break;
			default :
				System.out.println("Unhandled event type: " + event.getType());
				break;
		}
	}

	private void sendUpdates()
	{
		for (Token rq : _itemReqTable.keySet())
		{
			ItemInfo itemInfo = _itemReqTable.get(rq);
			if (itemInfo == null || itemInfo.isPaused()) //Do not send update for the paused item.
				continue;

			// set updateRate arbitrary for this example
			int updateRate = 2; 
			for (int i=0; i < updateRate; i++)
			{
				// increment the canned data information for this item.
				itemInfo.increment();	

				// create a new OMMItemCmd
				OMMItemCmd cmd = new OMMItemCmd(); 

				// Set the request token associated with this item into the OMMItemCmd.
				cmd.setToken(rq);	
				
				// Encode update response message and set it into OMMItemCmd.
				OMMMsg updateMsg = encodeUpdateMsg(itemInfo);
				cmd.setMsg(updateMsg);  

				// Submit the OMMItemCmd to RFAJ.
   	            if (_providerDemo._provider.submit(cmd, null) ==  0)
	            {
	                System.err.println("Trying to submit for an item with an inactive handle.");

	                //iter.remove();
                    _itemReqTable.remove(rq);

                    // break out the for loop to get next request token
                    break; 
	            }
			}
		}
	}

	// Session was disconnected or closed.
	protected void processInactiveClientSessionEvent(OMMInactiveClientSessionEvent event)
	{
		System.out.println("Received OMM INACTIVE CLIENT SESSION PUB EVENT MSG with handle: " +
				event.getHandle());
		System.out.println("ClientSession from "+ event.getClientIPAddress()+ "/"+ event.getClientHostName() + "/" + event.getListenerName() + " has become inactive.");

		cleanup();
   	}

	// This event contains a request from consumer for processing. 
	// Each event is associated with a particular Thomson Reuters defined
	// or customer defined domains.
	protected void processOMMSolicitedItemEvent(OMMSolicitedItemEvent event)
	{
		OMMMsg msg = event.getMsg();

		switch (msg.getMsgModelType())
		{
			// Reuters defined domain message model - LOGIN
			case RDMMsgTypes.LOGIN:				
				processLoginRequest(event);
				break;
			// Reuters defined domain message model - DIRECTORY	
			case RDMMsgTypes.DIRECTORY:
				processDirectoryRequest(event);	
				break;				
			// Reuters defined domain message model - DICTIONARY
			case RDMMsgTypes.DICTIONARY:
				processDictionaryRequest(event);
				break;
			// All other reuters defined domain message model or 
			// customer's domain message model are considered items.
			default:		
				processItemRequest(event);
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
                    System.out.println("ERROR: Received unsupported NONSTREAMING request");
                    return;
                }
                
            	System.out.println("Login request received");
            	
                OMMAttribInfo attribInfo = msg.getAttribInfo();
                String username = null;
                if (attribInfo.has(OMMAttribInfo.HAS_NAME))
                	username = attribInfo.getName();
                System.out.println("username: " + username);
                            
        		// Data in attribInfo of LOGIN domain has been defined to be ElementList.
        		OMMElementList elementList = (OMMElementList) attribInfo.getAttrib(); 
    
        		// ElementList is iterable.  Each ElementEntry can be accessed through the iterator.
        		for (@SuppressWarnings("unchecked") Iterator<OMMElementEntry> iter = elementList.iterator(); iter.hasNext(); )
        		{
        			OMMElementEntry element = (OMMElementEntry) iter.next();
        			OMMData data = element.getData();	// Get the data from the ElementEntry.
        			System.out.println(element.getName() + ": " + data.toString());
        		}
    
        		// create a new OMMItemCmd
    			OMMItemCmd cmd = new OMMItemCmd(); 
    
    			// Set the request token associated with this item into the OMMItemCmd.
    			cmd.setToken(event.getRequestToken());	
    			
    			// Encode login response message and set it into OMMItemCmd.
                OMMMsg loginRespMsg = encodeLoginRespMsg(elementList,
                                                         msg.isSet(OMMMsg.Indication.REFRESH));
    			cmd.setMsg(loginRespMsg);  
    
    			// Submit the OMMItemCmd to RFAJ.
    	        if (_providerDemo._provider.submit(cmd, null) ==  0)
                {
    	        	System.err.println("Trying to submit Login response msg with inactive handle");
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
            	// Closing the Login stream should cause items associated with that login to be closed.  
            	// The cleanup method will close all items associated with this login.
            	System.out.println("Logout received");
                cleanup();
                return;
            default:
            	System.out.println("Received unsupported message type. " + msg.getMsgType());        	
            	return;
        }
	}

	@SuppressWarnings("deprecation")
    private void processDirectoryRequest(OMMSolicitedItemEvent event)
	{
		OMMMsg msg = event.getMsg();
		switch (msg.getMsgType())
        {
		    case OMMMsg.MsgType.REQUEST:
		    {
            	System.out.println("Directory request received");
    
        		// create a new OMMItemCmd
    			OMMItemCmd cmd = new OMMItemCmd(); 
    
    			// Set the request token associated with this item into the OMMItemCmd.
    			cmd.setToken(event.getRequestToken());	
    			
    			// Encode directory response message and set it into OMMItemCmd.
    			OMMMsg directoryRespMsg = encodeDirectoryRespMsg(event);
    			cmd.setMsg(directoryRespMsg);  
    
    			// Submit the OMMItemCmd to RFAJ.
    	        if (_providerDemo._provider.submit(cmd, null) ==  0)
                {
    	        	System.err.println("Trying to submit directory response msg with inactive handle");
                }
    	        else
    	        {
                    System.out.println("Directory reply sent");
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
            	// RFA internally will clean up the item.
            	System.out.println("Directory close request");
            	return;
            default:
            	System.out.println("Received unsupported message type. " + msg.getMsgType());
            	return;
        }
	}

	@SuppressWarnings("deprecation")
    private void processDictionaryRequest(OMMSolicitedItemEvent event)
	{
		OMMMsg msg = event.getMsg();
        
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REQUEST:
            {
            	System.out.println("Dictionary request received");
            	// OMMAttribInfo will be in the streaming and nonstreaming request.
            	OMMAttribInfo attribInfo = msg.getAttribInfo();
            	String name = attribInfo.getName();
            	System.out.println("dictionary name: " + name);	
    
        		// create a new OMMItemCmd
    			OMMItemCmd cmd = new OMMItemCmd(); 
    
    			// Set the request token associated with this item into the OMMItemCmd.
    			cmd.setToken(event.getRequestToken());	
    			
    			// Encode dictionary response message and set it into OMMItemCmd.
    			OMMMsg dictionaryRespMsg = null;
                if (name.equalsIgnoreCase("rwffld"))
                {
                	dictionaryRespMsg = encodeFldDictionary(event);
                }
                else // name.equalsIgnoreCase("rwfenum")
                {
                	dictionaryRespMsg = encodeEnumDictionary(event);
                }
    			cmd.setMsg(dictionaryRespMsg);  
    
    			// Submit the OMMItemCmd to RFAJ.
    	        if (_providerDemo._provider.submit(cmd, null) ==  0)
                {
    	        	System.err.println("Trying to submit dictionary response msg with inactive handle");
                }
    	        else
    	        {
                    System.out.println("Dictionary reply sent");
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
            	// 	RFA internally will clean up the item.
            	System.out.println("dictionary close request");
            	return;
            default:
            	System.out.println("Received unsupported message type. " + msg.getMsgType());
            	return;
	    }
	}

	// This method  accept incoming item requests. It sends refresh response.
	// If there is at least one streaming item request, an update timer
	// is active. The active timer periodically generates timer event.
	@SuppressWarnings("deprecation")
    private void processItemRequest(OMMSolicitedItemEvent event)
	{
		OMMMsg msg = event.getMsg();
		Token rq = event.getRequestToken();		
		ItemInfo itemInfo = _itemReqTable.get(rq); 

		switch (msg.getMsgType())
		{
		    case OMMMsg.MsgType.REQUEST:
		    {
                // Create itemInfo, if one does not exist for this item.
                // If this is a non-streaming request, do not add it to the request table.
                // If this is the first streaming request, activate update timer.
                // Send refresh response message.
                
                if ( itemInfo == null)
    			{
                    itemInfo = new ItemInfo();
                    itemInfo.setName(msg.getAttribInfo().getName());
                    if (msg.isSet(OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES))
                        itemInfo.setAttribInUpdates(true);
                    
                    if(msg.isSet(OMMMsg.Indication.NONSTREAMING))
                    {
                        System.out.println();
                        System.out.println("Received item non-streaming request for "
                                +  msg.getAttribInfo().getServiceName() + ":" + msg.getAttribInfo().getName());
                    }
                    else
                    {
        				System.out.println();
        				System.out.println("Received item streaming request for "
        						+  msg.getAttribInfo().getServiceName() + ":" + msg.getAttribInfo().getName());
        				_itemReqTable.put(rq, itemInfo);
        				itemInfo.setHandle(event.getHandle());
        				itemInfo.setPriorityCount(1);
        				itemInfo.setPriorityClass(1);
        				
        				if (_itemReqTable.size() == 1)
                            registerTimer();
                    }
                    
                    GenericOMMParser.parse(msg);
    			}
    			else //Re-issue request
    			{
    				System.out.println("Received item reissue for "
    						+  msg.getAttribInfo().getServiceName() + ":" + msg.getAttribInfo().getName());
    				GenericOMMParser.parse(msg);
    			}
    
    			if (msg.has(OMMMsg.HAS_PRIORITY))	
    			{
    				OMMPriority priority = msg.getPriority();
    				itemInfo.setPriorityClass(priority.getPriorityClass());
    				itemInfo.setPriorityCount(priority.getCount());
    			}
    			sendRefreshMsg(event, itemInfo);
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
    		    // For close request, the item is removed from collection.
                // If the collection is then empty, unregister update timer.
    			if ( itemInfo != null)
    			{
    				System.out.println("Item close request: " + itemInfo.getName());
    				// remove the reference to the Token associated for the item and its ItemInfo.
    				_itemReqTable.remove(rq);	
    				if (_itemReqTable.isEmpty())
    				{
    					unregisterTimer();
    				}
    			}
    			return;
    		}
    		default:		
    			System.out.println("Received unsupported message type. " + msg.getMsgType());
    			return;
		}
	}
		
	private void sendRefreshMsg(OMMSolicitedItemEvent event, ItemInfo itemInfo)
	{
		// create a new OMMItemCmd
		OMMItemCmd cmd = new OMMItemCmd(); 

		// Set the request token associated with this item into the OMMItemCmd.
		cmd.setToken(event.getRequestToken());	
		
		// Encode directory response message and set it into OMMItemCmd.
		OMMMsg refreshMsg = encodeRefreshMsg(event, itemInfo);
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

    private void registerTimer()
    {
        if (_timerHandle == null)
        {
        	int updateInterval = 1;
            TimerIntSpec timer = new TimerIntSpec();
            timer.setDelay(updateInterval*1000);
            timer.setRepeating(true);
            _timerHandle = _providerDemo._provider.registerClient(_providerDemo._eventQueue, timer, this, null);
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

	//------------------------------------------------------------------------------------
	// the methods below handle encoding of OMM messages
	//------------------------------------------------------------------------------------
	
	// To encode an OMMMsg follow the steps below:
	// 1. initialize encoder to encode MSG and provide estimated size of this message
	//    _encoder.initialize(OMMTypes.MSG, estimatedMsgSize)
	//    This will allocate the encoder's buffer 
	// 2. get OMMMsg from the pool
	//    OMMMsg outmsg = _providerDemo.getPool().acquireMsg();
	//    This will allocate memory for the message to encode
	// 3. set the header of the message
	// 4. link the message with the encoder
	//    _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);
	//    This will fill the encoder's buffer with the header's data and will
	//    set the encoder's to encode attribInfo and payload as the specified types.
	//    In this example the attribInfo are not being encoded (the message either
	//    does not have attribInfo or they were included in header) and the payload
	//    will be encoded as FIEL_LIST type
	// 5. encode data
	// 6. release the OMMMsg to memory pool
	//    _providerDemo.getPool().releaseMsg(outmsg)
	//    After copying data from the header to the encoder's buffer this message
	//    is not needed. The memory allocated for the message should be returned 
	//    to the pool. Note: this step can be done after step 4.
	// 7. returned the encoder's buffer to the calling method
	//    return (OMMMsg)_encoder.getEncodedObject()
	//    The encoded message is in the encoder's buffer at this time.
	
	private OMMMsg encodeUpdateMsg(ItemInfo itemInfo)
	{
		// set the encoder to encode an OMM message
		// The size is an estimate.  The size MUST be large enough for the entire message.
		// If size is not large enough, RFA will throw out exception related to buffer usage.
		_encoder.initialize(OMMTypes.MSG, 500);

		// allocate memory from memory pool for the message to encode
		OMMMsg outmsg = _providerDemo.getPool().acquireMsg();
		// pass version info
		Handle hd = itemInfo.getHandle();
		if ( hd != null )
		{
			outmsg.setAssociatedMetaInfo(hd);
		}

  		// Set the message type to be an update response.
		outmsg.setMsgType(OMMMsg.MsgType.UPDATE_RESP);
		
		// Set the message model to be MARKET_PRICE.  
		outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
		
		// Set the indication flag for data not to be conflated.
		outmsg.setIndicationFlags(OMMMsg.Indication.DO_NOT_CONFLATE);
		
		// Set the response type to be a quote update.
		outmsg.setRespTypeNum(RDMInstrument.Update.QUOTE);	

		// check if the original request for this item asked for attrib info to be included in the updates.
		if (itemInfo.getAttribInUpdates())	
		{
			outmsg.setAttribInfo(_providerDemo.getServiceName(), itemInfo.getName(), RDMInstrument.NameType.RIC);
		}
		// Initialize message encoding with the update response message.
		// No data is contained in the OMMAttribInfo, so use NO_DATA for the data type of OMMAttribInfo
		// There will be data encoded in the update response message (and we know it is FieldList), so
		// use FIELD_List for the data type of the update response message.
		_encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);

		// Initialize the field list encoding.
		// Specifies that this field list has only standard data (data that is not defined in a DataDefinition)
		// DictionaryId is set to 0.  This means the data encoded in this message used dictionary identified by id equal to 0.
		// Field list number is set to 1.  This identifies the field list (in case for caching in the application or downstream components).
		// Data definition id is set to 0 since standard data does not use data definition.
		// Large data is set to false.  This is used since updates in general are not large in size.  (If unsure, use true)
		_encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)1, (short)0);

		// TRDPRC_1
		// Initialize the entry with the field id and data type from RDMFieldDictionary for TRDPRC_1.
		_encoder.encodeFieldEntryInit( (short)6, OMMTypes.REAL);  
		double value = itemInfo.getTradePrice1();
		long longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		// Encode the real number with the price and the hint value.
		_encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);	
		//	BID
		// Initialize the entry with the field id and data type from RDMFieldDictionary for BID.
		_encoder.encodeFieldEntryInit( (short)22, OMMTypes.REAL);	 
		value = itemInfo.getBid();
		longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		_encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
		//	ASK
		// Initialize the entry with the field id and data type from RDMFieldDictionary for ASK.
		_encoder.encodeFieldEntryInit( (short)25, OMMTypes.REAL);	 
		value = itemInfo.getAsk();
		longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		_encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
		// 	ACVOL_1
		// Initialize the entry with the field id and data type from RDMFieldDictionary for ACVOL_1.
		_encoder.encodeFieldEntryInit( (short)32, OMMTypes.REAL);  
		_encoder.encodeReal(itemInfo.getACVol1(), OMMNumeric.EXPONENT_0);

		// Complete the FieldList.
		_encoder.encodeAggregateComplete();
		
		// release the message to the pool
        _providerDemo.getPool().releaseMsg(outmsg);
        
        // return encoded message
		return (OMMMsg)_encoder.getEncodedObject(); 
	}
	
	private OMMMsg encodeLoginRespMsg(OMMElementList elementList, boolean refreshRequested)
	{
		// set the encoder to encode an OMM message
		_encoder.initialize(OMMTypes.MSG, 1000);
		
		// allocate memory from memory pool for the message to encode
		OMMMsg outmsg = _providerDemo.getPool().acquireMsg();

		outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
		outmsg.setMsgModelType(RDMMsgTypes.LOGIN);
		outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
		outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "login accepted");
		
		if(refreshRequested)
		    outmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
		else
		    outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
		
		// Initialize message encoding with the login response message.
		// There will be attribInfo encoded in the login response message (and we know it is ElementList), 
		// so use ELEMENT_LIST for the data type of attribInfo.
		// No data is contained in the payload, so use NO_DATA for the data type of payload.
		_encoder.encodeMsgInit(outmsg, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);

		// encode attribInfo based on the attribInfo received in login request
		// and this provider supported features
		_encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
		for (Iterator<?> iter = elementList.iterator(); iter.hasNext(); )
		{
			OMMElementEntry element = (OMMElementEntry) iter.next();

			// set all attributes to the values received, except for DownloadConnectionConfig.
			// This attribute is should not be included in login response
			if ( !element.getName().equals(RDMUser.Attrib.DownloadConnectionConfig) ) 
			{
				_encoder.encodeElementEntryInit(element.getName(), element.getDataType());
				_encoder.encodeData(element.getData());
			}
		}
		// add SupportStandby attribute. 
		// In this example it is arbitrary set to not supported.
		_encoder.encodeElementEntryInit(RDMUser.Attrib.SupportStandby, OMMTypes.UINT); 
		_encoder.encodeUInt((long)0);

		// add SupportPauseResume attribute. 
		// In this example it is arbitrary set to not supported.
		_encoder.encodeElementEntryInit(RDMUser.Attrib.SupportPauseResume, OMMTypes.UINT); 
		_encoder.encodeUInt((long)0);
		
		_encoder.encodeAggregateComplete();

		// release the message to the pool
        _providerDemo.getPool().releaseMsg(outmsg);
        
        // return encoded message
		return (OMMMsg)_encoder.getEncodedObject(); 
	}

	private OMMMsg encodeDirectoryRespMsg(OMMSolicitedItemEvent event)
	{
		// set the encoder to encode an OMM message
		_encoder.initialize(OMMTypes.MSG, 1000);

		// allocate memory from memory pool for the message to encode
		OMMMsg respmsg = _providerDemo.getPool().acquireMsg();
        Handle hd = event.getHandle();        
        // pass version info
        if (hd != null )
        {
        	respmsg.setAssociatedMetaInfo(hd);
        }
        
        // set the data in this example by assigning arbitrary values
        respmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        respmsg.setMsgModelType(RDMMsgTypes.DIRECTORY);
        
        if (event.getMsg().isSet(OMMMsg.Indication.NONSTREAMING))
        	respmsg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE, ""); 
        else
        	respmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
        
        respmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        
        if(event.getMsg().isSet(OMMMsg.Indication.REFRESH))
            respmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            respmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        respmsg.setItemGroup(1);
        OMMAttribInfo outAttribInfo = _providerDemo.getPool().acquireAttribInfo();

        // AttribInfo value specifies what type of information is provided in directory response.
        // Encode the information that is being requested. 
        // This application supports only INFO, STATE, and GROUP.
        if (event.getMsg().has(OMMMsg.HAS_ATTRIB_INFO))
        {
        	OMMAttribInfo at = event.getMsg().getAttribInfo();
        	if (at.has(OMMAttribInfo.HAS_FILTER))
        	{
        		// Set the filter information to what was requested.
        		outAttribInfo.setFilter(at.getFilter());  
        	}
        }
        
        // Set the attribInfo into the message.
        respmsg.setAttribInfo(outAttribInfo);	

        // Initialize the response message encoding that encodes no data for attribInfo (the attribInfo are already set), 
        // and encodes payload with the MAP data type.
        _encoder.encodeMsgInit(respmsg, OMMTypes.NO_DATA, OMMTypes.MAP);

        // Map encoding initialization.
        // Specifies the flag for the map, the data type of the key as ascii_string, as defined by RDMUsageGuide.
        // the data type of the map entries is FilterList, the total count hint, and the dictionary id as 0
        _encoder.encodeMapInit( OMMMap.HAS_TOTAL_COUNT_HINT, OMMTypes.ASCII_STRING, OMMTypes.FILTER_LIST, 1, (short)0);

        // MapEntry:  Each service is associated with a map entry with the service name has the key.
        _encoder.encodeMapEntryInit( 0, OMMMapEntry.Action.ADD, null);
        _encoder.encodeString(_providerDemo.getServiceName(), OMMTypes.ASCII_STRING);

        // Filter list encoding initialization.
        // Specifies the flag, the data type in the filter entry as element list, and total count hint of 2 entries.
        _encoder.encodeFilterListInit( 0, OMMTypes.ELEMENT_LIST, 2);

        // Provide INFO filter if application requested.
        if ( (outAttribInfo.getFilter() & RDMService.Filter.INFO) !=0 )
        {
        	// Specifies the filter entry has data, action is SET, the filter id is the filter information, and data type is elementlist.  No permission data is provided.
        	_encoder.encodeFilterEntryInit( OMMFilterEntry.HAS_DATA_FORMAT, OMMFilterEntry.Action.SET, RDMService.FilterId.INFO, OMMTypes.ELEMENT_LIST, null);

        	_encoder.encodeElementListInit( OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        	_encoder.encodeElementEntryInit(RDMService.Info.Name, OMMTypes.ASCII_STRING);
        	_encoder.encodeString(_providerDemo.getServiceName(), OMMTypes.ASCII_STRING); 
        	_encoder.encodeElementEntryInit(RDMService.Info.Vendor, OMMTypes.ASCII_STRING);
        	_encoder.encodeString("Reuters", OMMTypes.ASCII_STRING); 
        	_encoder.encodeElementEntryInit(RDMService.Info.IsSource, OMMTypes.UINT);
        	_encoder.encodeUInt(0L);	
        	_encoder.encodeElementEntryInit(RDMService.Info.Capabilities, OMMTypes.ARRAY);
        	// Pass 0 as the size.  This lets the encoder to calculate the size for each UINT. 
        	_encoder.encodeArrayInit(OMMTypes.UINT, 0); 
        												 
        	_encoder.encodeArrayEntryInit(); 
        	_encoder.encodeUInt((long)RDMMsgTypes.DICTIONARY);
        	_encoder.encodeArrayEntryInit();
        	_encoder.encodeUInt((long)RDMMsgTypes.MARKET_PRICE);
        	_encoder.encodeAggregateComplete();	// Completes the Array.
        	_encoder.encodeElementEntryInit(RDMService.Info.DictionariesProvided, OMMTypes.ARRAY); 
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
        	_encoder.encodeElementEntryInit(RDMService.Info.QoS, OMMTypes.ARRAY); 
        	_encoder.encodeArrayInit(OMMTypes.QOS, 0);
        	_encoder.encodeArrayEntryInit();
        	_encoder.encodeQos(OMMQos.QOS_REALTIME_TICK_BY_TICK);
        	_encoder.encodeAggregateComplete();	// Completes the Array.
        	_encoder.encodeAggregateComplete();	// Completes the ElementList
        }

        // Provide STATE filter if application requested.
        if ( (outAttribInfo.getFilter() & RDMService.Filter.STATE) !=0 )
        {
        	_encoder.encodeFilterEntryInit( OMMFilterEntry.HAS_DATA_FORMAT, OMMFilterEntry.Action.UPDATE, RDMService.FilterId.STATE, OMMTypes.ELEMENT_LIST, null);
        	_encoder.encodeElementListInit( OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        	_encoder.encodeElementEntryInit(RDMService.SvcState.ServiceState, OMMTypes.UINT);
        	_encoder.encodeUInt(1L);
        	_encoder.encodeElementEntryInit(RDMService.SvcState.AcceptingRequests, OMMTypes.UINT);
        	_encoder.encodeUInt(1L);
        	_encoder.encodeElementEntryInit(RDMService.SvcState.Status, OMMTypes.STATE);
        	_encoder.encodeState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
        	_encoder.encodeAggregateComplete(); // Completes the ElementList.
        }

	    _encoder.encodeAggregateComplete(); // any type that requires a count needs to be closed.  This one is for FilterList.
	    _encoder.encodeAggregateComplete();  // any type that requires a count needs to be closed. This one is for Map.

	    // release attribInfo to the pool
	    _providerDemo.getPool().releaseAttribInfo(outAttribInfo);
	    
		// release the message to the pool
        _providerDemo.getPool().releaseMsg(respmsg);
        
        // return encoded message
        return ((OMMMsg)_encoder.getEncodedObject());
	}
		
	// Encoding of the enum dictionary
	private OMMMsg encodeEnumDictionary(OMMSolicitedItemEvent event)
	{
		// set the encoder to encode an OMM message
		_encoder.initialize(OMMTypes.MSG, 250000);

		// allocate memory from memory pool for the message to encode
		OMMMsg msg = _providerDemo.getPool().acquireMsg();
        Handle hd = event.getHandle();        
        // pass version info
        if (hd != null )
        {
        	msg.setAssociatedMetaInfo(hd);
        }
        
		msg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
		msg.setMsgModelType(RDMMsgTypes.DICTIONARY);		
		msg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
		
		if(event.getMsg().isSet(OMMMsg.Indication.REFRESH))
            msg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            msg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);

		if (event.getMsg().isSet(OMMMsg.Indication.NONSTREAMING))
        	msg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE, ""); 
        else
        	msg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");

        msg.setItemGroup(1);
		OMMAttribInfo attribInfo = _providerDemo._pool.acquireAttribInfo();
		attribInfo.setServiceName(_providerDemo.getServiceName());
		attribInfo.setName("RWFEnum");
		attribInfo.setFilter(RDMDictionary.Filter.NORMAL);
		msg.setAttribInfo(attribInfo);
		
		_encoder.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.SERIES);
		FieldDictionary.encodeRDMEnumDictionary(_providerDemo._rwfDictionary, _encoder);
		
	    // release attribInfo to the pool
	    _providerDemo.getPool().releaseAttribInfo(attribInfo);
	    
		// release the message to the pool
        _providerDemo.getPool().releaseMsg(msg);
        
        // return encoded message
		return (OMMMsg) _encoder.getEncodedObject();
	}

    // Encoding of the RDMFieldDictionary.
	private OMMMsg encodeFldDictionary(OMMSolicitedItemEvent event)
	{
		// allocate memory from memory pool for the message to encode
		// number_of_fids * 60(approximate size per row)
		int encoderSizeForFieldDictionary = _providerDemo._rwfDictionary.size() * 60;
        _encoder.initialize(OMMTypes.MSG, encoderSizeForFieldDictionary);

		OMMMsg msg = _providerDemo.getPool().acquireMsg();
        Handle hd = event.getHandle();        
        // pass version info
        if (hd != null )
        {
        	msg.setAssociatedMetaInfo(hd);
        }
        
		msg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
		msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
		msg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
		
		if(event.getMsg().isSet(OMMMsg.Indication.REFRESH))
            msg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            msg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
		
		if (event.getMsg().isSet(OMMMsg.Indication.NONSTREAMING))
        	msg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE, ""); 
        else
        	msg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
        
		msg.setItemGroup(1);
		OMMAttribInfo attribInfo = _providerDemo._pool.acquireAttribInfo();
		attribInfo.setServiceName(_providerDemo.getServiceName());
		attribInfo.setName("RWFFld");
		attribInfo.setFilter(RDMDictionary.Filter.NORMAL);
		msg.setAttribInfo(attribInfo);
		
		_encoder.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.SERIES);
		FieldDictionary.encodeRDMFieldDictionary(_providerDemo._rwfDictionary, _encoder);
		
	    // release attribInfo to the pool
	    _providerDemo.getPool().releaseAttribInfo(attribInfo);
	    
		// release the message to the pool
        _providerDemo.getPool().releaseMsg(msg);
        
        // return encoded message
		return (OMMMsg) _encoder.getEncodedObject();
	}
	
	private OMMMsg encodeRefreshMsg(OMMSolicitedItemEvent event, ItemInfo itemInfo)
	{
		// set the encoder to encode an OMM message
		// The size is an estimate.  The size MUST be large enough for the entire message.
		// If size is not large enough, RFA will throw out exception related to buffer usage.
		_encoder.initialize(OMMTypes.MSG, 500);

		// allocate memory from memory pool for the message to encode
		OMMMsg outmsg = _providerDemo.getPool().acquireMsg();
		
		// pass version info
		Handle hd = event.getHandle();
		if ( hd != null )
		{
			outmsg.setAssociatedMetaInfo(hd);
		}

		// Set the message type to be refresh response.
		outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);	
		// Set the message model type to be the type requested.
		outmsg.setMsgModelType(event.getMsg().getMsgModelType());		
		// Indicates this message will be the full refresh; or this is the last refresh in the multi-part refresh.        
		outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);	
		// Set the item group to be 2.  Indicates the item will be in group 2.
		outmsg.setItemGroup(2);	
		// Set the state of the item stream.
		// Set state
		if (event.getMsg().isSet(OMMMsg.Indication.NONSTREAMING))
		{
		    outmsg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE, "OK");
		}
		else
		{
		    outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "OK");
		}

		// This code encodes refresh for MARKET_PRICE domain
		outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
		
		if(event.getMsg().isSet(OMMMsg.Indication.REFRESH))
            outmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
		
		outmsg.setAttribInfo(event.getMsg().getAttribInfo());
		_encoder.initialize(OMMTypes.MSG, 1000);
		_encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);

		_encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA | OMMFieldList.HAS_INFO, (short)0, (short)1, (short)0);
		// 	RDNDISPLAY
		_encoder.encodeFieldEntryInit( (short)2, OMMTypes.UINT);
		_encoder.encodeUInt(100L);
		//	RDN_EXCHID
		_encoder.encodeFieldEntryInit( (short)4, OMMTypes.ENUM);
		_encoder.encodeEnum(155);
		//	DIVIDEND_DATE
		_encoder.encodeFieldEntryInit( (short)38, OMMTypes.DATE);
		_encoder.encodeDate(2006, 12, 25);
		//	TRDPRC_1
		_encoder.encodeFieldEntryInit( (short)6, OMMTypes.REAL);
		double value = itemInfo.getTradePrice1();
		long longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		_encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
		//	BID
		_encoder.encodeFieldEntryInit( (short)22, OMMTypes.REAL);	
		value = itemInfo.getBid();
		longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		_encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);
		//	ASK
		_encoder.encodeFieldEntryInit( (short)25, OMMTypes.REAL);	 
		value = itemInfo.getAsk();
		longValue = Rounding.roundDouble2Long(value, OMMNumeric.EXPONENT_NEG4);
		_encoder.encodeReal(longValue, OMMNumeric.EXPONENT_NEG4);

		// 	ACVOL_1
		_encoder.encodeFieldEntryInit( (short)32, OMMTypes.REAL);
		_encoder.encodeReal(itemInfo.getACVol1(), OMMNumeric.EXPONENT_0);
		// ASK_TIME
		_encoder.encodeFieldEntryInit( (short)267, OMMTypes.TIME);
		_encoder.encodeTime(19, 12, 23, 0);
		_encoder.encodeAggregateComplete();
            
		// release the message to the pool
        _providerDemo.getPool().releaseMsg(outmsg);
        
        // return encoded message
		return (OMMMsg) _encoder.getEncodedObject();
	}
}