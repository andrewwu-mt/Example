package com.reuters.rfa.example.omm.multipleConsumers;

import java.util.Iterator;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMArray;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMDataBuffer;
import com.reuters.rfa.omm.OMMDateTime;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMEnum;
import com.reuters.rfa.omm.OMMFieldEntry;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMQos;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * Application client to manage item requests & responses, decode data
 */
public class ItemClient implements Client
{
    // parent
    private final ConsumerClient m_consumerClient;

    // input configuration
    int m_itemCount;
    int m_decodeLevel;

    // response data decoding
    static final int DECODE_PAYLOAD = 1;
    static final int DECODE_ITERATE = 2;
    static final int DECODE_DATA = 3;
    static final int DECODE_CONTENTS = 4;
    static final int DECODE_ALL = 5;
    private byte[] m_bytes;

    // statistics
    boolean m_bAllImagesReceived;
    int m_updateCurrentInterval;
    int m_refreshCurrentInterval;
    int m_statusCurrentInterval;
    int m_finalStatusCurrentInterval;
    int m_requestCurrentInterval;

    int m_requestTotal;
    int m_refreshTotal;

    /*
     * Constructor
     */
    public ItemClient(ConsumerClient consumerClient, int count, int decodeLevel)
    {
        m_consumerClient = consumerClient;

        m_decodeLevel = decodeLevel;
        m_itemCount = count;

        m_bAllImagesReceived = false;
        m_bytes = new byte[1000];
    }

