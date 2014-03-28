package com.reuters.rfa.example.framework.sub;

import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.Dispatchable;
import com.reuters.rfa.common.EventQueue;

/**
 * RunSync is an utility object. It is used for a synchronous request.
 * setSyncReceived must be called to notify the RunSync that the application has
 * received an event.
 */
class RunSync
{
    public RunSync()
    {
        _syncReceived = false;
        _syncEventQueue = EventQueue.create("myEventQueue");
    }

    /**
     * Dispatch events until timeout has expired or setSyncReceived() is called.
     * 
     * @param time the time it need to wait in millisecond. -1 is for infinite.
     * @return true if setSyncReceived() is called, false if timeout has
     *         expired.
     */
    public boolean run(long time)
    {
        return (time == Dispatchable.INFINITE_WAIT) ? runSyncInfinite() : runSyncTimeout(time);
    }

    /**
     * Call to notify that run() can be terminated.
     */
    public void setSyncReceived()
    {
        _syncReceived = true;
    }

    protected boolean runSyncInfinite()
    {
        while (!_syncReceived)
        {
            try
            {
                _syncEventQueue.dispatch(EventQueue.INFINITE_WAIT);
            }
            catch (DispatchException de)
            {
                System.out.println("Queue deactivated");
            }
            catch (Exception dae)
            {
                dae.printStackTrace();
            }
        }
        return _syncReceived;
    }

    protected boolean runSyncTimeout(long timeoutMillisecs)
    {
        long expireTime = System.currentTimeMillis() + timeoutMillisecs;
        long dispatchTimeout = timeoutMillisecs;
        while (!_syncReceived)
        {
            try
            {
                if (dispatchTimeout <= 0)
                    return false || _syncReceived;
                _syncEventQueue.dispatch(dispatchTimeout);
                dispatchTimeout = expireTime - System.currentTimeMillis();
            }
            catch (DispatchException de)
            {
                System.out.println("Queue deactivated");
            }
            catch (Exception dae)
            {
                dae.printStackTrace();
            }
        }
        return true;
    }

    EventQueue _syncEventQueue;
    boolean _syncReceived;
}
