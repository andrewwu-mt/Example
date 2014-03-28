package com.reuters.rfa.example.framework.chain;

/**
 * SegmentChainClient provides the callback mechanism for SegmentChain to notify
 * its client that an error occurs or the segment chain is updated or completed.
 * 
 * @see SegmentChain
 */
public interface SegmentChainClient
{
    /**
     * The callback method called when an error occurred.
     * 
     */
    void processError(SegmentChain segmentChain);

    /**
     * The callback method called after all segments of chain was completed.
     * 
     */
    void processComplete(SegmentChain segmentChain);

    /**
     * The callback method called when receiving the next segment of chain.
     * 
     */
    void processUpdate(SegmentChain segmentChain);
}
