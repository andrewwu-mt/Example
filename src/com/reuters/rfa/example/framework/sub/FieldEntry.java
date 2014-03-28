package com.reuters.rfa.example.framework.sub;

/**
 * Wrapper for {@link com.reuters.rfa.omm.OMMFieldEntry OMMFieldEntry} or
 * {@link com.reuters.tibmsg.TibField TibField}.
 */
public interface FieldEntry
{
    short getFieldId();

    String getData();

    short getDataType();
}
