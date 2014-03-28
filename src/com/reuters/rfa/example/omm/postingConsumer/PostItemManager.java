package com.reuters.rfa.example.omm.postingConsumer;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.ExampleUtil;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMHandleItemCmd;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;
import com.reuters.rfa.utility.HexDump;

/**
 * <p>
 * The is a Client class that handles request/response for items and does posts
 * </p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Encoding streaming request message for the specified model using OMM
 * message
 * <li>Register/subscribe one or multiple messages to RFA</li>
 * <li>Implement a Client which processes events from an
 * <code>OMMConsumer</code>
 * <li>Use {@link com.reuters.rfa.example.utility.GenericOMMParser
 * GenericOMMParser} to parse {@link com.reuters.rfa.omm.OMMMsg OMMMsg} response
 * messages.
 * <li>Read configuration of items to be posted
 * <li>Create and Send Post Messages containing data or message
 * <li>Process Ack Messages received from RFA
 * <li>Unregistered all items when the application is not interested anymore.
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder},
 * {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer} from
 * StarterConsumer_Post
 * 
 * @see StarterConsumer_Post
 * 
 */
public class PostItemManager implements Client
{
    StarterConsumer_Post _mainApp;
    private String _className = "PostItemManager: ";

    OMMItemIntSpec _itemIntSpec = new OMMItemIntSpec();
    OMMHandleItemCmd _ommPostHandleItemCmd;

    OMMPool _pool;
    OMMMsg _ommmsg;

    OMMMsg _postOMMMsg;
    OMMEncoder _postOMMEncoder;
    OMMAttribInfo _postOMMAttrib;

    OMMMsg _payloadOMMMsg;
    OMMEncoder _payloadOMMEncoder;
    OMMAttribInfo _payloadMsgOMMAttrib;

    boolean _bSendItemRequest_ip;
    boolean _bSendPostAfterItemOpen_ip;

    int _iSequenceNo = 0;
    int _iPostId = 0;

    private final static int SINGLE_PART = 1;
    private final static int MULTI_PART_START = 2;
    private final static int MULTI_PART_MIDDLE = 3;
    private final static int MULTI_PART_END = 4;

    private final static int NO_PAYLOAD = 0;
    private final static int PAYLOAD_DATA = 1;

    private final static int AUTO = -1;
    
    int _itemOpenCount;
    private int _itemRefreshCount;

    ArrayList<String> _itemList;
    Hashtable<Handle, String> _itemHandles;
    Hashtable<Handle, byte[]> _itemLocks;

    ArrayList<PostInfo> _postList;
    Hashtable<Integer, PostInfo> _postSubmitIds;

    String INFO_APPNAME;
    String APPNAME;

    /*
     * Constructor
     */
    public PostItemManager(StarterConsumer_Post mainApp)
    {
        _mainApp = mainApp;
        _itemList = new ArrayList<String>();
        _postList = new ArrayList<PostInfo>();

        _itemHandles = new Hashtable<Handle, String>();
        _itemLocks = new Hashtable<Handle, byte[]>();
        _postSubmitIds = new Hashtable<Integer, PostInfo>();

        _pool = _mainApp.getPool();
        _ommmsg = _pool.acquireMsg();

        _postOMMMsg = _pool.acquireMsg();
        _postOMMEncoder = _pool.acquireEncoder();
        _postOMMAttrib = _pool.acquireAttribInfo();

        _payloadOMMMsg = _pool.acquireMsg();
        _payloadOMMEncoder = _pool.acquireEncoder();
        _payloadMsgOMMAttrib = _pool.acquireAttribInfo();

        _ommPostHandleItemCmd = new OMMHandleItemCmd();

        INFO_APPNAME = _mainApp.INFO_APPNAME;
        APPNAME = _mainApp.APPNAME;
    }

