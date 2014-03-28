package com.reuters.rfa.example.omm.gui.viewer;

import java.util.Iterator;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.dictionary.DataDef;
import com.reuters.rfa.dictionary.DataDefDictionary;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.dictionary.FieldEntryDef;
import com.reuters.rfa.example.framework.sub.OMMSubAppContext;
import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.example.utility.gui.JLoggedStatusBar;
import com.reuters.rfa.omm.OMMDataBuffer;
import com.reuters.rfa.omm.OMMDataDefs;
import com.reuters.rfa.omm.OMMFieldEntry;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMMap;
import com.reuters.rfa.omm.OMMMapEntry;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.session.omm.OMMItemEvent;

/**
 * Client for OMM Data and manager of the various table models. Inspects the
 * data to activate the right table in the display and passes the data to the
 * table models.
 */
public class OMMClient implements Client
{
    private MapTableModel _mapTableModel;
    private FieldListTableModel _summaryTableModel;
    private FieldListTableModel _fieldListTableModel;
    private MapValues _mapValues;
    private FieldListValues _fieldListValues;
    private String _servicename;
    String _name;
    private short _mmt;
    boolean _isStreaming;
    private Handle _handle;
    SubAppContext _appContext;
    ViewerItemPanel _itemPanel;
    private JLoggedStatusBar _status;
    private boolean _fieldColumnsAdded;
    private boolean _keyColumnAdded;
    private boolean _debug;

    public OMMClient(SubAppContext appContext, JLoggedStatusBar status)
    {
        _status = status;
        _appContext = appContext;

        FieldDictionary dict = _appContext.getFieldDictionary();
        _fieldListTableModel = new FieldListTableModel(dict, true);
        _fieldListValues = new FieldListValues(_fieldListTableModel, 128, -1, 1);
        _fieldListTableModel._fieldListValues = _fieldListValues;

        _mapTableModel = new MapTableModel(dict);
        _summaryTableModel = new FieldListTableModel(dict, false);
        _mapValues = new MapValues(_mapTableModel, _summaryTableModel);
        _mapTableModel._values = _mapValues;
        _summaryTableModel._fieldListValues = _mapValues.getSummary();

        _debug = CommandLine.booleanVariable("debug");
    }

