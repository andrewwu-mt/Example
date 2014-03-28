package com.reuters.rfa.example.omm.consPerf;

import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.Dispatchable;
import com.reuters.rfa.common.EventQueue;

/**
 * <p>
 * This class dispatches OMM messages from an optional
 * {@link com.reuters.rfa.common.EventQueue EventQueue} response queue. In case
 * of null response queue, it simple waits until application is running for time
 * specified by runTime parameter.
 */
public class Dispatcher extends Thread
{
    EventQueue _responseQueue;
    private boolean _isRunning = true;

    public Dispatcher(EventQueue responseQueue)
    {
        _responseQueue = responseQueue;
    }

    public synchronized void run()
    {
        if (_responseQueue != null)
            System.out.println(getName() + " : starting dispatch");

        if (_responseQueue == null)
        {
            while (_isRunning)
            {
                try
                {
                    wait();
                }
                catch (InterruptedException e)
                {
                }
            }
        }
        else
        {
            while (_isRunning)
            {
                try
                {
                    _responseQueue.dispatch(Dispatchable.INFINITE_WAIT);
                }
                catch (DispatchException de)
                {
                    System.out.println(getName() + " : Queue deactivated");
                    break;
                }
            }
        }
    }

    public void terminate(RequestManager reqMgr)
    {
        System.out.println("Controller: terminating...");
        reqMgr.cleanup(0);
        _isRunning = false;
        this.interrupt();
    }
}
