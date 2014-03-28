package com.reuters.rfa.example.omm.consPerf;

import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMMsg;

/**
 * <p>
 * This class is responsible for parsing and displaying OMM messages and
 * printing statistics at display interval.
 * 
 * It uses {@link com.reuters.rfa.example.utility.GenericOMMParser GenericOMMParser}
 * to parse {@link com.reuters.rfa.omm.OMMMsg OMMMsg} response messages.
 */
public class DataDisplay
{
    boolean _printData;
    boolean _printStatistics;
    int _displayInterval;
    StringBuilder _text;
    String _fieldDictionaryFilename;
    String _enumDictionaryFilename;

    private int _totalRefreshCount;
    private int _totalStatusCount;
    private int _totalUpdateCount;
    private int _updateCount;
    private int _refreshCount;

    public DataDisplay()
    {
        _printData = CommandLine.booleanVariable("printData");
        _printStatistics = CommandLine.booleanVariable("printStatistics");
        _displayInterval = CommandLine.intVariable("displayInterval");
        _fieldDictionaryFilename = CommandLine.variable("rdmFieldDictionary");
        _enumDictionaryFilename = CommandLine.variable("enumType");
        _text = new StringBuilder();
    }

    public void init() throws DictionaryException
    {
        GenericOMMParser.initializeDictionary(_fieldDictionaryFilename, _enumDictionaryFilename);
    }

    // print update rate, total images and total status messages received so far
    // update rates can be affected by non-update messages received between
    // displayInterval
    public void printStats()
    {
        if (_printStatistics)
        {
            getStats(_text, _displayInterval);
            System.out.println(_text);
        }
    }

    public void printData(OMMMsg respMsg)
    {
        if (_printData || respMsg.getMsgType() == OMMMsg.MsgType.STATUS_RESP)
        {
            GenericOMMParser.parse(respMsg);
        }
    }

    public void updateStats(byte msgType)
    {
        if (msgType == OMMMsg.MsgType.REFRESH_RESP)
        {
            _totalRefreshCount++;
            _refreshCount++;

        }
        else if (msgType == OMMMsg.MsgType.STATUS_RESP)
        {
            _totalStatusCount++;
        }
        else if (msgType == OMMMsg.MsgType.UPDATE_RESP)
        {
            _updateCount++;
            _totalUpdateCount++;
        }
    }

    private void getStats(StringBuilder textStats, int interval)
    {
        textStats.setLength(0);
        int totalRefreshes;
        int totalStatuses;
        int totalUpdateCount;
        int refreshes;
        int updates;
        synchronized (this)
        {
            // collect all data
            totalRefreshes = _totalRefreshCount;
            totalStatuses = _totalStatusCount;
            totalUpdateCount = _totalUpdateCount;
            refreshes = _refreshCount;
            updates = _updateCount;
            // clear the counters of refreshes and updates for the last period
            _refreshCount = 0;
            _updateCount = 0;
        }

        // now format the output
        textStats.append("Total Refresh Count: ");
        textStats.append(totalRefreshes);
        textStats.append("\t");

        textStats.append("Total Update Count: ");
        textStats.append(totalUpdateCount);
        textStats.append("\t");

        textStats.append("Total Status Count: ");
        textStats.append(totalStatuses);
        textStats.append("\t");

        textStats.append("Image Rate: ");
        textStats.append((int)(refreshes / interval));
        textStats.append("\t");

        textStats.append("Update Rate: ");
        textStats.append((int)(updates / interval));
        textStats.append("\n\n");
    }
}
