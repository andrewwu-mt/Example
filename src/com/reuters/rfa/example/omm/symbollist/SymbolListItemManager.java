package com.reuters.rfa.example.omm.symbollist;

import java.util.HashMap;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.framework.sub.OMMSubAppContext;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.session.omm.OMMItemEvent;

/**
 * Makes non-streaming requests for items from a single service and domain,
 * manages the handles, and processes events.
 */
public class SymbolListItemManager implements Client
{
    String _serviceName;
    short _messageModelType;
    OMMSubAppContext _appContext;
    HashMap<String, Handle> _nameMap; // <String itemName, Handle itemHandle>
    HashMap<Handle, String> _handleMap; // <String itemName, Handle itemHandle>
    int _count;

    public SymbolListItemManager(OMMSubAppContext appContext, String serviceName, short mmt,
            int totalCountHint)
    {
        _appContext = appContext;
        _serviceName = serviceName;
        _messageModelType = mmt;
        _nameMap = new HashMap<String, Handle>(Math.min(totalCountHint, 16));
        _handleMap = new HashMap<Handle, String>(Math.min(totalCountHint, 16));
    }

    public void processEvent(Event event)
    {
        if (event.getType() != Event.OMM_ITEM_EVENT)
            System.out.println("Received unhandled event: " + event);

        OMMItemEvent ommItemEvent = (OMMItemEvent)event;
        Handle itemHandle = event.getHandle();
        String itemName = _handleMap.get(itemHandle);
        OMMMsg msg = ommItemEvent.getMsg();
        if (msg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
        {
            if (msg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
            {
                System.out.println("Received refresh complete for: " + itemName);
            }
            else
            {
                System.out.println("Received refresh for: " + itemName);
            }
        }
        else if (msg.getMsgType() == OMMMsg.MsgType.UPDATE_RESP)
        {
            System.out.println("Received update for: " + itemName);
        }
        else if (msg.getMsgType() == OMMMsg.MsgType.GENERIC)
        {
            System.out.println("Received generic message type, not supported. ");
        }
        else if (msg.getMsgType() == OMMMsg.MsgType.STATUS_RESP)
        {
            System.out.println("Received status response for: " + itemName);
            GenericOMMParser.parse(msg);
        }
        else
        {
            System.out.println("ERROR: Received unexpected message type. " + msg.getMsgType());
        }

        if (msg.isFinal())
        {
            _nameMap.remove(itemName);
            _handleMap.remove(itemHandle);
        }
    }

    public int numberOfOpens()
    {
        return _count;
    }

    public void open(String itemName)
    {
        // all requests are made as snapshots
        Handle itemHandle = _appContext.register(this, _serviceName, itemName, false,
                                                 _messageModelType);
        _handleMap.put(itemHandle, itemName);
        _nameMap.put(itemName, itemHandle);
        _count++;
    }

    public void close(String itemName)
    {
        Handle itemHandle = _nameMap.remove(itemName);
        if (itemHandle == null)
            return;
        _appContext.unregister(itemHandle);
        _handleMap.remove(itemHandle);
    }
}