    /*
     * Initialize from configuration
     */
    public boolean initialize()
    {
        _bSendItemRequest_ip = CommandLine.booleanVariable("openItemStreams");
        _bSendPostAfterItemOpen_ip = CommandLine.booleanVariable("sendPostAfterItemOpen");

        // item names
        String itemList = CommandLine.variable("itemName");
        StringTokenizer st = new StringTokenizer(itemList, ",");
        while (st.hasMoreTokens())
        {
            String itemName = st.nextToken().trim();
            _itemList.add(itemName);
            _itemOpenCount++;
        }

        // post items
        String postInputFileName = CommandLine.variable("postInputFileName");

        try
        {
            FileInputStream fstream = new FileInputStream(postInputFileName);

            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            String strLine;

            while ((strLine = br.readLine()) != null)
            {
                processPostConfiguration(strLine);
            }
            // }
            // Close the input stream
            in.close();
        }
        catch (Exception e)
        {// Catch exception if any

            System.err.println("Post Input File Error: " + e.getMessage());

            e.printStackTrace();

            return false;
        }

        boolean bDumpPost = CommandLine.booleanVariable("dumpPost");
        int i = 0;
        if (bDumpPost == true)
        {
            for (i = 0; i < _postList.size(); i++)
            {
                if (i == 0)
                {
                    System.out.println("Dump Post List");
                    System.out.println("*****************");
                }
                System.out.print((i + 1) + ". ");
                ((PostInfo)_postList.get(i)).dump(true);
            }

            if (i != 0)
                System.out.println("*****************");
        }

        return true;
    }

