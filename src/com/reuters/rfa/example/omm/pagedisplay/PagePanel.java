package com.reuters.rfa.example.omm.pagedisplay;

import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.JPanel;

import com.reuters.rfa.ansipage.PageUpdate;
import com.reuters.rfa.example.utility.gui.JLoggedStatusBar;

/**
 * This class creates all the display components, which include the
 * {@link PageCanvas}, the user input region and {@link JLoggedStatusBar a
 * status bar}.
 * 
 * The class is responsible for the following:
 * <ul>
 * <li>Create a PageCanvas, which is the display device
 * <li>Create a status bar to display other events
 * <li>Handle the input of RIC names from users
 * <li>Create {@link PageClient} and obtain page from PageClient
 * <li>When the page is fully populated, the PagePanel passes the page to the
 * PageCanvas for display.
 * <li>Passing updates to the page canvas
 * 
 */
public class PagePanel
{
    protected PageClient _page;
    protected JPanel _panel;
    protected PageCanvas _canvas;
    protected JLoggedStatusBar _statusBar;
    protected String _serviceName;

    public PagePanel(String serviceName, short rows, short cols, int fontSize)
    {
        _panel = new JPanel();
        _panel.setLayout(new BorderLayout());
        _panel.setBackground(Color.lightGray);
        _serviceName = serviceName;

        _canvas = new PageCanvas(rows, cols, fontSize);
        _statusBar = new JLoggedStatusBar();

        _page = new PageClient(this, serviceName, rows, cols);

        _panel.add("Center", _canvas);
        _panel.add("South", _statusBar.component());
    }

    public void cleanUp()
    {
        if (_canvas != null)
        {
            _canvas.cleanUp();
            _canvas = null;
        }
        if (_page != null)
        {
            _page.unsubscribe();
            _page = null;
        }
    }

    /**
     * register as a page client
     */
    public void newSymbolEntered(String sbl)
    {
        _statusBar.setStatus("Requesting " + sbl);

        if (_page != null)
            _page.unsubscribe();

        _canvas.clearDisplay();
        _canvas.setPage(_page);
        _page.subscribe(sbl);
    }

    public void repaintCanvas()
    {
        _canvas.repaint();
    }

    public void clearCanvas()
    {
        _canvas.clearDisplay();
    }

    public void addUpdate(PageUpdate u, boolean fade)
    {
        _canvas.addUpdate(u, fade);
    }

}
