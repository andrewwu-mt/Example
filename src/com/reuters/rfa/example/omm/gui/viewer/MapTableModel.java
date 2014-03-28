package com.reuters.rfa.example.omm.gui.viewer;

import java.util.ArrayList;
import java.util.Iterator;

import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;

/**
 * Adapt an {@link com.reuters.rfa.omm.OMMMap OMMMap} into a
 * <code>TableModel</code> that can be used by used by a <code>JTable</code>.
 */
public class MapTableModel extends FadingTableModel
{
    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;
    ArrayList<FidDef> _columns;
    MapValues _values;
    FieldDictionary _dictionary;
    boolean _initialized;

    public MapTableModel(FieldDictionary dictionary)
    {
        _initialized = false;
        _dictionary = dictionary;
        _columns = new ArrayList<FidDef>();
    }

    public void processClear()
    {
        fireTableStructureChanged();
        fireTableDataChanged();
    }

    public String getColumnName(int column)
    {
        if (column == 0)
            return "Key";
        return ((FidDef)_columns.get(column - 1)).getName();
    }

    public int getColumnCount()
    {
        int size = _columns.size();
        if (size > 0)
            return size + 1;
        else if (_initialized)
            return 1;
        else
            return 0;
    }

    public int getRowCount()
    {
        return (_values != null) ? _values.size() : 0;
    }

    public Object getValueAt(int rowIndex, int columnIndex)
    {
        if (columnIndex == 0)
            return _values.getStringValue(rowIndex, null);
        FidDef fiddef = (FidDef)_columns.get(columnIndex - 1);
        String fieldStringValue = _values.getStringValue(rowIndex, fiddef);

        if (fiddef.getOMMType() == OMMTypes.ENUM)
            return _dictionary.expandedValueFor(fiddef.getFieldId(),
                                                Integer.parseInt(fieldStringValue));

        return fieldStringValue;
    }

    public void addColumn(FidDef fiddef)
    {
        _columns.add(fiddef);
    }

    public void addKeyColumn(Object keyColumnObject)
    {
        _initialized = true;
    }

    public void clearColumns()
    {
        _columns.clear();
        _initialized = false;
    }

    public void fade()
    {
        int i = 0;

        for (Iterator<FieldListValues> iter = _values.iterator(); iter.hasNext();)
        {
            FieldListValues values = iter.next();
            int j = 0;
            for (Iterator<FieldValue> fiter = values.iterator(); fiter.hasNext();)
            {
                FieldValue fv = fiter.next();
                if (fv.fade())
                    fireTableCellUpdated(values._row, j);
                j++;
            }
            i++;
        }
    }

    public boolean isSuspect()
    {
        return _values.getDataState() == OMMState.Data.SUSPECT;
    }

    public boolean isUpdated(int rowIndex, int columnIndex)
    {
        if (columnIndex == 0)
            return false;

        FidDef fiddef = (FidDef)_columns.get(columnIndex - 1);
        FieldValue fieldValue = _values.getFieldValue(rowIndex, fiddef);
        if (fieldValue == null)
            return false;
        return fieldValue.isUpdated();

    }

}
