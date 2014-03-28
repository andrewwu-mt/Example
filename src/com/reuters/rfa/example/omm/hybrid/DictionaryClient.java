package com.reuters.rfa.example.omm.hybrid;

import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.omm.OMMMsg;

/**
 * Callback client from {@link DictionaryManager DictionaryManager}
 * 
 */
public interface DictionaryClient
{

    /**
     * Called when then dictionary used by this service is loaded
     * 
     * @param dictionary The full dictionary containing
     *            {@link com.reuters.rfa.rdm.RDMDictionary.Type#FIELD_DEFINITIONS
     *            FIELD_DEFINITIONS} and
     *            {@link com.reuters.rfa.rdm.RDMDictionary.Type#ENUM_TABLES
     *            ENUM_TABLES}
     * @param serviceName
     */
    void processDictionaryComplete(FieldDictionary dictionary, String serviceName);

    /**
     * Called when the response message is received from the provider. Ignore if
     * not interested.
     * 
     * @param responseMsg
     */
    void processDictionaryResponse(OMMMsg responseMsg);

}
