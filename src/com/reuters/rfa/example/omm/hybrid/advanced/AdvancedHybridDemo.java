package com.reuters.rfa.example.omm.hybrid.advanced;

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
 * {@link AdvancedSessionManager AdvancedSessionManager} to create an
 * {@link AdvancedSessionClient AdvancedSessionClient} to handle the session.
 * <li>The client sends login request, the AdvancedSessionClient forwards the
 * request to the source application.
 * <li>The AdvancedSessionClient uses {@link DirectoryClient DirectoryClient} to
 * make directory request.
 * <li>When the directory response arrives, the AdvancedSessionManager gets
 * dictionaries used and dictionaries provided for every service and request
 * dictionary to the source application.
 * <li>If the item request comes before the full dictionary, the request will be
 * put into the pending queue.
 * <li>Every item request will be forward to the source application.
 * <li>Every item response will be managed by {@link ItemGroupManager
 * ItemGroupManager}
 * </ul>
 * 
 */
public class AdvancedHybridDemo extends HybridDemo
{

    public AdvancedHybridDemo(String instanceName)
    {
        super(instanceName);
    }

    protected ProviderServer createProviderServer(String listenerName)
    {
        return new ProviderServer(this, listenerName);
    }

    protected SessionManager createSessionManager()
    {
        return new AdvancedSessionManager(this);
    }

    public static void main(String[] args)
    {

        System.out.println("*****************************************************************************");
        System.out.println("*              Begin RFA Java Advanced Hybrid Program                       *");
        System.out.println("*****************************************************************************");

        CommandLine.addOption("runTime", 600, "Number of seconds to run the application.");
        CommandLine.addOption("session", "myNamespace::hybridSession", "Provider session.");
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

        HybridDemo hybridDemo = new AdvancedHybridDemo("AdvancedHybridDemo");
        if (hybridDemo.init())
        {
            hybridDemo.run();
        }
        hybridDemo.cleanup();

        System.out
                .println("*****************************************************************************");
        System.out
                .println("*              End RFA Java Advanced Hybrid Program                         *");
        System.out
                .println("*****************************************************************************");
    }

}
