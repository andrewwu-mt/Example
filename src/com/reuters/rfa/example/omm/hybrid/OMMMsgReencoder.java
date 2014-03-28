package com.reuters.rfa.example.omm.hybrid;

import java.util.Calendar;
import java.util.Iterator;

import com.reuters.rfa.common.PublisherPrincipalIdentity;
import com.reuters.rfa.dictionary.DataDefDictionary;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMArray;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMDataBuffer;
import com.reuters.rfa.omm.OMMDateTime;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMEnum;
import com.reuters.rfa.omm.OMMFieldEntry;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMFilterEntry;
import com.reuters.rfa.omm.OMMFilterList;
import com.reuters.rfa.omm.OMMIterable;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMPriority;
import com.reuters.rfa.omm.OMMQos;
import com.reuters.rfa.omm.OMMQosReq;
import com.reuters.rfa.omm.OMMSeries;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.omm.OMMVector;
import com.reuters.rfa.omm.OMMVectorEntry;
import com.reuters.rfa.utility.HexDump;

/**
 * A utility class uses to reencode an {@link OMMMsg}.
 * 
 * <p>
 * This class is uses for generic reencoding. For simplicity, it checks every
 * property even though some of the messages don't have some properties. Not all
 * of the methods on this interface are supported for all types. If the method
 * is not supported, an {@link com.reuters.rfa.omm.OMMException OMMException}
 * will be thrown.
 * 
 * <p>
 * To reencode an message that contains {@link OMMTypes#FIELD_LIST}, the RDM
 * dictionary is required. By Using
 * {@link #initializeDictionary(String, String)} or
 * {@link #setLocalDictionary(FieldDictionary)}.
 */
public class OMMMsgReencoder
{

    protected static OMMEncoder _encoder;
    protected static OMMPool _pool;

    private static boolean _useEncodeData;
    private static boolean _useEncodeString;
    private static boolean _usePreEncodeDataDefs;

    private static FieldDictionary _localDictionary;
    private static String _className = "[OMMMsgReencoder]";

    private static boolean _bEncodePublisherInfo;
    private static PublisherPrincipalIdentity _pi;

    private static boolean _bEncodeServiceName;
    private static String _serviceName;

    static
    {
        _pool = OMMPool.create();
        _encoder = _pool.acquireEncoder();
        _encoder.initialize(OMMTypes.MSG, 10000);
        _localDictionary = FieldDictionary.create();
    }

    /**
     * Indicates whether or not uses {@link OMMEncoder#encodeData(OMMData)} when
     * encoding {@link OMMData} from {@link OMMAttribInfo#getAttrib() attrib}
     * and {@linkplain OMMMsg#getPayload() payload} data
     */
    public static boolean useEncodeData()
    {
        return _useEncodeData;
    }

    /**
     * Indicates whether or not uses
     * {@link OMMEncoder#encodeString(String, short)} when encoding every data
     * type except the following: <br>
     * STATE, QOS, OoSReq, ARRAY, all list types and entry types (i.e.
     * ElementList or VectorEnry).
     */
    public static boolean useEncodeString()
    {
        return _useEncodeString;
    }

    /**
     * Indicates whether or not uses
     * {@link OMMEncoder#encodeDataDefs(com.reuters.rfa.omm.OMMDataDefs)
     * OMMEncoder#encodeDataDefs(OMMDataDefs)} when reencoding a set pre-encoded
     * data definitions of {@link OMMSeries}, {@link OMMMap} or
     * {@link OMMVector}
     * 
     */
    public static boolean usePreEncodeDataDefs()
    {
        return _usePreEncodeDataDefs;
    }

    /**
     * Determines whether or not uses {@link OMMEncoder#encodeData(OMMData)} to
     * reencode {@link OMMData} from {@link OMMAttribInfo#getAttrib() attrib}
     * and {@linkplain OMMMsg#getPayload() payload} data
     */
    public static void setEncodeData(boolean encodeData)
    {
        _useEncodeData = encodeData;
        if (_useEncodeData)
        {
            System.out.println(_className
                    + " Enable using encodeData(OMMData) when reencoding message");
        }
        else
        {
            System.out.println(_className + " Not use encodeData(OMMData) when reencoding message");
        }
    }

    /**
     * Determines whether or not uses
     * {@link OMMEncoder#encodeString(String, short)} when encoding every data
     * type except the following: <br>
     * STATE, QOS, OoSReq, ARRAY, all list types and entry types (i.e.
     * ElementList or VectorEnry).
     * 
     * <p>
     * Note: This will use only if {@link #useEncodeData()} is false
     */
    public static void setEncodeString(boolean encodeString)
    {
        _useEncodeString = encodeString;
        if (_useEncodeString)
        {
            System.out.println(_className
                    + " Enable using encodeString(String, dataType) when reencoding message");
        }
        else
        {
            System.out.println(_className
                    + " Not use encodeString(String, dataType) when reencoding message");
        }
    }

