package com.reuters.rfa.example.omm.batchviewcons;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.ArrayList;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.common.Handle;
import com.reuters.rfa.common.InterestSpec;
import com.reuters.rfa.example.utility.CommandLine;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEncoder;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMPool;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMInstrument;
import com.reuters.rfa.rdm.RDMMsgTypes;
import com.reuters.rfa.rdm.RDMUser;
import com.reuters.rfa.session.TimerIntSpec;
import com.reuters.rfa.session.omm.OMMItemEvent;
import com.reuters.rfa.session.omm.OMMItemIntSpec;

/**
 * <p>
 * The is a Client class that handle request and response for items in the
 * following Reuters Domain:
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_PRICE MARKET_PRICE},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_BY_ORDER MARKET_BY_ORDER},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_BY_PRICE MARKET_BY_PRICE},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#MARKET_MAKER MARKET_MAKER},
 * {@linkplain com.reuters.rfa.rdm.RDMMsgTypes#SYMBOL_LIST SYMBOL_LIST}, in
 * generic way. User can specify message model type by passing on the command
 * line parameter, mmt.
 * </p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Encoding streaming view and/or batch request message for the specified
 * model using OMM message. User can request view by passing sendView command
 * line parameter along with other view related parameters e.g. viewType and
 * viewData. If user is requesting more than one item, request is sent as batch
 * request. User can request Batch Reissues by passing sendReissue command line
 * parameter along with reissue related parameters e.g. reissueInterval,
 * reissueWithPAR and reissueWithPriority.
 * <li>Register/subscribe one or multiple messages to RFA</li>
 * <li>Implement a Client which processes events from an
 * <code>OMMConsumer</code>
 * <li>Use {@link com.reuters.rfa.example.utility.GenericOMMParser
 * GenericOMMParser} to parse {@link com.reuters.rfa.omm.OMMMsg OMMMsg} response
 * messages.
 * <li>Unregistered all items when the application is not interested anymore.
 * </ul>
 * 
 * Note: This class will use {@link com.reuters.rfa.omm.OMMEncoder OMMEncoder},
 * {@link com.reuters.rfa.omm.OMMPool OMMPool} and
 * {@link com.reuters.rfa.session.omm.OMMConsumer OMMConsumer} from
 * StarterConsumer
 * 
 * @see StarterConsumer_BatchView
 * 
 */
public class BatchViewItemManager implements Client
{

    int _numItemsRequested = 0;
    List<Handle> _itemHandles;
    StarterConsumer_BatchView _mainApp;
    private String _className = "BatchViewItemManager";

    private Handle _reissueTimerHandle;
    private boolean _itemsPaused;
    private boolean _reissueParRequired;
    private boolean _reissuePriorityRequired;
    private byte _reissuePriorityClass = 1;
    private byte _reissuePriorityCount = 1;

    public BatchViewItemManager(StarterConsumer_BatchView mainApp)
    {
        _mainApp = mainApp;
        _itemHandles = new ArrayList<Handle>();
        _itemsPaused = CommandLine.booleanVariable("initialRequestPaused");
        _reissueParRequired = CommandLine.booleanVariable("reissueWithPAR");
        _reissuePriorityRequired = CommandLine.booleanVariable("reissueWithPriority");
    }

