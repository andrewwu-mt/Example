package com.reuters.rfa.example.omm.batchviewcons;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
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
 * <li>Parsing directory response and storing list of services that up.
 * <li>Sending item request by calling
 * {@linkplain StarterConsumer_BatchView#processDirectoryInfo()}
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder},
 * {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer}.
 * 
 * @see com.reuters.rfa.example.omm.batchviewcons.StarterConsumer_BatchView
 * 
 */
public class BatchViewDirectoryClient implements Client
{
    private StarterConsumer_BatchView _mainApp;

    /*
     * List of Services, which corresponds to OMMMap entries Note: This contains
     * only services that has ServiceState UP.
     */
    private List<String> _serviceList;
    boolean _directoryReqPending = false;

    private String _className = "BatchViewDirectoryClient";

    protected BatchViewDirectoryClient(StarterConsumer_BatchView mainApp)
    {
        _mainApp = mainApp;
        _serviceList = new ArrayList<String>();
    }

    /**
     * Encodes an {@link OMMMsg} for directory request and register the message
     * to RFA.
     */
    protected void sendRequest()
    {
        OMMMsg msg = encodeSrcDirReqMsg();
        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
        ommItemIntSpec.setMsg(msg);
        _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(), ommItemIntSpec, this,
                                                 null);
        _mainApp.getPool().releaseMsg(msg);
        _directoryReqPending = true;
    }

    private OMMMsg encodeSrcDirReqMsg()
    {
        OMMPool pool = _mainApp.getPool();

        OMMMsg msg = pool.acquireMsg();
        // This application need only a snapshot of directory.
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.DIRECTORY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH | OMMMsg.Indication.NONSTREAMING);

        OMMAttribInfo attribInfo = pool.acquireAttribInfo();
        // Specifies the filter information needed. We need only STATE
        // information.
        // See RDMUsageGuide.
        attribInfo.setFilter(RDMService.Filter.STATE);
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

        // Display directory response with PSGenericOMMParser
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
                    OMMMapEntry mapEntry = (OMMMapEntry)iter.next();
                    if (mapEntry.getDataType() != OMMTypes.FILTER_LIST)
                    {
                        System.err.println("ERROR: " + _className
                                + ".processEvent() OMMMapEntry expected a OMMFilterList");
                        return;
                    }

                    // each mapEntry will be a separate service
                    byte action = mapEntry.getAction();
                    String serviceName = mapEntry.getKey().toString(); // Key is a service name.

                    if (action == OMMMapEntry.Action.ADD)
                    {
                        decodeServiceData(serviceName, mapEntry);
                    }
                    else if (action == OMMMapEntry.Action.UPDATE)
                    {
                        if (_serviceList.contains(serviceName))
                        {
                            decodeServiceData(serviceName, mapEntry);
                        } // else ignore if this service doesn't existed yet.
                    }
                    else if (action == OMMMapEntry.Action.DELETE)
                    {
                        _serviceList.remove(serviceName);
                    }
                }
                if (respMsg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
                {
                    if (_directoryReqPending)
                    {
                        _directoryReqPending = false;
                        _mainApp.processDirectoryInfo();
                    }
                    else
                        return;
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
            }
        }
    }

    /**
     * Decodes directory filter list for each service.
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
                    if (stateUp && acceptRequest)
                    {
                        _serviceList.add(serviceName);
                    }
                    else
                    {
                        // if this service is not UP or not accept request,
                        // remove
                        // from the list.
                        _serviceList.remove(serviceName);
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
     * Note: This list contains only the services that ServiceState are UP and
     * the server has accepted the request.
     * 
     * @return the list of services that are UP.
     */
    public List<String> getServiceList()
    {
        return _serviceList;
    }
}
