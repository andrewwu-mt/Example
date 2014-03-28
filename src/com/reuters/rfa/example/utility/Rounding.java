package com.reuters.rfa.example.utility;

import com.reuters.rfa.internal.rwf.RwfUtil;
import com.reuters.rfa.omm.OMMException;
import com.reuters.rfa.omm.OMMNumeric;

public class Rounding
{
    static public int roundFloat2Int(float f, int exponentHint)
    {
        validateHint(exponentHint);

        double dval = f * RwfUtil.ExpToHint[exponentHint - OMMNumeric.EXPONENT_NEG14];
        float fval = (float)dval;

        int intValue = Math.round(fval);
        return intValue;
    }

    static public long roundDouble2Long(double d, int exponentHint)
    {
        validateHint(exponentHint);

        double dval = d * RwfUtil.ExpToHint[exponentHint - OMMNumeric.EXPONENT_NEG14];

        long longValue = Math.round(dval);
        return longValue;
    }

    static void validateHint(int hint)
    {
        if (hint > OMMNumeric.MAX_HINT)
            throw new OMMException("Error: Hint " + hint + " is out of range!");

    }
}
