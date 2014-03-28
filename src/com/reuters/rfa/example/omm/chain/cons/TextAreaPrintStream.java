package com.reuters.rfa.example.omm.chain.cons;

import java.io.PrintStream;
import javax.swing.JTextArea;
import com.reuters.rfa.omm.OMMData;

/**
 * TextAreaPrintStream is a utility class that provides functionality
 * to print representations of various data values in a specified JTextArea
 * 
 */
public class TextAreaPrintStream extends PrintStream
{
    /*
     * public TextAreaStream(JTextArea area) { super(System.out); _textArea =
     * area; }
     */
    public TextAreaPrintStream(JTextArea textArea)
    {
        super(System.out);
        _textArea = textArea;
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
        _textArea.append(string + "\n");
    }

    public void print(int s)
    {
        _textArea.append(Integer.toString(s));
    }

    public void println(int s)
    {
        _textArea.append(Integer.toString(s) + "\n");
    }

    public void print(boolean b)
    {
        _textArea.append(Boolean.toString(b));
    }

    public void println(boolean b)
    {
        _textArea.append(Boolean.toString(b) + "\n");
    }

    public void print(char c)
    {
        _textArea.append(Character.toString(c));
    }

    public void println(char c)
    {
        _textArea.append(Character.toString(c) + "\n");
    }

    public void print(char[] s)
    {
        _textArea.append(new String(s));
    }

    public void println(char[] s)
    {
        _textArea.append(new String(s) + "\n");
    }

    public void print(double d)
    {
        _textArea.append(Double.toString(d));
    }

    public void println(double d)
    {
        _textArea.append(Double.toString(d) + "\n");
    }

    public void print(float f)
    {
        _textArea.append(Float.toString(f));
    }

    public void println(float f)
    {
        _textArea.append(Float.toString(f) + "\n");
    }

    public void print(long l)
    {
        _textArea.append(Long.toString(l));
    }

    public void println(long l)
    {
        _textArea.append(Long.toString(l) + "\n");
    }

    public void print(Object obj)
    {
        _textArea.append(obj.toString());
    }

    public void println(Object obj)
    {
        _textArea.append(obj.toString() + "\n");
    }

    public void print(OMMData data)
    {
        OMMData _data = data;
        _textArea.append(_data.toString());
    }

    public void println(OMMData data)
    {
        OMMData _data = data;
        _textArea.append(_data.toString() + "\n");
    }

    JTextArea _textArea;
}
