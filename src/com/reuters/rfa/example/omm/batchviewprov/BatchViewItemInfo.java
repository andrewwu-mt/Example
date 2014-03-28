package com.reuters.rfa.example.omm.batchviewprov;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.reuters.rfa.common.Handle;
import com.reuters.rfa.omm.OMMArray;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMUser;

/**
 * ItemInfo is a class that contains all the relevant information regarding to
 * an item. It is used as the source of canned data for the StarterProvider_Interactive.
 * 
 */
public class BatchViewItemInfo
{
    String _name;

    double _trdPrice = 10.0000;
    double _bid = 9.8000;
    double _ask = 10.2000;
    long _acvol = 100;
    
    int _priorityCount = 0;
    int _priorityClass = 0;
    Handle _handle;

    boolean _isPaused = false;
    boolean _attribInUpdates = false;
    
    boolean _isViewDefined = false; 
    List<Short> _fidsToSend = null; // references view or all fid list from ProviderClientSession
    List<Short> _fidsList = null;
    
    private static List<Short> _allMarketPriceFids;

    static
    {
        // initialize (default) full view fids
        _allMarketPriceFids = new ArrayList<Short>();
        _allMarketPriceFids.add((short)2); // RDNDISPLAY
        _allMarketPriceFids.add((short)4); // RDN_EXCHID
        _allMarketPriceFids.add((short)6); // TRDPRC_1
        _allMarketPriceFids.add((short)22); // BID
        _allMarketPriceFids.add((short)25); // ASK
        _allMarketPriceFids.add((short)32); // ACVOL_1
        _allMarketPriceFids.add((short)38); // DIVPAYDATE
        _allMarketPriceFids.add((short)267); // ASK_TIME
    }

    BatchViewItemInfo()
    {
        _fidsList =  new ArrayList<Short>(10);
    }
    
    public List<Short> getFidsToSend()
    {
        return _fidsToSend;
    }

    public void setFidsToSend(List<Short> fidsToSend)
    {
        _fidsToSend = fidsToSend;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getName()
    {
        return _name;
    }

    public void setAttribInUpdates(boolean b)
    {
        _attribInUpdates = b;
    }

    public boolean getAttribInUpdates()
    {
        return _attribInUpdates;
    }

    public double getTradePrice1()
    {
        return _trdPrice;
    }

    public double getBid()
    {
        return _bid;
    }

    public double getAsk()
    {
        return _ask;
    }

    public long getACVol1()
    {
        return _acvol;
    }

    public void increment()
    {
        if ((_trdPrice >= 100.0000) || (_bid >= 100.0000) || (_ask >= 100.0000))
        {
            // reset prices
            _trdPrice = 10.0000;
            _bid = 9.8000;
            _ask = 10.2000;
        }
        else
        {
            _trdPrice += 0.0500; // 0.0500
            _bid += 0.0500; // 0.0500
            _ask += 0.0500; // 0.0500
        }

        if (_acvol < 1000000)
            _acvol += 50;
        else
            _acvol = 100;
    }

    public int getPriorityCount()
    {
        return _priorityCount;
    }

    public void setPriorityCount(int priorityCount)
    {
        _priorityCount = priorityCount;
    }

    public int getPriortyClass()
    {
        return _priorityClass;
    }

    public void setPriorityClass(int priorityClass)
    {
        _priorityClass = priorityClass;
    }

    public void setHandle(Handle handle)
    {
        _handle = handle;
    }

    public Handle getHandle()
    {
        return _handle;
    }
    
    public void setPaused(boolean pause)
    {
        _isPaused = pause;
    }

    public boolean isPaused()
    {
        return _isPaused;
    }
    
    private boolean isView()
    {
        return _isViewDefined;
    }
    
    /**
     * Check msg for View information.
     * @param msg
     * @return true if view has changed. This can
     */
    public boolean checkForView(OMMMsg msg)
    {
        if (msg.getMsgModelType() != RDMMsgTypes.MARKET_PRICE)
        {
            return setView(false, null);
        }

        if (msg.isSet(OMMMsg.Indication.VIEW) == false)
        {
            return setView(false, null);
        }

        // View flag is specified.

        OMMData payload = msg.getPayload();
        if (payload == null || msg.getPayload().getType() != OMMTypes.ELEMENT_LIST)
        {
            // keep exiting view if specified.
            return isView();
        }

        return updateView((OMMElementList)msg.getPayload());
    }

    /**
     * Parse the elemList for View information.
     * 
     * @param elemList OMMElementList from OMMMsg payload.
     * @return boolean indicating if view has changed.
     */
    private boolean updateView(OMMElementList elemList)
    {
        boolean sendAllFids = false;

        for (Iterator<?> iter = elemList.iterator(); iter.hasNext();)
        {
            OMMElementEntry elemEntry = (OMMElementEntry)iter.next();
            if (elemEntry.getName().equals(RDMUser.View.ViewData))
            {
                _fidsList.clear();

                OMMArray viewData = (OMMArray)elemEntry.getData();
                for (Iterator<?> viewDataIter = viewData.iterator(); viewDataIter.hasNext();)
                {
                    OMMEntry arrayEntry = (OMMEntry)viewDataIter.next();
                    OMMData data = arrayEntry.getData();
                    if (data == null || !(data instanceof OMMNumeric))
                    {
                        sendAllFids = true;
                        break;
                    }
                    _fidsList.add((short)((OMMNumeric)data).getLongValue());
                }
            }
            else if (elemEntry.getName().equals(RDMUser.View.ViewType))
            {
                OMMNumeric viewTypeData = (OMMNumeric)(elemEntry.getData());
                if (viewTypeData.getLongValue() != RDMUser.View.FIELD_ID_LIST)
                {
                    sendAllFids = true;
                    break;
                }
            }
        }

        if (sendAllFids || _fidsList.size() == 0)
        {
            return setView(false, null);
        }

        return setView(true, _fidsList);
    }

    /**
     * Sets view flag and fids List.
     * 
     * @param isView boolean indicating if view is specified.
     * @param fidList List of view data.
     * @return boolean indicating if view has changed.
     */
    private boolean setView(boolean isView, List<Short> fidList)
    {
        boolean viewChanged = _isViewDefined != isView;

        _isViewDefined = isView;

        if (fidList == null)
        {
            _fidsToSend = _allMarketPriceFids;
        }
        else
        {
            _fidsToSend = fidList;
            viewChanged = true;
        }

        return viewChanged;
    }
}