    /*
     * Handle Item Responses; Called by RFA is eventQ=null or ConsumerClient's
     * ResponseDispatcher
     */
    public void processEvent(Event event)
    {
        OMMMsg msg = ((OMMItemEvent)event).getMsg();

        // decode update based on input configuration
        if (msg.getMsgType() == OMMMsg.MsgType.UPDATE_RESP)
        {
            m_updateCurrentInterval++;
            if (m_decodeLevel >= DECODE_PAYLOAD)
            {
                if (msg.getDataType() == OMMTypes.FIELD_LIST)
                {
                    OMMFieldList fieldList = (OMMFieldList)msg.getPayload();
                    if (m_decodeLevel >= DECODE_ITERATE)
                    {
                        Iterator<?> iter = fieldList.iterator();
                        while (iter.hasNext())
                        {
                            OMMFieldEntry fieldEntry = (OMMFieldEntry)iter.next();
                            if (m_decodeLevel >= DECODE_DATA)
                            {
                                short fid = fieldEntry.getFieldId();
                                short type = MultipleConsumers.m_Dictionary.getFidDef(fid)
                                        .getOMMType();
                                OMMData data = fieldEntry.getData(type);
                                if (m_decodeLevel >= DECODE_CONTENTS)
                                    decode(data);
                            }
                        }
                    }
                }
            }
            return;
        }

        // handle refreshes
        if (msg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
        {
            m_refreshCurrentInterval++;
            m_refreshTotal++;
            if (++m_refreshTotal == m_itemCount)
            {
                m_bAllImagesReceived = true;
                m_consumerClient.log("Received all " + m_itemCount
                        + " images; Ready to receive updates!");
            }
            return;
        }

        // handle status messages
        if (msg.isFinal())
            m_finalStatusCurrentInterval++;
        else
            m_statusCurrentInterval++;
    }

    /*
     * Decode primitive data
     */
    // I suppressed the unused warnings because this is just to decode the
    // message in a perf test.
    // What the client does after they decode, is beyond the scope of the test.
    @SuppressWarnings("unused")
    void decode(OMMData data)
    {
        switch (data.getType())
        {
            case OMMTypes.REAL:
            {
                long value = ((OMMNumeric)data).getLongValue();
                int hint = ((OMMNumeric)data).getHint();
                break;
            }

            case OMMTypes.REAL_4RB:
            {
                int value = (int)((OMMNumeric)data).getLongValue();
                int hint = ((OMMNumeric)data).getHint();
                break;
            }
            case OMMTypes.TIME:
            {
                OMMDateTime dt = (OMMDateTime)data;
                int hr = dt.getHour();
                int min = dt.getMinute();
                int sec = dt.getSecond();
                int msec = dt.getMillisecond();
                break;
            }
            case OMMTypes.ENUM:
            {
                int enumValue = ((OMMEnum)data).getValue();
                break;
            }
            case OMMTypes.UINT:
            {
                long l = ((OMMNumeric)data).toLong();
                break;
            }
            case OMMTypes.RMTES_STRING:
            {
                if (m_decodeLevel >= DECODE_ALL)
                {
                    int length = ((OMMDataBuffer)data).getBytes(m_bytes, 0);
                    @SuppressWarnings("deprecation")
                    // this is suppressed because the new String method has a
                    // severe
                    // performance hit.
                    String s = new String(m_bytes, 0, 0, length);
                }
                break;
            }
            case OMMTypes.REAL_8RB:
            {
                long value = ((OMMNumeric)data).getLongValue();
                int hint = ((OMMNumeric)data).getHint();
                break;
            }
            case OMMTypes.TIME_3:
            case OMMTypes.TIME_5:
            {
                OMMDateTime dt = (OMMDateTime)data;
                int hr = dt.getHour();
                int min = dt.getMinute();
                int sec = dt.getSecond();
                int msec = dt.getMillisecond();
                break;
            }
            case OMMTypes.INT_1:
            case OMMTypes.INT_2:
            case OMMTypes.INT_4:
            {
                int i = (int)((OMMNumeric)data).toLong();
                break;
            }
            case OMMTypes.INT:
            case OMMTypes.UINT_1:
            case OMMTypes.UINT_2:
            case OMMTypes.UINT_4:
            case OMMTypes.INT_8:
            case OMMTypes.UINT_8:
            {
                long l = ((OMMNumeric)data).toLong();
                break;
            }
            case OMMTypes.FLOAT:
            case OMMTypes.FLOAT_4:
            {
                float f = ((OMMNumeric)data).toFloat();
                break;
            }
            case OMMTypes.DOUBLE:
            case OMMTypes.DOUBLE_8:
            {
                double d = ((OMMNumeric)data).toDouble();
                break;
            }
            case OMMTypes.DATE:
            case OMMTypes.DATE_4:
            {
                OMMDateTime dt = (OMMDateTime)data;
                int date = dt.getDate();
                int month = dt.getMonth();
                int year = dt.getYear();
                break;
            }
            case OMMTypes.DATETIME:
            case OMMTypes.DATETIME_7:
            case OMMTypes.DATETIME_9:
            {
                OMMDateTime dt = (OMMDateTime)data;
                int date = dt.getDate();
                int month = dt.getMonth();
                int year = dt.getYear();
                int hr = dt.getHour();
                int min = dt.getMinute();
                int sec = dt.getSecond();
                int msec = dt.getMillisecond();
                break;
            }
            case OMMTypes.QOS:
                long qosRate = ((OMMQos)data).toQos().getRate();
                long qosTimeliness = ((OMMQos)data).toQos().getTimeliness();
                break;
            case OMMTypes.STATE:
            {
                byte streamState = ((OMMState)data).getStreamState();
                byte dataState = ((OMMState)data).getDataState();
                short code = ((OMMState)data).getCode();
                String text = ((OMMState)data).getText();
                break;
            }
            case OMMTypes.ARRAY:
            {
                for (Iterator<?> iter = ((OMMArray)data).iterator(); iter.hasNext();)
                {
                    OMMEntry arrayEntry = (OMMEntry)iter.next();
                    OMMData arrayData = arrayEntry.getData();
                    decode(arrayData);
                }
                break;
            }
            case OMMTypes.BUFFER:
            case OMMTypes.ASCII_STRING:
            case OMMTypes.UTF8_STRING:
            {
                if (m_decodeLevel > DECODE_ALL)
                {
                    String s = ((OMMDataBuffer)data).toString();
                }
                break;
            }
            case OMMTypes.NO_DATA:
                break;
            case OMMTypes.XML:
            case OMMTypes.FIELD_LIST:
            case OMMTypes.ELEMENT_LIST:
            case OMMTypes.ANSI_PAGE:
                break;
            case OMMTypes.OPAQUE_BUFFER:
            {
                OMMDataBuffer value = (OMMDataBuffer)data;
                break;
            }
            case OMMTypes.FILTER_LIST:
            case OMMTypes.VECTOR:
            case OMMTypes.MAP:
            case OMMTypes.SERIES:
                break;
            case OMMTypes.UNKNOWN:
                m_consumerClient.log("Unknown OMM Data");
                break;
            default:
            {
                m_consumerClient.log("Unsupported OMM Data");
                break;
            }
        }
    }

    /*
     * Create & Send Item requests
     */
    void makeRequests(EventQueue eventQ)
    {
        String mmtstr = CommandLine.variable("mmt");
        short mmt = RDMMsgTypes.msgModelType(mmtstr);
        String servicename = CommandLine.variable("serviceName");

        OMMMsg msg = m_consumerClient.m_pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(mmt);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);

        OMMItemIntSpec spec = new OMMItemIntSpec();

        String[] buildRicList = buildRicList(m_itemCount, ".O");
        for (int i = 0; i < buildRicList.length; i++)
        {
            String itemName = buildRicList[i];

            msg.setAttribInfo(servicename, itemName, RDMInstrument.NameType.RIC);
            spec.setMsg(msg);
            m_consumerClient.m_consumer.registerClient(eventQ, spec, this, null);
            m_requestCurrentInterval++;
            m_requestTotal++;
        }

        m_consumerClient.log("Requested " + buildRicList.length + " items from " + servicename);
        m_consumerClient.m_pool.releaseMsg(msg);
    }

