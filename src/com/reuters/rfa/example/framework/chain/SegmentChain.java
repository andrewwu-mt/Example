package com.reuters.rfa.example.framework.chain;

import java.io.UnsupportedEncodingException;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.framework.sub.NormalizedEvent;
import com.reuters.rfa.example.framework.sub.SubAppContext;

/**
 * <p>
 * This class provides several operations that application must perform for
 * consuming and managing the segment chain data such as news story.
 * </p>
 * 
 * This class is responsible for the following actions:
 * <ul>
 * <li>Request the first segment and find the next link from NEXT_LR field.
 * <li>Request the next link until no next link.
 * <li>Notify a {@link SegmentChainClient client} when receiving each segment
 * data, completing all segments and founding error.
 * </ul>
 * 
 * @see SegmentChainClient
 */
public class SegmentChain implements Client
{
    static boolean FidsInitialized = false;
    static boolean initFidsFail = false;
    static short SegText;
    static int SegTextLength;
    static short NextLink;
    static short NextTake;
    static short PreviousLink;
    static short PreviousTake;
    static short NumberOfTakes;
    static short CurrentTake;
    static short TabText;

    protected SubAppContext _subContext;
    protected Handle _handle;
    String _currentSegText;
    boolean _initialized;
    boolean _error;
    boolean _complete;
    int _limit;
    int _count;
    String _errorText;
    String _symbol;
    String _nextLink;
    String _nextTake;
    String _previousLink;
    String _previousTake;
    String _tabText;

    SegmentChainClient _client;

    public SegmentChain(SubAppContext subContext, SegmentChainClient client, String symbol)
    {
        _subContext = subContext;
        _symbol = symbol;
        _limit = -1;
        _count = 0;
        _complete = false;
        _error = false;
        _initialized = false;
        _currentSegText = "";
        _client = client;

        initializeFids(subContext.getFieldDictionary());

        if (!FidsInitialized)
        {
            initFidsFail = true;
            _error = true;
            _complete = true;
            _errorText = "Initialize field id from dictionary fail";
            System.err.println("Initialize field id from dictionary fail");
            return;
        }

        if (symbol == null || symbol.equals(""))
        {
            _error = true;
            _complete = true;
            _errorText = "Empty record name";
            System.err.println("Empty record name");
            return;
        }

        setRecord(symbol);

        _initialized = true;
    }

    public void cleanup()
    {
        dropRecord();
    }

    public void setLimit(int limit)
    {
        _limit = limit;
    }

    public boolean complete()
    {
        return _complete;
    }

    public boolean error()
    {
        return _error;
    }

    public int count()
    {
        return _count;
    }

    public int limit()
    {
        return _limit;
    }

    public String errorText()
    {
        return _errorText;
    }

    public String symbol()
    {
        return _symbol;
    }

    public String tabText()
    {
        return _tabText;
    }

    public String nextTake()
    {
        return _nextTake;
    }

    public String previousTake()
    {
        return _previousTake;
    }

    public String currentSegText()
    {
        return _currentSegText;
    }

    public void setClient(SegmentChainClient client)
    {
        _client = client;
    }

    protected void setRecord(String rec)
    {
        _handle = _subContext.register(this, _subContext.getServiceName(), rec, false);
    }

    protected void dropRecord()
    {
        if (_handle != null)
        {
            _subContext.unregister(_handle);
            _handle = null;
        }
    }

    protected void indicateComplete()
    {
        _complete = true;
        if (_client != null)
            _client.processComplete(this);
    }

    protected void indicateUpdate()
    {
        if (_client != null)
            _client.processUpdate(this);
    }

    static synchronized void initializeFids(FieldDictionary dictionary)
    {
        if (FidsInitialized || initFidsFail)
        {
            return;
        }

        FidDef def = dictionary.getFidDef("SEG_TEXT");
        if (def == null)
        {
            return;
        }
        SegText = def.getFieldId();
        SegTextLength = dictionary.isOMM() ? def.getMaxOMMLength() : def.getMaxMfeedLength();

        def = dictionary.getFidDef("NEXT_LR");
        if (def == null)
        {
            return;
        }
        NextLink = def.getFieldId();

        def = dictionary.getFidDef("SEG_FORW");
        if (def == null)
        {
            return;
        }
        NextTake = def.getFieldId();

        def = dictionary.getFidDef("PREV_LR");
        if (def == null)
        {
            return;
        }
        PreviousLink = def.getFieldId();

        def = dictionary.getFidDef("SEG_BACK");
        if (def == null)
        {
            return;
        }
        PreviousTake = def.getFieldId();

        def = dictionary.getFidDef("NO_TAKES");
        if (def == null)
        {
            return;
        }
        NumberOfTakes = def.getFieldId();

        def = dictionary.getFidDef("CUR_TAKE");
        if (def == null)
        {
            return;
        }
        CurrentTake = def.getFieldId();

        def = dictionary.getFidDef("TABTEXT");
        if (def == null)
        {
            return;
        }
        TabText = def.getFieldId();

        FidsInitialized = true;
    }

    public void processEvent(Event event)
    {
        NormalizedEvent nevent = _subContext.getNormalizedEvent(event);

        if (nevent.getMsgType() == NormalizedEvent.STATUS)
        {
            if (nevent.isFinal())
            {
                indicateError(nevent, nevent.getStatusText());
                return;
            }
        }
        else
        {
            byte[] bytes = new byte[SegTextLength];
            int n = nevent.getFieldBytes(SegText, bytes, 0);
            if (n == 0)
            {
                _currentSegText = "";
                indicateError(nevent, nevent.getItemName() + " is missing SEG_TEXT field");
                return;
            }
            try
            {
                _currentSegText = new String(bytes, "RMTES");
            }
            catch (UnsupportedEncodingException e)
            {
                _currentSegText = new String(bytes);
            }
        }

        _previousTake = nevent.getFieldString(PreviousTake);
        _previousLink = nevent.getFieldString(PreviousLink);

        _count++;
        String tabText = nevent.getFieldString(TabText);
        if (tabText != null)
            _tabText = tabText;

        if (_initialized)
            indicateUpdate();

        _nextLink = nevent.getFieldString(NextLink);
        _nextLink = (_nextLink == null) ? "" : _nextLink.trim();
        _nextTake = nevent.getFieldString(NextTake);

        if ((_limit > 0) && (_count >= _limit))
        {
            indicateComplete();
            return;
        }

        if (!_nextLink.equals(""))
        {
            dropRecord();
            setRecord(_nextLink);
            return;
        }

        indicateComplete();
    }

    private void indicateError(NormalizedEvent nevent, String text)
    {
        _errorText = text;
        _error = true;
        _complete = true;
        if (_initialized && (_client != null))
            _client.processError(this);
        dropRecord();
    }
}
