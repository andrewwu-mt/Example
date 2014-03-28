package com.reuters.rfa.example.omm.pagedisplay;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.Timer;

import com.reuters.rfa.ansipage.CellColor;
import com.reuters.rfa.ansipage.CellStyle;
import com.reuters.rfa.ansipage.Page;
import com.reuters.rfa.ansipage.PageUpdate;

/**
 * This class controls how a {@link Page} is displayed
 * 
 */
public class PageCanvas extends Canvas
{
    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;
    protected int _numRows;
    protected int _numColumns;
    protected int _rowHeight;
    protected int _columnWidth;
    protected int _lineAscent;
    protected int _descent;
    protected PageClient _page;
    Font _boldFont;
    Font _normalFont;
    boolean _blink = true;
    Vector<FadeItem> _fadeVector;
    Timer _fadeTimer;
    static char[] cs = new char[1];

    public PageCanvas(int h, int w, int fontSize)
    {
        _normalFont = new Font("Monospaced", Font.PLAIN, fontSize);
        _boldFont = new Font("Monospaced", Font.BOLD, fontSize);
        setFont(_normalFont);
        _numRows = h;
        _numColumns = w;
        setBackground(Color.black);
        _fadeVector = new Vector<FadeItem>(20);
        _fadeTimer = new Timer(250, new FadeClient());
        _fadeTimer.start(); // will repeat, by default
    }

    public void addNotify()
    {
        super.addNotify();
        measure();
    }

    /**
     * Apply PageUpdate to the page display
     */
    public void addUpdate(PageUpdate u, boolean fade)
    {
        paintUpdate(u, fade);
        synchronized (this)
        {
            int i = 0;

            // check if the update is already in the fade list
            for (; i < _fadeVector.size(); i++)
            {
                FadeItem fi = (FadeItem)_fadeVector.elementAt(i);
                if (u.equals(fi.update()))
                {
                    fi.reset();
                    break;
                }
            }
            if (i == _fadeVector.size())
            {
                // add the update to the fade list
                _fadeVector.addElement(new FadeItem(u));
            }
        }
    }

    synchronized void cleanUp()
    {
        _page = null;
        _fadeTimer.stop();
        _fadeVector.removeAllElements();
    }

    public void clearDisplay()
    {
        _page = null;
        Graphics g = this.getGraphics();
        if (g == null)
            return;
        Dimension d = this.getSize();
        g.clearRect(0, 0, d.width, d.height);
    }

    public Dimension getMinimumSize()
    {
        return new Dimension(_numColumns * _columnWidth, _numRows * _rowHeight);
    }

    public Dimension getPreferredSize()
    {
        return new Dimension((_numColumns + 1) * _columnWidth, (_numRows + 1) * _rowHeight);
    }

    /**
     * Use the font to measure height and width
     */
    public void measure()
    {
        FontMetrics fm = this.getFontMetrics(this.getFont());
        // If we don't have font metrics yet, just return.
        if (fm == null)
            return;
        _columnWidth = fm.charWidth(' ') + 1;
        _rowHeight = fm.getHeight();
        _lineAscent = fm.getAscent();
        _descent = fm.getDescent();
    }

    /**
     * When the page is fully populated, use information in the graphics object
     * to paint each character in the page.
     */
    public void paint(Graphics g)
    {
        if ((_page == null) || !_page.hasData())
            return;

        // Display a range of cells with equals attributes at a time.
        for (short row = 1; row < _page.getRows(); row++)
        {
            for (short col = 1; col < _page.getCols(); col++)
            {
                paintCell(g, row, col, false);
            }
        }
    }

