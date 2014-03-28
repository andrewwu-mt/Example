package com.reuters.rfa.example.omm.cons;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * <p>
 * The is a Client class that handles request and response for items in the
 * following Reuters Domains:
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_PRICE MARKET_PRICE},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_BY_ORDER MARKET_BY_ORDER},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_BY_PRICE MARKET_BY_PRICE},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_MAKER MARKET_MAKER},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#SYMBOL_LIST SYMBOL_LIST}, in
 * generic way. User can specify message model type by passing on the command
 * line parameter, mmt.
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
 * <li>Unregistered all items when the application is not interested anymore.
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder},
 * {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer} from
 * StarterConsumer
 * 
 * @see StarterConsumer
 * 
 */
public class ItemManager implements Client
{
    LinkedList<Handle> _itemHandles;
    StarterConsumer _mainApp;

    private String _className = "ItemManager";

    public ItemManager(StarterConsumer mainApp)
    {
        _mainApp = mainApp;
        _itemHandles = new LinkedList<Handle>();
    }

    /**
     * Encodes streaming request messages and register them to RFA
     */
    public void sendRequest()
    {
        System.out.println(_className + ".sendRequest: Sending item request...");
        String serviceName = CommandLine.variable("serviceName");
        String itemNames = CommandLine.variable("itemName");
        String mmt = CommandLine.variable("mmt");
        short capability = RDMMsgTypes.msgModelType(mmt);

        // Note: "," is a valid character for RIC name.
        // This application need to be modified if RIC names have ",".
        StringTokenizer st = new StringTokenizer(itemNames, ",");
        LinkedList<String> itemNamesList = new LinkedList<String>();
        while (st.hasMoreTokens())
            itemNamesList.add(st.nextToken().trim());

        Iterator<String> iter = itemNamesList.iterator();

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();

        // Preparing to send item request message
        OMMPool pool = _mainApp.getPool();
        OMMMsg ommmsg = pool.acquireMsg();

        ommmsg.setMsgType(OMMMsg.MsgType.REQUEST);
        ommmsg.setMsgModelType(capability);
        ommmsg.setPriority((byte)1, 1);

        // Setting OMMMsg with negotiated version info from login handle

        if (_mainApp.getLoginHandle() != null)
        {
            ommmsg.setAssociatedMetaInfo(_mainApp.getLoginHandle());
        }

        if (CommandLine.booleanVariable("attribInfoInUpdates"))
            ommmsg.setIndicationFlags(OMMMsg.Indication.REFRESH | OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES);
        else
            ommmsg.setIndicationFlags(OMMMsg.Indication.REFRESH);

        while (iter.hasNext())
        {
            String itemName = (String)iter.next();
            System.out.println(_className + ": Subscribing to " + itemName);

            ommmsg.setAttribInfo(serviceName, itemName, RDMInstrument.NameType.RIC);

            // Set the message into interest spec
            ommItemIntSpec.setMsg(ommmsg);
            Handle itemHandle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(),
                                                                         ommItemIntSpec, this, null);
            _itemHandles.add(itemHandle);
        }
        pool.releaseMsg(ommmsg);
    }

    /**
     * Unregisters/unsubscribes all items individually
     */
    public void closeRequest()
    {
        Iterator<Handle> iter = _itemHandles.iterator();
        Handle itemHandle = null;
        while (iter.hasNext())
        {
            itemHandle = (Handle)iter.next();
            _mainApp.getOMMConsumer().unregisterClient(itemHandle);
        }
        _itemHandles.clear();
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
    }

}
