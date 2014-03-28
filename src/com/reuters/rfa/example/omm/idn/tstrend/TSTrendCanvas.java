package com.reuters.rfa.example.omm.idn.tstrend;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.reuters.ts1.TS1Constants;
import com.reuters.ts1.TS1Def;
import com.reuters.ts1.TS1Field;
import com.reuters.ts1.TS1Fields;
import com.reuters.ts1.TS1Point;

/**
 * A canvas used to draw time series graphs using TSLineMaker.
 ** 
 ** @see TSLineMaker
 **/

public class TSTrendCanvas extends Canvas
{

    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;

    // Constructor
    public TSTrendCanvas()
    {
        setBackground(Color.white);
        _numLine = 0;
    }

    // Operations
    /**
     * Updates the canvas with date from the given time series.
     **/
    public void update(TS1Fields series)
    {
        FontMetrics fm = getFontMetrics(getFont());
        Font font = getFont();
        Rectangle bounds = getBounds();

        // The line number must be reset so that we use the same
        // colors every time we update this canvas.
        //
        _numLine = 0;

        // A reference to the time series must be retained so that the
        // trend can be repainted as needed.
        //
        _timeSeries = series;

        Graphics g = getGraphics();
        if (g == null)
            return;

        clearDisplay();

        g.setColor(Color.red);
        g.setFont(new Font(font.getName(), Font.BOLD, font.getSize()));
        g.drawString(series.getName(), (getBounds().width - fm.stringWidth(series.getName())) / 2,
                     fm.getHeight());
        g.setFont(font);

        // g.drawString("Info: " + series.text(), 0, 20);

        int factCount = series.getFactCount();
        TS1Field vy = null;
        double maxf = 0;
        double minf = Double.MAX_VALUE;

        double lmaxf = 0;
        double lminf = 0;

        for (int i = 0; i < factCount; i++)
        {
            if (series.getDataDef(i).getDisplayStyle() == TS1Def.DisplayStyle.LINE)
            {
                /*
                 * rfaj1317 : call toDouble() on a valid TS1Point;
                 */
                TS1Point tsp1 = series.getFields(i).getMaxValue();
                if (tsp1 != null)
                    lmaxf = tsp1.toDouble();

                /*
                 * rfaj1317 : call toDouble() on a valid TS1Point;
                 */
                tsp1 = series.getFields(i).getMinValue();
                if (tsp1 != null)
                    lminf = tsp1.toDouble();

                maxf = maxf > lmaxf ? maxf : lmaxf;
                minf = minf < lminf ? minf : lminf;
            }
        }

        minf = minf * .99;
        maxf = maxf * 1.01;

        _maker = new TSLineMaker(g, minf, maxf);
        _maker.setBaseSize(0, bounds.height, bounds.width, bounds.height);

        // Get date vector.
        //
        Calendar[] dates = series.getDates();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
        sdf.setTimeZone(TimeZone.getDefault());

        // Draw the time period for this time series.
        //
        Date sd = dates[0].getTime();
        String sdString = sdf.format(sd);
        Date ed = dates[dates.length - 1].getTime();
        String edString = sdf.format(ed);

        String interval = TS1Constants.periodString(series.getPeriod());
        String range = edString + " - " + sdString + " (" + interval + ")";

        g.drawString(range, bounds.width - fm.stringWidth(range) - 10, bounds.height - 10);

        // Get field vectors.
        //
        for (int i = 0; i < factCount; i++)
        {
            vy = series.getFields(i);
            if (series.getDataDef(i).getDisplayStyle() == TS1Def.DisplayStyle.LINE)
            {
                Color lineColor = getNextColor();
                g.setColor(lineColor);
                String fid = series.getDataDef(i).getName();

                lmaxf = 0;
                lminf = 0;

                /*
                 * rfaj1317 : call toDouble() on a valid TS1Point;
                 */
                TS1Point tsp1 = series.getFields(i).getMaxValue();
                if (tsp1 != null)
                    lmaxf = tsp1.toDouble();
                String max = "Max=" + lmaxf;

                /*
                 * rfaj1317 : call toDouble() on a valid TS1Point;
                 */
                tsp1 = series.getFields(i).getMinValue();
                if (tsp1 != null)
                    lminf = tsp1.toDouble();
                String min = "Min=" + lminf;

                String label = fid + " " + min + " " + max;
                g.drawString(label, 0, (i * fm.getHeight()) + 10);

                // Draw line for this field, using points in the vectors
                // as Y coordinates (Dates as the X coordinates)
                //
                _maker.drawLine(series.getDates(), vy);
            }
        }
    }

    // Utilities
    /**
     * Returns the next color to use.
     **/
    public Color getNextColor()
    {
        Color c = null;
        int colorNumber = _numLine % 13;
        switch (colorNumber)
        {
            case 0:
                c = Color.black;
                break;
            case 1:
                c = Color.blue;
                break;
            case 2:
                c = Color.red;
                break;
            case 3:
                c = Color.green;
                break;
            case 4:
                c = Color.orange;
                break;
            case 5:
                c = Color.yellow;
                break;
            case 6:
                c = Color.cyan;
                break;
            case 7:
                c = Color.magenta;
                break;
            case 8:
                c = Color.pink;
                break;
            case 9:
                c = Color.darkGray;
                break;
            case 10:
                c = Color.gray;
                break;
            case 11:
                c = Color.lightGray;
                break;
        }
        _numLine++;
        return c;
    }

    // GUI Maintenance
    /**
     * Paints the display.
     **/
    public void paint(Graphics g)
    {
        g.setColor(Color.red);
        FontMetrics fm = getFontMetrics(getFont());
        g.drawString("Y-Price", 10, fm.getHeight() + 5);
        g.drawString("X-Date", getBounds().width - 10 - fm.stringWidth("X-Date"),
                     getBounds().height - 10);
        // The series must be redrawn every time this operation
        // is performed, otherwise, the series will be erased.
        //
        if (_timeSeries != null)
            update(_timeSeries);
    }

    /**
     * Clears the display.
     **/
    public void clearDisplay()
    {
        Graphics g = getGraphics();
        if (g == null)
            return;
        Dimension d = getSize();
        g.clearRect(0, 0, d.width, d.height);
        // _timeSeries = null;
    }

    // Data
    protected TS1Fields _timeSeries;
    protected TSLineMaker _maker;
    protected int _numLine;

    /**
     * Clears the display.
     **/
    public void clearTimeSeries()
    {
        _timeSeries = null;
    }
}
