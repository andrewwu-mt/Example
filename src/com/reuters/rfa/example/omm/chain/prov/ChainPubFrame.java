package com.reuters.rfa.example.omm.chain.prov;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.Dispatchable;
import com.reuters.rfa.common.DispatchableNotificationClient;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.config.ConfigUtil;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMErrorIntSpec;
import com.reuters.rfa.session.omm.OMMListenerIntSpec;
import com.reuters.rfa.session.omm.OMMProvider;

/**
 * ChainPubFrame is an instance of javax.swing.JFrame
 * that contains a 'Suffix Record' javax.swing.JTextField that requires the user to give
 * a based RIC for generating the chain header RIC.
 * After clicking 'Init', ChainPubFrame reads each underlying RIC from file
 * and verifies its character length whether it is conformed to
 * user selected template (14 characters for LINK_A and 21 characters for LONGLINK template) or not.
 * If there is an invalid one, the user must fix the file content, re-specify the file path
 * and click 'Init' again. 
 * 
 * After verification is complete without defect,
 * ChainPubFrame will acquire a Session,
 * create an EventSource.OMM_PROVIDER and an EventQueue,
 * and create a ProviderClients, which implements com.reuters.rfa.common.Client.
 * Then it generates the chain header RICs and prepares fid-value for each chain header
 * in MARKET_PRICE model.
 * 
 */
public class ChainPubFrame extends WindowAdapter implements ActionListener
{
    private OMMPool _pool;
    private Session _session;
    private String _sessionName;
    private String _serviceName;
    private String _suffixRecord = "ABC.O";
    private OMMProvider _provider;
    private SessionClient _sessionNotificationClient;
    private Handle _csListenerIntSpecHandle, _errIntSpecHandle;
    private FieldDictionary _rwfDictionary;
    private Object _closure = new Object();
    private EventQueue _eventQueue;
    private ProviderClients _myClient;

    // === Variables for GUI ===
    private JFrame frame;
    private JPanel panel;
    private JTextField filePath, service, suffix;
    private JRadioButton linkRadio, longRadio;
    private ButtonGroup btGrp;
    private JButton fileBrowse, initButton;
    private JFileChooser fileChooser;
    protected TextArea textArea;
    private File _ricFile;
    protected static final Font arial = new Font("Arial", Font.PLAIN, 12);
    protected static final Font courier = new Font("Courier New", Font.PLAIN, 12);
    // === Variables for GUI ===

    protected static String _template;
    protected ArrayList<String> _ricList;
    protected static final int LINK_A_SIZE = 14;
    protected static final int LONGLINK_SIZE = 21;

    protected boolean _shutdown = false;

    /**
     * ChainPubFrame constructor
     */
    public ChainPubFrame(String session, OMMPool pool, String serviceName, FieldDictionary rwfDict,
            File ricFile)
    {
        super();
        _sessionName = session;
        _pool = pool;
        _serviceName = serviceName;
        _rwfDictionary = rwfDict;
        _ricFile = ricFile;
        createGUI(); // Create main window
    }