    /**
     * The entire region is assumed to same the same set of attributes
     */
    public void paintCell(Graphics g, short row, short col, boolean fade)
    {
        Color bg, fg;

        Page page = _page.getPage();
        cs[0] = page.getChar(row, col);

        if (page.hasStyle(row, col, CellStyle.reverse)
                || (_blink && page.hasStyle(row, col, CellStyle.blink)))
        {
            if (fade)
            {
                CellColor cc = page.getForegroundFadeColor(row, col);
                bg = (cc == CellColor.mono) ? Color.GREEN : cc.getColor();
                cc = page.getBackgroundFadeColor(row, col);
                fg = (cc == CellColor.mono) ? Color.BLACK : cc.getColor();
            }
            else
            {
                CellColor cc = page.getForegroundColor(row, col);
                bg = (cc == CellColor.mono) ? Color.GREEN : cc.getColor();
                cc = page.getBackgroundColor(row, col);
                fg = (cc == CellColor.mono) ? Color.BLACK : cc.getColor();
            }
        }
        else
        {
            if (fade)
            {
                CellColor cc = page.getForegroundFadeColor(row, col);
                fg = (cc == CellColor.mono) ? Color.BLACK : cc.getColor();
                cc = page.getBackgroundFadeColor(row, col);
                bg = (cc == CellColor.mono) ? Color.GREEN : cc.getColor();
            }
            else
            {
                CellColor cc = page.getForegroundColor(row, col);
                fg = (cc == CellColor.mono) ? Color.GREEN : cc.getColor();
                cc = page.getBackgroundColor(row, col);
                bg = (cc == CellColor.mono) ? Color.BLACK : cc.getColor();
            }
        }

        g.setColor(bg);

        int y = (row - 1) * _rowHeight;
        int x = (col - 1) * _columnWidth;
        // int w = _columnWidth;
        g.fillRect(x, y, _columnWidth, _rowHeight);

        if ((_boldFont != null)
                && (page.hasStyle(row, col, CellStyle.bright) || page.hasStyle(row, col,
                                                                               CellStyle.dim)))
            g.setFont(_boldFont);
        else
            g.setFont(_normalFont);

        g.setColor(fg);
        g.drawChars(cs, 0, 1, x + 1, y + _lineAscent);
    }

    /**
     * Analogous to paint(Graphics), except this is processed on a PageUpdate
     */
    public void paintUpdate(PageUpdate u, boolean fade)
    {
        if ((_page == null))
            return;

        short row = u.getRow();
        for (short col = u.getBeginningColumn(); col < u.getEndingColumn(); col++)
        {
            paintCell(getGraphics(), row, col, fade);
        }
    }

    public void setFont(Font f)
    {
        super.setFont(f);
        measure();
        repaint();
    }

    public void setPage(PageClient page)
    {
        _page = page;
        _fadeVector.removeAllElements();
    }

    /**
     * Manages the fading of a {@linkplain PageUpdate} the initial count of 8,
     * combined with the Thread sleep of 250 ms, means the regions will be faded
     * after 2 seconds.
     */
    class FadeItem
    {
        FadeItem(PageUpdate u)
        {
            _u = u;
            _count = 8;
        }

        void reset()
        {
            _count = 8;
        }

        void paint()
        {
            _count--;
            if (_count <= 0)
                paintUpdate(_u, false);
            // else
            // paintUpdate(_u, true);
        }

        PageUpdate update()
        {
            return _u;
        }

        boolean done()
        {
            return _count == 0;
        }

        int _count = 0;
        PageUpdate _u;
    }

    class FadeClient implements ActionListener
    {
        public void actionPerformed(ActionEvent event)
        {
            Vector<FadeItem> removes = new Vector<FadeItem>();
            for (int i = 0; i < _fadeVector.size(); i++)
            {
                FadeItem fi = (FadeItem)_fadeVector.elementAt(i);
                fi.paint();
                if (fi.done())
                    removes.addElement(fi);
            }
            for (int i = 0; i < removes.size(); i++)
            {
                FadeItem fi = (FadeItem)removes.elementAt(i);
                _fadeVector.removeElement(fi);
                paintUpdate(fi.update(), false);
            }
            if (_blink)
                _blink = false;
            else
                _blink = true;
        }
    }
}