    /**
     * Encodes streaming request messages and register them to RFA
     */
    public void sendRequest()
    {
        String itemNames = CommandLine.variable("itemName");
        String serviceName = CommandLine.variable("serviceName");
        String mmt = CommandLine.variable("mmt");
        short capability = RDMMsgTypes.msgModelType(mmt);
        
        // Note: "," is a valid character for RIC name.
        // This application need to be modified if RIC names have ",".
        String[] itemNamesList = null;
        String ricFileName = CommandLine.variable("ricFile");
        if (ricFileName != null && !ricFileName.equals("false"))
        {
            int maxCount = CommandLine.intVariable("maxCount");
            itemNamesList = buildRicListFromFile(ricFileName, maxCount);
            if (itemNamesList == null)
            {
                System.out
                        .println("BatchViewItemManager.sendRequest(): Warning: Could not find ricFileName \""
                                + ricFileName + "\"");
                return;
            }
        }
        else
        {
            itemNamesList = itemNames.split(",");
        }

        System.out.println(_className + ".sendRequest: Application sending item request"
                           + (_itemsPaused ? " w/ PAUSE" : "") + "...");

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();

        // Preparing to send item request message
        OMMPool pool = _mainApp.getPool();
        OMMMsg ommmsg = pool.acquireMsg();

        ommmsg.setMsgType(OMMMsg.MsgType.REQUEST);
        ommmsg.setMsgModelType(capability);
        ommmsg.setPriority((byte)1, 1);
        int indicationFlags = OMMMsg.Indication.REFRESH; 
        if (_itemsPaused)
            indicationFlags |= OMMMsg.Indication.PAUSE_REQ;


        // Setting OMMMsg with negotiated version info from login handle
        if (_mainApp.getLoginHandle() != null)
        {
            ommmsg.setAssociatedMetaInfo(_mainApp.getLoginHandle());
        }

        if (itemNamesList.length == 1)
        {
            // request without batching
            String itemName = itemNamesList[0].trim();
            System.out.println(_className + ": Application subscribing to " + itemName);
            ommmsg.setAttribInfo(serviceName, itemName, RDMInstrument.NameType.RIC);
            // Set the message into interest spec
            ommItemIntSpec.setMsg(encodeRequestPayLoad(ommmsg, null, indicationFlags));
            Handle itemHandle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(),
                                                                         ommItemIntSpec, this, null);
            _itemHandles.add(itemHandle);
            _numItemsRequested++;
        }
        else
        {
            ommmsg.setAttribInfo(serviceName, null, RDMInstrument.NameType.RIC);
            // Batch request with/without view
            List<String> itemsList = new ArrayList<String>(itemNamesList.length);

            System.out.println(_className + ": Application subscribing items for Batch:");
            for (int i = 0; i < itemNamesList.length; i++)
            {
                itemsList.add(itemNamesList[i].trim());
                System.out.println("			" + itemNamesList[i].trim());
                _numItemsRequested++;
            }

            while (itemsList.size() > 0)
            {
                // Set the message into interest spec
                ommItemIntSpec.setMsg(encodeRequestPayLoad(ommmsg, itemsList, indicationFlags));
                Handle itemHandle = _mainApp.getOMMConsumer()
                        .registerClient(_mainApp.getEventQueue(), ommItemIntSpec, this, null);
                _itemHandles.add(itemHandle);
            }
        }
        pool.releaseMsg(ommmsg);

