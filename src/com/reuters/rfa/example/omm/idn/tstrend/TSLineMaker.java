package com.reuters.rfa.example.omm.idn.tstrend;

import java.awt.Graphics;
import java.util.Calendar;

import com.reuters.ts1.TS1Point;
import com.reuters.ts1.TS1Computation;
import com.reuters.ts1.TS1Field;

/**
 * A TSLineMaker accepts a TimeSeriesDateVector and a TimeSeriesFieldVector and
 * uses their values as X and Y coordinates of a sequence of time values. The
 * line is made by connecting the adjacent points. The X and Y coordinates are
 * normalized to fit the size set by setBaseSize().
 ** 
 ** @version 1.0 "1998.09.11"
 **/

public class TSLineMaker
{

    // Operations
    public void setBaseSize(int x, int y, int w, int h)
    {
        _baseX = x;
        _baseY = y;
        _baseWidth = w;
        _baseHeight = h;
    }

    public void drawLine(Calendar[] x, TS1Field y)
    {
        int cx = 0, cy = 0, nx, ny;
        boolean start = true;

        long maxX = x[0].getTime().getTime();
        long minX = x[x.length - 1].getTime().getTime();

        double xFactor = (double)(maxX - minX) / _baseWidth;

        double range = _max - _min;
        double scale = _baseHeight / range;

        TS1Field yscaled = TS1Computation.subtract(y, _min);
        yscaled = TS1Computation.multiply(yscaled, scale);
        yscaled = TS1Computation.multiply(yscaled, -1);
        yscaled = TS1Computation.add(yscaled, _baseHeight);

        for (int i = 0; i < yscaled.getCount(); i++)
        {
            TS1Point value = yscaled.getValues()[i];
            if (value.isValid())
            {
                if (start)
                {
                    cx = _baseX + (int)((x[i].getTime().getTime() - minX) / xFactor);
                    cy = (int)value.toDouble();
                    start = false;
                }
                else
                {
                    nx = _baseX + (int)((x[i].getTime().getTime() - minX) / xFactor);
                    ny = (int)value.toDouble();
                    _gc.drawLine(cx, cy, nx, ny);
                    cx = nx;
                    cy = ny;
                }
            }
            else
            {
                start = true;
            }
        }
        /*
         * double maxY = (_max <= 0) ? y.maxValue() : _max; double minY = (_max
         * <= 0) ? y.minValue() : _min;
         * 
         * 
         * float xFactor = (float) (_baseWidth/(maxX - minX)); float yFactor =
         * (float) (_baseHeight/(maxY - minY));
         * 
         * Enumeration ex = x.dateValues(); Enumeration ey = y.fieldValues();
         * 
         * while(ex.hasMoreElements() && ey.hasMoreElements()) { try { if (start
         * == true) { cx =_baseX + (int)((((Long)ex.nextElement()).longValue() -
         * minX)*xFactor); cy =_baseY - (int)((((TimeSeriesFieldValue)ey.
         * nextElement()).value()-minY)*yFactor); start = false; } else { long
         * ix = ((Long)ex.nextElement()).longValue(); nx = _baseX + (int)((ix -
         * minX)*xFactor); float iy =
         * ((TimeSeriesFieldValue)ey.nextElement()).value(); ny =_baseY -
         * (int)((iy-minY)*yFactor); _gc.drawLine(cx,cy,nx,ny); cx = nx; cy =
         * ny; } } catch (IllegalAccessException e) { //
         * System.out.println("Skip one point: " + e); } }
         */
    }

    // Data
    protected int _baseX;
    protected int _baseY;
    protected int _baseWidth;
    protected int _baseHeight;
    protected Graphics _gc;
    protected double _max;
    protected double _min;

    // Constructor
    public TSLineMaker(Graphics g, double min, double max)
    {
        _gc = g;
        _min = min;
        _max = max;
    }

    // Constructor
    public TSLineMaker(Graphics g, int max, int min)
    {
        _gc = g;
        _min = min;
        _max = max;
    }
}
