package com.reuters.rfa.example.omm.pagedisplay;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Vector;

import com.reuters.rfa.ansipage.Page;
import com.reuters.rfa.ansipage.PageUpdate;
import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.framework.sub.NormalizedEvent;
import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.utility.gui.Status;

/**
 * This is a client class that register page(input from user) into RFA using
 * {@link SubAppContext}. This class implements {@link Client} to process event
 * from the back-end server infrastructure. It uses
 * {@link com.reuters.rfa.example.ansipage ANSI Page package} to parse data and
 * use {@link PagePanel} to display data.
 * 
 */
public class PageClient implements Client
{
    boolean _active;
    boolean _hasData;
    Page _page;
    PagePanel _panel;
    PrintStream _printStream;
    Status _status;
    Handle _handle;
    SubAppContext _appContext;
    String _itemName;
    String _serviceName;
    Vector<PageUpdate> _pageUpdateList;

    public PageClient(PagePanel panel, String serviceName, short rows, short cols)
    {
        _active = false;
        _hasData = false;
        _panel = panel;
        _printStream = _panel._statusBar.printStream();
        _status = _panel._statusBar;
        _itemName = "";
        _serviceName = serviceName;
        _pageUpdateList = new Vector<PageUpdate>();
        _page = new Page(rows, cols);
    }

    public void processEvent(Event event)
    {
        _printStream.println(event);
        NormalizedEvent nevent = _appContext.getNormalizedEvent(event);
        int msgType = nevent.getMsgType();

        switch (event.getType())
        {
            case Event.OMM_ITEM_EVENT:
            {
                if (msgType == NormalizedEvent.REFRESH)
                {
                    _status.setStatus(_itemName + ": " + nevent.getStatusText());
                    byte[] data = nevent.getPayloadBytes();
                    parseData(data, false);
                    _panel.repaintCanvas();
                    _hasData = true;
                }
                else if (msgType == NormalizedEvent.UPDATE)
                {
                    byte[] data = nevent.getPayloadBytes();
                    parseData(data, true);
                }
                else if (msgType == NormalizedEvent.STATUS)
                {
                    _status.setStatus(_itemName + ": " + nevent.getStatusText());
                    _panel.repaintCanvas();
                    if (nevent.isClosed())
                    {
                        if (_page != null)
                        {
                            _page.reset(_pageUpdateList);
                            _panel.repaintCanvas();
                            _active = false;
                            _handle = null;
                            _hasData = false;
                        }
                    }
                    else if (nevent.isRename())
                    {
                        // msg.getState().getStreamState() ==
                        // OMMState.Stream.REDIRECT)
                        String attribInfoName = nevent.getNewItemName();
                        _status.setStatus(_itemName + ": renamed to: " + attribInfoName);
                        _itemName = attribInfoName;
                    }
                }
            }
                break;
            default:
                // ignore other events
        }
    }

    /**
     * Indicates whether the page is fully populated or not
     */
    public boolean hasData()
    {
        return _hasData;
    }

    public boolean active()
    {
        return _active;
    }

    /**
     * Unregister the current page
     */
    public void unsubscribe()
    {
        if (_handle != null)
        {
            _appContext.unregister(_handle);
            _handle = null;
            _active = false;
            _page.reset(_pageUpdateList);
        }
    }

    /**
     * Register page to RFA by using {@link SubAppContext}
     * 
     * @param name an instrument (Reuter Instrument Code or RIC)
     */
    public void subscribe(String name)
    {
        _itemName = name;
        _handle = _appContext.register(this, _serviceName, _itemName, true);
        _active = true;
    }

    // Decode the page data received and get the list of PageUpdates.
    // Apply each PageUpdate to the display panel.
    protected void parseData(byte[] data, boolean update)
    {
        Vector<PageUpdate> pageUpdateList = new Vector<PageUpdate>();
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        _page.decode(bais, pageUpdateList);
        Iterator<PageUpdate> iter = pageUpdateList.iterator();
        // pass the updated regions to the canvas.
        while (iter.hasNext())
        {
            PageUpdate u = (PageUpdate)iter.next();
            _panel.addUpdate(u, update);
        }
    }

    public short getCols()
    {
        return _page.getNumberOfColumns();
    }

    public short getRows()
    {
        return _page.getNumberOfRows();
    }

    public Page getPage()
    {
        return _page;
    }
}