        if (CommandLine.booleanVariable("sendReissue"))
            registerReissueTimer();
    }

    /**
     * Registers a timer to signal when to perform a reissue.
     */
    private void registerReissueTimer()
    {
        if (_reissueTimerHandle == null)
        {
            TimerIntSpec timer = new TimerIntSpec();
            int interval = CommandLine.intVariable("reissueInterval");
            if (interval <= 1)
                interval = 1;
            timer.setDelay(interval * 1000);
            timer.setRepeating(true);
            _reissueTimerHandle = _mainApp.getOMMConsumer().registerClient(_mainApp.getEventQueue(), timer,
                                                                           this, null);
        }
    }

    protected void unregisterReissueTimer()
    {
        if (_reissueTimerHandle != null)
        {
            _mainApp.getOMMConsumer().unregisterClient(_reissueTimerHandle);
            _reissueTimerHandle = null;
        }
    }

    private void sendReissue()
    {
        String serviceName = CommandLine.variable("serviceName");
        String mmt = CommandLine.variable("mmt");
        short capability = RDMMsgTypes.msgModelType(mmt);

        OMMItemIntSpec ommItemIntSpec = new OMMItemIntSpec();

        // Preparing to send batch reissue message
        OMMPool pool = _mainApp.getPool();
        OMMMsg ommmsg = pool.acquireMsg();

        ommmsg.setMsgType(OMMMsg.MsgType.REQUEST);
        ommmsg.setMsgModelType(capability);
        int indicationFlags = 0; // OMMMsg.Indication.REFRESH intentionally left off.

        if (_reissueParRequired)
        {
            if (_itemsPaused)
            {
                _itemsPaused = false;
                System.out.println(_className + ".sendReissue: Application reissuing Batch request...");
            }
            else
            {
                _itemsPaused = true;
                indicationFlags = OMMMsg.Indication.PAUSE_REQ;
                System.out.println(_className + ".sendReissue: Application reissuing Batch request w/ PAUSE...");
            }
        }
        
        if (_reissuePriorityRequired)
        {
            // simply increment the priority each time.
            if (++_reissuePriorityCount > Integer.MAX_VALUE)
            {
                _reissuePriorityCount = 1;
                if (++_reissuePriorityClass > Byte.MAX_VALUE)
                    _reissuePriorityClass = 1;
            }
            ommmsg.setPriority(_reissuePriorityClass, _reissuePriorityCount);
        }

        // Setting OMMMsg with negotiated version info from login handle
        if (_mainApp.getLoginHandle() != null)
        {
            ommmsg.setAssociatedMetaInfo(_mainApp.getLoginHandle());
        }

        // Note: item name not needed for a batch reissue.
        ommmsg.setAttribInfo(serviceName, null, RDMInstrument.NameType.RIC);

        if (CommandLine.booleanVariable("sendView"))
        {
            // encode view into payload.
            ommItemIntSpec.setMsg(encodeRequestPayLoad(ommmsg, null, indicationFlags));
        }
        else
        {
            ommmsg.setIndicationFlags(indicationFlags);
            ommItemIntSpec.setMsg(ommmsg);
        }

        _mainApp.getOMMConsumer().reissueClient(_itemHandles, ommItemIntSpec);
        pool.releaseMsg(ommmsg);
    }

    /**
     * Unregisters/unsubscribes all items individually
     */
    public void closeRequest()
    {
        if (_itemHandles.size() > 0)
            _mainApp.getOMMConsumer().unregisterClient(_itemHandles, (InterestSpec)null);
    }

    /**
     * Process incoming events based on the event type. Events of type
     * {@link com.reuters.rfa.common.Event#OMM_ITEM_EVENT OMM_ITEM_EVENT} are
     * parsed using {@link com.reuters.rfa.example.utility.GenericOMMParser
     * GenericOMMParser}
     */
    public void processEvent(Event event)
    {
        if (event.getType() == Event.COMPLETION_EVENT)
        {
            System.out.println(_className + ": Receive a COMPLETION_EVENT, " + event.getHandle());
            _itemHandles.remove(event.getHandle()); // update the handle list
            return;
        }
        else if (event.getType() == Event.TIMER_EVENT)
        {
            sendReissue();
            return;
        }

        System.out.println(_className + ".processEvent: Received Item Event...");
        if (event.getType() != Event.OMM_ITEM_EVENT)
        {
            System.out.println("ERROR: " + _className + " Received an unsupported Event type.");
            _mainApp.cleanup(-1);
            return;
        }

        OMMItemEvent ie = (OMMItemEvent)event;
        OMMMsg respMsg = ie.getMsg();
        GenericOMMParser.parse(respMsg);

        // add the batch request item response to the item handle list for
        // proper closing
        if (_numItemsRequested != 1 && respMsg.getMsgType() == OMMMsg.MsgType.REFRESH_RESP
                && respMsg.isSet(OMMMsg.Indication.REFRESH_COMPLETE))
        {
            _itemHandles.add(event.getHandle());
        }
    }

    /**
     * Encode view payload into {@link OMMMsg} if user has indicated it and view
     * type is valid. Encode batch request if user has indicated to send request
     * as batch. This method also sets indication flag into {@link OMMMsg}.
     * 
     * @param ommmsg ommmsg to populate with the view payload
     * @return OMMMsg with view encoded payload
     */
    public OMMMsg encodeRequestPayLoad(OMMMsg ommmsg, List<String> itemsList, int indicationFlags)
    {
        int estimatedSize = itemsList == null ? 0 : itemsList.size() * 10;
        if (estimatedSize > 64000)
            estimatedSize = 64000;
        estimatedSize += 200; // add room for non-batch encoded data
                              // (ElementList/Entry, Array)

        int viewType = CommandLine.intVariable("viewType");
        String viewData = CommandLine.variable("viewData");
        boolean sendView = CommandLine.booleanVariable("sendView");
        boolean sendBatch = itemsList != null && itemsList.size() > 0;
        indicationFlags |= (CommandLine.booleanVariable("attribInfoInUpdates")) ? OMMMsg.Indication.ATTRIB_INFO_IN_UPDATES
                : 0;

        if (sendBatch)
            indicationFlags |= OMMMsg.Indication.BATCH_REQ;

        // If view doesn't need to be sent i.e. either user has not indicated it
        // or viewType is not valid
        // set appropriate indication flag and return the OMMMsg with other data
        // in the payload.
        if (!sendView
                || !(viewType == RDMUser.View.FIELD_ID_LIST || viewType == RDMUser.View.ELEMENT_NAME_LIST))
        {
            if (sendView)
                System.err.println("Unknown view type: " + viewType
                        + ", view data will not be sent. Expecting view type of '"
                        + RDMUser.View.FIELD_ID_LIST + "' or '" + RDMUser.View.ELEMENT_NAME_LIST + "'");

            ommmsg.setIndicationFlags(indicationFlags);
            if (sendBatch)
            {
                OMMEncoder encoder = _mainApp.getEncoder();
                encoder.initialize(OMMTypes.MSG, estimatedSize);
                encoder.encodeMsgInit(ommmsg, OMMTypes.NO_DATA, OMMTypes.ELEMENT_LIST);
                encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);
                encoder.encodeElementEntryInit(RDMUser.Feature.ItemList, OMMTypes.ARRAY);
                encoder.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
                String itemName;
                while (itemsList.size() > 0)
                {
                    itemName = itemsList.get(0);
                    if ((itemName.length() + encoder.getEncodedSize()) > estimatedSize)
                    {
                        break; // don't encode any more items in this payload.
                    }
                    else
                    {
                        encoder.encodeArrayEntryInit();
                        encoder.encodeString(itemsList.remove(0), OMMTypes.ASCII_STRING);
                    }
                }
                encoder.encodeAggregateComplete(); // completes the array
                encoder.encodeAggregateComplete(); // completes the element list
                return (OMMMsg)encoder.acquireEncodedObject();
            }
            return ommmsg;
        }

        // View is valid and needs to be sent.

        // View and Batch payload in element list
        indicationFlags = indicationFlags | OMMMsg.Indication.VIEW;
        ommmsg.setIndicationFlags(indicationFlags);
        OMMEncoder encoder = _mainApp.getEncoder();
        encoder.initialize(OMMTypes.MSG, sendBatch ? viewData.length() * 5 + estimatedSize + 200
                : viewData.length() * 5 + 100);
        encoder.encodeMsgInit(ommmsg, OMMTypes.NO_DATA, OMMTypes.ELEMENT_LIST);
        encoder.encodeElementListInit(OMMElementList.HAS_STANDARD_DATA, (short)0, (short)0);

        // View Entries
        // 1 - ViewType
        // 2 - Array of field ids or element names
        if (viewType == RDMUser.View.FIELD_ID_LIST)
        {
            encoder.encodeElementEntryInit(RDMUser.View.ViewType, OMMTypes.UINT);
            encoder.encodeUInt(RDMUser.View.FIELD_ID_LIST);
            encoder.encodeElementEntryInit(RDMUser.View.ViewData, OMMTypes.ARRAY);

            // As type for FID is short, size of 2 for array entry is sufficient
            // for FIELD_ID_LIST view data
            encoder.encodeArrayInit(OMMTypes.INT, 2);
            for (String fldIdStr : viewData.split(","))
            {
                encoder.encodeArrayEntryInit();
                encoder.encodeInt(Integer.parseInt(fldIdStr));
            }
            encoder.encodeAggregateComplete(); // completes the array
        }
        else if (viewType == RDMUser.View.ELEMENT_NAME_LIST)
        {
            encoder.encodeElementEntryInit(RDMUser.View.ViewType, OMMTypes.UINT);
            encoder.encodeUInt(RDMUser.View.ELEMENT_NAME_LIST);

            encoder.encodeElementEntryInit(RDMUser.View.ViewData, OMMTypes.ARRAY);
            encoder.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
            for (String elemName : viewData.split(","))
            {
                encoder.encodeArrayEntryInit();
                encoder.encodeString(elemName, OMMTypes.ASCII_STRING);
            }
            encoder.encodeAggregateComplete(); // completes the array
        }

        if (sendBatch)
        {
            encoder.encodeElementEntryInit(RDMUser.Feature.ItemList, OMMTypes.ARRAY);
            encoder.encodeArrayInit(OMMTypes.ASCII_STRING, 0);
            String itemName;
            while (itemsList.size() > 0)
            {
                itemName = itemsList.get(0);
                if ((itemName.length() + encoder.getEncodedSize()) > 65000)
                {
                    break; // don't encode any more items.
                }
                else
                {
                    encoder.encodeArrayEntryInit();
                    encoder.encodeString(itemsList.remove(0), OMMTypes.ASCII_STRING);
                }
            }
            encoder.encodeAggregateComplete(); // completes the array
        }

        encoder.encodeAggregateComplete(); // completes the element list
        return (OMMMsg)encoder.getEncodedObject();
    }

    /**
     * @param ricFileName - is the name of file that have ric or item name. One
     *            item per line. Ex ricFile.txt IBM.N SUNW.O THAI.BK RTR.L ...
     * @param maxCount - maximum number of symbol to read from ric file name.
     *            Default is -1, which means read all symbols.
     */
    public static String[] buildRicListFromFile(String ricFileName, int maxCount)
    {
        RandomAccessFile dataFile = null;
        try
        {
            int i = 0;
            dataFile = new RandomAccessFile(ricFileName, "r");
            String line = dataFile.readLine();
            ArrayList<String> itemList = new ArrayList<String>();

            while (line != null && (line = line.trim()).length() > 0)
            {
                line = dataFile.readLine();
                if ((maxCount == -1 || i < maxCount) && line != null
                        && (line = line.trim()).length() > 0)
                {
                    itemList.add(line);
                    i++;
                }
            }
            String[] ricList = new String[itemList.size()];
            ricList = (String[])itemList.toArray(ricList);

            dataFile.close();
            dataFile = null;
            itemList = null;

            return ricList;
        }
        catch (FileNotFoundException ex)
        {
            return null;
        }
        catch (IOException ex)
        {
            System.out.println("IO error processing " + ex.getMessage());
            System.out.println("Exiting...");
            System.exit(1);
        }
        return null;
    }
}
