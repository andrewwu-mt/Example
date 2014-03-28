package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import java.text.DateFormat;
import java.util.Date;

public class Clock implements Runnable
{
    public Clock(OrderBookDisplay window)
    {
        super();
        _formatter = DateFormat.getTimeInstance(DateFormat.MEDIUM);
        _mainwindow = window;
        done = false;
    }

    /**
     * run
     * 
     * Implement this java.lang.Runnable method
     */
    public void run()
    {
        while (!done)
        {
            now = new Date();
            _mainwindow.updateCurrentTime(_formatter.format(now));
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
            }
        }
    }

    public void setDone(boolean state)
    {
        done = state;
    }

    private DateFormat _formatter;
    private Date now;
    private OrderBookDisplay _mainwindow;
    private boolean done = false;
}
