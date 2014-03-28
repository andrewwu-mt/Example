package com.reuters.rfa.example.omm.hybridni;

import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;

import com.reuters.rfa.common.Context;
import com.reuters.rfa.common.DeactivatedException;
import com.reuters.rfa.common.DispatchQueueInGroupException;
import com.reuters.rfa.common.EventQueueGroup;
import com.reuters.rfa.common.Token;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.omm.OMMAttribInfo;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMService;
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.Session;

/**
 * <li>A hybrid application that retrieves data from a provider application and
 * publishes it to a source distributor.
 * 
 * <li>It uses the OMMConsumer event source to connect to the provider
 * application and OMM Provider event source to connect to the source
 * distributor
 * 
 * <pre>
 * 
 *                           ======================
 *                            Provider Application
 *                           =============.========
 *                                        |
 *                                        |
 *       =================================.==============================================
 *       :                   |-- OMMConsumer Event Source(Connection Type = RSSL)       :
 *       :                   |                                                          : 
 *       :    HybridNIDemo --|                                                          :
 *       :                   |                                                          :    
 *       :                   |-- OMMProvider Event Source (Connection Type = RSSL_NIPROV):
 *       =================================.==============================================
 *                                        |
 *                                        |        
 *                           =============.========
 *                             Source Distributor 
 *                           ======================
 * </pre>
 * 
 * <p>
 * <b>Startup</b>
 * </p> <li>The {@link ProviderNIClient ProviderNIClient} is created. This
 * creates the {@link com.reuters.rfa.session.omm.OMMProvider OMMProvider} event
 * source. <li>The <code>ProviderNIClient</code> requests a login to the source
 * distributor. <li>On receiving a successful login response from the OMM
 * Provider, the {@link ConsumerClient ConsumerClient} is created. The
 * <code>ConsumerClient</code> creates an OMMConsumer event source and a login
 * is requested from a Provider application. <li>On receiving a successful login
 * response from a provider application, the ConsumerClient is ready to send
 * requests to the OMMConsumer.
 * 
 * <p>
 * <b>Sending Requests to OMMConsumer(provider application) & Publishing to the
 * OMMProvider(source distributor)</b>
 * </p> <li>On receiving a successful login response from a provider
 * application, the ConsumerClient sends a directory request to the OMMConsumer
 * <li>When the ConsumerClient receives a directory refresh, the ConsumerClient
 * requests items from the OMMConsumer and publishes the directory refresh to
 * the OMMProvider. <li>On receiving item refreshes/updates and directory
 * updates, the ConsumerClient will publishes the messages to the OMMProvider.
 * 
 * <p>
 * <b>Disconnect/Reconnect from/to OMMProvider(source distributor)</b>
 * </p> <li>The ProviderNIClient will receive a Login status message from the
 * OMMProvider. The ConsumerClient will be notified to stop publishing messages.
 * <li>On a reconnect to the source distributor the ProviderNIClient will
 * receive a Login refresh, and the ConsumerClient will reissue the directory
 * request <li>On receiving a directory refresh, the ConsumerClient will publish
 * the directory refresh and reissue the item requests <li>On receiving item
 * refresh, the ConsumerClient will publish the directory refresh and reissue
 * the item requests <li>On receiving item refreshes/updates and directory
 * updates, the ConsumerClient will publishes the messages to the OMMProvider.
 * Thus publishing to the source distributor is resumed
 * 
 * <p>
 * <b>Disconnect/Reconnect from/to OMMConsumer(provider application)</b>
 * </p> <li>The ConsumerClient will receive a Login status message/Directory
 * update message <li>The ConsumerClient will publish only the Directory update
 * message; the rest of the messages are not published. <li>On a reconnect to
 * the provider application, the ConsumerClient resumes publishing messages
 * 
 * <p>
 * <b>Shutdown</b>
 * </p>
 * Application will shutdown due to one of the following: <li>The runtime
 * duration(specified by command line argument) is over <li>The OMMConsumer has
 * been notified that the stream is closed <li>The OMMProvider has been notified
 * that the stream is closed </ul>
 */
public class HybridNIDemo
{
    final String _className;
    volatile boolean _isTimeout;
    final Timer _timer;

    Session _session; // RFA session
    final EventQueueGroup _eventQueueGroup;

    // clients
    ProviderNIClient _providerNIClient; // uses OMM Provider Event Source
    ConsumerClient _consumerClient; // uses OMM Consumer Event Source

