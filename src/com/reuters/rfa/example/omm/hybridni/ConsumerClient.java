package com.reuters.rfa.example.omm.hybridni;

import java.util.StringTokenizer;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.example.omm.hybrid.OMMMsgReencoder;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * Handles requests/responses to/from an OMMConsumer. The OMMConsumer sources
 * data from a provider application.
 * <p>
 * The ConsumerClient is responsible for
 * <li>sending requests to the OMM Consumer and
 * <li>handling responses from the OMM Consumer
 * <li>handling disconnect
 * 
 * <p>
 * <b>Startup</b>
 * </p>
 * The OMMHybridNIDemo
 * <li>Creates the ConsumerClient when ProviderNIClient indicates that login is
 * granted by source distributor. This creates the OMM Consumer
 * <li>Sends login request to OMMConsumer
 * 
 * <p>
 * <b>Sending Requests</b>
 * </p>
 * <li>On receiving a login refresh, a directory request is made
 * <li>On receiving a directory refresh, item requests are made
 * 
 * <p>
 * <b>Processing Directory and Item Refreshes/Updates</b>
 * </p>
 * <li>On receiving a directory refresh,a flag is set to indicate that refresh
 * has been received;
 * <li>On receiving item refresh messages,a flag is set to indicate that refresh
 * has been received
 * <li>Directory & Item Refresh Messages are published to OMMProvider
 * <li>Update messages are published to OMMProvider only if refreshReceived Flag
 * is set to TRUE
 * 
 * <p>
 * <b>Disconnect(Lost connection with provider application)</b>
 * </p>
 * <li>The directory update message is published to OMMprovider
 * <li>On receiving a login suspect, no further messages are published to
 * OMMprovider; This is achieved by setting the refreshReceived flag to FALSE
 * 
 * <p>
 * <b>Reconnect(Reconnect to provider application)</b>
 * </p>
 * <li>A login refresh message is received
 * <li>Reissue directory request
 * <li>On receiving a directory refresh message, reissue all item requests; thus
 * resume publishing messages
 * 
 * <p>
 * <b>Closed Stream</b>
 * </p>
 * <li>The directory & item handles are unregistered
 * <li>The event queue is deactivated
 * <li>The OMM Consumer is destroyed
 * <li>The application is notified to shutdown
 * 
 */
public class ConsumerClient implements Client
{
    private final String _className; // class name

    private HybridNIDemo _parent; // the application

    private OMMConsumer _consumer; // OMM Consumer
    private final EventQueue _consumerEventQueue; // event q
    private Handle _loginHandle; // login Handle
    private boolean _bLoggedIn = false;
    private boolean _bDisconnected = false;
    
    // interest spec
    private OMMItemIntSpec _ommItemIntSpec = new OMMItemIntSpec();

    private boolean _bReceivedItemUpdates = false;

    // Cache to store items requested
    private int _itemCount = -1; // count of items & index
    // final short DIRECTORY_ID = 0; // index for directory
    
    // list of requested items
    private ItemCache[] _cachedItemsList = new ItemCache[100];

    /*
     * 
     * :------:------------------:--------------:--------:-----------------:
     * :String: short : Token : Handle : boolean :
     * :------:------------------:--------------:--------:-----------------: :
     * name : messageModelType : publishToken : handle : refreshReceived :
     * :======:=================-:==============:========:=================:
     * (DIRECTORY) 0 --> : : : : : :
     * :------:------------------:--------------:--------:-----------------:
     * (ITEM 1) 1 --> :"A.N" : : : : : :
     * :------:------------------:--------------:--------:-----------------: : :
     * : : : : : v
     * :------:------------------:--------------:--------:-----------------:
     */

    /**
     * Constructor
     */
    protected ConsumerClient(HybridNIDemo parent)
    {
        _className = "consumerClient";

        _parent = parent;

        _consumerEventQueue = EventQueue.create(_className + "Queue");
        _parent._eventQueueGroup.addEventQueue(_consumerEventQueue, null);
    }

    /**
     * Cleanup - deactivate event queue - unregister directory & item requests -
     * destroy the OMM consumer event source
     */
    void cleanup()
    {
        System.out.println(_className + " Cleaning up");

        _consumerEventQueue.deactivate();

        if (_loginHandle != null)
        {
            // unregister directory & item handles
            for (int i = 0; i < _itemCount; i++)
            {
                _consumer.unregisterClient(_cachedItemsList[i]._handle);
                _cachedItemsList[i]._handle = null;
            }
        }
        _consumer.destroy();
    }