    /*
     * Process the line read configuration
     */
    void processPostConfiguration(String line)
    {
        if (line.length() == 0)
        {
            return;
        }

        char c1 = line.charAt(0);
        if ((c1 == '#') || (c1 == '*'))
        {
            return;
        }

        StringTokenizer singlePostItemInfoTk = new StringTokenizer(line, " ");

        PostInfo postInfo = null;

        if (singlePostItemInfoTk.hasMoreTokens())
            postInfo = new PostInfo();

        while (singlePostItemInfoTk.hasMoreTokens())
        {
            String nameValue = singlePostItemInfoTk.nextToken().trim();
            
            int splitterIdx = nameValue.indexOf("="); 

            String tag = nameValue.substring(0, splitterIdx); 
            String value = nameValue.substring(splitterIdx+1, nameValue.length());
            
            if (tag == null)
                continue;

            if (tag.equalsIgnoreCase("name"))
                postInfo.itemName = value;
            else if (tag.equalsIgnoreCase("service"))
            {
                if (ExampleUtil.isNumeric(value) == true)
                {
                    postInfo.bUseServiceId = true;
                    postInfo.serviceId = Integer.parseInt(value);
                }
                else
                {
                    postInfo.bUseServiceName = true;
                    postInfo.serviceName = value;
                }
            }
            else if (tag.equalsIgnoreCase("type"))
            {
                if (value.equalsIgnoreCase("onStream"))
                    postInfo.bOnStream = true;
                else
                    postInfo.bOnStream = false;
            }
            else if (tag.equalsIgnoreCase("part"))
            {
                if (value.equalsIgnoreCase("single"))
                    postInfo.partType = SINGLE_PART;
                else if (value.equalsIgnoreCase("multi_first"))
                    postInfo.partType = MULTI_PART_START;
                else if (value.equalsIgnoreCase("multi_middle"))
                    postInfo.partType = MULTI_PART_MIDDLE;
                else if (value.equalsIgnoreCase("multi_last"))
                    postInfo.partType = MULTI_PART_END;
                else
                    postInfo.partType = SINGLE_PART;
            }// auto, off, number...
            else if (tag.equalsIgnoreCase("id"))
            {
                if (value.equalsIgnoreCase("true"))
                {
                    postInfo.bId = true;
                    postInfo.postId = AUTO;
                }
                else if (value.equalsIgnoreCase("false"))
                {
                    postInfo.bId = false;
                }
                else
                {
                    postInfo.bId = true;
                    postInfo.postId = Integer.parseInt(value);
                }
            }// auto, off, number...
            else if (tag.equalsIgnoreCase("sequence"))
            {
                if (value.equalsIgnoreCase("true"))
                {
                    postInfo.bSequence = true;
                    postInfo.sequenceId = AUTO;
                }
                else if (value.equalsIgnoreCase("false"))
                {
                    postInfo.bSequence = false;
                }
                else
                {
                    postInfo.bSequence = true;
                    postInfo.sequenceId = Integer.parseInt(value);
                }
            }
            else if (tag.equalsIgnoreCase("pe"))
            {
            	// save this; this will be processed at the end since it requires serviceID
                postInfo.sPE = value;
            }
            else if (tag.equalsIgnoreCase("ack"))
            {
                if (value.equalsIgnoreCase("true"))
                    postInfo.bNeedAck = true;
                else
                    postInfo.bNeedAck = false;
            }
            else if (tag.equalsIgnoreCase("userRightsMask"))
            {
            	if( value.equalsIgnoreCase("0") )
            	{
            		postInfo.bSetUserRights = true;
            		postInfo.userRightsMask = 0;
            	}
            	else
            	{
            		String[] pieces1 = value.split("\\|");
            		postInfo.sUserRightsMask = value;

            		for( int i = 0; i < pieces1.length; i++ )
            		{
            			if (pieces1[i].equalsIgnoreCase("create"))
            			{
            				postInfo.bSetUserRights = true;
            				postInfo.userRightsMask |= OMMMsg.UserRights.CREATE_FLAG;
            			}

            			if (pieces1[i].equalsIgnoreCase("delete"))
            			{
            				postInfo.bSetUserRights = true;
            				postInfo.userRightsMask |= OMMMsg.UserRights.DELETE_FLAG;
            			}

            			if (pieces1[i].equalsIgnoreCase("modifyperm"))
            			{
            				postInfo.bSetUserRights = true;
            				postInfo.userRightsMask |= OMMMsg.UserRights.MODIFY_PERMISSION_DATA_FLAG;
            			}
            		}
            	}
            }
            else if (tag.equalsIgnoreCase("attrib"))
            {
                if (value.equalsIgnoreCase("attribInfo"))
                    postInfo.bAttribInfo = true;
                else if (value.equalsIgnoreCase("attribInfoAttrib"))
                {
                    postInfo.bAttribInfo = true;
                    postInfo.bAttribData = true;
                }
                else
                // none
                {
                    postInfo.bAttribInfo = false;
                    postInfo.bAttribData = false;
                }
            }
            else if (tag.equalsIgnoreCase("payload"))
            {
                if (value.equalsIgnoreCase("none"))
                    postInfo.payloadType = NO_PAYLOAD;
                else if (value.equalsIgnoreCase("data"))
                    postInfo.payloadType = PAYLOAD_DATA;
                else if (value.equalsIgnoreCase("update_msg"))
                    postInfo.payloadType = OMMMsg.MsgType.UPDATE_RESP;
                else if (value.equalsIgnoreCase("status_msg"))
                    postInfo.payloadType = OMMMsg.MsgType.STATUS_RESP;
                else if (value.equalsIgnoreCase("refresh_msg"))
                    postInfo.payloadType = OMMMsg.MsgType.REFRESH_RESP;
                else
                    postInfo.payloadType = NO_PAYLOAD;
            }
            else if (tag.equalsIgnoreCase("payloadMsgAttrib"))
            {
                if (value.equalsIgnoreCase("attribInfo"))
                    postInfo.bPayloadMsgAttribInfo = true;
                else if (value.equalsIgnoreCase("attribInfoAttrib"))
                {
                    postInfo.bPayloadMsgAttribInfo = true;
                    postInfo.bPayloadMsgAttribData = true;
                }
                else
                // none
                {
                    postInfo.bPayloadMsgAttribInfo = false;
                    postInfo.bPayloadMsgAttribData = false;
                }
            }
            else if (tag.equalsIgnoreCase("payloadMsgPayload"))
            {
                if (value.equalsIgnoreCase("none"))
                    postInfo.payloadMsgPayload = NO_PAYLOAD;
                else if (value.equalsIgnoreCase("data"))
                    postInfo.payloadMsgPayload = PAYLOAD_DATA;
            }
        }

        if( postInfo.sPE != null )
        {
        	if( postInfo.sPE.equalsIgnoreCase("useRefreshLock") )
        		postInfo.bUseRefreshLock = true;
        	else
        	{
        		if( postInfo.bUseServiceId == true )
        		{
        			postInfo.dacsLock = ExampleUtil.generatePELock(postInfo.serviceId, postInfo.sPE);
        		}
        		else
        		{
        			System.out.println("Error! Ignoring PE "+postInfo.sPE+" since serviceID is missing!");        			
        		}
            }
        }

        // offstream posting must encode attrib
        if (postInfo.bOnStream == false)
        {
            postInfo.bAttribInfo = true;
        }

        _postList.add(postInfo);

    }

    /*
     * send item requests for the configured items
     */
    public void sendItemRequests()
    {
        String serviceName = CommandLine.variable("serviceName");

        for (int i = 0; i < _itemList.size(); i++)
        {
            String itemName = (String)_itemList.get(i);

            System.out.println("--> " + _className + "Subscribing to item " + itemName
                    + ", service " + serviceName);

            Handle itemHandle = openItemStream(itemName, serviceName);
            _itemHandles.put(itemHandle, itemName);
        }
    }

