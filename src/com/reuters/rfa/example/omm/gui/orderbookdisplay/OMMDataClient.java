package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMSeries;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMDictionary;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;
import com.reuters.rfa.rdm.RDMUser;

import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.dictionary.DictionaryException;

import java.util.Iterator;
import java.util.prefs.Preferences;

public abstract class OMMDataClient implements Client
{
    static final short PROD_PERM = 1;
    static final short CURRENCY = 15;
    static final short TRD_UNITS = 53;
    static final short QUOTIM = 1025;
    static final short QUOTIM_MS = 3855;
    static final short RDN_EXCH = 1709;
    static final short PR_RNK_RUL = 3423;
    static final short MKT_STATE = 5002;
    static final short OR_RNK_RUL = 3425;
    static final short ORDER_ID = 3426;
    static final short ORDER_PRC = 3427;
    static final short ORDER_SIDE = 3428;
    static final short ORDER_SIZE = 3429;

    public abstract void processMarketByOrder(Event event);

    protected OMMItemIntSpec _ommItemIntSpec;
    protected Handle _loginHandle = null;
    protected Handle _dirHandle;
    protected Handle _dictHandle;
    protected EventQueue _eventQueue;
    protected OMMConsumer _ommConsumer;
    protected String myServiceName = null;

    protected FieldDictionary _dataDict;
    protected short _dictLocation;
    private String fieldDictionaryFilename;
    private String enumDictionaryFilename;
    protected Preferences _prefs;

    protected Callback myCallback;

    protected OMMEncoder encoder;
    protected OMMPool pool;

    protected boolean _loggedIn = false;
    protected boolean _connectionUp = false;
    protected boolean _log = false;
    protected boolean _debug = false;

    protected boolean m_bSubscribedToAppendixA = false;
    protected boolean m_bSubscribedToEnumTypeDef = false;

    public OMMDataClient(EventQueue eventQueue, OMMConsumer subscriber, short dictLocation,
            Preferences prefs)
    {
        _prefs = prefs;
        _eventQueue = eventQueue;
        _ommConsumer = subscriber;

        // Create a OMMPool.
        pool = OMMPool.create();

        // Create an OMMEncoder
        encoder = pool.acquireEncoder();

        // Get this from preferences
        fieldDictionaryFilename = "/var/reuters/RDM/RDMFieldDictionary";
        enumDictionaryFilename = "/var/reuters/RDM/enumtype.def";

        _dictLocation = dictLocation;
        _dataDict = FieldDictionary.create();

        // if Local, Load Dictionary
        if (dictLocation == 1)
        {
            try
            {
                FieldDictionary.readRDMFieldDictionary(_dataDict, fieldDictionaryFilename);
                FieldDictionary.readEnumTypeDef(_dataDict, enumDictionaryFilename);
                m_bSubscribedToEnumTypeDef = true;
                m_bSubscribedToAppendixA = true;
            }
            catch (DictionaryException e)
            {
                _dictLocation = 2;
                m_bSubscribedToEnumTypeDef = false;
                m_bSubscribedToAppendixA = false;
            }
        }
        sendRequest(RDMMsgTypes.LOGIN, null);

    }

    void init()
    {
        sendRequest(RDMMsgTypes.LOGIN, null);
    }

    public void setDebug(boolean debug)
    {
        _debug = debug;
    }

    public void setCallback(Callback callback)
    {
        myCallback = callback;
    }

    public void sendRequest(short msgType, String serviceName)
    {
        OMMMsg ommmsg;
        _ommItemIntSpec = new OMMItemIntSpec();

        switch (msgType)
        {
            case (RDMMsgTypes.LOGIN):
                ommmsg = encodeLoginReqMsg();
                _ommItemIntSpec.setMsg(ommmsg);
                System.out.println("LoginManager: Sending login request...");
                _loginHandle = _ommConsumer.registerClient(_eventQueue,
                                                           _ommItemIntSpec, this, null);
                break;
            case (RDMMsgTypes.DIRECTORY):
                ommmsg = encodeSrcDirReqMsg();
                _ommItemIntSpec.setMsg(ommmsg);
                _dirHandle = _ommConsumer.registerClient(_eventQueue, _ommItemIntSpec, this, null);
                break;

            case (RDMMsgTypes.DICTIONARY):
                myServiceName = serviceName;
                encoder.initialize(OMMTypes.MSG, 5000);
                ommmsg = pool.acquireMsg();
                ommmsg.setMsgType(OMMMsg.MsgType.REQUEST);
                ommmsg.setMsgModelType(RDMMsgTypes.DICTIONARY);
                ommmsg.setIndicationFlags(OMMMsg.Indication.REFRESH);

                OMMAttribInfo attribInfo = (OMMAttribInfo)pool.acquireAttribInfo();
                attribInfo.setFilter(RDMDictionary.Filter.NORMAL);

                attribInfo.setName("RWFFld");
                attribInfo.setServiceName(myServiceName);

                ommmsg.setAttribInfo(attribInfo);
                _ommItemIntSpec.setMsg(ommmsg);
                _ommConsumer.registerClient(_eventQueue, _ommItemIntSpec, this, null);
                // *_log<<"Requested FieldDef dictionary " <<endl;
                myCallback.notifyStatus("Requested FieldDef dictionary");

                attribInfo.setName("RWFEnum");
                ommmsg.setAttribInfo(attribInfo);
                _ommItemIntSpec.setMsg(ommmsg);
                _ommConsumer.registerClient(_eventQueue, _ommItemIntSpec, this, null);

                // *_log<<"Requested EnumDef dictionary " <<endl;
                myCallback.notifyStatus("Requested EnumDef dictionary");
                break;
            default:
                // *_log << "Msg model type <" << msgType << "> not handled!! "
                // <<
                // endl;
                myCallback.notifyStatus("Invalid message model");
        }

    }

