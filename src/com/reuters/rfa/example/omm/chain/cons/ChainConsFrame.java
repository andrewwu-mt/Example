package com.reuters.rfa.example.omm.chain.cons;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DispatchException;
import com.reuters.rfa.common.Dispatchable;
import com.reuters.rfa.common.DispatchableNotificationClient;
import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.common.EventSource;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.session.Session;
import com.reuters.rfa.session.omm.OMMConsumer;

/**
 * ChainConsFrame is an instance of javax.swing.JFrame
 * that contains a javax.swing.JTextField that requires the user to specify
 * a Chain name, an Item name and a Service name.
 * Note that all fields are mandatory. 
 * 
 * When it finishes creating the GUI, then it will acquire a Session,
 * create an EventSource.OMM_CONSUMER and an EventQueue,
 * and create a LoginClient and an ItemManager, which implements com.reuters.rfa.common.Client.
 * 
 */
public class ChainConsFrame extends JFrame implements ActionListener
{
    // RFA objects
    protected Session _session;
    protected EventQueue _eventQueue;
    protected OMMConsumer _ommConsumer;
    protected LoginClient _loginClient;
    protected ItemManager _itemManager;

    protected OMMEncoder _encoder;
    protected OMMPool _pool;

    public JTextArea _output;
    private JTextField _symbolField, _serviceNameField;
    private JLabel _mmtLabel = new JLabel("Message Model", SwingConstants.CENTER);
    private JLabel _chaineLabel = new JLabel("Chain Name", SwingConstants.CENTER);
    private JLabel _symbolLabel = new JLabel("Item Name", SwingConstants.CENTER);
    private JLabel _serviceNameLabel = new JLabel("Service Name", SwingConstants.CENTER);
    private JButton _subscribeButton, _initializeButton, _terminateButton, _terminateAllButton;
    private JComboBox modelType;
    private JButton _itemsubscribeButton;
    private JPanel _upperPanel, _lowerPanel;
    private JScrollPane _spane;
    private SessionClient _sessionNotificationClient;

    static int _frameCount = 0; // Keep track of number of frames created
    private boolean _login;
    public boolean _chainFrame; // Check if the frame is for chain subscription
                                // or for item subscription
    public String mmt = "MARKET_PRICE";
    private StarterConsumer_Chain _mainApp;

    private static final long serialVersionUID = 1L;
    
    /**
     * ChainConsFrame constructor
     */
    public ChainConsFrame(boolean chainFrame, StarterConsumer_Chain mainApp)
    {
        _mainApp = mainApp;
        _chainFrame = chainFrame;

        Container c = getContentPane();
        c.setLayout(new BorderLayout());
        addUpperPanel();
        addLowerPanel();
        c.add(_upperPanel, BorderLayout.CENTER);
        c.add(_lowerPanel, BorderLayout.SOUTH);
        _frameCount++;
    }

    public void addUpperPanel()
    {
        _upperPanel = new JPanel(new BorderLayout());
        _output = new JTextArea();
        _output.setColumns(100);
        _output.setLineWrap(true);
        _spane = new JScrollPane(_output);
        _upperPanel.add(_spane, BorderLayout.CENTER);
    }

