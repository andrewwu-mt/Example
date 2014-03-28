package com.reuters.rfa.example.omm.gui.viewer;

import java.util.Iterator;

import javax.swing.table.AbstractTableModel;

import com.reuters.rfa.common.QualityOfService;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.omm.OMMArray;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMDataBuffer;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMFieldEntry;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMQos;
import com.reuters.rfa.omm.OMMTypes;

/**
 * Stores a single field as a String and manages when the field was last updated
 * so a GUI component can determine how the field's value should be displayed.
 */
public class FieldValue
{
    String _name;
    Object _value;
    short _type;
    short _fid;

    int _fade;

    AbstractTableModel _model;

    public FieldValue(AbstractTableModel model, FidDef def)
    {
        _model = model;
        if (def != null)
        {
            _name = def.getName();
            _type = def.getOMMType();
            _fid = def.getFieldId();
        }
    }

    public boolean fade()
    {
        if (_fade > 0)
        {
            _fade--;
            if (_fade == 0)
            {
                return true;
            }
        }
        return false;
    }

    public boolean isUpdated()
    {
        return _fade > 0;
    }

    public Object setValue(Object value)
    {
        Object oldvalue = _value;
        _value = value;
        setFade();
        return oldvalue;
    }

    public void setFade()
    {
        _fade = 2;
    }

    public void update(OMMFieldEntry entry)
    {
        OMMData d = entry.getData(_type);
        setData(d);
    }

    public void remove()
    {
        _value = null;
    }

    private void setData(OMMData data)
    {
        switch (data.getType())
        {
            case OMMTypes.ARRAY:
            {
                setArray((OMMArray)data);
            }
                break;
            case OMMTypes.RMTES_STRING:
            {
                OMMDataBuffer db = (OMMDataBuffer)data;
                if (db.hasPartialUpdates())
                {
                    Iterator<?> iter = ((OMMDataBuffer)data).partialUpdateIterator();
                    StringBuilder newValue = new StringBuilder(getStringValue().length());
                    newValue.append(getStringValue());
                    while (iter.hasNext())
                    {
                        OMMDataBuffer partial = (OMMDataBuffer)iter.next();
                        String partialString = partial.toString();
                        int hpos = partial.horizontalPosition();
                        int end = hpos + partialString.length();
                        newValue.replace(hpos, end, partialString);
                    }
                    _value = newValue.toString();
                }
                else
                {
                    _value = data.toString();
                }
            }
                break;
            default:
            {
                if (OMMTypes.isDataFormat(data.getType()))
                {
                    // not supported yet
                }
                else
                {
                    _value = data.toString();
                }
            }
        }
        setFade();
    }

    @SuppressWarnings("deprecation")
    private void setArray(OMMArray array)
    {
        switch (array.getDataType())
        {
            case OMMTypes.INT32:
            case OMMTypes.UINT32:
            {
                int[] value = new int[array.getCount()];
                int i = 0;
                for (Iterator<?> iter = array.iterator(); iter.hasNext();)
                {
                    value[i++] = (int)((OMMNumeric)((OMMEntry)iter.next()).getData()).toLong();
                }
                _value = value;
            }
                break;
            case OMMTypes.QOS:
            {
                QualityOfService[] value = new QualityOfService[array.getCount()];
                int i = 0;
                for (Iterator<?> iter = array.iterator(); iter.hasNext();)
                {
                    value[i++] = ((OMMQos)((OMMEntry)iter.next()).getData()).toQos();
                }
                _value = value;
            }
                break;
            default:
            {
                String[] value = new String[array.getCount()];
                int i = 0;
                for (Iterator<?> iter = array.iterator(); iter.hasNext();)
                {
                    value[i++] = ((OMMEntry)iter.next()).getData().toString();
                }
                _value = value;
            }
                break;
        }
    }

    public String getName()
    {
        return _name;
    }

    public short getOMMType()
    {
        return _type;
    }

    public short getFieldId()
    {
        return _fid;
    }

    public Object getValue()
    {
        return _value;
    }

    public String getStringValue()
    {
        return (_value == null) ? "" : _value.toString();
    }
}
