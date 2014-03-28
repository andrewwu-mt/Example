package com.reuters.rfa.example.framework.prov;

/**
 * StreamItem is abstract class for item streams which be used to send data to a
 * client.
 * 
 */
public abstract class StreamItem
{

    /**
     * Closes this stream and releases any resources associated with this
     * stream.
     * 
     */
    public abstract void close();

    /**
     * Returns the message model type for data of this stream. The return value
     * is one of the types defined in
     * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes}.
     * 
     * @return message model type for data of this stream.
     */
    public abstract short getMsgModelType();
}