    /**
     * set the LoginGranted flag to true
     */
    private void setLoggedIn(boolean bState)
    {
        _bLoggedIn = bState;
        _bDisconnected = !_bLoggedIn;
    }

    /**
     * set the disconnected flag to true
     */
    private void setDisconnected(boolean bState)
    {
        _bDisconnected = bState;
    }

    /**
     * return true if logged in
     */
    private boolean isLoggedIn()
    {
        return _bLoggedIn;
    }

    /**
     * return true if disconnected
     */
    private boolean isDisconnected()
    {
        return _bDisconnected;
    }

    /**
     * Initialize - Populate cache with directory request & item requests -
     * Create the OMM Consumer
     */
    void initialize()
    {
        System.out.println(_className + " Initializing");

        // Directory
        ItemCache itemCache = new ItemCache("DIRECTORY", (short)0);
        _cachedItemsList[++_itemCount] = itemCache;

        // Items
        // String serviceName = CommandLine.variable( "serviceName" );
        String itemNames = CommandLine.variable("itemName");
        String mmt = CommandLine.variable("mmt");
        short capability = RDMMsgTypes.msgModelType(mmt);

        // Note: "," is a valid character for RIC name.
        // This application need to be modified if RIC names have ",".
        StringTokenizer st = new StringTokenizer(itemNames, ",");
        while (st.hasMoreTokens())
        {
            String itemName = st.nextToken().trim();
            itemCache = new ItemCache(itemName, capability);
            if (_cachedItemsList.length == _itemCount + 1)
            {
                int previousCapacity = _cachedItemsList.length;
                int newCapacity = _cachedItemsList.length * 2;
                ItemCache[] newcachedItemsList = new ItemCache[newCapacity];
                System.arraycopy(_cachedItemsList, 0, newcachedItemsList, 0, previousCapacity);
                _cachedItemsList = newcachedItemsList;
            }
            _cachedItemsList[++_itemCount] = itemCache;
        }

        // create OMM Consumer
        _consumer = (OMMConsumer)_parent._session.createEventSource(EventSource.OMM_CONSUMER,
                                                                    "consumer");
    }

    /**
     * Encode login request and register them to RFA
     */
    void makeLoginRequest()
    {
        OMMMsg requestMessage = _parent.encodeLoginRequestMessage(RDMUser.Role.CONSUMER);

        _ommItemIntSpec.setMsg(requestMessage);
        System.out.println(_className + ": Sending login request to Consumer...");
        _loginHandle = registerRequest(_ommItemIntSpec, null);
    }

    /**
     * Encode directory request and register/reissue them to RFA; The request is
     * registered if a handle is not available; Otherwise the request is
     * reissued
     */
    private void makeDirectoryRequest()
    {
        OMMMsg requestMessage = _parent.encodeDirectoryRequestMessage();

        _ommItemIntSpec.setMsg(requestMessage);
        System.out.println(_className + ": Sending directory request...");

        ItemCache itemCache = _cachedItemsList[0];
        // The handle is null for initial request
        if (itemCache._handle == null)
        {
            itemCache._publishToken = _parent.generateToken();

            ItemClosure itemClosure = new ItemClosure(0, itemCache._publishToken);
            itemCache._handle = registerRequest(_ommItemIntSpec, itemClosure);
        }
        else
        // reissue
        {
            reissueRequest(_ommItemIntSpec, _cachedItemsList[0]._handle);
        }
        _parent.releaseEncodedMessage(requestMessage);
    }

    /**
     * Encode item requests and register/reissue them to RFA The request is
     * registered if a handle is not available; Otherwise the request is
     * reissued
     */
    private void makeItemRequests()
    {
        System.out.println(_className + ": sendRequest: Sending item requests...");

        OMMMsg ommItemRequestMessage = _parent.encodeItemRequestMessageHeader();

        String serviceName = CommandLine.variable("serviceName");

        for (int i = 1; i <= _itemCount; i++)
        {
            ItemCache itemCache = _cachedItemsList[i];
            if (itemCache == null)
                break;

            ommItemRequestMessage = _parent.encodeItemRequestAttribInfoMessage(ommItemRequestMessage,
                                                        itemCache._messageModelType, serviceName,
                                                        itemCache._name);

            // Set the message into interest spec
            _ommItemIntSpec.setMsg(ommItemRequestMessage);

            itemCache._publishToken = _parent.generateToken();
            ItemClosure itemClosure = new ItemClosure(_itemCount, itemCache._publishToken);

            if (itemCache._handle == null)
                itemCache._handle = registerRequest(_ommItemIntSpec, itemClosure);
            else
                reissueRequest(_ommItemIntSpec, itemCache._handle);
        }
        _parent.releaseEncodedMessage(ommItemRequestMessage);
    }

