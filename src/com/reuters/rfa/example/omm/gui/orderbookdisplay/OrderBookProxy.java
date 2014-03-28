package com.reuters.rfa.example.omm.gui.orderbookdisplay;

public class OrderBookProxy
{
    private long _OBViewerPtr; // Pointer to OBViewer
    Callback _window;
    boolean _bAcquired;

    native public void initCpp(Callback window);

    native public long nativeAcquire(short dictType);

    native public void nativeRelease();

    native public void subscribe(String svcName, String itemName);

    native public void unsubscribe();

    native public void setDebug(boolean debug);

    native public void setCallbacks();

    static
    {
        System.loadLibrary("orderbook");
    }

    public OrderBookProxy(Callback window)
    {
        _window = window;
        _bAcquired = false;
        initCpp(window);
    }

    public void acquire(short dictType)
    {
        if (_OBViewerPtr == 0)
        {
            _OBViewerPtr = nativeAcquire(dictType);
            _bAcquired = true;
        }
    }

    public void release()
    {
        nativeRelease();
        _OBViewerPtr = 0;
        _bAcquired = false;
    }

    public boolean isAcquired()
    {
        return _bAcquired;
    }

}
