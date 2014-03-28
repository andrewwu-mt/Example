package com.reuters.rfa.example.omm.hybrid;

import com.reuters.rfa.common.EventQueue;
import com.reuters.rfa.session.omm.OMMConsumer;

/**
 * Represents a class that has {@link com.reuters.rfa.common.EventQueue
 * EventQueue} and {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer}
 * 
 */

public interface RequestClient
{
    EventQueue getEventQueue();

    OMMConsumer getConsumer();
}
