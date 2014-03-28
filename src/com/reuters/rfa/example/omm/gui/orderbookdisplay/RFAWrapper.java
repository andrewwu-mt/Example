package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMConsumer;
import com.reuters.rfa.common.EventSource;

import java.util.Timer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

public class RFAWrapper implements Runnable, TimerCallback
{
    private Callback _window;

    private final String _sessionName = "OBSession";
    private String _serviceName = null;
    private String _itemName = null;
    private EventQueue _eventQueue = null;
    private Session _rfaSession = null;
    private OMMConsumer eventSource = null;
    private MBOClient mboClient = null;

    private short _dictLocation;

    private int _retryCount = 0;
    private boolean _timerActivated = false;
    private boolean _bAcquired;
    private AtomicBoolean _isStopping;
    private Timer timer;

    private static Preferences prefs = null;
    private static final String prefsNodename = "/com/reuters/rfa";
    private static final String _nameSpace = "myNamespace";

    public RFAWrapper(Callback window)
    {
        _window = window;
        _bAcquired = false;
        _isStopping = new AtomicBoolean(false);

        prefs = Preferences.userRoot().node(prefsNodename);
    }

    public void initRFA(short dictLocation)
    {
        setDictLocation(dictLocation);
        // Context initialize
        if (!Context.initialize())
        {
            _window.notifyStatus("ERROR: Could not Initialize Context");
            System.exit(-1);
        }

        // Acquire Session
        String sess = _nameSpace + "::" + _sessionName;
        _rfaSession = Session.acquire(sess);
        if (_rfaSession == null)
        {
            System.out.println("Could not acquire session.");
            Context.uninitialize();
            System.exit(1);
        }

        // Create an Event Queue
        _eventQueue = EventQueue.create("ommEventQueue");

        // Create OMMConsumer
        eventSource = (OMMConsumer)_rfaSession.createEventSource(EventSource.OMM_CONSUMER,
                                                                 "myOMMConsumer", true);

        // Create data Client
        // create a new MBOClient
        mboClient = new MBOClient(_eventQueue, eventSource, _dictLocation, prefs);
        mboClient.setCallback(_window);

        _isStopping.set(false);
        _bAcquired = true;

        return;
    }

    public void run()
    {
        // mboClient.init();

        // Implement Message Loop to dispatch events
        while (!isStopping())
        {
            try
            {
                _eventQueue.dispatch(1000);
            }
            catch (DispatchException de)
            {
                System.out.println("Queue deactivated");
                return;
            }
        }

        unsubscribe();
    }

    // Implement subscribe
    public void subscribe(String serviceName, String itemName)
    {
        if (!mboClient.subscribe(serviceName, itemName))
        {
            _serviceName = serviceName;
            _itemName = itemName;
            timer = new Timer();
            timer.schedule(new TimerClient(this), 10 * 1000);

            _timerActivated = true;
            _retryCount++;
        }
    }

    public void processTimer()
    {
        if (!mboClient.subscribe(_serviceName, _itemName))
        {
            String str = "Failed to subscribe item name... will retry in ~10 secs...";
            str += _retryCount++;
            _window.notifyStatus(str);
        }
        else
        {
            dropTimerClient();
            _timerActivated = false;
        }
    }

    // Implement unsubscribe
    public void unsubscribe()
    {
        mboClient.unsubscribe();
        if (_timerActivated)
        {
            dropTimerClient();
            _timerActivated = false;
            _window.notifyStatus("Cancelled item subscription.");
        }
        else
            _window.notifyStatus("Item un-subscribed..");
    }

    private void dropTimerClient()
    {
        timer.cancel();
    }

    public void stop()
    {
        _isStopping.set(true);
    }

    public void setDebug(boolean debug)
    {
        mboClient.setDebug(debug);
    }

    public void setDictLocation(short dictLocation)
    {
        _dictLocation = dictLocation;
    }

    public void release()
    {
        stop();
        cleanup();

        _bAcquired = false;
    }

    public boolean isAcquired()
    {
        return _bAcquired;
    }

    public boolean isStopping()
    {
        return _isStopping.get();
    }

    public void cleanup()
    {
        // *****************************************************************
        // Shutdown & Cleanup
        // *****************************************************************

        // force the event queue not send out any events
        _window.notifyStatus("Shutting down RFA");

        if (_timerActivated)
        {
            dropTimerClient();
            _timerActivated = false;
        }

        if (_eventQueue != null)
        {
            _eventQueue.deactivate();
            _eventQueue = null;
        }

        if (mboClient != null)
        {
            mboClient.unsubscribe();
            mboClient.cleanup();
            mboClient = null;
        }

        // Subscriber
        if (eventSource != null)
            eventSource.destroy();

        // Release Session
        if (_rfaSession != null)
            _rfaSession.release();

        // Unitialize Context
        if (Context.getInitializedCount() > 0)
            Context.uninitialize();
    }
}