    /*
     * Build RIC list
     */
    static String[] buildRicList(int totalImage, String exchange)
    {
        char arr[] = { 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
                'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' };
        StringBuilder itemName = new StringBuilder();
        int item_count = 0;

        String[] itemList = new String[totalImage];
        for (int i = 0; i < 26; i++)
        {
            for (int j = 0; j < 26; j++)
            {
                for (int k = 0; k < 1000; k++)
                {
                    itemName.append(arr[i]);
                    itemName.append(arr[j]);
                    itemName.append(k);
                    itemName.append(exchange);
                    itemList[item_count] = itemName.toString();
                    itemName.setLength(0);
                    item_count++;
                    if (item_count >= totalImage)
                        return itemList;
                }
            }
        }
        return itemList;
    }

    /*
     * Print Statistics when invoked by the application's timer
     */
    StringBuilder printStats(int counter, StringBuilder dumpString, boolean bAppendCounter,
            boolean bPrintStats)
    {
        // updates
        if (m_bAllImagesReceived == true)
        {
            // updates are consolidated for all the sessions &
            // printed on a single line;
            // e.g. 1. 20000,21000 (1.=StatisticsCounter,
            // 20000=1st session update,
            // 21000=2nd session update
            //
            // If 1st session, append statistics counter,
            // else append comma(,)
            if (bAppendCounter == true)
            {
                dumpString.append(counter);
                dumpString.append(". ");
            }
            else
            {
                dumpString.append(", ");
            }

            appendUpdate(dumpString);

            // print the line only if last session
            if (bPrintStats == true)
            {
                m_consumerClient.logLine(dumpString.toString());
                dumpString.setLength(0);
            }

            return dumpString;
        }

        // request, refresh, update, status
        dumpString.append(counter);
        dumpString.append(".(");
        dumpString.append(m_consumerClient.m_session.getName());
        dumpString.append(") ");

        appendGeneralStatistics(dumpString);

        m_consumerClient.logLine(dumpString.toString());
        dumpString.setLength(0);

        return dumpString;
    }

    /*
     * Print Login Status OR Updates or General Statistics
     */
    void printStats(int counter, StringBuilder dumpString)
    {
        dumpString.append(counter);
        dumpString.append(".(");
        dumpString.append(m_consumerClient.m_session.getName());
        dumpString.append(") ");

        if (m_consumerClient.m_loggedIn == false) // pending login
        {
            dumpString.append("Awaiting Login ");
        }
        else if (m_bAllImagesReceived == true) // updates
        {
            // append update divided by 5
            appendUpdate(dumpString);
            dumpString.append(" updates");
        }
        else
        {
            appendGeneralStatistics(dumpString);
        }

        m_consumerClient.logLine(dumpString.toString());
        dumpString.setLength(0);

        return;
    }

    /*
     * Append updates; Statistics are divided by display interval(5) to get per
     * minute value
     */
    StringBuilder appendUpdate(StringBuilder dumpString)
    {
        dumpString.append(m_updateCurrentInterval / 5);
        m_updateCurrentInterval = 0;
        return dumpString;
    }

    /*
     * Append Request / refresh / Update / Status Statistics are divided by
     * display interval(5) to get per minute value
     */
    StringBuilder appendGeneralStatistics(StringBuilder dumpString)
    {
        dumpString.append("Request: ");
        dumpString.append(m_requestCurrentInterval / 5);
        dumpString.append("\tRefresh: ");
        dumpString.append(m_refreshCurrentInterval / 5);
        dumpString.append("\tUpdate: ");
        dumpString.append(m_updateCurrentInterval / 5);
        dumpString.append("\tStatus: ");
        dumpString.append(m_statusCurrentInterval / 5);

        m_requestCurrentInterval = m_refreshCurrentInterval = m_statusCurrentInterval = m_updateCurrentInterval = m_finalStatusCurrentInterval = 0;
        return dumpString;
    }

    /*
     * returns true if all Items have been reveived
     */
    boolean allItemsReceived(int i)
    {
        return m_bAllImagesReceived;
    }
}
// //////////////////////////////////////////////////////////////////////////////
// / End of file
// //////////////////////////////////////////////////////////////////////////////

