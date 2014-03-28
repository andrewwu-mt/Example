package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import javax.swing.*;

public class StatusBar extends JLabel
{

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/** Creates a new instance of StatusBar */
    public StatusBar()
    {
        super();
        // super.setPreferredSize(new Dimension(100, 16));
        setVerticalAlignment(SwingConstants.TOP);
        setMessage("Ready");
        try
        {
            jbInit();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public void setMessage(String message)
    {
        setText(" " + message);
    }

    private void jbInit() throws Exception
    {
    }
}
