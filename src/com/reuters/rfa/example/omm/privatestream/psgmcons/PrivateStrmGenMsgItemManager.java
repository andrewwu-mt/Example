package com.reuters.rfa.example.omm.privatestream.psgmcons;

import java.util.Iterator;
import java.util.LinkedList;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.omm.privatestream.common.PSGenericOMMParser;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMHandleItemCmd;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;

/**
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Encoding streaming request message for private messages using OMM message
 * <li>Encoding and sending Generic messages via the <code>OMMConsumer</code>
 * interface
 * <li>Register/subscribe one or multiple messages to RFA</li>
 * <li>Implement a Client which processes events from an
 * <code>OMMConsumer</code>
 * <li>Use {@link com.reuters.rfa.example.utility.GenericOMMParser
 * GenericOMMParser} to parse {@link com.reuters.rfa.omm.OMMMsg OMMMsg} response
 * messages.
 * <li>Unregistered items when the application is not interested anymore.
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder},
 * {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer} from
 * StarterConsumer_PrivateStreamGenericMsg
 * 
 * @see StarterConsumer_PrivateStreamGenericMsg
 * 
 */
public class PrivateStrmGenMsgItemManager implements Client
{
    private static final short GENERIC_DOMAIN_MODEL = RDMMsgTypes.MAX_RESERVED + 1;

    LinkedList<Handle> _itemHandles;
    StarterConsumer_PrivateStreamGenericMsg _mainApp;

    private String _className = "PrivateStrmGenMsgItemManager";
    private short _capability;
    private String _serviceName;
    private String _currentItemName = null;
    private int _genericMsgsSent = 0;
    private Handle _timerHandle = null;
    private Handle _errorHandle = null;
    private static final String _itemReqName = "PrivateStreamGenericMessageSyncComm.T";

    /**
     * @param mainApp a reference to the main application class
     */
    public PrivateStrmGenMsgItemManager(StarterConsumer_PrivateStreamGenericMsg mainApp)
    {
        _mainApp = mainApp;
        _itemHandles = new LinkedList<Handle>();
        _serviceName = CommandLine.variable("serviceName");
    }

    /**
     * Encodes private streaming request messages and register them to RFA
     */
    public void initiateRequests()
    {
        System.out.println(_className + ".initiateRequests: Sending item request...");
        _capability = GENERIC_DOMAIN_MODEL;
        sendRequest(_capability, _serviceName, _itemReqName);
    }

    private void sendRequest(short capability, String serviceName, String itemName)
    {
        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();

        // Preparing to send item request message
        OMMPool pool = _mainApp.getPool();
        OMMMsg ommMsg = pool.acquireMsg();

        ommMsg.setMsgType(OMMMsg.MsgType.REQUEST);
        ommMsg.setMsgModelType(capability);
        ommMsg.setPriority((byte)1, 1);
        ommMsg.setIndicationFlags(OMMMsg.Indication.PRIVATE_STREAM
                                  | OMMMsg.Indication.REFRESH);

        System.out.println("---------------------------------------------------------------");
        System.out.println(_className + ": Subscribing to " + itemName);
        PSGenericOMMParser.parse(ommMsg);

        ommMsg.setAttribInfo(serviceName, itemName, RDMInstrument.NameType.RIC);

        // Set the message into interest spec
        ommItemIntSpec.setMsg(ommMsg);
        Handle itemHandle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(),
                                                                     ommItemIntSpec, this, null);
        _itemHandles.add(itemHandle);

