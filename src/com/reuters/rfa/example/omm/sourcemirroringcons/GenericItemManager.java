package com.reuters.rfa.example.omm.sourcemirroringcons;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;
import com.reuters.rfa.session.omm.OMMHandleItemCmd;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * <p>
 * This is a Client class that handles request, response and generic messages for
 * an item 'custom' domain model ('128')
 * </p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Encoding streaming request message for custom model using OMM message
 * <li>Register/subscribe one item through RFA</li>
 * <li>Implement a Client which processes events from an <code>OMMConsumer</cod>
 *  <li>Using the client to send Consumer Status Mode message
 *  <li>Initialize and continue a synchronous bidirectional conversation using
 *  	generic messages with the example application StarterProvider_SourceMirroring.
 *  <li>Use {@link com.reuters.rfa.example.utility.GenericOMMParser GenericOMMParser}
 *      to parse {@link com.reuters.rfa.omm.OMMMsg OMMMsg} response messages and
 *      generic messages.
 *  <li>Unregister the item when the application is not interested anymore.
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder},
 * {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer} from
 * StarterConsumer_SourceMirroring
 * 
 * @see StarterConsumer_SourceMirroring
 * 
 */
public class GenericItemManager implements Client
{
    private static final short GENERIC_DOMAIN_MODEL = RDMMsgTypes.MAX_RESERVED + 1;
    private Handle _itemHandle = null;
    StarterConsumer_SourceMirroring _mainApp;

    private String _className = "GenericItemManager";
    private int _genericMsgsSent = 0;
    private final String _itemName = "TALK";

    public GenericItemManager(StarterConsumer_SourceMirroring mainApp)
    {
        _mainApp = mainApp;
    }

    /**
     * Encodes streaming request messages and register them to RFA
     */
    public void sendRequest()
    {
        System.out.println(_className + ".sendRequest: Sending item request...");

        short capability = GENERIC_DOMAIN_MODEL;

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();

        // Preparing to send item request message
        OMMPool pool = _mainApp.getPool();
        OMMMsg ommmsg = pool.acquireMsg();

        ommmsg.setMsgType(OMMMsg.MsgType.REQUEST);
        ommmsg.setMsgModelType(capability);
        ommmsg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        ommmsg.setPriority((byte)1, 1);

        System.out.println(_className + ": Subscribing to " + _itemName);

        ommmsg.setAttribInfo(_mainApp._serviceName, _itemName, RDMInstrument.NameType.RIC);

        // Set the message into interest spec
        ommItemIntSpec.setMsg(ommmsg);
        _itemHandle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(),
                                                               ommItemIntSpec, this, null);

        pool.releaseMsg(ommmsg);
    }

    /**
     * Unregisters/unsubscribes item
     */
    public void closeRequest()
    {
        _mainApp.getOMMConsumer().unregisterClient(_itemHandle);
    }

    /**
     * Process incoming events based on the event type. Events of type
     * {@link com.reuters.rfa.common.Event#OMM_ITEM_EVENT OMM_ITEM_EVENT} are
     * parsed using {@link com.reuters.rfa.example.utility.GenericOMMParser
     * GenericOMMParser}
     */
    public void processEvent(Event event)
    {
        if (event.getType() == Event.OMM_CMD_ERROR_EVENT)
        {
            System.out.println("Received OMMCmd ERROR EVENT");
            System.out.println(((OMMCmdErrorEvent)event).getStatus().getStatusText());
            return;
        }

        if (event.getType() == Event.COMPLETION_EVENT)
        {
            System.out.println(_className + ": Receive a COMPLETION_EVENT, " + event.getHandle());
            return;
        }

        if (event.getType() == Event.TIMER_EVENT)
        {
            System.out.println("Generic Item Manager Consumer Timer event fired.");
            return;
        }

        System.out.println();
        System.out.println(_className + ".processEvent: Received Item Event...");
        if (event.getType() != Event.OMM_ITEM_EVENT)
        {
            System.out.println("ERROR: " + _className + " Received an unsupported Event type.");
            _mainApp.cleanup(-1);
            return;
        }

        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg respMsg = ie.getMsg();
        GenericOMMParser.parse(respMsg);

        processItemEvent(respMsg, event.getHandle());
    }

    private void processItemEvent(OMMMsg respMsg, Handle itemHandle)
    {
        // TODO we assume that we have received a valid, open refresh complete
        if (respMsg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP
                || respMsg.getMsgType() == OMMMsg.MsgType.GENERIC)
        {
            sendGenericMsg(itemHandle);
        }
    }

    private void sendGenericMsg(Handle itemHandle)
    {
        System.out.println("SENDING GENERIC MSG FROM CONSUMER TO PROVIDER:  " + _itemName + " "
                + itemHandle);
        OMMPool pool = _mainApp.getPool();
        OMMMsg msg = pool.acquireMsg();
        msg.setMsgModelType(GENERIC_DOMAIN_MODEL); // 18 is a custom OMM domain
        msg.setMsgType(OMMMsg.MsgType.GENERIC);
        msg.setSeqNum(_genericMsgsSent + 1);
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

        try
        {
            _mainApp.getOMMConsumer().submit(cmd, null);
        }
        catch (IllegalArgumentException e)
        {
            System.out.println(e);
        }

        pool.releaseEncoder(encoder);
        System.out.println("SENT CONSUMER TO PROVIDER GENERIC MESSAGE: " + itemHandle);
        _genericMsgsSent++;

    }
}