    public void addLowerPanel()
    {
        JPanel panela = new JPanel(new GridLayout(1, 3));

        // A Frame for subscribing chain will have lower panel for begin new
        // item subscription.
        if (_chainFrame)
        {
            _lowerPanel = new JPanel(new GridLayout(3, 1));

            panela.add(_mmtLabel);
            // 2 message models for chain in OMM interface
            String[] mmodel = { "MARKET_PRICE", "SYMBOL_LIST" };
            modelType = new JComboBox(mmodel);

            panela.add(modelType);
            panela.add(_chaineLabel);

            modelType.addActionListener(this);
        }
        else
        {
            _lowerPanel = new JPanel(new GridLayout(2, 1));
            panela.add(_symbolLabel);
        }

        _symbolField = new JTextField("");
        panela.add(_symbolField);

        panela.add(_serviceNameLabel);
        _serviceNameField = new JTextField("");
        panela.add(_serviceNameField);

        _lowerPanel.add(panela);

        JPanel panelb = new JPanel(new GridLayout(1, 3));

        _initializeButton = new JButton("Init");
        _initializeButton.addActionListener(this);

        if (_chainFrame)
        {
            _subscribeButton = new JButton("Chain Subscription");
        }
        else
        {
            _subscribeButton = new JButton("Subscribe");
        }
        _subscribeButton.setEnabled(false);
        _subscribeButton.addActionListener(this);

        if (!_chainFrame)
        {
            _terminateButton = new JButton("Terminate");
            _terminateButton.setEnabled(false);
            _terminateButton.addActionListener(this);
        }

        panelb.add(_initializeButton);
        panelb.add(_subscribeButton);

        if (!_chainFrame)
        {
            panelb.add(_terminateButton);
        }

        _lowerPanel.add(panelb);

        if (_chainFrame)
        {

            JPanel panelc = new JPanel(new GridLayout(1, 1));

            _itemsubscribeButton = new JButton("Item Subscription");
            _itemsubscribeButton.setEnabled(false);
            _itemsubscribeButton.addActionListener(this);

            panelc.add(_itemsubscribeButton);

            _lowerPanel.add(panelc);

        }
    }

    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == _initializeButton)
        {
            _output.append("Initializing OMM Chain Consumer\n");
            init();
            _subscribeButton.setEnabled(true);
            _initializeButton.setEnabled(false);
        }
        else if (e.getSource() == _subscribeButton)
        {

            if ((_symbolField.getText().length() > 0) && (_serviceNameField.getText().length() > 0))
            {
                // Check if login successful.
                if (_login)
                {
                    _output.append("OMMConsumerChainFrame" + " Login successful..." + "\n");
                    String itemNames = _symbolField.getText();
                    String serviceName = _serviceNameField.getText();
                    _itemManager.sendRequest(itemNames, serviceName);
                    if (_chainFrame)
                    {
                        _itemsubscribeButton.setEnabled(true);
                    }
                    else
                    {
                        _terminateButton.setEnabled(true);
                    }
                }
                else
                {
                    _output.append("OMMConsumerChainFrame" + ": Login has been denied / rejected / closed.");
                    _output.append("OMMConsumerChainFrame" + ": Preparing to clean up and exiting...");
                    System.out.println("OMMConsumerChainFrame" + ": Login has been denied / rejected / closed.");
                    System.out.println("OMMConsumerChainFrame" + ": Preparing to clean up and exiting...");
                    cleanup(1);
                }
            }
            else
            {
                _output.append("Unacceptable symbol");
            }
        }
        else if (e.getSource() == _terminateButton)
        {
            _output.append("Terminating\n");
            _subscribeButton.setEnabled(false);
            _terminateButton.setEnabled(false);
            _initializeButton.setEnabled(true);
            cleanup(0);
        }
        else if (e.getSource() == _terminateAllButton)
        {
            System.exit(0);
        }
        else if (e.getSource() == _itemsubscribeButton)
        {
            _mainApp.init(false);
        }
        else if (e.getSource() == modelType)
        {
            // Select model type.
            if (modelType.getSelectedItem() == "MARKET_PRICE")
            {
                mmt = "MARKET_PRICE";
            }
            else if (modelType.getSelectedItem() == "SYMBOL_LIST")
            {
                mmt = "SYMBOL_LIST";
            }
        }
    }

    public void init()
    {
        // Create a Session
        String sessionName = CommandLine.variable("session");
        _session = Session.acquire(sessionName);
        if (_session == null)
        {
            _output.append("Could not acquire session.");
            Context.uninitialize();
            System.exit(1);
        }

        // Create an Event Queue
        _eventQueue = EventQueue.create("myEventQueue");

        // Create a OMMPool.
        _pool = OMMPool.create();

        // Create an OMMEncoder
        _encoder = _pool.acquireEncoder();
        _encoder.initialize(OMMTypes.MSG, 5000);

        // Initialize client for login domain.
        _loginClient = new LoginClient(this);

        // Initialize item manager for item domains
        _itemManager = new ItemManager(this);

        // Create an OMMConsumer event source
        _ommConsumer = (OMMConsumer)_session.createEventSource(EventSource.OMM_CONSUMER,
                                                               "myOMMConsumer", true);

        // Application may choose to down-load the enumtype.def and
        // RWFFldDictionary
        // This example program loads the dictionaries from file only.
        String fieldDictionaryFilename = CommandLine.variable("rdmFieldDictionary");
        String enumDictionaryFilename = CommandLine.variable("enumType");
        try
        {
            GenericOMMParserI.initializeDictionary(fieldDictionaryFilename, enumDictionaryFilename);
        }
        catch (DictionaryException ex)
        {
            System.out.println("ERROR: Unable to initialize dictionaries.");
            System.out.println(ex.getMessage());
            if (ex.getCause() != null)
                System.err.println(": " + ex.getCause().getMessage());
            cleanup(-1);
            return;
        }
        _sessionNotificationClient = new SessionClient();
        _eventQueue.registerNotificationClient(_sessionNotificationClient, null);

        // Send login request
        // Application must send login request first
        _loginClient.sendRequest();
    }

    public void cleanup(int val)
    {
        System.out.println(Context.string());

        // unregister all items
        if (_itemManager != null)
            _itemManager.closeAllRequest();

        // unregister login
        if (_loginClient != null)
            _loginClient.closeRequest();
        // destroy EventSource
        if (_ommConsumer != null)
        {
            _ommConsumer.destroy();
        }

        _eventQueue.deactivate();
        _session.release();
        Context.uninitialize();

        System.out.println(getClass().toString() + " exiting.");
        if (val != 0)
        {
            System.exit(val);
        }
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
                            _eventQueue.dispatch(0);
                        }
                        while (_eventQueue.dispatch(0) > 0);
                    }
                    catch (DispatchException de)
                    {
                        System.out.println("Queue deactivated");
                    }
                    catch (Exception dae)
                    {
                        dae.printStackTrace();
                    }
                }
            });
        }
    }

    protected OMMPool getPool()
    {
        return _pool;
    }

    protected OMMConsumer getOMMConsumer()
    {
        return _ommConsumer;
    }

    protected EventQueue getEventQueue()
    {
        return _eventQueue;
    }

    public OMMEncoder getEncoder()
    {
        return _encoder;
    }

    public void processLogin(boolean success)
    {
        _login = success;
    }
}
