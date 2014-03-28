package com.reuters.rfa.example.framework.sub;

import java.util.Iterator;

import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMFieldEntry;
import com.reuters.rfa.omm.OMMFieldList;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.omm.OMMEnum;
import com.reuters.rfa.session.omm.OMMItemEvent;

public class OMMNormalizedEvent implements NormalizedEvent
{
    class OMMFieldListIterator implements Iterator<FieldEntry>
    {
        OMMFieldList _fieldList;
        Iterator<?> _iterator;
        FieldEntry _entry;
        OMMFieldEntry _ommentry;

        public OMMFieldListIterator(OMMFieldList fieldList)
        {
            reset(fieldList);
            _entry = new FieldEntry()
            {
                public short getDataType()
                {
                    return _dictionary.getFidDef(getFieldId()).getOMMType();
                }

                public short getFieldId()
                {
                    return _ommentry.getFieldId();
                }

                public String getData()
                {
                    return _ommentry.getData().toString();
                }
            };
        }

        public void reset(OMMFieldList fieldList)
        {
            _fieldList = fieldList;
            if (fieldList == null)
                _iterator = NormalizedEvent.EmptyIterator;
            else
                _iterator = _fieldList.iterator();
        }

        public boolean hasNext()
        {
            return _iterator.hasNext();
        }

        public FieldEntry next()
        {
            _ommentry = (OMMFieldEntry)_iterator.next();
            return _entry;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }

    FieldDictionary _dictionary;
    OMMItemEvent _event;

    public OMMNormalizedEvent(FieldDictionary dictionary, OMMItemEvent event)
    {
        _dictionary = dictionary;
        _event = event;
    }

    public byte[] getPermissionData()
    {
        OMMMsg msg = _event.getMsg();
        return msg.getPermissionData();
    }

    public byte[] getPayloadBytes()
    {
        OMMMsg msg = _event.getMsg();
        if (msg.getDataType() != OMMTypes.NO_DATA)
            return msg.getPayload().getBytes();
        return new byte[0];
    }

    private OMMData getFieldData(short fid)
    {
        OMMMsg msg = _event.getMsg();
        if (msg.getDataType() == OMMTypes.FIELD_LIST)
        {
            OMMFieldEntry fe = ((OMMFieldList)msg.getPayload()).find(fid);
            if (fe == null)
                return null;
            short type = _dictionary.getFidDef(fid).getOMMType();
            return fe.getData(type);
        }
        throw new IllegalArgumentException("OMMMsg payload must be field list");
    }

    public String getFieldString(short fid)
    {
        OMMData data = getFieldData(fid);
        if(data != null)
        {
        	FidDef fiddef = _dictionary.getFidDef(fid);
        	if(fiddef.getOMMType() == OMMTypes.ENUM)
        		return _dictionary.expandedValueFor(fid, ((OMMEnum)data).getValue());
        	else
        		return data.toString();
        }

        return null;
    }

    public int getFieldInt(short fid, int defaultValue)
    {
        OMMData data = getFieldData(fid);
        if (data != null)
            return (int)((OMMNumeric)data).toLong();
        return defaultValue;
    }

    public double getFieldDouble(short fid, double defaultValue)
    {
        OMMData data = getFieldData(fid);
        if (data != null)
            return ((OMMNumeric)data).toDouble();
        return defaultValue;
    }

    public int getFieldBytes(short fid, byte[] dest, int offset)
    {
        OMMData data = getFieldData(fid);
        if (data != null)
            return data.getBytes(dest, offset);
        return 0;
    }

    public boolean isSuspect()
    {
        OMMMsg msg = _event.getMsg();
        if (msg.has(OMMMsg.HAS_STATE))
            return msg.getState().getDataState() == OMMState.Data.SUSPECT;
        return false;
    }

    public boolean isOK()
    {
        OMMMsg msg = _event.getMsg();
        if (msg.has(OMMMsg.HAS_STATE))
            return msg.getState().getDataState() == OMMState.Data.OK;
        return false;
    }

    public boolean isRename()
    {
        OMMMsg msg = _event.getMsg();
        if (msg.has(OMMMsg.HAS_STATE))
            return msg.getState().getStreamState() == OMMState.Stream.REDIRECT;
        return false;
    }

    public boolean isFinal()
    {
        OMMMsg msg = _event.getMsg();
        return msg.isFinal();
    }

    public boolean isSolicited()
    {
        OMMMsg msg = _event.getMsg();
        return msg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP
                && msg.getRespTypeNum() == OMMMsg.RespType.SOLICITED;
    }

    public int getMsgType()
    {
        OMMMsg msg = _event.getMsg();
        switch (msg.getMsgType())
        {
            case OMMMsg.MsgType.REFRESH_RESP:
                return REFRESH;
            case OMMMsg.MsgType.UPDATE_RESP:
                return UPDATE;
            case OMMMsg.MsgType.STATUS_RESP:
            default:
                return STATUS;
        }
    }

    public short getDataType()
    {
        return _event.getMsg().getDataType();
    }

    public String getStatusText()
    {
        OMMMsg msg = _event.getMsg();
        if (msg.has(OMMMsg.HAS_STATE))
            return msg.getState().getText();
        return "";
    }

    public String getItemName()
    {
        OMMMsg msg = _event.getMsg();
        if (msg.has(OMMMsg.HAS_ATTRIB_INFO))
        {
            OMMAttribInfo ai = msg.getAttribInfo();
            if (ai.has(OMMAttribInfo.HAS_NAME))
                return ai.getName();
        }
        return "";
    }

    public short getUpdateType()
    {
        OMMMsg msg = _event.getMsg();
        return msg.getRespTypeNum();
    }

    public String getNewItemName()
    {
        return getItemName();
    }

    public boolean isClosed()
    {
        OMMMsg msg = _event.getMsg();
        if (msg.has(OMMMsg.HAS_STATE))
            return msg.getState().isFinal();
        return false;
    }

    /**
     * @return Iterator for {@link com.reuters.rfa.omm.OMMFieldList
     *         OMMFieldList}
     */
    public Iterator<?> fieldIterator()
    {
        OMMMsg msg = _event.getMsg();
        if (msg.getDataType() == OMMTypes.FIELD_LIST)
            return ((OMMFieldList)msg.getPayload()).iterator();
        else if (msg.getDataType() == OMMTypes.NO_DATA)
            return EmptyIterator;
        else
            throw new IllegalArgumentException("OMMMsg payload must be field list");
    }

    public int getSeqNum()
    {
        return (int)_event.getMsg().getSeqNum();
    }

    public int getFieldListNum()
    {
        OMMMsg msg = _event.getMsg();
        if (msg.getDataType() == OMMTypes.FIELD_LIST)
        {
            return ((OMMFieldList)msg.getPayload()).getListNum();
        }
        return 0;
    }

}
