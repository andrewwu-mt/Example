package com.reuters.rfa.example.omm.domainServer;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import com.reuters.rfa.example.framework.prov.LoginAuthenticator;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMAttribInfo;

/**
 * Simple implementation of LoginAuthenticator, the list of authorized user will
 * be passed via CommandLine argument "validUsers",if this argument didn't
 * specify, the default value "all" will be used which will allow every login
 * request.
 * 
 */
public class LoginAuthenticatorImpl implements LoginAuthenticator
{
    final Set<String> _authorizedUsers = new HashSet<String>();

    public LoginAuthenticatorImpl()
    {
        String userList = CommandLine.variable("validUsers");
        StringTokenizer validUsers = new StringTokenizer(userList, ",");
        String userName = null;
        while (validUsers.hasMoreTokens())
        {
            userName = validUsers.nextToken().trim();
            _authorizedUsers.add(userName);
        }
    }

    public boolean authenticate(OMMAttribInfo ai)
    {
        String userName = ai.getName();

        if (_authorizedUsers.contains("all") || _authorizedUsers.contains(userName))
        {
            return true;
        }

        return false;
    }
}
