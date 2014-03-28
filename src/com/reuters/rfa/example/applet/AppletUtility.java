package com.reuters.rfa.example.applet;

import java.applet.Applet;
import com.reuters.rfa.config.ConfigDb;
import com.reuters.rfa.example.utility.CommandLine;

/**
 * Utility for an applet application.
 * 
 */
public class AppletUtility
{
    /**
     * Loads applet parameters into CommandLine
     * 
     * @param applet An applet object
     * @param paramName Array of parameter name
     */
    public static void loadAppletParameter(Applet applet, String[] paramName)
    {
        for (int i = 0; i < paramName.length; i++)
        {
            String paramValue = applet.getParameter(paramName[i]);
            if (paramValue != null)
            {
                CommandLine.changeDefault(paramName[i], paramValue);
            }
        }
    }

    /**
     * Loads applet parameters into configDb. The format of parameter is as
     * follows: config1=value1 | config2=value2 | config3=value3
     * 
     * @param applet An applet object
     * @param paramName Parameter name
     * @param configDb Configuration database
     */
    public static void loadConfigDbParameter(Applet applet, String paramName, ConfigDb configDb)
    {
        String config = applet.getParameter(paramName);
        if (config != null && configDb != null)
        {
            String[] configArray = config.split("\\|");

            for (int i = 0; i < configArray.length; i++)
            {
                String[] tmp = configArray[i].split("=");
                if (tmp.length == 2)
                {
                    configDb.addVariable(tmp[0].trim(), tmp[1].trim());
                }
            }
        }
    }
}
