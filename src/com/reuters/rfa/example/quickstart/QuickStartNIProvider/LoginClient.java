package com.reuters.rfa.example.quickstart.QuickStartNIProvider;

import java.net.InetAddress;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMState;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * <p>The is a Client class that handle request and response for Login.</p>
 *
 * This class is responsible for the following:
 * <ul>
 *  <li>Encoding streaming request message for Login using OMM message
 *  <li>Register for Login to RFA</li>
 *  <li>Implement a Client which processes events from a <code>OMMProvider</code>(non-interactive client publishing) and handle Login responses</li>
 *  <li>Use {@link com.reuters.rfa.example.utility.GenericOMMParser GenericOMMParser}
 *      to parse {@link com.reuters.rfa.omm.OMMMsg OMMMsg} response messages.
 *  <li>Unregistered login, logout from server.
 * </ul>
 *
 * Note: This class will use {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMProvider OMMProvider} from OMMProviderNIDemo
 *
 * @see QSNIProvider
 *
 */
// This class is a Client implementation that is utilized to handle login activities
// between application and RFA.
// An instance of this class is created by QSNIProviderDemo.
// This class performs the following functions:
// - Creates and encodes login request message (method encodeLoginReqMsg()).
// - Registers the client (itself) with RFA (method sendRequest()). The registration
// will cause RFA to send login request. RFA will return back a handle instance.
// - Unregisters the client in RFA (method closeRequest()).
// - Processes events for this client (method processEvent()). processEvent() method
// must be implemented by a class that implements Client interface.
//
// The class keeps the following members:
// Handle 	_loginHandle - 		a handle returned by RFA on registering the client
//								application uses this handle to identify this client
// QSNIProviderDemo _mainApp - 	main application class
// boolean _loggedIn -			flag indicating login status of application

public class LoginClient implements Client
{
    Handle _loginHandle;
    QSNIProvider _mainApp;
    
    boolean _loggedIn;
	private String _className = "LoginClient";

    public LoginClient(QSNIProvider mainApp)
    {
        _mainApp = mainApp;
        _loggedIn = false;
    }

    // Gets encoded streaming request messages for Login and register it to RFA
    public void sendRequest()
    {
        OMMMsg ommmsg = encodeLoginReqMsg();
        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();
        ommItemIntSpec.setMsg(ommmsg);
        System.out.println(_className+": Sending login request");
        _loginHandle = _mainApp._provider.registerClient(
                        _mainApp._eventQueue, ommItemIntSpec, this, null);
    }

    // Encodes request message for login
    private OMMMsg encodeLoginReqMsg()
    {
        String username = "guest";
        try { username = System.getProperty("user.name"); }  catch( Exception e ) {}
        String application = "256";
        String position = "1.1.1.1/net";
        try { position = InetAddress.getLocalHost().getHostAddress() + "/" +
              InetAddress.getLocalHost().getHostName(); }  catch( Exception e ) {}

        OMMPool pool = _mainApp.getPool();
        OMMEncoder encoder = _mainApp.getEncoder();
        encoder.initialize(OMMTypes.MSG, 500);
        OMMMsg msg = pool.acquireMsg();
        msg.setMsgType(OMMMsg.MsgType.REQUEST);
        msg.setMsgModelType(RDMMsgTypes.LOGIN);
        msg.setIndicationFlags(OMMMsg.Indication.REFRESH);
        msg.setAttribInfo(null, username, RDMUser.NameType.USER_NAME);

        encoder.encodeMsgInit(msg, OMMTypes.ELEMENT_LIST, OMMTypes.NO_DATA);
        encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short) 0);
        encoder.encodeElementEntryInit(RDMUser.Attrib.ApplicationId, OMMTypes.ASCII_STRING);
        encoder.encodeString(application, OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit(RDMUser.Attrib.Position, OMMTypes.ASCII_STRING);
        encoder.encodeString(position, OMMTypes.ASCII_STRING);
        encoder.encodeElementEntryInit(RDMUser.Attrib.Role, OMMTypes.UINT);
	    encoder.encodeUInt((long)RDMUser.Role.PROVIDER);
        encoder.encodeAggregateComplete();

        //Get the encoded message from the encoder
        OMMMsg encMsg = (OMMMsg)encoder.getEncodedObject();

        //Release the message that own by the application
        pool.releaseMsg(msg);

        return encMsg; //return the encoded message
    }

    // Unregisters/unsubscribes login handle
    public void closeRequest()
    {
    	if (_loginHandle != null) {
	        _mainApp._provider.unregisterClient(_loginHandle);
	        _loginHandle = null;
    	}
    }

    // This is a Client method. When an event for this client is dispatched,
    // this method gets called.
    public void processEvent(Event event)
    {
    	if (event.getType() == Event.COMPLETION_EVENT) 
    	{
    		System.out.println(_className+": Receive a COMPLETION_EVENT, "+event.getHandle());
    		return;
    	}

        if (event.getType() != Event.OMM_ITEM_EVENT) 
        {
            System.out.println("ERROR: "+_className+" Received an unsupported Event type.");
            _mainApp.cleanup();
            return;
        }

        OMMItemEvent ie = (OMMItemEvent) event;
        OMMMsg respMsg = ie.getMsg();

        System.out.println(_className + ".processEvent: Received Login Response ");
        
        if (respMsg.isFinal()) 
        {
        	System.out.println(_className + ": Login Response message is final.");
        	GenericOMMParser.parse(respMsg);
            _mainApp.cleanup();
        	return;
        }

        System.out.println (_className+": Received Login Response - "+OMMMsg.MsgType.toString(respMsg.getMsgType()));
    	GenericOMMParser.parse(respMsg);
        
    	// Note: This code assumes that there is only one login refresh 
    	// (the Session is configured with one Connection in the connectionList
    	// so it only connects to one SrcDist). 
        if((respMsg.getMsgType() == OMMMsg.MsgType.STATUS_RESP) &&
           (respMsg.getRespTypeNum() == OMMMsg.RespType.SOLICITED) &&
           (respMsg.has(OMMMsg.HAS_STATE)) &&
           (respMsg.getState().getStreamState() == OMMState.Stream.OPEN) &&
           (respMsg.getState().getDataState() == OMMState.Data.OK) )
        {
        	if(!_loggedIn)
        	{
        		_loggedIn = true;
        		_mainApp.processLogin(true);
        	}
        }
        else if(respMsg.has(OMMMsg.HAS_STATE) &&
        		respMsg.getState().getDataState() == OMMState.Data.SUSPECT)
        {
        	if(_loggedIn)
        	{
        		_loggedIn = false;
        		_mainApp.processLogin(false);
        	}
        }
    }
}
