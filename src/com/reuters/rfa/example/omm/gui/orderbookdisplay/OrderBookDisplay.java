package com.reuters.rfa.example.omm.gui.orderbookdisplay;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.TableModelEvent;

/**
 * <p>
 * This is a main class to run the OrderBookDisplay GUI application.
 * </p>
 * 
 * <p>
 * The purpose of this demo application is to visually demonstrate the MarketByOrder feature.
 * This GUI demo application is a multi-threaded application consuming real-time,
 * level 2 market data, using the RFA API. It displays all the market orders for
 * a given item. Additionally, it displays the summary data, and enables the user
 * to sort the BID or ASK prices.
 * </p>
 * 
 * <p>
 * Copyright: Copyright (c) 2011
 * </p>
 * 
 */
public class OrderBookDisplay extends JFrame implements Callback
{

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public OrderBookDisplay()
    {
        try
        {
            this.setSize(750, 400);
            this.setTitle("OrderBook Display");

            initialize();

            clk = new Clock(this);
            clkThread = new Thread(clk);
            clkThread.start();

            this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            // add a listener that will close the window
            addWindowListener(new WindowAdapter()
            {
                public void windowClosing(WindowEvent evt)
                {
                    OrderBookDisplay.this.cleanup();
                }
            }); // end addWindowListener statement

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public void start()
    {
        // initGUI from AWT thread to avoid deadlocks
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                try
                {
                    initialize();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void main(String[] args)
    {
        OrderBookDisplay mainwindow = new OrderBookDisplay();
        mainwindow.setVisible(true);
    }

    private void initialize() throws Exception
    {
        this.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        // this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setResizable(false);
        this.setContentPane(ContentPane);
        ContentPane.setMaximumSize(new Dimension(1000, 500));
        ContentPane.setMinimumSize(new Dimension(750, 400));
        ContentPane.setPreferredSize(new Dimension(750, 400));
        ContentPane.setBounds(new Rectangle(0, 0, 10, 10));
        ContentPane.setLayout(gridBagLayout1);

        paneBid.setMaximumSize(new Dimension(350, 400));
        paneBid.setMinimumSize(new Dimension(320, 200));
        paneBid.setOpaque(false);
        paneBid.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        paneBid.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        paneBid.setPreferredSize(new Dimension(340, 225));

        paneAsk.setMaximumSize(new Dimension(350, 400));
        paneAsk.setMinimumSize(new Dimension(320, 200));
        paneAsk.setOpaque(false);
        paneAsk.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        paneAsk.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        paneAsk.setPreferredSize(new Dimension(340, 225));

        // Input Pane
        panelInput.setLayout(gridBagLayout2);
        txtSvcName.setMaximumSize(new Dimension(100, 22));
        txtSvcName.setMinimumSize(new Dimension(75, 22));
        txtSvcName.setPreferredSize(new Dimension(85, 22));
        txtSvcName.setToolTipText("Enter a valid service name.");
        txtSvcName.setText("RSSL_PROV");
        txtSvcName.setHorizontalAlignment(SwingConstants.CENTER);
        txtSvcName.addKeyListener(new OBDisplayWindow_txtSvcName_keyAdapter(this));
        txtItemName.setMaximumSize(new Dimension(100, 22));
        txtItemName.setMinimumSize(new Dimension(70, 22));
        txtItemName.setPreferredSize(new Dimension(80, 22));
        txtItemName.setToolTipText("Enter a 2-part RIC name.");
        txtItemName.setText("IBM.ARC");
        txtItemName.setHorizontalAlignment(SwingConstants.CENTER);
        txtItemName.addKeyListener(new OBDisplayWindow_txtItemName_keyAdapter(this));
        btnOpen.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, Color.white,
                                                          Color.white, new Color(103, 101, 98),
                                                          new Color(148, 145, 140)));
        btnOpen.setMaximumSize(new Dimension(75, 22));
        btnOpen.setMinimumSize(new Dimension(55, 22));
        btnOpen.setPreferredSize(new Dimension(60, 22));
        btnOpen.setToolTipText("Open Subscription.");
        btnOpen.setMargin(new Insets(1, 15, 1, 15));
        btnOpen.setText("Open");
        btnOpen.addActionListener(new OBDisplayWindow_btnOpen_actionAdapter(this));
        btnClose.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, Color.white,
                                                           Color.white, new Color(103, 101, 98),
                                                           new Color(148, 145, 140)));
        btnClose.setMaximumSize(new Dimension(75, 22));
        btnClose.setMinimumSize(new Dimension(55, 22));
        btnClose.setPreferredSize(new Dimension(60, 22));
        btnClose.setToolTipText("Close Subscription.");
        btnClose.setMargin(new Insets(1, 15, 1, 15));
        btnClose.setText("Close");
        btnClose.setEnabled(false);
        btnClose.addActionListener(new OBDisplayWindow_btnClose_actionAdapter(this));

        panelInput.setAlignmentX((float)0.5);
        panelInput.setAlignmentY((float)0.5);
        panelInput.setBorder(new TitledBorder(BorderFactory.createEtchedBorder(Color.white,
                                                                               new Color(148, 145,
                                                                                       140)),
                "Input"));
        panelInput.setMaximumSize(new Dimension(350, 80));
        panelInput.setMinimumSize(new Dimension(300, 70));
        panelInput.setPreferredSize(new Dimension(340, 70));

        lblNumBids.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.white,
                                                             Color.white, new Color(124, 124, 124),
                                                             new Color(178, 178, 178)));
        lblNumOffers.setAlignmentX((float)0.5);
        lblNumOffers.setBorder(
                     BorderFactory.createCompoundBorder(
                                   BorderFactory.createBevelBorder(
                                                 BevelBorder.LOWERED, Color.white, Color.white,
                                                 new Color(124, 124, 124),
                                                 new Color(178, 178, 178)),
                                   BorderFactory.createEmptyBorder(0, 0, 0, 2)));
        numOffPanel.setLayout(null);
        lblCurTime.setHorizontalAlignment(SwingConstants.CENTER);
        lblCurTime.setHorizontalTextPosition(SwingConstants.CENTER);
        menuItemDebugLog.addActionListener(
                         new OBDisplayWindow_menuItemDebugLog_actionAdapter(this));
        panelInput.add(btnClose,
                       new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                               GridBagConstraints.NONE, new Insets(5, 2, 5, 5), 0, 0));
        lblCurrency.setBackground(Color.white);
        lblCurrency.setBorder(null);
        lblCurrency.setMaximumSize(new Dimension(50, 15));
        lblCurrency.setMinimumSize(new Dimension(30, 15));
        lblCurrency.setOpaque(true);
        lblCurrency.setPreferredSize(new Dimension(40, 15));
        lblCurrency.setText("");
        lblUnits.setBackground(Color.white);
        lblUnits.setMaximumSize(new Dimension(50, 15));
        lblUnits.setMinimumSize(new Dimension(30, 15));
        lblUnits.setOpaque(true);
        lblUnits.setPreferredSize(new Dimension(40, 15));
        lblUnits.setText("");
        lblExch.setBackground(Color.white);
        lblExch.setMaximumSize(new Dimension(50, 15));
        lblExch.setMinimumSize(new Dimension(40, 15));
        lblExch.setOpaque(true);
        lblExch.setPreferredSize(new Dimension(30, 15));
        lblExch.setText("");
        label1.setText("Currency:");
        label2.setText("Units:");
        label3.setText("Exch Id:");

        panelSummary.setLayout(gridBagLayout3);
        panelSummary.setBorder(
                     new TitledBorder(BorderFactory.createEtchedBorder(
                                                    Color.white, new Color(148, 145, 140)), "Summary"));
        panelSummary.setMaximumSize(new Dimension(350, 80));
        panelSummary.setMinimumSize(new Dimension(300, 70));
        panelSummary.setPreferredSize(new Dimension(340, 70));
        panelSummary.add(label1,
                         new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                                 GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0));
        panelSummary.add(lblCurrency, new GridBagConstraints(1, 0, 1, 1, 0.2, 0.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(5, 0, 5, 5), 0, 0));
        panelSummary.add(label2,
                         new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                                 GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0));
        panelSummary.add(lblUnits, new GridBagConstraints(3, 0, 1, 1, 0.2, 0.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(5, 0, 5, 5), 0, 0));
        panelSummary.add(label3,
                         new GridBagConstraints(4, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                                 GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0));
        panelSummary.add(lblExch, new GridBagConstraints(5, 0, 1, 1, 0.2, 0.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(5, 0, 5, 5), 0, 0));

        lblNumBids.setBackground(Color.white);
        lblNumBids.setMaximumSize(new Dimension(50, 15));
        lblNumBids.setMinimumSize(new Dimension(30, 15));
        lblNumBids.setOpaque(true);
        lblNumBids.setPreferredSize(new Dimension(40, 15));
        lblNumBids.setText("");
        label4.setText("# Bids: ");
        numBidPanel.setMaximumSize(new Dimension(350, 25));
        numBidPanel.setMinimumSize(new Dimension(300, 20));
        numBidPanel.setPreferredSize(new Dimension(340, 20));
        flowLayout1.setHgap(0);
        numBidPanel.setLayout(flowLayout1);
        numBidPanel.add(label4);
        numBidPanel.add(lblNumBids);

        lblNumOffers.setBackground(Color.white);
        lblNumOffers.setMaximumSize(new Dimension(50, 15));
        lblNumOffers.setMinimumSize(new Dimension(30, 15));
        lblNumOffers.setOpaque(true);
        lblNumOffers.setPreferredSize(new Dimension(40, 15));
        lblNumOffers.setText("");
        label5.setText("# Offers: ");
        numOffPanel.setMaximumSize(new Dimension(350, 25));
        numOffPanel.setMinimumSize(new Dimension(300, 20));
        numOffPanel.setPreferredSize(new Dimension(340, 20));
        numOffPanel.setLayout(flowLayout1);
        numOffPanel.add(label5);
        numOffPanel.add(lblNumOffers);

        DateRenderer dateRenderer = new DateRenderer();
        IntegerCellRenderer intRenderer = new IntegerCellRenderer();
        CurrencyCellRenderer currencyRenderer = new CurrencyCellRenderer();
        StringRenderer stringRenderer = new StringRenderer();
        // Bid Table
        initBidTable();
        // tblBid.setMinimumSize(new Dimension(300, 200));
        tblBid.setIntercellSpacing(new Dimension(0, 0));
        // tblBid.setMaximumSize(new Dimension(350, 300));
        // tblBid.setPreferredSize(new Dimension(330, 215));
        tblBid.setRowSelectionAllowed(true);
        tblBid.setAutoscrolls(true);
        // tblBid.setCellSelectionEnabled(false);
        tblBid.setDefaultRenderer(Class.forName("java.lang.Integer"), intRenderer);
        tblBid.setDefaultRenderer(Class.forName("java.util.Date"), dateRenderer);
        tblBid.setDefaultRenderer(Class.forName("java.lang.String"), stringRenderer);
        tblBid.setDefaultRenderer(Class.forName("java.lang.Float"), currencyRenderer);

        paneBid.setViewportView(tblBid);

        // Ask Table
        initAskTable();
        // tblAsk.setMaximumSize(new Dimension(350, 300));
        // tblAsk.setMinimumSize(new Dimension(300, 200));
        // tblAsk.setPreferredSize(new Dimension(330, 215));
        tblAsk.setIntercellSpacing(new Dimension(0, 0));
        tblAsk.setRowSelectionAllowed(true);
        tblAsk.setAutoscrolls(true);
        tblAsk.setDefaultRenderer(Class.forName("java.lang.Integer"), intRenderer);
        tblAsk.setDefaultRenderer(Class.forName("java.util.Date"), dateRenderer);
        tblAsk.setDefaultRenderer(Class.forName("java.lang.String"), stringRenderer);
        tblAsk.setDefaultRenderer(Class.forName("java.lang.Float"), currencyRenderer);

        paneAsk.setViewportView(tblAsk);

        // Left Panel
        panelLeft.setMaximumSize(new Dimension(350, 400));
        panelLeft.setMinimumSize(new Dimension(340, 400));
        panelLeft.setPreferredSize(new Dimension(340, 400));
        panelLeft.setLayout(gridBagLayout4);

        // Right Panel
        panelRight.setMaximumSize(new Dimension(350, 400));
        panelRight.setMinimumSize(new Dimension(340, 400));
        panelRight.setPreferredSize(new Dimension(340, 400));
        panelRight.setLayout(gridBagLayout5);

        // Status Panel
        myStatusBar.setMaximumSize(new Dimension(1000, 30));
        myStatusBar.setMinimumSize(new Dimension(450, 25));
        myStatusBar.setPreferredSize(new Dimension(550, 30));

        myStatusBar.setFont(new java.awt.Font("MS Reference Sans Serif", Font.PLAIN, 10));
        myStatusBar.setToolTipText("RFA Status Messages.");
        lblLastActivity.setToolTipText("Last Data Activity");
        panelBottom.setMaximumSize(new Dimension(1000, 40));
        panelBottom.setMinimumSize(new Dimension(750, 25));
        panelBottom.setPreferredSize(new Dimension(750, 30));
        myStatusBar.setAlignmentX((float)0.5);
        myStatusBar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.white,
                                                              Color.white, new Color(103, 101, 98),
                                                              new Color(148, 145, 140)));

        lblLastActivity.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.white,
                                                                  Color.white, new Color(103, 101,
                                                                          98), new Color(148, 145,
                                                                          140)));
        lblLastActivity.setMaximumSize(new Dimension(200, 25));
        lblLastActivity.setMinimumSize(new Dimension(80, 25));
        lblLastActivity.setPreferredSize(new Dimension(100, 25));
        lblLastActivity.setHorizontalTextPosition(SwingConstants.LEFT);
        lblLastActivity.setText("");
        lblLastActivity.setFont(new java.awt.Font("Arial", Font.PLAIN, 9));
        lblCurTime.setFont(new java.awt.Font("Arial", Font.PLAIN, 12));
        lblCurTime.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.white,
                                                             Color.white, new Color(103, 101, 98),
                                                             new Color(148, 145, 140)));
        lblCurTime.setMaximumSize(new Dimension(200, 25));
        lblCurTime.setMinimumSize(new Dimension(80, 25));
        lblCurTime.setPreferredSize(new Dimension(100, 25));
        lblCurTime.setText("");
        panelBottom.setLayout(gridBagLayout6);
        ContentPane.add(panelLeft, new GridBagConstraints(0, 0, 1, 1, 0.5, 1.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 10), 0,
                0));
        ContentPane.add(panelRight, new GridBagConstraints(1, 0, 1, 1, 0.5, 1.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 10, 0, 0), 0,
                0));
        this.setJMenuBar(menuBar);
        menuBar.add(menuOptns);
        menuItemGMT.setHorizontalAlignment(SwingConstants.LEFT);
        menuItemGMT.setHorizontalTextPosition(SwingConstants.RIGHT);
        menuItemGMT.setSelected(true);
        // menuItemGMT.setIconTextGap(20);
        menuItemLocal.setHorizontalAlignment(SwingConstants.LEFT);
        menuItemLocal.setHorizontalTextPosition(SwingConstants.RIGHT);
        menuItemLocal.setIcon(null);

        menuOptns.add(menuDict);
        menuOptns.add(menuTimeFmt);
        menuOptns.add(menuItemDebugLog);
        ButtonGroup group = new ButtonGroup();
        group.add(menuItemNetworkDict);
        group.add(menuItemLocalDict);
        menuItemLocalDict.setSelected(true);
        menuDict.add(menuItemNetworkDict);
        menuDict.add(menuItemLocalDict);
        menuItemNetworkDict.setMargin(new Insets(2, 10, 2, 2));
        menuItemNetworkDict.setSelected(false);
        menuItemLocalDict.setSelected(true);
        menuItemLocalDict.setMargin(new Insets(2, 10, 2, 2));

        ButtonGroup group2 = new ButtonGroup();
        group2.add(menuItemGMT);
        group2.add(menuItemLocal);
        menuItemGMT.setMargin(new Insets(2, 10, 2, 2));
        menuItemLocal.setMargin(new Insets(2, 10, 2, 2));
        menuItemLocal.setSelected(true);
        menuTimeFmt.add(menuItemGMT);
        menuTimeFmt.add(menuItemLocal);
        menuItemQuit.addActionListener(new OBDisplayWindow_menuItemQuit_actionAdapter(this));
        menuOptns.add(menuItemQuit);

        panelLeft.add(panelInput, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(0, 5, 0, 0), 0, 0));
        panelLeft.add(numBidPanel, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(0, 5, 0, 0), 0, 0));
        panelLeft.add(paneBid,
                      new GridBagConstraints(0, 2, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                              GridBagConstraints.BOTH, new Insets(0, 5, 5, 0), 0, 0));
        panelRight.add(panelSummary, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(0, 0, 0, 5), 0, 0));
        panelRight.add(numOffPanel, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(0, 0, 0, 5), 0, 0));
        panelRight.add(paneAsk,
                       new GridBagConstraints(0, 2, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                               GridBagConstraints.BOTH, new Insets(0, 0, 5, 5), 0, 0));
        ContentPane.add(panelBottom, new GridBagConstraints(0, 1, 2, 1, 1.0, 0.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(0, 0, 0, 5), 0, 0));
        panelBottom.add(lblCurTime,
                        new GridBagConstraints(2, 0, 1, 1, 0.2, 0.0, GridBagConstraints.NORTHEAST,
                                GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        panelBottom.add(lblLastActivity,
                        new GridBagConstraints(1, 0, 1, 1, 0.2, 0.0, GridBagConstraints.NORTHEAST,
                                GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
        panelBottom.add(myStatusBar, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(0, 5, 0, 2), 0, 0));
        panelInput.add(txtItemName, new GridBagConstraints(1, 0, 1, 1, 0.2, 0.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(5, 2, 5, 5), 0, 0));
        panelInput.add(btnOpen,
                       new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                               GridBagConstraints.NONE, new Insets(5, 5, 5, 2), 0, 0));
        panelInput.add(txtSvcName, new GridBagConstraints(0, 0, 1, 1, 0.8, 0.0,
                GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(5, 5, 10, 5), 0, 0));

        _OBProxy = new RFAWrapper(this);
    }

    public void updateCurrentTime(String time)
    {
        lblCurTime.setText(time);
    }

    private void initBidTable()
    {

        Vector<String> colNames = new Vector<String>();
        colNames.add("OrderID");
        colNames.add("Quote Time");
        colNames.add("BID Size");
        colNames.add("BID Price");

        bidTableModel = new MyTableModel();
        bidTableModel.setColumnIdentifiers(colNames);
        tblBid.setModel(bidTableModel);
        tblBid.setAutoCreateRowSorter(true);
        return;

    }

    private void initAskTable()
    {
        Vector<String> colNames = new Vector<String>();
        colNames.add("ASK Price");
        colNames.add("ASK Size");
        colNames.add("Quote Time");
        colNames.add("OrderID");

        askTableModel = new MyTableModel();
        askTableModel.setColumnIdentifiers(colNames);
        tblAsk.setModel(askTableModel);
        tblAsk.setAutoCreateRowSorter(true);
        return;
    }

    /*************************************
     * Call back Functions
     **************************************/

    public void notifyStatus(String status)
    {
        myStatusBar.setMessage(status);
    }

    public void processInsertOrder(String orderId, String orderSz, String orderPr, String QteTime,
            String orderSide)
    {
        Date frmtTime = formatTime(QteTime);
        Number frmtOrderSz = new Integer(orderSz);
        Number frmtOrderPr = new Float(orderPr);

        System.out.println("Received Insert call back with follg. values");
        System.out.print("OrderId: " + orderId);
        System.out.print(" QteTime: " + frmtTime);
        System.out.print(" orderSz: " + orderSz);
        System.out.print(" orderPr: " + orderPr);
        System.out.println(" orderSide: " + orderSide);

        Vector<Object> v = new Vector<Object>();
        int cnt;
        if (orderSide.equals("BID"))
        {
            v.add(orderId);
            v.add(frmtTime);
            v.add(frmtOrderSz);
            v.add(frmtOrderPr);
            bidTableModel.addRow(v);
            cnt = bidTableModel.getRowCount();
            lblNumBids.setText(Integer.toString(cnt));
            changeColor(TableModelEvent.INSERT, cnt - 1, tblBid);
        }
        else
        {
            v.add(frmtOrderPr);
            v.add(frmtOrderSz);
            v.add(frmtTime);
            v.add(orderId);
            askTableModel.addRow(v);
            cnt = askTableModel.getRowCount();
            lblNumOffers.setText(Integer.toString(cnt));
            changeColor(TableModelEvent.INSERT, cnt - 1, tblAsk);
        }

        return;
    }

    public void processUpdateOrder(String orderId, String orderSz, String orderPr, String QteTime)
    {

        System.out.println("Received Update call back with follg. values");
        System.out.print("OrderId: " + orderId);
        System.out.print(" orderSz: " + orderSz);
        System.out.print(" orderPr: " + orderPr);
        System.out.println(" QteTime: " + QteTime);

        int index = findIndex(askTableModel, orderId, 3);
        if (index >= 0)
        {
            if (QteTime != null)
                askTableModel.setValueAt(formatTime(QteTime), index, 2);
            if (orderSz != null)
                askTableModel.setValueAt(new Integer(orderSz), index, 1);
            if (orderPr != null)
                askTableModel.setValueAt(new Float(orderPr), index, 0);
            changeColor(TableModelEvent.UPDATE, index, tblAsk);
        }
        else
        {
            index = findIndex(bidTableModel, orderId, 0);
            if (index >= 0)
            {
                if (QteTime != null)
                    bidTableModel.setValueAt(formatTime(QteTime), index, 1);
                if (orderSz != null)
                    bidTableModel.setValueAt(new Integer(orderSz), index, 2);
                if (orderPr != null)
                    bidTableModel.setValueAt(new Float(orderPr), index, 3);
                changeColor(TableModelEvent.UPDATE, index, tblBid);
            }
        }

        return;
    }

    public void processDeleteOrder(String orderId)
    {
        MyTableModel tableModel;
        System.out.println("Received Delete call back with follg. values");
        System.out.println("OrderId: " + orderId);
        tableModel = askTableModel;
        JLabel lbl = lblNumOffers;
        JTable myTable = tblAsk;

        int index = findIndex(tableModel, orderId, 3);

        if (index < 0)
        {
            tableModel = bidTableModel;
            myTable = tblBid;
            index = findIndex(tableModel, orderId, 0);
            lbl = lblNumBids;
        }
        System.out.println("Num of Rows in the Table: " + tableModel.getRowCount());

        if (index >= 0)
        {
            tableModel.removeRow(index);
            lbl.setText(Integer.toString(tableModel.getRowCount()));
            changeColor(TableModelEvent.DELETE, index - 1, myTable);

        }

        return;
    }

    public void processSummary(String currency, String trdUnits, String exchId, String mktState)
    {
        // System.out.println("Received Summary call back with follg. values");
        lblCurrency.setText(currency);
        lblUnits.setText(trdUnits);
        txtExch.setText(exchId);

        /*
         * System.out.print("currency: " + currency);
         * System.out.print(" trdUnits: " + trdUnits);
         * System.out.print(" exchId: " + exchId);
         * System.out.println(" mktState: " + mktState);
         */
        return;
    }

    public void processClear()
    {

        initAskTable();
        initBidTable();

        return;
    }

    /****** End of Call Backs ************************/

    public short getDictType()
    {

        if (menuItemLocalDict.isSelected())
        {
            return OrderBookDisplay.DICTIONARY_LOCAL;
        }
        else
        {
            return OrderBookDisplay.DICTIONARY_NETWORK;
        }
    }

    public short getTimeType()
    {
        if (menuItemLocal.isSelected())
        {
            return OrderBookDisplay.TIME_LOCAL;
        }
        else
        {
            return OrderBookDisplay.TIME_GMT;
        }
    }

    private void startSubscribe()
    {
        // data dictionary settings can only be set once per life of the
        // application
        if (!_bInitialized)
        {
            menuItemNetworkDict.setEnabled(false);
            menuItemLocalDict.setEnabled(false);
            menuDict.setEnabled(false);

            _OBProxy.initRFA(this.getDictType());
            _OBProxy.setDebug(menuItemDebugLog.isSelected());
            _bInitialized = true;
        }

        if (_OBProxy == null)
            return;

        // Initialize TableModels
        initBidTable();
        initAskTable();

        myStatusBar.setMessage("Subscribing to Service: " + txtSvcName.getText() + " Item: "
                + txtItemName.getText());
        Thread dispatchThread = new Thread(_OBProxy);

        // Make a login request and Start the dispatch Thread
        dispatchThread.start();
        _OBProxy.subscribe(txtSvcName.getText(), txtItemName.getText());
        notifyStatus("");
        btnClose.setEnabled(true);
        btnOpen.setEnabled(false);

    }

    private void cleanup()
    {

        if (clkThread != null)
        {
            clk.setDone(true);
            clkThread.interrupt();
        }

        if (_OBProxy.isAcquired())
        {
            _OBProxy.unsubscribe();
            _OBProxy.release();
        }

        this.dispose();
        System.exit(0);
    }

    private int findIndex(MyTableModel tableModel, String orderId, int colNum)
    {
        String curId;
        int index = -1;

        if (orderId == null)
        {
            return index;
        }

        int rowCnt = tableModel.getRowCount();
        for (int i = 0; i < rowCnt; i++)
        {
            curId = (String)tableModel.getValueAt(i, colNum);
            if (curId.equalsIgnoreCase(orderId))
            {
                index = i;
                break;
            }
        }

        return index;
    }

    private Date formatTime(String time)
    {
        // String frmtTime;

        long timeInms = Long.parseLong(time);

        Calendar calGMT = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

        int secs = (int)(timeInms / 1000);
        timeInms %= 1000;
        int mins = secs / 60;
        secs %= 60;
        int hrs = mins / 60;
        mins %= 60;
        calGMT.set(Calendar.HOUR_OF_DAY, hrs);
        calGMT.set(Calendar.MINUTE, mins);
        calGMT.set(Calendar.SECOND, secs);
        Date date = calGMT.getTime();

        if (getTimeType() == OrderBookDisplay.TIME_LOCAL)
        {
            Calendar calLocal = new GregorianCalendar();
            calLocal.setTimeInMillis(calGMT.getTimeInMillis());
            date = calLocal.getTime();
        }

        return date;
    }

    public void menuItemQuit_actionPerformed(ActionEvent actionEvent)
    {
        cleanup();
    }

    public void btnOpen_actionPerformed(ActionEvent actionEvent)
    {
        startSubscribe();
    }

    public void btnClose_actionPerformed(ActionEvent actionEvent)
    {
        // Stop Dispatching events
        _OBProxy.stop();

        // Clear table Models
        initBidTable();
        initAskTable();

        lblCurrency.setText("");
        lblUnits.setText("");
        lblExch.setText("");

        _bInitialized = false;
        lblNumBids.setText("");
        lblNumOffers.setText("");

        btnClose.setEnabled(false);
        btnOpen.setEnabled(true);
        myStatusBar.setMessage("Ready");
    }

    public void menuItemDebugLog_actionPerformed(ActionEvent actionEvent)
    {
        if (_OBProxy != null && _OBProxy.isAcquired())
        {
            _OBProxy.setDebug(menuItemDebugLog.isSelected());
        }

    }

    public void txtSvcName_keyPressed(KeyEvent keyEvent)
    {
        txtItemName_keyPressed(keyEvent);
    }

    public void txtItemName_keyPressed(KeyEvent keyEvent)
    {
        if (txtItemName.getText().length() == 0 || txtSvcName.getText().length() == 0)
        {
            return;
        }
        if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER && btnOpen.isEnabled())
        {
            startSubscribe();
        }

    }

    private void changeColor(int eventType, int modelIndex, JTable myTable)
    {
        System.out.print("Row: " + modelIndex);
        System.out.print("\tType: " + eventType);

        int tableRow;
        try
        {
            restoreColors(myTable);
            tableRow = myTable.convertRowIndexToView(modelIndex);
            System.out.println("\tRow in View: " + tableRow);
            // determine the Type, and set the background color
            switch (eventType)
            {
                case TableModelEvent.INSERT:
                    myTable.setRowSelectionInterval(tableRow, tableRow);
                    myTable.setSelectionBackground(Color.GREEN);
                    break;
                case TableModelEvent.UPDATE:
                    myTable.setRowSelectionInterval(tableRow, tableRow);
                    myTable.setSelectionBackground(Color.YELLOW);
                    break;
                case TableModelEvent.DELETE:
                    // if (tableRow == 0) tableRow++;
                    myTable.setRowSelectionInterval(tableRow, tableRow);
                    myTable.setSelectionBackground(Color.RED);
                    break;
                default:
                    break;
            }
        }
        catch (Exception exp)
        {
            System.out.println(exp.getMessage());
            System.out.println(exp.getStackTrace());
        }
    }

    private void restoreColors(JTable myTable)
    {
        if (myTable.getSelectedRow() > 0)
        {
            myTable.setSelectionBackground(Color.WHITE);
            myTable.setSelectionForeground(Color.BLACK);
        }

    }

    Clock clk;
    Thread clkThread;
    private RFAWrapper _OBProxy = null;
    boolean _bInitialized = false;
    public static final short DICTIONARY_LOCAL = 1;
    public static final short DICTIONARY_NETWORK = 2;
    public static final short TIME_LOCAL = 1;
    public static final short TIME_GMT = 2;

    GridBagLayout gridBagLayout1 = new GridBagLayout();
    JPanel ContentPane = new JPanel();
    GridBagLayout gridBagLayout2 = new GridBagLayout();
    JPanel panelInput = new JPanel();
    JTextField txtSvcName = new JTextField();
    JTextField txtItemName = new JTextField();
    JButton btnOpen = new JButton();
    JButton btnClose = new JButton();
    GridBagLayout gridBagLayout3 = new GridBagLayout();
    JPanel panelSummary = new JPanel();
    JTextField txtExch = new JTextField();
    JLabel label1 = new JLabel();
    JLabel label2 = new JLabel();
    JLabel label3 = new JLabel();
    JLabel lblCurrency = new JLabel();
    JLabel lblUnits = new JLabel();
    JLabel lblExch = new JLabel();
    JPanel panelLeft = new JPanel();
    JPanel panelRight = new JPanel();
    GridBagLayout gridBagLayout4 = new GridBagLayout();
    GridBagLayout gridBagLayout5 = new GridBagLayout();
    JScrollPane paneBid = new JScrollPane();
    JScrollPane paneAsk = new JScrollPane();
    MyTableModel askTableModel = null;
    MyTableModel bidTableModel = null;
    JTable tblAsk = new JTable();
    JTable tblBid = new JTable();
    // TableSorter bidSorter;
    // TableSorter askSorter;
    JPanel panelBottom = new JPanel();
    GridBagLayout gridBagLayout6 = new GridBagLayout();
    StatusBar myStatusBar = new StatusBar();
    JLabel lblLastActivity = new JLabel();
    JLabel lblCurTime = new JLabel();
    JMenuBar menuBar = new JMenuBar();
    JMenu menuOptns = new JMenu("Options");
    JMenu menuDict = new JMenu("Dictionary");
    JMenu menuTimeFmt = new JMenu("Time Format");
    JRadioButtonMenuItem menuItemGMT = new JRadioButtonMenuItem("GMT");
    JRadioButtonMenuItem menuItemLocal = new JRadioButtonMenuItem("Local");
    JMenuItem menuItemQuit = new JMenuItem("Quit");
    JRadioButtonMenuItem menuItemNetworkDict = new JRadioButtonMenuItem("Network");
    JRadioButtonMenuItem menuItemLocalDict = new JRadioButtonMenuItem("Local");
    JCheckBoxMenuItem menuItemDebugLog = new JCheckBoxMenuItem("Debug Log");
    JPanel numBidPanel = new JPanel();
    JLabel lblNumBids = new JLabel();
    JLabel label4 = new JLabel();
    JPanel numOffPanel = new JPanel();
    JLabel lblNumOffers = new JLabel();
    JLabel label5 = new JLabel();
    FlowLayout flowLayout1 = new FlowLayout(FlowLayout.RIGHT);

    class OBDisplayWindow_txtItemName_keyAdapter extends KeyAdapter
    {
        private OrderBookDisplay adaptee;

        OBDisplayWindow_txtItemName_keyAdapter(OrderBookDisplay adaptee)
        {
            this.adaptee = adaptee;
        }

        public void keyPressed(KeyEvent keyEvent)
        {
            adaptee.txtItemName_keyPressed(keyEvent);
        }
    }

    class OBDisplayWindow_txtSvcName_keyAdapter extends KeyAdapter
    {
        private OrderBookDisplay adaptee;

        OBDisplayWindow_txtSvcName_keyAdapter(OrderBookDisplay adaptee)
        {
            this.adaptee = adaptee;
        }

        public void keyPressed(KeyEvent keyEvent)
        {
            adaptee.txtSvcName_keyPressed(keyEvent);
        }
    }

    class OBDisplayWindow_menuItemDebugLog_actionAdapter implements ActionListener
    {
        private OrderBookDisplay adaptee;

        OBDisplayWindow_menuItemDebugLog_actionAdapter(OrderBookDisplay adaptee)
        {
            this.adaptee = adaptee;
        }

        public void actionPerformed(ActionEvent actionEvent)
        {
            adaptee.menuItemDebugLog_actionPerformed(actionEvent);
        }
    }

    class OBDisplayWindow_btnClose_actionAdapter implements ActionListener
    {
        private OrderBookDisplay adaptee;

        OBDisplayWindow_btnClose_actionAdapter(OrderBookDisplay adaptee)
        {
            this.adaptee = adaptee;
        }

        public void actionPerformed(ActionEvent actionEvent)
        {
            adaptee.btnClose_actionPerformed(actionEvent);
        }
    }

    class OBDisplayWindow_btnOpen_actionAdapter implements ActionListener
    {
        private OrderBookDisplay adaptee;

        OBDisplayWindow_btnOpen_actionAdapter(OrderBookDisplay adaptee)
        {
            this.adaptee = adaptee;
        }

        public void actionPerformed(ActionEvent actionEvent)
        {
            adaptee.btnOpen_actionPerformed(actionEvent);
        }
    }

    class OBDisplayWindow_menuItemQuit_actionAdapter implements ActionListener
    {
        private OrderBookDisplay adaptee;

        OBDisplayWindow_menuItemQuit_actionAdapter(OrderBookDisplay adaptee)
        {
            this.adaptee = adaptee;
        }

        public void actionPerformed(ActionEvent actionEvent)
        {
            adaptee.menuItemQuit_actionPerformed(actionEvent);
        }
    }

}
