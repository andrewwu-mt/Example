package com.reuters.rfa.example.omm.provni;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * <p>
 * The is a Client class that handle request and response for Login.
 * </p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Encoding streaming request message for Login using OMM message
 * <li>Register for Login to RFA</li>
 * <li>Implement a Client which processes events from a <code>OMMProvider</code>
 * (non-interactive client publishing) and handle Login responses</li>
 * <li>Use {@link com.reuters.rfa.example.utility.GenericOMMParser
 * GenericOMMParser} to parse {@link com.reuters.rfa.omm.OMMMsg OMMMsg} response
 * messages.
 * <li>Unregistered login, logout from server.
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMProvider OMMProvider} from
 * OMMProviderNIDemo
 * 
 * @see StarterProvider_NonInteractive
 * 
 */

public class LoginClient implements Client
{
    Handle _loginHandle;
    StarterProvider_NonInteractive _mainApp;
    boolean _loggedIn;

    private String _className = "LoginClient";

    public LoginClient(StarterProvider_NonInteractive mainApp)
    {
        _mainApp = mainApp;
        _loginHandle = null;
        _loggedIn = false;
    }

    /**
     * Encodes streaming request messages for Login and register it to RFA
     */
    public void sendRequest()
    {
        OMMMsg ommmsg = encodeLoginReqMsg();
        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
        ommItemIntSpec.setMsg(ommmsg);
        System.out.println(_className + ": Sending login request...");
        _loginHandle = _mainApp._provider.registerClient(_mainApp._eventQueue, ommItemIntSpec,
                                                         this, null);
    }

    private OMMMsg encodeLoginReqMsg()
    {
        OMMPool pool = _mainApp._pool;
        OMMEncoder encoder = pool.acquireEncoder();
        encoder.initialize(OMMTypes.MSG, 500);
        OMMMsg msg = pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.LOGIN);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        msg.setAttribInfo(null, CommandLine.variable("user"), RDMUser.NameType.USER_NAME);

        encoder.encodeMsgInit(msg, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);
        encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        encoder.encodeElementEntryInit("ApplicationId", OMMTypes.ASCII_STRING);
        encoder.encodeString(CommandLine.variable("application"), OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit("Position", OMMTypes.ASCII_STRING);
        encoder.encodeString(CommandLine.variable("position"), OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit(RDMUser.Attrib.Role, OMMTypes.UINT);
        encoder.encodeUInt((long)RDMUser.Role.PROVIDER);
        encoder.encodeAggregateComplete();

        // Get the encoded message from the encoder
        OMMMsg encMsg = (OMMMsg)encoder.getEncodedObject();

        // Release the message that own by the application
        pool.releaseMsg(msg);

        return encMsg; // return the encoded message
    }

    /**
     * Unregisters/unsubscribes login handle
     * 
     */
    public void closeRequest()
    {
        if (_loginHandle != null)
        {
            _mainApp._provider.unregisterClient(_loginHandle);
            _loginHandle = null;
        }
    }

    public void processEvent(Event event)
    {
        if (event.getType() == Event.COMPLETION_EVENT)
        {
            System.out.println(_className + ": Receive a COMPLETION_EVENT, " + event.getHandle());
            return;
        }
        else if (event.getType() == Event.OMM_CMD_ERROR_EVENT)
        {
            processOMMCmdErrorEvent((OMMCmdErrorEvent)event);
            return;
        }

        if (event.getType() != Event.OMM_ITEM_EVENT)
        {
            System.out.println("ERROR: " + _className + " Received an unsupported Event type.");
            _mainApp.cleanup();
            return;
        }

        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg respMsg = ie.getMsg();
        if (respMsg.getMsgModelType() != RDMMsgTypes.LOGIN)
        {
            System.out.println("ERROR: " + _className + " Received a non-LOGIN model type.");
            _mainApp.cleanup();
            return;
        }

        System.out.println(_className + ".processEvent: Received Login Response... ");

        if (respMsg.isFinal())
        {
            System.out.println(_className + ": Login Response message is final.");
            GenericOMMParser.parse(respMsg);
            _mainApp.cleanup();
            return;
        }

        System.out.println(_className + ": Received Login Response - "
                + OMMMsg.MsgType.toString(respMsg.getMsgType()));
        GenericOMMParser.parse(respMsg);

        // Note: This code assumes that there is only one login refresh
        // (the Session is configured with one Connection in the connectionList
        // so it only connects to one SrcDist). If there is more than one
        // Connection, then this could would need to be changed to always
        // call processLogin(true) when there is a refresh. In general,
        // if applications wish to use two Connections, it is recommended
        // that they use two Sessions. That way the initial and recovery
        // refreshes will not be sent to Connections that do not need them.
        if ((respMsg.getMsgType() == OMMMsg.MsgType.STATUS_RESP)
                && (respMsg.getRespTypeNum() == OMMMsg.RespType.SOLICITED)
                && (respMsg.has(OMMMsg.HAS_STATE))
                && (respMsg.getState().getStreamState() == OMMState.Stream.OPEN)
                && (respMsg.getState().getDataState() == OMMState.Data.OK))
        {
            if (!_loggedIn)
            {
                _loggedIn = true;
                _mainApp.processLogin(true);
            }
        }
        else if (respMsg.has(OMMMsg.HAS_STATE)
                && respMsg.getState().getDataState() == OMMState.Data.SUSPECT)
        {
            if (_loggedIn)
            {
                _loggedIn = false;
                _mainApp.processLogin(false);
            }
        }
    }

    protected void processOMMCmdErrorEvent(OMMCmdErrorEvent errorEvent)
    {
        System.out.println("Received OMMCmd ERROR EVENT for id: " + errorEvent.getCmdID() + "  "
                + errorEvent.getStatus().getStatusText());
    }

}