    /**
     * Register Request with RFA
     */
    private Handle registerRequest(OMMItemIntSpec ommItemIntSpec, Object closure)
    {
        Handle handle = _consumer
                .registerClient(_consumerEventQueue, ommItemIntSpec, this, closure);
        return handle;
    }

    /**
     * Reissue Request to RFA
     */
    private void reissueRequest(OMMItemIntSpec ommItemIntSpec, Handle handle)
    {
        _consumer.reissueClient(handle, ommItemIntSpec);
    }

    /**
     * Process Event
     */
    public void processEvent(Event event)
    {
        int eventType = event.getType();

        if (eventType == Event.OMM_ITEM_EVENT)
        {
            OMMMsg msg = ((OMMItemEvent)event).getMsg();

            // GenericOMMParser.parse(msg);
            switch (msg.getMsgModelType())
            {
                case RDMMsgTypes.LOGIN:
                    processLoginResponse(msg);
                    break;
                case RDMMsgTypes.DIRECTORY:
                    processDirectoryResponse((OMMItemEvent)event);
                    break;
                default:
                    processItemResponse((OMMItemEvent)event);
                    break;
            }
            return;
        }

        if (eventType == Event.COMPLETION_EVENT)
            System.out.println(_className + ": Receive a COMPLETION_EVENT, " + event.getHandle());
    }

    /**
     * Process Login Response Message based upon the message types
     */
    private void processLoginResponse(OMMMsg responseMessage)
    {
        System.out.println(_className + ":* Received Login Response from consumer - "
                + OMMMsg.MsgType.toString(responseMessage.getMsgType()));

        // Login accepted; send directory request
        if ((responseMessage.getMsgType() == OMMMsg.MsgType.STATUS_RESP)
                && (responseMessage.has(OMMMsg.HAS_STATE))
                && (responseMessage.getState().getStreamState() == OMMState.Stream.OPEN)
                && (responseMessage.getState().getDataState() == OMMState.Data.OK))
        {
            // reconnect
            if (isDisconnected())
            {
                setLoggedIn(true);
                setDisconnected(false);
            }

            // initial login; executed only once at startup
            // when logging the 1st time
            if (!isLoggedIn())
            {
                setLoggedIn(true);
            }

            makeDirectoryRequest();
        }

        // stream is closed; cleanup & destroy application
        if (responseMessage.isFinal())
        {
            System.out.println(_className + ":* Received stream closed. Destroying itself");
            cleanup();
            _parent.shutdown();
        }

        // received disconnect; stop sending messages to providerNI
        if (responseMessage.has(OMMMsg.HAS_STATE)
                && responseMessage.getState().getDataState() == OMMState.Data.SUSPECT)
        {
            if (isLoggedIn())
            {
                System.out.println(_className + ":* Received disconnect from consumer...");
                setLoggedIn(false);
                setDisconnected(true);
                stopPublishingMessages();
            }
        }

        if (responseMessage.getMsgType() == OMMMsg.MsgType.GENERIC)
        {
            System.out.println("Received generic message type, not supported. ");
        }

    }

