package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import java.util.TimerTask;

public class TimerClient extends TimerTask
{
    private TimerCallback myTimerCallBack;

    public TimerClient(TimerCallback timerCallBack)
    {
        myTimerCallBack = timerCallBack;
    }

    public void run()
    {
        myTimerCallBack.processTimer();
    }

}
