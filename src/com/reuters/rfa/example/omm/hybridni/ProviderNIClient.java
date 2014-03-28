package com.reuters.rfa.example.omm.hybridni;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;
import com.reuters.rfa.session.omm.OMMItemCmd;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;
import com.reuters.rfa.session.omm.OMMProvider;

/**
 * Handles requests/responses to/from a non-interactive OMMProvider.
 * <p>
 * The OMMProvider publishes to Source Distributor Application.
 * <p>
 * The ProviderNIClient is responsible for
 * <li>sending login request to the OMMProvider
 * <li>handling login response from OMMProvider
 * <li>publishing messages to the OMMProvider
 * <li>handling disconnect from OMMProvider
 * 
 * <p>
 * <b>Startup</b>
 * </p>
 * The OMMHybridNIDemo
 * <li>Creates the ProviderNIClient at startup (Creates the OMMProvider)
 * <li>Sends login request to OMMProvider
 * <li>On receiving a successful login response, notify the OMMHybridNIDemo; The
 * OMMHybridNIDemo in turn creates the ConsumerClient & sends a Login request to
 * the OMMConsumer
 * 
 * <p>
 * <b>Publishing messages to OMMprovider</b>
 * </p>
 * <li>The ConsumerClient publishes the response messages it receives to the
 * OMMprovider
 * 
 * <p>
 * <b>Disconnect(Disconnect provider application)</b>
 * </p>
 * <li>Receives a login suspect message; the OMMHybridNIDemo(in turn
 * ConsumerClient) is notified
 * <li>The ConsumerClient stops publishing messages by resetting the
 * refreshReceived flag to FALSE for all items
 * 
 * <p>
 * <b>Reconnect(Reconnect to source distributor)</b>
 * </p>
 * <li>Receives a login refresh message
 * <li>Notify the OMMHybridNIDemo(in turn ConsumerClient); reissue directory
 * request
 * 
 * <p>
 * <b>Closed Stream</b>
 * </p>
 * <li>The event queue is deactivated, the OMMProvider is destroyed
 * <li>The application is notified to shutdown </ul>
 */
public class ProviderNIClient implements Client
{
    final String _className; // class name

    HybridNIDemo _parent; // the application

    OMMProvider _provider; // OMM Provider
    final EventQueue _providerEventQueue; // event q
    Handle _loginHandle; // login handle
    Handle _errorHandle; // error handle
    final OMMItemCmd _itemCommand; // item command to publish

    boolean _bLoggedIn; // logged in to providerNI
    boolean _bDisconnected; // disconnected

    /**
     * Constructor
     */
    public ProviderNIClient(HybridNIDemo parent)
    {
        _parent = parent;

        _className = "providerniClient";

        _providerEventQueue = EventQueue.create(_className + "Queue");
        _parent._eventQueueGroup.addEventQueue(_providerEventQueue, null);

        _bLoggedIn = false;
        _bDisconnected = false;

        _itemCommand = new OMMItemCmd();
    }

    /**
     * Cleanup - deactivate event queue - unregister error & login handles -
     * destroy the OMM Provider event source
     */
    void cleanup()
    {
        System.out.println(_className + " Cleaning up");

        _providerEventQueue.deactivate();

        // unregister error handle
        if (_errorHandle != null)
        {
            _provider.unregisterClient(_errorHandle);
            _errorHandle = null;
        }

        // unregister login
        if (_loginHandle != null)
        {
            _provider.unregisterClient(_loginHandle);
            _loginHandle = null;
        }

        _provider.destroy();
    }

    /**
     * Create the OMM Provider
     */
    void initialize()
    {
        System.out.println("provider ni client" + "Initializing");

        _provider = (OMMProvider)_parent._session.createEventSource(EventSource.OMM_PROVIDER,
                                                                    "providerni");
    }

