package com.reuters.rfa.example.framework.sub;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.config.ConfigDb;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMFilterList;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMSeries;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMDictionary;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * OMMSubAppContext is an utility class used to perform the routine tasks that
 * are specific to an OMM Subscriber. For example:
 * <ul>
 * <li>creating a session</li>
 * <li>creating an OMMConsumer Event Source</li>
 * <li>sending login and directory requests</li>
 * <li>processing Events received</li>
 * <li>loading dictionaries from file if fileDictionary is set to true</li>
 * </ul>
 */
public class OMMSubAppContext extends SubAppContext
{
    static public void addCommandLineOptions()
    {
        CommandLine.addOption("rdmFieldDictionary", "/var/triarch/RDMFieldDictionary",
                              "RDMFieldDictionary filename");
        CommandLine.addOption("requestPARSupport", true,
                              "Send login request for support pause and resume");
    }

    public OMMSubAppContext(AppContextMainLoop mainLoop)
    {
        super(mainLoop);
        createSession();
        _pool = OMMPool.create();
        _loginInfo = new LoginInfo();
        _ommConsumer = (OMMConsumer)_session.createEventSource(EventSource.OMM_CONSUMER,
                                                               "Consumer", false);
        login();
        _loadedDictionaries = new LinkedList<String>();
        _services = new HashMap<String, ServiceInfo>();
        _pendingDictionaries = new HashMap<Handle, String>();
        _isComplete = false;
        _encoder = _pool.acquireEncoder();
        _encoder.initialize(OMMTypes.MSG, 1000);
        _spec = new OMMItemIntSpec();
        _dictionary = FieldDictionary.create();
        _dictHandles = new HashMap<Handle, Integer>();

        boolean fileDictionary = CommandLine.booleanVariable("fileDictionary");
        if (fileDictionary)
            loadDictionary();
    }

    public OMMSubAppContext(AppContextMainLoop mainLoop, ConfigDb configDb)
    {
        super(mainLoop, configDb);
        createSession();
        _pool = OMMPool.create();
        _loginInfo = new LoginInfo();
        _ommConsumer = (OMMConsumer)_session.createEventSource(EventSource.OMM_CONSUMER,
                                                               "Consumer", false);
        login();
        _loadedDictionaries = new LinkedList<String>();
        _services = new HashMap<String, ServiceInfo>();
        _pendingDictionaries = new HashMap<Handle, String>();
        _isComplete = false;
        _encoder = _pool.acquireEncoder();
        _encoder.initialize(OMMTypes.MSG, 1000);
        _spec = new OMMItemIntSpec();
        _dictionary = FieldDictionary.create();
        _dictHandles = new HashMap<Handle, Integer>();

        boolean fileDictionary = CommandLine.booleanVariable("fileDictionary");
        if (fileDictionary)
            loadDictionary();
    }

    public NormalizedEvent getNormalizedEvent(Event event)
    {
        if (event.getType() != Event.OMM_ITEM_EVENT)
            throw new IllegalArgumentException("Event must be OMMItemEvent");
        return new OMMNormalizedEvent(_dictionary, (OMMItemEvent)event);
    }

    public ServiceInfo getServiceInfo(String serviceName)
    {
        return _services.get(serviceName);
    }

    public LoginInfo getLoginInfo()
    {
        return _loginInfo;
    }

    @SuppressWarnings("unchecked")
    // dictionary toNameMap returns map
    public Map<String, FidDef> getDictionary()
    {
        if (_dictionaryMap == null)
        {
            if (_dictionary == null)
                _dictionaryMap = null;
            else
                _dictionaryMap = _dictionary.toNameMap();
        }

        return _dictionaryMap;
    }

    public FieldDictionary getFieldDictionary()
    {
        return _dictionary;
    }

    /**
     * Register an {@link com.reuters.rfa.rdm.RDMMsgTypes#MARKET_PRICE
     * MARKET_PRICE} interest
     */
    public Handle register(Client client, EventQueue queue, String serviceName, String itemName, boolean streaming)
    {
        return register(client, queue, serviceName, itemName, streaming, RDMMsgTypes.MARKET_PRICE);
    }

