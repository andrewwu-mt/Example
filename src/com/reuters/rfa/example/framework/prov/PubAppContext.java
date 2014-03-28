package com.reuters.rfa.example.framework.prov;

import java.io.PrintStream;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.config.ConfigUtil;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.framework.sub.AppContextMainLoop;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.TimerIntSpec;
import com.reuters.rfa.session.omm.OMMItemCmd;
import com.reuters.rfa.session.omm.OMMProvider;

/**
 * PubAppContext is an abstract class which performs several operations that a
 * publisher application must perform before it can publish items. This class is
 * able to publish only an open message model.
 * 
 * <p>
 * In order to use PubAppContext, at startup {@link #addCommandLineOptions()}
 * must be called to setup command line options. Each domain that is added to
 * PubAppContext need to call <code>addCommandLineOptions()</code> to set
 * service ready to be use.
 * </p>
 * 
 * <p>
 * <b>This is the example code that demonstrate how to use PubAppContext.</b>
 * </p>
 * 
 * <pre>
 * public static void main(String[] args)
 * {
 * 
 *     // setup the command line, this line is required
 *     PubAppContext.addCommandLineOptions();
 *     CommandLine.setArguments(args);
 * 
 *     // create a new instance
 *     AppContextMainLoop mainLoop = new AppContextMainLoop(null);
 *     PubAppContext appContext = PubAppContext.create(mainLoop);
 * 
 *     // create the domain manager such as Dictionary manager and add to
 *     // PubAppContext.
 *     DictionaryMgr _dictionaryMgr = new DictionaryMgr(appContext, &quot;&lt;serviceName&gt;&quot;);
 *     appContext.addDomainMgr(_dictionaryMgr);
 * 
 *     // initialize PubAppContext
 *     appContext.init();
 * 
 *     // only dictionary domain call autoDictionary() to load dictionary from
 *     // files.
 *     _dictionaryMgr.autoDictionary();
 * 
 *     // each domain call indicateServiceInitialized() to set service ready to be
 *     // use.
 *     _dictionaryMgr.indicateServiceInitialized();
 * 
 *     // dispatching event mainloop
 *     // dispatch until timeout has expired if -runTime is configured. if not,
 *     // dispatch infinitely
 *     mainLoop.run();
 * 
 *     // cleaning up
 *     appContext.cleanup();
 * }
 * </pre>
 * 
 * </p>
 * 
 * @see com.reuters.rfa.example.framework.sub.AppContextMainLoop
 * @see com.reuters.rfa.example.framework.prov.DictionaryMgr
 * @see com.reuters.rfa.example.utility.CommandLine
 */
public abstract class PubAppContext
{
    /**
     * Creates PubAppContext.
     * 
     * @param mainloop
     * @return PubAppContext.
     */
    public static PubAppContext create(AppContextMainLoop mainloop)
    {
        return new ServerPubAppContext(mainloop);
    }

    /**
     * Adds essential command line arguments.
     * 
     */
    public static void addCommandLineOptions()
    {
        CommandLine.addOption("debug", false, "enable debug tracing");
        CommandLine.addOption("provSession", "myNamespace::provSession", "Session name to use");
        CommandLine.addOption("rdmFieldDictionary", "/var/triarch/RDMFieldDictionary",
                              "RDMFieldDictionary path and filename");
        CommandLine.addOption("enumType", "/var/triarch/enumtype.def", "enumtype.def filename");
        CommandLine.addOption("listenenerName", "", "listener name for the session");
        CommandLine.addOption("acceptSessions", true, "accept all sessions");
        CommandLine.addOption("vendor", "RFAExample", "vendor name");
        CommandLine.addOption("pubServiceName", "MY_SERVICE", "service name used by provider");
        CommandLine.addOption("isSource", false, "is this original source of data");
        AppContextMainLoop.addCommandLineOptions();
    }

