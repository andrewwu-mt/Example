package com.reuters.rfa.example.omm.gui.quotelist;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.Enumeration;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.utility.gui.Status;

/**
 * This class implements a TableModel for displaying data of each record in
 * Swing's JTable. It uses {@link RecordRenderer}, {@link FieldRenderer}, and
 * LoggedStatusBar to display and fade updates for the records supplied to
 * {@link #add(String, String)} and field names supplied to
 * {@link #setFieldNames(String[])}.
 */
public class RecordTableModel extends AbstractTableModel implements TableCellRenderer,
        ActionListener, AdjustmentListener, Observer
{
    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;

    public RecordTableModel(SubAppContext appContext, Status status)
    {
        _appContext = appContext;
        _emptyLabel = new JLabel("XX");
        _pendingRics = new Vector<String>();
        _headerRenderers = new Vector<JComponent>();
        _recordRows = new Vector<RecordRenderer>();
        _status = status;
        _table = new JTable(this);
        _fontMetrics = _table.getFontMetrics(UIManager.getFont("Table.font"));
        _table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        _table.setRowSelectionAllowed(true);
        _table.setSelectionMode(2);
        _table.setShowHorizontalLines(true);
        _table.setShowVerticalLines(true);
        _table.setPreferredScrollableViewportSize(new Dimension(50, 50));
        _table.setDefaultRenderer(getColumnClass(0), this);
        _tablePane = new JScrollPane(_table);
        _tablePane.getVerticalScrollBar().addAdjustmentListener(this);
    }

    /**
     * Request a record from the service
     */
    public void add(String servicename, String ric)
    {
        // if (_fieldNames == null)
        // return;

        synchronized (this)
        {
            if (hasRecord(ric) || _pendingRics.contains(ric))
                return;

            _pendingRics.addElement(ric);
            _servicename = servicename;
        }
        createRenderers();
    }

    protected void createRenderers()
    {
        synchronized (this)
        {
            for (int i = 0; i < _pendingRics.size(); i++)
            {
                String ric = (String)_pendingRics.elementAt(i);
                RecordRenderer rr = new RecordRenderer(_appContext, _servicename, ric, _fieldNames,
                        this, _status);
                _recordRows.addElement(rr);
                rr.addObserver(this);
                int row = _recordRows.size();
                fireTableRowsInserted(row, row);
                if (_table.isShowing())
                {
                    // Rectangle showingRect =
                    // _tablePane.getViewport().getViewRect(); // TODO not
                    // needed?
                    Rectangle cellRect = _table.getCellRect(_table.getRowCount(), 0, true);
                    cellRect.setSize(_table.getWidth(), cellRect.getSize().height);
                    // cellRect may not be init'ed yet
                    // so always setShowing to true
                    // rr.setShowing(showingRect.intersects(cellRect));
                    rr.setShowing(true);
                }
            }
            _pendingRics.removeAllElements();
        }
        resizeColumns();
    }

    // AdjustmentListener
    public void adjustmentValueChanged(AdjustmentEvent e)
    {
        Rectangle showingRect = _tablePane.getViewport().getViewRect();
        for (int i = 0; i < getRowCount(); i++)
        {
            Rectangle cellRect = _table.getCellRect(i, 0, true);
            cellRect.setSize(_table.getWidth(), cellRect.getSize().height);
            getRow(i).setShowing(showingRect.intersects(cellRect));
        }
    }

    /**
     * Make sure this is called to properly clean up record clients
     */
    public void cleanUp()
    {
        for (Enumeration<RecordRenderer> e = _recordRows.elements(); e.hasMoreElements();)
        {
            RecordRenderer rr = (RecordRenderer)e.nextElement();
            rr.deleteObserver(this);
            rr.cleanup();
        }
        _recordRows.removeAllElements();
        removeAll();
    }

    /**
     * Used to access this table model's view, so it can be added to a container
     */
    public Component component()
    {
        return _tablePane;
    }

    /**
     * When applet is disabled, fade thread is stopped
     */
    public void disable()
    {
        if (_timer != null)
        {
            _timer.stop();
            _timer = null;
        }
    }

    /**
     * When applet is enabled, fade thread is started
     */
    public void enable()
    {
        if (_timer == null)
        {
            _timer = new Timer(1000, this);
            _timer.start();
        }
        else
            _timer.restart();
    }

    public int getColumnCount()
    {
        if (_fieldNames != null)
            return _fieldNames.length;
        return 0;
    }

    public String getColumnName(int column)
    {
        return _fieldNames[column];
    }

    public int getColumnWidth(int col)
    {
        String fieldName = getColumnName(col);
        int value = _fontMetrics.stringWidth(fieldName);
        for (int row = 0, rowCount = getRowCount(); row < rowCount; row++)
        {
            int fieldWidth = _fontMetrics.stringWidth((String)getValueAt(row, col));
            value = Math.max(value, fieldWidth);
        }
        return value + 20;
    }

    public int getRowCount()
    {
        return _recordRows.size();
    }

    protected String getSymbolForRow(int i)
    {
        return getRow(i).symbol();
    }

    /**
     * TableCellRenderer interface Normally, a single cell renderer is shared
     * through a table for every column that has the same class. The record
     * table requires separate cell renderers for each cell be each cell is
     * highlighted and updated separately. This method retrieves the
     * FieldRenderer for the cell requested. If the cell does not exist (the
     * record for the given row does not contain a field) A dummy label is
     * created to use as a renderer.
     */
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column)
    {
        if (!_pendingRics.isEmpty())
            createRenderers();

        // column is index of table view, not of table model.
        // Must use JTable.getColumnName(int) instead of
        // TableModel.getColumnName(int)
        JComponent c = null;
        if (row != -1)
        {
            synchronized (this)
            {
                RecordRenderer rr = (RecordRenderer)_recordRows.elementAt(row);
                c = rr.fieldRenderer(table.getColumnName(column));
            }
        }
        else
        // header
        {
            // for some reason, when moving columns value of column
            // is incremented by one. For now, just prevent the
            // array out of bounds exception. Operation will perform
            // correctly anyway.
            if (column >= getColumnCount())
                column = getColumnCount() - 1;
            c = (JComponent)_headerRenderers.elementAt(column);
        }
        if (c == null)
        {
            // Create dummy renderer
            if (value == "XX")
                c = _emptyLabel;
            else
                c = new JLabel((String)value);
            c.setForeground(Color.black);

            // cache the header renderer
            if (row == -1)
                _headerRenderers.setElementAt(c, column);
        }
        if (row == -1)
        {
            ((JLabel)c).setText((String)value);
            c.setBorder(new BevelBorder(BevelBorder.RAISED));
        }
        else
        {
            if (isSelected)
                c.setBackground(table.getSelectionBackground());
            else
                c.setBackground(table.getBackground());
        }
        return c;
    }

    public Object getValueAt(int row, int col)
    {
        String fieldName = getColumnName(col);
        RecordRenderer rr = (RecordRenderer)_recordRows.elementAt(row);
        return rr.getValue(fieldName);
    }

    public boolean hasRecord(String ric)
    {
        for (int i = 0, recordCount = _recordRows.size(); i < recordCount; i++)
        {
            if (((RecordRenderer)_recordRows.elementAt(i)).symbol().equals(ric))
                return true;
        }
        return false;
    }

    /**
     * Drops record clients
     */
    public void remove(int[] rows)
    {
        // go in reverse order so indexes don't change as rows are deleted
        for (int i = rows.length - 1; i >= 0; i--)
        {
            int removeIndex = rows[i];
            synchronized (this)
            {
                RecordRenderer rr = (RecordRenderer)_recordRows.elementAt(removeIndex);
                _status.setStatus("Removed: " + rr.symbol());
                _recordRows.removeElementAt(removeIndex);
                rr.deleteObserver(this);
                rr.cleanup();
            }
            fireTableRowsDeleted(i, i);
        }
        // if (_recordRows.isEmpty())
        // _fieldNames = null;
        fireTableStructureChanged();
        resizeColumns();
    }

    /**
     * Drop all record clients
     */
    public void removeAll()
    {
        // go in reverse order so indexes don't change as rows are deleted
        for (int i = _recordRows.size() - 1; i >= 0; i--)
        {
            synchronized (this)
            {
                RecordRenderer rr = (RecordRenderer)_recordRows.elementAt(i);
                rr.deleteObserver(this);
                rr.cleanup();
                _recordRows.removeElementAt(i);
                fireTableRowsDeleted(i, i);
            }
        }
        // _fieldNames = null;
        fireTableStructureChanged();
    }

    public void removeSelected()
    {
        remove(_table.getSelectedRows());
        _table.clearSelection();
    }

    /**
     * Resize the columns to fit the longest string in each column
     */
    public void resizeColumns()
    {
        TableColumn column = null;
        int cellWidth = 0;
        for (int col = 0; col < getColumnCount(); col++)
        {
            column = _table.getColumnModel().getColumn(col);
            cellWidth = getColumnWidth(col);
            if (column.getMinWidth() < cellWidth)
            {
                column.setMinWidth(cellWidth);

                // swing 1.0.3 method
                // column.setWidth(cellWidth);

                // JDK 1.2 method
                column.setPreferredWidth(cellWidth);
            }
        }
        _table.getTableHeader().resizeAndRepaint();
        _table.revalidate();
        _table.repaint();
    }

    /**
     * Set the column names for the table
     */
    public void setFieldNames(String[] newFieldNames)
    {
        boolean changed = false;
        if (_fieldNames == null)
            changed = true;
        else if (newFieldNames.length != _fieldNames.length)
            changed = true;

        if (newFieldNames.length > _headerRenderers.size())
            _headerRenderers.setSize(newFieldNames.length);

        for (int i = 0; !changed && (i < _fieldNames.length); i++)
        {
            if (!_fieldNames[i].equals(newFieldNames[i]))
            {
                changed = true;
            }
        }

        _fieldNames = newFieldNames;

        if (changed)
            fireTableStructureChanged();

        // model also serves as header renderer
        for (int i = 0; i < newFieldNames.length; i++)
            _table.getColumnModel().getColumn(i).setHeaderRenderer(this);

        resizeColumns();
    }

    public void setSelectable(boolean select)
    {
        if (select)
        {
            _table.setRowSelectionAllowed(true);
            _table.setSelectionMode(2);
        }
        else
            _table.setRowSelectionAllowed(false);
    }

    // ActionListener
    public void actionPerformed(ActionEvent ae)
    {
        for (Enumeration<RecordRenderer> e = _recordRows.elements(); e.hasMoreElements();)
            ((RecordRenderer)e.nextElement()).fade();
        component().validate();
        component().repaint();
    }

    protected RecordRenderer getRow(int i)
    {
        return (RecordRenderer)_recordRows.elementAt(i);
    }

    public void update(Observable ob, Object obj)
    {
        int row = _recordRows.indexOf(ob);
        fireTableRowsUpdated(row, row);
        resizeColumns();
    }

    public void change(String oldElement, String element)
    {
        for (int i = 0; i < _recordRows.size(); i++)
        {
            RecordRenderer rr = (RecordRenderer)_recordRows.get(i);
            if (oldElement.equals(rr.symbol()))
            {
                _status.setStatus("Changed: " + oldElement + " to " + element);
                rr.changeSymbol(element);
                fireTableRowsUpdated(i, i);

                fireTableStructureChanged();
                resizeColumns();
                return;
            }
        }
    }

    public void remove(String removedElement)
    {
        for (int i = 0; i < _recordRows.size(); i++)
        {
            RecordRenderer rr = (RecordRenderer)_recordRows.get(i);
            if (removedElement.equals(rr.symbol()))
            {
                _status.setStatus("Removed: " + rr.symbol());
                _recordRows.removeElementAt(i);
                rr.deleteObserver(this);
                rr.cleanup();
                fireTableRowsDeleted(i, i);
                if (_recordRows.isEmpty())
                    _fieldNames = null;

                fireTableStructureChanged();
                resizeColumns();
                return;
            }
        }
    }

    public SubAppContext appContext()
    {
        return _appContext;
    }

    protected Vector<String> _pendingRics;
    protected Vector<RecordRenderer> _recordRows;
    protected Vector<JComponent> _headerRenderers;
    protected String[] _fieldNames;
    protected JTable _table;
    protected Status _status;
    protected SubAppContext _appContext;

    protected FontMetrics _fontMetrics;
    protected JScrollPane _tablePane;
    protected String _servicename;
    protected JLabel _emptyLabel;

    protected javax.swing.Timer _timer;

}