    /**
     * Creates GUI by using javax.swing.
     */
    private void createGUI()
    {
        frame = new JFrame("StarterProviderInteractive_Chain");
        frame.addWindowListener(this);

        panel = new JPanel();
        panel.setFont(new Font("Arial", Font.BOLD, 13));
        panel.setPreferredSize(new Dimension(640, 450));
        panel.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        JLabel svcLabel = new JLabel("Service Name : ");
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.EAST;
        panel.add(svcLabel, c);

        service = new JTextField();
        service.setText(_serviceName);
        service.setEditable(false);
        c.gridx = 1;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        panel.add(service, c);

        JLabel suffixLabel = new JLabel("Suffix Record : ");
        c.gridx = 2;
        c.gridy = 0;
        c.anchor = GridBagConstraints.EAST;
        panel.add(suffixLabel, c);

        suffix = new JTextField(10);
        suffix.setText(_suffixRecord);
        c.gridx = 3;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        panel.add(suffix, c);

        JLabel tempLabel = new JLabel("Template (MARKET_PRICE only) : ");
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.insets = new Insets(0, 5, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        panel.add(tempLabel, c);

        linkRadio = new JRadioButton("LINK_A", true);
        longRadio = new JRadioButton("LONGLINK", false);
        btGrp = new ButtonGroup();
        btGrp.add(linkRadio);
        btGrp.add(longRadio);
        linkRadio.setFont(arial);
        longRadio.setFont(arial);

        c.gridx = 1;
        c.gridy = 1;
        c.anchor = GridBagConstraints.WEST;
        panel.add(linkRadio, c);

        c.gridx = 2;
        c.gridy = 1;
        c.gridwidth = 1;
        c.insets = new Insets(0, 10, 0, 0);
        panel.add(longRadio, c);

        JLabel listLabel = new JLabel("Full path to underlying RICs list file : ");
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.EAST;
        panel.add(listLabel, c);

        filePath = new JTextField(27);
        filePath.setCaretPosition(0);
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        panel.add(filePath, c);

        fileChooser = new JFileChooser();
        fileBrowse = new JButton("Browse");
        fileBrowse.addActionListener(this);
        c.gridx = 3;
        c.gridy = 2;
        c.gridwidth = 1;
        c.insets = new Insets(0, 5, 0, 0);
        c.anchor = GridBagConstraints.WEST;
        panel.add(fileBrowse, c);

        initButton = new JButton("Init");
        initButton.setEnabled(true);
        initButton.addActionListener(this);
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 4;
        c.insets = new Insets(5, 0, 5, 0);
        c.anchor = GridBagConstraints.CENTER;
        panel.add(initButton, c);

        textArea = new TextArea(null, 20, 85, TextArea.SCROLLBARS_VERTICAL_ONLY);
        textArea.setEditable(false);
        textArea.setVisible(true);
        textArea.setBackground(Color.WHITE);
        textArea.setFont(courier);

        c.gridy = 4;
        panel.add(textArea, c);
        panel.setVisible(true);
        frame.add(panel);
        frame.setVisible(true);
        frame.pack();
    }

    protected OMMPool getPool()
    {
        return _pool;
    }

    protected OMMProvider getProvider()
    {
        return _provider;
    }

    protected EventQueue getEventQueue()
    {
        return _eventQueue;
    }

    protected String getServiceName()
    {
        return _serviceName;
    }

    protected String getSuffixRecord()
    {
        return _suffixRecord;
    }

    protected FieldDictionary getFieldDict()
    {
        return _rwfDictionary;
    }

    /**
     * Initialize RFA.
     */
    private void init()
    {
        // prior to acquiring the session, update the provider connection
        // to use the request message type (OMMMsg.MsgType.REQUEST) rather 
        // than the deprecated request message types (see OMMMsg.MsgType).
        ConfigUtil.useDeprecatedRequestMsgs(_sessionName, false);
        
        // Create a Session
        _session = Session.acquire(_sessionName);
        if (_session == null)
        {
            System.out.println("Could not acquire session.");
            System.exit(1);
        }
        
        // Create an OMMProvider event source
        _provider = (OMMProvider)_session.createEventSource(EventSource.OMM_PROVIDER,
                                                            "OMMChainProvider Server");
        _eventQueue = EventQueue.create("OMMChainProvider Server EventQueue");

        _myClient = new ProviderClients(this);

        OMMListenerIntSpec listenerIntSpec = new OMMListenerIntSpec();
        _csListenerIntSpecHandle = _provider.registerClient(_eventQueue, listenerIntSpec,
                                                            _myClient, null);

        OMMErrorIntSpec errIntSpec = new OMMErrorIntSpec();
        _errIntSpecHandle = _provider.registerClient(_eventQueue, errIntSpec, _myClient, null);

        _sessionNotificationClient = new SessionClient();
        _eventQueue.registerNotificationClient(_sessionNotificationClient, _closure);

        textArea.append("Initializing ... \n");
    }

    class SessionClient implements DispatchableNotificationClient
    {
        // Implementations of DispatchableNotificationClient::notify
        public void notify(Dispatchable dispSource, Object closure)
        {

            java.awt.EventQueue.invokeLater(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        do
                        {
                            _eventQueue.dispatch(1000);
                        }
                        while (_eventQueue.dispatch(0) > 0);
                    }
                    catch (DispatchException de)
                    {
                        System.out.println("Queue deactivated");
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                }
            });
        }
    }

    /*
     * Action performed for GUI(non-Javadoc)
     * 
     * @see
     * java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == fileBrowse)
        {
            // Open browse file dialog
            int returnVal = fileChooser.showOpenDialog(null);
            if (returnVal == JFileChooser.APPROVE_OPTION)
            {
                _ricFile = fileChooser.getSelectedFile();
                filePath.setText(_ricFile.getAbsolutePath());
            }
            else if (returnVal == JFileChooser.ERROR_OPTION)
            {
                textArea.append("Cannot open file \n");
            }

        }
        else if (e.getSource() == initButton)
        {
            // String path = filePath.getText().trim();
            String svc = service.getText().trim();

            // if ( path.equals("") )
            // textArea.append("All fields are mandatory.\n");

            boolean verified = false;
            if (linkRadio.isSelected())
            {
                verified = verifyRicList(LINK_A_SIZE); // 14-character length
                _template = "LINK_A";
            }
            else
            {
                verified = verifyRicList(LONGLINK_SIZE); // 21-character length
                _template = "LONGLINK";
            }

            if (suffix.getText().trim().equals(""))
            {
                textArea.append("Please specify the suffix record.\n");
                return;
            }
            else
                _suffixRecord = suffix.getText().trim();

            if (verified)
            {
                // If all underlying RICs pass a verification,
                // then initialize RFA and Clients.
                init();
                _myClient.initChain(_ricList, _template);
                textArea.append("Ready for client\n");
            }
        }
    }

    /**
     * Verify whether the length of each chain member contained in file is
     * compatible with designated template.
     */
    private boolean verifyRicList(int length)
    {
        textArea.append("Verifying rics ...\n");
        _ricList = new ArrayList<String>();
        BufferedReader in;
        try
        {
            in = new BufferedReader(new FileReader(_ricFile));
            String str;
            while ((str = in.readLine()) != null)
            {
                // Length of each underlying RIC must be
                // less than or equal 14 for LINK_A and 21 for LONGLINK
                if (str.length() <= length)
                {
                    if (str.indexOf('.') == -1)
                        str = str + ".NaE";
                    _ricList.add(str);
                }
                else
                {
                    textArea.append("RIC <"
                            + str
                            + "> is longer than template standard. Please fix it and re-specify the file path\n");
                    _ricList.clear();
                    return false;
                }
            }
            in.close();
        }
        catch (Exception e)
        {
            textArea.append("Cannot open file. Please specify full path to underlying RICs list file \n");
            return false;
        }

        return true;
    }

    /**
     * Cleanup RFA components before closing a window. *
     */
    public void windowClosing(WindowEvent e)
    {
        textArea.append("Cleaning up resources....\n");
        if (_csListenerIntSpecHandle != null)
            _provider.unregisterClient(_csListenerIntSpecHandle);
        if (_errIntSpecHandle != null)
            _provider.unregisterClient(_errIntSpecHandle);
        if (_provider != null)
            _provider.destroy();
        if (_eventQueue != null)
            _eventQueue.deactivate();
        if (_session != null)
            _session.release();
        textArea.append("Exiting now ...\n");
        
        _shutdown = true;
        System.exit(0);
    }
}