    /**
     * Determines whether or not uses
     * {@link OMMEncoder#encodeDataDefs(com.reuters.rfa.omm.OMMDataDefs)
     * OMMEncoder#encodeDataDefs(OMMDataDefs)} when reencoding a set pre-encoded
     * data definitions of {@link OMMSeries}, {@link OMMMap} or
     * {@link OMMVector}.
     * 
     * <p>
     * Note: Set this to true will work only when {@link #useEncodeData()} is
     * false
     */
    public static void setPreEncodeDataDefs(boolean preEncodeDataDefs)
    {
        _usePreEncodeDataDefs = preEncodeDataDefs;
        if (_usePreEncodeDataDefs)
        {
            System.out.println(_className
                    + " Enable using encodeDataDefs(OMMDataDefs) when reencoding message");
        }
        else
        {
            System.out.println(_className
                    + " Not use encodeDataDefs(OMMDataDefs) when reencoding message");
        }
    }

    public static void setLocalDictionary(FieldDictionary localDictionary)
    {
        if (localDictionary != null)
        {
            _localDictionary = localDictionary;
        }
    }

    public static FieldDictionary getLocalDictionary()
    {
        return _localDictionary;
    }

    /**
     * Initialize local dictionary from dictionary file. The local dictionary
     * need be initialized before encode the messages that contain
     * {@link OMMTypes#FIELD_LIST}
     */
    public static void initializeDictionary(String fieldDictionaryFilename,
            String enumDictionaryFilename)
    {
        try
        {
            FieldDictionary.readRDMFieldDictionary(_localDictionary, fieldDictionaryFilename);
            System.out.println(_className + " field dictionary read from "
                    + fieldDictionaryFilename);

            FieldDictionary.readEnumTypeDef(_localDictionary, enumDictionaryFilename);
            System.out.println(_className + " enum dictionary read from " + enumDictionaryFilename);
        }
        catch (DictionaryException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Loads field definition into local dictionary
     * 
     * @param series - payload data from dictionary response of
     *            {@link com.reuters.rfa.rdm.RDMDictionary.Type#FIELD_DEFINITIONS
     *            RWFFld}
     * @throws DictionaryException if series cannot be decoded
     */
    public static void setRDMFldDictionary(OMMSeries series)
    {
        FieldDictionary.decodeRDMFldDictionary(_localDictionary, series);
    }

    /**
     * Loads enumeration into local dictionary
     * 
     * @param series - payload data from dictionary response of RWFEnum
     * @throws DictionaryException if series cannot be decoded
     */
    public static void setRDMEnumDictionary(OMMSeries series)
    {
        FieldDictionary.decodeRDMEnumDictionary(_localDictionary, series);
    }

    /**
     * Decodes the specified message, reencodes it and returns a new encoded
     * OMMMsg.
     * 
     * @param msg OMMMsg that will be decode and reencode into a new OMMMsg
     * @param encoderSize reinitialize encoder with a buffer of the given size
     * @return a new OMMMsg that re-encode from the msg
     */
    public static OMMMsg getEncodeMsgfrom(OMMMsg msg, int encoderSize)
    {
        short attibDataType = OMMTypes.NO_DATA;
        short dataType = OMMTypes.NO_DATA; // payload data

        _encoder.initialize(OMMTypes.MSG, encoderSize);
        OMMMsg newMsg = _pool.acquireMsg();
        OMMAttribInfo newAi = null;
        newMsg.setMsgType(msg.getMsgType());
        newMsg.setMsgModelType(msg.getMsgModelType());
        newMsg.setIndicationFlags(getIndicationFlagsFromMsg(msg));

        // 10 Hint flags not include HEADER
        if (msg.has(OMMMsg.HAS_STATE))
        {
            OMMState s = msg.getState();
            newMsg.setState(s.getStreamState(), s.getDataState(), s.getCode(), s.getText());
        }
        if (msg.has(OMMMsg.HAS_PRIORITY))
        {
            OMMPriority p = msg.getPriority();
            newMsg.setPriority(p.getPriorityClass(), p.getCount());
        }
        if (msg.has(OMMMsg.HAS_QOS))
        {
            newMsg.setQos(msg.getQos());
        }
        if (msg.has(OMMMsg.HAS_QOS_REQ))
        {
            newMsg.setQosReq(msg.getQosReq());
        }
        if (msg.has(OMMMsg.HAS_ITEM_GROUP))
        {
            newMsg.setItemGroup(msg.getItemGroup());
        }
        if (msg.has(OMMMsg.HAS_PERMISSION_DATA))
        {
            newMsg.setPermissionData(msg.getPermissionData());
        }
        if (msg.has(OMMMsg.HAS_SEQ_NUM))
        {
            newMsg.setSeqNum(msg.getSeqNum());
        }
        if (msg.has(OMMMsg.HAS_CONFLATION_INFO))
        {
            newMsg.setConflationInfo(msg.getConflationCount(), msg.getConflationTime());
        }
        if (msg.has(OMMMsg.HAS_RESP_TYPE_NUM))
        {
            newMsg.setRespTypeNum(msg.getRespTypeNum());

        }

        // introduced in RFAJ6.5(July 2010) - Posting Feature;
        // applicable for setting publisher info on update,refresh,status OMMMsg
        if (_bEncodePublisherInfo == true)
        {
            newMsg.setPrincipalIdentity(_pi);
        }

        if (msg.has(OMMMsg.HAS_ATTRIB_INFO))
        {
            OMMAttribInfo ai = msg.getAttribInfo();
            newAi = _pool.acquireAttribInfo();

            // 6 Hint flags
            // applicable for setting service name on update,refresh,status
            // OMMMsg
            // that came with a Post Message;
            if (_bEncodeServiceName == true)
            {
                newAi.setServiceName(_serviceName);
            }
            else
            {
                if (ai.has(OMMAttribInfo.HAS_SERVICE_NAME))
                {
                    newAi.setServiceName(ai.getServiceName());
                }
            }

            if (ai.has(OMMAttribInfo.HAS_NAME))
            {
                newAi.setName(ai.getName());
            }
            if (ai.has(OMMAttribInfo.HAS_NAME_TYPE))
            {
                newAi.setNameType(ai.getNameType());
            }
            if (ai.has(OMMAttribInfo.HAS_FILTER))
            {
                newAi.setFilter(ai.getFilter());
            }
            if (ai.has(OMMAttribInfo.HAS_ID))
            {
                newAi.setId(ai.getId());
            }
            if (ai.has(OMMAttribInfo.HAS_ATTRIB))
            {
                attibDataType = ai.getAttribType();
            }
            newMsg.setAttribInfo(newAi);
        }

        // For payload data
        if (msg.getDataType() != OMMTypes.NO_DATA)
        {
            dataType = msg.getDataType();
        }

        if ((attibDataType == OMMTypes.NO_DATA) && (dataType == OMMTypes.NO_DATA))
        {
            _encoder.encodeMsg(newMsg);
        }
        else
        {
            _encoder.encodeMsgInit(newMsg, attibDataType, dataType);
            if (attibDataType != OMMTypes.NO_DATA)
            {
                if (!useEncodeData())
                {
                    encodeDataFrom(msg.getAttribInfo().getAttrib());
                }
                else
                {
                    _encoder.encodeData(msg.getAttribInfo().getAttrib());
                }
            }
            if (dataType != OMMTypes.NO_DATA)
            {
                if (!useEncodeData())
                {
                    encodeDataFrom(msg.getPayload());
                }
                else
                {
                    _encoder.encodeData(msg.getPayload());
                }
            }
        }

        // OMMMsg encodedMsg = (OMMMsg)_encoder.getEncodedObject();
        OMMMsg encodedMsg = (OMMMsg)_encoder.acquireEncodedObject();
        _pool.releaseMsg(newMsg);
        if (newAi != null)
        {
            _pool.releaseAttribInfo(newAi);
        }

        return encodedMsg;
    }

    public static OMMMsg changeResponseTypeToUnsolicited(OMMMsg msg, int encoderSize)
    {
        short attibDataType = OMMTypes.NO_DATA;
        short dataType = OMMTypes.NO_DATA; // payload data

        _encoder.initialize(OMMTypes.MSG, encoderSize);
        OMMMsg newMsg = _pool.acquireMsg();
        OMMAttribInfo newAi = null;
        newMsg.setMsgType(msg.getMsgType());
        newMsg.setMsgModelType(msg.getMsgModelType());
        newMsg.setIndicationFlags(getIndicationFlagsFromMsg(msg));

        // 10 Hint flags not include HEADER
        if (msg.has(OMMMsg.HAS_STATE))
        {
            OMMState s = msg.getState();
            newMsg.setState(s.getStreamState(), s.getDataState(), s.getCode(), s.getText());
        }
        if (msg.has(OMMMsg.HAS_PRIORITY))
        {
            OMMPriority p = msg.getPriority();
            newMsg.setPriority(p.getPriorityClass(), p.getCount());
        }
        if (msg.has(OMMMsg.HAS_QOS))
        {
            newMsg.setQos(msg.getQos());
        }
        if (msg.has(OMMMsg.HAS_QOS_REQ))
        {
            newMsg.setQosReq(msg.getQosReq());
        }
        if (msg.has(OMMMsg.HAS_ITEM_GROUP))
        {
            newMsg.setItemGroup(msg.getItemGroup());
        }
        if (msg.has(OMMMsg.HAS_PERMISSION_DATA))
        {
            newMsg.setPermissionData(msg.getPermissionData());
        }
        if (msg.has(OMMMsg.HAS_SEQ_NUM))
        {
            newMsg.setSeqNum(msg.getSeqNum());
        }
        if (msg.has(OMMMsg.HAS_CONFLATION_INFO))
        {
            newMsg.setConflationInfo(msg.getConflationCount(), msg.getConflationTime());
        }
        if (msg.has(OMMMsg.HAS_RESP_TYPE_NUM))
        {
            newMsg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);

        }

        // introduced in RFAJ6.5(July 2010) - Posting Feature;
        // applicable for setting publisher info on update,refresh,status OMMMsg
        if (_bEncodePublisherInfo == true)
        {
            newMsg.setPrincipalIdentity(_pi);
        }

        if (msg.has(OMMMsg.HAS_ATTRIB_INFO))
        {
            OMMAttribInfo ai = msg.getAttribInfo();
            newAi = _pool.acquireAttribInfo();

            // 6 Hint flags
            if (_bEncodeServiceName == true)
            {
                newAi.setServiceName(_serviceName);
            }
            else
            {
                if (ai.has(OMMAttribInfo.HAS_SERVICE_NAME))
                {
                    newAi.setServiceName(ai.getServiceName());
                }
            }

            if (ai.has(OMMAttribInfo.HAS_NAME))
            {
                newAi.setName(ai.getName());
            }
            if (ai.has(OMMAttribInfo.HAS_NAME_TYPE))
            {
                newAi.setNameType(ai.getNameType());
            }
            if (ai.has(OMMAttribInfo.HAS_FILTER))
            {
                newAi.setFilter(ai.getFilter());
            }
            if (ai.has(OMMAttribInfo.HAS_ID))
            {
                newAi.setId(ai.getId());
            }
            if (ai.has(OMMAttribInfo.HAS_ATTRIB))
            {
                attibDataType = ai.getAttribType();
            }
            newMsg.setAttribInfo(newAi);
        }

        // For payload data
        if (msg.getDataType() != OMMTypes.NO_DATA)
        {
            dataType = msg.getDataType();
        }

        if ((attibDataType == OMMTypes.NO_DATA) && (dataType == OMMTypes.NO_DATA))
        {
            _encoder.encodeMsg(newMsg);
        }
        else
        {
            _encoder.encodeMsgInit(newMsg, attibDataType, dataType);
            if (attibDataType != OMMTypes.NO_DATA)
            {
                _encoder.encodeData(msg.getAttribInfo().getAttrib());
            }
            if (dataType != OMMTypes.NO_DATA)
            {
                _encoder.encodeData(msg.getPayload());
            }
        }

        OMMMsg encodedMsg = (OMMMsg)_encoder.getEncodedObject();
        _pool.releaseMsg(newMsg);
        if (newAi != null)
        {
            _pool.releaseAttribInfo(newAi);
        }

        return encodedMsg;
    }

    /**
     * @return the combination of indication flags the have been set in the
     *         message specified
     */
    public static int getIndicationFlagsFromMsg(OMMMsg msg)
    {
        int flags = 0;
        // There are 7 possible indication flags.

        if (msg.isSet(OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES))
        {
            flags |= OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES;
        }
        if (msg.isSet(OMMMsg.Indication.CONFLATION_INFO_IN_UPDATES))
        {
            flags |= OMMMsg.Indication.CONFLATION_INFO_IN_UPDATES;
        }
        if (msg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
        {
            flags |= OMMMsg.Indication.REFRESH_COMPLETE;
        }
        if (msg.isSet(OMMMsg.Indication.CLEAR_CACHE))
        {
            flags |= OMMMsg.Indication.CLEAR_CACHE;
        }
        if (msg.isSet(OMMMsg.Indication.DO_NOT_CACHE))
        {
            flags |= OMMMsg.Indication.DO_NOT_CACHE;
        }
        if (msg.isSet(OMMMsg.Indication.DO_NOT_CONFLATE))
        {
            flags |= OMMMsg.Indication.DO_NOT_CONFLATE;
        }
        if (msg.isSet(OMMMsg.Indication.DO_NOT_RIPPLE))
        {
            flags |= OMMMsg.Indication.DO_NOT_RIPPLE;
        }
        if (msg.isSet(OMMMsg.Indication.VIEW))
        {
            flags |= OMMMsg.Indication.VIEW;
        }
        if (msg.isSet(OMMMsg.Indication.BATCH_REQ))
        {
            flags |= OMMMsg.Indication.BATCH_REQ;
        }

        return flags;
    }

    @SuppressWarnings("deprecation")
    private static void encodeDataFrom(OMMData data)
    {

        if (data == null)
        {
            return;
        }

        if (data.isBlank())
        {
            _encoder.encodeBlank();
            return;
        }

        if (useEncodeString())
        {
            if (!((data instanceof OMMState) || (data instanceof OMMQos)
                    || (data instanceof OMMQosReq) || (data instanceof OMMArray) || (data instanceof OMMIterable)))
            {
                _encoder.encodeString(data.toString(), data.getType());
                return;
            }
        }

        short dataType = data.getType();
        switch (dataType)
        {
            case OMMTypes.UNKNOWN:
                System.err.println("ERROR: " + _className
                        + " UNKNOWN data type, cannot encode this data");
                String rawData = HexDump.hexDump(data.getBytes(), data.getEncodedLength());
                System.out.println(rawData);
                break;

            case OMMTypes.INT32:
            case OMMTypes.INT_1:
            case OMMTypes.INT_2:
            case OMMTypes.INT_4:
            {
                OMMNumeric int32 = (OMMNumeric)data;
                _encoder.encodeInt((int)int32.toLong());
                break;
            }
            case OMMTypes.UINT32:
            case OMMTypes.UINT_1:
            case OMMTypes.UINT_2:
            case OMMTypes.UINT_4:
            {
                OMMNumeric uint32 = (OMMNumeric)data;
                _encoder.encodeUInt(uint32.toLong());
                break;
            }
            case OMMTypes.INT:
            case OMMTypes.INT_8:
            {
                OMMNumeric int64 = (OMMNumeric)data;
                _encoder.encodeInt(int64.toLong());
                break;
            }
            case OMMTypes.UINT:
            case OMMTypes.UINT_8:
            {
                OMMNumeric uint64 = (OMMNumeric)data;
                _encoder.encodeUInt(uint64.toLong());
                break;
            }
            case OMMTypes.FLOAT:
            case OMMTypes.FLOAT_4:
            {
                OMMNumeric floatValue = (OMMNumeric)data;
                _encoder.encodeFloat(floatValue.toFloat());
                break;
            }
            case OMMTypes.DOUBLE:
            case OMMTypes.DOUBLE_8:
            {
                OMMNumeric doubleValue = (OMMNumeric)data;
                _encoder.encodeDouble(doubleValue.toDouble());
                break;
            }
            case OMMTypes.REAL32:
            case OMMTypes.REAL_4RB:
            {
                OMMNumeric real32 = (OMMNumeric)data;
                _encoder.encodeReal((int)real32.getLongValue(), real32.getHint());
                break;
            }
            case OMMTypes.REAL:
            case OMMTypes.REAL_8RB:
            {
                OMMNumeric real64 = (OMMNumeric)data;
                _encoder.encodeReal(real64.getLongValue(), real64.getHint());
                break;
            }
            case OMMTypes.ENUM:
            {
                OMMEnum enumValue = (OMMEnum)data;
                _encoder.encodeEnum(enumValue.getValue());
                break;
            }
            case OMMTypes.DATE:
            case OMMTypes.DATE_4:
            {
                OMMDateTime ommDate = (OMMDateTime)data;
                Calendar calendar = ommDate.toCalendar();
                _encoder.encodeDate(calendar);
                // _encoder.encodeDate(calendar.get(Calendar.YEAR) - 1900,
                // calendar.get(Calendar.MONTH),
                // calendar.get(Calendar.DAY_OF_MONTH));
                break;
            }
            case OMMTypes.TIME:
            case OMMTypes.TIME_3:
            case OMMTypes.TIME_5:
            {
                OMMDateTime time = (OMMDateTime)data;
                _encoder.encodeTime(time.toCalendar());
                break;
            }
            case OMMTypes.DATETIME:
            case OMMTypes.DATETIME_7:
            case OMMTypes.DATETIME_9:
            {
                OMMDateTime dateTime = (OMMDateTime)data;
                _encoder.encodeDateTime(dateTime.toCalendar());
                break;
            }

            case OMMTypes.QOS:
            {
                OMMQos qos = (OMMQos)data;
                _encoder.encodeQos(qos);
                break;
            }

            case OMMTypes.STATE:
            {
                OMMState state = ((OMMState)data);
                _encoder.encodeState(state.getStreamState(), state.getDataState(), state.getCode(),
                                     state.getText());
                break;
            }

            case OMMTypes.ARRAY:
            {
                encodeArrayFrom((OMMArray)data);
                break;
            }
            case OMMTypes.UTF8_STRING:
            case OMMTypes.ASCII_STRING:
            case OMMTypes.RMTES_STRING:
            {
                // Note: String that has special special charecters i.e. RMTES,
                // UTF8
                // need to encode in a proper way.
                // The code below can be use for general string
                OMMDataBuffer stringBuffer = (OMMDataBuffer)data;
                _encoder.encodeString(stringBuffer.toString(), dataType);
                break;
            }
            case OMMTypes.BUFFER:
            case OMMTypes.OPAQUE_BUFFER:
            case OMMTypes.XML:
            case OMMTypes.ANSI_PAGE:
            {
                OMMDataBuffer buffer = (OMMDataBuffer)data;
                _encoder.encodeBytes(buffer.getBytes());
                break;
            }
            case OMMTypes.FIELD_LIST:
            {
                encodeFieldList((OMMFieldList)data);
                break;
            }
            case OMMTypes.ELEMENT_LIST:
            {
                encodeElementListFrom((OMMElementList)data);
                break;
            }
            case OMMTypes.FILTER_LIST:
            {
                encodeFilterListFrom((OMMFilterList)data);
                break;
            }
            case OMMTypes.VECTOR:
            {
                encodeVectorFrom((OMMVector)data);
                break;
            }
            case OMMTypes.MAP:
            {
                encodeMapFrom((OMMMap)data);
                break;
            }
            case OMMTypes.SERIES:
            {
                encodeSeriesFrom((OMMSeries)data);
                break;
            }
            default:
            {
                System.err.println("ERROR: " + _className + " Unsupport data type "
                        + OMMTypes.toString(dataType));
                break;
            }
        }

    }

    private static void encodeArrayFrom(OMMArray array)
    {
        _encoder.encodeArrayInit(array.getDataType(), array.getWidth());

        Iterator<?> iter = array.iterator();
        OMMEntry entry;
        while (iter.hasNext())
        {
            entry = (OMMEntry)iter.next();
            _encoder.encodeArrayEntryInit();
            encodeDataFrom(entry.getData());
        }
        _encoder.encodeAggregateComplete();
    }

    private static void encodeFieldList(OMMFieldList list)
    {
        int flags = 0;
        if (list.has(OMMFieldList.HAS_INFO))
        {
            flags |= OMMFieldList.HAS_INFO;
        }
        if (list.has(OMMFieldList.HAS_DEFINED_DATA))
        {
            flags |= OMMFieldList.HAS_DEFINED_DATA;
        }
        if (list.has(OMMFieldList.HAS_DATA_DEF_ID))
        {
            flags |= OMMFieldList.HAS_DATA_DEF_ID;
        }
        if (list.has(OMMFieldList.HAS_STANDARD_DATA))
        {
            flags |= OMMFieldList.HAS_STANDARD_DATA;
        }

        if (list.getEncodedLength() < 253)
            _encoder.useSize(OMMEncoder.SMALL_SIZE);
        _encoder.encodeFieldListInit(flags, list.getDictId(), list.getListNum(),
                                     list.getDataDefId());
        if (list.getEncodedLength() < 253)
            _encoder.useSize(OMMEncoder.LARGE_SIZE);

        // Iterate FieldList entries
        Iterator<?> iter = list.iterator();
        OMMFieldEntry fieldEntry;
        // Define Data first, no need to call encodeFieldEntryInit because we
        // have defined types already
        if (list.has(OMMFieldList.HAS_DEFINED_DATA))
        {
            int defineCount = list.getCount() - list.getStandardCount();
            for (int i = 0; i < defineCount; i++)
            {
                fieldEntry = (OMMFieldEntry)iter.next();
                encodeDataFrom(fieldEntry.getData());
            }
        }

        // The rest of them are standard data entries.
        // Standard data come after define data.
        short fieldType = OMMTypes.UNKNOWN;
        while (iter.hasNext())
        {
            fieldEntry = (OMMFieldEntry)iter.next();
            // For standard data FieldEntry.getDataType() will return
            // OMMTypes.UNKNOWN.
            // We need to look up field type from dictionary
            FidDef fiddef = _localDictionary.getFidDef(fieldEntry.getFieldId());
            if (fiddef != null)
            {
                fieldType = fiddef.getOMMType();
                _encoder.encodeFieldEntryInit(fieldEntry.getFieldId(), fieldType);
                encodeDataFrom(fieldEntry.getData(fieldType));
            }
            else
            {
                System.err.println("ERROR: " + _className + " Received field id: "
                        + fieldEntry.getFieldId() + " - Not defined in dictionary");
                String rawData = HexDump.hexDump(fieldEntry.getData().getBytes(), fieldEntry
                        .getData().getEncodedLength());
                System.out.println(rawData);
            }
        }

        // Call encodeAggregateComplete() only when the list has standard data
        // If the list has only define data, we don't need to call this. Because
        // we know exactly how many entries this element list contains
        // (the number of definitions encoded into the data definitions)
        if (list.getStandardCount() > 0)
        {
            _encoder.encodeAggregateComplete();
        }
    }

    private static void encodeElementListFrom(OMMElementList list)
    {
        int flags = 0;
        if (list.has(OMMElementList.HAS_INFO))
        {
            flags |= OMMElementList.HAS_INFO;
        }
        if (list.has(OMMElementList.HAS_DEFINED_DATA))
        {
            flags |= OMMElementList.HAS_DEFINED_DATA;
        }
        if (list.has(OMMElementList.HAS_DATA_DEF_ID))
        {
            flags |= OMMElementList.HAS_DATA_DEF_ID;
        }
        if (list.has(OMMElementList.HAS_STANDARD_DATA))
        {
            flags |= OMMElementList.HAS_STANDARD_DATA;
        }

        if (list.getEncodedLength() < 253)
            _encoder.useSize(OMMEncoder.SMALL_SIZE);
        _encoder.encodeElementListInit(flags, list.getListNum(), list.getDataDefId());
        if (list.getEncodedLength() < 253)
            _encoder.useSize(OMMEncoder.LARGE_SIZE);

        // Iterate ElementList entries
        Iterator<?> iter = list.iterator();
        OMMElementEntry elementEntry;
        // Define Data first, no need to call encodeElementEntryInit because we
        // have defined types already
        if (list.has(OMMElementList.HAS_DEFINED_DATA))
        {
            int defineCount = list.getCount() - list.getStandardCount();
            for (int i = 0; i < defineCount; i++)
            {
                elementEntry = (OMMElementEntry)iter.next();
                encodeDataFrom(elementEntry.getData());
            }
        }

        // The rest of them are standard data entries.
        // Standard data come after define data.
        while (iter.hasNext())
        {
            elementEntry = (OMMElementEntry)iter.next();
            _encoder.encodeElementEntryInit(elementEntry.getName(), elementEntry.getDataType());
            encodeDataFrom(elementEntry.getData());
        }

        // Call encodeAggregateComplete() only when the list has standard data
        // If the list has only define data, we don't need to call this. Because
        // we know exactly how many entries this element list contains
        // (the number of definitions encoded into the data definitions)
        if (list.getStandardCount() > 0)
        {
            _encoder.encodeAggregateComplete();
        }
    }

    private static void encodeFilterListFrom(OMMFilterList filter)
    {
        int flags = 0;
        if (filter.has(OMMFilterList.HAS_PERMISSION_DATA_PER_ENTRY))
        {
            flags |= OMMFilterList.HAS_PERMISSION_DATA_PER_ENTRY;
        }
        if (filter.has(OMMFilterList.HAS_TOTAL_COUNT_HINT))
        {
            flags |= OMMFilterList.HAS_TOTAL_COUNT_HINT;
        }

        // Header
        _encoder.encodeFilterListInit(flags, filter.getDataType(), filter.getTotalCountHint());

        // Iterate FilterList entries
        Iterator<?> iter = filter.iterator();
        OMMFilterEntry filterEntry;
        while (iter.hasNext())
        {
            flags = 0; // reset flags to use in OMMFilterEntry
            filterEntry = (OMMFilterEntry)iter.next();
            if (filterEntry.has(OMMFilterEntry.HAS_DATA_FORMAT))
            {
                flags |= OMMFilterEntry.HAS_DATA_FORMAT;
            }
            if (filterEntry.has(OMMFilterEntry.HAS_PERMISSION_DATA))
            {
                flags |= OMMFilterEntry.HAS_PERMISSION_DATA;
            }
            _encoder.encodeFilterEntryInit(flags, filterEntry.getAction(),
                                           filterEntry.getFilterId(), filterEntry.getDataType(),
                                           filterEntry.getPermissionData());
            // Don't encode data for CLEAR_ACTION because it has no data.
            if (filterEntry.getAction() != OMMFilterEntry.Action.CLEAR)
            {
                encodeDataFrom(filterEntry.getData());
            }
        }
        _encoder.encodeAggregateComplete();
    }

    private static void encodeVectorFrom(OMMVector vector)
    {
        int flags = 0;
        short dataType = vector.getDataType();
        if (vector.has(OMMVector.HAS_DATA_DEFINITIONS))
        {
            flags |= OMMVector.HAS_DATA_DEFINITIONS;
        }
        if (vector.has(OMMVector.HAS_SUMMARY_DATA))
        {
            flags |= OMMVector.HAS_SUMMARY_DATA;
        }
        if (vector.has(OMMVector.HAS_PERMISSION_DATA_PER_ENTRY))
        {
            flags |= OMMVector.HAS_PERMISSION_DATA_PER_ENTRY;
        }
        if (vector.has(OMMVector.HAS_TOTAL_COUNT_HINT))
        {
            flags |= OMMVector.HAS_TOTAL_COUNT_HINT;
        }
        if (vector.has(OMMVector.HAS_SORT_ACTIONS))
        {
            flags |= OMMVector.HAS_SORT_ACTIONS;
        }

        // Vector Header
        _encoder.encodeVectorInit(flags, dataType, vector.getTotalCountHint());

        // Data Definition if Vector entries are FieldList or ElementList
        if (vector.has(OMMVector.HAS_DATA_DEFINITIONS))
        {
            if (_usePreEncodeDataDefs)
            {
                _encoder.encodeDataDefs(vector.getDataDefs());
            }
            else
            {
                short dbtype = (dataType == OMMTypes.FIELD_LIST) ? OMMTypes.FIELD_LIST_DEF_DB
                        : OMMTypes.ELEMENT_LIST_DEF_DB;
                DataDefDictionary dictionary = DataDefDictionary.create(dbtype);
                DataDefDictionary.decodeOMMDataDefs(dictionary, vector.getDataDefs());
                if (dataType == OMMTypes.FIELD_LIST)
                {
                    encodeFieldListDefinitionsFrom(dictionary);
                }
                else if (dataType == OMMTypes.ELEMENT_LIST)
                {
                    encodeElementListDefinitionsFrom(dictionary);
                }
                else
                {
                    System.err.println("ERROR: " + _className
                            + " Vector cannot contain OMMDataDefDb for "
                            + OMMTypes.toString(dataType));
                }
            }
        }

        // Summary data
        if (vector.has(OMMVector.HAS_SUMMARY_DATA))
        {
            encodeSummaryDataFrom(vector.getSummaryData());
        }

        // Iterate Vector entries
        Iterator<?> iter = vector.iterator();
        OMMVectorEntry vectorEntry;
        byte action = 0;
        while (iter.hasNext())
        {
            flags = 0; // reset flags to use in OMMVectorEntry
            vectorEntry = (OMMVectorEntry)iter.next();
            action = vectorEntry.getAction();
            if (vectorEntry.has(OMMVectorEntry.HAS_PERMISSION_DATA))
            {
                flags = OMMVectorEntry.HAS_PERMISSION_DATA;
            }

            // VectorEntry init
            _encoder.encodeVectorEntryInit(flags, action, vectorEntry.getPosition(),
                                           vectorEntry.getPermissionData());

            // Element data.
            // CLEAR_ACTION or DELETE_ACTION should not encode data because they
            // have no data.
            if (!(action == OMMVectorEntry.Action.CLEAR || action == OMMVectorEntry.Action.DELETE))
            {
                encodeDataFrom(vectorEntry.getData());
            }
        }
        _encoder.encodeAggregateComplete();
    }

    private static void encodeMapFrom(OMMMap map)
    {
        int flags = 0;
        short dataType = map.getDataType();
        if (map.has(OMMMap.HAS_DATA_DEFINITIONS))
        {
            flags |= OMMMap.HAS_DATA_DEFINITIONS;
        }
        if (map.has(OMMMap.HAS_SUMMARY_DATA))
        {
            flags |= OMMMap.HAS_SUMMARY_DATA;
        }
        if (map.has(OMMMap.HAS_PERMISSION_DATA_PER_ENTRY))
        {
            flags |= OMMMap.HAS_PERMISSION_DATA_PER_ENTRY;
        }
        if (map.has(OMMMap.HAS_TOTAL_COUNT_HINT))
        {
            flags |= OMMMap.HAS_TOTAL_COUNT_HINT;
        }
        if (map.has(OMMMap.HAS_KEY_FIELD_ID))
        {
            flags |= OMMMap.HAS_KEY_FIELD_ID;
        }

        // Map Header
        _encoder.encodeMapInit(flags, map.getKeyDataType(), dataType, map.getTotalCountHint(),
                               map.getKeyFieldId());

        // Data Definition if map entries are FieldList or ElementList
        if (map.has(OMMMap.HAS_DATA_DEFINITIONS))
        {
            if (_usePreEncodeDataDefs)
            {
                _encoder.encodeDataDefs(map.getDataDefs());
            }
            else
            {
                short dbtype = (dataType == OMMTypes.FIELD_LIST) ? OMMTypes.FIELD_LIST_DEF_DB
                        : OMMTypes.ELEMENT_LIST_DEF_DB;
                DataDefDictionary dictionary = DataDefDictionary.create(dbtype);
                DataDefDictionary.decodeOMMDataDefs(dictionary, map.getDataDefs());
                if (dataType == OMMTypes.FIELD_LIST)
                {
                    encodeFieldListDefinitionsFrom(dictionary);
                }
                else if (dataType == OMMTypes.ELEMENT_LIST)
                {
                    encodeElementListDefinitionsFrom(dictionary);
                }
                else
                {
                    System.err.println("ERROR: " + _className
                                    + " Map cannot contain OMMDataDefDb for "
                                    + OMMTypes.toString(dataType));
                }
            }
        }

        // Summary data
        if (map.has(OMMMap.HAS_SUMMARY_DATA))
        {
            encodeSummaryDataFrom(map.getSummaryData());
        }

        // Iterate Map entries
        Iterator<?> iter = map.iterator();
        OMMMapEntry mapEntry;
        while (iter.hasNext())
        {
            flags = 0; // reset flags to use in OMMMapEntry
            mapEntry = (OMMMapEntry)iter.next();
            if (mapEntry.has(OMMMapEntry.HAS_PERMISSION_DATA))
            {
                flags = OMMMapEntry.HAS_PERMISSION_DATA;
            }
            // MapEntry init
            _encoder.encodeMapEntryInit(flags, mapEntry.getAction(), mapEntry.getPermissionData());

            // Key
            encodeDataFrom(mapEntry.getKey());

            // Value. Don't encode value for DELETE_ACTION because it doesn't
            // have value.
            if (mapEntry.getAction() != OMMMapEntry.Action.DELETE)
            {
                encodeDataFrom(mapEntry.getData());
            }
        }
        _encoder.encodeAggregateComplete();
    }

    private static void encodeFieldListDefinitionsFrom(DataDefDictionary fieldListDefDb)
    {
        DataDefDictionary.encodeAllDataDefs(fieldListDefDb, _encoder);
    }

    private static void encodeElementListDefinitionsFrom(DataDefDictionary elementListDefDb)
    {
        DataDefDictionary.encodeAllDataDefs(elementListDefDb, _encoder);
    }

    private static void encodeSummaryDataFrom(OMMData summaryData)
    {
        if (summaryData.getEncodedLength() < 253)
            _encoder.useSize(OMMEncoder.SMALL_SIZE);
        _encoder.encodeSummaryDataInit();
        if (summaryData.getEncodedLength() < 253)
            _encoder.useSize(OMMEncoder.LARGE_SIZE);
        encodeDataFrom(summaryData);
    }

    private static void encodeSeriesFrom(OMMSeries series)
    {
        int flags = 0;
        short dataType = series.getDataType();

        if (series.has(OMMSeries.HAS_DATA_DEFINITIONS))
        {
            flags |= OMMSeries.HAS_DATA_DEFINITIONS;
        }
        if (series.has(OMMSeries.HAS_SUMMARY_DATA))
        {
            flags |= OMMSeries.HAS_SUMMARY_DATA;
        }
        if (series.has(OMMSeries.HAS_TOTAL_COUNT_HINT))
        {
            flags |= OMMSeries.HAS_TOTAL_COUNT_HINT;
        }

        // Series Header
        _encoder.encodeSeriesInit(flags, dataType, series.getTotalCountHint());

        // Data Definition if Series entries are FieldList or ElementList
        if (series.has(OMMSeries.HAS_DATA_DEFINITIONS))
        {
            if (_usePreEncodeDataDefs)
            {
                _encoder.encodeDataDefs(series.getDataDefs());
            }
            else
            {
                short dbtype = (dataType == OMMTypes.FIELD_LIST) ? OMMTypes.FIELD_LIST_DEF_DB
                        : OMMTypes.ELEMENT_LIST_DEF_DB;
                DataDefDictionary dictionary = DataDefDictionary.create(dbtype);
                DataDefDictionary.decodeOMMDataDefs(dictionary, series.getDataDefs());
                if (dataType == OMMTypes.FIELD_LIST)
                {
                    encodeFieldListDefinitionsFrom(dictionary);
                }
                else if (dataType == OMMTypes.ELEMENT_LIST)
                {
                    encodeElementListDefinitionsFrom(dictionary);
                }
                else
                {

                    System.err.println("ERROR: " + _className
                            + " Series cannot contain OMMDataDefDb for "
                            + OMMTypes.toString(dataType));
                }
            }
        }

        // Summary data
        if (series.has(OMMSeries.HAS_SUMMARY_DATA))
        {
            encodeSummaryDataFrom(series.getSummaryData());
        }

        // Iterate Series entries
        Iterator<?> iter = series.iterator();
        OMMEntry entry;
        while (iter.hasNext())
        {
            entry = (OMMEntry)iter.next();
            // SeriesEntry init
            _encoder.encodeSeriesEntryInit();

            // entry
            encodeDataFrom(entry.getData());
        }
        _encoder.encodeAggregateComplete();

    }

    public static void setEncodePublisherInfo(boolean encode, PublisherPrincipalIdentity pi)
    {
        _bEncodePublisherInfo = encode;
        _pi = pi;
    }

    public static void setServiceName(boolean encode, String serviceName)
    {
        _bEncodeServiceName = encode;
        _serviceName = serviceName;
    }

}
