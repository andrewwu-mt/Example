package com.reuters.rfa.example.framework.sub;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.StandardPrincipalIdentity;
import com.reuters.rfa.common.TokenizedPrincipalIdentity;
import com.reuters.rfa.dictionary.DictionaryConverter;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.session.DataDictInfo;
import com.reuters.rfa.session.DataDictInfo.DictType;
import com.reuters.rfa.session.MarketDataDictSub;
import com.reuters.rfa.session.MarketDataItemSub;
import com.reuters.rfa.session.MarketDataSubscriber;
import com.reuters.rfa.session.MarketDataSubscriberInterestSpec;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.event.ConnectionEvent;
import com.reuters.rfa.session.event.MarketDataDictEvent;
import com.reuters.rfa.session.event.MarketDataDictStatus;
import com.reuters.rfa.session.event.MarketDataItemEvent;
import com.reuters.rfa.session.event.MarketDataSvcEvent;
import com.reuters.tibmsg.TibException;
import com.reuters.tibmsg.TibMsg;

/**
 * MarketDataSubAppContext is an utility class used to perform the routine tasks
 * that are specific to a MarketData Subscriber application. For example:
 * <ul>
 * <li>creating a session</li>
 * <li>setting up InterestSpec</li>
 * <li>creating an MarketDataSubscriber Event Source</li>
 * <li>loading dictionaries from file if fileDictionary is set to true</li>
 * </ul>
 */
public class MarketDataSubAppContext extends SubAppContext
{
    static public void addCommandLineOptions()
    {
        CommandLine.addOption("mounttpi", false, "use TokenizedPrincipalIdentity for Session");
        CommandLine.addOption("appendixaFilename", "/var/triarch/appendix_a",
                              "appendix_a path and filename");
    }

    @SuppressWarnings("deprecation")
	public MarketDataSubAppContext(AppContextMainLoop mainLoop)
    {
        super(mainLoop);
        createSession();

        // The usage of a StandardPrincipalIdentity when creating an EventSource is deprecated starting in 7.2
        // and will be removed completely in a future release. 
        _marketDataSubscriber = (MarketDataSubscriber)_session
                .createEventSource(EventSource.MARKET_DATA_SUBSCRIBER, "Subscriber", false,
                                   _standardPI);

        _loadedDictionaries = new LinkedList<Object>();
        _pendingDictionaries = new HashMap<DictType, DataDictInfo>();
        _services = new HashMap<String, ServiceInfo>();
        boolean fileDictionary = CommandLine.booleanVariable("fileDictionary");
        _dictionary = FieldDictionary.create();
        if (fileDictionary)
            loadDictionary();
        MarketDataSubscriberInterestSpec marketDataSubscriberInterestSpec = new MarketDataSubscriberInterestSpec();
        marketDataSubscriberInterestSpec.setMarketDataSvcInterest(true);
        marketDataSubscriberInterestSpec.setConnectionInterest(true);
        marketDataSubscriberInterestSpec.setEntitlementsInterest(false);
        _mdsClientHandle = _marketDataSubscriber.registerClient(_eventQueue,
                                                                marketDataSubscriberInterestSpec,
                                                                this, null);
    }

    public NormalizedEvent getNormalizedEvent(Event event)
    {
        if (event.getType() != Event.MARKET_DATA_ITEM_EVENT)
            throw new IllegalArgumentException("Event must be MarketDataItemEvent");
        return new MarketDataNormalizedEvent(_dictionary, (MarketDataItemEvent)event);
    }

    /*
     * override from SubAppContext because we need to acquire session with
     * tokenizedPI
     */
    public void createSession()
    {
        String sessionName = CommandLine.variable("session");
        _mounttpi = CommandLine.booleanVariable("mounttpi");
        createPrincipals();

        if (_mounttpi)
            _session = Session.acquire(sessionName, _tokenizedPI);
        else
            _session = Session.acquire(sessionName);
        if (_session == null)
        {
            _printStream.println("Could not acquire session.");
            System.exit(1);
        }
        else
            _printStream.println("Successfully acquired session: " + sessionName);
    }

    public FieldDictionary getFieldDictionary()
    {
        return _dictionary;
    }

    @SuppressWarnings("unchecked")
    public Map<String, FidDef> getDictionary()
    {
        if (_dictionaryMap == null)
        {
            if (_dictionary == null)
                initFieldDictionaryFromTibMsg();
            if (_dictionary != null)
                _dictionaryMap = (Map<String, FidDef>)_dictionary.toNameMap();
        }
        return _dictionaryMap;
    }

