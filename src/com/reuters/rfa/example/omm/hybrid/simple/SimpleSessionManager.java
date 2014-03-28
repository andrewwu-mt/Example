package com.reuters.rfa.example.omm.hybrid.simple;

import com.reuters.rfa.common.Handle;
import com.reuters.rfa.dictionary.DictionaryException;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.omm.hybrid.HybridDemo;
import com.reuters.rfa.example.omm.hybrid.OMMMsgReencoder;
import com.reuters.rfa.example.omm.hybrid.ProviderServer;
import com.reuters.rfa.example.omm.hybrid.SessionClient;
import com.reuters.rfa.example.omm.hybrid.SessionManager;
import com.reuters.rfa.example.utility.CommandLine;

/**
 * Used for creating {@link SimpleSessionClient SimpleSessionClient}
 * 
 * 
 */
public class SimpleSessionManager extends SessionManager
{

    private final FieldDictionary _dictionary;
    private final String _instanceName;

    public SimpleSessionManager(HybridDemo hybridDemo)
    {
        super(hybridDemo);
        _dictionary = FieldDictionary.create();
        _instanceName = "[SimpleSessionManager]";
    }

    protected SessionClient createSessionClient(Handle sessionHandle)
    {
        return new SimpleSessionClient(this, sessionHandle);
    }

    public void init(ProviderServer client)
    {
        super.init(client);

        String fieldDictionary = CommandLine.variable("rdmFieldDictionary");
        String enumType = CommandLine.variable("enumType");

        try
        {
            FieldDictionary.readRDMFieldDictionary(_dictionary, fieldDictionary);
            FieldDictionary.readEnumTypeDef(_dictionary, enumType);
        }
        catch (DictionaryException e)
        {
            System.err.println(_instanceName + e.toString());
        }

        // OMMMsgReencoder needs dictionary to parse OMMMsg
        OMMMsgReencoder.setLocalDictionary(_dictionary);

        OMMMsgReencoder.setEncodeData(CommandLine.booleanVariable("useEncodeData"));
        OMMMsgReencoder.setEncodeString(CommandLine.booleanVariable("useEncodeString"));
        OMMMsgReencoder.setPreEncodeDataDefs(CommandLine.booleanVariable("usePreEncodeDataDefs"));
    }

    public void cleanup()
    {
        super.cleanup();

    }
}
