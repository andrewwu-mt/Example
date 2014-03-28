package com.reuters.rfa.example.omm.sourcemirroringcons;

import java.util.HashMap;
import java.util.Iterator;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMFilterEntry;
import com.reuters.rfa.omm.OMMFilterList;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;
import com.reuters.rfa.session.omm.OMMCmdErrorEvent;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;
import com.reuters.rfa.session.omm.OMMHandleItemCmd;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * <p>
 * This is a Client class that handle request and response for Directory domain.
 * </p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Encoding Directory request using OMM message
 * <li>Register for Directory request to RFA</li>
 * <li>Implement a Client which processes Item and CmdErr events
 * <li>Use {@link com.reuters.rfa.example.utility.GenericOMMParser
 * GenericOMMParser} to parse {@link com.reuters.rfa.omm.OMMMsg OMMMsg} response
 * messages.
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder},
 * {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMErrorIntSpec OMMErrorIntSpec}
 * 
 * @see StarterConsumer_SourceMirroring
 * 
 */

public class DirectoryClient implements Client
{
    private OMMItemIntSpec _ommItemIntSpec;
    private Handle _dirHandle;
    private StarterConsumer_SourceMirroring _parentConsumer;
    private String _currentServiceName;
    
    // list of Service Entries, which corresponds to MapEntries
    private HashMap<String, ServiceEntry> _serviceMap;
    
    private static final short DIRECTORY_DOMAIN_MODEL = 4;

    protected DirectoryClient(StarterConsumer_SourceMirroring consDemo, String serviceNameRequest)
    {
        _parentConsumer = consDemo;
        _ommItemIntSpec = new OMMItemIntSpec();
        _dirHandle = null;
        _serviceMap = new HashMap<String, ServiceEntry>();
        _currentServiceName = serviceNameRequest;
    }

    protected void cleanup()
    {
        _ommItemIntSpec = null;
        _dirHandle = null;
        _parentConsumer = null;
        _serviceMap.clear();
        _serviceMap = null;
    }

    protected void sendRequest()
    {
        OMMMsg ommmsg = encodeSrcDirReqMsg();
        _ommItemIntSpec = new OMMItemIntSpec();
        _ommItemIntSpec.setMsg(ommmsg);
        _dirHandle = _parentConsumer.getOMMConsumer()
                .registerClient(_parentConsumer.getEventQueue(), _ommItemIntSpec, this, null);
    }

    private OMMMsg encodeSrcDirReqMsg()
    {
        OMMPool pool = _parentConsumer._pool;
        OMMMsg msg = pool.acquireMsg();

        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.DIRECTORY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        OMMAttribInfo attribInfo = pool.acquireAttribInfo();
        
        // Specifies the filter information needed. see RDMUsageGuide.
        attribInfo.setFilter(RDMService.FilterId.INFO | RDMService.Filter.STATE
                | RDMService.Filter.GROUP);
        
        attribInfo.setServiceName(_currentServiceName);
        msg.setAttribInfo(attribInfo);
        return msg;
    }

    public void sendGenericMsgWithStatusMode()
    {
        OMMErrorIntSpec errIntSpec = new OMMErrorIntSpec();
        _parentConsumer._ommConsumer.registerClient(_parentConsumer._eventQueue, errIntSpec, this,
                                                    null);

        OMMPool pool = _parentConsumer.getPool();
        OMMMsg msg = pool.acquireMsg();

        OMMEncoder encoder = pool.acquireEncoder();
        encoder.initialize(OMMTypes.MSG, 6000);

        msg.setMsgModelType(DIRECTORY_DOMAIN_MODEL);
        msg.setMsgType(OMMMsg.MsgType.GENERIC);
        msg.setIndicationFlags(OMMMsg.Indication.GENERIC_COMPLETE);

        OMMAttribInfo ai = pool.acquireAttribInfo();
        ai.setName("ConsumerStatus");
        if (_parentConsumer._sourceMirroringMode < 0 || _parentConsumer._sourceMirroringMode > 2)
        {
            System.out.println("Wrong Consumer Status parameter ");
            return;
        }
        msg.setAttribInfo(ai);

        /* put payload in */
        encoder.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.MAP);
        encoder.encodeMapInit(0, OMMTypes.ASCII_STRING, OMMTypes.ELEMENT_LIST, 0, (short)0);
        encoder.encodeMapEntryInit(OMMElementList.HAS_STANDARD_DATA, OMMMapEntry.Action.ADD, null);
        encoder.encodeString(_currentServiceName, OMMTypes.ASCII_STRING);
        encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        encoder.encodeElementEntryInit("SourceMirroringMode", OMMTypes.UINT);
        encoder.encodeUInt(_parentConsumer._sourceMirroringMode);
        encoder.encodeAggregateComplete(); // ElementList
        encoder.encodeAggregateComplete(); // MapEntry

