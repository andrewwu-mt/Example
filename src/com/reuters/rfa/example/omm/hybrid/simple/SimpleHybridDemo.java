package com.reuters.rfa.example.omm.hybrid.simple;

import com.reuters.rfa.example.omm.hybrid.HybridDemo;
import com.reuters.rfa.example.omm.hybrid.ProviderServer;
import com.reuters.rfa.example.omm.hybrid.SessionManager;
import com.reuters.rfa.example.utility.CommandLine;

/**
 * A hybrid application which publishes data retrieved from a provider
 * application upon a request received from a consumer application.
 * 
 * <p>
 * <b>Processing sequence</b>
 * </p>
 * <ul>
 * <li>A {@link ProviderServer ProviderServer} starts listening for a client
 * connection.
 * <li>When the client connects, the ProviderServer asks an
 * {@link SimpleSessionManager SimpleSessionManager} to create an
 * {@link SimpleSessionClient SimpleSessionClient} to handle the session.
 * <li>The SimpleHybridDemo passes every request to the source application and
 * passes every response to the client without additional processing.
 * </ul>
 * 
 * Note that to parse {@link com.reuters.rfa.omm.OMMFieldList OMMFieldList},
 * dictionary must be present.
 * 
 */
public class SimpleHybridDemo extends HybridDemo
{

    public SimpleHybridDemo(String instanceName)
    {
        super(instanceName);
    }

    protected ProviderServer createProviderServer(String listenerName)
    {
        return new ProviderServer(this, listenerName);
    }

    protected SessionManager createSessionManager()
    {
        return new SimpleSessionManager(this);
    }

    public static void main(String[] args)
    {

        System.out.println("*****************************************************************************");
        System.out.println("*              Begin RFA Java Simple Hybrid Demo Program                    *");
        System.out.println("*****************************************************************************");
        
        CommandLine.addOption("runTime", 600, "Number of seconds to run the application.");
        CommandLine.addOption("session", "myNamespace::hybridSession",
                           "Session must contain at least two connections, connectionType \"RSSL\" and \"RSSL_PROV\".");
        CommandLine.addOption("rdmFieldDictionary", "/var/triarch/RDMFieldDictionary",
                              "RDMField dictionary name and location.");
        CommandLine.addOption("enumType", "/var/triarch/enumtype.def",
                              "RDMEnum dictionary name and location.");
        CommandLine.addOption("useReencoder", true,
                              "Reencode messages before passing through source/sink application.");
        CommandLine.addOption("useEncodeData", false,
                              "Enable OMMMsgReencoder.useEncodeData() when reencoding messages.");
        CommandLine.addOption("useEncodeString", false,
                              "Enable OMMMsgReencoder.useEncodeString() when reencoding messages.");
        CommandLine.addOption("usePreEncodeDataDefs", false,
                           "Enable OMMMsgReencoder.usePreEncodeDataDefs() when reencoding messages.");

        CommandLine.setArguments(args);

        HybridDemo hybridDemo = new SimpleHybridDemo("SimpleHybridDemo");
        if (hybridDemo.init())
        {
            hybridDemo.run();
        }
        hybridDemo.cleanup();

        System.out.println("*****************************************************************************");
        System.out.println("*                 End RFA Java Simple Hybrid Program                        *");
        System.out.println("*****************************************************************************");
    }

}
