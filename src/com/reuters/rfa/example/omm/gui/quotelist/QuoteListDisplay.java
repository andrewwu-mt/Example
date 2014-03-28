package com.reuters.rfa.example.omm.gui.quotelist;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.utility.gui.JLoggedStatusBar;
import com.reuters.rfa.example.utility.gui.ServiceSelector;
import com.reuters.rfa.example.utility.gui.Status;

/**
 * This class is responsible for Building the GUI display.
 */
public class QuoteListDisplay implements ActionListener
{
    public QuoteListDisplay(SubAppContext appContext, Status status, FieldSelector fieldSelector)
    {
        _fieldSelector = fieldSelector;
        _serviceSelector = new ServiceSelector(appContext, status);
        _recordTableModel = new RecordTableModel(appContext, status);
        _recordTableModel.setSelectable(true);

        initGUI(status);
    }

    public void setService(String servicename)
    {
        _serviceSelector.component().setSelectedItem(servicename);
    }

    // Event processing -- from ActionListener
    public void actionPerformed(ActionEvent e)
    {
        if (e.getActionCommand().equals("AddToList"))
        {
            String ric = _ricField.getText().trim();
            if (ric.equals(""))
                return;
            addRecord(ric);
        }
        else if (e.getActionCommand().equals("Remove"))
        {
            _recordTableModel.removeSelected();
        }
        else if (e.getActionCommand().equals("GetFields"))
        {
            String[] fieldNames = _fieldSelector.selectedFields();
            _recordTableModel.setFieldNames(fieldNames);
            _dialog.setVisible(false);
        }
        else if (e.getActionCommand().equals("SetFields"))
        {
            _dialog.setVisible(true);
        }
    }

    // Access
    public Component component()
    {
        return _panel;
    }

    public void disable()
    {
        _fieldSelector.removeFieldListener(this);
        _recordTableModel.disable();
        cleanUp();
    }

    protected void initGUI(Status status)
    {
        // create top panel for this tab panel
        JPanel ricPanel = new JPanel();
        ricPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        ricPanel.add(_serviceSelector.component(), FlowLayout.LEFT);

        JLabel label = new JLabel("Enter Symbol: ");
        ricPanel.add(label);

        _ricField = new JTextField(8);

        _ricField.setToolTipText("RIC to add to quote list");
        _ricField.setActionCommand("AddToList");
        _ricField.addActionListener(this);
        _ricField.setEnabled(false); // wait for dictionary
        ricPanel.add(_ricField);

        _removeRecordButton = new JButton("Remove Selected");

        _removeRecordButton.setToolTipText("Remove selected records");
        _removeRecordButton.setActionCommand("Remove");
        _removeRecordButton.addActionListener(this);
        ricPanel.add(_removeRecordButton);

        _fieldButton = new JButton("Fields");

        _fieldButton.setToolTipText("Set Fields");
        _fieldButton.setActionCommand("SetFields");
        _fieldButton.addActionListener(this);
        ricPanel.add(_fieldButton);

        // create panel for table tab panel
        _panel = new JPanel();
        _panel.setLayout(new BorderLayout());
        _panel.add("North", ricPanel);
        _panel.add("Center", _recordTableModel.component());
        _panel.add("South", ((JLoggedStatusBar)status).component());

        _dialog = new JDialog();
        _dialog.setTitle("Set Fields");
        _dialog.getContentPane().add(_fieldSelector.component());
        _dialog.pack();
    }

    protected void cleanUp()
    {
        _recordTableModel.cleanUp();
        _serviceSelector.cleanUp();
        _ricField.setText("");
    }

    // Operations
    protected void addRecord(String ric)
    {
        String[] fieldNames = _fieldSelector.selectedFields();
        if (fieldNames.length == 0)
        {
            JOptionPane.showMessageDialog(null, "Must select a field");
            return;
        }
        String servicename = _serviceSelector.service();
        if (servicename == null)
        {
            JOptionPane.showMessageDialog(null, "Service is not available");
            return;
        }
        _recordTableModel.setFieldNames(fieldNames);
        _recordTableModel.add(servicename, ric);
        _ricField.selectAll();
    }

    protected void enableDisplay(boolean enable)
    {
        if (_ricField != null)
            _ricField.setEnabled(true);

        if (_removeRecordButton != null)
            _removeRecordButton.setEnabled(enable);

        if (_fieldButton != null)
            _fieldButton.setEnabled(enable);

        _fieldSelector.addFieldListener(this);
        _recordTableModel.enable();
    }

    protected ServiceSelector _serviceSelector;
    protected RecordTableModel _recordTableModel;
    protected JTextField _ricField;
    protected JPanel _panel;
    protected FieldSelector _fieldSelector;
    protected JButton _removeRecordButton;
    protected JButton _fieldButton;
    protected JDialog _dialog;
}
