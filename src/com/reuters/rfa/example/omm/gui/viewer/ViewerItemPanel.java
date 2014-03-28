package com.reuters.rfa.example.omm.gui.viewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.gui.JLoggedStatusBar;
import com.reuters.rfa.example.utility.gui.Status;

/**
 * This class is responsible for:
 * <ul>
 * <li>Constructing the JTables for displaying field lists, maps of field lists,
 * or maps of field lists with summary data. If Java 6 or later is used, the
 * tables will be sortable. Note that sorting large tables, such as those used
 * for symbol lists, can be very time consuming.
 * <li>Creating the {@link OMMClient} and passing it the requested parameters.
 * </ul>
 * 
 */
public class ViewerItemPanel
{

    protected ViewerPanel _viewer;

    protected OMMClient _tableClient;

    protected JTable _mapTable;
    protected JTable _seriesTable;
    protected JTable _summaryTable;
    protected JTable _fieldListTable;
    protected JPanel _panel;

    protected JPanel _tablePanel;
    JLoggedStatusBar _status;

    public ViewerItemPanel(ViewerPanel viewer)
    {
        _viewer = viewer;
        _status = new JLoggedStatusBar();
        _tableClient = new OMMClient(_viewer._appContext, _status);
        initGui(_status);
    }

    public void initGui(Status status)
    {
        _panel = new JPanel();

        initSummaryPanel();
        initMapEntriesPanel();

        _tablePanel = new JPanel(new BorderLayout());

        _tableClient._itemPanel = this;

        _panel.setLayout(new BorderLayout());
        _panel.add("Center", _tablePanel);
        _panel.add("South", ((JLoggedStatusBar)status).component());
    }

    public JComponent component()
    {
        return _panel;
    }

    public void clearTablePanel()
    {
        _tablePanel.removeAll();
        _tablePanel.invalidate();
        _tablePanel.doLayout();
        _tablePanel.repaint();
    }

    public void enableFieldListTablePanel()
    {
        clearTablePanel();
        JComponent fl = initFieldListPanel();
        _tablePanel.add("Center", fl);
        _tablePanel.invalidate();
        _tablePanel.doLayout();
        _tablePanel.repaint();
    }

    public void enableMapPanel()
    {
        clearTablePanel();
        JComponent tableMapPane = initMapEntriesPanel();
        _tablePanel.add("Center", tableMapPane);
        _tablePanel.invalidate();
        _tablePanel.doLayout();
        _tablePanel.repaint();
    }

    public void enableMapSummaryPanel()
    {
        clearTablePanel();
        JComponent tableMapPane = initMapEntriesPanel();
        JComponent summaryTablePane = initSummaryPanel();
        _tablePanel.add("North", summaryTablePane);
        _tablePanel.add("Center", tableMapPane);
        _tablePanel.invalidate();
        _tablePanel.doLayout();
        _tablePanel.repaint();
    }

    private void initTableSorter(JTable table, AbstractTableModel model)
    {
        // NOTE 1: this code only works with JDK 1.6 or later
        // NOTE 2: since the data is stored as Strings, the data will be
        // in String order, not numeric order. This means "1000" < "200"
        // instead of 200 < 1000.
        // NOTE 3: TableRowSorter does not perform well with large tables.
        // A more robust approach would be to store the OMMMap data,
        // sort it by value, and then only display the top of the data
        // instead of letting TableRowSorter do the sorting.
        try
        {
            Class<?> aClass = Class.forName("javax.swing.table.TableRowSorter");
            Class<?>[] cArgs = { javax.swing.table.TableModel.class };
            Constructor<?> aCons = aClass.getConstructor(cArgs);
            Object[] oArr = { model };
            Object tableRowSorter = aCons.newInstance(oArr); // new
                                                             // TableRowSorter(_tableModel);

            Class<?> tableClass = javax.swing.JTable.class;
            Class<?> aClass2 = Class.forName("javax.swing.RowSorter");
            Class<?>[] cArgs2 = { aClass2 };
            Method m = tableClass.getMethod("setRowSorter", cArgs2);
            Object[] oArr2 = { tableRowSorter };
            m.invoke(table, oArr2); // _table.setRowSorter(tableRowSorter);

            Class<?> aClass3 = Class.forName("javax.swing.DefaultRowSorter");
            Class<?>[] cArgs3 = { Boolean.TYPE };
            m = aClass3.getMethod("setSortsOnUpdates", cArgs3);
            Object[] oArr3 = { Boolean.TRUE };
            m.invoke(tableRowSorter, oArr3);
        }
        catch (Exception e)
        {
            System.out.println("Sorter not available");
        }
    }

    private JComponent initMapEntriesPanel()
    {
        FadingTableModel model = _tableClient.getMapTableModel();
        if (model._table == null)
        {
            _mapTable = new JTable(model);
            if (CommandLine.booleanVariable("sort"))
                initTableSorter(_mapTable, model);
        }
        return initTable(_mapTable, model);
    }

    private JComponent initSummaryPanel()
    {
        FadingTableModel model = _tableClient.getMapSummaryTableModel();
        if (model._table == null)
        {
            _summaryTable = new JTable(model);
        }
        return initTable(_summaryTable, model);
    }

    private JComponent initFieldListPanel()
    {
        FadingTableModel model = _tableClient.getFieldListTableModel();
        if (model._table == null)
        {
            _fieldListTable = new JTable(model);
            if (CommandLine.booleanVariable("sort"))
                initTableSorter(_fieldListTable, model);
        }
        return initTable(_fieldListTable, model);
    }

    private JComponent initTable(JTable table, FadingTableModel model)
    {
        model._table = table;
        table.setDefaultRenderer(Object.class, model.getCellRenderer());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowSelectionAllowed(true);
        table.setSelectionMode(2);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(true);
        table.setPreferredScrollableViewportSize(new Dimension(50, 50));
        JComponent tablePane = new JScrollPane(table);
        return tablePane;
    }

    protected void cleanUp()
    {
    }

    protected void open(short mmt, String servicename, String ric, boolean isStreaming)
    {
        _tableClient.open(mmt, servicename, ric, isStreaming);
        _viewer._ricField.selectAll();
    }

}
