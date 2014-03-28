package com.reuters.rfa.example.omm.dictionary;

import java.io.PrintStream;

import com.reuters.rfa.dictionary.EnumTable;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMTypes;

/**
 * A utility class to format and display field and enumeration dictionaries from
 * {@link FieldDictionary}.
 * 
 */
public class DictionaryUtil
{

    /**
     * Prints field dictionary in a readable format to System.out
     * 
     * @param dict the full dictionary containing
     *            {@link com.reuters.rfa.rdm.RDMDictionary.Type#FIELD_DEFINITIONS
     *            FIELD_DEFINITIONS} and
     *            {@link com.reuters.rfa.rdm.RDMDictionary.Type#ENUM_TABLES
     *            ENUM_TABLES}
     */
    protected static void printFieldDictionary(FieldDictionary dict)
    {
        printFieldDictionary(dict, System.out);
    }

    /**
     * Prints field dictionary in a readable format to the provided PrintStream
     * 
     * @param dict the full dictionary containing
     *            {@link com.reuters.rfa.rdm.RDMDictionary.Type#FIELD_DEFINITIONS
     *            FIELD_DEFINITIONS} and
     *            {@link com.reuters.rfa.rdm.RDMDictionary.Type#ENUM_TABLES
     *            ENUM_TABLES}
     */
    protected static void printFieldDictionary(FieldDictionary dict, PrintStream ps)
    {
        // display field dictionary information
        ps.println("!__________________________");
        ps.println("!   RDM Field Dictionary   ");
        ps.println("!__________________________");
        ps.println("!@Version=" + dict.getFieldProperty("Version"));
        ps.println("!@Id=" + dict.getDictId());

        // Header columns
        ps.println("!");
        ps.println("!ACRONYM    DDE ACRONYM          FID  RIPPLES TO  FIELD TYPE     LENGTH  RWF TYPE   RWF LEN");
        ps.println("!-------    -----------          ---  ----------  ----------     ------  --------   -------");

        // Iterator all fields
        int maxFid = dict.getMaxFieldId();
        int fid;

        // Positive FID
        for (fid = 0; fid <= maxFid; fid++)
        {
            printFidDef(dict.getFidDef((short)fid), ps);
        }

        // Negative FID
        int minNegFid = dict.getMinNegFieldId();
        for (fid = -1; fid >= minNegFid; fid--)
        {
            printFidDef(dict.getFidDef((short)fid), ps);
        }
    }

    /**
     * Prints data of the specified fidDef as the format below to the provided
     * PrintStream. <br>
     * "ACRONYM" "DDE ACRONYM" "FID" "RIPPLES TO" "FIELD TYPE" "LENGTH"
     * "RWF TYPE" "RWF LEN"
     * <p>
     * It the def is null, prints nothing
     */
    private static void printFidDef(FidDef def, PrintStream ps)
    {

        if (def == null)
        {
            return;
        }
        StringBuilder strBuff = new StringBuilder(80);
        int offset;

        // ACRONYM
        strBuff.append(def.getName());
        appendTab(strBuff, 11);

        // DDE ACRONYM
        if(def.getLongName() == null)
        {
            strBuff.append(" NULL");
        }
        else
        {
            strBuff.append("\"");
            strBuff.append(def.getLongName());
            strBuff.append("\"");
        }
        offset = strBuff.length(); // save offset to insert blanks

        // FID
        strBuff.append(def.getFieldId());
        insertTab(strBuff, offset, 36);
        appendTab(strBuff, 39);

        // RIPPLES TO
        if (def.getRippleName() != null)
        {
            strBuff.append(def.getRippleName());
        }
        else
        {
            strBuff.append("NULL");
        }
        appendTab(strBuff, 51);

        // FIELD TYPE
        strBuff.append(FidDef.MfeedType.toString(def.getMfeedType()));
        offset = strBuff.length(); // save offset to insert blanks

        // LENGTH
        strBuff.append(def.getMaxMfeedLength());
        if ((def.getMfeedType() == FidDef.MfeedType.ENUMERATED)
                || (def.getOMMType() == OMMTypes.ENUM))
        {
            strBuff.append(" ( ");
            strBuff.append(def.getExpandedLength());
            strBuff.append(" )");
        }
        insertTab(strBuff, offset, 71);
        appendTab(strBuff, 74);

        // RWF_TYPE
        strBuff.append(OMMTypes.toString(def.getOMMType()));
        offset = strBuff.length(); // save offset to insert blanks

        // RWF_LEN
        strBuff.append(def.getMaxOMMLength());
        insertTab(strBuff, offset, 91);
        ps.println(strBuff.toString());
    }