    private OMMMsg encodeLoginReqMsg()
    {
        encoder.initialize(OMMTypes.MSG, 500);
        OMMMsg msg = pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.LOGIN);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        String userName = _prefs.get("UserName", "guest");
        msg.setAttribInfo(null, userName, RDMUser.NameType.USER_NAME);

        encoder.encodeMsgInit(msg, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);
        encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        String appId = _prefs.get("ApplicationId", "guest");
        encoder.encodeElementEntryInit("ApplicationId", OMMTypes.ASCII_STRING);
        encoder.encodeString(appId, OMMTypes.ASCII_STRING);

        String position = _prefs.get("Position", "localhost");
        encoder.encodeElementEntryInit("Position", OMMTypes.ASCII_STRING);
        encoder.encodeString(position, OMMTypes.ASCII_STRING);
        encoder.encodeAggregateComplete();

        // Get the encoded message from the encoder
        OMMMsg encMsg = (OMMMsg)encoder.getEncodedObject();

        // Release the message that own by the application
        pool.releaseMsg(msg);

        return encMsg; // return the encoded message
    }

    private OMMMsg encodeSrcDirReqMsg()
    {
        encoder.initialize(OMMTypes.MSG, 5000);
        OMMMsg msg = pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.DIRECTORY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        
        OMMAttribInfo attribInfo = (OMMAttribInfo)pool.acquireAttribInfo();
        attribInfo.setFilter(RDMService.Filter.INFO | RDMService.Filter.STATE
                | RDMService.Filter.GROUP); // Specifies the filter information needed.
                                            // see RDMUsageGuide.
        msg.setAttribInfo(attribInfo);
        return msg; // return the encoded message
    }

    public void closeRequest()
    {
        _ommConsumer.unregisterClient(_loginHandle);
    }

    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.CONNECTION_EVENT:
                processConnectionEvent(event);
                break;
            case Event.OMM_ITEM_EVENT:
                processOMMItemEvent(event);
                break;
            default:
            {
                System.out.println("ERROR: processEvent() received a unsupported Event type");
                System.exit(-1);
            }
        }
    }

    public void processConnectionEvent(Event event)
    {
    }

    public void processOMMItemEvent(Event event)
    {
        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg respMsg = ie.getMsg();
        short msgModelType = respMsg.getMsgModelType();
        switch (msgModelType)
        {
            case (RDMMsgTypes.LOGIN):
                processLoginMessage(respMsg);
                break;
            case (RDMMsgTypes.DIRECTORY):
                processSrcDirMessage(respMsg);
                break;
            case (RDMMsgTypes.DICTIONARY):
                processOMMDataDictionaryEvent(respMsg);
                break;
            case (RDMMsgTypes.MARKET_BY_ORDER):
                processMarketByOrder(event);
                break;
            default:
            {
                System.out.println("OMM Msg type <" + msgModelType + "> not handled!! ");
                return;
            }

        }

    }

    public void processLoginMessage(OMMMsg respMsg)
    {
        // *_log<< "Received login response" << endl;
        myCallback.notifyStatus("Received login response");

        if (respMsg.has(OMMMsg.HAS_ATTRIB_INFO))
        {
            OMMAttribInfo attribInfo = respMsg.getAttribInfo();
            if (attribInfo.has(OMMAttribInfo.HAS_SERVICE_NAME))
                // *_log<<"Service Name: : " <<
                // attribInfo.getServiceName()<<endl;
                System.out.println("");
            if (attribInfo.has(OMMAttribInfo.HAS_NAME))
                // *_log<<"Name : " << attribInfo.getName()<<endl;
                System.out.println("");

            // *_log<< "response type : " <<
            // msgRespTypeToString(respMsg.getRespType()) << endl;
        }
        if (respMsg.has(OMMMsg.HAS_STATE))
        {
            OMMState respState = respMsg.getState();
            StringBuffer buffer = new StringBuffer();
            buffer.append("Login state: ");
            // buffer.append(streamStateToString (respState.getStreamState()));

            buffer.append("; code: ");
            // buffer.append(statusCodeToString (respState.getCode()));
            buffer.append(" status: " + respState.getText());
            // *_log<<buffer.toString();
            myCallback.notifyStatus(buffer.toString());

            if ((respMsg.getState().getStreamState() == OMMState.Stream.OPEN)
                    && (respMsg.getState().getDataState() == OMMState.Data.OK))
            {
                System.out.println("Login successful...");
                // Now we can send the directory request
                if (!_loggedIn)
                {
                    // send service request only once.
                    _loggedIn = true;
                    sendRequest(RDMMsgTypes.DIRECTORY, null);

                }
            }
            else if (respMsg.getMsgType() == OMMMsg.MsgType.STATUS_RESP)
            {
                if (respMsg.getState().getStreamState() == OMMState.Stream.CLOSED)
                {
                    // Check for connection loss, recovery and cleanup
                    System.out.println("Login denied");
                    // cleanup();
                    _loggedIn = false;
                }
            }
        }
    }

    public void processSrcDirMessage(OMMMsg respMsg)
    {
        // *_log<<"Received source directory response"<<endl;
        myCallback.notifyStatus("Received source directory response");

        if (respMsg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP
                || respMsg.getMsgType() == OMMMsg.MsgType.UPDATE_RESP)
        {
            OMMMap map;
            if (respMsg.getDataType() == OMMTypes.MAP)
            {
                try
                {
                    map = (OMMMap)respMsg.getPayload();
                }
                catch (Exception e) // Just in case
                {
                    System.out.println(e.getClass().getName() + ": " + e.getMessage());
                    throw new RuntimeException("Source Directory Response has no Services.");
                }
                for (Iterator iter = map.iterator(); iter.hasNext();)
                {
                    OMMMapEntry mapEntry = (OMMMapEntry)((OMMMapEntry)iter.next());
                    if (mapEntry.getDataType() != OMMTypes.FILTER_LIST)
                        System.out.println("ERROR--expected a FilterList");

                    // each mapEntry will be a separate service
                    String serviceName = mapEntry.getKey().toString();
                    String str = "Received service: ";
                    str += serviceName;
                    myCallback.notifyStatus(str);

                    /*
                     * if (serviceName.equals(myServiceName)) { if
                     * ((_dictLocation == 2) && !m_bSubscribedToAppendixA) {
                     * sendRequest(RDMMsgTypes.DICTIONARY, serviceName); } }
                     */
                }
            }
            else
                myCallback.notifyStatus("Source directory response has no services!");
        }
    }

    public void processOMMDataDictionaryEvent(OMMMsg respMsg)
    {
        // *_log<<"Received dictionary Response"<<endl<<endl;
        // _dataDict.setDictId(respMsg.getAttribInfo().getID());

        if (respMsg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP
                || respMsg.getMsgType() == OMMMsg.MsgType.UPDATE_RESP)
        {
            System.out.println("Received full dictionary");
            OMMSeries series = (OMMSeries)respMsg.getPayload();
            if (isFieldDictionary(series))
            {
                // *_log<<"Received fid dict from network"<<endl;
                myCallback.notifyStatus("Received fid dict from network");
                FieldDictionary.decodeRDMFldDictionary(_dataDict, series);
                if (respMsg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
                    m_bSubscribedToAppendixA = true;
            }
            else if (isEnumDictionary(series))
            {
                // *_log<<"Received enumeration dict from network"<<endl;
                myCallback.notifyStatus("Received enumeration dict from network");
                FieldDictionary.decodeRDMEnumDictionary(_dataDict, series);
                if (respMsg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
                    m_bSubscribedToEnumTypeDef = true;
            }
            else
            {
                // *_log<<"Dictionary name not recognized"<<endl;
                myCallback.notifyStatus("Dictionary name not recognized");
            }
        }
        else
        // status msg
        {
            // ignore status msg
        }
    }

    private boolean isFieldDictionary(OMMSeries series)
    {
        OMMElementList elist = (OMMElementList)series.getSummaryData();
        for (Iterator eiter = elist.iterator(); eiter.hasNext();)
        {
            OMMElementEntry eentry = (OMMElementEntry)eiter.next();
            if (eentry.getName().equals("Type"))
            {
                long type = ((OMMNumeric)eentry.getData()).toLong();
                if (type == RDMDictionary.Type.FIELD_DEFINITIONS)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isEnumDictionary(OMMSeries series)
    {
        OMMElementList elist = (OMMElementList)series.getSummaryData();
        for (Iterator eiter = elist.iterator(); eiter.hasNext();)
        {
            OMMElementEntry eentry = (OMMElementEntry)eiter.next();
            if (eentry.getName().equals("Type"))
            {
                long type = ((OMMNumeric)eentry.getData()).toLong();
                if (type == RDMDictionary.Type.ENUM_TABLES)
                {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean dictDownloadComplete()
    {
        return (m_bSubscribedToEnumTypeDef && m_bSubscribedToAppendixA);
    }

    public void cleanup()
    {
        if (_loginHandle != null)
        {
            _ommConsumer.unregisterClient(_loginHandle);
            _loginHandle = null;
        }
    }

}
