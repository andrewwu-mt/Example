package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import java.util.Iterator;
import java.util.prefs.Preferences;

import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMEnum;
import com.reuters.rfa.omm.OMMFieldEntry;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMIterable;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.dictionary.FidDef;

public class MBOClient extends OMMDataClient
{
    private Handle mboHandle = null;

    private boolean m_bPopulateSummaryData = true;

    private boolean m_bSubscribedToDictionary = false;

    public MBOClient(EventQueue eventQueue, OMMConsumer subscriber, short dictLocation,
            Preferences prefs)
    {
        super(eventQueue, subscriber, dictLocation, prefs);

        m_bPopulateSummaryData = true;
    }

    // Implement Subscribe
    public boolean subscribe(String svcName, String itemName)
    {
        // clear out orders from any previous subscriptions

        myCallback.processClear();
        if ((this.dictDownloadComplete() == true) && (this._loggedIn == true))
        {
            encoder.initialize(OMMTypes.MSG, 5000);
            OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();

            // Preparing to send item request message
            OMMMsg ommmsg = pool.acquireMsg();

            ommmsg.setMsgType(OMMMsg.MsgType.REQUEST);
            ommmsg.setMsgModelType(RDMMsgTypes.MARKET_BY_ORDER);
            ommmsg.setIndicationFlags(OMMMsg.Indication.REFRESH);

            ommmsg.setAttribInfo(svcName, itemName, RDMInstrument.NameType.RIC);

            // Set the message into interest spec
            ommItemIntSpec.setMsg(ommmsg);
            mboHandle = _ommConsumer.registerClient(_eventQueue, ommItemIntSpec, this, null);

            pool.releaseMsg(ommmsg);
            String str = "Sent MarketByOrder request for: " + svcName + " : " + itemName;
            myCallback.notifyStatus(str);

            m_bPopulateSummaryData = true;
 
            return true;
        }
        else
        {
            if ((_dictLocation == 2) && !m_bSubscribedToDictionary)
            {
                sendRequest(RDMMsgTypes.DICTIONARY, svcName);
                m_bSubscribedToDictionary = true;
            }
        }

        return false;
    }

    // Implement Unsubscribe
    public void unsubscribe()
    {
        if (mboHandle != null)
        {
            _ommConsumer.unregisterClient(mboHandle);
            mboHandle = null;
            myCallback.notifyStatus("Unsubscribe complete");
        }
    }

