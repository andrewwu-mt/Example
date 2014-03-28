package com.reuters.rfa.example.omm.domainServer;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

import com.reuters.rfa.example.framework.prov.AbstractProvDomainMgr;
import com.reuters.rfa.example.framework.prov.DictionaryMgr;
import com.reuters.rfa.example.framework.prov.LoginMgr;
import com.reuters.rfa.example.framework.prov.PubAppContext;
import com.reuters.rfa.example.framework.sub.AppContextMainLoop;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.rdm.RDMMsgTypes;

/**
 * DomainServer is the main class of provider application which publishes
 * Reuters Domain Model data using the Open Message Model. This example uses
 * PubAppContext, the provider framework, to publish the Open Message Model
 * data.
 * 
 * <p>
 * <b>DomainServer is responsible for:</b>
 * </p>
 * <ul>
 * <li>Reads input parameter from commamd line argument.
 * <li>Creates DataGenerator for each message model type.
 * <li>Creates DictionaryMgr to provide Dictionary domain.
 * <li>Creates LoginMgr and LoginAuthenticator for handle Login request.
 * <li>Creates the other domains based on command line argument.
 * <li>Initialize PubAppContext.
 * </ul>
 * 
 * <p>
 * <b>The following are the currently available command line configuration
 * parameters.</b>
 * </p>
 * <ul>
 * <li><b>debug</b> - Enable debug tracing. (default is &quot;false&quot;)
 * <li><b>provSession</b> - Session name to use. (default is
 * &quot;myNamespace::provSession&quot;)
 * <li><b>rdmFieldDictionary</b> - RDMFieldDictionary path and filename.
 * (default is &quot;/var/triarch/RDMFieldDictionary&quot;)
 * <li><b>enumType</b> - enumtype.def filename. (default is
 * &quot;/var/triarch/enumtype.def&quot;)
 * <li><b>listenenerName</b> - Listener name for the session. (default is
 * &quot;&quot;)
 * <li><b>acceptSessions</b> - Accept all sessions. (default is
 * &quot;true&quot;)
 * <li><b>vendor</b> - Vendor name. (default is &quot;RFAExample&quot;)
 * <li><b>pubServiceName</b> - Service name used by provider. (default is
 * &quot;MY_SERVICE&quot;)
 * <li><b>isSource</b> - Is this original source of data. (default is
 * &quot;false&quot;)
 * <li><b>runTime</b> - How long application should run before exiting in
 * seconds (default is &quot;-1&quot;)
 * <li><b>mmt</b> - List of domains separated by ',' that supported by the
 * server. (default is &quot;MARKET_BY_ORDER,MARKET_BY_PRICE,MARKET_MAKER&quot;)
 * <li><b>&lt;domain name&gt;_updateInterval</b>
 * example:MARKET_BY_ORDER_updateInterval - Interval time in seconds between
 * each update message. (default is &quot;3&quot;)
 * <li><b>&lt;domain name&gt;_encodeDataDef</b>
 * example:MARKET_BY_ORDER_encodeDataDef - Enabled option to optimized bandwidth
 * by using DataDefinitions. (default is &quot;true&quot;)
 * <li><b>SYMBOL_LIST_totalSymbols</b> - Total number of symbols in the Symbol
 * List. (default is &quot;25&quot;)
 * <li><b>validUsers</b> - List of valid users that allow to request an item,
 * separated by ',' (default is &quot;all&quot; which allow everyone)
 * </ul>
 * 
 * @see com.reuters.rfa.example.omm.domainServer.RDMProvDomainMgr
 * @see com.reuters.rfa.example.omm.domainServer.DataGenerator
 * @see com.reuters.rfa.example.framework.prov
 * 
 */
public class DomainServer
{

    PubAppContext _pubContext;
    AppContextMainLoop _mainloop;
    DictionaryMgr _dictionaryMgr;
    LoginMgr _loginMgr;

    ArrayList<AbstractProvDomainMgr> _domainMgrs = new ArrayList<AbstractProvDomainMgr>();
    String _serviceName = null;
    static HashMap<String, String> _dataGeneratorClassMap;

    static
    {
        _dataGeneratorClassMap = new HashMap<String, String>();
        _dataGeneratorClassMap.put(RDMMsgTypes.toString(RDMMsgTypes.MARKET_PRICE),
                     "com.reuters.rfa.example.omm.domainServer.marketprice.MarketPriceGenerator");
        _dataGeneratorClassMap.put(RDMMsgTypes.toString(RDMMsgTypes.MARKET_BY_ORDER),
                     "com.reuters.rfa.example.omm.domainServer.marketbyorder.MarketByOrderGenerator");
        _dataGeneratorClassMap.put(RDMMsgTypes.toString(RDMMsgTypes.MARKET_BY_PRICE),
                     "com.reuters.rfa.example.omm.domainServer.marketbyprice.MarketByPriceGenerator");
        _dataGeneratorClassMap.put(RDMMsgTypes.toString(RDMMsgTypes.MARKET_MAKER),
                     "com.reuters.rfa.example.omm.domainServer.marketmaker.MarketMakerGenerator");
        _dataGeneratorClassMap.put(RDMMsgTypes.toString(RDMMsgTypes.SYMBOL_LIST),
                     "com.reuters.rfa.example.omm.domainServer.symbollist.SymbolListGenerator");
    }

