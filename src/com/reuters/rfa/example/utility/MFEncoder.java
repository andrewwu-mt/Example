package com.reuters.rfa.example.utility;

import java.util.HashMap;

/**
 * Marketfeed encoder class. Could be made more efficient.
 * 
 * Usage: 1) Set each parameter for constructing header 2) Add each field by
 * using appendField method 3) use makeBuffer for returning MF message with
 * bytes[] format
 * 
 * Note: To change or remove field, use chageFieldValue or removeField method To
 * commit changing header parameters or fields in message ,use makeBuffer again
 * to get new changed message.
 * 
 */

public class MFEncoder
{
    // Parameters for constructing MF Header
    // some of these may not be used in some msgType
    private MessageType _msgType;
    private String _tag;
    private String _ricName;
    private String _verSub;
    private String _rStatus;
    private String _fieldListNo;
    private String _rtl;
    // private String _deltaRTL; // TODO not needed?

    // Internal parameters used in this class
    private StringBuilder _buffer;
    private StringBuilder _header;
    private HashMap<String, MessageType> _msgTypeList;
    private int _headerLength;

    // Special characters
    private static final char FS = '\034';
    private static final char GS = '\035';
    private static final char RS = '\036';
    private static final char US = '\037';

    // Message type for identifying each message
    public static final MessageType SnapResponse = new MessageType("340");
    public static final MessageType Verify = new MessageType("318");
    public static final MessageType Update = new MessageType("316");
    public static final MessageType Correction = new MessageType("317");
    public static final MessageType ClosingRun = new MessageType("312");

    static class MessageType
    {
        private String _msgType;

        public MessageType(String strType)
        {
            _msgType = strType;
        }

        public String toString()
        {
            return _msgType;
        }
    }

    public MFEncoder()
    {
        _buffer = new StringBuilder(1500);
        _header = new StringBuilder(1500);
        _msgTypeList = new HashMap<String, MessageType>();

        // Mapping for setMessageType(String)
        _msgTypeList.put(SnapResponse.toString(), SnapResponse);
        _msgTypeList.put(Verify.toString(), Verify);
        _msgTypeList.put(Update.toString(), Update);
        _msgTypeList.put(Correction.toString(), Correction);
        _msgTypeList.put(ClosingRun.toString(), ClosingRun);

        intialize();
    }

    private void intialize()
    {
        // init all parameters used in header
        _msgType = null;
        _tag = "";
        _ricName = "";
        _verSub = "";
        _rStatus = "";
        _fieldListNo = "";
        _rtl = "";
        // _deltaRTL = "";

        // init internal variables
        _header.setLength(0);
        _buffer.setLength(0);
        _headerLength = 0;

    }

    public void setMessageType(MessageType msgType)
    {
        _msgType = msgType;
    }

    public void setMessageType(String msgType)
    {
        _msgType = (MessageType)_msgTypeList.get(msgType);
    }

    public void setTag(String tag)
    {
        _tag = tag;
    }

    public void setRicName(String ricName)
    {
        _ricName = ricName;
    }

    public void setRStatus(String rStatus)
    {
        _rStatus = rStatus;
    }

    public void setFieldListNumber(String fieldListNo)
    {
        _fieldListNo = fieldListNo;
    }

    public void setRTL(String rtl)
    {
        _rtl = rtl;
    }

    public void setDeltaRTL(String deltaRTL)
    {
        // _deltaRTL = deltaRTL;
    }

    public void appendField(String fid, String value)
    {
        // Allow append field after making buffer
        if (_buffer.length() != 0 && _buffer.charAt(_buffer.length() - 1) == FS)
            _buffer.deleteCharAt(_buffer.length() - 1);
        _buffer.append(RS);
        _buffer.append(fid);
        _buffer.append(US);
        _buffer.append(value);
    }

    public void removeField(String fid)
    {
        int startFieldPos, endValPos;
        startFieldPos = _buffer.indexOf(String.valueOf(RS) + fid) + 1;
        endValPos = _buffer.indexOf(String.valueOf(RS), startFieldPos);
        // If can not find RS then endValPos is the end of _buffer
        if (endValPos == -1)
        {
            endValPos = _buffer.length();
        }
        _buffer.delete(startFieldPos - 1, endValPos);
    }

