package com.reuters.rfa.example.framework.sub;

import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Iterator;

import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.session.MarketDataEnums;
import com.reuters.rfa.session.event.MarketDataItemEvent;
import com.reuters.rfa.session.event.MarketDataItemStatus;
import com.reuters.rfa.session.event.MarketDataItemEvent.MarketDataMessageType;
import com.reuters.rfa.utility.Base64;
import com.reuters.tibmsg.TibException;
import com.reuters.tibmsg.TibField;
import com.reuters.tibmsg.TibMsg;

public class MarketDataNormalizedEvent implements NormalizedEvent
{
    /**
     * Adapter which makes TibMsg look like an iterator
     * 
     * @see MarketDataNormalizedEvent#fieldIterator()
     */
    class TibMsgIterator implements Iterator<FieldEntry>
    {
        TibMsg _msg;
        TibField _field;
        int _lastStatus;
        FieldEntry _entry;
        NumberFormat _formatter;

        public TibMsgIterator(byte[] data) throws TibException
        {
            _msg = new TibMsg();
            _formatter = new DecimalFormat();
            _formatter.setMaximumFractionDigits(17);
            _msg.UnPack(data);
            _field = new TibField();
            _lastStatus = _field.First(_msg);
            _entry = new FieldEntry()
            {
                public short getDataType()
                {
                    return (_field.MfeedFid() == 0) ? _dictionary.getFidDef(_field.Name())
                            .getFieldId() // SASS field
                            : _dictionary.getFidDef((short)_field.MfeedFid()).getOMMType();
                }

                public short getFieldId()
                {
                    return (_field.MfeedFid() == 0) ? _dictionary.getFidDef(_field.Name()).getFieldId() // SASS field
                            : (short)_field.MfeedFid();
                }

                @SuppressWarnings("deprecation")
                public String getData()
                {
                    byte[] data = _field.RawData();
                    if (_field.Type() == TibMsg.TIBMSG_OPAQUE)
                    {
                        if (data.length == 3)
                        {
                            int value = Base64.convertBase64ToInt(data, 0, data.length);
                            return Integer.toString(value);
                        }
                        else
                        {
                            data = Base64.convertBase64ToBytes(data, 0, data.length);
                        }
                    }
                    if (data == null || data.length == 0)
                        return "";
                    // the new constructor is less performant
                    return new String(data, 0, 0, data.length);
                }
            };
        }

        public void reset(byte[] newData) throws TibException
        {
            _msg.ReUse();
            _msg.UnPack(newData);
            _field.ReUse();
            _lastStatus = _field.First(_msg);
        }

        public boolean hasNext()
        {
            return _lastStatus == TibMsg.TIBMSG_OK;
        }

        public FieldEntry next()
        {
            _lastStatus = _field.Next();
            return _entry;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }

    }

    FieldDictionary _dictionary;
    TibMsg _tibmsg;
    MarketDataItemEvent _event;
    TibMsgIterator _iterator;
    MarketfeedHeaderParser _headerParser;

    public MarketDataNormalizedEvent(FieldDictionary dictionary, MarketDataItemEvent event)
    {
        _dictionary = dictionary;
        _headerParser = new MarketfeedHeaderParser();
        _tibmsg = new TibMsg();
        setEvent(event);
    }

    public void setEvent(MarketDataItemEvent event)
    {
        _event = event;
        _headerParser.clear();
        _tibmsg.ReUse();
        if (event.getMarketDataMsgType() == MarketDataItemEvent.PERMISSION_DATA)
            return;
        byte[] data = _event.getData();
        if (data != null && data.length != 0)
        {
            try
            {

                if (_event.getDataFormat() == MarketDataEnums.DataFormat.MARKETFEED)
                    _headerParser.setBuffer(data);
                _tibmsg.UnPack(data);
            }
            catch (TibException e)
            {
            }
        }
    }

    public byte[] getPermissionData()
    {
        if (_event.getMarketDataMsgType() != MarketDataItemEvent.PERMISSION_DATA)
            return null;
        return _event.getData();
    }

    public byte[] getPayloadBytes()
    {
        return _event.getData();
    }

    private boolean isFieldListDataFormat()
    {
        return _event.getDataFormat() == MarketDataEnums.DataFormat.MARKETFEED
                || _event.getDataFormat() != MarketDataEnums.DataFormat.QFORM
                || _event.getDataFormat() != MarketDataEnums.DataFormat.IFORM
                || _event.getDataFormat() != MarketDataEnums.DataFormat.TIBMSG;
    }

