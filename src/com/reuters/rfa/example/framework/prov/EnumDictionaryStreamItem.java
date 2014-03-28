package com.reuters.rfa.example.framework.prov;

import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMSeries;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMDictionary;

/**
 * EnumDictionaryStreamItem is a stream item for a Enum Dictionary.
 * 
 * This class is responsible for encoding the Open Message Model data for a Enum
 * Dictionary.
 * 
 */
public class EnumDictionaryStreamItem extends DictionaryStreamItem
{
    private FieldDictionary _fieldDictionary;

    protected EnumDictionaryStreamItem(DictionaryMgr mgr, String name)
    {
        super(mgr, name);
    }

    /**
     * Sets a Enum Dictionary to this stream item.
     * 
     * @param dictionary a Enum Dictionary.
     */
    public void setFieldDictionary(FieldDictionary dictionary)
    {
        _fieldDictionary = dictionary;
    }

    public void encodeSeries(OMMEncoder encoder, int filter)
    {
        if (filter == RDMDictionary.Filter.INFO)
        {
            encoder.encodeSeriesInit(OMMSeries.HAS_SUMMARY_DATA, OMMTypes.ELEMENT_LIST, 0);
            encoder.encodeSummaryDataInit();
            encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
            String version = _fieldDictionary.getEnumProperty("Version");
            if (version != null && !version.equals(""))
            {
                encoder.encodeElementEntryInit(RDMDictionary.Summary.Version, OMMTypes.ASCII_STRING);
                encoder.encodeString(version, OMMTypes.ASCII_STRING);
            }
            encoder.encodeElementEntryInit(RDMDictionary.Summary.Type, OMMTypes.UINT);
            encoder.encodeUInt((long)RDMDictionary.Type.FIELD_DEFINITIONS);
            encoder.encodeElementEntryInit(RDMDictionary.Summary.DictionaryId, OMMTypes.UINT);
            encoder.encodeUInt((long)_fieldDictionary.getDictId());
            encoder.encodeAggregateComplete(); // Completes the ElementList
            encoder.encodeAggregateComplete(); // Series
        }
        else
            FieldDictionary.encodeRDMEnumDictionary(_fieldDictionary, encoder);
    }
}
