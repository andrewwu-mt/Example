package com.reuters.rfa.example.framework.sub;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.Dispatchable;
import com.reuters.rfa.common.DispatchableNotificationClient;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.example.utility.CommandLine;

public class AppContextMainLoop implements DispatchableNotificationClient
{
    private EventQueue _eventQueue;
    private List<Runnable> _runnables = Collections.synchronizedList(new ArrayList<Runnable>());
    private long _runTime;
    private PrintStream _printStream;

    public AppContextMainLoop(PrintStream printStream)
    {
        _printStream = (printStream == null) ? System.out : printStream;

        _eventQueue = EventQueue.create("RDMProvider EventQueue");
        _runTime = CommandLine.intVariable("runTime"); // -1 is
                                                       // Dispatchable.INFINITE_WAIT
    }

    static public void addCommandLineOptions()
    {
        CommandLine.addOption("runTime", -1, "How long application should run before exiting (in seconds)");
    }

    public void cleanup()
    {
        _eventQueue.deactivate();
        _eventQueue.destroy();
    }

    public EventQueue getEventQueue()
    {
        return _eventQueue;
    }

    public PrintStream getPrintStream()
    {
        return _printStream;
    }

    public long getRunTime()
    {
        return _runTime;
    }

    /**
     * Dispatch events in the application's thread infinitely or until timeout
     * if -runTime is configured.
     */
    public void run()
    {
        runInit();
        if (_runTime == -1)
            runInfinite();
        else
            runTime();
    }

    /**
     * Dispatch events in {@link java.awt.EventQueue}'s thread. This is
     * non-blocking.
     */
    public void runAwt()
    {
        _eventQueue.registerNotificationClient(this, null);
        // clear any pending events
        try
        {
            while (_eventQueue.dispatch(0) > 0)
                ;
        }
        catch (DispatchException e)
        {
        }
        runInit();
    }

    /*
     * Override if need to initialize something before dispatching events
     */
    protected void runInit()
    {
    }

    private Runnable getRunnable()
    {
        if (_runnables.size() > 0)
        {
            return _runnables.remove(_runnables.size() - 1);
        }
        return new Runnable()
        {
            public void run()
            {
                try
                {
                    while (_eventQueue.dispatch(0) > 0)
                        ;
                }
                catch (DispatchException de)
                {
                    _printStream.println("Queue deactivated");
                }
                catch (Exception dae)
                {
                    dae.printStackTrace();
                }
                _runnables.add(this);
            }
        };
    }

    /*
     * Run infinitely when no -runTime parameter is specified
     */
    private void runInfinite()
    {
        try
        {
            while (true)
                _eventQueue.dispatch(Dispatchable.INFINITE_WAIT);
        }
        catch (DispatchException de)
        {
            _printStream.println("Queue deactivated");
            System.exit(1);
        }
    }

    /*
     * Run the program based on -runTime command line parameter
     */
    private void runTime()
    {
        // Dispatch item events for a while
        long startTime = System.currentTimeMillis();

        while ((System.currentTimeMillis() - startTime) < _runTime * 1000)
            try
            {
                _eventQueue.dispatch(1000);
            }
            catch (DispatchException de)
            {
                _printStream.println("Queue deactivated");
                return;
            }
        _printStream.println(Context.string());
        _printStream.println((System.currentTimeMillis() - startTime) / 1000 + " seconds elapsed, "
                + getClass().toString() + " exiting");
    }

    public void notify(Dispatchable dispSource, Object closure)
    {
        Runnable runnable = getRunnable();
        java.awt.EventQueue.invokeLater(runnable);
    }

}