    private TibField getField(String fieldName)
    {
        if (!isFieldListDataFormat())
            throw new IllegalArgumentException("Data format is not compatible");
        if (_tibmsg != null)
        {
            try
            {
                return _tibmsg.Get(fieldName);
            }
            catch (TibException e)
            {
            }
        }
        return null;
    }

    public String getFieldString(short fid)
    {
        FidDef fieldDef = _dictionary.getFidDef(fid);
        TibField field = getField(fieldDef.getName());
        if (field != null)
        {
        	if(fieldDef.getMfeedType() == FidDef.MfeedType.ENUMERATED)
        	{
        		String enumString = field.StringData("ISO8859-1");
        		return _dictionary.expandedValueFor(fid, Integer.parseInt(enumString));
        	}
        	else
        		return field.StringData("ISO8859-1");
        }
        return null;
    }

    public int getFieldInt(short fid, int defaultValue)
    {
        FidDef fieldDef = _dictionary.getFidDef(fid);
        TibField field = getField(fieldDef.getName());
        if (field != null)
        {
            try
            {
                return field.IntData();
            }
            catch (TibException e)
            {
            }
        }
        return defaultValue;
    }

    public double getFieldDouble(short fid, double defaultValue)
    {
        FidDef fieldDef = _dictionary.getFidDef(fid);
        TibField field = getField(fieldDef.getName());
        if (field != null)
        {
            try
            {
                return field.DoubleData();
            }
            catch (TibException e)
            {
            }
        }
        return defaultValue;
    }

    public int getFieldBytes(short fid, byte[] dest, int offset)
    {
        FidDef fieldDef = _dictionary.getFidDef(fid);
        TibField field = getField(fieldDef.getName());
        if (field != null)
        {
            byte[] bytes = null;
            try
            {
                bytes = field.StringData("ISO8859-1").getBytes("ISO8859-1");
            }
            catch (UnsupportedEncodingException e)
            {// bytes == null
                return 0;
            }
            System.arraycopy(bytes, 0, dest, offset, bytes.length);
            return bytes.length;
        }

        return 0;
    }

    public boolean isOK()
    {
        MarketDataItemStatus status = _event.getStatus();
        if (status.getState() == MarketDataItemStatus.OK)
            return true;
        return false;// for closed, stale, and closed recover
    }

    public boolean isSuspect()
    {
        MarketDataItemStatus status = _event.getStatus();
        if (status.getState() == MarketDataItemStatus.CLOSED
                || status.getState() == MarketDataItemStatus.CLOSED_RECOVER
                || status.getState() == MarketDataItemStatus.STALE)
            return true;
        return false;// for ok and no change
    }

    public boolean isRename()
    {
        return _event.getMarketDataMsgType() == MarketDataItemEvent.RENAME;
    }

    public boolean isFinal()
    {
        if ((_event.getStatus().getState() == MarketDataItemStatus.CLOSED_RECOVER)
                || (_event.getStatus().getState() == MarketDataItemStatus.CLOSED))
            return true;
        return false;
    }

    public boolean isSolicited()
    {
        return _event.getMarketDataMsgType() == MarketDataItemEvent.IMAGE;
    }

    public String getItemName()
    {
        return _event.getItemName();
    }

    public String getNewItemName()
    {
        return _event.getNewItemName();
    }

    public int getMsgType()
    {
        MarketDataMessageType mdmt = _event.getMarketDataMsgType();
        if (mdmt == MarketDataItemEvent.IMAGE || mdmt == MarketDataItemEvent.UNSOLICITED_IMAGE)
        {
            return REFRESH;
        }
        else if (mdmt == MarketDataItemEvent.UPDATE || mdmt == MarketDataItemEvent.CLOSING_RUN
                || mdmt == MarketDataItemEvent.CORRECTION)
        {
            return UPDATE;
        }
        else
        {
            // MarketDataItemEvent.STATUS
            // MarketDataItemEvent.PERMISSION_DATA
            // MarketDataItemEvent.RENAME
            return STATUS;
        }
    }