    /**
     * Encode login request and register them to RFA Register for Error
     * Notifications
     */
    void makeLoginRequest()
    {
        OMMMsg ommmsg = _parent.encodeLoginRequestMessage(RDMUser.Role.PROVIDER);
        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
        ommItemIntSpec.setMsg(ommmsg);

        System.out.println(_className + ": Sending login request to NI Provider...");
        _loginHandle = _provider.registerClient(_providerEventQueue, ommItemIntSpec, this, null);

        OMMErrorIntSpec errIntSpec = new OMMErrorIntSpec();
        _errorHandle = _provider.registerClient(_providerEventQueue, errIntSpec, this, null);

        System.out.println("Initialization complete, waiting for login response");
    }

    /**
     * Process Event
     */
    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_ITEM_EVENT:
            {
                OMMMsg msg = ((OMMItemEvent)event).getMsg();
                if (msg.getMsgModelType() == RDMMsgTypes.LOGIN)
                    processLoginResponse(msg);
                else
                    System.out.println(_className + ": Received unsupported message!");

                break;
            }
            case Event.OMM_CMD_ERROR_EVENT:
            {
                OMMCmdErrorEvent errorEvent = (OMMCmdErrorEvent)event;
                System.out.println("Received OMMCmd ERROR EVENT for id: " + errorEvent.getCmdID()
                        + "  " + errorEvent.getStatus().getStatusText());
                break;
            }
            case Event.COMPLETION_EVENT:
                System.out.println(_className + ": Receive a COMPLETION_EVENT, "
                        + event.getHandle());
                break;
            default:
                break;
        }
    }

    /**
     * Process Login Response Message based upon the message type
     */
    void processLoginResponse(OMMMsg responseMessage)
    {
        System.out.println(_className + ":# Received Login Response from ni provider - "
                + OMMMsg.MsgType.toString(responseMessage.getMsgType()));

        // Login accepted
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

                // re-issue the directory request
                _parent.niproviderIsReconnected();
            }

            // initial login; executed only once at startup
            // when logging the 1st time
            if (!isLoggedIn())
            {
                GenericOMMParser.parse(responseMessage);
                setLoggedIn(true);
                _parent.niproviderIsLoggedIn();
            }
            return;
        }

        // stream is closed; cleanup & destroy application
        if (responseMessage.isFinal())
        {
            System.out.println(_className
                    + ":# Received stream closed from ni provider. Destroying Application...");
            cleanup();
            _parent.shutdown();
            return;
        }

        // received disconnect; notify parent to stop publishing messages
        if (responseMessage.has(OMMMsg.HAS_STATE)
                && responseMessage.getState().getDataState() == OMMState.Data.SUSPECT)
        {
            if (isLoggedIn())
            {
                System.out.println(_className + ":# Received disconnect from ni provider...");
                setLoggedIn(false);
                setDisconnected(true);
                _parent.niproviderIsDisconnected();
            }
        }
        if (responseMessage.getMsgType() == OMMMsg.MsgType.GENERIC)
        {
            System.out.println("Received generic message type, not supported. ");
        }

    }

    /**
     * Submit message to OMM Provider
     */
    void submitResponse(OMMMsg msg, Token token)
    {

        _itemCommand.setMsg(msg);
        _itemCommand.setToken(token);

        int ret = _provider.submit(_itemCommand, null);
        if (ret == 0)
            System.err.println("Trying to submit for an item with an inactive handle.");
    }

    /**
     * set the LoginGranted flag to true
     */
    void setLoggedIn(boolean bState)
    {
        _bLoggedIn = bState;
        _bDisconnected = !_bLoggedIn;
    }

    /**
     * set the disconnected flag to true
     */
    void setDisconnected(boolean bState)
    {
        _bDisconnected = bState;
    }

    /**
     * return true if logged in
     */
    boolean isLoggedIn()
    {
        return _bLoggedIn;
    }

    /**
     * return true if disconnected
     */
    boolean isDisconnected()
    {
        return _bDisconnected;
    }

    /**
     * Generate Token
     */
    Token generateToken()
    {
        return _provider.generateToken();
    }
}
// ///////////////////////////////////////////////////////////////////////////////
// / End of file
// ///////////////////////////////////////////////////////////////////////////////
