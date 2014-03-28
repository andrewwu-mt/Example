package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import javax.swing.SwingConstants;
import javax.swing.table.*;

public class StringRenderer extends DefaultTableCellRenderer
{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public StringRenderer()
    {
        super();
    }

    public void setValue(Object value)
    {
        if (value instanceof String)
        {
            this.setHorizontalAlignment(SwingConstants.LEFT);
        }
        super.setValue((value == null) ? "" : value);

    }
}
