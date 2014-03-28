package com.reuters.rfa.example.omm.privatestream.pscons;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.omm.privatestream.common.PSGenericOMMParser;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * <p>
 * The is a Client class that handle request and response for items in the
 * following Reuters Domain:
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_PRICE MARKET_PRICE},
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
 * StarterConsumer_PrivateStream
 * 
 * @see StarterConsumer_PrivateStream
 * 
 */
public class PrivateStrmItemManager implements Client
{
    LinkedList<Handle> _itemHandles;
    StarterConsumer_PrivateStream _mainApp;

    private String _className = "PrivateStrmItemManager";

    /**
     * @param mainApp a reference to the main application class
     */
    public PrivateStrmItemManager(StarterConsumer_PrivateStream mainApp)
    {
        _mainApp = mainApp;
        _itemHandles = new LinkedList<Handle>();
    }

    /**
     * Encodes private streaming request messages and register them to RFA
     */
    public void sendRequest()
    {
        System.out.println(_className + ".sendRequest: Sending item request(s) - PrivateStream");
        String serviceName = CommandLine.variable("serviceName");
        String itemNames = CommandLine.variable("itemName");
        short capability = 6;

        // Note: "," is a valid character for RIC name.
        // This application need to be modified if RIC names have ",".
        StringTokenizer st = new StringTokenizer(itemNames, ",");
        LinkedList<String> itemNamesList = new LinkedList<String>();
        while (st.hasMoreTokens())
            itemNamesList.add(st.nextToken().trim());

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();

        // Preparing to send item request message
        OMMPool pool = _mainApp.getPool();
        OMMMsg ommmsg = pool.acquireMsg();

        ommmsg.setMsgType(OMMMsg.MsgType.REQUEST);
        ommmsg.setMsgModelType(capability);
        ommmsg.setPriority((byte)1, 1);

        ommmsg.setIndicationFlags(OMMMsg.Indication.PRIVATE_STREAM
                                  | OMMMsg.Indication.REFRESH);

        Iterator<String> iter = itemNamesList.iterator();
        while (iter.hasNext())
        {
            String itemName = iter.next();
            System.out.println(_className + ": Subscribing to " + itemName);

            ommmsg.setAttribInfo(serviceName, itemName, RDMInstrument.NameType.RIC);

            // Set the message into interest spec
            ommItemIntSpec.setMsg(ommmsg);
            Handle itemHandle = _mainApp.getOMMConsumer()
                    .registerClient(_mainApp.getEventQueue(), ommItemIntSpec, this, null);
            _itemHandles.add(itemHandle);
        }
        pool.releaseMsg(ommmsg);
    }

    /**
     * Encodes single streaming request message and registers it to RFA. If
     * PrivateOrStandard is true, then a private stream request is done,
     * otherwise a standard/public stream request is done.
     */
    public void sendRequest(boolean PrivateOrStandard, OMMAttribInfo attribInfo)
    {
        if (PrivateOrStandard)
            System.out.println(_className + ".sendRequest: Sending single item request - PrivateStream");
        else
            System.out.println(_className + ".sendRequest: Sending single item request - StandardStream");
        short capability = 6;

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();

        // Preparing to send item request message
        OMMPool pool = _mainApp.getPool();
        OMMMsg ommmsg = pool.acquireMsg();

        ommmsg.setMsgType(OMMMsg.MsgType.REQUEST);
        ommmsg.setMsgModelType(capability);
        ommmsg.setPriority((byte)1, 1);

        if (PrivateOrStandard)
            ommmsg.setIndicationFlags(OMMMsg.Indication.PRIVATE_STREAM
                                      | OMMMsg.Indication.REFRESH);

        System.out.println(_className + ": Subscribing to " + attribInfo.getName());
        ommmsg.setAttribInfo(attribInfo);

        // Set the message into interest spec
        ommItemIntSpec.setMsg(ommmsg);
        Handle itemHandle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(),
                                                                     ommItemIntSpec, this, null);
        _itemHandles.add(itemHandle);

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
            itemHandle = iter.next();
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
        PSGenericOMMParser.parse(respMsg);

        if (respMsg.has(OMMMsg.HAS_STATE))
        {
            if (respMsg.getState().getStreamState() == OMMState.Stream.REDIRECT)
            {
                // Here, the provider has not allowed a standard/public stream
                // (e.g. for restricted item RES-DS.N).
                // OMMState.Stream.REDIRECT effectively means that this stream has closed
                // and that it needs to be re-requested with the private stream.
                // It was originally requested as standard, but is now private.
                // The consumer client will then need to re-request the same
                // info with the private stream.
                // The Redirect message will contain the info for the client to re-request.
                System.out.println("\nApplication received a private stream status message with Redirect stream state\n");
                if (!respMsg.has(OMMMsg.HAS_ATTRIB_INFO))
                {
                    System.out
                            .println("No stream is re-requested as private since no attrib info is present in this message");
                    return;
                }

                // re-request item as private stream
                sendRequest(true, respMsg.getAttribInfo());
            }
        }
    }

}
