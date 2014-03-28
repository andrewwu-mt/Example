package com.reuters.rfa.example.utility;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;

import com.reuters.rfa.dacs.AuthorizationException;
import com.reuters.rfa.dacs.AuthorizationLock;
import com.reuters.rfa.omm.OMMArray;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMDataBuffer;
import com.reuters.rfa.omm.OMMDateTime;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMEnum;
import com.reuters.rfa.omm.OMMFilterEntry;
import com.reuters.rfa.omm.OMMFilterList;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMQos;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.omm.OMMVector;
import com.reuters.rfa.omm.OMMVectorEntry;

public final class ExampleUtil
{
    private final static StringBuilder _sb = new StringBuilder(100);
    private static ArrayList<Long> _longArrayList;
    
    public static void dumpAttribDataElements(OMMAttribInfo ai)
    {
        if (ai.getAttribType() == OMMTypes.NO_DATA)
            return;

        OMMElementList elementList = (OMMElementList)ai.getAttrib();

        for (Iterator<?> iter = elementList.iterator(); iter.hasNext();)
        {
            OMMElementEntry element = (OMMElementEntry)iter.next();
            OMMData data = element.getData(); // Get the data from the
                                              // ElementEntry.
            System.out.println(element.getName() + ": " + data.toString());
        }
    }

    public static void duplicateAttribInfoHeader(OMMAttribInfo srcai, 
    		OMMAttribInfo destai, boolean bDumpAttributes )
            throws Exception
    {
        Field[] fields = OMMAttribInfo.class.getDeclaredFields();

        try
        {
            for (int i = 0; i < fields.length; i++)
            {
                Field f = fields[i];
                if (f.getType() != Integer.TYPE)
                    continue;

                int value = f.getInt(null);
                if( bDumpAttributes )
                	System.out.println(f.getName());

                if (srcai.has(value))
                {
                	if( bDumpAttributes )
                		System.out.println("--> found " + f.getName());
                    switch (value)
                    {
                        case OMMAttribInfo.HAS_SERVICE_NAME:
                            destai.setServiceName(srcai.getServiceName());
                            break;
                        case OMMAttribInfo.HAS_NAME:
                            destai.setName(srcai.getName());
                            break;
                        case OMMAttribInfo.HAS_NAME_TYPE:
                            destai.setNameType(srcai.getNameType());
                            break;
                        case OMMAttribInfo.HAS_FILTER:
                            destai.setFilter(srcai.getFilter());
                            break;
                        case OMMAttribInfo.HAS_ID:
                            destai.setId(srcai.getId());
                            break;
                        case OMMAttribInfo.HAS_ATTRIB:
                        {
                        	if( bDumpAttributes )
                        		System.out.println("ai data pending.....");
                            break;
                        }
                        default:
                            throw new Exception("Missing copy method for AttribInfo flag");
                    }
                }
            }
        }
        catch (IllegalAccessException e)
        {
            e.printStackTrace();
        }
    }

    /*
     * get service name from OMMMsg
     */
    public static String getServiceNameFromOMMMsg(OMMMsg ommMsg)
    {
        String serviceName = null;

        if (ommMsg.has(OMMMsg.HAS_ATTRIB_INFO))
        {
            OMMAttribInfo ai = ommMsg.getAttribInfo();

            if (ai.has(OMMAttribInfo.HAS_SERVICE_NAME))
            {
                serviceName = ai.getServiceName();
            }
        }

        return serviceName;
    }

    public static void dumpCommandArgs()
    {
        // dump command line arguments
        String commandLineString = CommandLine.getConfiguration();

        System.out.println("Input/Default Configuration");
        System.out.println("======================");
        System.out.println(commandLineString);
        System.out.println("======================");
    }

    /*
     * sleep
     */
    public static void slowDown(int duration)
    {
        try
        {
            Thread.sleep(duration);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    public static String mapFlagsString(OMMMap data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMMap.HAS_DATA_DEFINITIONS))
        {
            buf.append("HAS_DATA_DEFINITIONS");
        }

        if (data.has(OMMMap.HAS_SUMMARY_DATA))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_SUMMARY_DATA");
        }

