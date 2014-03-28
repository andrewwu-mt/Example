package com.reuters.rfa.example.omm.idn.newsviewer;

import com.reuters.rfa.example.framework.chain.SegmentChain;
import com.reuters.rfa.example.framework.chain.SegmentChainClient;
import com.reuters.rfa.example.framework.sub.SubAppContext;

/**
 * This is a SegmentChainClient class that handle callback for story data.
 * 
 * @see SegmentChainClient
 */
public class NewsStoryClient implements SegmentChainClient
{
    String _serviceName;
    SubAppContext _appContext;
    NewsStoryViewer _viewer;
    SegmentChain _segchain;
    boolean _neverTabular;

    public NewsStoryClient(NewsStoryViewer viewer, SubAppContext appContext, String serviceName,
            String lang_ind, String pnac)
    {
        _serviceName = serviceName;
        _appContext = appContext;
        _viewer = viewer;
        _segchain = new SegmentChain(appContext, this, pnac);
        _neverTabular = lang_ind.equals("KO") || lang_ind.equals("JP") || lang_ind.equals("TH")
                || lang_ind.equals("ZH");
    }

    public void cancel()
    {
        _segchain.cleanup();
    }

    public void processUpdate(SegmentChain chain)
    {
        if (_viewer == null)
            return;

        String type = chain.tabText();
        // FUTURE only set tabular if the story does not need international
        // character fonts
        if (type != null && type.equals("T") && !_neverTabular)
            _viewer.setTabular(true);
        else
            // type.equals("X") or _neverTabular
            _viewer.setTabular(false);

        String segText = chain.currentSegText();
        _viewer.appendStory(segText);
    }

    public void processComplete(SegmentChain chain)
    {
        // All segments of chain was completed.
    }

    public void processError(SegmentChain chain)
    {
        System.err.println(chain.errorText());
    }
}
