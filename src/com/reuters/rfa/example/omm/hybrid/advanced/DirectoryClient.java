package com.reuters.rfa.example.omm.hybrid.advanced;

import java.util.Iterator;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.omm.hybrid.DictionaryManager;
import com.reuters.rfa.omm.OMMArray;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMDataBuffer;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMFilterEntry;
import com.reuters.rfa.omm.OMMFilterList;
import com.reuters.rfa.omm.OMMItemGroup;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * Manages directory request and response.
 * 
 * 
 */
public class DirectoryClient implements Client
{

    private AdvancedSessionClient _parent;
    private ItemGroupManager _itemGroupManager;
    private DictionaryManager _dictionaryManager;

    private String _instanceName;

    private Handle _directoryHandle;

    protected DirectoryClient(AdvancedSessionClient parent)
    {
        _parent = parent;
        _itemGroupManager = _parent._itemGroupManager;
        _dictionaryManager = _parent._dictionaryManager;
        _instanceName = "[DirectoryClient #" + AdvancedSessionClient.numberOfInstances + "]";
    }

    protected void sendRequest()
    {
        OMMPool pool = _parent._pool;
        OMMMsg msg = pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.DIRECTORY);
        OMMAttribInfo attribInfo = pool.acquireAttribInfo();
        attribInfo.setFilter(RDMService.Filter.INFO | RDMService.Filter.STATE
                | RDMService.Filter.GROUP);
        msg.setAttribInfo(attribInfo);

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
        ommItemIntSpec.setMsg(msg);
        System.out.println(_instanceName + " Sending directory request...");

        _directoryHandle = _parent.getConsumer().registerClient(_parent.getEventQueue(),
                                                                ommItemIntSpec, this, null);

