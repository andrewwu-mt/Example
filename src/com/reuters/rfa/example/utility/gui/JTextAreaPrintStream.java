package com.reuters.rfa.example.utility.gui;

import java.io.PrintStream;
import javax.swing.JTextArea;

public class JTextAreaPrintStream extends PrintStream
{
    public JTextAreaPrintStream(JTextArea area)
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

    public void println(String string)
    {
        _textArea.append(string);
        println();
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

    public void println(Object obj)
    {
        _textArea.append(obj.toString());
        println();
    }

    JTextArea _textArea;
}
