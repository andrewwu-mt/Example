package com.reuters.rfa.example.omm.gui.quotelist;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Observable;

import javax.swing.table.TableModel;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.example.framework.sub.NormalizedEvent;
import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.utility.gui.Status;

/**
 * Process events of each item and manage a row of {@linkplain FieldRenderer
 * FieldRenderers}
 * 
 */
public class RecordRenderer extends Observable implements Client
{
    public RecordRenderer(SubAppContext appContext, String servicename, String ric,
            String fields[], TableModel model, Status status)
    {
        _stale = true;
        _appContext = appContext;
        _status = status;
        _ric = ric;
        _model = model;
        _servicename = servicename;
        _fields = fields;
        createFieldRenderers();
    }

    // Access
    public FieldRenderer fieldRenderer(String fieldName)
    {
        if (_record == null)
            return null;
        return (FieldRenderer)_fieldRenderers.get(fieldName);
    }

    public String getValue(String fieldName)
    {
        String value = null;
        if (_record != null)
        {
            if (_active)
            {
                if (_hasData)
                {
                    String field = (String)_record.get(fieldName);
                    if (field != null)
                        value = field;
                    else
                        value = "XX";
                }
                else
                    value = "Pending";
            }
            else
                value = "Inactive";
        }
        else
        {
            value = "";
        }
        return value;
    }

    public String symbol()
    {
        return _ric;
    }

    // Operations
    public void setShowing(boolean b)
    {
        // always setShowing for now
        setShowing();
    }

    protected void cleanup()
    {
        dropClient();
        for (Enumeration<FieldRenderer> e = _fieldRenderers.elements(); e.hasMoreElements();)
            ((FieldRenderer)e.nextElement()).cleanup();
    }

    protected void createFieldRenderers()
    {
        int numberOfFields = _model.getColumnCount();
        _fieldRenderers = new Hashtable<String, FieldRenderer>();

        for (int col = 0; col < numberOfFields; col++)
        {
            String fieldName = _model.getColumnName(col);
            FieldRenderer fieldRenderer = new FieldRenderer();
            _fieldRenderers.put(fieldName, fieldRenderer);
        }
    }

    protected void fade()
    {
        if (_fieldRenderers == null)
            return;
        for (Enumeration<FieldRenderer> e = _fieldRenderers.elements(); e.hasMoreElements();)
            ((FieldRenderer)e.nextElement()).fade();
    }

    protected void setNotShowing()
    {
        (new Exception()).printStackTrace();
        if ((_record != null) && _active)
            dropClient();
    }

    protected void setShowing()
    {
        if (_record == null)
            addClient();
    }

    // Client operations
    protected void addClient()
    {
        _active = true;
        _status.setStatus(_ric + " : Adding client");
        _record = new HashMap<String, String>();

        _handle = _appContext.register(this, _servicename, _ric, true);

        for (Enumeration<FieldRenderer> e = _fieldRenderers.elements(); e.hasMoreElements();)
        {
            FieldRenderer f = (FieldRenderer)e.nextElement();
            f.setActive(true);
            f.setStale(true);
        }
    }

    protected void dropClient()
    {
        _status.setStatus(_ric + " : Dropping client");
        _record = null;
        for (Enumeration<FieldRenderer> e = _fieldRenderers.elements(); e.hasMoreElements();)
            ((FieldRenderer)e.nextElement()).setActive(false);
        _appContext.unregister(_handle);
    }

    public void processEvent(com.reuters.rfa.common.Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_ITEM_EVENT:
                processItemEvent(event);
                break;
            case Event.CONNECTION_EVENT:
            case Event.MARKET_DATA_SVC_EVENT:
            default:
                System.out.println("SessionExample.MyClient: unhandled event: " + event);
                break;
        }
    }

    protected void indicateStatus(NormalizedEvent nevent)
    {
        if (nevent.isClosed())
        {
            _active = false;
            _status.setStatus(_ric + " Closed: " + nevent.getStatusText());
            for (Enumeration<FieldRenderer> en = _fieldRenderers.elements(); en.hasMoreElements();)
            {
                FieldRenderer f = (FieldRenderer)en.nextElement();
                f.setActive(false);
                f.setStale(true);
            }
            setChanged();
            notifyObservers(null);
            // dropClient();
        }
        else if (nevent.isSuspect())
        {
            if (_stale)
                return;
            for (Enumeration<FieldRenderer> en = _fieldRenderers.elements(); en.hasMoreElements();)
            {
                FieldRenderer f = (FieldRenderer)en.nextElement();
                f.setStale(true);
            }
            _stale = true;
            _status.setStatus(_ric + " Stale: " + nevent.getStatusText());
            setChanged();
            notifyObservers(null);
        }
        else if (nevent.isOK())
        {
            if (!_stale)
                return;

            for (Enumeration<FieldRenderer> en = _fieldRenderers.elements(); en.hasMoreElements();)
            {
                FieldRenderer f = (FieldRenderer)en.nextElement();
                f.setStale(false);
            }
            _stale = false;
            _status.setStatus(_ric + " Not Stale: " + nevent.getStatusText());
            setChanged();
            notifyObservers(null);
        }
    }

    protected void parse(NormalizedEvent nevent)
    {
        Map<String, FidDef> dictionary = _appContext.getDictionary();
        for (int i = 0; i < _fields.length; i++)
        {
            String fieldName = _fields[i];
            if (fieldName.equals("_SYMB_"))
            {
                _record.put(fieldName, _ric);
                ((FieldRenderer)_fieldRenderers.get(fieldName)).processFieldUpdate(_ric);
            }
            else
            {
                FidDef def = (FidDef)dictionary.get(fieldName);
                if (def == null)
                    continue;
                String data = nevent.getFieldString(def.getFieldId());
                if (data != null)
                {
                    _record.put(fieldName, data);
                    ((FieldRenderer)_fieldRenderers.get(fieldName)).processFieldUpdate(data);
                }
            }
        }

        for (Enumeration<FieldRenderer> en = _fieldRenderers.elements(); en.hasMoreElements();)
        {
            FieldRenderer f = (FieldRenderer)en.nextElement();

            if (nevent.isSuspect())
                f.setStale(true);
            else if (nevent.isOK())
                f.setStale(false);
            // else no change
        }

        indicateStatus(nevent);
    }

    protected void processItemEvent(Event event)
    {
        if (_record == null)
            return;

        NormalizedEvent nevent = _appContext.getNormalizedEvent(event);
        int msgType = nevent.getMsgType();
        switch (msgType)
        {
            case NormalizedEvent.REFRESH:
            {
                _hasData = true;
                parse(nevent);
                _status.setStatus(_ric + " Image: " + nevent.getStatusText());
                setChanged();
                notifyObservers(null);
            }
                break;
            case NormalizedEvent.UPDATE:
            {
                _hasData = true;
                parse(nevent);
                setChanged();
                notifyObservers(null);
            }
                break;
            case NormalizedEvent.STATUS:
            {
                indicateStatus(nevent);
            }
                break;
        }
    }

    public void changeSymbol(String element)
    {
        dropClient();
        _ric = element;
        addClient();
    }

    protected Handle _handle;
    protected Hashtable<String, FieldRenderer> _fieldRenderers;
    protected Status _status;
    protected String _ric;
    protected String _text;
    protected HashMap<String, String> _record;
    protected SubAppContext _appContext;
    protected TableModel _model;
    protected String _servicename;
    protected String[] _fields;
    protected boolean _active;
    protected boolean _hasData;
    protected boolean _stale;
}