    // data encoding purposes
    OMMPool _pool;
    OMMEncoder _encoder;

    /**
     * Constructor
     */
    public HybridNIDemo(String className)
    {
        _className = "[" + className + "]";

        _eventQueueGroup = EventQueueGroup.create("group");
        _timer = new Timer(true);
        _isTimeout = false;

        _pool = OMMPool.create();
        _encoder = _pool.acquireEncoder();
        _encoder.initialize(OMMTypes.MSG, 1000);

    }

    /**
     * Application Main
     */
    public static void main(String[] args)
    {

        System.out.println("*****************************************************************************");
        System.out.println("*                Begin RFA Java Hybrid NI Program                 	        *");
        System.out.println("*****************************************************************************");

        CommandLine.addOption("runTime", 600, "Number of seconds to run the application.");
        CommandLine.addOption("session", "myNamespace::hybridNISession", "hybridNI session.");
        CommandLine.addOption("itemName", "TRI.N", "List of items to open separated by ','.");
        CommandLine.addOption("mmt", "MARKET_PRICE", "Message Model Type");
        CommandLine.addOption("serviceName", "DIRECT_FEED",
                              "Service used for requests;  Defaults to DIRECT_FEED");
        CommandLine.addOption("attribInfoInUpdates", false, "Send attribInfo in updates.");
        String username = "guest";
        try
        {
            username = System.getProperty("user.name");
        }
        catch (Exception e)
        {
        }
        CommandLine.addOption("user", username, "DACS username for login");
        String defaultPosition = "1.1.1.1/net";
        try
        {
            defaultPosition = InetAddress.getLocalHost().getHostAddress() + "/"
                    + InetAddress.getLocalHost().getHostName();
        }
        catch (Exception e)
        {
        }
        CommandLine.addOption("position", defaultPosition, "DACS position for login");
        CommandLine.addOption("application", "256", "DACS application ID for login");
        CommandLine.setArguments(args);

        HybridNIDemo hybridDemoNI = new HybridNIDemo("HybridDemoNI");
        if (hybridDemoNI.initialize())
        {
            hybridDemoNI.run();
        }
        hybridDemoNI.shutdown();

        System.out.println("*****************************************************************************");
        System.out.println("*                End RFA Java Hybrid NI Program                             *");
        System.out.println("*****************************************************************************");
    }

    /**
     * Initialize
     */
    boolean initialize()
    {
        System.out.println(_className + " Initializing");

        // setup run time by scheduling timer task
        int runTime = CommandLine.intVariable("runTime");
        _timer.schedule(new TimerTask()
        {
            public void run()
            {
                _isTimeout = true;
            }
        }, runTime * 1000);

        Context.initialize();
        // acquire session
        String sessionName = CommandLine.variable("session");
        _session = Session.acquire(sessionName);
        if (_session == null)
        {
            System.out.println("Initialization failed!");
            return false;
        }
        System.out.println("RFA Version: " + Context.getRFAVersionInfo().getProductVersion());

        // create providerNI client for handling login
        _providerNIClient = new ProviderNIClient(this);
        _providerNIClient.initialize();
        _providerNIClient.makeLoginRequest();

        return true;
    }

    /**
     * Shutdown application
     */
    void shutdown()
    {
        System.out.println(_className + " Cleaning up");

        _eventQueueGroup.deactivate();
        if (_providerNIClient != null)
            _providerNIClient.cleanup();
        if (_consumerClient != null)
            _consumerClient.cleanup();

        // in case, the session cannot be acquired
        if (_session != null)
            _session.release();

        Context.uninitialize();
        System.out.println(_className + " Bye..... End of Tests!");
        System.exit(0);
    }

