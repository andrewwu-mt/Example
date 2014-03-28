package com.reuters.rfa.example.omm.domainServer.marketprice;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.dictionary.DataDefDictionary;
import com.reuters.rfa.example.framework.prov.ProvDomainMgr;
import com.reuters.rfa.example.omm.domainServer.DataGenerator;
import com.reuters.rfa.example.omm.domainServer.DataStreamItem;
import com.reuters.rfa.example.omm.domainServer.ItemInfo;
import com.reuters.rfa.example.omm.domainServer.marketprice.MarketPriceGenerator.Entry;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPriority;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;

/**
 * MarketPriceStreamItem is a stream item for Market Price domain.
 * 
 * This class is responsible for encoding the Open Message Model data for Market
 * Price domain and sending refresh and update message.
 */
public class MarketPriceStreamItem extends DataStreamItem
{

    OMMEncoder _encoder;
    ItemInfo _itemInfo;
    byte[] _preencodedSummaryDataBuffer;
    DataDefDictionary _dataDefDictionary;

    public MarketPriceStreamItem(DataGenerator dataGenerator, ProvDomainMgr mgr, Token token,
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
            Object[] entries = itemInfo.getNextInitialEntries();
            encodeRefresh(itemInfo, entries, solicited);
        }
        else
        {
            Object[] entries = itemInfo.getNextEntries();
            encodeUpdate(itemInfo, entries);
        }
    }

    private void encodeRefresh(ItemInfo itemInfo, Object[] entries, boolean solicited)
    {
        OMMMsg outmsg = _mgr.getPool().acquireMsg();

        outmsg.setAttribInfo(itemInfo.getOMMAttribInfo());
        outmsg.setItemGroup(0);
        outmsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
        outmsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);

        if (!itemInfo.isStreamingRequest())
        {
            outmsg.setState(OMMState.Stream.NONSTREAMING, OMMState.Data.OK, OMMState.Code.NONE,
                            "OK");
        }
        else
        {
            outmsg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "OK");
        }
        outmsg.setItemGroup(2); // Set the item group to be 2. Indicates the
                                // item will be in group 2.
        
        if (solicited)
            outmsg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            outmsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        
        _encoder.initialize(OMMTypes.MSG, 5000);
        _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);
        _mgr.getPool().releaseMsg(outmsg);

        _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)1, (short)0);

        encodeData(entries, true);
        _encoder.encodeAggregateComplete(); // FIELDLIST
    }

    private void encodeUpdate(ItemInfo itemInfo, Object[] entries)
    {

        OMMMsg outmsg = _mgr.getPool().acquireMsg();
        if (itemInfo.getAttribInUpdates())
        {
            outmsg.setAttribInfo(itemInfo.getOMMAttribInfo());
        }
        outmsg.setMsgModelType(RDMMsgTypes.MARKET_PRICE);
        outmsg.setMsgType(OMMMsg.MsgType.UPDATE_RESP);
        outmsg.setIndicationFlags(OMMMsg.Indication.DO_NOT_CONFLATE);

        _encoder.initialize(OMMTypes.MSG, 2000);
        _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.FIELD_LIST);
        _mgr.getPool().releaseMsg(outmsg);

        _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)1, (short)0);
        encodeData(entries, false);
        _encoder.encodeAggregateComplete(); // FIELDLIST
    }

    private void encodeData(Object[] entries, boolean isRefresh)
    {
        Entry entry = (Entry)entries[0];
        if (isRefresh)
        {
            _encoder.encodeFieldEntryInit((short)2, OMMTypes.UINT); // RDNDISPLAY
                                                                    // "DISPLAYTEMPLATE"
            _encoder.encodeUInt(100L);
            _encoder.encodeFieldEntryInit((short)4, OMMTypes.ENUM); // RDN_EXCHID
                                                                    // "IDN EXCHANGE ID"
            _encoder.encodeEnum(155);
            _encoder.encodeFieldEntryInit((short)38, OMMTypes.DATE); // DIVPAYDATE
                                                                     // "DIVIDEND DATE"
            _encoder.encodeDate(2009, 5, 15);
        }
        _encoder.encodeFieldEntryInit((short)6, OMMTypes.REAL); // TRDPRC_1
                                                                // "LAST"
        _encoder.encodeReal((long)entry.trdPrice, OMMNumeric.EXPONENT_NEG2);
        _encoder.encodeFieldEntryInit((short)22, OMMTypes.REAL); // BID "BID"
        _encoder.encodeReal((long)entry.bid, OMMNumeric.EXPONENT_NEG2);
        _encoder.encodeFieldEntryInit((short)25, OMMTypes.REAL); // ASK "ASK"
        _encoder.encodeReal((long)entry.ask, OMMNumeric.EXPONENT_NEG2);
        _encoder.encodeFieldEntryInit((short)32, OMMTypes.REAL); // ACVOL_1
                                                                 // "VOL ACCUMULATED"
        _encoder.encodeReal(entry.acvol, OMMNumeric.EXPONENT_0);

        if (isRefresh)
        {
            _encoder.encodeFieldEntryInit((short)267, OMMTypes.TIME); // ASK_TIME
            _encoder.encodeTime(19, 12, 23, 0);
        }
    }
}
