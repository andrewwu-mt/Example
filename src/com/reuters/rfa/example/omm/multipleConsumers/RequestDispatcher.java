package com.reuters.rfa.example.omm.multipleConsumers;

/**
 * Thread to dispatch item requests; Exits when all requests are made
 */
public class RequestDispatcher extends Thread
{
    ConsumerClient m_parent;

    public RequestDispatcher(ConsumerClient parent, String name)
    {
        setName("App Request Dispatcher-" + name);
        m_parent = parent;
    }

    public void run()
    {
        while (true)
        {
            boolean bSendItemRequest = m_parent.makeItemRequests();

            if (bSendItemRequest == true)
                break;

            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        m_parent.log("Exiting Request Dispatcher Thread.......");
    }
}
// //////////////////////////////////////////////////////////////////////////////
// / End of file
// //////////////////////////////////////////////////////////////////////////////

