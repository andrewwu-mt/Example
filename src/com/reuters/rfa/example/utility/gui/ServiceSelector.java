package com.reuters.rfa.example.utility.gui;

//import java.util.ArrayList;
//import java.util.List;

import javax.swing.JComboBox;

import com.reuters.rfa.example.framework.sub.DirectoryClient;
import com.reuters.rfa.example.framework.sub.ServiceInfo;
import com.reuters.rfa.example.framework.sub.SubAppContext;

public class ServiceSelector implements DirectoryClient
{

    protected boolean _serviceStatusReceived = false;
    // protected List _services;
    protected Status _status;
    protected SubAppContext _appContext;
    JComboBox _comboBox;

    public ServiceSelector(SubAppContext appContext, Status status)
    {
        _appContext = appContext;
        _status = status;
        // _services = new ArrayList();
        _comboBox = new JComboBox();
        _comboBox.setEditable(false);
        _appContext.setDirectoryClient(this);

    }

    public void cleanUp()
    {
        // _services.clear();
        _comboBox.removeAllItems();
    }

    public JComboBox component()
    {
        return _comboBox;
    }

    public String service()
    {
        String serviceName = (String)_comboBox.getSelectedItem();
        return serviceName;
    }

    // Operations
    /**
     * Display the given text on a status bar.
     **/
    public void showStatus(String text)
    {
        if (_status != null)
            _status.setStatus(text);
    }

    public void processNewService(ServiceInfo serviceInfo)
    {
        _comboBox.addItem(serviceInfo.getServiceName());
    }

    public void processServiceRemoved(ServiceInfo serviceInfo)
    {
        _comboBox.removeItem(serviceInfo.getServiceName());

    }

    public void processServiceUpdated(ServiceInfo serviceInfo)
    {
    }
}
