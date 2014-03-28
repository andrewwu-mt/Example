package com.reuters.rfa.example.omm.symbollist;

import java.util.Iterator;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.framework.sub.OMMSubAppContext;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMItemEvent;

/**
 * A SymbolListClient is used for making a
 * {@link com.reuters.rfa.rdm.RDMMsgTypes#SYMBOL_LIST SYMBOL_LIST} request and
 * populating the responses. The SymbolListClient will make a non-streaming
 * request for each new symbol discovered.
 */
public class SymbolListClient implements Client
{
    OMMSubAppContext _appContext;
    String _serviceName;
    short _msgModelType;
    String _name;
    Handle _handle;
    SymbolListItemManager _itemManager;

    public SymbolListClient(OMMSubAppContext appContext, String serviceName, String itemName,
            short msgModelType)
    {
        _appContext = appContext;
        _serviceName = serviceName;
        _msgModelType = msgModelType;
        _name = itemName;
    }

    /**
     * open a {@link com.reuters.rfa.rdm.RDMMsgTypes#SYMBOL_LIST SYMBOL_LIST}
     * request. Using itself as a callback client.
     */
    public void open()
    {
        _handle = (_appContext).register(this, _serviceName, _name, true, RDMMsgTypes.SYMBOL_LIST);
    }

    void processOMMMsg(OMMMsg msg)
    {
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REFRESH_RESP:
                if (msg.getDataType() == OMMTypes.MAP)
                {
                    processMap((OMMMap)msg.getPayload());
                }
                else
                {
                    System.out.println("Cannot process this message, payload has no data or incorrect data type");
                    GenericOMMParser.parse(msg);
                }
                if (msg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
                    System.out.println(_name + " contains "
                            + (_itemManager != null ? _itemManager.numberOfOpens() : 0) + " items");
                break;
            case OMMMsg.MsgType.UPDATE_RESP:
                if (msg.getDataType() == OMMTypes.MAP)
                {
                    processMap((OMMMap)msg.getPayload());
                }
                else
                {
                    System.out.println("Cannot process this message, payload has no data or incorrect data type");
                    GenericOMMParser.parse(msg);
                }
                break;
            case OMMMsg.MsgType.STATUS_RESP:
                GenericOMMParser.parse(msg);
                break;
            case OMMMsg.MsgType.GENERIC:
                System.out.println("Received generic message type, not supported. ");
                break;
            default:
                System.out.println("ERROR: Received unexpected message type. " + msg.getMsgType());
        }
    }

    void processMap(OMMMap symbolList)
    {
        if (_itemManager == null)
            _itemManager = new SymbolListItemManager(_appContext, _serviceName, _msgModelType,
                    symbolList.getTotalCountHint());
        for (Iterator<?> iter = symbolList.iterator(); iter.hasNext();)
        {
            OMMMapEntry item = (OMMMapEntry)iter.next();
            String itemName = item.getKey().toString();
            switch (item.getAction())
            {
                case OMMMapEntry.Action.ADD:
                    add(itemName);
                    break;
                case OMMMapEntry.Action.UPDATE:
                    // ignore updates
                    break;
                case OMMMapEntry.Action.DELETE:
                    delete(itemName);
                    break;
            }
        }
    }

    void add(String itemName)
    {
        _itemManager.open(itemName);
    }

    void delete(String itemName)
    {
        _itemManager.close(itemName);
    }

    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_ITEM_EVENT:
                OMMMsg msg = ((OMMItemEvent)event).getMsg();
                processOMMMsg(msg);
                break;
            default:
                System.out.println("Unhandled event: " + event);
                break;
        }
    }

}
