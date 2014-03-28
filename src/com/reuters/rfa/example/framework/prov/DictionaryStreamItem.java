package com.reuters.rfa.example.framework.prov;

import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;

/**
 * DictionaryStreamItem is a stream item for Dictionary domain.
 * 
 * This class is responsible for encoding the Open Message Model data for
 * Dictionary domain.
 * 
 */
public abstract class DictionaryStreamItem extends StreamItem
{
    String _name;
    protected DictionaryMgr _dictMgr;

    public DictionaryStreamItem(DictionaryMgr mgr, String name)
    {
        _name = name;
        _dictMgr = mgr;
        _dictMgr.addDictionaryStreamItem(name, this);
    }

    /**
     * Encodes Dictionary message.
     * 
     * @param filter ID values usually used for selecting
     *            {@link com.reuters.rfa.omm.OMMFilterEntry OMMFilterEntry}.
     * @return OMMMsg encodeed Dictionary message
     */
    public OMMMsg encodeMsg(int filter, boolean solicited)
    {
        OMMPool pool = _dictMgr.getPool();
        OMMEncoder enc = pool.acquireEncoder();
        enc.initialize(OMMTypes.MSG, 640000);
        OMMMsg msg = pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REFRESH_RESP);
        msg.setMsgModelType(RDMMsgTypes.DICTIONARY);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH_COMPLETE);
        
        if (solicited)
            msg.setRespTypeNum(OMMMsg.RespType.SOLICITED);
        else
            msg.setRespTypeNum(OMMMsg.RespType.UNSOLICITED);
        
        msg.setState(OMMState.Stream.OPEN, OMMState.Data.OK, OMMState.Code.NONE, "");
        msg.setItemGroup(1);
        OMMAttribInfo attribInfo = pool.acquireAttribInfo();
        attribInfo.setServiceName(_dictMgr.getServiceName());
        attribInfo.setName(_name);
        attribInfo.setFilter(filter); // Specifies all of the normally needed
                                      // data will be sent.
        msg.setAttribInfo(attribInfo);
        enc.encodeMsgInit(msg, OMMTypes.NO_DATA, OMMTypes.SERIES); // Data is
                                                                   // Series.
        encodeSeries(enc, filter);
        pool.releaseMsg(msg);
        pool.releaseAttribInfo(attribInfo);
        OMMMsg encmsg = (OMMMsg)enc.acquireEncodedObject();
        pool.releaseEncoder(enc);
        return encmsg;
    }

    /**
     * Encodes Dictionary data.
     * 
     * @param enc partial encoded message.
     * @param filter ID values usually used for selecting
     *            {@link com.reuters.rfa.omm.OMMFilterEntry OMMFilterEntry}.
     */
    abstract public void encodeSeries(OMMEncoder enc, int filter);

    public void close()
    {

    }

    public short getMsgModelType()
    {
        return RDMMsgTypes.DICTIONARY;
    }
}
