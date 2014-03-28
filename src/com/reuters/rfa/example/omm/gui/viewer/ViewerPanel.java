package com.reuters.rfa.example.omm.gui.viewer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.reuters.rfa.example.framework.sub.LoginInfo;
import com.reuters.rfa.example.framework.sub.OMMSubAppContext;
import com.reuters.rfa.example.framework.sub.ServiceInfo;
import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.framework.sub.SubAppContextClient;
import com.reuters.rfa.example.utility.gui.ServiceSelector;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.rdm.RDMMsgTypes;

/**
 * GUI builder and GUI event processor for the Viewer example. This class is
 * responsible for:
 * <ul>
 * <li>Constructs the main display container
 * <li>Constructs the ServiceSelector
 * <li>Constructs a message model type selector which monitors the
 * ServiceSelector through the <code>MMTActionListener</code> inner class.
 * <li>Constructing buttons panel to interact with items such as Reissue
 * Streaming, Reissue Priority, Pause, Resume and Resume with refresh. The
 * buttons will be disabled/enabled for corresponding with the item status.
 * <li>Constructing JTabbedPane that contain JTables for displaying data of an
 * item.
 * <li>Constructing the JTables for displaying field lists, maps of field lists,
 * or maps of field lists with summary data. If Java 6 or later is used, the
 * tables will be sortable. Note that sorting large tables, such as those used
 * for symbol lists, can be very time consuming.
 * <li>Creating the {@link ViewerItemPanel} and passing it the requested
 * parameters.
 * </ul>
 * 
 */
public class ViewerPanel implements ActionListener, SubAppContextClient
{
    protected SubAppContext _appContext;
    protected ServiceSelector _serviceSelector;
    protected JTextField _ricField;
    protected JCheckBox _snapshot;
    protected JTextField _pclassField;
    protected JTextField _pcountField;
    protected Map<JComponent, ViewerItemPanel> _itemList;

    protected JPanel _panel;
    protected JPanel _buttonPanel;
    protected JButton _closeButton;
    protected JButton _reissueButton;
    protected JCheckBox _pauseCheckBox;
    protected JCheckBox _refreshCheckBox;
    protected JTextArea _loginInfo;
    protected JTabbedPane _tabPane;
    protected JComboBox _mmtSelector;

    protected StringBuilder _tempLogingInfo;
    protected boolean _supportPauseResume;

    public ViewerPanel(SubAppContext appContext)
    {
        _appContext = appContext;
        _itemList = new HashMap<JComponent, ViewerItemPanel>();
        _tempLogingInfo = new StringBuilder();
        _serviceSelector = new ServiceSelector(appContext, null);

        _supportPauseResume = false;

        initGUI();
        enableDisplay(true);
    }

    public Component component()
    {
        return _panel;
    }

    public void disable()
    {
        cleanUp();
    }

    public void cleanUp()
    {

    }

    protected void initGUI()
    {
        // create panel for table tab panel
        _panel = new JPanel(new BorderLayout());
        _tabPane = new JTabbedPane();

        JPanel loginPanel = initLoginInfo();
        _tabPane.add("Login", loginPanel);

        _tabPane.addChangeListener(new ChangeListener()
        {
            public void stateChanged(ChangeEvent e)
            {
                enableButtons();

                if (_tabPane.getSelectedIndex() == 0) // login
                {
                    _loginInfo.setText(_tempLogingInfo.toString());
                }
            }
        });

        JPanel ricPanel = initRicPanel();

        _panel.setLayout(new BorderLayout());
        _panel.add("North", ricPanel);
        _panel.add("Center", _tabPane);

        _loginInfo.setText(_tempLogingInfo.toString());
    }

    protected JPanel initLoginInfo()
    {
        JPanel loginPanel = new JPanel(new BorderLayout());
        _loginInfo = new JTextArea();
        _loginInfo.setEditable(false);
        JScrollPane sp = new JScrollPane(_loginInfo);
        loginPanel.add("Center", sp);
        return loginPanel;
    }

