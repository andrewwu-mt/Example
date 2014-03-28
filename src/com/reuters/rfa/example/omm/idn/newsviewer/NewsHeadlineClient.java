package com.reuters.rfa.example.omm.idn.newsviewer;

import com.reuters.rfa.common.Client;
import com.reuters.rfa.common.Event;
import com.reuters.rfa.dictionary.FidDef;
import com.reuters.rfa.dictionary.FieldDictionary;
import com.reuters.rfa.example.framework.sub.NormalizedEvent;
import com.reuters.rfa.example.utility.GenericOMMParser;
import com.reuters.rfa.omm.OMMMsg;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.session.omm.OMMItemEvent;

/**
 * <p>
 * This is a Client class that handle response for headline.
 * </p>
 * 
 * This class is responsible for the following:
 * <ul>
 * <li>Receive and parse headline data for both OMM and MarketData.
 * <li>Check require field for those data.
 * <li>Send valid data to display by {@link NewsHeadlineViewer}.
 * </ul>
 * 
 */
public class NewsHeadlineClient implements Client
{
    NewsHeadlineViewer _viewer;
    com.reuters.rmtes.RmtesCharsetProvider rmtesProv = new com.reuters.rmtes.RmtesCharsetProvider();
    java.nio.charset.Charset rmtesCharset = rmtesProv.charsetForName("RMTES");
    
    public NewsHeadlineClient(NewsHeadlineViewer viewer)
    {
        _viewer = viewer;
        initializeFids(_viewer._appContext.getFieldDictionary());

        if (!FidsInitialized)
        {
            System.err.println("Initialize field id from dictionary fail");
        }
    }

    public void processEvent(Event event)
    {
        switch (event.getType())
        {
            case Event.OMM_ITEM_EVENT:
                processOMMItemEvent((OMMItemEvent)event);
                break;
            case Event.COMPLETION_EVENT:
                System.out.println("Received COMPLETION_EVENT for handle " + event.getHandle());
                break;
            default:
                System.out.println("NewsHeadlineClient.processEvent: unhandled event type: "
                        + event.getType());
                break;
        }
    }

    protected void processOMMItemEvent(OMMItemEvent event)
    {
        OMMMsg msg = event.getMsg();
        if (msg.getDataType() != OMMTypes.NO_DATA)
        {
            NormalizedEvent nevent = _viewer._appContext.getNormalizedEvent(event);
            processHeadline(nevent);
        }
        GenericOMMParser.parse(msg);
    }

    protected void processHeadline(NormalizedEvent nevent)
    {
        if (!FidsInitialized)
        {
            return;
        }

        Headline headline = new Headline();
        // parse mandatory fields

        String displayName = nevent.getFieldString(DSPLY_NAME);
        String pnac = nevent.getFieldString(PNAC);
        String attribtn = nevent.getFieldString(ATTRIBTN);
        String prodCode = nevent.getFieldString(PROD_CODE);
        String storyTime = nevent.getFieldString(STORY_TIME);
        String storyDate = nevent.getFieldString(STORY_DATE);
        String bcastText = null;

        byte[] bytes = new byte[BCAST_TEXT_LENGTH];
        int nbytes = nevent.getFieldBytes(BCAST_TEXT, bytes, 0);
        bcastText = new String(bytes, 0, nbytes, rmtesCharset);
        if (displayName == null || pnac == null || attribtn == null || prodCode == null
                || storyTime == null || storyDate == null)
        {
            System.out.println("Drop message: missing require fields");
            return;
        }

        byte b = Byte.parseByte(displayName);
        headline.setOption(b);
        headline.setPnac(pnac);
        headline.setText(bcastText);
        headline.setAttribution(attribtn);
        headline.setProdCodes(prodCode);
        headline.setStoryTime(storyTime);
        headline.setStoryDate(storyDate);

        // parse optional fields
        String lang = nevent.getFieldString(LANG_IND);
        String topicCode = nevent.getFieldString(TOPIC_CODE);
        String coIds = nevent.getFieldString(CO_IDS);

        if (lang != null)
        {
            headline.setLang(lang);
        }
        if (topicCode != null)
        {
            headline.setTopicCodes(topicCode);
        }
        if (coIds != null)
        {
            headline.setCompanyCodes(coIds);
        }

        _viewer.addHeadline(headline);
    }

    static synchronized void initializeFids(FieldDictionary dictionary)
    {
        if (FidsInitialized)
        {
            return;
        }

        FidDef def = dictionary.getFidDef("BCAST_TEXT");
        if (def == null)
        {
            return;
        }
        BCAST_TEXT = def.getFieldId();
        BCAST_TEXT_LENGTH = dictionary.isOMM() ? def.getMaxOMMLength() : def.getMaxMfeedLength();

        def = dictionary.getFidDef("DSPLY_NAME");
        if (def == null)
        {
            return;
        }
        DSPLY_NAME = def.getFieldId();

        def = dictionary.getFidDef("PNAC");
        if (def == null)
        {
            return;
        }
        PNAC = def.getFieldId();

        def = dictionary.getFidDef("ATTRIBTN");
        if (def == null)
        {
            return;
        }
        ATTRIBTN = def.getFieldId();

        def = dictionary.getFidDef("PROD_CODE");
        if (def == null)
        {
            return;
        }
        PROD_CODE = def.getFieldId();

        def = dictionary.getFidDef("TOPIC_CODE");
        if (def == null)
        {
            return;
        }
        TOPIC_CODE = def.getFieldId();

        def = dictionary.getFidDef("CO_IDS");
        if (def == null)
        {
            return;
        }
        CO_IDS = def.getFieldId();

        def = dictionary.getFidDef("LANG_IND");
        if (def == null)
        {
            return;
        }
        LANG_IND = def.getFieldId();

        def = dictionary.getFidDef("STORY_TIME");
        if (def == null)
        {
            return;
        }
        STORY_TIME = def.getFieldId();

        def = dictionary.getFidDef("STORY_DATE");
        if (def == null)
        {
            return;
        }
        STORY_DATE = def.getFieldId();

        FidsInitialized = true;
    }

    static boolean FidsInitialized = false;
    static short DSPLY_NAME;
    static short PNAC;
    static short BCAST_TEXT;
    static int BCAST_TEXT_LENGTH;
    static short ATTRIBTN;
    static short PROD_CODE;
    static short TOPIC_CODE;
    static short CO_IDS;
    static short LANG_IND;
    static short STORY_TIME;
    static short STORY_DATE;
}