    public void processEvent(Event event)
    {
        OMMMsg msg = ((OMMItemEvent)event).getMsg();
        if (_debug)
        	GenericOMMParser.parse( msg );

        if (_handle != event.getHandle())
        {
            // _handle was closed while events were queued
            return;
        }

        if (msg.isFinal())
        {
            _handle = null;
            clearDataAndTables();
        }

        if (msg.isSet(OMMMsg.Indication.CLEAR_CACHE))
        {
            clearDataAndTables();
        }

        if (msg.has(OMMMsg.HAS_STATE))
        {
            String stateText = msg.getState().toString();
            // only log if it changed
            if (!_status.getCurrentStatus().equals(stateText))
                _status.setStatus(msg.getState().toString());
        }

        if (msg.getDataType() == OMMTypes.MAP)
        {
            System.out.println("DEBUG: OMMClient.processEvent: map:");
            GenericOMMParser.parse(msg);
            processMap(msg);
        }
        else if (msg.getDataType() == OMMTypes.FIELD_LIST)
        {
            processFieldList(msg);
        }
        else if ((msg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
                && (msg.getDataType() != OMMTypes.NO_DATA))
        {
            _status.setStatus("Unsupported data format: " + OMMTypes.toString(msg.getDataType()));
            close();
        }
        else if ((msg.getMsgType() == OMMMsg.MsgType.STATUS_RESP) && msg.has(OMMMsg.HAS_STATE))
        {
            _mapValues.setDataState(msg.getState().getDataState());
            _fieldListValues.setDataState(msg.getState().getDataState());
        }
    }

    private void clearDataAndTables()
    {
        _fieldListValues.clear();
        _fieldListTableModel.processClear();
        _mapValues.clear();
        _mapTableModel.processClear();
        _mapTableModel.fireTableDataChanged();
        _mapTableModel.fireTableStructureChanged();
    }

    private void processFieldList(OMMMsg msg)
    {
        OMMFieldList fieldList = (OMMFieldList)msg.getPayload();
        _itemPanel.enableFieldListTablePanel();

        boolean ripple = (msg.getMsgType() == OMMMsg.MsgType.UPDATE_RESP)
                && !msg.isSet(OMMMsg.Indication.DO_NOT_RIPPLE);
        if (msg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
        {
            _fieldListValues.refresh(_appContext.getFieldDictionary(), fieldList);
            _fieldListTableModel.fireTableStructureChanged();
        }
        else
        {
            _fieldListValues.update(_appContext.getFieldDictionary(), fieldList, ripple);
        }
        _fieldListTableModel.fireTableDataChanged();
        _fieldListTableModel.resizeColumns();
    }

    private void processMap(OMMMsg msg)
    {
        boolean dataChanged = false;
        boolean structureChanged = false;
        OMMMap map = (OMMMap)msg.getPayload();
        boolean ripple = (msg.getMsgType() == OMMMsg.MsgType.UPDATE_RESP)
                && !msg.isSet(OMMMsg.Indication.DO_NOT_RIPPLE);
        if ((msg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
                && (msg.getRespTypeNum() == OMMMsg.RespType.SOLICITED))
        {
            if (!_fieldColumnsAdded || !_keyColumnAdded)
            {
                if (map.has(OMMMap.HAS_SUMMARY_DATA))
                {
                    _itemPanel.enableMapSummaryPanel();
                }
                else
                {
                    _itemPanel.enableMapPanel();
                }

                if (map.has(OMMMap.HAS_TOTAL_COUNT_HINT))
                    _mapValues.setCount(map.getTotalCountHint());
                else
                    _mapValues.setCount(64);

                if (!_keyColumnAdded)
                {
                    _keyColumnAdded = true;
                    if (map.getKeyFieldId() != 0)
                    {
                        FidDef fiddef = _appContext.getFieldDictionary()
                                .getFidDef(map.getKeyFieldId());
                        if (fiddef != null)
                            _mapTableModel.addKeyColumn(fiddef);
                    }
                    else
                    {
                        _mapTableModel.addKeyColumn("Key");
                    }
                    structureChanged = true;
                }

                if (map.has(OMMMap.HAS_DATA_DEFINITIONS))
                {
                    _fieldColumnsAdded = true;
                    OMMDataDefs datadefs = map.getDataDefs();
                    DataDefDictionary dictionary = DataDefDictionary
                            .create(OMMTypes.FIELD_LIST_DEF_DB);
                    DataDefDictionary.decodeOMMDataDefs(dictionary, datadefs);

                    // assume there is only one definition and it is using 0 for dataDefId
                    DataDef datadef = dictionary.getDataDef((short)0);
                    
                    for (Iterator<Object> defiter = datadef.iterator(); defiter.hasNext();)
                    {
                        FieldEntryDef entry = (FieldEntryDef)defiter.next();
                        FidDef fiddef = _appContext.getFieldDictionary()
                                .getFidDef(entry.getFieldId());
                        if (fiddef != null)
                            _mapTableModel.addColumn(fiddef);
                    }
                }
            }
        }

        if (msg.has(OMMMsg.HAS_STATE))
        {
            _mapValues.setDataState(msg.getState().getDataState());
            dataChanged = true;
        }

        if (map.has(OMMMap.HAS_SUMMARY_DATA))
        {
            if (msg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP)
            {
                _mapValues.refreshSummary(_appContext.getFieldDictionary(),
                                          (OMMFieldList)map.getSummaryData());
                _summaryTableModel.fireTableStructureChanged();
            }
            else
            {
                _mapValues.updateSummary(_appContext.getFieldDictionary(),
                                         (OMMFieldList)map.getSummaryData(), ripple);
            }
            _summaryTableModel.fireTableDataChanged();
        }

        for (Iterator<?> miter = map.iterator(); miter.hasNext();)
        {
            OMMMapEntry mentry = (OMMMapEntry)miter.next();
            OMMDataBuffer key = (OMMDataBuffer)mentry.getKey();
            if (_debug)
                System.out.println(OMMMapEntry.Action.toString(mentry.getAction()) + ": "
                        + key.toString());
            if (mentry.getAction() == OMMMapEntry.Action.ADD)
            {
                OMMFieldList fieldList = (OMMFieldList)mentry.getData();
                if (!_fieldColumnsAdded)
                {
                    _fieldColumnsAdded = true;
                    for (Iterator<?> fiter = fieldList.iterator(); fiter.hasNext();)
                    {
                        OMMFieldEntry fentry = (OMMFieldEntry)fiter.next();
                        FidDef fiddef = _appContext.getFieldDictionary()
                                .getFidDef(fentry.getFieldId());
                        if (fiddef != null)
                            _mapTableModel.addColumn(fiddef);
                    }
                }

                _mapValues.add(_appContext.getFieldDictionary(), map, key, fieldList);
                dataChanged = true;
            }
            else if (mentry.getAction() == OMMMapEntry.Action.UPDATE
                    && mentry.getDataType() != OMMTypes.NO_DATA)
            {
                OMMFieldList fieldList = (OMMFieldList)mentry.getData();
                _mapValues.update(_appContext.getFieldDictionary(), key, fieldList, ripple);
                dataChanged = true;
            }
            else
            // (mentry.getAction() == OMMMapEntry.DELETE_ACTION)
            {
                _mapValues.delete(key);
                dataChanged = true;
            }
        }

        if (structureChanged)
            _mapTableModel.fireTableStructureChanged();
        if (dataChanged)
            _mapTableModel.fireTableDataChanged();
    }

    public void open(short mmt, String servicename, String name, boolean isSreaming)
    {
        if (servicename.equals(_servicename) && name.equals(_name) && (_mmt == mmt))
            return;

        close();

        _name = name;
        _servicename = servicename;
        _mmt = mmt;
        _isStreaming = isSreaming;

        _handle = ((OMMSubAppContext)_appContext).register(this, _servicename, _name, isSreaming,
                                                           _mmt);
        _status.setStatus("Requesting " + _servicename + ", " + RDMMsgTypes.toString(_mmt) + ", "
                + _name);
    }

    public void reissue(int indicationFlags, byte priorityClass, int priorityCount)
    {
        ((OMMSubAppContext)_appContext).reissue(_handle, _servicename, _name,
                                                OMMMsg.MsgType.REQUEST, _mmt,
                                                indicationFlags, priorityClass,
                                                priorityCount);
 
        _status.setStatus("Send " 
                          + (((indicationFlags & OMMMsg.Indication.REFRESH) != 0) ? "[REFRESH] " : "")
                          + (((indicationFlags & OMMMsg.Indication.PAUSE_REQ) != 0) ? "[PAUSE] " : "")
                          + " [Priority Class=" + priorityClass
                          + " Count=" + priorityCount + "]");
    }

    public void close()
    {
        if (_handle != null)
        {
            _appContext.unregister(_handle);
            _handle = null;
        }
        clearDisplay();
        _name = "";
        _servicename = "";
        _mmt = 0;
    }

    private void clearDisplay()
    {
        _itemPanel.clearTablePanel();
        if (_fieldListValues != null)
            _fieldListValues.clear();
        if (_mapValues != null)
            _mapValues.clear();
        _mapTableModel.clearColumns();
        _mapTableModel.fireTableDataChanged();
        _fieldListTableModel.fireTableDataChanged();
        _summaryTableModel.fireTableDataChanged();
        _mapTableModel.fireTableStructureChanged();
        _fieldListTableModel.fireTableStructureChanged();
        _summaryTableModel.fireTableStructureChanged();

        _mapTableModel.clearColumns();
        if (_mapValues != null)
            _mapValues.clear();
        _mapTableModel.fireTableStructureChanged();
        _summaryTableModel.fireTableStructureChanged();
        if (_fieldListValues != null)
            _fieldListValues.clear();
        _fieldListTableModel.fireTableStructureChanged();

        _fieldColumnsAdded = false;
        _keyColumnAdded = false;

    }

    public FadingTableModel getMapTableModel()
    {
        return _mapTableModel;
    }

    public FadingTableModel getMapSummaryTableModel()
    {
        return _summaryTableModel;
    }

    public FadingTableModel getFieldListTableModel()
    {
        return _fieldListTableModel;
    }
}
