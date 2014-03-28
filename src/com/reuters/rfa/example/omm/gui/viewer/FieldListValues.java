package com.reuters.rfa.example.omm.gui.viewer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.table.AbstractTableModel;

import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMDataBuffer;
import com.reuters.rfa.omm.OMMFieldEntry;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;

/**
 * Stores Map of {@linkplain com.reuters.rfa.example.omm.gui.viewer.FieldValue
 * FieldValues}.
 */
public class FieldListValues
{
    Map<String, FieldValue> _entries;
    byte _dataState;
    AbstractTableModel _model;
    int _row;
    int _column;

    public FieldListValues(AbstractTableModel model, int size, int row, int col)
    {
        _model = model;
        _entries = new LinkedHashMap<String, FieldValue>(size);
        _row = row;
        _column = col;
    }

    public int size()
    {
        return _entries.size();
    }

    public Iterator<String> keyIterator()
    {
        return _entries.keySet().iterator();
    }

    public Iterator<FieldValue> iterator()
    {
        return _entries.values().iterator();
    }

    public void clear()
    {
        _entries.clear();
    }

    public void setDataState(byte dataState)
    {
        if (dataState == OMMState.Data.NO_CHANGE)
            return;
        _dataState = dataState;
        _model.fireTableDataChanged();
    }

    public byte getDataState()
    {
        return _dataState;
    }

    public FieldValue getValue(String name)
    {
        return (FieldValue)_entries.get(name);
    }

    public void addField(String value)
    {
        int listCount = _entries.size();
        int row = _row;
        int col = _column;
        if (row == -1)
            row = listCount;
        else if (col == -1)
            col = listCount;

        FieldValue field = new FieldValue(_model, null);
        _entries.put("key", field);
        field.setValue(value);
        field.setFade();
    }

    public void refresh(FieldDictionary dictionary, OMMFieldList fieldList)
    {
        for (Iterator<?> fiter = fieldList.iterator(); fiter.hasNext();)
        {
            OMMFieldEntry fentry = (OMMFieldEntry)fiter.next();
            FidDef fiddef = dictionary.getFidDef(fentry.getFieldId());
            if (fiddef != null)
            {
                setField(dictionary, fentry, fiddef, false, true);
            }
        }
    }

    public void update(FieldDictionary dictionary, OMMFieldList fieldList, boolean ripple)
    {
        for (Iterator<?> fiter = fieldList.iterator(); fiter.hasNext();)
        {
            OMMFieldEntry fentry = (OMMFieldEntry)fiter.next();
            FidDef fiddef = dictionary.getFidDef(fentry.getFieldId());
            if (fiddef != null)
            {
                setField(dictionary, fentry, fiddef, ripple, false);
            }
        }
        if (_row != -1)
            _model.fireTableRowsUpdated(_row, _row);
    }

    private void setField(FieldDictionary dictionary, OMMFieldEntry fentry, FidDef fiddef,
            boolean ripple, boolean add)
    {
        FieldValue field = getValue(fiddef.getName());
        if (field == null)
        {
            if (add)
            {
                short type = fentry.getDataType();
                if (type == OMMTypes.UNKNOWN)
                    type = fiddef.getOMMType();
                int listCount = _entries.size();
                int row = _row;
                int col = _column;
                if (row == -1)
                    row = listCount;
                else if (col == -1)
                    col = listCount;

                field = new FieldValue(_model, fiddef);
                _entries.put(fiddef.getName(), field);
                field.update(fentry);
            }
        }
        else if (ripple && (fiddef.getRippleFieldId() != 0))
        {
            Object tmp = field.getStringValue();
            setFirstField(fentry, fiddef, field);
            while ((fiddef.getRippleFieldId() != 0)
                    && ((field = getValue(dictionary.getFidDef(fiddef.getRippleFieldId()).getName())) != null))
            {
                tmp = field.setValue(tmp);
                short fieldId = field.getFieldId();
                fiddef = dictionary.getFidDef(fieldId);
            }
        }
        else
        {
            setFirstField(fentry, fiddef, field);
        }
    }

    private void setFirstField(OMMFieldEntry fentry, FidDef fiddef, FieldValue field)
    {
        OMMData data = fentry.getData(fiddef.getOMMType());
        if (data.getType() == OMMTypes.RMTES_STRING)
        {
            OMMDataBuffer db = (OMMDataBuffer)data;
            if (db.hasPartialUpdates())
            {
                Iterator<?> iter = ((OMMDataBuffer)data).partialUpdateIterator();
                StringBuilder newValue = new StringBuilder(fiddef.getMaxOMMLength());
                newValue.append(field.getStringValue());
                while (iter.hasNext())
                {
                    OMMDataBuffer partial = (OMMDataBuffer)iter.next();
                    String partialString = partial.toString();
                    int hpos = partial.horizontalPosition();
                    int end = hpos + partialString.length();
                    newValue.replace(hpos, end, partialString);
                }
                field.setValue(newValue.toString());
            }
            else
            {
                field.setValue(data.toString());
            }
        }
        else
            field.setValue(data.toString());
    }
}
