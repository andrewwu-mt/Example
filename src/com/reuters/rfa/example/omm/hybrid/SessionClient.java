package com.reuters.rfa.example.omm.hybrid;

import com.reuters.rfa.common.Client;

/**
 * Represents a session between a provider and a consumer
 * 
 */
public interface SessionClient extends Client
{
    void init();

    void cleanup();
}
