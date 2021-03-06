/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.server.types.common.funcs;

import com.foundationdb.server.types.LazyList;
import com.foundationdb.server.types.TClass;
import com.foundationdb.server.types.TCustomOverloadResult;
import com.foundationdb.server.types.TExecutionContext;
import com.foundationdb.server.types.TInstance;
import com.foundationdb.server.types.TScalar;
import com.foundationdb.server.types.TOverloadResult;
import com.foundationdb.server.types.TPreptimeContext;
import com.foundationdb.server.types.TPreptimeValue;
import com.foundationdb.server.types.value.ValueSource;
import com.foundationdb.server.types.value.ValueTarget;
import com.foundationdb.server.types.texpressions.TInputSetBuilder;
import com.foundationdb.server.types.texpressions.TScalarBase;

import java.util.List;

public abstract class Pad extends TScalarBase
{
    public static TScalar[] create(TClass stringType, TClass intType, TClass textType)
    {
        return new TScalar[]
        {
            new Pad(stringType, intType, textType, "LPAD") // prepend
            {
                @Override
                String doPadding(String st, int times, String toAdd)
                {
                    StringBuilder prefix = new StringBuilder();                    
                    int delta = times - st.length();
                    int limit = delta / toAdd.length();
                    int remain = delta % toAdd.length();

                    while (limit-- > 0)
                        prefix.append(toAdd);
                    for (int n = 0; n < remain; ++n)
                        prefix.append(toAdd.charAt(n));
                    
                    return prefix.append(st).toString();
                }   
            },
            new Pad(stringType, intType, textType, "RPAD") // append
            {
                @Override
                String doPadding(String st, int times, String toAdd)
                {
                    StringBuilder ret = new StringBuilder(st);
                    int delta = times - st.length();
                    int limit = delta / toAdd.length();
                    int remain = delta % toAdd.length();
                    
                    while (limit-- > 0)
                        ret.append(toAdd);
                    for (int n = 0; n < remain; ++n)
                        ret.append(toAdd.charAt(n));

                    return ret.toString();
                }
            }
        };
    }

    abstract String doPadding(String st, int times, String toAdd);
    
    private final TClass stringType;
    private final TClass intType;
    private final TClass textType;
    private final String name;
    
    Pad(TClass stringType, TClass intType, TClass textType, String name)
    {
        this.stringType = stringType;
        this.intType = intType;
        this.textType = textType;
        this.name = name;
    }
    
    @Override
    protected void doEvaluate(TExecutionContext context, LazyList<? extends ValueSource> inputs, ValueTarget output)
    {
        String st = inputs.get(0).getString();
        int length = inputs.get(1).getInt32();
        String toAdd = inputs.get(2).getString();
        
        if (length < 0)
            output.putNull();
        else if (length <= st.length())
            output.putString(st.substring(0, length), null);
        else if (toAdd.isEmpty())
            output.putNull();
        else
            output.putString(doPadding(st, length, toAdd), null);
    }
    
    @Override
    protected void buildInputSets(TInputSetBuilder builder)
    {
        builder.covers(stringType, 0, 2).covers(intType, 1);
    }

    @Override
    public String displayName()
    {
        return name;
    }

    @Override
    public TOverloadResult resultType()
    {
        return TOverloadResult.custom(new TCustomOverloadResult()
        {
            @Override
            public TInstance resultInstance(List<TPreptimeValue> inputs, TPreptimeContext context)
            {
                ValueSource len = inputs.get(1).value();
                
                // if the argument isn't available
                // return LONGTEXT 
                if (len == null)
                    return textType.instance(anyContaminatingNulls(inputs));
                else if (len.isNull())
                    return stringType.instance(0, anyContaminatingNulls(inputs));
                else
                    return stringType.instance(len.getInt32(), anyContaminatingNulls(inputs));
            }
        });
    }
    
}
