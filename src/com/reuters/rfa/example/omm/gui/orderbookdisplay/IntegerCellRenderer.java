package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import javax.swing.table.DefaultTableCellRenderer;
import java.text.NumberFormat;

public class IntegerCellRenderer extends DefaultTableCellRenderer
{
    private static final long serialVersionUID = 1L;
    
    NumberFormat formatter = null;

    public IntegerCellRenderer()
    {
        super();
        setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    }

    public void setValue(Object value)
    {
        if ((value != null) && (value instanceof Integer))
        {
            if (formatter == null)
            {
                formatter = NumberFormat.getIntegerInstance();
            }
            Number numberValue = (Number)value;
            value = formatter.format(numberValue.intValue());
        }
        super.setValue(value);
    }
}