    private JPanel initRicPanel()
    {
        JPanel ricPanel = new JPanel();
        ricPanel.setLayout(new BorderLayout());
        ricPanel.setPreferredSize(new Dimension(200, 70));
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        topPanel.add(_serviceSelector.component());
        _serviceSelector.component().addItemListener(new MMTActionListener());
        _serviceSelector.component().setEditable(false);

        _mmtSelector = new JComboBox();
        _mmtSelector.setEditable(false);
        topPanel.add(_mmtSelector);

        _pclassField = new JTextField(3);
        _pclassField.setText("1");
        _pclassField.setToolTipText("Priority Class");
        _pclassField.setActionCommand("Change Priority");
        _pclassField.addActionListener(this);
        topPanel.add(new JLabel("Class"));
        topPanel.add(_pclassField);

        _pcountField = new JTextField(3);
        _pcountField.setText("1");
        _pcountField.setToolTipText("Priority Count");
        _pcountField.setActionCommand("Change Priority");
        _pcountField.addActionListener(this);
        topPanel.add(new JLabel("Count"));
        topPanel.add(_pcountField);

        _snapshot = new JCheckBox("Snapshot");
        topPanel.add(_snapshot);

        _ricField = new JTextField(8);
        _ricField.setText("TRI.N");
        _ricField.setToolTipText("RIC to open");
        _ricField.setActionCommand("Open");
        _ricField.addActionListener(this);
        topPanel.add(new JLabel("RIC"));
        topPanel.add(_ricField);

        JButton open = new JButton("Open");
        open.setActionCommand("Open");
        open.addActionListener(this);
        topPanel.add(open);

        _buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        _refreshCheckBox = new JCheckBox("Refresh");
        _buttonPanel.add(_refreshCheckBox);
        
        _pauseCheckBox = new JCheckBox("Pause");
        _buttonPanel.add(_pauseCheckBox);
        
        _reissueButton = new JButton("Reissue");
        _reissueButton.setActionCommand("Reissue");
        _reissueButton.addActionListener(this);
        _buttonPanel.add(_reissueButton);

        JSeparator aSep = new JSeparator(SwingConstants.VERTICAL);
        aSep.setPreferredSize(new Dimension(1, 20));
        _buttonPanel.add(aSep);

        _closeButton = new JButton("Close");
        _closeButton.setActionCommand("Close");
        _closeButton.addActionListener(this);
        _buttonPanel.add(_closeButton);

        ricPanel.add(BorderLayout.NORTH, topPanel);
        ricPanel.add(BorderLayout.SOUTH, _buttonPanel);
        enableButtons();

        return ricPanel;
    }

    public void processComplete()
    {
        _tempLogingInfo = updateLoginInfo();
        if (_loginInfo != null)
        {
            _loginInfo.setText(_tempLogingInfo.toString());
        }
    }

    protected void enableDisplay(boolean enable)
    {
        if (_ricField != null)
            _ricField.setEnabled(enable);
    }

    protected void enableButtons()
    {

        if ((_tabPane.getSelectedIndex() == 0) || (_itemList.size() <= 0))
        {
            enableAllButtons(true);
            _closeButton.setEnabled(false);
            if (!_supportPauseResume)
            {
                enablePauseCapability(false);
            }
            return;
        }

        ViewerItemPanel itemPanel = (ViewerItemPanel)_itemList.get(_tabPane.getSelectedComponent());
        if (!itemPanel._tableClient._isStreaming)
        {
            enableAllButtons(false);
            _closeButton.setEnabled(true);
        }
        else
        {
            enableAllButtons(true);
        }
        if (!_supportPauseResume)
        {
            enablePauseCapability(false);
        }

    }

    protected void enableAllButtons(boolean enable)
    {
        Component[] comp = _buttonPanel.getComponents();
        for (int i = 0; i < comp.length; i++)
        {
            comp[i].setEnabled(enable);
        }
    }

    protected void enablePauseCapability(boolean enable)
    {
        _pauseCheckBox.setEnabled(enable);
    }

    protected StringBuilder updateLoginInfo()
    {
        LoginInfo info = ((OMMSubAppContext)_appContext).getLoginInfo();
        StringBuilder buf = new StringBuilder();
        buf.append("UserName: " + info.getUserName() + "\n");
        buf.append("Position: " + info.getPosition() + "\n");
        buf.append("AppId: " + info.getAppId() + "\n");
        buf.append("SupportPauseResume: " + info.getSupportPauseResume() + "\n");
        buf.append("SingleOpen: " + info.getSingleOpen() + "\n");
        buf.append("AllowSuspect: " + info.getAllowSuspect() + "\n");

        if (info.getSupportPauseResume() > 0)
        {
            _supportPauseResume = true;
        }

        return buf;
    }

    protected byte getPriorityClass()
    {
        try
        {
            int n = Integer.parseInt(_pclassField.getText());
            if ((n > 255) || (n < 1))
                return 1;
            return (byte)n;
        }
        catch (NumberFormatException ne)
        {
            return 1;
        }
    }

    protected int getPriorityCount()
    {
        try
        {
            int n = Integer.parseInt(_pcountField.getText());
            if ((n > 65535) || (n < 1))
                return 1;
            return n;
        }
        catch (NumberFormatException ne)
        {
            return 1;
        }
    }

