package com.reuters.rfa.example.omm.consPerf;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMItemEvent;

/**
 * <p>
 * This class handles responses for login, timers and item requests (in
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_PRICE MARKET_PRICE} RDM
 * domain).
 * 
 * It uses {@link com.reuters.rfa.example.omm.consPerf.DataDisplay
 * DataDisplay} to print statistics at display interval. DataDisplay class also
 * prints the messages at it arrives.
 * 
 * @see DataDisplay
 */
public class ResponseManager implements Client
{
    String _className = "ResponseManager";
    RequestManager _reqManager;
    FieldDictionary _RDMFieldDictionary;
    DataDisplay _dataDisplay;

    boolean _loginSuccessful;

    public void init(RequestManager reqMgr)
    {
        this._reqManager = reqMgr;
        _dataDisplay = new DataDisplay();

        try
        {
            _dataDisplay.init();
        }
        catch (DictionaryException ex)
        {
            System.out.println("ERROR: Unable to initialize dictionaries.");
            System.out.println(ex.getMessage());
            if (ex.getCause() != null)
                System.err.println(": " + ex.getCause().getMessage());
            _reqManager.cleanup(-1);
        }
    }

    public void processEvent(Event event)
    {
        // System.out.println(_className +
        // ": received an OMM Event or a Timer Event...");
        if (event.getType() == Event.TIMER_EVENT)
        {
            _dataDisplay.printStats();
            return;
        }
        if (event.getType() != Event.OMM_ITEM_EVENT)
        {
            System.out.println("ERROR: " + _className + " Received an unsupported Event type.");
            _reqManager.cleanup(-1);
            return;
        }
        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg respMsg = ie.getMsg();

        _dataDisplay.printData(respMsg);

        if (respMsg.getMsgModelType() == RDMMsgTypes.LOGIN)
        {
            processLogin(respMsg);
        }
        else
        {
            processItemEvent(ie);
        }
    }

    private void processItemEvent(OMMItemEvent itemEvent)
    {
        OMMMsg respMsg = itemEvent.getMsg();
        if (respMsg.getMsgType() == OMMMsg.MsgType.GENERIC)
        {
            System.out.print(_className + ": Generic Message received, ignoring...");
            return;
        }
        System.out.println(_className + ".processEvent: Received Item Response... ");
        if (respMsg.getMsgModelType() == RDMMsgTypes.MARKET_PRICE)
        {
            _dataDisplay.updateStats(respMsg.getMsgType());
        }
        else
        {
            System.out.print(_className + ": Received unhandled response - "
                    + RDMMsgTypes.toString(respMsg.getMsgModelType()));
        }
    }

    private void processLogin(OMMMsg respMsg)
    {
        System.out.println(_className + ".processEvent: Received Login Response... ");
        if (respMsg.isFinal())
        {
            System.out.println(_className + ": Login Response message is final.");
            System.out.println(_className + ": Login has been denied / rejected / closed.");
            System.out.println(_className + ": Preparing to clean up and exiting...");
            _reqManager.cleanup(1, false);
            return;
        }

        _dataDisplay.updateStats(respMsg.getMsgType());
        if ((respMsg.getMsgType() == OMMMsg.MsgType.STATUS_RESP) && (respMsg.has(OMMMsg.HAS_STATE))
                && (respMsg.getState().getStreamState() == OMMState.Stream.OPEN)
                && (respMsg.getState().getDataState() == OMMState.Data.OK))
        {
            System.out.println(_className + ": Received Login STATUS OK Response");
            _loginSuccessful = true;
        }
        else
        {
            System.out.println(_className + ": Received Login Response - "
                    + OMMMsg.MsgType.toString(respMsg.getMsgType()));
        }
    }

    public boolean isReady()
    {
        return _loginSuccessful;
    }
}
