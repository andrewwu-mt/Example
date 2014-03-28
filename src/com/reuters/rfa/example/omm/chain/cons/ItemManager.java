package com.reuters.rfa.example.omm.chain.cons;

import java.util.Iterator;
import java.util.LinkedList;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMFieldEntry;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMIterable;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * <p>
 * The is a Client class that handles request and response for items in the
 * following Reuters Domain:
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_PRICE MARKET_PRICE},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_BY_ORDER MARKET_BY_ORDER},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_BY_PRICE MARKET_BY_PRICE},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_MAKER MARKET_MAKER},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#SYMBOL_LIST SYMBOL_LIST},
 * in generic way. User can specify message model type by
 * passing on the command line parameter, mmt.
 * </p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Encoding streaming request message for the specified model using OMM
 * message
 * <li>Register/subscribe one or multiple messages to RFA</li>
 * <li>Implement a Client which processes events from an
 * <code>OMMConsumer</code>
 * <li>Use {@link GenericOMMParserI} to parse
 * {@link com.reuters.rfa.omm.OMMMsg OMMMsg} response messages.
 * <li>Unregistered all items when the application is not interested anymore.
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder},
 * {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer} from
 * StarterConsumer_Chain
 * 
 * @see StarterConsumer_Chain
 * 
 */
public class ItemManager implements Client
{
    LinkedList<Handle> _itemHandles;
    public ChainConsFrame _sFrame;
    String serviceName;

    private String _className = "ItemManager";

    public ItemManager(ChainConsFrame sFrame)
    {
        _sFrame = sFrame;
        _itemHandles = new LinkedList<Handle>();;
    }

    /**
     * Encodes streaming request messages and register them to RFA
     */
    public void sendRequest(String _itemNames, String _serviceName)
    {
        String itemName;

        itemName = _itemNames;
        serviceName = _serviceName;

        System.out.println(_className + ".sendRequest: Sending item request...");
        // Message Model from ChainConsFrame class (selected by user)
        String mmt = _sFrame.mmt;

        // set message model type [MARKET_PRICE, SYMBOL_LIST]
        short capability = RDMMsgTypes.msgModelType(mmt);

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();

        // Preparing to send item request message
        OMMPool pool = _sFrame.getPool();
        OMMMsg ommmsg = pool.acquireMsg();

        ommmsg.setMsgType(OMMMsg.MsgType.REQUEST);
        ommmsg.setMsgModelType(capability);
        ommmsg.setPriority((byte)1, 1);
        
        if (CommandLine.booleanVariable("attribInfoInUpdates"))
            ommmsg.setIndicationFlags(OMMMsg.Indication.REFRESH
                                      | OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES);
        else
            ommmsg.setIndicationFlags(OMMMsg.Indication.REFRESH);

        System.out.println(_className + ": Subscribing to " + itemName);
        ommmsg.setAttribInfo(serviceName, itemName, RDMInstrument.NameType.RIC);

        // Set the message into interest spec
        ommItemIntSpec.setMsg(ommmsg);

        Handle itemHandle = _sFrame.getOMMConsumer().registerClient(_sFrame.getEventQueue(),
                                                                    ommItemIntSpec, this, null);
        _itemHandles.add(itemHandle);

        pool.releaseMsg(ommmsg);
    }

    /**
     * Unregisters/unsubscribes individual item
     */
    public void closeRequest(Handle handle)
    {
        Handle iHandle = handle;

        Iterator<Handle> iter = _itemHandles.iterator();
        Handle itemHandle = null;
        while (iter.hasNext())
        {
            itemHandle = (Handle)iter.next();
            if (itemHandle == iHandle)
            {
                _sFrame.getOMMConsumer().unregisterClient(itemHandle);
            }
        }
        _itemHandles.clear();
    }

    /**
     * Unregisters/unsubscribes all items
     */
    public void closeAllRequest()
    {
        Iterator<Handle> iter = _itemHandles.iterator();
        Handle itemHandle = null;
        while (iter.hasNext())
        {
            itemHandle = (Handle)iter.next();
            _sFrame.getOMMConsumer().unregisterClient(itemHandle);
        }
        _itemHandles.clear();
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
            _sFrame.cleanup(-1);
            return;
        }

        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg respMsg = ie.getMsg();

