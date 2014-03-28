package com.reuters.rfa.example.omm.chain.cons;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * <p>
 * The is a Client class that handles request and response for Login.
 * </p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Encoding streaming request message for Login using OMM message
 * <li>Register for Login to RFA</li>
 * <li>Implement a Client which processes events from a <code>OMMConsumer</code>
 * and handle Login responses</li>
 * <li>Use {@link GenericOMMParserI} to parse
 * {@link com.reuters.rfa.omm.OMMMsg OMMMsg} response messages.
 * <li>Unregistered login, logout from server.
 * </ul>
 * 
 */

public class LoginClient implements Client
{
    Handle _loginHandle;
    ChainConsFrame _sFrame;

    private String _className = "LoginClient";

    public LoginClient(ChainConsFrame sFrame)
    {
        _sFrame = sFrame;
        _loginHandle = null;
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
        _loginHandle = _sFrame.getOMMConsumer().registerClient(_sFrame.getEventQueue(),
                                                               ommItemIntSpec, this, null);
    }

    private OMMMsg encodeLoginReqMsg()
    {
        OMMEncoder encoder = _sFrame.getEncoder();
        OMMPool pool = _sFrame.getPool();
        encoder.initialize(OMMTypes.MSG, 500);
        OMMMsg msg = pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.LOGIN);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        msg.setAttribInfo(null, CommandLine.variable("user"), RDMUser.NameType.USER_NAME);

        encoder.encodeMsgInit(msg, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);
        encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        encoder.encodeElementEntryInit(RDMUser.Attrib.ApplicationId, OMMTypes.ASCII_STRING);
        encoder.encodeString(CommandLine.variable("application"), OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit(RDMUser.Attrib.Position, OMMTypes.ASCII_STRING);
        encoder.encodeString(CommandLine.variable("position"), OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit(RDMUser.Attrib.Role, OMMTypes.UINT);
        encoder.encodeUInt((long)RDMUser.Role.CONSUMER);
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
            _sFrame.getOMMConsumer().unregisterClient(_loginHandle);
            _loginHandle = null;
        }
    }

    public void processEvent(Event event)
    {
        System.out.println("login client's processEvent");

        if (event.getType() == Event.COMPLETION_EVENT)
        {
            System.out.println(_className + ": Receive a COMPLETION_EVENT, " + event.getHandle());
            return;
        }

        System.out.println(_className + ".processEvent: Received Login Response... ");

        if (event.getType() != Event.OMM_ITEM_EVENT)
        {
            System.out.println("ERROR: " + _className + " Received an unsupported Event type.");
            _sFrame.cleanup(-1);
            return;
        }

        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg respMsg = ie.getMsg();
        if (respMsg.getMsgModelType() != RDMMsgTypes.LOGIN)
        {
            System.out.println("ERROR: " + _className + " Received a non-LOGIN model type.");
            _sFrame.cleanup(-1);
            return;
        }

        if (respMsg.isFinal())
        {
            System.out.println(_className + ": Login Response message is final.");
            GenericOMMParserI.parse(respMsg, _sFrame._itemManager);
            // Send the login result back
            _sFrame.processLogin(false);
            return;
        }

        if ((respMsg.getMsgType() == OMMMsg.MsgType.STATUS_RESP) && (respMsg.has(OMMMsg.HAS_STATE))
                && (respMsg.getState().getStreamState() == OMMState.Stream.OPEN)
                && (respMsg.getState().getDataState() == OMMState.Data.OK))
        {
            System.out.println(_className + ": Received Login Response - REFRESH_SOLICITED");
            GenericOMMParserI.parse(respMsg, _sFrame._itemManager);
            // Send the login result back
            _sFrame.processLogin(true);
        }
        else
        {
            System.out.println(_className + ": Received Login Response - "
                    + OMMMsg.MsgType.toString(respMsg.getMsgType()));
            GenericOMMParserI.parse(respMsg, _sFrame._itemManager);
        }
    }

}