    /**
     * Run the application for the duration specified by runTime config variable
     */
    void run()
    {
        while (!_isTimeout)
        {
            try
            {
                _eventQueueGroup.dispatch(1000);
            }
            catch (DeactivatedException e)
            {
                e.printStackTrace();
            }
            catch (DispatchQueueInGroupException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Encode Login Request Message
     */
    OMMMsg encodeLoginRequestMessage(int role)
    {
        OMMMsg requestMessage = _pool.acquireMsg();
        requestMessage.setMsgType(OMMMsg.MsgType.REQUEST);
        requestMessage.setMsgModelType(RDMMsgTypes.LOGIN);
        requestMessage.setIndicationFlags(OMMMsg.Indication.REFRESH);
        requestMessage.setAttribInfo(null, CommandLine.variable("user"),
                                     RDMUser.NameType.USER_NAME);

        _encoder.initialize(OMMTypes.MSG, 500);
        _encoder.encodeMsgInit(requestMessage, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);
        _encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
        _encoder.encodeElementEntryInit("ApplicationId", OMMTypes.ASCII_STRING);
        _encoder.encodeString(CommandLine.variable("application"), OMMTypes.ASCII_STRING);
        _encoder.encodeElementEntryInit("Position", OMMTypes.ASCII_STRING);
        _encoder.encodeString(CommandLine.variable("position"), OMMTypes.ASCII_STRING);
        _encoder.encodeElementEntryInit(RDMUser.Attrib.Role, OMMTypes.UINT);
        _encoder.encodeUInt((long)role);
        _encoder.encodeAggregateComplete();

        // Get the encoded message from the _encoder
        OMMMsg encodedMessage = (OMMMsg)_encoder.getEncodedObject();

        // Release the message that own by the application
        _pool.releaseMsg(requestMessage);

        return encodedMessage; // return the encoded message
    }

    /**
     * Encode Directory Request Message
     */
    OMMMsg encodeDirectoryRequestMessage()
    {
        OMMMsg requestMessage = _pool.acquireMsg();
        requestMessage.setMsgType(OMMMsg.MsgType.REQUEST);
        requestMessage.setMsgModelType(RDMMsgTypes.DIRECTORY);
        requestMessage.setIndicationFlags(OMMMsg.Indication.REFRESH);
        OMMAttribInfo attribInfo = _pool.acquireAttribInfo();
        attribInfo.setFilter(RDMService.Filter.INFO | RDMService.Filter.STATE
                | RDMService.Filter.GROUP);
        requestMessage.setAttribInfo(attribInfo);
        return requestMessage;
    }

    /**
     * Encode Item Request Message Header
     */
    OMMMsg encodeItemRequestMessageHeader()
    {
        OMMMsg ommItemRequestMsg = _pool.acquireMsg();
        ommItemRequestMsg.setMsgType(OMMMsg.MsgType.REQUEST);
        ommItemRequestMsg.setPriority((byte)1, 1);
        if (CommandLine.booleanVariable("attribInfoInUpdates"))
            ommItemRequestMsg.setIndicationFlags(OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES
                                                 | OMMMsg.Indication.REFRESH);
        else
            ommItemRequestMsg.setIndicationFlags(OMMMsg.Indication.REFRESH);

        return ommItemRequestMsg;
    }

    /**
     * Encode Item Request AttribInfo Message
     */
    OMMMsg encodeItemRequestAttribInfoMessage(OMMMsg ommItemRequestMsg, short capability,
            String serviceName, String itemName)
    {
        ommItemRequestMsg.setMsgModelType(capability);
        ommItemRequestMsg.setAttribInfo(serviceName, itemName, RDMInstrument.NameType.RIC);
        return ommItemRequestMsg;
    }

    /**
     * Release encoded message back to the pool
     */
    void releaseEncodedMessage(OMMMsg msg)
    {
        _pool.releaseMsg(msg);
    }

    /**
     * The OMMProvider has been granted login; Create the
     * ConsumerClient(OMMConsumer) & send login request
     */
    void niproviderIsLoggedIn()
    {
        // create consumer client
        _consumerClient = new ConsumerClient(this);
        _consumerClient.initialize();
        _consumerClient.makeLoginRequest();
    }

    /**
     * The hybrid application has lost connection with the source distributor;
     * Notify the ConsumerClient to stop publishing messages
     */
    void niproviderIsDisconnected()
    {
        _consumerClient.stopPublishingMessages();
    }

    /**
     * The hybrid application has restored connection with the source
     * distributor; Notify the ConsumerClient to resume publishing messages
     */
    void niproviderIsReconnected()
    {
        _consumerClient.resumePublishingMessages();
    }

    /**
     * Generate Token for publishing
     */
    Token generateToken()
    {
        return (_providerNIClient.generateToken());
    }

    /**
     * Publish Message to Provider
     */
    void publishMessage2ProviderNI(OMMMsg msg, Token publishToken)
    {
        _providerNIClient.submitResponse(msg, publishToken);
    }
}
// ///////////////////////////////////////////////////////////////////////////////
// / End of file
// ///////////////////////////////////////////////////////////////////////////////
