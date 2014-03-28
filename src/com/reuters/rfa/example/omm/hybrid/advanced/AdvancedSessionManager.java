package com.reuters.rfa.example.omm.hybrid.advanced;

import com.reuters.rfa.common.Handle;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.omm.hybrid.DictionaryManager;
import com.reuters.rfa.example.omm.hybrid.HybridDemo;
import com.reuters.rfa.example.omm.hybrid.OMMMsgReencoder;
import com.reuters.rfa.example.omm.hybrid.ProviderServer;
import com.reuters.rfa.example.omm.hybrid.SessionClient;
import com.reuters.rfa.example.omm.hybrid.SessionManager;
import com.reuters.rfa.example.utility.CommandLine;

/**
 * Used for creating {@link AdvancedSessionClient AdvancedSessionClient}
 * 
 * 
 */
public class AdvancedSessionManager extends SessionManager
{

    private final DictionaryManager _dictionaryManager;

    public DictionaryManager getDictionaryManager()
    {
        return _dictionaryManager;
    }

    public AdvancedSessionManager(HybridDemo manager)
    {
        super(manager);

        _dictionaryManager = new DictionaryManager();
    }

    protected SessionClient createSessionClient(Handle sessionHandle)
    {
        return new AdvancedSessionClient(this, sessionHandle);
    }

    public void init(ProviderServer client)
    {
        super.init(client);

        String fieldDictionary = CommandLine.variable("rdmFieldDictionary");
        String enumType = CommandLine.variable("enumType");

        FieldDictionary dictionary = _dictionaryManager.readDictionary(fieldDictionary, enumType);
        if (dictionary == null)
            System.exit(-1);
        // set default local dictionary
        OMMMsgReencoder.setLocalDictionary(dictionary);

        OMMMsgReencoder.setEncodeData(CommandLine.booleanVariable("useEncodeData"));
        OMMMsgReencoder.setEncodeString(CommandLine.booleanVariable("useEncodeString"));
        OMMMsgReencoder.setPreEncodeDataDefs(CommandLine.booleanVariable("usePreEncodeDataDefs"));
    }

    public void cleanup()
    {
        super.cleanup();
    }

}