    /**
     * Prints enumeration dictionary in a readable format to System.out
     * 
     * @param dict the full dictionary containing
     *            {@linkplain com.reuters.rfa.rdm.RDMDictionary.Type#FIELD_DEFINITIONS
     *            FIELD_DEFINITIONS} and
     *            {@linkplain com.reuters.rfa.rdm.RDMDictionary.Type#ENUM_TABLES
     *            ENUM_TABLES}
     */
    protected static void printEnumDictionary(FieldDictionary dict)
    {
        printEnumDictionary(dict, System.out);
    }

    /**
     * Prints enumeration dictionary in a readable format to the provided
     * PrintStream
     * 
     * @param dict the full dictionary containing
     *            {@link com.reuters.rfa.rdm.RDMDictionary.Type#FIELD_DEFINITIONS
     *            FIELD_DEFINITIONS} and
     *            {@link com.reuters.rfa.rdm.RDMDictionary.Type#ENUM_TABLES
     *            ENUM_TABLES}
     */
    protected static void printEnumDictionary(FieldDictionary dict, PrintStream ps)
    {
        // display field dictionary information
        ps.println("!__________________________");
        ps.println("!   Enum Dictionary   ");
        ps.println("!__________________________");
        ps.println("!@Version=" + dict.getEnumProperty("Version"));
        ps.println("!@Id=" + dict.getDictId());

        EnumTable[] enumTables = dict.getEnumTables();
        EnumTable enumTable;
        short[] enumFid;
        short fieldId;
        int[] enumValue;
        String[] expandedValues;
        FidDef def;
        StringBuilder strBuff = new StringBuilder();
        int index, offset;
        // Why enumIndex start from 1
        for (int enumIdex = 1; enumIdex < enumTables.length; enumIdex++)
        {
            enumTable = enumTables[enumIdex];
            // EnumTable Header
            ps.println("!");
            ps.println("! EnumIndex  [" + enumIdex + "]");
            ps.println("! ACRONYM    FID");
            ps.println("! -------    ---");
            ps.println("!");
            enumFid = enumTable.getFieldIds();
            for (index = 0; index < enumFid.length; index++)
            {
                strBuff.setLength(0);
                fieldId = enumFid[index];
                def = dict.getFidDef(fieldId);
                if (def != null)
                {
                    strBuff.append(def.getName());
                    offset = strBuff.length(); // save offset to insert blanks
                }
                else
                {
                    offset = 0;
                }
                strBuff.append(fieldId);
                insertTab(strBuff, offset, 16);
                ps.println(strBuff.toString());
            }

            ps.println("!");
            ps.println("! VALUE      DISPLAY");
            ps.println("! -----      -------");
            enumValue = enumTable.getEnumValues();
            expandedValues = enumTable.getExpandedValues();
            // The arrays for getEnumValues() getExpandedValues()
            // are guaranteed to be the same length.
            for (index = 0; index < enumValue.length; index++)
            {
                strBuff.setLength(0);
                strBuff.append(enumValue[index]);
                insertTab(strBuff, 0, 7);
                appendTab(strBuff, 16);
                strBuff.append("\"");
                strBuff.append(expandedValues[index]);
                strBuff.append("\"");
                ps.println(strBuff.toString());
            }
        }
    }

    /**
     * Appends the strBuff with blanks until position. If the tabStop position
     * is less than the lenght, append only one blank.
     * 
     * @param strBuff
     * @param tabStop the last position to append blanks
     */
    private static void appendTab(StringBuilder strBuff, int tabStop)
    {
        if (strBuff.length() >= tabStop)
        {
            strBuff.append(" ");
        }
        else
        {
            while (strBuff.length() < tabStop)
            {
                strBuff.append(" ");
            }
        }
    }

    /**
     * Inserts blanks into the strBuff at the offset until length equals to the
     * newLenght
     * 
     * @param strBuff
     * @param offset the offset
     * @param newLength the length after inserted blanks
     */
    private static void insertTab(StringBuilder strBuff, int offset, int newLength)
    {
        while (strBuff.length() < newLength)
        {
            strBuff.insert(offset, " ");
        }
    }
}
