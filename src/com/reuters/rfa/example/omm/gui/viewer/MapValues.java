package com.reuters.rfa.example.omm.gui.viewer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMDataBuffer;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMState;

/**
 * Stores Map of {@link com.reuters.rfa.example.omm.gui.viewer.FieldListValues
 * FieldListValues}.
 */
public class MapValues
{
    Map<String, FieldListValues> _entries;
    FieldListValues _summary;
    byte _dataState;
    short _keyFieldId;
    MapTableModel _model;

    public MapValues(MapTableModel model, FieldListTableModel fmodel)
    {
        _model = model;
        _summary = new FieldListValues(fmodel, 8, 0, -1);
        _entries = new LinkedHashMap<String, FieldListValues>(0);
    }

    public short getKeyFieldId()
    {
        return _keyFieldId;
    }

    public Iterator<FieldListValues> iterator()
    {
        return _entries.values().iterator();
    }

    public void setCount(int count)
    {
        _entries = new LinkedHashMap<String, FieldListValues>(count);
    }

    public void refreshSummary(FieldDictionary dictionary, OMMFieldList summary)
    {
        _summary.refresh(dictionary, summary);
    }

    public void updateSummary(FieldDictionary dictionary, OMMFieldList summary, boolean ripple)
    {
        _summary.update(dictionary, summary, ripple);
    }

    public FieldListValues add(FieldDictionary dictionary, OMMMap map, OMMDataBuffer key,
            OMMFieldList entryFieldList)
    {
        FieldListValues values = null;
        values = new FieldListValues(_model, entryFieldList.getCount() + 1, _entries.size(), -1);
        values.addField(key.toString());
        values.refresh(dictionary, entryFieldList);
        int oldSize = _entries.size();
        _entries.put(key.toString(), values);
        _model.fireTableRowsInserted(oldSize, oldSize);
        return values;
    }

    public void update(FieldDictionary dictionary, OMMDataBuffer key, OMMFieldList entryFieldList,
            boolean ripple)
    {
        FieldListValues values = (FieldListValues)_entries.get(key.toString());
        if (values == null)
        {
            System.out.println("update for nonexistent key " + key.toString());
            // This is possible between a first refresh and a refresh complete.
            // Otherwise this is a
            // problem in the data provider.
            return;
        }
        values.update(dictionary, entryFieldList, ripple);
        _model.fireTableRowsUpdated(values._row, values._row);

    }

    public void delete(OMMDataBuffer key)
    {
        FieldListValues values = (FieldListValues)_entries.get(key.toString());
        if (values == null)
        {
            System.out.println("deleting nonexistent key " + key.toString());
            // This is possible between a first refresh and a refresh complete.
            // Otherwise this is a problem in the data provider.
            return;
        }
        for (Iterator<Map.Entry<String, FieldListValues>> iter = _entries.entrySet().iterator(); iter
                .hasNext();)
        {
            Map.Entry<String, FieldListValues> entry = iter.next();
            if (entry.getValue() == values)
            {
                iter.remove();
                while (iter.hasNext())
                {
                    entry = iter.next();
                    FieldListValues v = (FieldListValues)entry.getValue();
                    v._row--;
                }
            }
        }
        _model.fireTableRowsDeleted(values._row, values._row);
    }

    public int size()
    {
        return (_entries != null) ? _entries.size() : 0;
    }

    public void clear()
    {
        if (_entries != null)
            _entries.clear();
        _summary.clear();
    }

    public String getStringValue(int rowIndex, FidDef fiddef)
    {
        if (fiddef == null)
        {
            Iterator<String> iter = _entries.keySet().iterator();
            for (int i = 1; i <= rowIndex; i++)
                iter.next();
            return iter.next();
        }

        Iterator<FieldListValues> flListIter = _entries.values().iterator();
        for (int i = 1; i <= rowIndex; i++)
            flListIter.next();
        FieldListValues fields = (FieldListValues)flListIter.next();
        fields.iterator();
        FieldValue field = fields.getValue(fiddef.getName());
        if (field != null)
            return field.getStringValue();
        return ""; // in case DataDefs are not used so all rows do not have the
                   // same columns
    }

    public FieldValue getFieldValue(int rowIndex, FidDef fiddef)
    {
        if (fiddef == null)
            return null;

        Iterator<FieldListValues> iter = _entries.values().iterator();
        for (int i = 1; i <= rowIndex; i++)
            iter.next();
        FieldListValues fields = (FieldListValues)iter.next();
        fields.iterator();
        FieldValue field = fields.getValue(fiddef.getName());
        return field;
    }

    public FieldListValues getSummary()
    {
        return _summary;
    }

    void dump()
    {
        Iterator<Map.Entry<String, FieldListValues>> iter = _entries.entrySet().iterator();
        while (iter.hasNext())
        {
            Map.Entry<String, FieldListValues> me = iter.next();
            System.out.println("key " + me.getKey());
            System.out.println("value");
            FieldListValues flistcache = me.getValue();
            System.out.println("count: " + flistcache.size());
            for (Iterator<FieldValue> fliter = flistcache.iterator(); fliter.hasNext();)
            {
                FieldValue field = fliter.next();
                System.out.print("\t" + field.getName() + ": ");
                System.out.println(field.getStringValue());
            }
        }
    }

    public void setDataState(byte dataState)
    {
        if (dataState == OMMState.Data.NO_CHANGE)
            return;
        _summary.setDataState(dataState);
        _dataState = dataState;
        _model.fireTableDataChanged();
    }

    public byte getDataState()
    {
        return _dataState;
    }

}