        OMMHandleItemCmd cmd = new OMMHandleItemCmd();
        cmd.setHandle(_dirHandle);
        cmd.setMsg(msg);
        cmd.setMsg((OMMMsg)encoder.getEncodedObject());

        try
        {
            _parentConsumer.getOMMConsumer().submit(cmd, null);
        }
        catch (IllegalArgumentException e)
        {
            System.out.println(e);
        }

        pool.releaseEncoder(encoder);
        System.out.println("Sent Status Mode generic message ");
        // The directory message with Mirroring status mode has been sent,
        // start the item request
        _parentConsumer._itemManager.sendRequest();
        System.out.println("Sent generic item message ");
    }

    public void processEvent(Event event)
    {
        System.out.print("Received Directory Response... ");
        switch (event.getType())
        {
            case Event.OMM_CMD_ERROR_EVENT: // for cmd error handle
                System.out.println("Received OMMCmd ERROR EVENT");
                System.out.println(((OMMCmdErrorEvent)event).getStatus().getStatusText());
                break;
            case Event.OMM_ITEM_EVENT:
            {
                OMMItemEvent ie = (OMMItemEvent)event;
                OMMMsg respMsg = ie.getMsg();
                if (respMsg.getMsgModelType() != RDMMsgTypes.DIRECTORY)
                {
                    System.out.println("ERROR: DirectoryManager.processEvent() received a non-DIRECTORY message type.");
                    System.exit(-1);
                }

                if (respMsg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP
                        || respMsg.getMsgType() == OMMMsg.MsgType.UPDATE_RESP)
                {
                    if (!respMsg.has(OMMMsg.HAS_STATE)
                            || ((respMsg.getState().getStreamState() == OMMState.Stream.OPEN) && (respMsg
                                    .getState().getDataState() == OMMState.Data.OK)))
                    {
                        decodeDirectoryResponse(respMsg);

                        ServiceEntry entry = (ServiceEntry)_serviceMap.get(_currentServiceName);
                        // OMMConsumer only need to know if the server is
                        // accepting
                        // requests to send request
                        if (entry == null)
                        {
                            System.out.println("ERROR: The requested service "
                                    + _currentServiceName + " does not exist...");
                            System.out.println("ERROR: Exiting the application...");
                            System.exit(1);
                        }
                        else if (entry.getAcceptingRequests())
                        {
                            _parentConsumer.processDirectory();
                        }
                    }
                    else if ((respMsg.getState().getStreamState() != OMMState.Stream.OPEN)
                            || (respMsg.getState().getDataState() != OMMState.Data.OK))
                    {
                        // Source is unavailable. Will wait for the source to come up.
                        System.out.println("Directory stream is stale.  recovering when server is up");
                        decodeDirectoryResponse(respMsg);
                    }
                }
                else if (respMsg.getMsgType() == OMMMsg.MsgType.STATUS_RESP)
                {
                    // A Directory stream will only receive a status RespMsg
                    // when the Login is Closed or ClosedRecoverable
                    // see RDMUsageGuide.
                }
                break;
            }
            default:
            {
                System.out.println("ERROR: DirectoryClient::processEvent() received an unsupported Event type");
                System.exit(-1);
            }
        }
    }

    private void decodeDirectoryResponse(OMMMsg msg) throws RuntimeException
    {
        int mmt = msg.getMsgModelType();
        int respType = msg.getMsgType();
        int respTypeNum = msg.getRespTypeNum();
        System.out.println("mmt: " + mmt + " respType: " + respType + " respTypeNum: "
                + respTypeNum);
        GenericOMMParser.parse(msg);
        decodePayload(msg);
    }

    private void decodePayload(OMMMsg msg)
    {
        OMMMap map;
        if (msg.getDataType() == OMMTypes.MAP)
        {
            try
            {
                map = (OMMMap)msg.getPayload();
            }
            catch (Exception e) // Just in case
            {
                System.out.println(e.getClass().getName() + ": " + e.getMessage());
                throw new RuntimeException("in method DirectoryManager.decodePayload(OMMMsg msg)");
            }
            for (Iterator<?> iter = map.iterator(); iter.hasNext();)
            {
                OMMMapEntry mapEntry = (OMMMapEntry)((OMMEntry)iter.next());
                if (mapEntry.getDataType() != OMMTypes.FILTER_LIST)
                    System.out.println("ERROR--expected a FilterList");

                // each mapEntry will be a separate service
                ServiceEntry serviceEntry = null;
                byte action = mapEntry.getAction();
                String serviceName = mapEntry.getKey().toString();
                if (action == OMMMapEntry.Action.ADD)
                {
                    serviceEntry = new ServiceEntry();
                    serviceEntry._serviceName = serviceName.toString();
                    _serviceMap.put(serviceName.toString(), serviceEntry);
                    serviceEntry.decodeServiceData(mapEntry);
                }
                else if (action == OMMMapEntry.Action.UPDATE)
                {
                    serviceEntry = getServiceEntry(serviceName);
                    serviceEntry.decodeServiceData(mapEntry);
                }
                else if (action == OMMMapEntry.Action.DELETE)
                {
                    continue;
                }
            }
        }
    }

    private ServiceEntry getServiceEntry(String serviceName)
    {
        if (!_serviceMap.containsKey(serviceName))
        {
            return null;
        }
        return (ServiceEntry)_serviceMap.get(serviceName);
    }

    protected boolean serviceExists(String serviceName)
    {
        if (getServiceEntry(serviceName) == null)
            return false;
        return true;
    }

    // Returns true if the service is accepting consumer status for a given
    // Service Name
    boolean getAcceptingConsumerStatus(String serviceName)
    {
        return getServiceEntry(serviceName).getAcceptingConsumerStatus();
    }
}

