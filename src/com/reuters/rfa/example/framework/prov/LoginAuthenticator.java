package com.reuters.rfa.example.framework.prov;

import com.reuters.rfa.omm.OMMAttribInfo;

/**
 * This interface is used by LoginMgr. It is responsible for login
 * authentication, implementor must validate information in AttribInfo which
 * obtains from incoming login request.
 * 
 * @see LoginMgr
 */
public interface LoginAuthenticator
{
    /**
     * The authentication method, this method will be called by
     * {@link com.reuters.rfa.example.framework.prov.LoginMgr LoginMgr} when the
     * login request is processing.
     * 
     * @param ai Login's OMMAttribInfo
     * @return true if the request is permitted ,false if the request is denied.
     */
    public boolean authenticate(OMMAttribInfo ai);
}
