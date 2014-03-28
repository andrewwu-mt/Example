package com.reuters.rfa.example.framework.sub;

public class MarketfeedHeaderParser
{
    static final byte FS = '\034';
    static final byte GS = '\035';
    static final byte RS = '\036';
    static final byte US = '\037';

    public static final int CLOSING_RUN_OPCODE = 312;
    public static final int UPDATE_OPCODE = 316;
    public static final int CORRECTION_OPCODE = 317;
    public static final int VERIFY_OPCODE = 318;
    public static final int VERIFY_NOSYNC_OPCODE = 318;
    public static final int RESPONSE_OPCODE = 340;

    public static boolean isDelimiter(byte b)
    {
        return FS <= b && b <= US;
    }

    static private short atos(byte[] in, int fromIndex, int byteLen)
    {
        int i = fromIndex;
        int endIndex = i + byteLen;
        int sign = 1;
        int r = 0;

        while (i < endIndex && Character.isWhitespace((char)in[i]))
        {
            i++;
        }

        if (i < endIndex && in[i] == '-')
        {
            sign = -1;
            i++;
        }
        else if (i < endIndex && in[i] == '+')
        {
            i++;
        }

        while (i < endIndex)
        {
            char ch = (char)in[i];
            if ('0' <= ch && ch < '0' + 1)
                r = r * 10 + ch - '0';
            i++;
        }
        return (short)(r * sign);
    }

    public MarketfeedHeaderParser()
    {
    }

    public boolean hasError()
    {
        return _error || _buf == null;
    }

    public String errorText()
    {
        return _errorText;
    }

    public void setBuffer(byte[] buf)
    {
        _buf = buf;
        clear();
        if (_buf != null)
            parseHeader();
    }

    public short getFieldListNumber()
    {
        return _fieldListNumber;
    }

    public short getRTL()
    {
        return _rtl;
    }

    public int getCode()
    {
        return _code;
    }

    public void clear()
    {
        _error = false;
        _errorText = null;
        _position = 0;
        _fieldListNumber = 0;
        _code = 0;
        _rtl = 0;
    }

    private void parseHeader()
    {
        if (_buf[0] != FS)
        {
            _error = true;
            _errorText = "MISSING_FS";
            return;
        }

        _position = 1;
        int start = _position;
        readRequiredValue(FS, "MISSING_OPC");
        if (_error)
            return;
        _code = atos(_buf, start, _position - start);

        switch (_code)
        {
            case UPDATE_OPCODE:
            case CORRECTION_OPCODE:
            case CLOSING_RUN_OPCODE:
                start = _position;
                readOptionalValue(US); // TAG
                if (_error)
                    return;

                start = _position;
                readRequiredValue(GS, "MISSING_RIC"); // RIC
                if (_error)
                    return;

                start = _position;
                readOptionalValue(US); // RTL
                if (_error)
                    return;
                _rtl = atos(_buf, start, _position - start);

                break;

            case RESPONSE_OPCODE:
                start = _position;
                readOptionalValue(US); // TAG
                if (_error)
                    return;

                readOptionalValue(RS); // ILA
                if (_error)
                    return;

                start = _position;
                readRequiredValue(GS, "MISSING_RIC"); // RIC
                if (_error)
                    return;

                start = _position;
                readOptionalValue(RS); // R_STATUS
                if (_error)
                    return;
                _code = atos(_buf, start, _position - start);

                readRequiredValue(US, "MISSING_FLN"); // FLN
                if (_error)
                    return;
                _fieldListNumber = atos(_buf, start, _position - start);

                start = _position;
                readRequiredValue(US, "MISSING_RTL"); // RTL
                if (_error)
                    return;
                _rtl = atos(_buf, start, _position - start);
                break;

            case VERIFY_OPCODE:
                start = _position;
                readOptionalValue(US); // TAG
                if (_error)
                    return;

                start = _position;
                readRequiredValue(GS, "MISSING_RIC"); // RIC
                if (_error)
                    return;

                if (readOptionalValue(RS)) // VER_SUB
                    _code = CORRECTION_OPCODE;
                if (_error)
                    return;

                start = _position;
                readRequiredValue(US, "MISSING_FLN"); // FLN
                if (_error)
                    return;
                _fieldListNumber = atos(_buf, start, _position - start);

                start = _position;
                readRequiredValue(US, "MISSING_RTL"); // RTL
                if (_error)
                    return;
                _rtl = atos(_buf, start, _position - start);
                break;

            default:
                _error = true;
                _errorText = "MISSING_OPC";
                return;
        }

        if (_buf[_position] != RS)
        {
            _error = true;
            _errorText = "MISSING_RSD";
            return;
        }
    }

    private boolean readOptionalValue(byte delim)
    {
        if (_buf[_position] == delim)
        {
            _position++;
            readValue();
            return !_error;
        }
        return false;
    }

    private void readRequiredValue(byte delim, String errorText)
    {
        if (_buf[_position] != delim)
        {
            _error = true;
            _errorText = errorText;
            return;
        }
        _position++;
        readValue();
    }

    private void readValue()
    {
        int offset = _position;
        while (offset < _buf.length)
        {
            if (isDelimiter(_buf[offset]))
            {
                _position = offset;
                return;
            }
            offset++;
        }
        if (offset == _buf.length)
        {
            _errorText = "MISSING_DEL";
            _error = true;
        }
        _position = offset;
    }

    /*
     * private void checkDelimiter(byte delimiter, String text) { if
     * (_buf[_position] != delimiter) { _error = true; _errorText = text; } }
     */
    byte[] _buf;
    int _position;
    boolean _error;
    String _errorText;
    short _fieldListNumber;
    short _code;
    short _rtl;
}