    private void initFieldDictionaryFromTibMsg()
    {
        try
        {
            if (TibMsg.GetMfeedDictionary() == null)
                return;
            DictionaryConverter.initializeFromTibMfeedDict(_dictionary);
        }
        catch (TibException te)
        {
            _printStream.println("Could not convert TibMfeedDict to FieldDictionary: "
                    + te.getMessage());
            return;
        }
    }

    public Handle register(Client client, EventQueue queue, String serviceName, String itemName,
            boolean streaming)
    {
        MarketDataItemSub sub = new MarketDataItemSub();
        sub.setItemName(itemName);
        sub.setServiceName(serviceName);
        sub.setSnapshotReq(!streaming);
        return _marketDataSubscriber.subscribe(queue, sub, client, null);
    }

    public void unregister(Handle handle)
    {
        _marketDataSubscriber.unsubscribe(handle);
    }

    public void cleanup()
    {
        _marketDataSubscriber.unregisterClient(_mdsClientHandle);
        _marketDataSubscriber.unsubscribeAll();
        _marketDataSubscriber.destroy();
        _session.release();
        super.cleanup();
        Context.uninitialize();
    }

    private void createPrincipals()
    {
        String user = CommandLine.variable("user");
        String position = CommandLine.variable("position");
        String application = CommandLine.variable("application");

        _tokenizedPI = new TokenizedPrincipalIdentity();
        String tokenString = user;
        if (!application.equals(""))
        {
            tokenString = tokenString + "+" + application;
            if (!position.equals(""))
                tokenString = tokenString + "+" + position;
        }
        _tokenizedPI.setBuffer(tokenString.getBytes());
        _standardPI = new StandardPrincipalIdentity();
        _standardPI.setName(user);
        _standardPI.setPosition(position);
        _standardPI.setAppName(application);
    }

    /*
     * Subscribe for dictionary
     */
    private void requestDictionaries(DataDictInfo[] dataDictInfo)
    {
        for (int i = 0; i < dataDictInfo.length; ++i)
        {
            if (dataDictInfo[i].getDictType() == DataDictInfo.UNKNOWN)
                continue;
            if (!_loadedDictionaries.contains(dataDictInfo[i].getDictType())
                    && !_pendingDictionaries.containsKey(dataDictInfo[i].getDictType()))
            {
                _printStream.println("Requesting dictionary \"" + dataDictInfo[i].getDictType() + "\"");
                _pendingDictionaries.put(dataDictInfo[i].getDictType(), dataDictInfo[i]);
                MarketDataDictSub marketDataDictSub = new MarketDataDictSub();
                marketDataDictSub.setDataDictInfo(dataDictInfo[i]);
                _marketDataSubscriber.subscribe(_eventQueue, marketDataDictSub, this, null);
            }
        }
    }

    /*
     * loadDictionary is called when commandLine argument fileDictionary is true
     */
    private void loadDictionary()
    {
        _dictionary = FieldDictionary.create();
        try
        {
            String fieldfilename = CommandLine.variable("appendixaFilename");
            FieldDictionary.readAppendixA(_dictionary, fieldfilename);
            _printStream.println("Loaded Appendix_A Field Dicitonary: " + fieldfilename);
            String enumfilename = CommandLine.variable("enumType");
            FieldDictionary.readEnumTypeDef(_dictionary, enumfilename);
            _printStream.println("Loaded Enum Dicitonary: " + enumfilename);
            _loadedDictionaries.add(DataDictInfo.MARKETFEED);
        }
        catch (DictionaryException e)
        {
            _printStream.println(e.getMessage());
            if (e.getCause() != null)
                _printStream.println('\t' + e.getCause().getMessage());
            return;
        }

        try
        {
            DictionaryConverter.toTibMsgMfeedDict(_dictionary);
        }
        catch (TibException te)
        {
            _printStream.println("Could not load TibMsg Marketfeed dictionary from FieldDictionary: "
                            + te.getMessage());
        }
    }

    /*
     * Callback from RFA
     */
    public void processEvent(Event event)
    {
        _printStream.println(new Date());
        switch (event.getType())
        {
            case Event.MARKET_DATA_SVC_EVENT:
                processMarketDataSvcEvent((MarketDataSvcEvent)event);
                break;
            case Event.MARKET_DATA_DICT_EVENT:
                processMarketDataDictEvent((MarketDataDictEvent)event);
                break;
            case Event.CONNECTION_EVENT:
                ConnectionEvent connectionEvent = (ConnectionEvent)event;
                _printStream.print("Received CONNECTION_EVENT: "
                        + connectionEvent.getConnectionName());
                _printStream.println("  " + connectionEvent.getConnectionStatus().toString());
                _printStream.println(connectionEvent.getConnectionStatus().getStatusText());
                break;
            case Event.COMPLETION_EVENT:
                _printStream.println("Received COMPLETION_EVENT for handle " + event.getHandle());
                break;
            default:
                _printStream.println("MarketDataSubAppContext.processEvent: unhandled event type: "
                        + event.getType());
                break;
        }
    }