    public short getDataType()
    {
        if (_event.getData() == null)
            return OMMTypes.NO_DATA;
        else if (_event.getDataFormat() == MarketDataEnums.DataFormat.ANSI_PAGE)
            return OMMTypes.ANSI_PAGE;
        else if ((_event.getDataFormat() == MarketDataEnums.DataFormat.MARKETFEED)
                || (_event.getDataFormat() == MarketDataEnums.DataFormat.QFORM)
                || (_event.getDataFormat() == MarketDataEnums.DataFormat.TIBMSG)
                || (_event.getDataFormat() == MarketDataEnums.DataFormat.IFORM))
        {
            return OMMTypes.FIELD_LIST;
        }
        else
        {
            return OMMTypes.UNKNOWN;
        }
    }

    public short getUpdateType()
    {
        MarketDataMessageType mdmt = _event.getMarketDataMsgType();
        if (mdmt == MarketDataItemEvent.UPDATE)
        {
            if (_event.getDataFormat() == MarketDataEnums.DataFormat.MARKETFEED)
            {
                int code = _headerParser.getCode();
                if (code == MarketfeedHeaderParser.CLOSING_RUN_OPCODE)
                    return RDMInstrument.Update.CLOSING_RUN;
                else if (code == MarketfeedHeaderParser.CORRECTION_OPCODE)
                    return RDMInstrument.Update.CORRECTION;
                else if (code == MarketfeedHeaderParser.VERIFY_NOSYNC_OPCODE)
                    return RDMInstrument.Update.VERIFY;
            }
        }
        else if (mdmt == MarketDataItemEvent.CLOSING_RUN)
        {
            return RDMInstrument.Update.CLOSING_RUN;
        }
        else if (mdmt == MarketDataItemEvent.CORRECTION)
        {
            return RDMInstrument.Update.CORRECTION;
        }

        return RDMInstrument.Update.UNSPECIFIED;
    }

    public String getStatusText()
    {
        MarketDataItemStatus status = _event.getStatus();
        if (status != null)
            return status.getStatusText();
        return "";
    }

    public boolean isClosed()
    {
        if ((_event.getStatus().getState() == MarketDataItemStatus.CLOSED_RECOVER)
                || (_event.getStatus().getState() == MarketDataItemStatus.CLOSED))
            return true;
        return false;
    }

    /**
     * @return Iterator for Marketfeed or QForm data that internally uses
     *         {@link com.reuters.tibmsg.TibMsg TibMsg}
     */
    public Iterator<?> fieldIterator()
    {
        if (!isFieldListDataFormat())
            throw new IllegalArgumentException("Data format is not compatible");

        byte[] data = _event.getData();
        if (data.length != 0)
        {
            try
            {
                if (_iterator == null)
                    _iterator = new TibMsgIterator(data);
                else if (_iterator.hasNext())
                {
                    // don't reuse because it is probably still in use
                    _iterator = new TibMsgIterator(data);
                }
                else
                {
                    // this reuses an iterator and can be dangerous in some
                    // usage patterns
                    _iterator.reset(data);
                }
                return _iterator;
            }
            catch (TibException e)
            {
            }
        }

        return EmptyIterator;
    }

    /**
     * @return RTL from Marketfeed header or SEQ_NO from QForm
     */
    public int getSeqNum()
    {
        switch (_event.getDataFormat())
        {
            case MarketDataEnums.DataFormat.MARKETFEED:
                return _headerParser.getRTL();
            case MarketDataEnums.DataFormat.QFORM:
            case MarketDataEnums.DataFormat.IFORM:
            case MarketDataEnums.DataFormat.TIBMSG:
            {
                try
                {
                    TibField rectype = _tibmsg.Get("SEQ_NO");
                    return rectype.IntData();
                }
                catch (TibException e)
                {
                }
            }
            default:
                return 0;
        }
    }

    /**
     * @return FLN from Marketfeed header or REC_TYPE from QForm or 0 if not
     *         field list
     */
    public int getFieldListNum()
    {
        switch (_event.getDataFormat())
        {
            case MarketDataEnums.DataFormat.MARKETFEED:
                return _headerParser.getFieldListNumber();
            case MarketDataEnums.DataFormat.QFORM:
            case MarketDataEnums.DataFormat.IFORM:
            case MarketDataEnums.DataFormat.TIBMSG:
            {
                try
                {
                    TibField rectype = _tibmsg.Get("REC_TYPE");
                    return rectype.IntData();
                }
                catch (TibException e)
                {
                }
            }
            default:
                return 0;
        }
    }
}