    /*
     * open an item stream
     */
    public Handle openItemStream(String itemName, String serviceName)
    {
        _ommmsg.setMsgType(OMMMsg.MsgType.REQUEST);
        _ommmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
        _ommmsg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        _ommmsg.setPriority((byte)1, 1);
        _ommmsg.setAttribInfo(serviceName, itemName, RDMInstrument.NameType.RIC);

        _itemIntSpec.setMsg(_ommmsg);

        Handle itemHandle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(),
                                                                     _itemIntSpec, this, null);

        return itemHandle;
    }

    /*
     * close the item
     */
    public void close(String itemName, boolean bUseLoginStream)
    {
        Handle postHandle = null;
        if (bUseLoginStream == true)
        {
            postHandle = _mainApp.getLoginHandle();
        }
        else
        {
            postHandle = getItemHandle(itemName);
        }

        _mainApp.getOMMConsumer().unregisterClient(postHandle);
    }

    /**
     * Unregisters/unsubscribes all items individually
     */
    public void closeRequest()
    {
        Enumeration<Handle> em = _itemHandles.keys();
        Handle itemHandle = null;

        while (em.hasMoreElements())
        {
            itemHandle = (Handle)em.nextElement();
            _mainApp.getOMMConsumer().unregisterClient(itemHandle);
        }
        _itemList.clear();
    }

    /*
     * send posts for the configured items
     */
    public void sendPosts()
    {
        if (_postList == null || _postList.size() == 0)
        {
            System.out.println(INFO_APPNAME + "Input file not available OR No posts available");
            return;
        }

        for (int i = 0; i < _postList.size(); i++)
        {
            PostInfo postInfo = (PostInfo)_postList.get(i);

            int id = i + 1;
            System.out.println("\n--> ........" + _className + "Sending post: " + id
                    + "...........>");

            int nextSequenceNumber = postInfo.sequenceId;
            if (nextSequenceNumber == AUTO)
                nextSequenceNumber = _iSequenceNo + 1;

            int nextPostId = postInfo.postId;
            if (nextPostId == AUTO)
                nextPostId = _iPostId + 1;

            postInfo.dump(false);

            System.out.print(String.format(" (AutoPostId=%d, AutoSequenceNo=%d)\n", nextPostId,
                                           nextSequenceNumber));
            int submitPostId = doPost(postInfo);
            System.out
                    .println("--> ........" + _className + "Sending post: " + id + "...........>");

            //
            // 1st post cached for demo purposes;
            // Rest of the posts are not cached since memory may grow
            //
            if (i == 0)
                _postSubmitIds.put(new Integer(submitPostId), postInfo);
        }
    }

    /*
     * post to RFA
     */
    public int doPost(PostInfo postInfo)
    {
        _postOMMMsg.clear();

        // ****************************************//
        // - message type and msg model -
        // ****************************************//
        _postOMMMsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
        _postOMMMsg.setMsgType(OMMMsg.MsgType.POST);

        // ****************************************//
        // - sequence number -
        // ****************************************//
        // if configured, set the sequence number
        if (postInfo.bSequence == true)
        {
            if (postInfo.sequenceId == AUTO)
                _postOMMMsg.setSeqNum(++_iSequenceNo);
            else
                _postOMMMsg.setSeqNum(postInfo.sequenceId);
        }

        // ****************************************//
        // - post id -
        // ****************************************//
        // if configured, set the post id
        if (postInfo.bId == true)
        {
            if (postInfo.postId == AUTO)
                _postOMMMsg.setId(++_iPostId);
            else
                _postOMMMsg.setId(postInfo.postId);
        }

        // ****************************************//
        // - permission expression -
        // ****************************************//
        if (postInfo.bUseRefreshLock == true ) // use item refresh lock
        {
        	// invalid for off stream posting 
        	if (postInfo.bOnStream == false )
        	{
        		System.out.println("Ignoring Refresh Lock for off stream posting");
        	}
        	else // if refresh lock available, set it for onstream posting
        	{
        		Handle itemHandle  = getItemHandle( postInfo.itemName );
        		byte[] permLock = getItemRefreshLock( itemHandle );
        		
        		if( permLock != null )
        			_postOMMMsg.setPermissionData( permLock );
        	}
        }
        else // use the configured dacs lock  
        {
        	if (postInfo.dacsLock != null )
        	{
        		_postOMMMsg.setPermissionData( postInfo.dacsLock );
        	}
        }

        // ****************************************//
        // - user rights -
        // ****************************************//
        // if configured, set the user rights mask
        if ( postInfo.bSetUserRights == true )
        {
        	_postOMMMsg.setUserRightsMask( postInfo.userRightsMask );
        }

        // ****************************************//
        // - indication flags: part details -
        // ****************************************//
        // if configured, set the part details
        int indicationFlags = 0;

        if (postInfo.partType == SINGLE_PART)
            indicationFlags = OMMMsg.Indication.POST_COMPLETE | OMMMsg.Indication.POST_INIT;
        else if (postInfo.partType == MULTI_PART_START)
            indicationFlags = OMMMsg.Indication.POST_INIT;
        else if (postInfo.partType == MULTI_PART_END)
            indicationFlags = OMMMsg.Indication.POST_COMPLETE;

        // ****************************************//
        // - indication flags: ack -
        // ****************************************//
        // if configured, set the ack
        if (postInfo.bNeedAck == true)
            indicationFlags = indicationFlags | OMMMsg.Indication.NEED_ACK;

        // ****************************************//
        // - indication flags -
        // ****************************************//
        _postOMMMsg.setIndicationFlags(indicationFlags);

        // ****************************************//
        // - attribInfo on postOMMMsg -
        // ****************************************//
        if (postInfo.bAttribInfo == true)
        {
            // use service name
            if (postInfo.bUseServiceName == true)
            {
                _postOMMMsg.setAttribInfo(postInfo.serviceName, postInfo.itemName,
                                          RDMInstrument.NameType.RIC);
            }
            // use service id
            else if (postInfo.bUseServiceId == true)
            {
                _postOMMAttrib.clear();
                _postOMMAttrib.setServiceID(postInfo.serviceId);
                _postOMMAttrib.setName(postInfo.itemName);
                _postOMMAttrib.setNameType(RDMInstrument.NameType.RIC);

                _postOMMMsg.setAttribInfo(_postOMMAttrib);
            }
        }

        OMMMsg sendMessage = _postOMMMsg;

        // **********************************************************************//
        // - use of encoder to set attribData, payload data or payload msg -
        // **********************************************************************//
        boolean bUseEncoder = false;
        short attribDataType = OMMTypes.NO_DATA;
        short payloadDataType = OMMTypes.NO_DATA;

        // extended attributes (aka attribData)
        if (postInfo.bAttribData == true)
        {
            attribDataType = OMMTypes.ELEMENT_LIST;
            bUseEncoder = true;
        }
        // payload
        if (postInfo.payloadType == NO_PAYLOAD)
        {
            payloadDataType = OMMTypes.NO_DATA;
        }
        else if (postInfo.payloadType == PAYLOAD_DATA)
        {
            payloadDataType = OMMTypes.FIELD_LIST;
            bUseEncoder = true;
        }
        else
        {
            payloadDataType = OMMTypes.MSG;
            bUseEncoder = true;
        }

        // encode the message
        if (bUseEncoder == true)
        {
            _postOMMEncoder.initialize(OMMTypes.MSG, 1000);
            _postOMMEncoder.encodeMsgInit(_postOMMMsg, attribDataType, payloadDataType);

            // encode extended attributes
            if (postInfo.bAttribData == true)
            {
                encodePostAttrib();
            }

            // payload data not configured
            if (postInfo.payloadType == NO_PAYLOAD)
            {

            }
            // encode payload=fieldList
            else if (postInfo.payloadType == PAYLOAD_DATA)
            {
                // - Payload : Field List - Start //
                _postOMMEncoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0,
                                                    (short)1, (short)0);

                _postOMMEncoder.encodeFieldEntryInit((short)32, OMMTypes.REAL);
                _postOMMEncoder.encodeReal(400, OMMNumeric.EXPONENT_0);

                _postOMMEncoder.encodeAggregateComplete();
                // - Payload : Field List - End //
            }
            // encode payload=OMMMsg
            else
            {
                // create payload msg based on the configuration
                OMMMsg payloadOMMMsg = createPayloadOMMMsg(postInfo);

                // encode payload message
                _postOMMEncoder.encodeMsg(payloadOMMMsg);
            }

            // get the encoded message
            sendMessage = (OMMMsg)_postOMMEncoder.getEncodedObject();
        }

        // set the message on the Cmd
        _ommPostHandleItemCmd.setMsg(sendMessage);

        // get the handle based on the stream
        Handle postHandle = null;
        if (postInfo.bOnStream == false)
        {
            postHandle = _mainApp.getLoginHandle();
        }
        else
        {
            postHandle = getItemHandle(postInfo.itemName);
        }

        int submitId = 0;
        if (postHandle != null)
        {
            _ommPostHandleItemCmd.setHandle(postHandle);

            submitId = _mainApp.getOMMConsumer().submit(_ommPostHandleItemCmd, postHandle);
        }
        else
        {
            System.out.println("* App failed to post since handle is NULL");
        }

        return submitId;
    }

    /*
     * encode attrib on the post message
     */
    void encodePostAttrib()
    {
        _postOMMEncoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);

        _postOMMEncoder.encodeElementEntryInit("AttribRow1", OMMTypes.ASCII_STRING);
        _postOMMEncoder.encodeString("Element1", OMMTypes.ASCII_STRING);

        _postOMMEncoder.encodeElementEntryInit("AttribRow2", OMMTypes.ASCII_STRING);
        _postOMMEncoder.encodeString("Element2", OMMTypes.ASCII_STRING);

        _postOMMEncoder.encodeAggregateComplete(); // ElementList
    }

    /*
     * encode attrib elementList on the post payload message
     */
    void encodePostPayloadAttrib()
    {
        _payloadOMMEncoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0,
                                                 (short)0);

        _payloadOMMEncoder.encodeElementEntryInit("payloadAttribRow1", OMMTypes.ASCII_STRING);
        _payloadOMMEncoder.encodeString("payloadElement1", OMMTypes.ASCII_STRING);

        _payloadOMMEncoder.encodeElementEntryInit("payloadAttribRow2", OMMTypes.ASCII_STRING);
        _payloadOMMEncoder.encodeString("payloadElement2", OMMTypes.ASCII_STRING);

        _payloadOMMEncoder.encodeAggregateComplete(); // ElementList
    }

    /*
     * encode payload OMMMsg
     */
    OMMMsg createPayloadOMMMsg(PostInfo postInfo)
    {
        _payloadOMMMsg.clear();

        // encode msg header based on the message type
        if (postInfo.payloadType == OMMMsg.MsgType.REFRESH_RESP)
        {
            _payloadOMMMsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
            
            _payloadOMMMsg.setIndicationFlags(OMMMsg.Indication.DO_NOT_CONFLATE |
            								  OMMMsg.Indication.REFRESH_COMPLETE );
            
            _payloadOMMMsg.setRespTypeNum((short)1);
        }
        else if (postInfo.payloadType == OMMMsg.MsgType.UPDATE_RESP)
        {
            _payloadOMMMsg.setMsgType(OMMMsg.MsgType.UPDATE_RESP);
            _payloadOMMMsg.setIndicationFlags(OMMMsg.Indication.DO_NOT_CONFLATE);
            _payloadOMMMsg.setRespTypeNum((short)1);
        }
        else if (postInfo.payloadType == OMMMsg.MsgType.STATUS_RESP)
        {
            _payloadOMMMsg.setMsgType(OMMMsg.MsgType.STATUS_RESP);
        }

        _payloadOMMMsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
        _payloadOMMMsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE,
                                "All is Well!");

        // encode attribbInfo
        if (postInfo.bPayloadMsgAttribInfo == true)
        {
            // use service name
            if (postInfo.bUseServiceName == true)
            {
                _payloadOMMMsg.setAttribInfo(postInfo.serviceName, postInfo.itemName,
                                             RDMInstrument.NameType.RIC);
            }
            // use service id
            else if (postInfo.bUseServiceId == true)
            {
                _payloadMsgOMMAttrib.clear();
                _payloadMsgOMMAttrib.setServiceID(postInfo.serviceId);
                _payloadMsgOMMAttrib.setName(postInfo.itemName);
                _payloadMsgOMMAttrib.setNameType(RDMInstrument.NameType.RIC);

                _payloadOMMMsg.setAttribInfo(_payloadMsgOMMAttrib);
            }
        }

        OMMMsg encodedMsg = _payloadOMMMsg;

        short attribDataType = OMMTypes.NO_DATA;
        short payloadDataType = OMMTypes.NO_DATA;
        boolean bUseEncoder = false;

        // use encoder is attribDataType and/or payloadDataType is configured
        if (postInfo.bPayloadMsgAttribData == true)
        {
            attribDataType = OMMTypes.ELEMENT_LIST;
            bUseEncoder = true;
        }

        if (postInfo.payloadMsgPayload != NO_PAYLOAD)
        {
            payloadDataType = OMMTypes.FIELD_LIST;
            bUseEncoder = true;
        }

        // encode message using encoder
        if (bUseEncoder == true)
        {
            _payloadOMMEncoder.initialize(OMMTypes.MSG, 500);
            _payloadOMMEncoder.encodeMsgInit(_payloadOMMMsg, attribDataType, payloadDataType);

            // ***** extended attributes : Element List - Start ***** //
            if (attribDataType == OMMTypes.ELEMENT_LIST)
            {
                encodePostPayloadAttrib();
            }
            // ***** extended attributes : Element List - End ***** //

            if (payloadDataType == OMMTypes.FIELD_LIST)
            {
                // ***** Payload : Field List - Start ***** //
                _payloadOMMEncoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0,
                                                       (short)1, (short)0);
                _payloadOMMEncoder.encodeFieldEntryInit((short)1, OMMTypes.UINT);
                _payloadOMMEncoder.encodeUInt(400);
                _payloadOMMEncoder.encodeAggregateComplete();
                // ***** Payload : Field List - End ***** //
            }
            encodedMsg = ((OMMMsg)_payloadOMMEncoder.getEncodedObject());
        }

        return encodedMsg;
    }

    /**
     * Process incoming events based on the event type. Events of type
     * {@link com.reuters.rfa.common.Event#OMM_ITEM_EVENT OMM_ITEM_EVENT} are
     * parsed using {@link com.reuters.rfa.example.utility.GenericOMMParser
     * GenericOMMParser}
     */
    public void processEvent(Event event)
    {
        System.out.println("\n*******" + _className + "::processEvent *Start* ************");
        if (event.getType() != Event.OMM_ITEM_EVENT)
        {
            System.out.println("<-- " + _className + "Received " + event.toString());
        }

        switch (event.getType())
        {
            case Event.COMPLETION_EVENT:
                break;

            case Event.OMM_CMD_ERROR_EVENT:
                _mainApp.processCmdErrorEvent(_className, event);
                break;

            case Event.OMM_ITEM_EVENT:
                processItemResponseMessage(event);
                break;

            default:
                System.out.println("Error! " + _className + " Received an unsupported Event type.");
                _mainApp.cleanup(-1);
                break;
        }
        System.out.println("\n*******" + _className + "::processEvent *End* ************");
    }

    /*
     * process item response messsage
     */
    void processItemResponseMessage(Event event)
    {
        String itemName = (String)_itemHandles.get(event.getHandle());

        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg ommMsg = ie.getMsg();
        short ommMsgType = ommMsg.getMsgType();
        String ommMsgTypeStr = OMMMsg.MsgType.toString((byte)ommMsgType);
        System.out.println("<-- " + _className + "Received for " + itemName + " "
                + event.toString() + " " + ommMsgTypeStr);

        GenericOMMParser.parse(ommMsg);

        byte messageType = ommMsg.getMsgType();
        switch (messageType)
        {
            case OMMMsg.MsgType.ACK_RESP:
            {
                _mainApp.processAckResponse(_className, ommMsg);
                break;
            }

            case OMMMsg.MsgType.REFRESH_RESP:
            {
                _itemRefreshCount++;
                System.out.println(INFO_APPNAME + "Received Item Refresh " + _itemRefreshCount
                        + " for Item " + itemName);

                if( ommMsg.has(OMMMsg.HAS_PERMISSION_DATA) )
                {
                	byte[] permLock = ommMsg.getPermissionData();
                	_itemLocks.put( event.getHandle(), permLock );        	
                }
                
                // if configured, send posts after item streams are open
                if ((_bSendPostAfterItemOpen_ip == true) && (_itemRefreshCount == _itemOpenCount))
                {
                    System.out.println(INFO_APPNAME
                                    + "All "
                                    + _itemOpenCount
                                    + " item(s) are opened; Starting to do Posts, based on configuration in "
                                    + APPNAME);
                    // slow down for service to come up
                    ExampleUtil.slowDown(1000);
                    sendPosts();
                }

                break;
            }

            case OMMMsg.MsgType.UPDATE_RESP:
            {
                break;
            }

            case OMMMsg.MsgType.STATUS_RESP:
            {
                _mainApp.processStatusResponse(_className, ommMsg);
                break;
            }

            default:
                System.out.println(_className + ": Received Item Response - "
                        + OMMMsg.MsgType.toString(ommMsg.getMsgType()));
                break;
        }

    }

    /*
     * get item handle given the item name
     */
    Handle getItemHandle(String nameMatch)
    {
        Enumeration<Handle> em = _itemHandles.keys();
        Handle itemHandle = null;

        while (em.hasMoreElements())
        {
            itemHandle = (Handle)em.nextElement();

            String itemName = (String)_itemHandles.get(itemHandle);

            if (itemName.equalsIgnoreCase(nameMatch))
                break;
        }

        return itemHandle;
    }

    /*
     * get item refresh lock given the item handle
     */
    byte[] getItemRefreshLock( Handle handle )
    {
    	if( handle == null )
    		return null;
    				
    	byte[] permLock = _itemLocks.get(handle);
        return permLock;
    }

    /*
     * Class to store post info read from configuration
     */
    class PostInfo
    {
        String itemName;
        String serviceName;
        int serviceId;
        boolean bUseServiceName;
        boolean bUseServiceId;
        boolean bOnStream;
        int partType;
        boolean bId;
        boolean bSequence;
        int postId;
        int sequenceId;
        boolean bNeedAck;
        // user rights
        boolean bSetUserRights;
        short userRightsMask;
        String sUserRightsMask;
        // dacs lock
        byte[] dacsLock;
        String sPE;
        boolean bUseRefreshLock;
        
        boolean bAttribInfo;
        boolean bAttribData;
        int payloadType;
        boolean bPayloadMsgAttribInfo;
        boolean bPayloadMsgAttribData;
        int payloadMsgPayload;

        public void dump(boolean eol)
        {
            System.out.print("name=" + itemName);

            if (bUseServiceName == true)
                System.out.print(" serviceName=" + serviceName);
            else if (bUseServiceId == true)
                System.out.print(" serviceId=" + serviceId);

            if (bOnStream == true)
                System.out.print(" type=onstream");
            else
                System.out.print(" type=offstream");

            if (partType == SINGLE_PART)
                System.out.print(" part=single");
            else if (partType == MULTI_PART_START)
                System.out.print(" type=multi_first");
            else if (partType == MULTI_PART_MIDDLE)
                System.out.print(" type=multi_middle");
            else if (partType == MULTI_PART_END)
                System.out.print(" type=multi_last");


            if (bSequence == true)
            {
                if (sequenceId == AUTO)
                    System.out.print(" sequence=AutoId");
                else
                    System.out.format(" sequence=" + sequenceId);
            }
            else
            {
                System.out.print(" sequence=false");
            }
            
            if (bId == true)
            {
                if (postId == AUTO)
                    System.out.print(" id=AutoId");
                else
                    System.out.format(" id=" + postId);
            }
            else
            {
                System.out.print(" id=false");
            }

            if( sPE != null )
            {
            	System.out.print(" PE(s)="+sPE);
            	System.out.print(" UseRefreshLock="+bUseRefreshLock);
            	System.out.print(" dacsLock="+ HexDump.formatHexString(dacsLock));
            }

            if (bNeedAck == true)
                System.out.print(" ack=true");
            else
                System.out.print(" ack=false");
            
            if( bSetUserRights == true )
            {
            	String s = String.format(" userRightsMask=%d ( %s )",userRightsMask,sUserRightsMask);
            	System.out.print( s );
            }
            
            if (bAttribInfo == true)
                System.out.print(" attribInfo=true");
            else
                System.out.print(" attribInfo=false");

            if (bAttribData == true)
                System.out.print(" attribData=true");
            else
                System.out.print(" attribData=false");

            if (payloadType == NO_PAYLOAD)
                System.out.println(" payload=none");
            else if (payloadType == PAYLOAD_DATA)
                System.out.println(" payload=data");
            else if (payloadType == OMMMsg.MsgType.UPDATE_RESP)
                System.out.println(" payload=update_msg");
            else if (payloadType == OMMMsg.MsgType.REFRESH_RESP)
                System.out.println(" payload=refresh_msg");
            else if (payloadType == OMMMsg.MsgType.STATUS_RESP)
                System.out.println(" payload=status_msg");

            if (bPayloadMsgAttribInfo == true)
                System.out.print(" payloadMsgAttribInfo=true");
            else
                System.out.print(" payloadMsgAttribInfo=false");

            if (bPayloadMsgAttribData == true)
                System.out.print(" payloadMsgAttribData=true");
            else
                System.out.print(" payloadMsgAttribData=false");

            if (payloadMsgPayload == NO_PAYLOAD)
                System.out.println(" payloadMsgPayload=none");
            else if (payloadMsgPayload == PAYLOAD_DATA)
                System.out.println(" payloadMsgPayload=data");

            if (eol == true)
                System.out.println();
        }
    }
}
