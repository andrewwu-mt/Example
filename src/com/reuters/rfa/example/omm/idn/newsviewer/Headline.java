package com.reuters.rfa.example.omm.idn.newsviewer;

/**
 * This class is used to keep headline data.
 * 
 */
public class Headline
{
    public Headline()
    {
    }

    public String toString()
    {
        return _option + " | " + _lang + " | " + _storyTime + " | " + _text;
    }

    public String getText()
    {
        return _text;
    }

    public void setText(String headline)
    {
        this._text = headline;
    }

    public byte[] getTextbytes()
    {
        return _textbytes;
    }

    public void setTextbytes(byte[] headlinebytes)
    {
        this._textbytes = headlinebytes;
    }

    public String getLang()
    {
        return _lang;
    }

    public void setLang(String lang)
    {
        this._lang = lang;
    }

    public String getPnac()
    {
        return _pnac;
    }

    public void setPnac(String pnac)
    {
        this._pnac = pnac;
    }

    public String getAttribution()
    {
        return _attribution;
    }

    public void setAttribution(String attribution)
    {
        this._attribution = attribution;
    }

    public String getCompanyCodes()
    {
        return _companyCodes;
    }

    public void setCompanyCodes(String codes)
    {
        _companyCodes = codes;
    }

    public String getProdCodes()
    {
        return _prodCodes;
    }

    public void setProdCodes(String codes)
    {
        _prodCodes = codes;
    }

    public String getStoryDate()
    {
        return _storyDate;
    }

    public void setStoryDate(String date)
    {
        _storyDate = date;
    }

    public String getStoryTime()
    {
        return _storyTime;
    }

    public void setStoryTime(String time)
    {
        _storyTime = time;
    }

    public String getTopicCodes()
    {
        return _topicCodes;
    }

    public void setTopicCodes(String codes)
    {
        _topicCodes = codes;
    }

    public void setOption(byte b)
    {
        _option = b;
    }

    public byte option()
    {
        return _option;
    }

    byte _option;

    byte[] _textbytes;
    String _text;
    String _pnac;
    String _storyDate;
    String _storyTime;
    String _attribution;
    String _topicCodes;
    String _companyCodes;
    String _prodCodes;
    String _lang;
}
