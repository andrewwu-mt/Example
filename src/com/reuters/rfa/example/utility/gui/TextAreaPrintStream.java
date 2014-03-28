package com.reuters.rfa.example.utility.gui;

import java.awt.TextArea;
import java.io.PrintStream;

public class TextAreaPrintStream extends PrintStream
{
    /*
     * public TextAreaStream(JTextArea area) { super(System.out); _textArea =
     * area; }
     */
    public TextAreaPrintStream(TextArea area)
    {
        super(System.out);
        _textArea = area;
    }

    public void println()
    {
        _textArea.append("\n");
    }

    public void print(String string)
    {
        _textArea.append(string);
    }

    public void print(int s)
    {
        _textArea.append(Integer.toString(s));
    }

    public void print(boolean b)
    {
        _textArea.append(Boolean.toString(b));
    }

    public void print(char c)
    {
        _textArea.append(Character.toString(c));
    }

    public void print(char[] s)
    {
        _textArea.append(new String(s));
    }

    public void print(double d)
    {
        _textArea.append(Double.toString(d));
    }

    public void print(float f)
    {
        _textArea.append(Float.toString(f));
    }

    public void print(long l)
    {
        _textArea.append(Long.toString(l));
    }

    public void print(Object obj)
    {
        _textArea.append(obj.toString());
    }

    // JTextArea _textArea;
    TextArea _textArea;
}