    /**
     * Register an interest for other model type
     */
    public Handle register(Client client, String serviceName, String itemName, boolean streaming, short msgModelType)
    {
        return register(client, _eventQueue, serviceName, itemName, streaming, msgModelType);
    }

    public void unregister(Handle handle)
    {
        _ommConsumer.unregisterClient(handle);
    }

    public void cleanup()
    {
        _ommConsumer.unregisterClient(_directoryHandle);
        _ommConsumer.unregisterClient(_loginHandle);
        _ommConsumer.unregisterClient(null);
        _ommConsumer.destroy();
        _session.release();
        super.cleanup();
        Context.uninitialize();
    }

    /*
     * The processEvent() method handles Events corresponding to Login,
     * Directory and Dictionary message model types.
     */
    public void processEvent(Event event)
    {
        OMMMsg msg = ((OMMItemEvent)event).getMsg();
        switch (msg.getMsgModelType())
        {
            case RDMMsgTypes.LOGIN:
                processLoginMsg(msg);
                break;
            case RDMMsgTypes.DIRECTORY:
                processDirectoryMsg(msg);
                break;
            case RDMMsgTypes.DICTIONARY:
                processDictionaryMsg(msg, event.getHandle());
                break;
            default:
        }
    }

    protected void processLoginMsg(OMMMsg msg)
    {
        GenericOMMParser.parse(msg);
        OMMAttribInfo att = msg.getAttribInfo();
        if (msg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP
                && att.getAttribType() != OMMTypes.NO_DATA)
        {
            _loginInfo.update(att);
        }
        else
        {
            _loginInfo.update(att);
            _directoryHandle = registerDirectory(this);
        }
    }

