package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import javax.swing.table.DefaultTableModel;
import java.util.Vector;
import java.util.Date;

public class MyTableModel extends DefaultTableModel
{
    private static final long serialVersionUID = 1L;

    public MyTableModel(Vector<?> colNames, int rowCount)
    {
        super(colNames, rowCount);

    }

    public MyTableModel()
    {
        super();

    }

    public void setColumnNames(Vector<?> colNames)
    {
        this.columnIdentifiers = colNames;
    }

    public void setData(Vector<?> data)
    {
        this.dataVector = data;
    }

    public Class<?> getColumnClass(int col)
    {
        Class<?> c = null;

        String name = getColumnName(col);
        if (name.indexOf("OrderID") >= 0)
        {
            c = String.class;
        }
        else if (name.indexOf("Price") >= 0)
        {
            c = Float.class;
        }
        else if (name.indexOf("Size") >= 0)
        {
            c = Integer.class;
        }
        else if (name.indexOf("Time") >= 0)
        {
            c = Date.class;
        }

        return c;
    }

    public boolean isCellEditable(int row, int col)
    {
        return false;
    }

}
