package com.reuters.rfa.example.omm.itemgroups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMDataBuffer;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
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
 * An ItemGroupManager keeps track of every item request and its group. It is
 * not responsible for requesting and closing item. To use this class, first you
 * need to call {@link #sendRequest() sendRequest()} to register for directory
 * stream. Then after every item request, you pass the handle you received to
 * {@link #addItem(String, String, Handle) addItem()}. Finally every refresh or
 * status that has {@link com.reuters.rfa.omm.OMMMsg#HAS_ITEM_GROUP
 * HAS_ITEM_GROUP} flag set you call {@link #applyGroup(Handle, OMMItemGroup)
 * applyGroup()}.
 */
public class ItemGroupManager implements Client
{

    ItemGroupsDemo _mainApp;
    Handle _directoryHandle;
    private String _name;

    Map<String, HashMap<OMMItemGroup, GroupEntry>> _services; // Key=serviceName,
                                                              // value=Map(key=groupId,
                                                              // value=ServiceEntry)
    Map<Handle, HandleEntry> _handles; // key=handle, value=HandleEntry

    /*
     * _services --> SERVICE_A --> Group 1 --> Item 1, Item 2 --> Group 2 -->
     * Item 3, Item 4 --> SERVICE_B --> Group 1 --> Item 1, Item 2 --> Group 2
     * --> Item 3, Item 4
     * 
     * GroupEntry has a set of HandleEntry. HandleEntry can belong only one
     * ServiceEntry at any time. However, it can change group if - Receive
     * status response with item group - Receive directory update with group
     * filter
     */

    static class GroupEntry
    {
        public GroupEntry()
        {
            handleEntries = new HashSet<HandleEntry>();
        }

        public OMMItemGroup groupId;
        public Set<HandleEntry> handleEntries; // Set of HandleEntry
    }

    static class HandleEntry
    {
        public Handle handle;
        public String serviceName;
        public String itemName;
        public GroupEntry serviceEntry;
    }

    public ItemGroupManager(ItemGroupsDemo app)
    {
        _mainApp = app;
        _name = "ItemGroupManager";

        _services = new HashMap<String, HashMap<OMMItemGroup, GroupEntry>>();
        _handles = new HashMap<Handle, HandleEntry>();
    }

    /**
     * Send directory request with
     * {@link com.reuters.rfa.rdm.RDMService.Filter#STATE STATE} and
     * {@link com.reuters.rfa.rdm.RDMService.Filter#GROUP GROUP}.
     * 
     */
    public void sendRequest()
    {
        OMMEncoder encoder = _mainApp.getEncoder();
        OMMPool pool = _mainApp.getPool();
        encoder.initialize(OMMTypes.MSG, 500);
        OMMMsg msg = pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.DIRECTORY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        OMMAttribInfo attribInfo = pool.acquireAttribInfo();
        attribInfo.setFilter(RDMService.Filter.STATE | RDMService.Filter.GROUP);
        msg.setAttribInfo(attribInfo);

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
        ommItemIntSpec.setMsg(msg);
        System.out.println(_name + ": Sending directory request...");

        _directoryHandle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(),
                                                                    ommItemIntSpec, this, null);

        pool.releaseMsg(msg);
    }

    public Iterator<Handle> getAllHandles()
    {
        return new ArrayList<Handle>(_handles.keySet()).iterator();
    }

    /**
     * Close directory stream and cleanup resource.
     * 
     */
    public void cleanup()
    {
        _handles.clear();
        _services.clear();

        _mainApp.getOMMConsumer().unregisterClient(_directoryHandle);
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
            System.out.println(_name + ": Received an unsupported Event type");
            return;
        }

        OMMItemEvent itemEvent = (OMMItemEvent)event;
        OMMMsg msg = itemEvent.getMsg();

        if (msg.getDataType() != OMMTypes.MAP)
        {
            System.out.println(_name + ": Expected MAP");
            return;
        }

        OMMMap map = (OMMMap)msg.getPayload();
        for (Iterator<?> iter = map.iterator(); iter.hasNext();)
        {
            OMMMapEntry mapEntry = (OMMMapEntry)iter.next();

            // each mapEntry will be a separate service
            byte action = mapEntry.getAction();
            String serviceName = mapEntry.getKey().toString();

            if (action == OMMMapEntry.Action.DELETE)
            {
                removeService(serviceName);
            }
            else
            {
                if (mapEntry.getDataType() != OMMTypes.FILTER_LIST)
                {
                    System.out.println(_name + ": Expected FILTER_LIST");
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

                case RDMService.FilterId.STATE:
                {
                    if (serviceFilterEntry.getDataType() == OMMTypes.ELEMENT_LIST)
                    {
                        decodeState(serviceName, serviceFilterEntry);
                    }
                    else
                    {
                        System.out.println(_name + ": Expected ELEMENT_LIST");
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
                        System.out.println(_name + ": Expected ELEMENT_LIST");
                    }
                }
                    break;
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
                applyStatus(serviceName, status);
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
                System.out.println(_name + ": Merging group " + fromGroup + " in " + serviceName
                        + " to group " + toGroup);
                mergeGroup(serviceName, fromGroup, toGroup);
                fromGroup = toGroup;
            }

            // Then we apply status to the group
            if (groupStatus != null)
            {
                applyStatus(serviceName, fromGroup, groupStatus);
            }
        }
    }

    private void mergeGroup(String serviceName, OMMItemGroup fromGroup, OMMItemGroup toGroup)
    {
        Map<OMMItemGroup, GroupEntry> groups = (Map<OMMItemGroup, GroupEntry>)_services
                .get(serviceName);

        // no watching item for this service
        if (groups == null)
            return;

        GroupEntry seFromGroup = (GroupEntry)groups.get(fromGroup);

        // no item in this group, no need to merge
        if (seFromGroup == null)
        {
            System.out.println("There is no item in " + fromGroup + " group.");
            return;
        }

        GroupEntry seToGroup = (GroupEntry)groups.get(toGroup);
        if (seToGroup == null)
        {
            seToGroup = new GroupEntry();
            seToGroup.groupId = toGroup;
            groups.put(toGroup, seToGroup);
        }

        for (Iterator<HandleEntry> iter = seFromGroup.handleEntries.iterator(); iter.hasNext();)
        {
            HandleEntry he = iter.next();

            // add this HandleEntry to new ServiceEntry
            seToGroup.handleEntries.add(he);

            // assign ServiceEntry to HandleEntry
            he.serviceEntry = seToGroup;

            System.out.println(_name + ": " + he.serviceName + ":" + he.itemName
                    + " was moved to group " + toGroup);
        }

        // all items have been merged to another group
        seFromGroup.handleEntries.clear();
    }

    // apply status to every item in the group
    private void applyStatus(String serviceName, OMMItemGroup fromGroup, OMMState groupStatus)
    {
        Map<OMMItemGroup, GroupEntry> groups = (Map<OMMItemGroup, GroupEntry>)_services
                .get(serviceName);

        // no watching item for this service
        if (groups == null)
            return;

        System.out.println(_name + ": applying group status " + groupStatus + " to group "
                + fromGroup + " for items in " + serviceName);

        GroupEntry ge = (GroupEntry)groups.get(fromGroup);

        // no watching item in this group
        if (ge == null)
        {
            System.out.println("There is no item in " + fromGroup + " group.");
            return;
        }

        for (Iterator<HandleEntry> seIter = ge.handleEntries.iterator(); seIter.hasNext();)
        {
            HandleEntry he = seIter.next();

            System.out.println("\t" + he.itemName);

            // Don't need to call unregister(handle)
            if (groupStatus.isFinal())
            {
                _handles.remove(he.handle);
                System.out.println("\t" + he.itemName + " was closed");
            }

        }

        if (groupStatus.isFinal())
            groups.remove(fromGroup);
    }

    // apply status to every item in the service
    private void applyStatus(String serviceName, OMMState status)
    {
        Map<OMMItemGroup, GroupEntry> groups = (Map<OMMItemGroup, GroupEntry>)_services
                .get(serviceName);

        // no watching item for this service
        if (groups == null)
            return;

        System.out.println(_name + ": " + "applying status " + status + " for items in "
                + serviceName);

        for (Iterator<GroupEntry> iter = groups.values().iterator(); iter.hasNext();)
        {
            GroupEntry ge = iter.next();

            // no watching item in this group
            if (ge == null)
                continue;

            for (Iterator<HandleEntry> seIter = ge.handleEntries.iterator(); seIter.hasNext();)
            {
                HandleEntry he = seIter.next();

                System.out.println("\t" + he.itemName);

                // Don't need to call unregister(handle)
                if (status.isFinal())
                {
                    _handles.remove(he.handle);
                    System.out.println("\t" + he.itemName + " was closed");
                }
            }
        }

        if (status.isFinal())
            _services.remove(serviceName);
    }

    /**
     * Add an item request handle to a watchlist.
     * 
     * @param serviceName
     * @param itemName
     * @param itemHandle The handle received from
     *            {@link com.reuters.rfa.session.omm.OMMConsumer#registerClient(com.reuters.rfa.common.EventQueue, com.reuters.rfa.common.InterestSpec, Client, Object)
     *            registerClient()} or
     *            {@link com.reuters.rfa.session.omm.OMMConsumer#reissueClient(Handle, com.reuters.rfa.common.InterestSpec)
     *            reissueClient()}
     */
    public void addItem(String serviceName, String itemName, Handle itemHandle)
    {
        Map<OMMItemGroup, GroupEntry> map =
                (Map<OMMItemGroup, GroupEntry>)_services.get(serviceName);
        
        if (map == null)
            _services.put(serviceName, new HashMap<OMMItemGroup, GroupEntry>());

        // Pending requests will not be put into _services.
        // In this way, directory update with group filter will not be applied
        // to pending requests
        HandleEntry he = new HandleEntry();
        he.handle = itemHandle;
        he.serviceName = serviceName;
        he.itemName = itemName;
        _handles.put(itemHandle, he);
    }

    /**
     * Apply this group to this handle.
     * 
     * @param itemHandle The same handle passed to
     *            {@link #addItem(String, String, Handle) addItem()}
     * @param group The {@link com.reuters.rfa.omm.OMMItemGroup OMMItemGroup}
     *            received from {@link com.reuters.rfa.omm.OMMMsg OMMMsg}
     */
    public void applyGroup(Handle itemHandle, OMMItemGroup group)
    {
        HandleEntry he = (HandleEntry)_handles.get(itemHandle);

        // Make sure that we call addItem() after we send item request
        if (he == null)
            return;

        Map<OMMItemGroup, GroupEntry> groups = (Map<OMMItemGroup, GroupEntry>)_services
                .get(he.serviceName);

        // Make sure that we call addItem() after we send item request
        if (groups == null)
            return;

        GroupEntry ge = (GroupEntry)groups.get(group);
        if (ge == null)
        {
            ge = new GroupEntry();
            ge.groupId = group;
            groups.put(group, ge);
        }

        assignGroup(he, ge);

        System.out.println(_name + ": " + he.serviceName + ":" + he.itemName
                + " was assigned to group " + group);
    }

    private void assignGroup(HandleEntry he, GroupEntry se)
    {
        // add this HandleEntry to new ServiceEntry
        se.handleEntries.add(he);

        // remove this HandleEntry from old ServiceEntry
        // receive status response with group
        if (he.serviceEntry != null)
        {
            he.serviceEntry.handleEntries.remove(he);
        }

        // assign ServiceEntry to HandleEntry
        he.serviceEntry = se;
    }

    /**
     * Remove a specific item.
     * 
     * @param itemHandle The handle you passed in from
     *            {@link #addItem(String, String, Handle) addItem()}
     */
    public void removeItem(Handle itemHandle)
    {
        HandleEntry he = (HandleEntry)_handles.remove(itemHandle);

        // we already remove this item
        if (he == null)
            return;

        if (he.serviceEntry != null && he.serviceEntry.handleEntries != null)
        {
            he.serviceEntry.handleEntries.remove(he);
        }
    }

    /**
     * Remove all items in the service.
     * 
     * @param serviceName
     */
    public void removeService(String serviceName)
    {
        Map<OMMItemGroup, GroupEntry> groups = (Map<OMMItemGroup, GroupEntry>)_services
                .get(serviceName);
        if (groups == null)
            return;

        // close every item in this service
        for (Iterator<Map.Entry<OMMItemGroup, GroupEntry>> iter = groups.entrySet().iterator(); iter
                .hasNext();)
        {
            Map.Entry<OMMItemGroup, GroupEntry> entry = iter.next();
            GroupEntry se = (GroupEntry)entry.getValue();

            // there is no item in this service
            if (se.handleEntries == null)
                continue;

            for (Iterator<HandleEntry> handleIter = se.handleEntries.iterator(); handleIter
                    .hasNext();)
            {
                HandleEntry he = handleIter.next();
                _handles.remove(he.handle);
            }
        }

        _services.remove(serviceName);
    }
}