        pool.releaseMsg(msg);
    }

    protected void closeRequest()
    {
        if (_directoryHandle != null)
            _parent.getConsumer().unregisterClient(_directoryHandle);
    }

    public void processEvent(Event event)
    {

        // the last directory response
        if (event.getType() == Event.COMPLETION_EVENT)
        {
            return;
        }

        if (event.getType() != Event.OMM_ITEM_EVENT)
        {
            System.out.println(_instanceName + " Received an unsupported Event type");
            return;
        }

        OMMItemEvent itemEvent = (OMMItemEvent)event;
        OMMMsg msg = itemEvent.getMsg();
        if (msg.getDataType() != OMMTypes.MAP)
        {
            System.out.println(_instanceName + " Expected MAP");
            return;
        }

        OMMMap map = (OMMMap)msg.getPayload();
        for (Iterator<?> iter = map.iterator(); iter.hasNext();)
        {
            OMMMapEntry mapEntry = (OMMMapEntry)iter.next();

            byte action = mapEntry.getAction();
            // each mapEntry will be a separate service
            String serviceName = mapEntry.getKey().toString();

            if (action == OMMMapEntry.Action.DELETE)
            {
                System.out.println(_instanceName + " Received DELETE for " + serviceName);

                OMMState closeStatus = _parent._pool.acquireState();
                closeStatus.setStreamState(OMMState.Stream.CLOSED);
                closeStatus.setDataState(OMMState.Data.NO_CHANGE);
                closeStatus.setCode(OMMState.Code.NONE);
                closeStatus.setText("Service has been closed.");
                _itemGroupManager.applyStatus(serviceName, closeStatus);

            }
            else
            {
                if (mapEntry.getDataType() != OMMTypes.FILTER_LIST)
                {
                    System.out.println(_instanceName + " Expected FILTER_LIST");
                    continue;
                }

                decodeMapEntry(serviceName, mapEntry);
            }
        }
    }

    private void decodeMapEntry(String serviceName, OMMMapEntry mapEntry)
    {
        OMMFilterList serviceFilterList = (OMMFilterList)(mapEntry.getData());
        for (Iterator<?> filter = serviceFilterList.iterator(); filter.hasNext();)
        {
            OMMFilterEntry serviceFilterEntry = (OMMFilterEntry)filter.next();
            switch (serviceFilterEntry.getFilterId())
            {
                case RDMService.FilterId.INFO:
                {
                    if (serviceFilterEntry.getDataType() == OMMTypes.ELEMENT_LIST)
                    {
                        decodeInfo(serviceName, serviceFilterEntry);
                    }
                    else
                    {
                        System.out.println(_instanceName + " Expected ELEMENT_LIST");
                    }
                }
                    break;
                case RDMService.FilterId.STATE:
                {
                    if (serviceFilterEntry.getDataType() == OMMTypes.ELEMENT_LIST)
                    {
                        decodeState(serviceName, serviceFilterEntry);
                    }
                    else
                    {
                        System.out.println(_instanceName + " Expected ELEMENT_LIST");
                    }
                }
                    break;
                case RDMService.FilterId.GROUP:
                {
                    if (serviceFilterEntry.getDataType() == OMMTypes.ELEMENT_LIST)
                    {
                        decodeGroup(serviceName, serviceFilterEntry);
                    }
                    else
                    {
                        System.out.println(_instanceName + " Expected ELEMENT_LIST");
                    }
                }
                    break;
            }
        }

    }

    private void decodeInfo(String serviceName, OMMFilterEntry serviceFilterEntry)
    {
        OMMData serviceFilterEntryData = serviceFilterEntry.getData();
        for (Iterator<?> eliter = ((OMMElementList)serviceFilterEntryData).iterator(); eliter
                .hasNext();)
        {
            OMMElementEntry entry = (OMMElementEntry)eliter.next();
            OMMData data = entry.getData();

            if (entry.getName().equals(RDMService.Info.DictionariesProvided))
            {
                if (data.getType() == OMMTypes.ARRAY)
                {
                    OMMArray array = (OMMArray)data;
                    Iterator<?> aiter = array.iterator();
                    OMMEntry aentry;
                    while (aiter.hasNext())
                    {
                        aentry = (OMMEntry)aiter.next();
                        _dictionaryManager.addDictionaryProvided(aentry.getData().toString(),
                                                                 serviceName);
                    }
                }
            }
            else if (entry.getName().equals(RDMService.Info.DictionariesUsed))
            {
                if (data.getType() == OMMTypes.ARRAY)
                {
                    OMMArray array = (OMMArray)data;
                    Iterator<?> aiter = array.iterator();
                    OMMEntry aentry;
                    while (aiter.hasNext())
                    {
                        aentry = (OMMEntry)aiter.next();
                        _dictionaryManager.addDictionaryUsed(aentry.getData().toString(),
                                                             serviceName);
                    }
                }
            }
        }
    }

    private void decodeState(String serviceName, OMMFilterEntry serviceFilterEntry)
    {
        OMMData serviceFilterEntryData = serviceFilterEntry.getData();
        for (Iterator<?> eliter = ((OMMElementList)serviceFilterEntryData).iterator(); eliter
                .hasNext();)
        {
            OMMElementEntry entry = (OMMElementEntry)eliter.next();
            OMMData data = entry.getData();

            if (entry.getName().equals(RDMService.SvcState.Status))
            {
                OMMState status = (OMMState)data;

                _itemGroupManager.applyStatus(serviceName, status);
            }
        }
    }

    private void decodeGroup(String serviceName, OMMFilterEntry serviceFilterEntry)
    {

        byte[] group = null;
        byte[] mergedToGroup = null;
        OMMState groupStatus = null;

        // Get Group, MergedToGroup and Status from the payload
        OMMData serviceFilterEntryData = serviceFilterEntry.getData();
        for (Iterator<?> eliter = ((OMMElementList)serviceFilterEntryData).iterator(); eliter
                .hasNext();)
        {
            OMMElementEntry entry = (OMMElementEntry)eliter.next();
            OMMData data = entry.getData();

            if (entry.getName().equals(RDMService.Group.Group))
            {
                group = ((OMMDataBuffer)data).getBytes().clone();
            }
            else if (entry.getName().equals(RDMService.Group.MergedToGroup))
            {
                mergedToGroup = ((OMMDataBuffer)data).getBytes().clone();
            }
            else if (entry.getName().equals(RDMService.Group.Status))
            {
                groupStatus = (OMMState)data;
            }
        }

        if (group != null)
        {
            // First we need to merge groups
            OMMItemGroup fromGroup = OMMItemGroup.create(group);
            if (mergedToGroup != null)
            {
                OMMItemGroup toGroup = OMMItemGroup.create(mergedToGroup);
                _itemGroupManager.mergeGroup(serviceName, fromGroup, toGroup);
                fromGroup = toGroup;

                System.out.println(_instanceName + " Merged group " + fromGroup + " in "
                        + serviceName + " to group " + toGroup);
            }

            // Then we apply status to the group
            if (groupStatus != null)
            {
                _itemGroupManager.applyStatus(serviceName, fromGroup, groupStatus);
            }
        }
    }

}