    /**
     * Process Directory Response Message based upon the message types
     */
    private void processDirectoryResponse(OMMItemEvent event)
    {
        OMMMsg responseMessage = event.getMsg();
        ItemClosure itemClosure = (ItemClosure)event.getClosure();
        int itemIdx = itemClosure._idx;

        System.out.println(_className + ":* Received Directory Response from consumer - "
                + OMMMsg.MsgType.toString(responseMessage.getMsgType()));

        // update; onpass to providerNI only if a refresh had been received previously
        // a provider disconnect sends a directory update with service down
        if (responseMessage.getMsgType() == OMMMsg.MsgType.UPDATE_RESP)
        {
            if (_cachedItemsList[itemIdx]._bRefreshReceived == true)
                _parent.publishMessage2ProviderNI(responseMessage, itemClosure._publishToken);

            return;
        }
        else if (responseMessage.getMsgType() == OMMMsg.MsgType.GENERIC)
        {
            System.out.println("Received generic message type, not supported. ");
        }

        // stream is closed; cleanup;
        // onpass to providerNI only if a refresh had been received previously
        if (responseMessage.isFinal())
        {
            if (_cachedItemsList[itemIdx]._bRefreshReceived == true)
                _parent.publishMessage2ProviderNI(responseMessage, itemClosure._publishToken);

            _cachedItemsList[itemIdx]._bRefreshReceived = false;
            _cachedItemsList[itemIdx]._handle = null;
            _cachedItemsList[itemIdx]._publishToken = null;

            return;
        }

        // refresh; set flag to indicate refresh has been received;
        // change response type from SOLICITED to UNSOLOCITED;
        // request items
        if (responseMessage.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
        {
            _cachedItemsList[itemIdx]._bRefreshReceived = true;
            OMMMsg reEncodedMessage = OMMMsgReencoder.changeResponseTypeToUnsolicited(event
                    .getMsg(), 1000);
            // Would need to replace map key from consumer with the correct
            // service name for provider.
            // For the time being make sure that the service name is the same
            // for provider, this hybrid application & the source distributor service name
            _parent.publishMessage2ProviderNI(reEncodedMessage, itemClosure._publishToken);
            makeItemRequests();
        }

        // disconnect; onpass this providerNI
        // not necessary to check if a previous refresh had been received;
        // this is because a previous login suspect message will reset
        // the refreshReceived status
        if (responseMessage.getMsgType() == OMMMsg.MsgType.STATUS_RESP)
        {
            // inform the providerNI that service is down
            _parent.publishMessage2ProviderNI(responseMessage, itemClosure._publishToken);
        }
    }

    /**
     * Process Item Response Messages based upon the message types
     */
    private void processItemResponse(OMMItemEvent event)
    {
        OMMMsg responseMessage = event.getMsg();
        ItemClosure itemClosure = (ItemClosure)event.getClosure();
        int itemIdx = itemClosure._idx;

        // update; onpass to providerNI only if a refresh had been received
        // previously
        if (responseMessage.getMsgType() == OMMMsg.MsgType.UPDATE_RESP)
        {
            if ((!_bReceivedItemUpdates) && (!_parent._providerNIClient.isDisconnected()))
            {
                System.out.println(_className + ":* Received Item Updates from consumer - "
                        + OMMMsg.MsgType.toString(responseMessage.getMsgType()));
                _bReceivedItemUpdates = true;
            }

            if (_cachedItemsList[itemIdx]._bRefreshReceived == true)
                _parent.publishMessage2ProviderNI(responseMessage, itemClosure._publishToken);

            return;
        }

        // stream is closed; cleanup;
        // onpass to providerNI only if a refresh had been received previously
        if (responseMessage.isFinal())
        {
            if (_cachedItemsList[itemIdx]._bRefreshReceived == true)
                _parent.publishMessage2ProviderNI(responseMessage, itemClosure._publishToken);

            _cachedItemsList[itemIdx]._bRefreshReceived = false;
            _cachedItemsList[itemIdx]._handle = null;
            _cachedItemsList[itemIdx]._publishToken = null;

            return;
        }

        // refresh; set flag to indicate refresh has been received;
        // change response type from SOLICITED to UNSOLICITED;
        // request items
        if (responseMessage.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
        {
            // change resp_type_num from SOLICITED to UNSOLICITED
            OMMMsg reEncodedMessage = OMMMsgReencoder.changeResponseTypeToUnsolicited(event
                    .getMsg(), 1000);
            _cachedItemsList[itemIdx]._bRefreshReceived = true;
            _parent.publishMessage2ProviderNI(reEncodedMessage, itemClosure._publishToken);
        }
        else if (responseMessage.getMsgType() == OMMMsg.MsgType.GENERIC)
        {
            System.out.println("Received generic message type, not supported. ");
        }
        else
        {
            System.out.println("ERROR: " + _className + ": Received unexpected message type. "
                    + responseMessage.getMsgType());
        }

    }

    /**
     * Reset the refreshReceived flag to indicate that refresh is pending
     */
    void stopPublishingMessages()
    {
        for (int i = 0; i < _itemCount; i++)
        {
            _cachedItemsList[i]._bRefreshReceived = false;
        }
        _bReceivedItemUpdates = false;
    }

    /**
     * Reissue directory request to resume publishing
     */
    void resumePublishingMessages()
    {
        // reissue directory request
        makeDirectoryRequest();
    }

    /**
     * The cached item
     */
    class ItemCache
    {
        String _name;
        short _messageModelType;
        Token _publishToken;
        Handle _handle;
        boolean _bRefreshReceived;

        protected ItemCache(String name, short messageModelType)
        {
            _name = name;
            _messageModelType = messageModelType;
            _bRefreshReceived = false;
        }
    }

    /**
     * The closure object associated with a request/response
     */
    class ItemClosure
    {
        int _idx;
        Token _publishToken;

        protected ItemClosure(int idx, Token token)
        {
            _idx = idx;
            _publishToken = token;
        }
    }
}
// ///////////////////////////////////////////////////////////////////////////////
// / End of file
// ///////////////////////////////////////////////////////////////////////////////
