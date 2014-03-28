package com.reuters.rfa.example.omm.domainServer.marketbyprice;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.dictionary.DataDef;
import com.reuters.rfa.dictionary.DataDefDictionary;
import com.reuters.rfa.example.framework.prov.ProvDomainMgr;
import com.reuters.rfa.example.omm.domainServer.DataGenerator;
import com.reuters.rfa.example.omm.domainServer.DataStreamItem;
import com.reuters.rfa.example.omm.domainServer.ItemInfo;
import com.reuters.rfa.example.omm.domainServer.marketbyprice.MarketByPriceGenerator.OrderEntry;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPriority;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;

/**
 * MarketByPriceStreamItem is a stream item for Market By Price domain.
 * 
 * This class is responsible for encoding the Open Message Model data for Market
 * By Price domain and sending refresh and update message.
 */
public class MarketByPriceStreamItem extends DataStreamItem
{

    OMMEncoder _encoder;
    ItemInfo _itemInfo;
    byte[] _preencodedSummaryDataBuffer;
    DataDefDictionary _dataDefDictionary;

    public MarketByPriceStreamItem(DataGenerator dataGenerator, ProvDomainMgr mgr, Token token,
            OMMMsg msg)
    {

        super(mgr, token);
        _encoder = _mgr.getPool().acquireEncoder();
        _itemInfo = new ItemInfo(dataGenerator);
        _itemInfo.setRequestToken(token);
        _itemInfo.setAttribInUpdates(msg.isSet(OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES));
        _itemInfo.setStreaming(!msg.isSet(OMMMsg.Indication.NONSTREAMING));
        OMMAttribInfo attribInfo = _mgr.getPool().acquireCopy(msg.getAttribInfo(), true);
        _itemInfo.setOMMAttribInfo(attribInfo);
        _itemInfo.setName(attribInfo.getName());

        if (msg.has(OMMMsg.HAS_PRIORITY))
        {
            OMMPriority priority = msg.getPriority();
            _itemInfo.setPriorityCount(priority.getCount());
            _itemInfo.setPriorityClass(priority.getPriorityClass());
        }

    }

    protected void handlePriorityRequest(OMMMsg msg, ItemInfo itemInfo)
    {
        if (!msg.has(OMMMsg.HAS_PRIORITY))
        {
            return;
        }

        OMMPriority priority = msg.getPriority();
        itemInfo.setPriorityCount(priority.getCount());
        itemInfo.setPriorityClass(priority.getPriorityClass());
    }

    // Always send multi-part refresh.
    public void sendRefresh(boolean solicited)
    {

        String serviceName = _itemInfo.getOMMAttribInfo().getServiceName();
        String itemName = _itemInfo.getOMMAttribInfo().getName();
        System.out.println("Received request for serviceName = " + serviceName + " itemName = "
                + itemName);

        _itemInfo.reset();

        while (!_itemInfo.isRefreshCompleted())
        {
            encodeResponse(_itemInfo, solicited);
            _mgr.submit(_token, (OMMMsg)_encoder.getEncodedObject());
        }
    }

    public boolean sendUpdate()
    {

        encodeResponse(_itemInfo, true);
        _mgr.submit(_token, (OMMMsg)_encoder.getEncodedObject());

        // for non-streaming request, remove the itemInfo only when the refresh
        // is completed
        if (_itemInfo.isRefreshCompleted() && !_itemInfo.isStreamingRequest())
        {
            // this is the last one, no more call is needed
            return true;
        }
        return false;
    }

    private void encodeResponse(ItemInfo itemInfo, boolean solicited)
    {
        // If this item is not yet completed, sends refresh, else sends update
        if (!itemInfo.isRefreshCompleted())
        {
            Object[] orderEntries = itemInfo.getNextInitialEntries();
            boolean completed = itemInfo.isRefreshCompleted();
            encodeRefresh(itemInfo, orderEntries, completed, solicited);
        }
        else
        {
            Object[] orderEntries = itemInfo.getNextEntries();
            encodeUpdate(itemInfo, orderEntries);
        }
    }

