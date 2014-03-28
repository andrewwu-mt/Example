package com.reuters.rfa.example.omm.multipleConsumers;

import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.Dispatchable;
import com.reuters.rfa.common.EventQueue;

/**
 * Thread to dispatch events; Created only is eventQ is used; Exits on shutdown
 */
public class ResponseDispatcher extends Thread
{
    ConsumerClient m_parent;
    private EventQueue m_eventQueue = null;

    private volatile boolean m_isRunning = true;

    public ResponseDispatcher(ConsumerClient parent, String name, EventQueue eventQueue)
    {
        setName("App ResponseQ Dispatcher-" + name);
        m_parent = parent;
        m_eventQueue = eventQueue;
    }

    public void terminate()
    {
        m_parent.log("Dispatcher: terminating...");
        m_isRunning = false;
        this.interrupt();
    }

    public synchronized void run()
    {
        m_parent.log("Start Dispatching Responses............");

        while (m_isRunning)
        {
            try
            {
                m_eventQueue.dispatch(Dispatchable.INFINITE_WAIT);
            }
            catch (DispatchException de)
            {
                m_parent.log("Queue deactivated");
                break;
            }
        }

        m_parent.log("Dispatcher terminated...");
    }
}
// //////////////////////////////////////////////////////////////////////////////
// / End of file
// //////////////////////////////////////////////////////////////////////////////