    OMMProvider _provider;
    Session _session;
    AppContextMainLoop _mainloop;
    FieldDictionary _dictionary;
    OMMPool _pool;
    OMMEncoder _encoder;
    OMMItemCmd _itemcmd;
    protected boolean _loginSuccess;
    protected DirectoryMgr _directoryHandler;

    public PubAppContext()
    {
        _itemcmd = new OMMItemCmd();
    }

    /**
     * Creates dictionary domain manager used by this PubAppContext. The
     * dictionary domain manager is created only one time.
     * 
     * @return created dictionary domain manager
     */
    public DirectoryMgr directoryMgr()
    {
        if (_directoryHandler == null)
        {
            String vendor = CommandLine.variable("vendor");
            boolean isSource = CommandLine.booleanVariable("isSource");
            _directoryHandler = new DirectoryMgr(this, vendor, isSource);
        }
        return _directoryHandler;
    }

    public PrintStream getPrintStream()
    {
        return _mainloop.getPrintStream();
    }

    public EventQueue getEventQueue()
    {
        return _mainloop.getEventQueue();
    }

    /**
     * @return the OMM encoder
     */
    public OMMEncoder getEncoder()
    {
        return _encoder;
    }

    /**
     * @return the OMM pool
     */
    public OMMPool getPool()
    {
        return _pool;
    }

    /**
     * @return {@link com.reuters.rfa.dictionary.FieldDictionary
     *         FieldDictionary} read from files.
     */
    public FieldDictionary getFieldDictionary()
    {
        return _dictionary;
    }

    public OMMProvider getProvider()
    {
        return _provider;
    }

    public void setDictionary(FieldDictionary dictionary)
    {
        _dictionary = dictionary;
    }

    /**
     * Sends OMM message to a client.
     * 
     * @param token the client request token
     * @param msg the OMM message to submit
     */
    public void submit(Token token, OMMMsg msg)
    {
        _itemcmd.setToken(token);
        _itemcmd.setMsg(msg);
        _provider.submit(_itemcmd, null);
    }

    /**
     * Cleanup the SubAppContext. The application should call this method when
     * the PubAppContext is not needed anymore to clean up resources.
     */
    public void cleanup()
    {
        _provider.destroy();
        _session.release();
        Context.uninitialize();
    }

    public void init()
    {
        Context.initialize();

        String sessionName = CommandLine.variable("provSession");
        
        // prior to acquiring the session, update the provider connection
        // to use the request message type (OMMMsg.MsgType.REQUEST) rather 
        // than the deprecated request message types (see OMMMsg.MsgType).
        ConfigUtil.useDeprecatedRequestMsgs(sessionName, false);
        
        _session = Session.acquire(sessionName);

        if (_session == null)
        {
            System.out.println("Could not acquire session.");
            System.exit(1);
        }

        _provider = (OMMProvider)_session.createEventSource(EventSource.OMM_PROVIDER, "OMMProvider");
    }

    /**
     * Adds a domain manager to this application context.
     * 
     * @param mgr a provider domain manager.
     */
    public void addDomainMgr(ProvDomainMgr mgr)
    {
        ServiceInfo si = directoryMgr().addServiceInfo(mgr.getServiceName());
        si.addCapability(mgr.getMsgModelType());
        ((AbstractProvDomainMgr)mgr).addDictionariesUsed(si);
    }

    public Handle registerTimerTask(Client task, long delay, boolean repeating)
    {
        TimerIntSpec timerInt = new TimerIntSpec();
        timerInt.setDelay(delay);
        timerInt.setRepeating(repeating);

        Handle handle = getProvider().registerClient(getEventQueue(), timerInt, task, null);
        return handle;
    }

    public void unregisterTimer(Handle handle)
    {
        getProvider().unregisterClient(handle);
    }

    /**
     * Returns a provider domain manager for the message model type.
     * 
     * @param msgModelType a message model type
     * @return a provider domain manager
     */
    abstract public ProvDomainMgr getDomainMgr(short msgModelType);

}