class ServiceEntry
{
    protected String _serviceName;
    private int _acceptingRequests;
    protected int _serviceAcceptingConsumerStatus = 0;

    // Constructor
    ServiceEntry()
    {
    }

    // Returns true if this service is accepting requests
    boolean getAcceptingRequests()
    {
        if (_acceptingRequests == RDMService.State.DOWN)
            return false;

        return true;
    }

    // Returns true if the service is accepting consumer status
    boolean getAcceptingConsumerStatus()
    {
        if (_serviceAcceptingConsumerStatus == 1)
            return true;

        return false;
    }

    // Decodes and stores service data for a single service
    void decodeServiceData(OMMMapEntry serviceData)
    {
        if (serviceData.getDataType() != OMMTypes.FILTER_LIST)
        {
            System.out.println("ERROR--expected a FilterList");
        }
        OMMFilterList serviceFilterList = (OMMFilterList)(serviceData.getData());
        for (Iterator<?> filter = serviceFilterList.iterator(); filter.hasNext();)
        {
            OMMFilterEntry serviceFilterEntry = (OMMFilterEntry)filter.next();
            switch (serviceFilterEntry.getFilterId())
            {
                case RDMService.FilterId.STATE:
                    decodeServiceStateData(serviceFilterEntry);
                    break;
                case RDMService.FilterId.INFO:
                    decodeServiceInfoData(serviceFilterEntry);
                    break;
                default:
                    System.out.println("ERROR-- Unknown Filter ID: "
                            + serviceFilterEntry.getFilterId());
            }
        }
    }

    void decodeServiceStateData(OMMFilterEntry serviceFilterEntry)
    {
        OMMData serviceFilterEntryData = serviceFilterEntry.getData();
        if (serviceFilterEntryData.getType() == OMMTypes.ELEMENT_LIST)
        {
            for (Iterator<?> eliter = ((OMMElementList)serviceFilterEntryData).iterator(); eliter
                    .hasNext();)
            {
                OMMElementEntry eentry = (OMMElementEntry)eliter.next();
                OMMData edata = eentry.getData();

                if (eentry.getName().compareTo(RDMService.SvcState.ServiceState) == 0)
                {
                    // do nothing - not used in scenario
                    // _serviceState = (int)((OMMNumeric)edata).getLongValue();
                }
                else if (eentry.getName().compareTo(RDMService.SvcState.AcceptingRequests) == 0)
                {
                    // cache it
                    _acceptingRequests = (int)((OMMNumeric)edata).getLongValue();
                }
                else if (eentry.getName().compareTo(RDMService.SvcState.Status) == 0)
                {
                    // do nothing - not used in scenario
                    // _stateStatus = (OMMState)edata;
                }
            }
        }
    }

    void decodeServiceInfoData(OMMFilterEntry serviceFilterEntry)
    {
        OMMData serviceFilterEntryData = serviceFilterEntry.getData();
        if (serviceFilterEntryData.getType() == OMMTypes.ELEMENT_LIST)
        {
            for (Iterator<?> eliter = ((OMMElementList)serviceFilterEntryData).iterator(); eliter
                    .hasNext();)
            {
                OMMElementEntry eentry = (OMMElementEntry)eliter.next();
                OMMData edata = eentry.getData();

                if (eentry.getName().compareTo(RDMService.Info.AcceptingConsumerStatus) == 0)
                {
                    // cache it
                    _serviceAcceptingConsumerStatus = (int)((OMMNumeric)edata).getLongValue();
                }
            }
        }
    }
} // end of ServiceEntry class

