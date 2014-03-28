package com.reuters.rfa.example.omm.hybrid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMSeries;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMDictionary;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * Handle dictionary requests and responses. DictionaryManager is a helper class
 * used for handling dictionary requests and responses.
 * {@link #addDictionaryClient(RequestClient, DictionaryClient, OMMMsg)
 * addDictionaryClient()} is used to open a new dictionary request. Before
 * making any request, the DictionaryManager will check if the dictionary
 * requested has already been loaded either from a file or from network. If the
 * dictionary has not yet been loaded, the DictionaryManager will request
 * dictionary with filter {@link com.reuters.rfa.rdm.RDMDictionary.Filter#INFO
 * INFO}. Once the response is back, it will check if full dictionary is
 * required. If yes, it will request dictionary with filter
 * {@link com.reuters.rfa.rdm.RDMDictionary.Filter#NORMAL NORMAL}.
 * 
 */

public class DictionaryManager
{
    Map<DictionaryClient, RequestClientHandle> _clients;

    OMMPool _pool;

    Map<Dictionary, byte[]> _loadedDictionaries; // key=Dictionary, value=byte[]
    Map<Dictionary, List<RequestClientHandle>> _pendingDictionaries; // key=Dictionary,
                                                                     // value=list
                                                                     // of
                                                                     // RequestClientHandles

    Map<String, Service> _services; // key=name, value=Service
    String _instanceName;

    /**
     * Represents a handle between a DictionaryClient and a DictionaryManager
     */
    static class RequestClientHandle implements Client
    {
        RequestClient requestClient;
        DictionaryClient dictionaryClient;

        OMMAttribInfo requestedAttribInfo;
        Handle requestHandle;

        String serviceName;
        Dictionary dictionary;

        boolean active;
        boolean streaming;
        boolean needAttribInfo;

        DictionaryManager manager;

        OMMMsg reqMsg;
        OMMMsg respMsg;
        OMMEncoder encoder;

        boolean complete; // need a FieldDictionary

        RequestClientHandle(DictionaryManager mgr, RequestClient rc, DictionaryClient dc, OMMMsg msg)
        {
            requestClient = rc;
            dictionaryClient = dc;
            active = true;
            manager = mgr;

            requestedAttribInfo = manager._pool.acquireCopy(msg.getAttribInfo(), true);
            dictionary = new Dictionary(requestedAttribInfo.getName(), "", 0, 0);
            serviceName = requestedAttribInfo.getServiceName();
            needAttribInfo = msg.isSet(OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES);

            complete = false;
            streaming = !msg.isSet(OMMMsg.Indication.NONSTREAMING);
        }

        void setComplete()
        {
            complete = true;
        }

        // open a dictionary info
        void openInfo()
        {
            if (reqMsg == null)
                reqMsg = manager._pool.acquireMsg();

            reqMsg.clear();
            reqMsg.setMsgType(OMMMsg.MsgType.REQUEST);
            reqMsg.setMsgModelType(RDMMsgTypes.DICTIONARY);
            reqMsg.setIndicationFlags(OMMMsg.Indication.NONSTREAMING
                                      | OMMMsg.Indication.REFRESH);
            OMMAttribInfo ai = manager._pool.acquireAttribInfo();
            ai.setServiceName(serviceName);
            ai.setName(dictionary.dictionaryName);
            ai.setFilter(RDMDictionary.Filter.INFO);
            reqMsg.setAttribInfo(ai);
            OMMItemIntSpec intSpec = new OMMItemIntSpec();
            intSpec.setMsg(reqMsg);

            requestHandle = requestClient.getConsumer()
                    .registerClient(requestClient.getEventQueue(), intSpec, this, null);
        }

        // open a full dictionary
        void openFull()
        {
            if (reqMsg == null)
                reqMsg = manager._pool.acquireMsg();

            reqMsg.clear();
            reqMsg.setMsgType(OMMMsg.MsgType.REQUEST);
            reqMsg.setMsgModelType(RDMMsgTypes.DICTIONARY);
            reqMsg.setIndicationFlags(OMMMsg.Indication.NONSTREAMING
                                      | OMMMsg.Indication.REFRESH);
            OMMAttribInfo ai = manager._pool.acquireAttribInfo();
            ai.setServiceName(serviceName);
            ai.setName(dictionary.dictionaryName);
            ai.setFilter(RDMDictionary.Filter.NORMAL);
            reqMsg.setAttribInfo(ai);
            OMMItemIntSpec intSpec = new OMMItemIntSpec();
            intSpec.setMsg(reqMsg);

            requestClient.getConsumer().registerClient(requestClient.getEventQueue(), intSpec,
                                                       this, null);
        }

        void processDictionaryInfo(OMMMsg msg)
        {
            if (!msg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
            {
                System.out.println(manager._instanceName + " Not support multi-part refresh");
                return;
            }

            OMMSeries series = (OMMSeries)msg.getPayload();

            if (!series.has(OMMSeries.HAS_SUMMARY_DATA))
            {
                System.out.println(manager._instanceName
                        + " Dictionary info must contain summary data.");
                return;
            }

            OMMElementList summary = (OMMElementList)series.getSummaryData();

            OMMEntry summaryEntry = summary.find(RDMDictionary.Summary.Version);
            if (summaryEntry != null)
                dictionary.dictionaryVersion = summaryEntry.getData().toString();
            summaryEntry = summary.find(RDMDictionary.Summary.DictionaryId);
            if (summaryEntry != null)
                dictionary.dictionaryId = (int)((OMMNumeric)summaryEntry.getData()).toLong();
            summaryEntry = summary.find(RDMDictionary.Summary.Type);
            if (summaryEntry != null)
                dictionary.dictionaryType = (int)((OMMNumeric)summaryEntry.getData()).toLong();

            // if the dictionary has alread been loaded, we don't need to
            // request a full dictionary
            if (manager.isLoaded(dictionary))
            {
                processResponse(manager.getRawDictionary(dictionary));
                return;
            }

            manager.addPendingRequest(this);

            int filter = msg.getAttribInfo().getFilter();
            if ((filter & RDMDictionary.Filter.NORMAL) == RDMDictionary.Filter.NORMAL
                    || (filter & RDMDictionary.Filter.VERBOSE) == RDMDictionary.Filter.VERBOSE)
            {
                processFullDictionary(msg);
            }

        }

        public void processEvent(Event event)
        {
            switch (event.getType())
            {
                case Event.OMM_ITEM_EVENT:
                {
                    OMMItemEvent ie = (OMMItemEvent)event;
                    OMMMsg msg = ie.getMsg();

                    if (msg.getMsgModelType() != RDMMsgTypes.DICTIONARY)
                    {
                        System.out.println(manager._instanceName + " Expected DICTIONARY");
                        return;
                    }

                    if (msg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
                    {
                        int filter = msg.getAttribInfo().getFilter();
                        if ((filter & RDMDictionary.Filter.INFO) == RDMDictionary.Filter.INFO)
                        {
                            processDictionaryInfo(msg);
                        }
                        else
                        {
                            processFullDictionary(msg);
                        }
                    }
                    else if (msg.getMsgType() == OMMMsg.MsgType.STATUS_RESP)
                    {
                        processStatusResponse(msg);
                    }
                    break;
                }
                default:
                    break;
            }
        }

        void processFullDictionary(OMMMsg msg)
        {
            if (msg.getMsgType() != OMMMsg.MsgType.REFRESH_RESP)
            {
                System.out.println(manager._instanceName + " Expected refresh response");
                return;
            }

            if (!msg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
            {
                System.out.println(manager._instanceName + " Not support multi-part refresh");
                return;
            }

            // cache dictionary
            byte[] rawDictionary = msg.getPayload().getBytes();
            manager.cache(this, rawDictionary);
            manager.flushPending(dictionary, rawDictionary);
        }

        void processResponse(byte[] rawDictionary)
        {
            if (!active)
                return;

            if (complete)
            {
                if (manager.hasDictionary(serviceName))
                {
                    dictionaryClient.processDictionaryComplete(manager.getDictionary(serviceName),
                                                               serviceName);
                }

                return;
            }

            if (respMsg == null)
                respMsg = manager._pool.acquireMsg();

            if (encoder == null)
                encoder = manager._pool.acquireEncoder();

            respMsg.clear();
            respMsg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
            respMsg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
            respMsg.setMsgModelType(RDMMsgTypes.DICTIONARY);
            respMsg.setState(OMMState.Stream.CLOSED, OMMState.Data.OK, OMMState.Code.NONE, "");

            respMsg.setAttribInfo(requestedAttribInfo);

            if (requestedAttribInfo.has(OMMAttribInfo.HAS_FILTER)
                    && requestedAttribInfo.getFilter() == RDMDictionary.Filter.INFO)
            {
                // send only INFO
                encoder.initialize(OMMTypes.MSG, 2000);
                encoder.encodeMsgInit(respMsg, OMMTypes.NO_DATA, OMMTypes.SERIES);
                manager.encodeInfoDictionary(dictionary, encoder);

            }
            else
            {
                // send everything
                boolean growEncoderFlag = false;
                int encoderSize = rawDictionary.length + 2000;

                do
                {
                    try
                    {
                        encoder.initialize(OMMTypes.MSG, encoderSize);
                        encoder.encodeMsgInit(respMsg, OMMTypes.NO_DATA, OMMTypes.SERIES);
                        manager.encodeFullDictionary(dictionary, encoder, rawDictionary);
                        growEncoderFlag = false;
                    }
                    catch (IndexOutOfBoundsException e)
                    {
                        // Not enough room, need to grow the encoderSize and try
                        // submit again.
                        growEncoderFlag = true;
                        encoderSize = (int)(encoderSize * 1.1);
                    }
                }
                while (growEncoderFlag);
            }

            OMMMsg readOnlyMsg = (OMMMsg)encoder.getEncodedObject();

            dictionaryClient.processDictionaryResponse(readOnlyMsg);
        }

        void processStatusResponse(OMMMsg msg)
        {
            OMMState state = msg.getState();

            respMsg.clear();
            respMsg.setMsgType(OMMMsg.MsgType.STATUS_RESP);
            respMsg.setState(state);

            if (needAttribInfo)
                respMsg.setAttribInfo(requestedAttribInfo);

            encoder.initialize(OMMTypes.MSG, 1000);
            encoder.encodeMsg(respMsg);
            OMMMsg readOnlyMsg = (OMMMsg)encoder.getEncodedObject();

            dictionaryClient.processDictionaryResponse(readOnlyMsg);
        }

        public void close()
        {
            active = false;
            if (streaming && requestHandle != null)
            {
                requestClient.getConsumer().unregisterClient(requestHandle);
            }
        }
    }

    static class Service
    {
        List<String> dictionaryUsed; // list of dictionary name that this service uses
        List<String> dictionaryProvided; // list of dictionary name that this service provides
        Map<Dictionary, byte[]> loadedDictionaries; // key=Dictionary, value=byte[]
        FieldDictionary fieldDictionary;
        String serviceName;

        Service(String svcName)
        {
            serviceName = svcName;
        }

        void addDictionaryUsed(String name)
        {
            if (dictionaryUsed == null)
                dictionaryUsed = new ArrayList<String>();

            dictionaryUsed.add(name);
        }

        void addDictionaryProvided(String name)
        {
            if (dictionaryProvided == null)
                dictionaryProvided = new ArrayList<String>();

            dictionaryProvided.add(name);
        }

        boolean isProvided(String name)
        {
            if (dictionaryProvided == null)
                return false;

            return dictionaryProvided.contains(name);
        }

        boolean isLoaded(Dictionary dictionary)
        {
            return loadedDictionaries.containsKey(dictionary);
        }

        void cache(OMMPool pool, Dictionary dictionary, byte[] rawDictionary)
        {
            if (loadedDictionaries == null)
                loadedDictionaries = new HashMap<Dictionary, byte[]>();

            loadedDictionaries.put((Dictionary)dictionary.clone(), rawDictionary);

            if (fieldDictionary == null)
                fieldDictionary = FieldDictionary.create();

            if (dictionary.dictionaryType == RDMDictionary.Type.FIELD_DEFINITIONS)
            {
                FieldDictionary.decodeRDMFldDictionary(fieldDictionary, (OMMSeries)pool
                        .acquireDataFor(OMMTypes.SERIES, rawDictionary));
            }
            else if (dictionary.dictionaryType == RDMDictionary.Type.ENUM_TABLES)
            {
                FieldDictionary.decodeRDMEnumDictionary(fieldDictionary, (OMMSeries)pool
                        .acquireDataFor(OMMTypes.SERIES, rawDictionary));
            }
        }

    }

    static class Dictionary implements Cloneable
    {
        String dictionaryName;
        String dictionaryVersion;
        int dictionaryType;
        int dictionaryId;

        Dictionary(String name, String version, int type, int id)
        {
            dictionaryName = name;
            dictionaryVersion = version;
            dictionaryType = type;
            dictionaryId = id;

            if (dictionaryVersion == null)
                dictionaryVersion = "";

            if (dictionaryName == null)
                dictionaryName = "";
        }

        public boolean equals(Object obj)
        {
            Dictionary info = (Dictionary)obj;

            return (dictionaryName.equals(info.dictionaryName)
                    && dictionaryVersion.equals(info.dictionaryVersion)
                    && dictionaryId == info.dictionaryId && dictionaryType == info.dictionaryType);
        }

        public int hashCode()
        {
            int hc = dictionaryType + dictionaryId;
            hc += dictionaryName.hashCode();
            hc += dictionaryVersion.hashCode();

            return hc;
        }

        public String toString()
        {
            return dictionaryName + "(type=" + dictionaryType + ", id=" + dictionaryId
                    + ", version=" + dictionaryVersion + ")";
        }

        public Object clone()
        {
            return new Dictionary(dictionaryName, dictionaryVersion, dictionaryType, dictionaryId);
        }
    }

    public DictionaryManager()
    {
        _clients = new HashMap<DictionaryClient, RequestClientHandle>();
        _pool = OMMPool.create();
        _loadedDictionaries = new HashMap<Dictionary, byte[]>();
        _pendingDictionaries = new HashMap<Dictionary, List<RequestClientHandle>>();
        _services = new HashMap<String, Service>();

        _instanceName = "[DictionaryManager]";
    }

    /**
     * Add a dictionary name that this service provided.
     * 
     * @param dictionaryName
     * @param serviceName
     */
    public void addDictionaryProvided(String dictionaryName, String serviceName)
    {
        Service service = (Service)_services.get(serviceName);
        if (service == null)
        {
            service = new Service(serviceName);
            _services.put(serviceName, service);
        }
        service.addDictionaryProvided(dictionaryName);
    }

    /**
     * Add a dictionary name that this service used.
     * 
     * @param dictionaryName
     * @param serviceName
     */
    public void addDictionaryUsed(String dictionaryName, String serviceName)
    {
        Service service = (Service)_services.get(serviceName);
        if (service == null)
        {
            service = new Service(serviceName);
            _services.put(serviceName, service);
        }
        service.addDictionaryUsed(dictionaryName);
    }

    /**
     * Add a new DictionaryClient.
     * 
     * @return a handle that represents this request
     */
    public Object addDictionaryClient(RequestClient rc, DictionaryClient dc, OMMMsg requestedMsg)
    {
        OMMAttribInfo attribInfo = requestedMsg.getAttribInfo();
        String serviceName = attribInfo.getServiceName();
        String dictName = attribInfo.getName();

        RequestClientHandle rcHandle = new RequestClientHandle(this, rc, dc, requestedMsg);

        Dictionary dictionary = getDictionary(serviceName, dictName);

        // no need to request a dictionary if we already have one
        if (dictionary != null)
        {
            rcHandle.dictionary = dictionary;
            rcHandle.processResponse(getRawDictionary(dictionary));
            return rcHandle;
        }

        rcHandle.openInfo();

        _clients.put(dc, rcHandle);
        return rcHandle;
    }

    /**
     * Remove the client
     * 
     */
    public void removeDictionaryClient(DictionaryClient dc)
    {
        RequestClientHandle rcHandle = (RequestClientHandle)_clients.remove(dc);
        if (rcHandle != null)
        {
            rcHandle.close();
        }
    }

    /**
     * 
     * @return true if this service has FieldDictionary
     */
    public boolean hasDictionary(String serviceName)
    {
        FieldDictionary fieldDictionary = getDictionary(serviceName);
        return (fieldDictionary != null && fieldDictionary.getEnumTables() != null && fieldDictionary
                .getMaxFieldId() > 0);
    }

    /**
     * @return a FieldDictionary for this service or null if none exists
     */
    public FieldDictionary getDictionary(String serviceName)
    {
        Service service = (Service)_services.get(serviceName);
        if (service == null)
            return null;

        return service.fieldDictionary;
    }

    /**
     * Read FieldDictionary and EnumType dictionary from a file
     */
    public FieldDictionary readDictionary(String fieldDictionary, String enumType)
    {
        FieldDictionary dictionary = null;
        try
        {
            dictionary = FieldDictionary.create();
            FieldDictionary.readRDMFieldDictionary(dictionary, fieldDictionary);
            FieldDictionary.readEnumTypeDef(dictionary, enumType);

            Dictionary fieldDict = new Dictionary(dictionary.getFieldProperty("Name"),
                    dictionary.getFieldProperty("Version"), RDMDictionary.Type.FIELD_DEFINITIONS,
                    dictionary.getDictId());

            OMMEncoder encoder = _pool.acquireEncoder();
            encoder.initialize(OMMTypes.SERIES, 350000);
            FieldDictionary.encodeRDMFieldDictionary(dictionary, encoder);
            _loadedDictionaries.put((Dictionary)fieldDict.clone(), encoder.getBytes());

            Dictionary enumDict = new Dictionary(dictionary.getEnumProperty("Name"),
                    dictionary.getEnumProperty("Version"), RDMDictionary.Type.ENUM_TABLES,
                    dictionary.getDictId());

            encoder.initialize(OMMTypes.SERIES, 350000);
            FieldDictionary.encodeRDMEnumDictionary(dictionary, encoder);
            _loadedDictionaries.put((Dictionary)enumDict.clone(), encoder.getBytes());
        }
        catch (DictionaryException e)
        {
            // e.printStackTrace();
            System.out.println("Dictionary read error: " + e.getMessage());
            if (e.getCause() != null)
                System.err.println(": " + e.getCause().getMessage());
            return null;
        }
        return dictionary;
    }

    /**
     * Request a dictionary for this service
     */
    public boolean requestDictionaryForService(RequestClient rc, DictionaryClient dc,
            String serviceName)
    {
        Service service = (Service)_services.get(serviceName);
        if (service == null)
            return false;

        if (service.dictionaryProvided == null)
            return false;

        boolean ret = true;
        for (Iterator<String> iter = service.dictionaryProvided.iterator(); iter.hasNext();)
        {
            String dictName = (String)iter.next();
            if (service.isProvided(dictName))
            {
                OMMMsg reqMsg = createDictionaryInfoRequestMessage(serviceName, dictName);
                RequestClientHandle rcHandle = (RequestClientHandle)addDictionaryClient(rc, dc,
                                                                                        reqMsg);
                rcHandle.setComplete();
            }
            else
            {
                boolean found = false;
                for (Iterator<Service> otherIter = _services.values().iterator(); otherIter
                        .hasNext();)
                {
                    Service otherService = (Service)otherIter.next();
                    if (otherService.isProvided(dictName))
                    {
                        OMMMsg reqMsg = createDictionaryInfoRequestMessage(otherService.serviceName,
                                                                           dictName);
                        RequestClientHandle rcHandle =
                                (RequestClientHandle)addDictionaryClient(rc, dc, reqMsg);
                        rcHandle.setComplete();
                        found = true;
                        break;
                    }
                }
                if (!found)
                    ret = false;
            }
        }

        return ret;
    }

    void flushPending(Dictionary dictionary, byte[] rawDictionary)
    {

        List<RequestClientHandle> list = (List<RequestClientHandle>)_pendingDictionaries
                .remove(dictionary);
        if (list == null)
            return;

        for (Iterator<RequestClientHandle> iter = list.iterator(); iter.hasNext();)
        {
            RequestClientHandle rcHandle = (RequestClientHandle)iter.next();

            rcHandle.processResponse(rawDictionary);
        }
    }

    void cache(RequestClientHandle rcHandle, byte[] rawDictionary)
    {
        _loadedDictionaries.put((Dictionary)rcHandle.dictionary.clone(), rawDictionary);
        Service service = (Service)_services.get(rcHandle.serviceName);

        // if someone requests the dictionary before the directory has arrived
        if (service == null)
        {
            addDictionaryProvided(rcHandle.dictionary.dictionaryName, rcHandle.serviceName);
            service = (Service)_services.get(rcHandle.serviceName);
        }

        service.cache(_pool, rcHandle.dictionary, rawDictionary);
    }

    boolean isLoaded(Dictionary dict)
    {
        return _loadedDictionaries.containsKey(dict);
    }

    boolean isPending(Dictionary dict)
    {
        return _pendingDictionaries.containsKey(dict);
    }

    byte[] getRawDictionary(Dictionary dict)
    {
        return (byte[])_loadedDictionaries.get(dict);
    }

    Dictionary getDictionary(String serviceName, String dictName)
    {
        Service service = (Service)_services.get(serviceName);
        if (service == null || service.loadedDictionaries == null)
            return null;

        for (Iterator<Dictionary> iter = service.loadedDictionaries.keySet().iterator(); iter
                .hasNext();)
        {
            Dictionary dictionary = (Dictionary)iter.next();
            if (dictionary.dictionaryName.equals(dictName))
                return dictionary;
        }
        return null;
    }

    byte[] getRawDictionary(String serviceName, String dictName)
    {
        Service service = (Service)_services.get(serviceName);
        if (service == null || service.loadedDictionaries == null)
            return null;

        for (Iterator<Dictionary> iter = service.loadedDictionaries.keySet().iterator(); iter
                .hasNext();)
        {
            Dictionary dictionary = (Dictionary)iter.next();
            if (dictionary.dictionaryName.equals(dictName))
                return (byte[])service.loadedDictionaries.get(dictionary);
        }
        return null;
    }

    void addPendingRequest(RequestClientHandle rcHandle)
    {

        List<RequestClientHandle> list = (List<RequestClientHandle>)_pendingDictionaries
                .get(rcHandle.dictionary);
        if (list == null)
        {
            list = new ArrayList<RequestClientHandle>();
            _pendingDictionaries.put((Dictionary)rcHandle.dictionary.clone(), list);
            rcHandle.openFull();
        }

        if (!list.contains(rcHandle))
            list.add(rcHandle);
    }

    void encodeInfoDictionary(Dictionary dictionary, OMMEncoder encoder)
    {
        encoder.encodeSeriesInit(OMMSeries.HAS_SUMMARY_DATA, OMMTypes.ELEMENT_LIST, 0);

        encoder.encodeSummaryDataInit(); // Summary data encoding initialization.
        
        // This element list has standard data.
        encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        
        encoder.encodeElementEntryInit(RDMDictionary.Summary.Version, OMMTypes.ASCII_STRING);
        encoder.encodeString(dictionary.dictionaryVersion, OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit(RDMDictionary.Summary.Type, OMMTypes.UINT);
        encoder.encodeUInt((long)dictionary.dictionaryType);
        encoder.encodeElementEntryInit(RDMDictionary.Summary.DictionaryId, OMMTypes.UINT);
        encoder.encodeUInt((long)dictionary.dictionaryId);
        encoder.encodeAggregateComplete(); // Completes the ElementList

        encoder.encodeAggregateComplete();
    }

    void encodeFullDictionary(Dictionary dictionary, OMMEncoder encoder, byte[] rawDictionary)
    {
        FieldDictionary tmpFieldDictionary = FieldDictionary.create();
        if (dictionary.dictionaryType == RDMDictionary.Type.FIELD_DEFINITIONS)
        {
            FieldDictionary.decodeRDMFldDictionary(tmpFieldDictionary,
                                                   (OMMSeries)_pool.acquireDataFor(OMMTypes.SERIES,
                                                                                   rawDictionary));
            FieldDictionary.encodeRDMFieldDictionary(tmpFieldDictionary, encoder);
        }
        else if (dictionary.dictionaryType == RDMDictionary.Type.ENUM_TABLES)
        {
            FieldDictionary.decodeRDMEnumDictionary(tmpFieldDictionary,
                                                    (OMMSeries)_pool.acquireDataFor(OMMTypes.SERIES,
                                                                                    rawDictionary));
            FieldDictionary.encodeRDMEnumDictionary(tmpFieldDictionary, encoder);
        }
    }

    // create a dictionary info request from service Name and dictionary name
    OMMMsg createDictionaryInfoRequestMessage(String serviceName, String dictionaryName)
    {
        OMMMsg reqMsg = _pool.acquireMsg();
        reqMsg.setMsgType(OMMMsg.MsgType.REQUEST);
        reqMsg.setMsgModelType(RDMMsgTypes.DICTIONARY);
        reqMsg.setIndicationFlags(OMMMsg.Indication.NONSTREAMING);
        OMMAttribInfo ai = _pool.acquireAttribInfo();
        ai.setServiceName(serviceName);
        ai.setName(dictionaryName);
        ai.setFilter(RDMDictionary.Filter.NORMAL);
        reqMsg.setAttribInfo(ai);
        return reqMsg;
    }
}
