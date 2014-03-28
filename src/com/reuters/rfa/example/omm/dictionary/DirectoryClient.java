package com.reuters.rfa.example.omm.dictionary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMArray;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
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
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * Client class that handle request and response for Directory information.
 * <p>
 * This class is responsible for the following:
 * <ul>
 * <li>Encoding non-streaming request message for Directory using OMM message
 * <li>Register for Directory
 * {@linkplain com.reuters.rfa.rdm.RDMService.Filter#INFO info} and
 * {@linkplain com.reuters.rfa.rdm.RDMService.Filter#STATE state} to RFA</li>
 * <li>Implement a Client which processes events from a <code>OMMConsumer</code>.
 * <li>Parsing directory response and provides list of dictionary names of each
 * service.
 * <li>Unregistered directory request.
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder},
 * {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer} from
 * DictionaryDemo
 * 
 * @see DictionaryDemo
 * 
 */
public class DirectoryClient implements Client
{
    @SuppressWarnings("unused")
    private Handle _dirHandle;
    private DictionaryDemo _mainApp;

    /*
     * List of Services, which corresponds to OMMMap entries Key - serviceName /
     * value ArrayList of Directory names that this service can provide.
     * 
     * Note: This contains only services that has ServiceState UP.
     */
    private HashMap<String, ArrayList<String>> _serviceMap;

    private String _className = "DirectoryClient";

    protected DirectoryClient(DictionaryDemo mainApp)
    {
        _mainApp = mainApp;
        _dirHandle = null;
        _serviceMap = new HashMap<String, ArrayList<String>>();
    }

    /**
     * Encodes an {@link OMMMsg} for directory request and register the message
     * to RFA. In order to know the list of dictionary names from a service,
     * application need to parse
     * {@link com.reuters.rfa.rdm.RDMService.Info#DictionariesProvided
     * RDMService.Info.DictionariesProvided} which is available in
     * {@link com.reuters.rfa.rdm.RDMService.FilterId#INFO
     * RDMService.FilterId.INFO}
     */
    protected void sendRequest()
    {
        OMMMsg msg = encodeSrcDirReqMsg();
        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
        ommItemIntSpec.setMsg(msg);
        _dirHandle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(),
                                                              ommItemIntSpec, this, null);
        _mainApp.getPool().releaseMsg(msg);
    }

    private OMMMsg encodeSrcDirReqMsg()
    {
        OMMPool pool = _mainApp.getPool();

        OMMMsg msg = pool.acquireMsg();
        // This application need only a snapshot of directory.
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.DIRECTORY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH
                               | OMMMsg.Indication.NONSTREAMING);
        
        OMMAttribInfo attribInfo = pool.acquireAttribInfo();
        // Specifies the filter information needed. To download dictionary we
        // need only INFO and STATE.
        // See RDMUsageGuide.
        attribInfo.setFilter(RDMService.Filter.INFO | RDMService.Filter.STATE);
        msg.setAttribInfo(attribInfo);
        return msg;
    }

    public void processEvent(Event event)
    {
        if (event.getType() == Event.COMPLETION_EVENT)
        {
            System.out.println(_className + ": Receive a COMPLETION_EVENT, " + event.getHandle());
            return;
        }

        System.out.println(_className + ".processEvent: Received Directory Response... ");

        if (event.getType() != Event.OMM_ITEM_EVENT)
        {
            System.out.println("ERROR: " + _className + " Received an unsupported Event type.");
            _mainApp.cleanup(-1);
        }

        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg respMsg = ie.getMsg();
        if (respMsg.getMsgModelType() != RDMMsgTypes.DIRECTORY)
        {
            System.out.println("ERROR: " + _className + ": Received a non-DIRECTORY model type.");
            _mainApp.cleanup(-1);
        }

        // Display directory response with GernericOMMParser
        GenericOMMParser.parse(respMsg);

        if (respMsg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
        {
            if ((respMsg.getState().getDataState() == OMMState.Data.OK))
            {
                if (respMsg.getDataType() != OMMTypes.MAP)
                {
                    System.out.println("ERROR: " + _className
                            + ".processEvent() incorrect data type, payload data must be OMMMap");
                    _mainApp.cleanup(-1);
                }
                OMMMap map = (OMMMap)respMsg.getPayload();

                // Iterate each MapEntry
                for (Iterator<?> iter = map.iterator(); iter.hasNext();)
                {
                    OMMMapEntry mapEntry = (OMMMapEntry)((OMMEntry)iter.next());
                    if (mapEntry.getDataType() != OMMTypes.FILTER_LIST)
                    {
                        System.err.println("ERROR: " + _className
                                + ".processEvent() OMMMapEntry expected a OMMFilterList");
                        return;
                    }

                    // each mapEntry will be a separate service
                    byte action = mapEntry.getAction();
                    
                    // Key is a service name.
                    String serviceName = mapEntry.getKey().toString();
                    
                    if (action == OMMMapEntry.Action.ADD)
                    {
                        decodeServiceData(serviceName, mapEntry);
                    }
                    else if (action == OMMMapEntry.Action.UPDATE)
                    {
                        if (_serviceMap.containsKey(serviceName))
                        {
                            decodeServiceData(serviceName, mapEntry);
                        } // else ignore if this service doesn't existed yet.
                    }
                    else if (action == OMMMapEntry.Action.DELETE)
                    {
                        _serviceMap.remove(serviceName);
                    }
                }
                if (respMsg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
                {
                    // Receive all dictionary names from all source directory
                    // Notify application to download dictionary
                    _mainApp.processDirectoryInfo();
                    _dirHandle = null; // Because the request is NON-Streaming
                }
            }
        }
        else if (respMsg.getMsgType() == OMMMsg.MsgType.STATUS_RESP)
        {
            // A Directory stream will only receive a status RespMsg when the
            // Login is Closed or ClosedRecoverable
            // see RDMUsageGuide.
            if (respMsg.isFinal())
            {
                _dirHandle = null;
            }
        }
        else if (respMsg.getMsgType() == OMMMsg.MsgType.GENERIC)
        {
            System.out.println("Received generic message type, not supported. ");
        }
        else
        {
            System.out.println("ERROR: " + _className + ": Received unexpected message type. "
                    + respMsg.getMsgType());
        }
    }

    /**
     * Decodes and stores list of dictionary names that each service can
     * provide. Filter the s
     * 
     * @param serviceName the name of service, corresponding to the key
     * @param serviceData the ommMapEntry that contain data of the serviceName
     */
    private void decodeServiceData(String serviceName, OMMMapEntry serviceData)
    {
        System.out.println(_className + ": Decoding service " + serviceName);
        OMMFilterList serviceFilterList = (OMMFilterList)(serviceData.getData());
        for (Iterator<?> filter = serviceFilterList.iterator(); filter.hasNext();)
        {
            OMMFilterEntry serviceFilterEntry = (OMMFilterEntry)filter.next();
            // Expects OMMElemetList in serviceFilterEntry
            OMMElementList elementList = (OMMElementList)serviceFilterEntry.getData();
            switch (serviceFilterEntry.getFilterId())
            {
                case RDMService.FilterId.INFO:
                {
                    // Get list of dictionary names
                    // that the service provides from "DictionariesProvided"
                    OMMElementEntry elementEntry = elementList
                            .find(RDMService.Info.DictionariesProvided);
                    if (elementEntry != null)
                    {
                        ArrayList<String> dictionaryNames = new ArrayList<String>(2);
                        // Expects OMMArray in the elementEntry of
                        // DictionariesProvided
                        OMMArray array = (OMMArray)elementEntry.getData();
                        Iterator<?> iter = array.iterator();
                        OMMEntry entry;
                        while (iter.hasNext())
                        {
                            entry = (OMMEntry)iter.next();
                            dictionaryNames.add(entry.getData().toString());
                        }
                        _serviceMap.put(serviceName, dictionaryNames);
                        System.out.println(_className + ": " + serviceName
                                + " DictionariesProvided " + dictionaryNames);
                    }
                }
                    break;
                case RDMService.FilterId.STATE:
                {
                    Iterator<?> elementIter = elementList.iterator();
                    boolean stateUp = false;
                    boolean acceptRequest = false;
                    // Expects OMMNumeric in the elementEntry of
                    // ServiceState and AcceptingRequests
                    while (elementIter.hasNext())
                    {
                        OMMElementEntry elementEntry = (OMMElementEntry)elementIter.next();
                        if (elementEntry.getName().equals(RDMService.SvcState.ServiceState))
                        {
                            OMMNumeric value = (OMMNumeric)elementEntry.getData();
                            if ((int)value.getLongValue() == RDMService.State.UP)
                            {
                                stateUp = true;
                            }
                        }
                        else if (elementEntry.getName()
                                .equals(RDMService.SvcState.AcceptingRequests))
                        {
                            OMMNumeric value = (OMMNumeric)elementEntry.getData();
                            if ((int)value.getLongValue() == 1)
                            {
                                acceptRequest = true;
                            }
                        }
                    }
                    if (!stateUp || !acceptRequest)
                    {
                        // if this service is not UP or not accept request,
                        // remove from the map.
                        // So the application doesn't download dictionary from it.
                        _serviceMap.remove(serviceName);
                    }
                }
                    break;
                default:
                    System.out.println(_className
                            + ".decodeServiceData() received registered Filter, "
                            + serviceFilterEntry.getFilterId() + ", ignored this one");
            }
        }
    }

    /**
     * 
     * Note: This map contains only the services that ServiceState are UP and
     * the server has accepted the request.
     * 
     * @return the map between service and ArrayList of dictionary names that
     *         the service can provide
     */
    public Map<String, ArrayList<String>> getServiceMap()
    {
        return _serviceMap;
    }
}
