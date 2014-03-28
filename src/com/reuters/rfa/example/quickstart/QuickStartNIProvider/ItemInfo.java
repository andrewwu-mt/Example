package com.reuters.rfa.example.quickstart.QuickStartNIProvider;

import com.reuters.rfa.common.Token;

// ItemInfo is a class that contains all the relevant information regarding to an
// item.  It is used as the source of canned data for the QSProviderDemo.
public class ItemInfo
{
	String _name;

	double _trdPrice= 10.0000;
	double _bid= 9.8000; 
	double _ask= 10.2000;
	long _acvol= 100;
	Token _token;

	public void setToken(Token t) { _token = t;    }
	public Token getToken() { return _token; }


	public void setName(String name) { _name = name;	}
	public String getName() { return _name; }


	public double getTradePrice1() { return _trdPrice; }
	public double getBid() { return _bid; }
	public double getAsk() { return _ask; }
	public long getACVol1() { return _acvol; }

    public void increment()
    {
    	if ((_trdPrice>=100.0000) || (_bid >= 100.0000) || (_ask >= 100.0000))
    	{
    		// reset prices
    		_trdPrice = 10.0000;
    		_bid = 9.8000;
    		_ask = 10.2000;
    	}
    	else
    	{
            _trdPrice += 0.0500; 
            _bid += 0.0500;  
            _ask += 0.0500; 
    	}

        if (_acvol<1000000)
            _acvol +=50;
        else
            _acvol = 100;
    }

}
