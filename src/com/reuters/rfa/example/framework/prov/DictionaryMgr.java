package com.reuters.rfa.example.framework.prov;

import java.util.HashMap;
import java.util.Map;

import com.reuters.rfa.common.Token;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;

/**
 * DictionaryMgr is a domain manager which handles Dictionary domain. This class
 * is responsible for loading Field Dicitionary and Enum Dictionary from the
 * file. The path of dictionary file is referred from rdmFieldDictionary and
 * enumType configuration parameter. It can encodes Dictionary response message
 * and sends the encoded message to a client that request for Dictionary.
 * 
 */
public class DictionaryMgr extends AbstractProvDomainMgr
{
    String _rdmDictionaryName;
    OMMEncoder _enc = null; // OMMPool.createEncoder(140000); // Dictionary
                            // tends to be large messages. So 140k bytes is needed here.
    String _enumType;
    Map<String, DictionaryStreamItem> _dictionaries;
    FieldDictionary _fieldDictionary;

    public DictionaryMgr(PubAppContext appContext, String serviceName)
    {
        super(appContext, RDMMsgTypes.DICTIONARY, serviceName);
        _enc = getPool().acquireEncoder();
        _enc.initialize(OMMTypes.MSG, 250000);// Dictionary tends to be large
                                              // messages. So 160k bytes is needed here.
        _dictionaries = new HashMap<String, DictionaryStreamItem>();
    }

    /**
     * Loads RDM Field Dicitonary and Enum Dicitonary from file.
     * 
     */
    public void autoDictionary()
    {
        if (loadFieldDicitonary())
        {
            _pubContext.addDomainMgr(this);
            // DictionaryStreamItems auto-add themselves back into the
            // DictionaryMgr
            FieldDictionaryStreamItem flddict = new FieldDictionaryStreamItem(this, "RWFFld");
            flddict.setFieldDictionary(_fieldDictionary);
            EnumDictionaryStreamItem enumdict = new EnumDictionaryStreamItem(this, "RWFEnum");
            enumdict.setFieldDictionary(_fieldDictionary);
        }
    }

    private boolean loadFieldDicitonary()
    {
        try
        {
            _fieldDictionary = FieldDictionary.create();
            String fieldfilename = CommandLine.variable("rdmFieldDictionary");
            FieldDictionary.readRDMFieldDictionary(_fieldDictionary, fieldfilename);
            _pubContext.getPrintStream().println("Loaded RDM Field Dicitonary: " + fieldfilename);
            String enumfilename = CommandLine.variable("enumType");
            FieldDictionary.readEnumTypeDef(_fieldDictionary, enumfilename);
            _pubContext.getPrintStream().println("Loaded Enum Dicitonary: " + enumfilename);
            _pubContext.setDictionary(_fieldDictionary);
            return true;
        }
        catch (DictionaryException e)
        {
            _pubContext.getPrintStream().println(e.getMessage());
        }
        return false;
    }

    public void processReqMsg(ClientSessionMgr clientSessionMgr, Token token, OMMMsg reqmsg)// OMMSolicitedItemEvent
                                                                                            // event)
    {
        System.out.println("Dictionary request received");
        OMMAttribInfo attribInfo = reqmsg.getAttribInfo();
        String name = attribInfo.getName();
        System.out.println("dictionary name: " + name);

        DictionaryStreamItem item = (DictionaryStreamItem)_dictionaries.get(name);
        OMMMsg encmsg = (item == null) ? encodeClosedStatus(reqmsg.getAttribInfo(), "Unknown dictionary") :
            item.encodeMsg(reqmsg.getAttribInfo().getFilter(),
                           reqmsg.isSet(OMMMsg.Indication.REFRESH));

        submit(token, encmsg);
        getPool().releaseMsg(encmsg);
    }

    /*
     * // Encoding of the enum dictionary private OMMMsg
     * encodeEnumDictionary(OMMEncoder enc, FieldDictionary dictionary) { //
     * This is the typical initialization of an response message.
     * enc.initialize(OMMTypes.MSG, 250000); OMMMsg msg =
     * getPool().acquireMsg(); msg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
     * msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
     * msg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
     * msg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
     * msg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE,
     * ""); msg.setItemGroup(1); OMMAttribInfo attribInfo =
     * getPool().acquireAttribInfo(); attribInfo.setServiceName("DIRECT_FEED");
     * attribInfo.setName("RWFEnum");
     * attribInfo.setFilter(RDMDictionary.Filter.NORMAL);
     * msg.setAttribInfo(attribInfo); enc.encodeMsgInit(msg, OMMTypes.NO_DATA,
     * OMMTypes.SERIES); FieldDictionary.encodeRDMEnumDictionary(dictionary,
     * enc); // private helper method that will encode the series. return
     * (OMMMsg) enc.getEncodedObject(); }
     * 
     * // Encoding of the RDMFieldDictionary. private OMMMsg
     * encodeFldDictionary(OMMEncoder enc, FieldDictionary dictionary) { // This
     * is the same message initialization as other response messages.
     * enc.initialize(OMMTypes.MSG, 250000); OMMMsg msg =
     * getPool().acquireMsg(); msg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
     * msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
     * msg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
     * msg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
     * msg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE,
     * ""); msg.setItemGroup(1); OMMAttribInfo attribInfo =
     * getPool().acquireAttribInfo(); attribInfo.setServiceName("DIRECT_FEED");
     * attribInfo.setName("RWFFld");
     * attribInfo.setFilter(RDMDictionary.Filter.NORMAL); // Specifies all of
     * the normally needed data will be sent. msg.setAttribInfo(attribInfo);
     * enc.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.SERIES); // Data is
     * Series. FieldDictionary.encodeRDMFieldDictionary(dictionary, enc); //
     * Private helper method that will encode the series. Please see this method
     * for // more details on Series encoding, along with data definition
     * encoding. return (OMMMsg) enc.getEncodedObject(); }
     */
    public void processCloseReqMsg(ClientSessionMgr clientSessionMgr, Token token,
            StreamItem streamItem)
    {
        // RFA internally will clean up the item.
        // Currently, Application never placed the directory on
        // its item info lookup table. So no application cleanup is needed here.
    }

    public void processReReqMsg(ClientSessionMgr clientSessionMgr, Token token, StreamItem item,
            OMMMsg reqmsg)
    {
        // reissue, only send refresh resp if requested.
        if (!reqmsg.isSet(OMMMsg.Indication.REFRESH))
            return; // no refresh request
        
        OMMMsg encmsg = ((DictionaryStreamItem)item).encodeMsg(reqmsg.getAttribInfo().getFilter(),
                                                               reqmsg.isSet(OMMMsg.Indication.REFRESH));

        submit(token, encmsg);
        getPool().releaseMsg(encmsg);
    }

    /**
     * Adds dictionary stream item to this dictionary domain.
     * 
     * @param name a dictionary name.
     * @param defStreamItem a dictionary stream item.
     */
    public void addDictionaryStreamItem(String name, DictionaryStreamItem defStreamItem)
    {
        _dictionaries.put(name, defStreamItem);
        ServiceInfo si = _pubContext.directoryMgr().getServiceInfo(_serviceName);
        si.addDictionaryProvided(defStreamItem._name);
    }

    public String getServiceName()
    {
        return _serviceName;
    }

    public StreamItem createStreamItem(Token token, OMMMsg msg)
    {
        return null; // not used
    }

}
