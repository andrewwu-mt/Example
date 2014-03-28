package com.reuters.rfa.example.omm.gui.quotelist;

import java.awt.Color;

import javax.swing.table.DefaultTableCellRenderer;

/**
 * <p>
 * This class is responsible for displaying the value of each item in a single
 * cell of a table. When an update is received, it keeps a two second fading
 * count for determining when a cell should be red and black.
 * </p>
 * 
 * <p>
 * It also caches the item state(stale/notStale) so it can mark the cell as
 * stale.
 * </p>
 * 
 * As a DefaultTableCellRenderer, this class overrides getText() and
 * getForeground to display the current RTField text and use the appropriate
 * color for state.
 **/

public class FieldRenderer extends DefaultTableCellRenderer
{
    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;

    public FieldRenderer()
    {
        super();
        _active = true;
    }

    /**
     * One second has passed, decrement the fade count as needed
     */
    public void fade()
    {
        if (_fadeCount > 0)
            _fadeCount--;
    }

    /**
     * @return STALE_COLOR, UPDATE_COLOR or DEFAULT_COLOR
     */
    public Color getForeground()
    {
        Color newColor;
        if (_active && _stale)
            newColor = STALE_COLOR;
        else if (_fadeCount > 0)
            newColor = UPDATE_COLOR;
        else
            newColor = DEFAULT_COLOR;
        return newColor;
    }

    /**
     * Returns the value of the field.
     */
    public String getText()
    {
        if (!_active)
            return "Inactive";

        if (_value == null)
        {
            if (_value == null)
                _value = "XX";
        }
        return _value;
    }

    /**
     * Resets the fade countdown to the maximum fade time and update value.
     */
    public void processFieldUpdate(String update)
    {
        _fadeCount = FADE_TIME;
        _value = update;
    }

    protected void cleanup()
    {
        _value = null;
    }

    protected void setActive(boolean b)
    {
        _active = b;
    }

    protected void setStale(boolean b)
    {
        _stale = b;
    }

    protected String _value;
    protected int _fadeCount;
    protected boolean _active;
    protected boolean _stale;

    protected static Color STALE_COLOR = Color.gray;
    protected static Color UPDATE_COLOR = Color.red;
    protected static Color DEFAULT_COLOR = Color.black;
    protected static int FADE_TIME = 2;

}
