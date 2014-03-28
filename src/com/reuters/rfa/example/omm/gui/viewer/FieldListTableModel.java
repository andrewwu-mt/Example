package com.reuters.rfa.example.omm.gui.viewer;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.Iterator;

import javax.swing.SwingUtilities;
import javax.swing.table.TableColumn;

import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;

/**
 * Adapt an {@link com.reuters.rfa.omm.OMMFieldList OMMFieldList} into a
 * <code>TableModel</code> that can be used by used by a <code>JTable</code>.
 */
public class FieldListTableModel extends FadingTableModel
{
    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;
    FieldListValues _fieldListValues;
    FieldDictionary _dictionary;
    boolean _vertical;

    public FieldListTableModel(FieldDictionary dictionary, boolean vert)
    {
        _dictionary = dictionary;
        _vertical = vert;
    }

    public String getColumnName(int columnIndex)
    {
        if (_vertical)
            return (columnIndex == 0) ? "Name" : "Value";

        FieldValue field = null;
        Iterator<FieldValue> iterator = _fieldListValues.iterator();
        for (int i = 0; i <= columnIndex; i++)
            field = (FieldValue)iterator.next();
        return field.getName();
    }

    public int getColumnCount()
    {
        if (_vertical)
            return 2;
        return _fieldListValues.size();
    }

    public int getRowCount()
    {
        if (_vertical)
            return _fieldListValues.size();
        return 1;
    }

    public Object getValueAt(int rowIndex, int columnIndex)
    {
        return (_vertical) ? getVertValueAt(rowIndex, columnIndex) : getRowValueAt(rowIndex,
                                                                                   columnIndex);
    }

    private Object getVertValueAt(int rowIndex, int columnIndex)
    {
        FieldValue field = null;
        Iterator<FieldValue> iterator = _fieldListValues.iterator();
        for (int i = 0; i <= rowIndex; i++)
            field = (FieldValue)iterator.next();
        if (columnIndex == 0)
        {
            return field.getName();
        }

        String v = field.getStringValue();
        if (field.getOMMType() == OMMTypes.ENUM)
        {
            return _dictionary.expandedValueFor(field.getFieldId(),
                                                Integer.parseInt(field.getStringValue()));
        }

        return v;
    }

    private Object getRowValueAt(int rowIndex, int columnIndex)
    {
        FieldValue field = null;
        Iterator<FieldValue> iterator = _fieldListValues.iterator();
        for (int i = 0; i <= columnIndex; i++)
            field = (FieldValue)iterator.next();
        short type = field.getOMMType();
        String v = field.getStringValue();
        if (type == OMMTypes.ENUM)
            return _dictionary.expandedValueFor(field.getFieldId(),
                                                Integer.parseInt(field.getStringValue()));

        return v;
    }

    public void processClear()
    {
        fireTableStructureChanged();
        fireTableDataChanged();
    }

    public void resizeColumns()
    {
        if (_vertical)
        {
            TableColumn column = _table.getColumnModel().getColumn(1);
            int origWidth = column.getWidth();
            int width = origWidth;
            Font font = _table.getFont();
            FontMetrics fontMetrics = _table.getFontMetrics(font);
            for (Iterator<FieldValue> iter = _fieldListValues.iterator(); iter.hasNext();)
            {
                FieldValue value = (FieldValue)iter.next();
                int textWidth = SwingUtilities.computeStringWidth(fontMetrics,
                                                                  value.getStringValue());
                if (textWidth > width)
                    width = textWidth;
            }
            if (width > origWidth)
            {
                width += _table.getIntercellSpacing().width;
                column.setPreferredWidth(width);
                _table.invalidate();
                _table.doLayout();
                _table.repaint();
            }
        }
        else
        {
            int i = 0;
            boolean changed = false;
            Font font = _table.getFont();
            FontMetrics fontMetrics = _table.getFontMetrics(font);

            for (Iterator<FieldValue> iter = _fieldListValues.iterator(); iter.hasNext();)
            {
                TableColumn column = _table.getColumnModel().getColumn(i);
                int origWidth = column.getWidth();
                FieldValue value = (FieldValue)iter.next();
                int width = SwingUtilities.computeStringWidth(fontMetrics, value.getStringValue());
                if (width > origWidth)
                {
                    width += _table.getIntercellSpacing().width;
                    column.setPreferredWidth(width);
                }
            }
            if (changed)
            {
                _table.invalidate();
                _table.doLayout();
                _table.repaint();
            }
        }
    }

    public void fade()
    {
        if (_fieldListValues == null)
            return;
        int i = 0;
        for (Iterator<FieldValue> iter = _fieldListValues.iterator(); iter.hasNext();)
        {
            FieldValue value = (FieldValue)iter.next();
            if (value.fade())
            {
                if (_vertical)
                    fireTableCellUpdated(i, 1);
                else
                    fireTableRowsUpdated(_fieldListValues._row, _fieldListValues._row);
            }
            i++;
        }
    }

    public boolean isSuspect()
    {
        return _fieldListValues.getDataState() == OMMState.Data.SUSPECT;
    }

    public boolean isUpdated(int rowIndex, int columnIndex)
    {
        if (columnIndex == 0)
            return false;
        Iterator<FieldValue> iter = _fieldListValues.iterator();
        int i = 0;
        while (iter.hasNext())
        {
            FieldValue v = (FieldValue)iter.next();
            if (i == rowIndex)
                return v.isUpdated();
            i++;
        }
        return false;
    }

}
