package com.reuters.rfa.example.framework.sub;

import java.util.Iterator;
import java.util.NoSuchElementException;

public interface NormalizedEvent
{
    /**
     * @return byte[] for the payload of the event
     * @throws IllegalArgumentException if event is incompatible
     */
    byte[] getPayloadBytes();

    /**
     * @return Permission data for this event, or null if it does not exist
     */
    byte[] getPermissionData();

    /**
     * @return String for the field name or null if the field name is not
     *         present
     * @throws IllegalArgumentException if event is incompatible or contains
     *             incompatible data
     */
    String getFieldString(short fieldId) throws IllegalArgumentException;

    /**
     * @return int for the field name, 0 if the field name is not present
     * @throws IllegalArgumentException if event is incompatible or contains
     *             incompatible data
     */
    int getFieldInt(short fieldId, int defaultValue) throws IllegalArgumentException;

    /**
     * @return double for the field name, 0 if the field name is not present
     * @throws IllegalArgumentException if event is incompatible or contains
     *             incompatible data
     */
    double getFieldDouble(short fieldId, double defaultValue) throws IllegalArgumentException;

    /**
     * @return number of bytes written
     * @throws IllegalArgumentException if event is incompatible or contains
     *             incompatible data
     */
    int getFieldBytes(short fieldId, byte[] dest, int offset) throws IllegalArgumentException;

    /**
     * @return {@link #REFRESH}, {@link #UPDATE}, or {@link #STATUS}
     */
    int getMsgType();

    /**
     * @return value from {@link com.reuters.rfa.rdm.RDMInstrument.Update} (when
     *         {@link #getMsgType()} is {@link #UPDATE})
     */
    short getUpdateType();

    /**
     * @return item name, or null if one is not present
     */
    String getItemName();

    /**
     * @return new name for item. Only valid if {@link #isRename()} is
     *         <code>true</code>
     */
    String getNewItemName();

    /**
     * @return if refresh is solicited.
     */
    boolean isSolicited();

    /**
     * @return if {@link #STATUS} is a rename
     */
    boolean isRename();

    /**
     * @return if the state is OK. Note that unspecified is not OK or suspect.
     */
    boolean isOK();

    /**
     * @return if the state is suspect/stale. Note that unspecified is not OK or
     *         suspect.
     */
    boolean isSuspect();

    /**
     * @return if the state closed, closed recover, or final redirect
     */
    boolean isClosed();

    /**
     * @return if this stream is closed, closed recover, final redirect, or a
     *         final nonstreaming response
     */
    boolean isFinal();

    /**
     * @return status text or an empty string if it is not present
     */
    String getStatusText();

    /**
     * @return sequence number
     */
    int getSeqNum();

    /**
     * @return field list number
     */
    int getFieldListNum();

    /**
     * @return value from {@link com.reuters.rfa.omm.OMMTypes OMMTypes}.
     *         Marketfeed, QForm, TIBMsg self-described, and IFORM return as
     *         {@link com.reuters.rfa.omm.OMMTypes#FIELD_LIST FIELD_LIST}.
     */
    short getDataType();

    /**
     * @return Iterator for Marketfeed, QForm, or OMM FieldList
     * @throws IllegalArgumentException if OMM data is not a FieldList
     * @throws RuntimeException that contains TibException
     */
    public Iterator<?> fieldIterator();

    static final int REFRESH = 1;
    static final int UPDATE = 2;
    static final int STATUS = 3;

    public static Iterator<?> EmptyIterator = new Iterator<Object>()
    {
        public boolean hasNext()
        {
            return false;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        public Object next()
        {
            throw new NoSuchElementException();
        }
    };
}