    private void encodeRefresh(ItemInfo itemInfo, Object[] orderEntries, boolean completed, boolean solicited)
    {
        OMMMsg outmsg = _mgr.getPool().acquireMsg();

        outmsg.setAttribInfo(itemInfo.getOMMAttribInfo());
        outmsg.setItemGroup(0);
        
        // Set the message type to be refresh response.
        outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        
        // Set the message model type to be the type
        // requested.
        outmsg.setMsgModelType(RDMMsgTypes.MARKET_BY_PRICE);
        if (completed)
        {
            // Indicates this message will be the full refresh;
            // or this is the last refresh in the multi-part refresh.
            outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        }
        if (completed && !itemInfo.isStreamingRequest())
        {
            outmsg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE,
                            "OK");
        }
        else
        {
            outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "OK");
        }
        
        if (solicited)
            outmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        
        _encoder.initialize(OMMTypes.MSG, 5000);
        _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.MAP);
        _mgr.getPool().releaseMsg(outmsg);

        int flags = 0;
        if (isEncodeDataDef())
        {
            flags = OMMMap.HAS_DATA_DEFINITIONS;
        }
        // Summary data is sent with the first part only
        if (itemInfo.isFirstPart())
        {
            flags |= OMMMap.HAS_SUMMARY_DATA;
        }
        _encoder.encodeMapInit(flags, OMMTypes.BUFFER, OMMTypes.FIELD_LIST, 0, (short)0);

        if (isEncodeDataDef())
        {
            encodeDataDef();
        }
        if (itemInfo.isFirstPart())
        {
            encodeSummaryData();
        }

        encodeData(orderEntries, isEncodeDataDef());
        _encoder.encodeAggregateComplete(); // MAP
    }

    private void encodeUpdate(ItemInfo itemInfo, Object[] orderEntries)
    {

        OMMMsg outmsg = _mgr.getPool().acquireMsg();
        if (itemInfo.getAttribInUpdates())
        {
            outmsg.setAttribInfo(itemInfo.getOMMAttribInfo());
        }
        outmsg.setMsgModelType(RDMMsgTypes.MARKET_BY_PRICE);
        outmsg.setMsgType(OMMMsg.MsgType.UPDATE_RESP);
        outmsg.setRespTypeNum((short)0); // INSTRUMENT_UPDATE_UNSPECIFIED

        _encoder.initialize(OMMTypes.MSG, 2000);
        _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.MAP);
        _mgr.getPool().releaseMsg(outmsg);

        _encoder.encodeMapInit(0, OMMTypes.BUFFER, OMMTypes.FIELD_LIST, 0, (short)0);
        encodeData(orderEntries, false);
        _encoder.encodeAggregateComplete(); // MAP
    }

    private void encodeSummaryData()
    {

        if (_preencodedSummaryDataBuffer == null)
        {
            OMMEncoder encoder = _mgr.getPool().acquireEncoder();
            encoder.initialize(OMMTypes.FIELD_LIST, 500);

            encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)1,
                                        (short)0);
            encoder.encodeFieldEntryInit((short)15, OMMTypes.ENUM); // CURRENCY
                                                                    // "CURRENCY"
            encoder.encodeEnum(840); // USD
            encoder.encodeFieldEntryInit((short)53, OMMTypes.ENUM); // TRD_UNITS
                                                                    // "TRADING UNITS"
            encoder.encodeEnum(2); // 2 decimal places
            encoder.encodeFieldEntryInit((short)133, OMMTypes.ENUM); // MKT_ST_IND
                                                                     // "MARKET STAT"
            encoder.encodeEnum(1); // fast market
            encoder.encodeFieldEntryInit((short)1709, OMMTypes.ENUM); // RDN_EXCHD2
                                                                      // "EXCHANGE ID 2"
            encoder.encodeEnum(2); // exch id
            encoder.encodeFieldEntryInit((short)3386, OMMTypes.DATE); // QUOTE_DATE
                                                                      // "QUOTE DATE"
            Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
            encoder.encodeDate(cal);
            encoder.encodeFieldEntryInit((short)1, OMMTypes.UINT); // PROD_PERM
                                                                   // "PERMISSION"
            encoder.encodeUInt(530L);
            encoder.encodeAggregateComplete();
            _preencodedSummaryDataBuffer = encoder.getBytes();
            _mgr.getPool().releaseEncoder(encoder);
        }
        _encoder.encodeSummaryDataInit();
        _encoder.encodeBytes(_preencodedSummaryDataBuffer);
    }

    private void encodeDataDef()
    {
        if (_dataDefDictionary == null)
        {
            _dataDefDictionary = DataDefDictionary.create(OMMTypes.FIELD_LIST_DEF_DB);
            DataDef dataDef = DataDef.create((short)0, OMMTypes.FIELD_LIST_DEF);
            dataDef.addDef((short)3427, OMMTypes.REAL); // ORDER_PRC
                                                        // "ORDER PRICE"
            dataDef.addDef((short)3428, OMMTypes.ENUM); // ORDER_SIDE
                                                        // "ORDER SIDE"
            dataDef.addDef((short)3429, OMMTypes.REAL); // ORDER_SIZE
                                                        // "ORDER SIZE"
            dataDef.addDef((short)3430, OMMTypes.UINT); // NO_ORD
                                                        // "NUMBER OF ORDERS"
            dataDef.addDef((short)3855, OMMTypes.UINT); // QUOTIM_MS
            _dataDefDictionary.putDataDef(dataDef);
        }

        _encoder.encodeDataDefsInit();
        DataDefDictionary.encodeDataDef(_dataDefDictionary, _encoder, (short)0);
        _encoder.encodeDataDefsComplete();
    }

    private void encodeData(Object[] orderEntries, boolean isDefined)
    {

        for (int i = 0; i < orderEntries.length; i++)
        {

            OrderEntry orderEntry = (OrderEntry)orderEntries[i];
            _encoder.encodeMapEntryInit(0, orderEntry.action, null);
            
            // Key = orderPrice and orderSide (B for buy or S for Sell)
            _encoder.encodeBytes(orderEntry.orderId.getBytes());

            if (orderEntry.action == OMMMapEntry.Action.ADD)
            {
                if (isDefined)
                {
                    _encoder.encodeFieldListInit(OMMFieldList.HAS_DATA_DEF_ID
                            | OMMFieldList.HAS_DEFINED_DATA, (short)0, (short)0, (short)0);
                    _encoder.encodeReal((long)orderEntry.orderPrice, OMMNumeric.EXPONENT_NEG2);
                    _encoder.encodeEnum(orderEntry.orderSide);
                    _encoder.encodeReal(orderEntry.orderSize, OMMNumeric.EXPONENT_0);
                    _encoder.encodeUInt(orderEntry.noOrder);
                    _encoder.encodeUInt(orderEntry.quotim);
                }
                else
                {
                    _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0,
                                                 (short)0, (short)0);
                    _encoder.encodeFieldEntryInit((short)3427, OMMTypes.REAL);
                    _encoder.encodeReal((long)orderEntry.orderPrice, OMMNumeric.EXPONENT_NEG2);
                    _encoder.encodeFieldEntryInit((short)3428, OMMTypes.ENUM);
                    _encoder.encodeEnum(orderEntry.orderSide);
                    _encoder.encodeFieldEntryInit((short)3429, OMMTypes.REAL);
                    _encoder.encodeReal(orderEntry.orderSize, OMMNumeric.EXPONENT_0);
                    _encoder.encodeFieldEntryInit((short)3430, OMMTypes.UINT);
                    _encoder.encodeUInt(orderEntry.noOrder);
                    _encoder.encodeFieldEntryInit((short)3855, OMMTypes.UINT);
                    _encoder.encodeUInt(orderEntry.quotim);
                    _encoder.encodeAggregateComplete();
                }
            }
            else if (orderEntry.action == OMMMapEntry.Action.UPDATE)
            {

                _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)0,
                                             (short)0);
                _encoder.encodeFieldEntryInit((short)3429, OMMTypes.REAL);
                _encoder.encodeReal(orderEntry.orderSize, OMMNumeric.EXPONENT_0);
                _encoder.encodeFieldEntryInit((short)3430, OMMTypes.UINT);
                _encoder.encodeUInt(orderEntry.noOrder);
                _encoder.encodeFieldEntryInit((short)3855, OMMTypes.UINT);
                _encoder.encodeUInt(orderEntry.quotim);
                _encoder.encodeAggregateComplete();
            }
            else
            {
                // Action.DELETE, don't need to encode data.
            }
        }
    }
}