        pool.releaseMsg(ommMsg);
    }

    /**
     * Unregisters/un-subscribes all items, one at a time
     */
    public void closeRequest()
    {
        Iterator<Handle> iter = _itemHandles.iterator();
        Handle itemHandle = null;
        while (iter.hasNext())
        {
            itemHandle = iter.next();
            _mainApp.getOMMConsumer().unregisterClient(itemHandle);
        }
        _itemHandles.clear();

        unregisterTimer();
    }

    /**
     * Unregisters/un-subscribes items individually
     * 
     * @param itemHandle : Handle
     */
    public void closeRequest(Handle itemHandle)
    {
        System.out.println("Sending a CLOSE request: " + itemHandle + " " + _currentItemName);
        _mainApp.getOMMConsumer().unregisterClient(itemHandle);
        _itemHandles.remove(itemHandle);
        _genericMsgsSent = 0;
    }

    /**
     * Process incoming events based on the event type. Events of type
     * {@link com.reuters.rfa.common.Event#OMM_ITEM_EVENT OMM_ITEM_EVENT} are
     * parsed using {@link com.reuters.rfa.example.utility.GenericOMMParser
     * GenericOMMParser}
     */
    public void processEvent(Event event)
    {
        if (event.getType() == Event.COMPLETION_EVENT)
        {
            System.out.println(_className + ": Receive a COMPLETION_EVENT, " + event.getHandle());
            return;
        }

        if (event.getType() != Event.OMM_ITEM_EVENT)
        {
            System.out.println("ERROR: " + _className + " Received an unsupported Event type.");
            _mainApp.cleanup(-1);
            return;
        }

        if (event.getType() == Event.OMM_CMD_ERROR_EVENT)
        {
            processOMMCmdErrorEvent((OMMCmdErrorEvent)event);
            return;
        }

        System.out.println(_className + ".processEvent: Received Item Event...");
        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg respMsg = ie.getMsg();
        PSGenericOMMParser.parse(respMsg);
        handlePrivateStreamGenericMessageSyncComm(respMsg, event.getHandle());
    }

    private void processOMMCmdErrorEvent(OMMCmdErrorEvent event)
    {
        System.out.println("Received OMMCmd ERROR EVENT for id: " + event.getCmdID() + " "
                + event.getStatus().getStatusText());
    }

    private void handlePrivateStreamGenericMessageSyncComm(OMMMsg respMsg, Handle itemHandle)
    {
        if (respMsg.has(OMMMsg.HAS_ATTRIB_INFO)
                && respMsg.getAttribInfo().has(OMMAttribInfo.HAS_NAME))
        {
            if (respMsg.getAttribInfo().getName().compareTo(_itemReqName) == 0)
            {
                if (respMsg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
                {
                    checkPrivateStreamResp(respMsg);
                    sendGenericMsg(itemHandle);
                    System.out.println(_className
                            + ".handlePrivateStream: Sent Generic Message Sync");
                    sleepBeforeMsgExchange();
                }
            }
        }
        else if (_currentItemName.compareTo(_itemReqName) == 0) // for subsequent generic msgs
        {
            sendGenericMsg(itemHandle);
            sleepBeforeMsgExchange();
        }
    }

    private void sleepBeforeMsgExchange()
    {
        try
        {
            int messageInterval = CommandLine.intVariable("messageInterval");
            Thread.sleep(messageInterval * 1000);
        }
        catch (InterruptedException e)
        {
        }
    }

    private void sendGenericMsg(Handle itemHandle)
    {
        // register for OMMCmdErrorEvents.
        if (_errorHandle == null)
        {
            OMMErrorIntSpec errorIntSpec = new OMMErrorIntSpec();
            _errorHandle = _mainApp._ommConsumer.registerClient(_mainApp.getEventQueue(),
                                                                errorIntSpec, this, null);
        }

        System.out.println("SENDING CONSUMER TO PROVIDER GENERIC MESSAGE: " + _currentItemName
                + " " + itemHandle);
        OMMPool pool = _mainApp.getPool();
        OMMMsg msg = pool.acquireMsg();
        msg.setMsgModelType(GENERIC_DOMAIN_MODEL);
        msg.setMsgType(OMMMsg.MsgType.GENERIC);
        msg.setSeqNum(_genericMsgsSent + 1);
        msg.setSecondarySeqNum(_genericMsgsSent + 999);
        msg.setIndicationFlags(OMMMsg.Indication.GENERIC_COMPLETE);

        /* put payload in */
        OMMEncoder encoder = pool.acquireEncoder();
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
        encoder.encodeString("From Consumer to Provider", OMMTypes.ASCII_STRING);
        encoder.encodeAggregateComplete(); // ElementList

        OMMHandleItemCmd cmd = new OMMHandleItemCmd();
        cmd.setMsg((OMMMsg)encoder.getEncodedObject());
        cmd.setHandle(itemHandle);

        _mainApp.getOMMConsumer().submit(cmd, null);

        pool.releaseEncoder(encoder);
        System.out.println("SENT CONSUMER TO PROVIDER GENERIC MESSAGE: " + itemHandle);
        _genericMsgsSent++;

    }

    private void checkPrivateStreamResp(OMMMsg respMsg)
    {
        int msgType = respMsg.getMsgType();
        String msgTypeString = null;
        switch (msgType)
        {
            case OMMMsg.MsgType.REFRESH_RESP:
                msgTypeString = "REFRESH_RESP";
                break;
            case OMMMsg.MsgType.UPDATE_RESP:
                msgTypeString = "UPDATE_RESP";
                break;
            case OMMMsg.MsgType.STATUS_RESP:
                msgTypeString = "STATUS_RESP";
                break;
            case OMMMsg.MsgType.GENERIC:
                msgTypeString = "GENERIC";
                break;
        }

        if (!isPrivateStreamResp(respMsg))
        {
            System.out.println(_className + ".handlePrivateStream: " + msgTypeString + " for "
                    + respMsg.getAttribInfo().getName() + " NOT a private stream");
            return;
        }

        _currentItemName = respMsg.getAttribInfo().getName();
    }

    private boolean isPrivateStreamResp(OMMMsg respMsg)
    {
        if (respMsg.isSet(OMMMsg.Indication.PRIVATE_STREAM))
            return true;
        return false;
    }

    private void unregisterTimer()
    {
        if (_timerHandle != null)
        {
            _mainApp.getOMMConsumer().unregisterClient(_timerHandle);
            _timerHandle = null;
        }
    }
}
