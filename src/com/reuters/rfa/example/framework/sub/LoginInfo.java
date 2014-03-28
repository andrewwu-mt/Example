package com.reuters.rfa.example.framework.sub;

import java.util.Iterator;

import com.reuters.rfa.common.Handle;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMData;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMNumeric;
import com.reuters.rfa.rdm.RDMUser;

/**
 * This class store a login response information. It will be initialized and
 * update value only from the framework.
 */
public class LoginInfo
{
    private String _userName;
    private short _nameType;
    private String _appId;
    private String _position;
    private long _singleOpen;
    private long _allowSuspect;
    private long _role;
    private long _supportPauseResume;
    private String _instanceId;
    private Handle _loginHandle;
    private boolean _isBlank = true;

    /* Use internally in framework */
    LoginInfo()
    {

    }

    LoginInfo(OMMAttribInfo attribInfo)
    {
        decodeMsg(attribInfo);
    }

    void update(OMMAttribInfo attribInfo)
    {
        decodeMsg(attribInfo);
    }

    void setHandle(Handle h)
    {
        _loginHandle = h;
    }

    /**
     * Verify whether the loginInfo contain the data.
     * 
     * @return blank flag
     */
    public boolean isBlank()
    {
        return _isBlank;
    }

    /**
     * Get login handle
     * 
     * @return login handle
     */
    public Handle getHandle()
    {
        return _loginHandle;
    }

    /**
     * 
     * @return userName
     */
    public String getUserName()
    {
        return _userName;
    }

    /**
     * @return NameType
     */
    public short getNameType()
    {
        return _nameType;
    }

    /**
     * @return appId
     */
    public String getAppId()
    {
        return _appId;
    }

    /**
     * @return position
     */
    public String getPosition()
    {
        return _position;
    }

    /**
     * @return singleOpen
     */
    public long getSingleOpen()
    {
        return _singleOpen;
    }

    /**
     * @return allowSuspect
     */
    public long getAllowSuspect()
    {
        return _allowSuspect;
    }

    /**
     * @return role
     */
    public long getRole()
    {
        return _role;
    }

    /**
     * @return supportPauseResume
     */
    public long getSupportPauseResume()
    {
        return _supportPauseResume;
    }

    /**
     * @return instanceId
     */
    public String getInstanceId()
    {
        return _instanceId;
    }

    private void decodeMsg(OMMAttribInfo attribInfo)
    {
        if (attribInfo == null)
            return;
        _isBlank = false;
        _userName = attribInfo.getName();
        _nameType = attribInfo.getNameType();

        if (attribInfo.has(OMMAttribInfo.HAS_ATTRIB))
        {
            OMMElementList elementList = (OMMElementList)attribInfo.getAttrib();
            OMMElementEntry element;
            OMMData data;
            for (Iterator<?> iter = elementList.iterator(); iter.hasNext();)
            {

                element = (OMMElementEntry)iter.next();
                data = element.getData();
                if (element.getName().equals(RDMUser.Attrib.ApplicationId))
                {
                    _appId = data.toString();
                }
                else if (element.getName().equals(RDMUser.Attrib.Position))
                {
                    _position = data.toString();
                }
                else if (element.getName().equals(RDMUser.Attrib.SingleOpen))
                {
                    if (data instanceof OMMNumeric)
                    {
                        _singleOpen = ((OMMNumeric)data).toLong();
                    }
                }
                else if (element.getName().equals(RDMUser.Attrib.AllowSuspectData))
                {
                    if (data instanceof OMMNumeric)
                    {
                        _allowSuspect = ((OMMNumeric)data).toLong();
                    }
                }
                else if (element.getName().equals(RDMUser.Attrib.Role))
                {
                    if (data instanceof OMMNumeric)
                    {
                        _role = ((OMMNumeric)data).toLong();
                    }
                }
                else if (element.getName().equals(RDMUser.Attrib.InstanceId))
                {
                    _instanceId = data.toString();
                }
                else if (element.getName().equals(RDMUser.Attrib.SupportPauseResume))
                {
                    if (data instanceof OMMNumeric)
                    {
                        _supportPauseResume = ((OMMNumeric)data).toLong();
                    }
                }
            }
        }
    }
}
