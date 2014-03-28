package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import javax.swing.table.DefaultTableCellRenderer;
import java.text.NumberFormat;

public class CurrencyCellRenderer extends DefaultTableCellRenderer
{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	NumberFormat formatter = null;

    public CurrencyCellRenderer()
    {
        super();
        setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    }

    public void setValue(Object value)
    {
        if ((value != null) && (value instanceof Number))
        {
            if (formatter == null)
            {
                formatter = NumberFormat.getNumberInstance();
                formatter.setMaximumFractionDigits(2);
                formatter.setMinimumFractionDigits(2);
            }
            Number numberValue = (Number)value;
            value = formatter.format(numberValue.doubleValue());
        }
        super.setValue(value);
    }

}