    public void changeFieldValue(String fid, String newValue)
    {
        int startFieldPos, startValPos, endValPos;
        startFieldPos = _buffer.indexOf(String.valueOf(RS) + fid) + 1;
        startValPos = _buffer.indexOf(String.valueOf(US), startFieldPos) + 1;
        endValPos = _buffer.indexOf(String.valueOf(RS), startValPos);
        // If can not find RS then endValPos is the end of _buffer
        if (endValPos == -1)
        {
            endValPos = _buffer.length();
        }
        _buffer.replace(startValPos, endValPos, newValue);

    }

    private void validateElements()
    {

        if (_msgType == null)
            throw new IllegalArgumentException("Message type is not set");
    }

    private void makeSnapResponseHeader()
    {
        _header.append(FS);
        _header.append(_msgType.toString());
        _header.append(US);
        _header.append(_tag);
        _header.append(GS);
        _header.append(_ricName);

        if (_rStatus != "")
        {
            _header.append(RS);
            _header.append(_rStatus);
        }
        _header.append(US);
        _header.append(_fieldListNo);
        _header.append(US);
        _header.append(_rtl);
    }

    private void makeUpdateHeader()
    {
        _header.append(FS);
        _header.append(_msgType.toString());
        _header.append(US);
        _header.append(_tag);
        _header.append(GS);
        _header.append(_ricName);
        _header.append(US);
        _header.append(_rtl);
    }

    private void makeCorrectionHeader()
    {
        _header.append(FS);
        _header.append(_msgType.toString());
        _header.append(US);
        _header.append(_tag);
        _header.append(GS);
        _header.append(_ricName);
        _header.append(US);
        _header.append(_rtl);
    }

    private void makeClosingRunHeader()
    {
        _header.append(FS);
        _header.append(_msgType.toString());
        _header.append(US);
        _header.append(_tag);
        _header.append(GS);
        _header.append(_ricName);
        _header.append(US);
        _header.append(_rtl);
    }

    private void makeVerifyHeader()
    {
        _header.append(FS);
        _header.append(_msgType.toString());
        _header.append(US);
        _header.append(_tag);
        _header.append(GS);
        _header.append(_ricName);

        if (_verSub != "")
        {
            _header.append(RS);
            _header.append(_verSub);
        }
        _header.append(US);
        _header.append(_fieldListNo);
        _header.append(US);
        _header.append(_rtl);
    }

    /*
     * Not used loccally private void makeAggregateUpdateHeader() {
     * _header.append(FS); _header.append(_msgType.toString());
     * _header.append(GS); _header.append(_ricName); if(_deltaRTL != "") {
     * _header.append(US); _header.append(_deltaRTL); } _header.append(US);
     * _header.append(_rtl); }
     */
    private void makeHeader()
    {
        _header.setLength(0);

        if (_msgType == SnapResponse)
        {
            makeSnapResponseHeader();
        }
        else if (_msgType == Update)
        {
            makeUpdateHeader();
        }
        else if (_msgType == Correction)
        {
            makeCorrectionHeader();
        }
        else if (_msgType == ClosingRun)
        {
            makeClosingRunHeader();
        }
        else if (_msgType == Verify)
        {
            makeVerifyHeader();
        }

        _headerLength = _header.length();
    }

    // Concatenate all elements previosly set into buffer
    public byte[] makeBuffer()
    {
        // if previous buffer has header, delete old header for making new
        // header
        if (_buffer.length() != 0)
            _buffer.delete(0, _headerLength);
        validateElements();
        makeHeader();
        _buffer.insert(0, _header);

        if (_buffer.charAt(_buffer.length() - 1) != FS)
            _buffer.append(FS);
        return (_buffer.toString()).getBytes();

    }

    // Clear all to reuse buffer
    public void clearAll()
    {
        intialize();
    }

    static public void printBuffer(byte[] buf)
    {
        for (int i = 0; i < buf.length; i++)
        {
            if (buf[i] <= 31)
            {
                System.out.print("{" + buf[i] + "}");
            }
            else
            {
                System.out.print((char)buf[i]);
            }

        }
        System.out.println();
    }

}
