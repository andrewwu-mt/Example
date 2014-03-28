package com.reuters.rfa.example.omm.symbollist;

import com.reuters.rfa.example.framework.sub.OMMSubAppContext;
import com.reuters.rfa.example.framework.sub.SubAppContext;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.rdm.RDMMsgTypes;

/**
 * This class is responsible for creating a
 * {@link com.reuters.rfa.example.framework.sub.SubAppContext SubAppContext} and
 * a {@link SymbolListClient}.
 */

public class StarterConsumer_SymbolList
{
    protected SymbolListClient _symbolListClient;
    protected SubAppContext _appContext;

    public StarterConsumer_SymbolList()
    {
        System.out.println("*****************************************************************************");
        System.out.println("*          Begin RFA Java StarterConsumer_SymbolList Program                *");
        System.out.println("*****************************************************************************");
        String serviceName = CommandLine.variable("serviceName");
        String itemName = CommandLine.variable("itemName");
        String mmt = CommandLine.variable("mmt");
        short msgModelType = RDMMsgTypes.msgModelType(mmt);
        _appContext = SubAppContext.create(System.out);
        _symbolListClient = new SymbolListClient((OMMSubAppContext)_appContext, serviceName,
                itemName, msgModelType);
    }

    public void init()
    {
        _symbolListClient.open();
    }

    public void run()
    {
        _appContext.run();
    }

    public void cleanup()
    {
        _appContext.cleanup();
    }

    public static void addCommandLineOptions()
    {
        CommandLine.addOption("itemName", "0#ARCA", "symbol list to request");
        CommandLine.addOption("mmt", "MARKET_PRICE", "Message Model Type");
        SubAppContext.addCommandLineOptions();
        CommandLine.changeDefault("serviceName", "DF_ARCA");
        CommandLine.changeDefault("type", "OMM");
    }

    public static void main(String argv[])
    {
        CommandLine.setProgramName("StarterConsumer_SymbolList");
        CommandLine.setProgramDesc("The StarterConsumer_SymbolList program demonstrates the use of RFA Java\n"
                        + "in subscribing to a Symbol List.");
        addCommandLineOptions();
        CommandLine.setArguments(argv);
        StarterConsumer_SymbolList demo = new StarterConsumer_SymbolList();
        demo.init();
        demo.run();
        demo.cleanup();
    }

}