    /*
     * After downloading the dictionaries, process the dictionaries.
     */
    protected void processDictionaryMsg(OMMMsg msg, Handle handle)
    {
        _printStream.println("Received dictionary: " + msg.getAttribInfo().getName());

        int msgType = msg.getMsgType();

        // Process only REFRESH_RESP msg
        if (msgType == OMMMsg.MsgType.STATUS_RESP || msgType == OMMMsg.MsgType.UPDATE_RESP)
        {
            GenericOMMParser.parse(msg);
            return;
        }

        OMMSeries series = (OMMSeries)msg.getPayload();

        // rfaj1781 - handle multi part refreshes.
        // cache the dictType since only first part of multi part refresh has
        // summaryData.
        int dictionaryType;
        Integer dictType = _dictHandles.get(handle);
        if (dictType == null)
        {
            // Means don't know the type yet
            dictionaryType = getDictionaryType(series);
            _dictHandles.put(handle, new Integer(dictionaryType));
        }
        else
        {
            dictionaryType = dictType.intValue();
        }

        if (dictionaryType == RDMDictionary.Type.FIELD_DEFINITIONS)
        {
            FieldDictionary.decodeRDMFldDictionary(_dictionary, series);
            if (msg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
            {
                System.out.println("Field Dictionary Refresh is complete");
                _dictHandles.remove(handle);
            }
        }
        else if (dictionaryType == RDMDictionary.Type.ENUM_TABLES)
        {
            FieldDictionary.decodeRDMEnumDictionary(_dictionary, series);
            if (msg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
            {
                System.out.println("Enum Dictionary Refresh is complete");
                _dictHandles.remove(handle);
            }
        }

        if (msg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
        {
            String dictionaryName = _pendingDictionaries.remove(handle);
            _loadedDictionaries.add(dictionaryName);
            if (_pendingDictionaries.isEmpty() && !_isComplete)
            {
                _isComplete = true;
                if (_client != null)
                    _client.processComplete();
            }
            // set the dictionary to the parser, we need dictionary to parse
            // field list
            GenericOMMParser.initializeDictionary(_dictionary);
        }
    }

    /*
     * Process the directory response message
     */
    protected void processDirectoryMsg(OMMMsg msg)
    {
        GenericOMMParser.parse(msg);

        if (msg.getDataType() == OMMTypes.NO_DATA)
        {
            // probably status
            return;
        }
        if (msg.getDataType() != OMMTypes.MAP)
        {

            _printStream.println("Directory payload must be a Map");
            return;
        }

        Set<String> dictionariesUsed = new HashSet<String>();
        OMMMap map = (OMMMap)msg.getPayload();
        for (Iterator<?> miter = map.iterator(); miter.hasNext();)
        {
            boolean isNew = false;
            OMMMapEntry mentry = (OMMMapEntry)miter.next();
            String serviceName = mentry.getKey().toString();
            if (mentry.getAction() == OMMMapEntry.Action.ADD)
            {
                RDMServiceInfo service = (RDMServiceInfo)_services.get(serviceName);
                if (service == null)
                {
                    service = new RDMServiceInfo(serviceName);
                    _services.put(serviceName, service);
                    isNew = true;
                }

                OMMFilterList flist = (OMMFilterList)mentry.getData();
                service.process(flist);

                if (_directoryClient != null)
                {
                    if (isNew)
                        _directoryClient.processNewService(service);
                    else
                        _directoryClient.processServiceUpdated(service);
                }

                String[] dictArray = (String[])service.get(RDMService.Info.DictionariesUsed);
                if (dictArray != null)
                    for (int i = 0; i < dictArray.length; i++)
                        dictionariesUsed.add(dictArray[i]);
            }
            else if (mentry.getAction() == OMMMapEntry.Action.UPDATE)
            {
                RDMServiceInfo service = (RDMServiceInfo)_services.get(serviceName);
                if (service == null)
                    continue;
                OMMFilterList flist = (OMMFilterList)mentry.getData();
                service.process(flist);

                if (_directoryClient != null)
                {
                    _directoryClient.processServiceUpdated(service);
                }

                String[] dictArray = (String[])service.get(RDMService.Info.DictionariesUsed);
                for (int i = 0; i < dictArray.length; i++)
                    dictionariesUsed.add(dictArray[i]);
            }
            else
            // (mentry.getAction() == OMMMapEntry.DELETE_ACTION)
            {
                ServiceInfo si = _services.remove(serviceName);
                if (_directoryClient != null)
                    _directoryClient.processServiceRemoved(si);
            }
        }

        // download the dictionary used
        if (_autoDictionaryDownload)
        {
            getDictionaries(dictionariesUsed);
        }

        if (_pendingDictionaries.isEmpty() && !_isComplete
                && (_serviceName.length() == 0 || _services.containsKey(_serviceName)))
        {
            _isComplete = true;
            if (_client != null)
                _client.processComplete();
        }

    }

    /*
     * Create a login request message and register the login request
     */
    private void login()
    {
        String username = CommandLine.variable("user");
        OMMItemIntSpec spec = new OMMItemIntSpec();
        spec.setMsg(encodeLogin(username, RDMUser.NameType.USER_NAME, OMMMsg.Indication.REFRESH));
        _loginHandle = _ommConsumer.registerClient(_eventQueue, spec, this, null);
        _loginInfo.setHandle(_loginHandle);
    }

    private OMMMsg encodeLogin(String userName, short nameType, int indication)
    {
        String position = CommandLine.variable("position");
        String applicationId = CommandLine.variable("application");

        OMMEncoder encoder = _pool.acquireEncoder();
        encoder.initialize(OMMTypes.MSG, 1000);
        OMMMsg msg = _pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.LOGIN);
        msg.setAttribInfo(null, userName, nameType);
        if (indication != 0)
            msg.setIndicationFlags(indication);

        encoder.encodeMsgInit(msg, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);
        encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        encoder.encodeElementEntryInit(RDMUser.Attrib.ApplicationId, OMMTypes.ASCII_STRING);
        encoder.encodeString(applicationId, OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit(RDMUser.Attrib.Position, OMMTypes.ASCII_STRING);
        encoder.encodeString(position, OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit(RDMUser.Attrib.Role, OMMTypes.UINT);
        encoder.encodeUInt(RDMUser.Role.CONSUMER);
        if (CommandLine.booleanVariable("requestPARSupport"))
        {
            encoder.encodeElementEntryInit(RDMUser.Attrib.SupportPauseResume, OMMTypes.UINT);
            encoder.encodeUInt(1);
        }
        encoder.encodeAggregateComplete();

        return (OMMMsg)encoder.getEncodedObject();
    }

    /*
     * Encode the directory message and register the client
     */
    private Handle registerDirectory(Client client)
    {
        OMMItemIntSpec spec = new OMMItemIntSpec();
        OMMMsg msg = _pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.DIRECTORY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        OMMAttribInfo ai = _pool.acquireAttribInfo();
        if (_serviceName.length() > 0)
            ai.setServiceName(_serviceName);
        ai.setFilter(RDMService.Filter.INFO | RDMService.Filter.STATE);
        msg.setAttribInfo(ai);
        spec.setMsg(msg);
        Handle handle = _ommConsumer.registerClient(_eventQueue, spec, client, null);
        return handle;
    }

    /*
     * Create a dictionary request message and register the request
     */
    private void openFullDictionary(String serviceName, String dictionaryName)
    {
        OMMItemIntSpec spec = new OMMItemIntSpec();
        OMMMsg msg = _pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH | OMMMsg.Indication.NONSTREAMING);
        OMMAttribInfo ai = _pool.acquireAttribInfo();
        ai.setServiceName(serviceName);
        ai.setName(dictionaryName);
        ai.setFilter(RDMDictionary.Filter.NORMAL);
        msg.setAttribInfo(ai);
        spec.setMsg(msg);
        Handle handle = _ommConsumer.registerClient(_eventQueue, spec, this, null);
        _pool.releaseMsg(msg);
        _pendingDictionaries.put(handle, dictionaryName);
    }

    /*
     * get all the dictionaries included in the argument Set
     */
    private void getDictionaries(Set<String> dictionariesUsed)
    {
        for (Iterator<String> iter = dictionariesUsed.iterator(); iter.hasNext();)
        {
            String dictionaryName = iter.next();
            if (!_loadedDictionaries.contains(dictionaryName)
                    && !_pendingDictionaries.containsValue(dictionaryName))
            {
                // find which service provides the dictionary
                String serviceName = findServiceForDictionary(dictionaryName);
                if (serviceName == null)
                {
                    _printStream.println("No service provides dictionary: " + dictionaryName);
                    continue;
                }
                openFullDictionary(serviceName, dictionaryName);
            }
        }
    }

    /*
     * Find a service that provides the dictionary with name dictionaryName
     */
    private String findServiceForDictionary(String dictionaryName)
    {
        for (Iterator<ServiceInfo> iter = _services.values().iterator(); iter.hasNext();)
        {
            ServiceInfo service = iter.next();

            // stop, if serviceState is DOWN (0) or not available
            Object oServiceState = service.get(RDMService.SvcState.ServiceState);
            if(oServiceState == null)
            	continue;
            int serviceState = Integer.parseInt((String) oServiceState);
            if( serviceState == 0 )
            	continue;
            
            // stop, if acceptRequests is false (0) or not available
            Object oAcceptingRequests = service.get(RDMService.SvcState.ServiceState);
            if(oAcceptingRequests == null)
            	continue;
            int acceptingRequests = Integer.parseInt((String) oAcceptingRequests);
            if( acceptingRequests == 0 )
            	continue;

            String[] dictionariesProvided = (String[])service
                    .get(RDMService.Info.DictionariesProvided);
            if (dictionariesProvided == null)
                continue;
            
            for (int i = 0; i < dictionariesProvided.length; i++)
            {
                if (dictionariesProvided[i].equals(dictionaryName))
                    return service.getServiceName();
            }
        }
        return null;
    }

    /*
     * loadDictionary is called when commandLine argument fileDictionary is true
     */
    private void loadDictionary()
    {
        try
        {
            String fieldfilename = CommandLine.variable("rdmFieldDictionary");
            FieldDictionary.readRDMFieldDictionary(_dictionary, fieldfilename);
            _printStream.println("Loaded RDM Field Dicitonary: " + fieldfilename);
            _loadedDictionaries.add("RWFFld");
            String enumfilename = CommandLine.variable("enumType");
            FieldDictionary.readEnumTypeDef(_dictionary, enumfilename);
            _printStream.println("Loaded Enum Dicitonary: " + enumfilename);
            _loadedDictionaries.add("RWFEnum");
        }
        catch (DictionaryException e)
        {
            _printStream.println(e.getMessage());
            if (e.getCause() != null)
                _printStream.println('\t' + e.getCause().getMessage());

        }
    }

    /*
     * Register an item request.
     */
    private Handle register(Client client, EventQueue queue, String serviceName, String itemName,
            boolean streaming, short msgModelType)
    {
        return register(client, queue, serviceName, itemName, streaming, msgModelType, (byte)0, 0);
    }

    /*
     * Register an item request with specify Priority value.
     */
    public Handle register(Client client, EventQueue queue, String serviceName, String itemName,
            boolean streaming, short msgModelType, byte priorityClass, int priorityCount)
    {
        OMMMsg msg = _pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        if (streaming)
            msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        else
            msg.setIndicationFlags(OMMMsg.Indication.NONSTREAMING
                                   | OMMMsg.Indication.REFRESH);
        msg.setMsgModelType(msgModelType);
        msg.setAttribInfo(serviceName, itemName, RDMInstrument.NameType.RIC);
        if (priorityClass + priorityCount > 0)
            msg.setPriority(priorityClass, priorityCount);
        _spec.setMsg(msg);
        Handle handle = _ommConsumer.registerClient(queue, _spec, client, null);
        _pool.releaseMsg(msg);
        return handle;
    }

    /*
     * Reissue an item request
     */
    public void reissue(Handle handle, String serviceName, String itemName, byte msgType,
            short msgModelType, int indicationFlags, byte priorityClass, int priorityCount)
    {
        OMMMsg msg = _pool.acquireMsg();
        msg.setMsgType(msgType);
        msg.setMsgModelType(msgModelType);
        msg.setAttribInfo(serviceName, itemName, RDMInstrument.NameType.RIC);
        msg.setPriority(priorityClass, priorityCount);
        msg.setIndicationFlags(indicationFlags);
        _spec.setMsg(msg);
        _ommConsumer.reissueClient(handle, _spec);
        _pool.releaseMsg(msg);
    }

    /*
     * Reissue a login request.
     */
    public void reissueLogin(String userName, short nameType, int indication)
    {
        OMMItemIntSpec spec = new OMMItemIntSpec();
        spec.setMsg(encodeLogin(userName, nameType, indication));
        _ommConsumer.reissueClient(_loginHandle, spec);
    }

    /*
     * Reissue a login, for pause all/resume all.
     */
    public void reissueLogin(int indication)
    {
        OMMItemIntSpec spec = new OMMItemIntSpec();
        spec.setMsg(encodeLogin(CommandLine.variable("user"), RDMUser.NameType.USER_NAME,
                                indication));
        _ommConsumer.reissueClient(_loginHandle, spec);
    }

    /*
     * Parse the series for the dictionary type
     */
    private int getDictionaryType(OMMSeries series)
    {
        OMMElementList elist = (OMMElementList)series.getSummaryData();
        OMMElementEntry eentry = elist.find(RDMDictionary.Summary.Type);
        if (eentry != null)
        {
            return (int)((OMMNumeric)eentry.getData()).toLong();
        }
        return 0;
    }

    public void addNewService(String serviceName)
    {
        ServiceInfo service = new RDMServiceInfo(serviceName);
        _services.put(serviceName, service);
        if (_directoryClient != null)
        {
            _directoryClient.processNewService(service);
        }
    }

    private final OMMPool _pool;
    private final LoginInfo _loginInfo;
    OMMConsumer _ommConsumer;
    Handle _loginHandle;
    Handle _directoryHandle;
    Map<Handle, String> _pendingDictionaries;
    List<String> _loadedDictionaries;
    FieldDictionary _dictionary;
    private HashMap<Handle, Integer> _dictHandles; // Key handle : Value
                                                   // dictionaryType - rfaj1781
    Map<String, FidDef> _dictionaryMap;
    Map<String, ServiceInfo> _services;
    OMMEncoder _encoder;
    OMMItemIntSpec _spec;
}
