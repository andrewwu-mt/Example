package com.reuters.rfa.example.omm.postingConsumer;

import java.util.Iterator;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
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
 * <li>Use {@link com.reuters.rfa.example.utility.GenericOMMParser
 * GenericOMMParser} to parse {@link com.reuters.rfa.omm.OMMMsg OMMMsg} response
 * messages.
 * <li>Unregistered login, logout from server.
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

public class PostLoginClient implements Client
{
    Handle _loginHandle;
    StarterConsumer_Post _mainApp;

    private String _className = "PostingLoginClient: ";
    private String _eventTag = "PostingLoginClient::processEvent";

    String INFO_APPNAME;
    String APPNAME;

    public PostLoginClient(StarterConsumer_Post mainApp)
    {
        _mainApp = mainApp;
        _loginHandle = null;

        INFO_APPNAME = _mainApp.INFO_APPNAME;
        APPNAME = _mainApp.APPNAME;
    }

    public Handle getHandle()
    {
        return _loginHandle;
    }

    /**
     * Create and send login request
     */
    public void sendRequest()
    {
        OMMMsg ommmsg = encodeLoginReqMsg();

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
        ommItemIntSpec.setMsg(ommmsg);
        System.out.println("--> " + _className + "Sending login request...");

        _loginHandle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(),
                                                                ommItemIntSpec, this, null);

    }

    /**
     * Encodes streaming request messages for Login
     */
    private OMMMsg encodeLoginReqMsg()
    {
        OMMEncoder encoder = _mainApp.getEncoder();
        OMMPool pool = _mainApp.getPool();
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
        encoder.encodeUInt(RDMUser.Role.CONSUMER);
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
            _mainApp.getOMMConsumer().unregisterClient(_loginHandle);
            _loginHandle = null;
        }
    }

    /**
     * Process events delivered to RFA
     */
    public void processEvent(Event event)
    {
        System.out.println("\n*******" + _eventTag + " *Start* ************");
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
                processLoginResponseMessage(event);
                break;

            default:
                System.out.println("Error! " + _className + " Received an unsupported Event type.");
                _mainApp.cleanup(-1);
                break;
        }
        System.out.println("\n*******" + _eventTag + " *End* **************");
    }

    /**
     * Process Login Response Message
     */
    void processLoginResponseMessage(Event event)
    {
        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg ommMsg = ie.getMsg();
        short ommMsgType = ommMsg.getMsgType();
        String ommMsgTypeStr = OMMMsg.MsgType.toString((byte)ommMsgType);
        System.out.println("<-- " + _className + "Received " + event.toString() + " "
                + ommMsgTypeStr);

        GenericOMMParser.parse(ommMsg);

        byte messageType = ommMsg.getMsgType();

        if (messageType == OMMMsg.MsgType.ACK_RESP)
        {
            _mainApp.processAckResponse(_className, ommMsg);
            return;
        }

        if (messageType == OMMMsg.MsgType.STATUS_RESP)
        {
            _mainApp.processStatusResponse(_className, ommMsg);
        }

        if (ommMsg.isFinal())
        {
            System.out.println("* Login Response message is final.");
            _mainApp.processLogin(false);
            return;
        }

        /* login granted */
        if ((ommMsg.getMsgType() == OMMMsg.MsgType.STATUS_RESP) && (ommMsg.has(OMMMsg.HAS_STATE))
                && (ommMsg.getState().getStreamState() == OMMState.Stream.OPEN)
                && (ommMsg.getState().getDataState() == OMMState.Data.OK))
        {
            boolean bPostingSupported = isPostingSupported(ommMsg);
            if (bPostingSupported == true)
            {
                System.out.println(INFO_APPNAME + " OMMPosting feature is available");
            }
            else
            {
                System.out.println(INFO_APPNAME + " OMMPosting feature is not available");
            }

            _mainApp.processLogin(true);
        }
        else
        {
            System.out.println("* Received Login Response - "
                    + OMMMsg.MsgType.toString(ommMsg.getMsgType()));
        }

        return;
    }

    /**
     * Check if posting is supported
     */
    boolean isPostingSupported(OMMMsg ommMsg)
    {
        if (!ommMsg.has(OMMMsg.HAS_ATTRIB_INFO))
            return false;

        OMMAttribInfo ai = ommMsg.getAttribInfo();

        if (!ai.has(OMMAttribInfo.HAS_ATTRIB))
            return false;

        OMMElementList elementList = (OMMElementList)ai.getAttrib();
        OMMData data = null;
        long supportOMMPost = 0;
        for (Iterator<?> iter = elementList.iterator(); iter.hasNext();)
        {
            OMMElementEntry ee = (OMMElementEntry)iter.next();
            if (ee.getName().equals(RDMUser.Attrib.SupportOMMPost))
            {
                data = ee.getData();

                if (data instanceof OMMNumeric)
                {
                    supportOMMPost = ((OMMNumeric)data).toLong();
                }
            }
        }

        if (supportOMMPost == 1)
            return true;
        else
            return false;
    }
}
