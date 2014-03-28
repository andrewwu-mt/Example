package com.reuters.rfa.example.omm.hybrid.advanced;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMItemGroup;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMState;

/**
 * An ItemGroupManager keeps track of every item request and item groups. It is
 * not responsible for requesting and closing item. To use this class, first you
 * need to call
 * {@link com.reuters.rfa.example.omm.hybrid.advanced.DirectoryClient#sendRequest()
 * sendRequest()} to register for directory stream. Then after every item
 * request, you pass the handle you received to
 * {@link #addItem(OMMMsg, Handle, Token) addItem()}. Finally every refresh or
 * status that has {@link com.reuters.rfa.omm.OMMMsg#HAS_ITEM_GROUP
 * HAS_ITEM_GROUP} flag set you call {@link #applyGroup(Handle, OMMItemGroup)
 * applyGroup()}.
 */
public class ItemGroupManager
{
    AdvancedSessionClient _parent;
    Handle _directoryHandle;
    private String _instanceName;

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
        public Token token;

        public String serviceName;
        public String itemName;
        short msgModelType;
        OMMAttribInfo attribInfo;

        public GroupEntry serviceEntry;
    }

    public ItemGroupManager(AdvancedSessionClient consumerAppContext)
    {
        _parent = consumerAppContext;
        _instanceName = "[ItemGroupManager #" + _parent._instanceNumber + "] ";

        _services = new HashMap<String, HashMap<OMMItemGroup, GroupEntry>>();
        _handles = new HashMap<Handle, HandleEntry>();
    }

    HandleEntry getHandleEntry(Handle handle)
    {
        return (HandleEntry)_handles.get(handle);
    }

    void mergeGroup(String serviceName, OMMItemGroup fromGroup, OMMItemGroup toGroup)
    {
        Map<OMMItemGroup, GroupEntry> groups = (Map<OMMItemGroup, GroupEntry>)_services
                .get(serviceName);

        // no watching item for this service
        if (groups == null)
            return;

        GroupEntry seFromGroup = (GroupEntry)groups.get(fromGroup);

        // no item in this group, no need to merge
        if (seFromGroup == null)
            return;

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

            System.out.println(_instanceName + he.serviceName + ":" + he.itemName
                    + " was moved to group " + toGroup);
        }

        // all items have been merged to another group
        seFromGroup.handleEntries.clear();

    }

    // apply status to every item in the group
    public void applyStatus(String serviceName, OMMItemGroup fromGroup, OMMState groupStatus)
    {
        Map<OMMItemGroup, GroupEntry> groups = (Map<OMMItemGroup, GroupEntry>)_services
                .get(serviceName);

        // no watching item for this service
        if (groups == null)
            return;

        System.out.println(_instanceName + "applying group status " + groupStatus + " to group "
                + fromGroup + " for items in " + serviceName);

        GroupEntry ge = (GroupEntry)groups.get(fromGroup);

        // no wachting item in this group
        if (ge == null)
            return;

        for (Iterator<HandleEntry> seIter = ge.handleEntries.iterator(); seIter.hasNext();)
        {
            HandleEntry he = seIter.next();

            System.out.println("\t" + he.itemName);

            _parent.submitItemStatus(he, he.token, groupStatus);

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
    public void applyStatus(String serviceName, OMMState status)
    {
        Map<OMMItemGroup, GroupEntry> groups = (Map<OMMItemGroup, GroupEntry>)_services
                .get(serviceName);

        // no watching item for this service
        if (groups == null)
            return;

        System.out.println(_instanceName + "applying status " + status + " for items in "
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

                _parent.submitItemStatus(he, he.token, status);

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
     */
    public void addItem(OMMMsg msg, Handle itemHandle, Token requestToken)
    {

        OMMAttribInfo attribInfo = msg.getAttribInfo();

        OMMAttribInfo newAttribInfo = _parent._pool.acquireAttribInfo();

        HandleEntry entry = new HandleEntry();
        entry.handle = itemHandle;
        entry.token = requestToken;
        entry.msgModelType = msg.getMsgModelType();

        if (attribInfo.has(OMMAttribInfo.HAS_NAME))
        {
            newAttribInfo.setName(attribInfo.getName());
            entry.itemName = attribInfo.getName();
        }
        if (attribInfo.has(OMMAttribInfo.HAS_SERVICE_NAME))
        {
            newAttribInfo.setServiceName(attribInfo.getServiceName());
            entry.serviceName = attribInfo.getServiceName();
        }
        if (attribInfo.has(OMMAttribInfo.HAS_NAME_TYPE))
        {
            newAttribInfo.setNameType(attribInfo.getNameType());
        }

        entry.attribInfo = newAttribInfo;

        if (entry.serviceName != null)
        {
            Map<OMMItemGroup, GroupEntry> groups = (Map<OMMItemGroup, GroupEntry>)_services
                    .get(entry.serviceName);
            if (groups == null)
                _services.put(entry.serviceName, new HashMap<OMMItemGroup, GroupEntry>());

            // Pending requests will not be put into _services.
            // In this way, directory update with group filter will not be
            // applied to pending requests

            _handles.put(itemHandle, entry);
        }
    }

    /**
     * Remove a specific item.
     * 
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
            GroupEntry ge = (GroupEntry)entry.getValue();

            // there is no item in this service
            if (ge.handleEntries == null)
                continue;

            for (Iterator<HandleEntry> handleIter = ge.handleEntries.iterator(); handleIter
                    .hasNext();)
            {
                HandleEntry he = handleIter.next();
                _handles.remove(he.handle);
            }
        }

        _services.remove(serviceName);
    }

    /**
     * Apply this group to this handle.
     * 
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

        System.out.println(_instanceName + ": " + he.serviceName + ":" + he.itemName
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
     * Clean up resource.
     * 
     */
    public void cleanup()
    {
        _handles.clear();
        _services.clear();
    }
}