    DomainServer()
    {

        _mainloop = new AppContextMainLoop(System.out);
        _pubContext = PubAppContext.create(_mainloop);
        initDomainMgr();
        _dictionaryMgr = new DictionaryMgr(_pubContext, _serviceName);
        _loginMgr = new LoginMgr(_pubContext);
        _loginMgr.setSupportPAR(false);
        _loginMgr.setAuthenticator(new LoginAuthenticatorImpl());
    }

    void initDomainMgr()
    {

        _serviceName = CommandLine.variable("pubServiceName");
        String domainMgrNames = CommandLine.variable("mmt");

        StringTokenizer dmgr = new StringTokenizer(domainMgrNames, ",");
        String domainName = null;

        while (dmgr.hasMoreTokens())
        {
            domainName = dmgr.nextToken().trim();
            _domainMgrs.add(createDomainMgr(domainName, _serviceName));
        }
    }

    private AbstractProvDomainMgr createDomainMgr(String domainMgrName, String serviceName)
    {

        String dataGeneratorClzStr = (String)_dataGeneratorClassMap.get(domainMgrName);
        if (dataGeneratorClzStr == null)
        {
            System.out.println(domainMgrName + " domain is not support.");
            System.exit(1);
        }
        short msgModelType = RDMMsgTypes.msgModelType(domainMgrName);
        DataGenerator dataGenerator = null;

        try
        {
            Class<?> dataGeneratorClz = Class.forName(dataGeneratorClzStr);
            Constructor<?> constructor = dataGeneratorClz.getConstructor((Class[])null);
            dataGenerator = (DataGenerator)constructor.newInstance((Object[])null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        int updateInterval = CommandLine.intVariable(domainMgrName + "_updateInterval");

        System.out.println("Initializing ... " + domainMgrName + " domain [" + serviceName + "]");
        return new RDMProvDomainMgr(_pubContext, dataGenerator, msgModelType, serviceName,
                updateInterval);
    }

    void init()
    {

        _pubContext.init();
        _dictionaryMgr.autoDictionary();

        Set<String> initializaedServices = new HashSet<String>();

        for (Iterator<AbstractProvDomainMgr> iterator = _domainMgrs.iterator(); iterator.hasNext();)
        {
            AbstractProvDomainMgr domain = iterator.next();
            if (!initializaedServices.contains(domain.getServiceName()))
            {
                System.out.print("[" + domain.getServiceName() + "] ");
                domain.indicateServiceInitialized();
                initializaedServices.add(domain.getServiceName());
            }
        }
        _mainloop.run();
    }

    void cleanup()
    {
        System.out.println("Cleaning up resources....");
        _pubContext.cleanup();
    }

    static public void addCommandLineOptions()
    {
        PubAppContext.addCommandLineOptions();
        CommandLine.addOption("mmt",
                           "MARKET_PRICE,MARKET_BY_ORDER,MARKET_BY_PRICE,MARKET_MAKER,SYMBOL_LIST	",
                           "List of comma separated domains supported by the server");

        String updateIntervalText = "Interval time in seconds between each update message";
        CommandLine.addOption("MARKET_PRICE_updateInterval", 3, updateIntervalText
                + " for MarketPrice domain");
        CommandLine.addOption("MARKET_BY_ORDER_updateInterval", 3, updateIntervalText
                + " for MarketByOrder domain");
        CommandLine.addOption("MARKET_BY_PRICE_updateInterval", 3, updateIntervalText
                + " for MarketByPrice domain");
        CommandLine.addOption("MARKET_MAKER_updateInterval", 3, updateIntervalText
                + " for MarketMaker domain");
        CommandLine.addOption("SYMBOL_LIST_updateInterval", 0, updateIntervalText
                + " for SymbolList domain");

        String encodeDataDefText = "Enabled option to optimized bandwidth by using DataDefinitions";
        CommandLine.addOption("MARKET_BY_ORDER_encodeDataDef", true, encodeDataDefText
                + " for MarketByOrder domain");
        CommandLine.addOption("MARKET_BY_PRICE_encodeDataDef", true, encodeDataDefText
                + " for MarketByPrice domain");
        CommandLine.addOption("MARKET_MAKER_encodeDataDef", true, encodeDataDefText
                + " for MarketMaker domain");
        CommandLine.addOption("SYMBOL_LIST_encodeDataDef", true, encodeDataDefText
                + " for SymbolList domain");
        CommandLine.addOption("SYMBOL_LIST_totalSymbols", 25,
                              "Total number of symbols in the Symbol List");
        CommandLine.addOption("validUsers", "all",
                           "List of valid users that allow to request an item, separated by ',' Default (\"all\") allow everyone");
    }

    public static void main(String[] argv)
    {
        DomainServer.addCommandLineOptions();
        CommandLine.setArguments(argv);

        DomainServer server = new DomainServer();
        server.init();
        server.cleanup();
    }
}