    /*
     * DirectoryClient.processServiceRemoved doesn't apply to MarketData domain
     */
    private void processMarketDataSvcEvent(MarketDataSvcEvent event)
    {
        _printStream.println("Received MARKET_DATA_SVC_EVENT: " + event + "\n");
        _printStream.println(event.getStatus().getStatusText());
        ServiceInfo si = (ServiceInfo)_services.get(event.getServiceName());
        if (si != null)
        {
            if (_directoryClient != null)
                _directoryClient.processServiceUpdated(si);
        }
        else
        {
            si = new MarketDataServiceInfo(event.getServiceName());
            // could store these properties in MarketDataServiceInfo
            // DataDictInfo[] ddi = event.getDataDictInfo();
            // QualityOfServiceInfo[] qosi = event.getQualityOfServiceInfo();
            // MarketDataSvcStatus status = event.getStatus();

            _services.put(event.getServiceName(), si);
            if (_directoryClient != null)
                _directoryClient.processNewService(si);
        }

        // download the dictionary used
        if (_autoDictionaryDownload)
        {
            requestDictionaries(event.getDataDictInfo());
        }

        if (!_isComplete && (_serviceName.length() == 0 || _services.containsKey(_serviceName))
                && _pendingDictionaries.isEmpty())
        {
            _isComplete = true;
            if (_client != null)
                _client.processComplete();
        }
    }

    private void processMarketDataDictEvent(MarketDataDictEvent marketDataDictEvent)
    {
        _printStream.println("Received MARKET_DATA_DICT_EVENT: "
                + marketDataDictEvent.getDataDictInfo().getDictType());
        if (marketDataDictEvent.getStatus().getState() != MarketDataDictStatus.OK)
        {
            _printStream.println("Data dictionary request failed: "
                    + marketDataDictEvent.getStatus().getStatusText());
            return;
        }

        try
        {
            if (marketDataDictEvent.getDataDictInfo().getDictType() == DataDictInfo.MARKETFEED)
            {
                if (TibMsg.GetMfeedDictionary() == null) // only unpack
                                                         // dictionary once
                {
                    TibMsg tibMsg = new TibMsg();
                    tibMsg.UnPack(marketDataDictEvent.getData());
                    TibMsg.UnPackMfeedDictionary2(tibMsg);
                    _printStream.println("Marketfeed Dictionary unpacked.");
                }
            }
            else if (marketDataDictEvent.getDataDictInfo().getDictType() == DataDictInfo.SASS)
            {
                if (TibMsg.GetDataDictionary() == null) // only unpack
                                                        // dictionary once
                {
                    TibMsg tibMsg = new TibMsg();
                    tibMsg.UnPack(marketDataDictEvent.getData());
                    TibMsg.UnPackDataDictionary(tibMsg);
                    _printStream.println("SASS Dictionary unpacked.");
                }
            }

            DataDictInfo ddinfo = (DataDictInfo)_pendingDictionaries.remove(marketDataDictEvent.getDataDictInfo().getDictType());
            _loadedDictionaries.add(ddinfo);
            initFieldDictionaryFromTibMsg();

            if (!_isComplete && (_serviceName.length() == 0 || _services.containsKey(_serviceName))
                    && _pendingDictionaries.isEmpty())
            {
                _isComplete = true;
                if (_client != null)
                {
                    _client.processComplete();
                }
            }
        }
        catch (TibException te)
        {
            _printStream.println("Cannot unpack dictionary: " + te.getMessage());
        }

    }

    public void addNewService(String serviceName)
    {
        ServiceInfo service = new MarketDataServiceInfo(serviceName);
        _services.put(serviceName, service);
        if (_directoryClient != null)
        {
            _directoryClient.processNewService(service);
        }
    }

    Handle _mdsClientHandle;
    List<Object> _loadedDictionaries;
    Map<DictType, DataDictInfo> _pendingDictionaries;
    Map<String, ServiceInfo> _services;
    FieldDictionary _dictionary;
    Map<String, FidDef> _dictionaryMap;
    MarketDataSubscriber _marketDataSubscriber;
    boolean _mounttpi;
    StandardPrincipalIdentity _standardPI;
    TokenizedPrincipalIdentity _tokenizedPI;
}