        if (respMsg.getMsgType() == OMMMsg.MsgType.STATUS_RESP)
        {
            // If receive STATUS_RESP, display the message and close the request
            GenericOMMParserI.parse(respMsg, this);
            Handle _handle = ie.getHandle();
            this.closeRequest(_handle);
        }
        else
        {
            // Check if this is from "Chain Subscription" frame
            if (_sFrame._chainFrame)
            {

                if (_sFrame.mmt.equals("MARKET_PRICE"))
                {
                    // Process chain record in MARKET_PRICE model
                    doChainFrame(ie);
                }
                else if (_sFrame.mmt.equals("SYMBOL_LIST"))
                {
                    // Process chain record in SYMBOL_LIST model
                    pSymbolList(ie);
                }
                else
                {
                    _sFrame._output.append("TYPE NOT SUPPORT" + "\n");
                }

            }
            else
            {
                // Process MARKET_PRICE item, parse OMM data
                OMMItemParse(ie);
            }
        }
    }

    /**
     * Process chain record in SYMBOL_LIST model; parse, format and print out.
     */
    public void pSymbolList(OMMItemEvent ie)
    {
        Event event = ie;
        short fid2 = 0;

        OMMMsg msg = ((OMMItemEvent)event).getMsg();
        OMMData data = msg.getPayload();

        // Iterate at the OMMMapEntry level
        for (Iterator<?> iter = ((OMMIterable)data).iterator(); iter.hasNext();)
        {
            OMMEntry entry = (OMMEntry)iter.next();
            OMMMapEntry mentry = (OMMMapEntry)entry;

            if ((mentry.getAction() != OMMMapEntry.Action.DELETE)
                    && mentry.getDataType() != OMMTypes.NO_DATA)
            {

                data = mentry.getData();

                // Get Field List data from OMMMapEntry
                OMMFieldList fieldList = (OMMFieldList)data;

                int n = 1;
                // Iterate at the FieldEntry level
                for (Iterator<?> iter2 = ((OMMIterable)fieldList).iterator(); iter2.hasNext();)
                {
                    OMMEntry entry2 = (OMMEntry)iter2.next();
                    OMMFieldEntry eentry2 = (OMMFieldEntry)entry2;

                    fid2 = eentry2.getFieldId();

                    // Arrange and display the SYMBOLs
                    if (fid2 == 3422)
                    {
                        _sFrame._output.append(eentry2.getData().toString() + " ");
                        if ((n % 7) == 0)
                        {
                            _sFrame._output.append("\n");
                        }
                        else
                        {
                            _sFrame._output.append("\t");
                        }
                        n++;
                    }
                }
            }
        }
    }

    /**
     * Process chain record in MARKET_PRICE model.
     */
    public void doChainFrame(OMMItemEvent ie)
    {
        Event event = ie;

        Handle _handle = event.getHandle();

        OMMMsg msg = ((OMMItemEvent)event).getMsg();
        OMMData data = msg.getPayload();
        int fid = 0;
        boolean isLINK_A = false, isLONGLINK = false;
        String NEXTLR = null, LONGNEXTLR = null;

        // Iterate the FieldEntry to find out the chain template and store the
        // value of NEXTLR, LONGNEXTLR
        for (Iterator<?> iter = ((OMMIterable)data).iterator(); iter.hasNext();)
        {
            OMMEntry entry = (OMMEntry)iter.next();
            OMMFieldEntry eentry = (OMMFieldEntry)entry;

            fid = eentry.getFieldId();

            if (fid == 813)
            {
                // if there is FID 813, this chain record has LONGLINK template.
                isLONGLINK = true;
            }
            else if (fid == 253)
            {
                // if there is FID 253, this chain record has LINK_A template.
                isLINK_A = true;
            }
            else if (fid == 238)
            {
                // store the value of next link for retrieving the subsequence
                // of the chain in LINK_A template.
                NEXTLR = eentry.getData().toString();
            }
            else if (fid == 815)
            {
                // store the value of next link for retrieving the subsequence
                // of the chain in LONGLINK template.
                LONGNEXTLR = eentry.getData().toString();
            }
        }

        if (isLONGLINK && !(LONGNEXTLR.isEmpty()))
        {
            // if this is LONKLINK template, retrieve its subsequence using
            // LONGNEXTLR.
            this.sendRequest(LONGNEXTLR, serviceName);
        }

        if (isLINK_A && !(NEXTLR.isEmpty()))
        {
            // if this is LINK_A template, retrieve its subsequence using
            // NEXTLR.
            this.sendRequest(NEXTLR, serviceName);
        }

        if (!(isLINK_A || isLONGLINK))
        {
            // if there is no NEXTLR or LONGNEXTLR, close the request since this
            // is not a chain record.
            _sFrame._output.append("\n" + "********** This is NOT Chain Record **********" + "\n");
            this.closeRequest(_handle);
        }
        else
        {
            // parse OMM data of chain record
            OMMparse(data);
        }
    }

    /**
     * Parse MARKET_PRICE item.
     */
    public void OMMItemParse(OMMItemEvent ie)
    {
        OMMItemEvent event = ie;

        Handle _handle = event.getHandle();

        OMMMsg respMsg = event.getMsg();
        OMMData data = respMsg.getPayload();
        int fid = 0;
        boolean isLINK_A = false, isLONGLINK = false;

        // Iterate the FieldEntry
        for (Iterator<?> iter = ((OMMIterable)data).iterator(); iter.hasNext();)
        {
            OMMEntry entry = (OMMEntry)iter.next();
            OMMFieldEntry eentry = (OMMFieldEntry)entry;

            fid = eentry.getFieldId();

            if (fid == 813)
            {
                isLONGLINK = true;
            }
            else if (fid == 253)
            {
                isLINK_A = true;
            }
        }

        if (!(isLINK_A || isLONGLINK))
        {
            GenericOMMParserI.parse(respMsg, this);
        }
        else
        {
            // if this is chain record, close the request
            _sFrame._output.append("\n" + "********** This is Chain Record **********" + "\n");
            this.closeRequest(_handle);
        }
    }

    /**
     * Parse chain record in MARKET_PRICE model
     */
    public void OMMparse(OMMData data)
    {

        OMMData _data = data;
        int fid = 0;
        int n = 1;

        // Iterate the FieldEntry
        for (Iterator<?> iter = ((OMMIterable)_data).iterator(); iter.hasNext();)
        {

            OMMEntry entry = (OMMEntry)iter.next();
            OMMFieldEntry eentry = (OMMFieldEntry)entry;

            fid = eentry.getFieldId();

            if (isLink_Fid(fid) || isLong_Fid(fid))
            {
                // parse the field entry, format the layout and print
                _sFrame._output.append(eentry.getData().toString() + " ");
                if ((n % 7) == 0)
                {
                    _sFrame._output.append("\n");

                }
                else
                {
                    _sFrame._output.append("\t");
                }
                n++;
            }
        }
    }

    /**
     * Check if the FID is in LINK_A template
     */
    public boolean isLink_Fid(int fid)
    {

        int _fid = fid;

        if (_fid == 240 || _fid == 241 || _fid == 242 || _fid == 243 || _fid == 244 || _fid == 245
                || _fid == 246 || _fid == 247 || _fid == 248 || _fid == 249 || _fid == 250
                || _fid == 251 || _fid == 252 || _fid == 253)
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Check if the FID is in LONGLINK template
     */
    public boolean isLong_Fid(int fid)
    {

        int _fid = fid;

        if (_fid == 800 || _fid == 801 || _fid == 802 || _fid == 803 || _fid == 804 || _fid == 805
                || _fid == 806 || _fid == 807 || _fid == 808 || _fid == 809 || _fid == 810
                || _fid == 811 || _fid == 812 || _fid == 813)
        {
            return true;
        }
        else
        {
            return false;
        }
    }
}
