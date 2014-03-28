package com.reuters.rfa.example.omm.itemgroups;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMItemGroup;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * 
 * <p>
 * The is a Client class that handle request and response for items in the
 * following Reuters Domain:
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_PRICE MARKET_PRICE},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_BY_ORDER MARKET_BY_ORDER},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_BY_PRICE MARKET_BY_PRICE},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_MAKER MARKET_MAKER},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#SYMBOL_LIST SYMBOL_LIST}, in a
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
 * <li>Use {@link ItemGroupManager} to manage item groups.
 * <li>Unregistered all items when the application is not interested anymore.
 * </ul>
 * 
 */
public class ItemManager implements Client
{
    ItemGroupsDemo _mainApp;
    ItemGroupManager _itemGroupManager;

    private String _className = "ItemManager";

    public ItemManager(ItemGroupsDemo mainApp, ItemGroupManager itemGroupManager)
    {
        _mainApp = mainApp;
        _itemGroupManager = itemGroupManager;
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

        if (CommandLine.booleanVariable("attribInfoInUpdates"))
            ommmsg.setIndicationFlags(OMMMsg.Indication.REFRESH
                                      | OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES);
        else
            ommmsg.setIndicationFlags(OMMMsg.Indication.REFRESH);

        while (iter.hasNext())
        {
            String itemName = (String)iter.next();
            System.out.println(_className + ": Subscribing to " + itemName);

            ommmsg.setAttribInfo(serviceName, itemName, RDMInstrument.NameType.RIC);

            // Set the message into interest spec
            ommItemIntSpec.setMsg(ommmsg);
            Handle itemHandle = _mainApp.getOMMConsumer()
                    .registerClient(_mainApp.getEventQueue(), ommItemIntSpec, this, null);

            _itemGroupManager.addItem(serviceName, itemName, itemHandle);
        }
        pool.releaseMsg(ommmsg);
    }

    /**
     * Unregisters/unsubscribes all items individually
     */
    public void closeRequest()
    {
        for (Iterator<Handle> iter = _itemGroupManager.getAllHandles(); iter.hasNext();)
        {
            Handle handle = iter.next();
            _itemGroupManager.removeItem(handle);
            _mainApp.getOMMConsumer().unregisterClient(handle);
        }
    }

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

        // Refresh will always provide group id
        // Status response can contain group id
        if ((respMsg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
                || (respMsg.getMsgType() == OMMMsg.MsgType.STATUS_RESP && respMsg
                        .has(OMMMsg.HAS_ITEM_GROUP)))
        {
            OMMItemGroup group = respMsg.getItemGroup();
            Handle itemHandle = event.getHandle();
            _itemGroupManager.applyGroup(itemHandle, group);
        }

        GenericOMMParser.parse(respMsg);
    }

}
