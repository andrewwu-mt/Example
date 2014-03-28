package com.reuters.rfa.example.omm.domainServer.symbollist;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.dictionary.DataDef;
import com.reuters.rfa.dictionary.DataDefDictionary;
import com.reuters.rfa.example.framework.prov.ProvDomainMgr;
import com.reuters.rfa.example.omm.domainServer.DataGenerator;
import com.reuters.rfa.example.omm.domainServer.DataStreamItem;
import com.reuters.rfa.example.omm.domainServer.ItemInfo;
import com.reuters.rfa.example.omm.domainServer.symbollist.SymbolListGenerator.OrderEntry;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPriority;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;

/**
 * SymbolListStreamItem is a stream item for Symbol List domain.
 * 
 * This class is responsible for encoding the Open Message Model data for Symbol
 * List domain and sending list of symbols refresh message.
 * 
 * DataDefinitions are use by default to reduce bandwidth. Each refresh includes
 * up to 150 Map entries. The TotalCountHint is not provided. The Map.KeyFieldId
 * is currently not set.
 * 
 */
public class SymbolListStreamItem extends DataStreamItem
{

    OMMEncoder _encoder;
    ItemInfo _itemInfo;
    int _maxEntries = 150;
    byte[] _preencodedSummaryDataBuffer;
    DataDefDictionary _dataDefDictionary;

    public SymbolListStreamItem(DataGenerator dataGenerator, ProvDomainMgr mgr, Token token,
            OMMMsg msg)
    {
        super(mgr, token);
        _encoder = _mgr.getPool().acquireEncoder();
        _itemInfo = new ItemInfo(dataGenerator, _maxEntries);
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
        // If this item is not yet completed, sends refresh,
        // else sends update
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
        
        // Set the message model type to be the type requested.
        outmsg.setMsgModelType(RDMMsgTypes.SYMBOL_LIST);
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

        _encoder.encodeMapInit(flags, OMMTypes.BUFFER, OMMTypes.FIELD_LIST, 0, (short)0);

        if (isEncodeDataDef())
        {
            encodeDataDef();
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
        outmsg.setMsgModelType(RDMMsgTypes.SYMBOL_LIST);
        outmsg.setMsgType(OMMMsg.MsgType.UPDATE_RESP);
        outmsg.setRespTypeNum((short)0); // INSTRUMENT_UPDATE_UNSPECIFIED

        _encoder.initialize(OMMTypes.MSG, 2000);
        _encoder.encodeMsgInit(outmsg, OMMTypes.NO_DATA, OMMTypes.MAP);
        _mgr.getPool().releaseMsg(outmsg);

        _encoder.encodeMapInit(0, OMMTypes.BUFFER, OMMTypes.FIELD_LIST, 0, (short)0);
        encodeData(orderEntries, false);
        _encoder.encodeAggregateComplete(); // MAP
    }

    private void encodeDataDef()
    {
        if (_dataDefDictionary == null)
        {
            _dataDefDictionary = DataDefDictionary.create(OMMTypes.FIELD_LIST_DEF_DB);
            DataDef dataDef = DataDef.create((short)0, OMMTypes.FIELD_LIST_DEF);
            dataDef.addDef((short)3422, OMMTypes.RMTES_STRING); // PROV_SYMB
                                                                // "PROVIDER SYMBOL"
            dataDef.addDef((short)1, OMMTypes.UINT_2); // PROD_PERM
                                                       // "PERMISSION"

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
            _encoder.encodeBytes(orderEntry.provSymb.getBytes());

            if (orderEntry.action == OMMMapEntry.Action.ADD)
            {
                if (isDefined)
                {
                    _encoder.encodeFieldListInit(OMMFieldList.HAS_DATA_DEF_ID
                            | OMMFieldList.HAS_DEFINED_DATA, (short)0, (short)0, (short)0);
                    _encoder.encodeRmtesString(orderEntry.provSymb);
                    _encoder.encodeUInt((long)orderEntry.prodPerm);
                }
                else
                {
                    _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0,
                                                 (short)0, (short)0);
                    _encoder.encodeFieldEntryInit((short)3422, OMMTypes.RMTES_STRING);
                    _encoder.encodeRmtesString(orderEntry.provSymb);
                    _encoder.encodeFieldEntryInit((short)1, OMMTypes.UINT);
                    _encoder.encodeUInt((long)orderEntry.prodPerm);
                    _encoder.encodeAggregateComplete();
                }
            }
            else if (orderEntry.action == OMMMapEntry.Action.UPDATE)
            {

                _encoder.encodeFieldListInit(OMMFieldList.HAS_STANDARD_DATA, (short)0, (short)0,
                                             (short)0);
                _encoder.encodeFieldEntryInit((short)3422, OMMTypes.RMTES_STRING);
                _encoder.encodeRmtesString(orderEntry.provSymb);
                _encoder.encodeFieldEntryInit((short)1, OMMTypes.UINT);
                _encoder.encodeUInt((long)orderEntry.prodPerm);
                _encoder.encodeAggregateComplete();
            }
            else
            {
                // Action.DELETE, don't need to encode data.DELETE
            }
        }
    }
}
