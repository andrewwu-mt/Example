package com.reuters.rfa.example.utility.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Enumeration;
import java.util.Vector;

import javax.swing.JTextField;

/**
 * An extension of the java.awt.TextField component that provides caching
 * capabilities of entered strings. On-the-fly cache retrieval is also provided.
 ** 
 **/

public class HistoryTextField extends JTextField implements ActionListener, KeyListener
{

    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;

    // Constructors
    /**
     * Creates a new HistoryTextField with a default cache size of 15.
     **/
    public HistoryTextField()
    {
        super();
        _cacheSize = 15;
        _cache = new Vector<String>(_cacheSize);
        this.addActionListener(this);
        this.addKeyListener(this);
    }

    /**
     * @param cacheSize int - The maximum number of distinct strings to cache.
     **/
    public HistoryTextField(int cacheSize)
    {
        super();
        _cacheSize = cacheSize;
        _cache = new Vector<String>(_cacheSize);
        this.addActionListener(this);
        this.addKeyListener(this);
    }

    /**
     * @param columns int - The column width of the text field.
     ** @param cacheSize int - The maximum number of distinct strings to cache.
     **/
    public HistoryTextField(int columns, int cacheSize)
    {
        super(columns);
        _cacheSize = cacheSize;
        _cache = new Vector<String>(_cacheSize);
        this.addActionListener(this);
        this.addKeyListener(this);
    }

    /**
     * @param text String - The initial text.
     ** @param cacheSize int - The maximum number of distinct strings to cache.
     **/
    public HistoryTextField(String text, int cacheSize)
    {
        super(text);
        _cacheSize = cacheSize;
        _cache = new Vector<String>(_cacheSize);
        _cache.addElement(text);
        selectAll();
        this.addActionListener(this);
        this.addKeyListener(this);
    }

    /**
     * @param text String - The initial text.
     ** @param columns int - The column width of the text field.
     ** @param cacheSize int - The maximum number of distinct strings to cache.
     **/
    public HistoryTextField(String text, int columns, int cacheSize)
    {
        super(text, columns);
        _cacheSize = cacheSize;
        _cache = new Vector<String>(_cacheSize);
        _cache.addElement(text);
        selectAll();
        this.addActionListener(this);
        this.addKeyListener(this);
    }

    // Attributes
    /**
     * @return int The size of the cache.
     **/
    public int getCacheSize()
    {
        return _cacheSize;
    }

    public int getCount()
    {
        return _cache.size();
    }

    // Query
    /**
     * Does the cache contain the given string?
     ** 
     * @return boolean
     ** @param s java.lang.String
     **/
    public boolean hasString(String s)
    {
        for (Enumeration<String> e = _cache.elements(); e.hasMoreElements();)
        {
            String string = e.nextElement();
            if (s.equals(string))
                return true;
        }
        return false;
    }

    // Operations
    /**
     * Sets the cache size.
     ** 
     * @param newCacheSize int
     **/
    public synchronized void setCacheSize(int newCacheSize)
    {
        _cacheSize = newCacheSize;
    }

    /**
     * @param text java.lang.String
     **/
    public void addString(String text)
    {
        setText(text);
        if (!hasString(text))
        {
            if (_cache.size() == _cacheSize)
                _cache.removeElementAt(_cacheSize - 1);
            _cache.insertElementAt(text, 0);
        }
        else
            moveString(text, 0);
    }

    protected void moveString(String s, int position)
    {
        String string = null;
        int index = 0; // Optimization to avoid Vector traversal
        for (Enumeration<String> e = _cache.elements(); e.hasMoreElements();)
        {
            String ne = e.nextElement();
            if (ne.equals(s))
            {
                string = ne;
                _cache.removeElementAt(index);
                break;
            }
            index++;
        }
        if (string != null)
            _cache.insertElementAt(string, position);
    }

    // Utilities
    protected String findFirstMatch(String s)
    {
        for (Enumeration<String> e = _cache.elements(); e.hasMoreElements();)
        {
            String ne = e.nextElement();
            if (ne.startsWith(s))
                return ne;
        }
        return null;
    }

    // Event Processing -- from ActionListener
    public void actionPerformed(ActionEvent e)
    {
        addString(getText());
        selectAll();
    }

    // Event Processing -- from KeyListener
    public void keyPressed(KeyEvent e)
    {
    }

    public void keyReleased(KeyEvent e)
    {
        String currText = getText();
        char key = e.getKeyChar();
        if (!(key == (char)KeyEvent.VK_ENTER) && !(key == (char)KeyEvent.VK_UNDEFINED)
                && !(key == (char)KeyEvent.VK_BACK_SPACE) && !(key == (char)KeyEvent.VK_DELETE)
                && !currText.equals(""))
        {
            String firstMatch = findFirstMatch(currText);
            if (firstMatch != null)
            {
                setText(firstMatch);
                select(_oldLength + 1, firstMatch.length());
            }
        }
    }

    public void keyTyped(KeyEvent e)
    {
        _oldLength = getSelectionStart();
    }

    // Data
    protected Vector<String> _cache;
    protected int _cacheSize;
    protected int _oldLength;
}
