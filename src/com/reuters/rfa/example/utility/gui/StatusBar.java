package com.reuters.rfa.example.utility.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;

/**
 * A simple status bar class that allows status messages to be displayed
 * permanently or temporarily. For safety, the user should call the cleanUp()
 * method when the StatusBar is no longer in use.
 ** 
 **/

public class StatusBar extends Panel
{

    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;

    // Constructor
    /**
     * Creates a new StatusBar with no initial status information. The interval
     * used for fading defaults to 1 second.
     **/
    public StatusBar()
    {
        this("");
    }

    /**
     * Creates a new StatusBar with the given status information. The initial
     * status information will be fixed. The interval used for fading defaults
     * to 1 second.
     **/
    public StatusBar(String status)
    {
        this(status, true);
    }

    /**
     * Creates a new StatusBar with the given status information. The initial
     * status information will be fixed or fading depending upon the value of
     * initialStatusFixed. The interval used for fading defaults to 1 second.
     * The number of intervals before the initial fade defaults to 3.
     **/
    public StatusBar(String status, boolean initialStatusFixed)
    {
        _timer = new Timer(1000, new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                fade();
            }
        });
        _timer.setRepeats(true);
        _numIntervals = 0;

        setLayout(new FlowLayout(FlowLayout.LEFT));
        _statusText = new Label();
        add(_statusText);

        if (initialStatusFixed)
            setStatusFixed(status);
        else
            setStatusFade(status, 3);
    }

    // Attributes
    /**
     * Returns the preferred size of this component.
     **/
    public Dimension getPreferredSize()
    {
        Dimension d = new Dimension();
        // The width is arbitrary.
        //
        d.width = getFontMetrics(getFont()).charWidth('A') * 50;
        d.height = (getFontMetrics(getFont()).getMaxAscent() + getFontMetrics(getFont())
                .getMaxDescent()) * 2;
        return d;
    }

    // Operations
    /**
     * Sets the status to the given newStatus. The status will be fixed.
     **/
    public void setStatusFixed(String newStatus)
    {
        _isFixed = true;
        _statusText.setText(newStatus);
        _timer.stop();
    }

    /**
     * Sets the status to the given newStatus. The status will fade after the
     * one interval.
     **/
    public void setStatusFade(String newStatus)
    {
        setStatusFade(newStatus, 1);
    }

    /**
     * Sets the status to the given newStatus. The status will fade after the
     * given number of intervals.
     **/
    public void setStatusFade(String newStatus, int numIntervals)
    {
        _isFixed = false;
        _statusText.setText(newStatus);
        _numIntervals = numIntervals;
        _timer.restart();
    }

    /** Clears the status bar. **/
    public void clearStatus()
    {
        setStatusFixed("");
    }

    /**
     * This method should be called for safety when this StatusBar is no longer
     * in use.
     **/
    public void cleanUp()
    {
        _timer.stop();
    }

    public void fade()
    {
        if ((_numIntervals > 0) && !_isFixed)
        {
            _numIntervals--;
            if (_numIntervals == 0)
                clearStatus();
        }
    }

    public void paint(Graphics g)
    {
        Rectangle bounds = getBounds();
        _statusText.setSize(bounds.width - 8, bounds.height - 8);
        try
        {
            Color background = getBackground();
            g.setColor(background.darker().darker());
            g.drawRect(0, 0, bounds.width - 2, bounds.height - 2);
            g.setColor(background.brighter().brighter());
            g.drawRect(1, 1, bounds.width - 2, bounds.height - 2);
        }
        catch (NullPointerException npe)
        {
        }
    }

    public String getText()
    {
        return _statusText.getText();
    }

    // Data
    protected Timer _timer;
    protected boolean _isFixed;
    protected Label _statusText;
    protected int _numIntervals;
}
