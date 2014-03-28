package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import javax.swing.table.*;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class DateRenderer extends DefaultTableCellRenderer
{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	DateFormat formatter;

    public DateRenderer()
    {
        super();
    }

    public void setValue(Object value)
    {
        if (value instanceof Date)
        {
            if (formatter == null)
            {
                formatter = new SimpleDateFormat("HH:mm:ss");
            }

            super.setValue((value == null) ? "" : formatter.format(value));
        }

    }

}