    protected void updateTabTitle(boolean isPaused)
    {
        Component[] coms = _tabPane.getComponents();
        for (int i = 1; i < coms.length; i++)
        {
            ViewerItemPanel itemPanel = (ViewerItemPanel)_itemList.get(coms[i]);
            if (itemPanel._tableClient._isStreaming)
            {
                _tabPane.setTitleAt(i, itemPanel._tableClient._name + (isPaused ? "(Paused)" : ""));
            }
        }
    }

    protected void reissueLogin(int indicationFlags)
    {
        ((OMMSubAppContext)_appContext).reissueLogin(indicationFlags);
        updateTabTitle((indicationFlags & OMMMsg.Indication.PAUSE_REQ) != 0);
    }

    public void actionPerformed(ActionEvent e)
    {
        if (e.getActionCommand().equals("Open"))
        {
            String ric = _ricField.getText();
            if (ric.equals(""))
                return;
            String servicename = _serviceSelector.service();
            if (servicename == null)
            {
                JOptionPane.showMessageDialog(null, "Service is not available");
                return;
            }
            short mmt = ((MMT)_mmtSelector.getSelectedItem())._mmt;

            // Create client
            ViewerItemPanel itemPanel = new ViewerItemPanel(this);
            itemPanel.open(mmt, servicename, ric, !_snapshot.isSelected());
            _itemList.put(itemPanel.component(), itemPanel);
            _tabPane.addTab(ric + (_snapshot.isSelected() ? "(Snapshot)" : ""),
                            itemPanel.component());
            _tabPane.setSelectedComponent(itemPanel.component());

        }
        else if (_tabPane.getSelectedIndex() == 0) // login tab
        {
            if (e.getActionCommand().equals("Reissue"))
            {
                int indicationFlags = 0;
                if (_pauseCheckBox.isEnabled()
                        && _pauseCheckBox.isSelected())
                    indicationFlags |= OMMMsg.Indication.PAUSE_REQ;
                
                if (_refreshCheckBox.isEnabled()
                        && _refreshCheckBox.isSelected())
                    indicationFlags |= OMMMsg.Indication.REFRESH;

                reissueLogin(indicationFlags);
            }
        }
        else
        // item tab
        {
            if (e.getActionCommand().equals("Reissue"))
            {
                ViewerItemPanel itemPanel = (ViewerItemPanel)_itemList.get(_tabPane
                        .getSelectedComponent());
                
                int indicationFlags = 0;
                if (_pauseCheckBox.isEnabled()
                        && _pauseCheckBox.isSelected())
                {
                    indicationFlags |= OMMMsg.Indication.PAUSE_REQ;
                    _tabPane.setTitleAt(_tabPane.getSelectedIndex(), 
                                        itemPanel._tableClient._name + "(Paused)");
                }
                else
                {
                    _tabPane.setTitleAt(_tabPane.getSelectedIndex(), itemPanel._tableClient._name);
;
                }
                
                if (_refreshCheckBox.isEnabled()
                        && _refreshCheckBox.isSelected())
                    indicationFlags |= OMMMsg.Indication.REFRESH;
                
                itemPanel._tableClient.reissue(indicationFlags, getPriorityClass(), getPriorityCount());
            }
            else if (e.getActionCommand().equals("Close"))
            {
                ViewerItemPanel itemPanel = (ViewerItemPanel)_itemList.remove(_tabPane
                        .getSelectedComponent());
                if (itemPanel == null)
                    return;
                _tabPane.remove(_tabPane.getSelectedComponent());
                if (itemPanel._tableClient._isStreaming)
                {
                    itemPanel._tableClient.close();
                }
            }
        }

        enableButtons();
    }

    class MMTActionListener implements ItemListener
    {

        public void itemStateChanged(ItemEvent e)
        {
            JComboBox cb = (JComboBox)e.getSource();
            String serviceName = (String)cb.getSelectedItem();
            ServiceInfo serviceInfo = ((OMMSubAppContext)_appContext).getServiceInfo(serviceName);
            if (serviceInfo == null)
            {
                System.err.println(serviceName + " not in directory yet");
                return;
            }

            _mmtSelector.removeAllItems();
            String[] mmtStrs = (String[])serviceInfo.get("Capabilities");
            for (int i = 0; i < mmtStrs.length; i++)
            {
                short mmt = Short.parseShort(mmtStrs[i]);
                if (mmt > RDMMsgTypes.DICTIONARY)
                    _mmtSelector.addItem(new MMT(Short.parseShort(mmtStrs[i])));

                // else initialization domains are not supported
                // other domains may or may not be supported (depending on if
                // the
                // payload data type is field list or map of field list), so
                // they are added just in case
            }
        }
    }

    class MMT
    {
        MMT(short mmt)
        {
            _mmtString = RDMMsgTypes.toString(mmt);
            _mmt = mmt;
        }

        public String toString()
        {
            return _mmtString;
        }

        String _mmtString;
        short _mmt;
    }

}
