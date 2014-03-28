package com.reuters.rfa.example.framework.idn;

/**
 * Client interface for {@link RefChain} callbacks.
 * 
 */
public interface RefChainClient
{
    void processRefChainComplete(RefChain refChain);

    void processRefChainError(RefChain refChain, String text);
}
