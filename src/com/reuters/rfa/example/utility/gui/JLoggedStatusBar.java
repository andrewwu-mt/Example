package com.reuters.rfa.example.utility.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintStream;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

public class JLoggedStatusBar implements ActionListener, Status
{
    // Constructor
    public JLoggedStatusBar()
    {
        initGUI();
        initLog();
    }

    // Event Processing -- from ActionListener
    public void actionPerformed(ActionEvent e)
    {
        if (e.getActionCommand().equals("ShowLog"))
        {
            _statusLog.setVisible(true);
        }
    }

    // Clean Up
    public void cleanUp()
    {
        _statusBar.cleanUp();
        _statusLog.setVisible(false);
        _statusLog = null;
    }

    // Access
    public JComponent component()
    {
        return _statusPanel;
    }

    public PrintStream printStream()
    {
        return _printStream;
    }

    // Operations
    /**
     * Display the given text on a status bar.
     **/
    public void setStatus(String text)
    {
        _log.append(text);
        _log.append("\n");
        _statusBar.setStatusFixed(text);
    }

    public String getCurrentStatus()
    {
        return _statusBar.getText();
    }

    protected void initGUI()
    {
        _statusBar = new StatusBar("", true);
        _statusBar.setFont(UIManager.getFont("Label.font"));
        _statusBar.setBackground(UIManager.getColor("Panel.background"));
        JButton statusButton = new JButton("Log");
        statusButton.setToolTipText("Show all status messages");
        statusButton.setActionCommand("ShowLog");
        statusButton.addActionListener(this);
        _statusPanel = new JPanel();
        _statusPanel.setLayout(new BorderLayout());
        _statusPanel.add("Center", _statusBar);
        _statusPanel.add("East", statusButton);
    }

    protected void initLog()
    {
        _statusLog = new JFrame("Status Log");
        _log = new JTextArea(5, 10);
        JScrollPane sp = new JScrollPane(_log);
        _statusLog.getContentPane().add(sp);
        _statusLog.setSize(new Dimension(400, 200));
        _printStream = new JTextAreaPrintStream(_log);

    }

    protected StatusBar _statusBar;
    protected JComboBox _comboBox;
    protected JPanel _statusPanel;
    protected JFrame _statusLog;
    protected JTextArea _log;
    protected JTextAreaPrintStream _printStream;
}