    public void processMarketByOrder(Event event)
    {
        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg respMsg = ie.getMsg();

        if (respMsg.has(OMMMsg.HAS_ATTRIB_INFO))
        {
            // log AttribInfo
        }

        if (respMsg.has(OMMMsg.HAS_STATE))
        {
            // Log Status Info
        }

        if (respMsg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
        {
            if (respMsg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
            {
                myCallback.notifyStatus("Refresh Complete.");
            }

        }

        // Decode the data
        if (respMsg.getDataType() != OMMTypes.NO_DATA)
        {
            // Get PayLoad
            OMMData data = respMsg.getPayload();
            if (data.getType() == OMMTypes.MAP)
            {
                // check if the current refresh has summary data
                OMMMap map = (OMMMap)data;
                if (map.has(OMMMap.HAS_SUMMARY_DATA))
                {
                    // check if we need to populate summary data
                    if (m_bPopulateSummaryData)
                    {
                        extractSummaryData(map.getSummaryData());
                        m_bPopulateSummaryData = false;
                    }
                }

                // Extract Payload

                for (Iterator iter = ((OMMIterable)map).iterator(); iter.hasNext();)
                {
                    OMMMapEntry entry = (OMMMapEntry)iter.next();
                    switch (entry.getAction())
                    {
                        case OMMMapEntry.Action.UPDATE:
                            updateOrder(entry);
                            break;

                        case OMMMapEntry.Action.ADD:
                            addOrder(entry);
                            break;

                        case OMMMapEntry.Action.DELETE:
                            deleteOrder(entry);
                            break;

                        default:
                            continue;
                    }
                }

            }
            else
            {
                myCallback.notifyStatus("Invalid data type; expecting Map!!!");
                // *_log<<"Un-able to process data: Invalid data type; expecting
                // Map!!!"<<endl;
            }

        }

    }

    /*
     * Process ADD Orders
     */

    void addOrder(OMMMapEntry entry)
    {
        OMMData data = entry.getData();
        if (data.getType() != OMMTypes.FIELD_LIST)
        {
            myCallback.notifyStatus("AddOrder: Invalid data type; expecting FieldList!!!");
            return;
        }

        OMMData keyData = entry.getKey();
        if (keyData.getType() != OMMTypes.BUFFER)
        {
            myCallback.notifyStatus("AddOrder: Invalid data type; expecting DataBuffer!!!");
            return;
        }

        String orderId = keyData.toString();

        // *_log<<"Adding new order : "<<orderId<<endl<<endl;

        String orderSize = null;
        String orderPrice = null;
        String quoteTime = null;
        String orderSide = null;

        OMMFieldList fieldList = (OMMFieldList)data;
        for (Iterator iter = ((OMMIterable)fieldList).iterator(); iter.hasNext();)
        {
            OMMFieldEntry fldEntry = (OMMFieldEntry)iter.next();
            FidDef fiddef = _dataDict.getFidDef(fldEntry.getFieldId());

            // if the field is not defined in the fid db, not much we can do;
            if (fiddef == null)
                continue;

            OMMData dataBuf = (OMMData)fldEntry.getData(fiddef.getOMMType());
            switch (fldEntry.getFieldId())
            {
                case ORDER_SIZE:
                    orderSize = dataBuf.toString();
                    break;

                case ORDER_PRC:
                    orderPrice = dataBuf.toString();
                    ;
                    break;

                case ORDER_SIDE:
                {
                    if (dataBuf.getType() == OMMTypes.ENUM)
                        orderSide = _dataDict.expandedValueFor(ORDER_SIDE,
                                                               ((OMMEnum)dataBuf).getValue());
                    else
                        orderSide = dataBuf.toString();
                }
                    break;

                case QUOTIM_MS:

                    quoteTime = dataBuf.toString();
                    break;

                default:
                    break;
            }
        }

        // System.out.println("AddOrder... Side: " + orderSide + "OrderId: " +
        // orderId);
        myCallback.processInsertOrder(orderId, orderSize, orderPrice, quoteTime, orderSide);

    }

    /*
     * Process UPDATE Orders
     */

    void updateOrder(OMMMapEntry entry)
    {
        OMMData data = entry.getData();
        if (data.getType() != OMMTypes.FIELD_LIST)
        {
            myCallback.notifyStatus("UpdateOrder: Invalid data type; expecting FieldList!!!");
            return;
        }

        OMMData key = entry.getKey();
        if (key.getType() != OMMTypes.BUFFER)
        {
            myCallback.notifyStatus("UpdateOrder: Invalid data type; expecting Buffer!!!");
            return;
        }

        // decode the key
        String orderId = key.toString();
        if (orderId.length() <= 0)
        {
            myCallback.notifyStatus("UpdateOrder: No OrderId");
            return;
        }

        // *_log<<"Update order: "<<orderId<<endl<<endl;

        String orderPrice = null;
        String quoteTime = null;
        String orderSize = null;

        OMMFieldList fieldList = (OMMFieldList)data;
        for (Iterator iter = ((OMMIterable)fieldList).iterator(); iter.hasNext();)
        {
            OMMFieldEntry fldEntry = (OMMFieldEntry)iter.next();
            FidDef fiddef = _dataDict.getFidDef(fldEntry.getFieldId());

            // if the field is not defined in the fid db, not much we can do;
            if (fiddef == null)
                continue;

            OMMData entryData = (OMMData)fldEntry.getData(fiddef.getOMMType());
            switch (fldEntry.getFieldId())
            {
                case ORDER_PRC:
                    orderPrice = entryData.toString();
                    // *_log<<"ORDER_PRC: "<<orderPrice<<endl;
                    break;
                case QUOTIM_MS:
                    quoteTime = entryData.toString();
                    ;
                    // *_log<<"QUOTIM_MS: "<<quoteTime<<endl;
                    break;
                case ORDER_SIZE:
                    orderSize = entryData.toString();
                    ;
                    // *_log<<"ORDER_SIZE: "<<orderSize<<endl;
                    break;

                default:
                    break;
            }
        }

        // System.out.println("UpdateOrder... OrderId: " + orderId);
        myCallback.processUpdateOrder(orderId, orderSize, orderPrice, quoteTime);

    }

    /*
     * Process DELETE Order
     */
    void deleteOrder(OMMMapEntry entry)
    {
        OMMData key = entry.getKey();
        if (key.getType() != OMMTypes.BUFFER)
        {
            myCallback.notifyStatus("DeleteOrder: Invalid data type; expecting Buffer!!!");
            return;
        }

        String orderId = key.toString();
        if (orderId.length() <= 0)
        {
            myCallback.notifyStatus("DeleteOrder: No OrderId");
            return;
        }

        // *_log<<"Deleting orderId: "<<orderId<<endl<<endl;
        // System.out.println("DeleteOrder... OrderId: " + orderId);
        myCallback.processDeleteOrder(orderId);
    }

    // Process Summary Data
    void extractSummaryData(OMMData summaryData)
    {
        if (summaryData.getType() == OMMTypes.FIELD_LIST)
        {
            OMMFieldList fldList = (OMMFieldList)summaryData;

            // *_log<<"Summary Data:"<<endl;

            String currency = null, exchId = null, trdUnits = null, mktState = null;

            for (Iterator iter = ((OMMIterable)fldList).iterator(); iter.hasNext();)
            {
                OMMFieldEntry field = (OMMFieldEntry)iter.next();
                FidDef fiddef = _dataDict.getFidDef(field.getFieldId());

                if (fiddef == null)
                    continue;

                OMMData data = field.getData(fiddef.getOMMType());
                switch (field.getFieldId())
                {
                    case CURRENCY:
                        currency = _dataDict.expandedValueFor(CURRENCY, ((OMMEnum)data).getValue());
                        break;

                    case TRD_UNITS:
                        trdUnits = _dataDict.expandedValueFor(TRD_UNITS, ((OMMEnum)data).getValue());
                        break;

                    case RDN_EXCH:
                        exchId = _dataDict.expandedValueFor(RDN_EXCH, ((OMMEnum)data).getValue());
                        break;

                    case MKT_STATE:
                        mktState = _dataDict.expandedValueFor(MKT_STATE, ((OMMEnum)data).getValue());
                        break;

                    default:
                        break;
                }
            }

            // *_log<<endl;

            myCallback.processSummary(currency, trdUnits, exchId, mktState);

        }
    }

}
