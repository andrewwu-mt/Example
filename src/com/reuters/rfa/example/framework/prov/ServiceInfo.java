package com.reuters.rfa.example.framework.prov;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import com.reuters.rfa.common.QualityOfService;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMFilterEntry;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMQos;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMService;

/**
 * ServiceInfo contains an information of one service. The service information
 * is used to create Directory message. It compose of a service name, vendor
 * name, isSource flag, capabilities, DictionariesProvided, DictionariesUse,
 * QualityOfService, ServiceState and AcceptingRequests.
 * 
 * @see com.reuters.rfa.rdm.RDMService.Info
 */
public class ServiceInfo
{
    String _serviceName;
    String _vendor;
    int _isSource;

    QualityOfService _qos;

    Set<Short> _capabilities;
    Set<String> _dictionariesProvided;
    Set<String> _dictionariesUsed;

    int _state;
    int _acceptingRequests;

    public ServiceInfo(String serviceName, String vendor, boolean isSource)
    {
        _serviceName = serviceName;
        _vendor = vendor;
        _isSource = isSource ? 1 : 0;

        _state = RDMService.State.DOWN;
        _acceptingRequests = RDMService.State.DOWN;

        _capabilities = new TreeSet<Short>();
        _dictionariesProvided = new LinkedHashSet<String>();
        _dictionariesUsed = new LinkedHashSet<String>();
    }

    /**
     * Sets QualityOfService
     * 
     * @param qos
     */
    public void setQos(QualityOfService qos)
    {
        _qos = qos;
    }

    /**
     * Sets ServiceState and AcceptingRequests
     * 
     * @param state
     * @param acceptingRequests
     */
    public void setState(int state, int acceptingRequests)
    {
        _state = state;
        _acceptingRequests = acceptingRequests;
    }

    /**
     * Adds dictionary name that this service can provide.
     * 
     * @param dictionaryName
     */
    public void addDictionaryProvided(String dictionaryName)
    {
        _dictionariesProvided.add(dictionaryName);
    }

    /**
     * Adds required dictionary name for this service.
     * 
     * @param dictionaryName
     */
    public void addDictionaryUsed(String dictionaryName)
    {
        _dictionariesUsed.add(dictionaryName);
    }

    /**
     * Adds message model type that this service can provide.
     * 
     * @param msgModelType
     */
    public void addCapability(short msgModelType)
    {
        _capabilities.add(new Short(msgModelType));
    }

    /**
     * Encodes service infotmation of Directory refresh message.
     * 
     * @param enc
     * @param filter
     */
    public void encodeRefresh(OMMEncoder enc, int filter)
    {
        enc.encodeMapEntryInit(0, OMMMapEntry.Action.ADD, null);
        enc.encodeString(_serviceName, OMMTypes.ASCII_STRING);

        enc.encodeFilterListInit(0, OMMTypes.ELEMENT_LIST, 0);

        if ((filter & RDMService.Filter.INFO) != 0)
        {
            enc.encodeFilterEntryInit(0, OMMFilterEntry.Action.SET, RDMService.FilterId.INFO,
                                      OMMTypes.ELEMENT_LIST, null);
            encodeInfoElementList(enc);
        }

        if ((filter & RDMService.Filter.STATE) != 0)
        {
            enc.encodeFilterEntryInit(0, OMMFilterEntry.Action.SET, RDMService.FilterId.STATE,
                                      OMMTypes.ELEMENT_LIST, null);
            encodeStateElementList(enc);
        }

        enc.encodeAggregateComplete(); // Completes the FilterList
    }

    private void encodeInfoElementList(OMMEncoder enc)
    {
        enc.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        enc.encodeElementEntryInit(RDMService.Info.Name, OMMTypes.ASCII_STRING);
        enc.encodeString(_serviceName, OMMTypes.ASCII_STRING);
        enc.encodeElementEntryInit(RDMService.Info.Vendor, OMMTypes.ASCII_STRING);
        enc.encodeString(_vendor, OMMTypes.ASCII_STRING);
        enc.encodeElementEntryInit(RDMService.Info.IsSource, OMMTypes.UINT);
        enc.encodeUInt((long)_isSource);

        enc.encodeElementEntryInit(RDMService.Info.Capabilities, OMMTypes.ARRAY);
        enc.encodeArrayInit(OMMTypes.UINT, 1);
        for (Iterator<Short> iter = _capabilities.iterator(); iter.hasNext();)
        {
            enc.encodeArrayEntryInit();
            enc.encodeUInt(((Short)iter.next()).longValue());
        }
        enc.encodeAggregateComplete();

        enc.encodeElementEntryInit(RDMService.Info.DictionariesProvided, OMMTypes.ARRAY);
        enc.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
        for (Iterator<String> iter = _dictionariesProvided.iterator(); iter.hasNext();)
        {
            enc.encodeArrayEntryInit();
            enc.encodeString((String)iter.next(), OMMTypes.ASCII_STRING);
        }
        enc.encodeAggregateComplete();

        enc.encodeElementEntryInit(RDMService.Info.DictionariesUsed, OMMTypes.ARRAY);
        enc.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
        for (Iterator<String> iter = _dictionariesUsed.iterator(); iter.hasNext();)
        {
            enc.encodeArrayEntryInit();
            enc.encodeString((String)iter.next(), OMMTypes.ASCII_STRING);
        }
        enc.encodeAggregateComplete(); // Completes the Array.

        enc.encodeElementEntryInit(RDMService.Info.QoS, OMMTypes.ARRAY);
        enc.encodeArrayInit(OMMTypes.QOS, 0);
        enc.encodeArrayEntryInit();
        if (_qos != null)
            enc.encodeQos(_qos.getTimeliness(), _qos.getRate());
        else
            enc.encodeQos(OMMQos.QOS_REALTIME_TICK_BY_TICK);
        enc.encodeAggregateComplete(); // Completes the Array.

        enc.encodeAggregateComplete(); // Completes the INFO ElementList
    }

    private void encodeStateElementList(OMMEncoder enc)
    {
        enc.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        encodePersistentState(enc);
        enc.encodeAggregateComplete(); // Completes the STATE ElementList
    }

    private void encodePersistentState(OMMEncoder enc)
    {
        enc.encodeElementEntryInit(RDMService.SvcState.ServiceState, OMMTypes.UINT);
        enc.encodeUInt((long)_state);
        enc.encodeElementEntryInit(RDMService.SvcState.AcceptingRequests, OMMTypes.UINT);
        enc.encodeUInt((long)_acceptingRequests);
    }

    public void encodeStateUpdate(OMMEncoder enc, byte streamState, byte dataState, short code,
            String text)
    {
        enc.encodeMapEntryInit(0, OMMMapEntry.Action.ADD, null);
        enc.encodeString(_serviceName, OMMTypes.ASCII_STRING);

        enc.encodeFilterListInit(0, OMMTypes.ELEMENT_LIST, 0);
        enc.encodeFilterEntryInit(0, OMMFilterEntry.Action.SET, RDMService.FilterId.STATE,
                                  OMMTypes.ELEMENT_LIST, null);

        enc.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        encodePersistentState(enc);
        enc.encodeElementEntryInit(RDMService.SvcState.Status, OMMTypes.STATE);
        enc.encodeState(streamState, dataState, code, text);
        enc.encodeAggregateComplete(); // Completes the STATE ElementList

        enc.encodeAggregateComplete(); // Completes the FilterList
    }
}
