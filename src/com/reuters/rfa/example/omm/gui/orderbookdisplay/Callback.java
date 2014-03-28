package com.reuters.rfa.example.omm.gui.orderbookdisplay;

public interface Callback
{
    public void notifyStatus(String status);

    public void processInsertOrder(String orderId, String orderSz, String orderPr, String QteTime,
            String orderSide);

    public void processUpdateOrder(String orderId, String orderSz, String orderPr, String QteTime);

    public void processDeleteOrder(String orderId);

    public void processSummary(String currency, String trdUnits, String exchId, String mktState);

    public void processClear();
}