        if (data.has(OMMMap.HAS_PERMISSION_DATA_PER_ENTRY))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_PERMISSION_DATA_PER_ENTRY");
        }

        if (data.has(OMMMap.HAS_TOTAL_COUNT_HINT))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_TOTAL_COUNT_HINT");
        }

        if (data.has(OMMMap.HAS_KEY_FIELD_ID))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_KEY_FIELD_ID");
        }
        return buf.toString();
    }

    public static String mapEntryFlagsString(OMMMapEntry data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMMapEntry.HAS_PERMISSION_DATA))
        {
            buf.append("HAS_PERMISSION_DATA");
        }
        return buf.toString();
    }

    public static String vectorFlagsString(OMMVector data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMVector.HAS_DATA_DEFINITIONS))
        {
            buf.append("HAS_DATA_DEFINITIONS");
        }

        if (data.has(OMMVector.HAS_SUMMARY_DATA))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_SUMMARY_DATA");
        }

        if (data.has(OMMVector.HAS_PERMISSION_DATA_PER_ENTRY))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_PERMISSION_DATA_PER_ENTRY");
        }

        if (data.has(OMMVector.HAS_TOTAL_COUNT_HINT))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_TOTAL_COUNT_HINT");
        }

        if (data.has(OMMVector.HAS_SORT_ACTIONS))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_SORT_ACTIONS");
        }
        return buf.toString();
    }

    public static String filterListFlagsString(OMMFilterList data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMFilterList.HAS_PERMISSION_DATA_PER_ENTRY))
        {
            buf.append("HAS_PERMISSION_DATA_PER_ENTRY");
        }

        if (data.has(OMMFilterList.HAS_TOTAL_COUNT_HINT))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_TOTAL_COUNT_HINT");
        }
        return buf.toString();
    }

    public static String vectorEntryFlagsString(OMMVectorEntry data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMVectorEntry.HAS_PERMISSION_DATA))
        {
            buf.append("HAS_PERMISSION_DATA");
        }
        return buf.toString();
    }

    public static String filterEntryFlagsString(OMMFilterEntry data)
    {
        StringBuilder buf = new StringBuilder(60);

        if (data.has(OMMFilterEntry.HAS_PERMISSION_DATA))
        {
            buf.append("HAS_PERMISSION_DATA");
        }
        if (data.has(OMMFilterEntry.HAS_DATA_FORMAT))
        {
            if (buf.length() != 0)
                buf.append(" | ");

            buf.append("HAS_DATA_FORMAT");
        }
        return buf.toString();
    }

    public static boolean isNumeric(String value)
    {
        try
        {
            Long.parseLong(value);
        }
        catch (NumberFormatException e)
        {
            return false;
        }
        return true;
    }

    public static long convertStringToNumeric(String value)
    {
        try
        {
            long numericvalue =  Long.parseLong( value );
            return numericvalue;
            
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }

    @SuppressWarnings({ "deprecation", "unused" })
    public static void decodePrimitive(OMMData data)
    {
        if (data == null)
            return;

        short dataType = data.getType();
        switch (dataType)
        {
            case OMMTypes.REAL32:
            {
                // int value = ((OMMNumeric)data).getLongValue();
                int value = (int)((OMMNumeric)data).getLongValue();
                int hint = ((OMMNumeric)data).getHint();

                hDumpRealString(dataType, hint, value);
                break;
            }
            case OMMTypes.REAL:
            {
                long value = ((OMMNumeric)data).getLongValue();
                int hint = ((OMMNumeric)data).getHint();

                hDumpRealString(dataType, hint, value);
                break;
            }

            case OMMTypes.REAL_4RB:
            {
                int value = (int)((OMMNumeric)data).getLongValue();
                int hint = ((OMMNumeric)data).getHint();

                hDumpRealString(dataType, hint, value);
                break;
            }
            case OMMTypes.ENUM:
            {
                int enumValue = ((OMMEnum)data).getValue();
                break;
            }
            case OMMTypes.UINT32:
            case OMMTypes.UINT:
            {
                long l = ((OMMNumeric)data).toLong();
                hDumpNumericString(dataType, l);
                break;
            }
            case OMMTypes.RMTES_STRING:
            {
                /*
                 * if (m_decodeLevel >= DECODE_ALL) { int length =
                 * ((OMMDataBuffer)data).getBytes(m_bytes, 0); String s = new
                 * String(m_bytes, 0, 0, length); }
                 */
                break;
            }
            case OMMTypes.REAL_8RB:
            {
                long value = ((OMMNumeric)data).getLongValue();
                int hint = ((OMMNumeric)data).getHint();

                hDumpRealString(dataType, hint, value);
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
            case OMMTypes.INT32:
            case OMMTypes.INT_1:
            case OMMTypes.INT_2:
            case OMMTypes.INT_4:
            {
                int i = (int)((OMMNumeric)data).toLong();

                hDumpNumericString(dataType, i);

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
                hDumpNumericString(dataType, l);
                break;
            }
            case OMMTypes.FLOAT:
            case OMMTypes.FLOAT_4:
            {
                float f = ((OMMNumeric)data).toFloat();
                hDumpNumericString(dataType, (long)f);
                break;
            }
            case OMMTypes.DOUBLE:
            case OMMTypes.DOUBLE_8:
            {
                double d = ((OMMNumeric)data).toDouble();
                hDumpNumericString(dataType, (long)d);
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
                    // decode(arrayData);
                }
                break;
            }
            case OMMTypes.BUFFER:
            case OMMTypes.ASCII_STRING:
            case OMMTypes.UTF8_STRING:
            {
                hDumpString(dataType, ((OMMDataBuffer)data).toString());
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
            case OMMTypes.MSG:
                System.out.println("Payload is OMMMsg");
                break;
            case OMMTypes.UNKNOWN:
                System.out.println("Unknown OMM Data");
                break;
            default:
            {
                // m_consumerClient.log("Unsupported OMM Data");
                break;
            }
        }
    }/* Decode primitive data */

    static private void hDumpRealString(short type, long hint, long value)
    {
        _sb.setLength(0);

        _sb.append("Type= ");
        _sb.append(OMMTypes.toString(type));
        _sb.append("; Hint= ");
        _sb.append(hint);
        _sb.append("; Value= ");
        _sb.append(value);

        System.out.println(_sb.toString());
    }

    static private void hDumpNumericString(short type, long value)
    {
        _sb.setLength(0);

        _sb.append("Type= ");
        _sb.append(OMMTypes.toString(type));
        _sb.append("; Value= ");
        _sb.append(value);

        System.out.println(_sb.toString());
    }

    /*
     * print string
     */
    static private void hDumpString(short type, String s)
    {
        _sb.setLength(0);

        _sb.append("Type= ");
        _sb.append(OMMTypes.toString(type));
        _sb.append("; Value= ");
        _sb.append(s);

        System.out.println(_sb.toString());
    }
   
    /*
     * generate DACS lock
     */
    public static byte[] generatePELock( int serviceID, String PEList ) 
    {
    	if( PEList == null )
    		return null;
    	
    	if( PEList.length() == 0 )
    		return null;
    	
 		String[] pieces = PEList.split(","); //6567,451 
 		int length = pieces.length;
 		if(length == 0)
 			return null;

 		if( _longArrayList == null )
 			_longArrayList = new ArrayList<Long>();
 		else
 			_longArrayList.clear();
 		
    	for( int i = 0; i < length; i++ )
    	{
    		String sValue = pieces[ i ];
    		
    		try
    		{
    			Long numericValue = Long.parseLong( sValue );
    			_longArrayList.add( numericValue );
    		}
    		catch(Exception e)
    		{
    			
    		}
    	}
    	
 		// copy from ArrayList to long[]
    	long vPEList[] = new long[ _longArrayList.size() ];
 		for( int i = 0; i < _longArrayList.size(); i++ )
 		{
 			vPEList[i] = _longArrayList.get(i);
 		}

 		// create DACS lock
    	try 
    	{
    		AuthorizationLock authLock = new AuthorizationLock( serviceID, AuthorizationLock.OR, vPEList );
        	byte[] dacsLock = authLock.getAuthorizationLock();
        	return dacsLock;
    	} 
    	catch (AuthorizationException e) 
    	{
    		System.out.println("DACS Lock Error! "+e.toString() );
    		return null;
    	}
    }
}
