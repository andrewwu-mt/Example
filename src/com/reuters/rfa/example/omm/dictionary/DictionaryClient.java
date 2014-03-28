package com.reuters.rfa.example.omm.dictionary;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMSeries;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMDictionary;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * Client class that handle request and response for dictionary.
 * <p>
 * This class is responsible for the following:
 * <ul>
 * <li>Encodes dictionary request for
 * {@linkplain com.reuters.rfa.rdm.RDMDictionary.Filter#INFO info} or
 * {@linkplain com.reuters.rfa.rdm.RDMDictionary.Filter#NORMAL full} and
 * registers it to RFA
 * <li>Implement a Client which processes events from a <code>OMMConsumer</code>
 * to process dictionary responses</li>
 * <li>Process dictionary information, compare version and ID with local files.
 * <li>Process dictionary data from network into {@link FieldDictionary}
 * <li>Display field and enumeration dictionaries or dump in to files
 * <li>Unregistered/closed dictionary requests.
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder},
 * {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer} from
 * DictionaryDemo
 * 
 * @see DictionaryDemo
 * @see DictionaryUtil
 * 
 */
public class DictionaryClient implements Client
{
    private HashMap<Handle, Integer> _dictHandles; // Key handle : Value
                                                   // dictionaryType
    private DictionaryDemo _mainApp;
    private FieldDictionary _downloadedDictionary;
    private String _serviceName;
    private String _fieldDictName;
    private String _enumDictName;

    private static byte RDM_MATCH = 0x01; // RDM field from network has the same
                                          // version and ID as local file
    private static byte ENUM_MATCH = 0x10; // Enumtable from network has the
                                           // same version and ID as local file
    private static byte RDM_COMPLETE = 0x01; // Received complete RDM field from
                                             // network or use local(if
                                             // version is the same)
    private static byte ENUM_COMPLETE = 0x10; // Received complete Enumtable
                                              // from network or use local(if
                                              // version is the same)
    private byte _dictInfo; // flag to check version of RDM and Enumtable
                            // between network and file
    private byte _fullDict; // flag to check that RDM and Enumtable are complete

    private String _className = "DictionaryClient";

    protected DictionaryClient(DictionaryDemo mainApp, String serviceName)
    {
        _mainApp = mainApp;
        _serviceName = serviceName;
        _downloadedDictionary = FieldDictionary.create();
        _dictHandles = new HashMap<Handle, Integer>();
    }

    /**
     * Encodes an <code>OMMMsg</code> to request dictionary
     * {@linkplain com.reuters.rfa.rdm.RDMDictionary.Filter#INFO info} and
     * registers it to RFA
     * 
     * @param dictionaryName the name of dictionary i.e. RWFEnum, RWFFld
     */
    protected void openDictionaryInfo(String dictionaryName)
    {
        OMMPool pool = _mainApp.getPool();

        OMMMsg msg = pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        
        OMMAttribInfo attribInfo = pool.acquireAttribInfo();
        attribInfo.setName(dictionaryName);
        attribInfo.setServiceName(_serviceName);
        attribInfo.setFilter(RDMDictionary.Filter.INFO);
        msg.setAttribInfo(attribInfo);

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
        ommItemIntSpec.setMsg(msg);
        Handle handle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(),
                                                                 ommItemIntSpec, this, null);
        _dictHandles.put(handle, null);

        System.out.println(_className + ".openDictionaryInfo: Downloading " + dictionaryName
                           + " info from " + _serviceName);
        _mainApp.getPool().releaseMsg(msg);
    }

    /**
     * Encodes an <code>OMMMsg</code> for
     * {@linkplain com.reuters.rfa.rdm.RDMDictionary.Filter#NORMAL full}
     * dictionary request and registers it to RFA
     * 
     * @param dictionaryName the name of dictionary i.e. RWFEnum, RWFFld
     */
    protected void openFullDictionary(String dictionaryName)
    {
        OMMPool pool = _mainApp.getPool();

        OMMMsg msg = pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH
                               | OMMMsg.Indication.NONSTREAMING);
        
        OMMAttribInfo attribInfo = pool.acquireAttribInfo();
        attribInfo.setName(dictionaryName);
        attribInfo.setServiceName(_serviceName);
        attribInfo.setFilter(RDMDictionary.Filter.NORMAL);
        msg.setAttribInfo(attribInfo);

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
        ommItemIntSpec.setMsg(msg);
        Handle handle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(),
                                                                 ommItemIntSpec, this, null);
        _dictHandles.put(handle, null);
        System.out.println(_className + ".openFullDictionary: Downloading full " + dictionaryName
                           + " from " + _serviceName);
        _mainApp.getPool().releaseMsg(msg);
    }

    /**
     * Modify the existing event stream, the one that uses for dictionary info. <br>
     * Encodes an <code>OMMMsg</code> for
     * {@linkplain com.reuters.rfa.rdm.RDMDictionary.Filter#NORMAL full}
     * dictionary request and registers it to RFA.
     * 
     * @param dictionaryName the name of dictionary i.e. RWFEnum, RWFFld
     * @param handle the handle of existing event stream
     */
    private void openFullDictionary(String dictionaryName, Handle handle)
    {
        OMMPool pool = _mainApp.getPool();

        OMMMsg msg = pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        
        OMMAttribInfo attribInfo = pool.acquireAttribInfo();
        attribInfo.setName(dictionaryName);
        attribInfo.setServiceName(_serviceName);
        attribInfo.setFilter(RDMDictionary.Filter.NORMAL);
        msg.setAttribInfo(attribInfo);

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
        ommItemIntSpec.setMsg(msg);
        _mainApp.getOMMConsumer().reissueClient(handle, ommItemIntSpec);
        // No need to add handle to the list, this handle already existed.
        _mainApp.getPool().releaseMsg(msg);
    }

    public void processEvent(Event event)
    {

        if (event.getType() == Event.COMPLETION_EVENT)
        {
            System.out.println(_className + ": Receive a COMPLETION_EVENT, " + event.getHandle());
            return;
        }

        if (event.getType() != Event.OMM_ITEM_EVENT)
        {
            System.out.println("ERROR: " + _className + " Received a unsupported Event type.");
            _mainApp.cleanup(-1);
        }

        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg respMsg = ie.getMsg();
        if (respMsg.getMsgModelType() != RDMMsgTypes.DICTIONARY)
        {
            System.out.println("ERROR: " + _className
                    + ".processEvent: Received a non-DICTIONARY model type.");
            _mainApp.cleanup(-1);
        }

        if (respMsg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
        {
            if (respMsg.getAttribInfo().getFilter() == RDMDictionary.Filter.INFO)
            {
                processDictionaryInfo(respMsg, event.getHandle());
            }
            else
            {
                processFullDictionary(respMsg, event.getHandle());
            }
        }
        else
        {
            System.out.println(_className + ": Received Dictionary Response - "
                    + OMMMsg.MsgType.toString(respMsg.getMsgType()));
            GenericOMMParser.parse(respMsg);
        }

        if (respMsg.isFinal())
        {
            _dictHandles.remove(event.getHandle());
        }
    }

    /**
     * Parses dictionary information for network and compare with the local
     * dictionary
     * <ul>
     * <li>if the version or ID are different, request full data dictionary from
     * network</li>
     * <li>if they are the same, use dictionary from file</li>
     * </ul>
     * 
     * @param msg the {@link OMMMsg} of dictionary refresh message
     * @param handle the handle of the message
     */
    private void processDictionaryInfo(OMMMsg msg, Handle handle)
    {
        String dictName = msg.getAttribInfo().getName();
        System.out.println(_className + ".processDictionaryInfo: Receive dictionary info of "
                + dictName);

        // The message contain only summary data
        GenericOMMParser.parse(msg);

        if (msg.getDataType() != OMMTypes.SERIES)
        {
            System.out.println("ERROR: " + _className
                               + ".processEvent() incorrect data type, payload data must be OMMSeries");
            return;
        }
        OMMSeries series = (OMMSeries)msg.getPayload();
        if (series.has(OMMSeries.HAS_SUMMARY_DATA))
        {
            if (series.getSummaryData().getType() != OMMTypes.ELEMENT_LIST)
            {
                System.out.println("ERROR: " + _className
                                   + ".processDictionaryInfo summary data must be OMMElementList");
                return;
            }
            OMMElementList summaryData = (OMMElementList)series.getSummaryData();
            Iterator<?> iter = summaryData.iterator();
            String version = "N/A";
            int dictionaryId = 0, dictionaryType = RDMDictionary.Type.UNSPECIFIED;
            while (iter.hasNext())
            {
                OMMElementEntry entry = (OMMElementEntry)iter.next();
                if (entry.getName().equals(RDMDictionary.Summary.Version))
                    version = entry.getData().toString();
                else if (entry.getName().equals(RDMDictionary.Summary.DictionaryId))
                    dictionaryId = (int)((OMMNumeric)entry.getData()).getLongValue();
                else if (entry.getName().equals(RDMDictionary.Summary.Type))
                    dictionaryType = (int)((OMMNumeric)entry.getData()).toLong();
            }

            boolean sameVersion = true;
            FieldDictionary localDict = _mainApp.getLocalDictionary();
            if (dictionaryType == RDMDictionary.Type.FIELD_DEFINITIONS)
            {
                // Check field dictionary version
                sameVersion = (dictionaryId == localDict.getDictId())
                        && version.equals(localDict.getFieldProperty("Version"));
                if (sameVersion)
                {
                    try
                    {
                        _fieldDictName = dictName;
                        FieldDictionary.readRDMFieldDictionary(_downloadedDictionary, CommandLine
                                .variable("rdmFieldDictionary"));
                        _dictInfo |= RDM_MATCH;
                        _fullDict |= RDM_COMPLETE;
                    }
                    catch (DictionaryException e)
                    {
                        // Error during read from file, download it instead.
                        sameVersion = false;
                    }
                }
            }
            else if (dictionaryType == RDMDictionary.Type.ENUM_TABLES)
            {
                // Check enum dictionary version
                sameVersion = (dictionaryId == localDict.getDictId())
                        && version.equals(localDict.getEnumProperty("Version"));
                if (sameVersion)
                {
                    try
                    {
                        _enumDictName = dictName;
                        FieldDictionary.readEnumTypeDef(_downloadedDictionary,
                                                        CommandLine.variable("enumType"));
                        _dictInfo |= ENUM_MATCH;
                        _fullDict |= ENUM_COMPLETE;
                    }
                    catch (DictionaryException e)
                    {
                        // Error during read from file, download it instead.
                        sameVersion = false;
                    }
                }
            }

            if (sameVersion)
            {
                System.out.println(_className + ": " + dictName
                                   + " from network has the same verion and ID as the local, use the local file");
                if (_dictInfo == (RDM_MATCH | ENUM_MATCH))
                {
                    System.out.println(_className
                                       + ": Field and EnumTable from network have the same version and ID as local files.");
                    System.out.println(_className + ": Preparing to clean up and exit...");
                    _mainApp.cleanup(0);
                }
            }
            else
            {
                System.out.println(_className + ": " + dictName
                                   + " from network has different version or ID, going to download full dictionary from network.");
                openFullDictionary(dictName, handle);
            }
        }
    }

    /**
     * Processes dictionary data from network into {@link FieldDictionary}. When
     * field and enumeration dictionaries are completely load into
     * <code>FieldDictionary</code>, it will displays or dumps to files. Then
     * exit the application
     * 
     * @param msg the {@link OMMMsg} of dictionary refresh message
     */
    private void processFullDictionary(OMMMsg msg, Handle handle)
    {
        String dictName = msg.getAttribInfo().getName();
        System.out.println(_className + ".processFullDictionary: Receive full dictionary of "
                + dictName);
        if (msg.getDataType() != OMMTypes.SERIES)
        {
            System.out.println("ERROR: " + _className
                    + ".processEvent() incorrect data type, payload data must be OMMSeries");
            return;
        }
        OMMSeries series = (OMMSeries)msg.getPayload();
        int dictionaryType;
        Integer dictType = (Integer)_dictHandles.get(handle);
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
            FieldDictionary.decodeRDMFldDictionary(_downloadedDictionary, series);
            if (msg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
            {
                _fieldDictName = dictName;
                _fullDict |= RDM_COMPLETE;
                System.out.println(_className
                        + ".processFullDictionary: Field Dictionary Refresh is complete");
            }
        }
        else if (dictionaryType == RDMDictionary.Type.ENUM_TABLES)
        {
            FieldDictionary.decodeRDMEnumDictionary(_downloadedDictionary, series);
            if (msg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
            {
                _enumDictName = dictName;
                _fullDict |= ENUM_COMPLETE;
                System.out.println(_className
                        + ".processFullDictionary: EnumTable Dictionary Refresh is complete");
            }
        }

        if (_fullDict == (RDM_COMPLETE | ENUM_COMPLETE))
        {
            if (CommandLine.booleanVariable("dumpFiles"))
            {
                try
                {

                    // Field Dictionary
                    File file1 = new File(_fieldDictName + "_network");
                    PrintStream ps1 = new PrintStream(new FileOutputStream(file1));
                    System.out.println(_className
                            + ".processFullDictionary: dump Field Dictionary in "
                            + file1.getAbsolutePath());
                    DictionaryUtil.printFieldDictionary(_downloadedDictionary, ps1);
                    ps1.close();

                    // EnumTable Dictionary
                    File file2 = new File(_enumDictName + "_network");
                    PrintStream ps2 = new PrintStream(new FileOutputStream(file2));
                    System.out.println(_className + ".processFullDictionary: dump EnumTable in "
                            + file2.getAbsolutePath());
                    DictionaryUtil.printEnumDictionary(_downloadedDictionary, ps2);
                    ps2.close();
                }
                catch (IOException ex)
                {
                    System.out.println("ERROR : " + _className
                            + ".processFullDictionary, cannot print the dictionaries to files");
                    System.out.println(ex.getMessage());
                }
            }
            else
            {
                System.out.println(_className + ".processFullDictionary: Printing Field Dictionary");
                DictionaryUtil.printFieldDictionary(_downloadedDictionary);

                System.out.println(_className + ".processFullDictionary: Printing Enum Dictionary");
                DictionaryUtil.printEnumDictionary(_downloadedDictionary);
            }

            // Finish data dictionary display, exit the program.
            System.out.println(_className + ": Finishing data dictionary display, prepare to clean up and exit");
            _mainApp.cleanup(0);
        }
    }

    int getDictionaryType(OMMSeries series)
    {
        int dictionaryType = RDMDictionary.Type.UNSPECIFIED;

        if (series.has(OMMSeries.HAS_SUMMARY_DATA))
        {
            if (series.getSummaryData().getType() != OMMTypes.ELEMENT_LIST)
            {
                System.out.println("ERROR: " + _className
                        + ".getDictionarytype summary data must be OMMElementList");
                return dictionaryType;
            }
            OMMElementList summaryData = (OMMElementList)series.getSummaryData();
            OMMElementEntry entry = summaryData.find(RDMDictionary.Summary.Type);
            if (entry != null)
            {
                dictionaryType = (int)((OMMNumeric)entry.getData()).toLong();
            }
        }
        return dictionaryType;
    }

    // TODO Remove unused code:
    // /**
    // * Unregisters/un-subscribes all dictionary handles
    // */
    // public void closeRequest()
    // {
    // Iterator iter = _dictHandles.keySet().iterator();
    // while (iter.hasNext())
    // {
    // Handle handle = (Handle) iter.next();
    // _mainApp.getOMMConsumer().unregisterClient(handle);
    // }
    // _dictHandles.clear();
    // }
}
