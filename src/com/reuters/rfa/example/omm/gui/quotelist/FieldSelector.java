package com.reuters.rfa.example.omm.gui.quotelist;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListModel;

/**
 * <p>
 * This class provides a component which lets the user select fields to use for
 * column headings in the table. It was written to function independently from
 * the events and the rest of the quote list example program.
 * 
 * The {@link #component()} method returns a JPanel which could be added to any
 * swing application.
 * </p>
 * 
 * <p>
 * The panel contains five widgets. A list displays all known field names and
 * lets the user select from them. This list is initialized with
 * initDefaultListModel(). The top text field and its complimentary "Add Field"
 * button let the user and individual field names to the list.
 */
public class FieldSelector
{
    protected JPanel _panel;
    protected JList _fieldList;
    protected JTextField _fieldNameField;

    public void addToList(String[] fieldNames)
    {
        DefaultListModel model = getListModel();
        for (int i = 0; i < fieldNames.length; i++)
        {
            if (!model.contains(fieldNames[i]))
                getListModel().addElement(fieldNames[i]);
        }
    }

    /*
     * TODO not needed? public void addToList(Vector fieldNames) { for
     * (Enumeration e = fieldNames.elements(); e.hasMoreElements();) {
     * getListModel().addElement(e.nextElement()); } }
     */
    public String[] allFields()
    {
        return (String[])getListModel().toArray();
    }

    /**
     * @return a JPanel which could be added to any swing application.
     * 
     */
    public JPanel component()
    {
        return _panel;
    }

    public DefaultListModel getListModel()
    {
        return (DefaultListModel)_fieldList.getModel();
    }

    public void initDefaultListModel()
    {
        DefaultListModel model = getListModel();

        model.addElement("DSPLY_NAME");
        model.addElement("TRDPRC_1");
        model.addElement("HIGH_1");
        model.addElement("LOW_1");
        model.addElement("DIVPAYDATE");
        model.addElement("RDN_EXCHID");
        model.addElement("NETCHNG_1");
        model.addElement("PCTCHNG");
        model.addElement("TRDTIM_1");
        model.addElement("OPEN_PRC");
        model.addElement("BID");
        model.addElement("ASK");
        model.addElement("ACVOL_1");
    }

    protected void initGUI()
    {
        class AddFieldListener implements ActionListener
        {
            public void actionPerformed(ActionEvent e)
            {
                String fieldName = _fieldNameField.getText();
                if (fieldName.equals(""))
                    return;
                getListModel().addElement(_fieldNameField.getText());
                int index = getListModel().getSize() - 1;
                _fieldList.addSelectionInterval(index, index);
            }
        }

        _fieldNameField = new JTextField(8);
        _fieldNameField.setActionCommand("AddField");
        _fieldNameField.addActionListener(new AddFieldListener());
        JLabel addFieldNameLabel = new JLabel("Add field to List: ");
        JPanel fieldPanel = new JPanel();
        fieldPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        fieldPanel.add(addFieldNameLabel);
        fieldPanel.add(_fieldNameField);

        _applyButton = new JButton("Apply");
        _applyButton.setActionCommand("GetFields");
        fieldPanel.add(_applyButton);

        _panel = new JPanel();
        _panel.setLayout(new BorderLayout());
        _panel.add(fieldPanel, BorderLayout.CENTER);

        JScrollPane sp = new JScrollPane(_fieldList);
        _panel.add(sp, BorderLayout.NORTH);

    }

    public void initList()
    {
        ListModel model = new DefaultListModel();
        _fieldList = new JList(model);
        _fieldList.setVisibleRowCount(10);
        initDefaultListModel();

        // We don't want the JList implementation to compute the width
        // or height of all of the list cells, so we give it a String
        // that's as big as we'll need for any cell. It uses this to
        // compute values for the fixedCellWidth and fixedCellHeight
        // properties.
        _fieldList.setPrototypeCellValue("DISPLAY_NAME 12");

        // display symbol for the row's request
        getListModel().addElement("_SYMB_");
    }

    public String[] selectedFields()
    {
        Object[] selection = _fieldList.getSelectedValues();
        String[] fieldNames = new String[selection.length];
        for (int i = 0; i < selection.length; i++)
        {
            fieldNames[i] = (String)selection[i];
        }
        return fieldNames;
    }

    public void selectFields(String[] fieldNames)
    {
        addToList(fieldNames);
        int[] indices = new int[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++)
        {
            indices[i] = getListModel().indexOf(fieldNames[i]);
        }
        _fieldList.setSelectedIndices(indices);
        _applyButton.doClick();
    }

    protected JButton _applyButton;

    public FieldSelector()
    {
        initList();
        initGUI();
    }

    public void addFieldListener(ActionListener listener)
    {
        _applyButton.addActionListener(listener);
    }

    public void removeFieldListener(ActionListener listener)
    {
        _applyButton.removeActionListener(listener);
    }
}
