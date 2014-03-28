package com.reuters.rfa.example.framework.sub;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.reuters.rfa.common.QualityOfService;
import com.reuters.rfa.omm.OMMArray;
import com.reuters.rfa.omm.OMMElementEntry;
import com.reuters.rfa.omm.OMMElementList;
import com.reuters.rfa.omm.OMMEntry;
import com.reuters.rfa.omm.OMMFilterEntry;
import com.reuters.rfa.omm.OMMFilterList;
import com.reuters.rfa.omm.OMMQos;
import com.reuters.rfa.omm.OMMTypes;
import com.reuters.rfa.rdm.RDMService;

/**
 * RDMServiceInfo represents a single RDMService.
 * 
 */
public class RDMServiceInfo implements ServiceInfo
{
    final String _serviceName;
    Map<String, Object> _elementListCache;

    public RDMServiceInfo(String serviceName)
    {
        _serviceName = serviceName;
        _elementListCache = new HashMap<String, Object>();
    }

    /*
     * Extract OMMElementLists from OMMFilterList and stores the data into a map
     * The data is stored as string, array of String, or array of QoS
     */
    public void process(OMMFilterList flist)
    {
        for (Iterator<?> fiter = flist.iterator(); fiter.hasNext();)
        {
            OMMFilterEntry fentry = (OMMFilterEntry)fiter.next();
        
            // save State, AcceptingRequests
            if (fentry.getFilterId() == RDMService.FilterId.STATE)
            {
                OMMElementList elist = (OMMElementList)fentry.getData();
                for (Iterator<?> eiter = elist.iterator(); eiter.hasNext();)
                {
                    OMMElementEntry ee = (OMMElementEntry)eiter.next();
                    _elementListCache.put(ee.getName(), ee.getData().toString() );
                }
                continue;
            }

            if (fentry.getFilterId() != RDMService.FilterId.INFO)
                continue;

            OMMElementList elist = (OMMElementList)fentry.getData();

            for (Iterator<?> eiter = elist.iterator(); eiter.hasNext();)
            {
                OMMElementEntry ee = (OMMElementEntry)eiter.next();
                if (ee.getDataType() == OMMTypes.ARRAY)
                {
                    OMMArray array = (OMMArray)ee.getData();
                    if (array.getDataType() == OMMTypes.QOS)
                    {
                        QualityOfService[] qosArray = new QualityOfService[array.getCount()];
                        int i = 0;
                        for (Iterator<?> iter = array.iterator(); iter.hasNext();)
                        {
                            qosArray[i++] = ((OMMQos)((OMMEntry)iter.next()).getData()).toQos();
                        }
                    }
                    else
                    {
                        String[] newStringArray = new String[array.getCount()];
                        int i = 0;
                        for (Iterator<?> aiter = array.iterator(); aiter.hasNext(); i++)
                        {
                            newStringArray[i] = ((OMMEntry)aiter.next()).getData().toString();
                        }
                        _elementListCache.put(ee.getName(), newStringArray);
                    }
                }
                else
                {
                    _elementListCache.put(ee.getName(), ee.getData().toString());
                }
            }
        }
    }

    public String getServiceName()
    {
        return _serviceName;
    }

    /**
     * @param key The key is an element name from
     *            {@link com.reuters.rfa.rdm.RDMService}
     * @return string or an array of string or an array of QoS depending on the
     *         key
     */
    public Object get(String key)
    {
        return _elementListCache.get(key);
    }

    public Iterator<String> iterator()
    {
        return _elementListCache.keySet().iterator();
    }

}
