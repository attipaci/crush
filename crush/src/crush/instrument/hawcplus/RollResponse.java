/*******************************************************************************
 * Copyright (c) 2018 Attila Kovacs <attila[AT]sigmyne.com>.
 * All rights reserved. 
 * 
 * This file is part of crush.
 * 
 *     crush is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     crush is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with crush.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush.instrument.hawcplus;

import crush.*;

import java.lang.reflect.*;

public class RollResponse extends FieldResponse {
    /**
     * 
     */
    private static final long serialVersionUID = 4687041979297880692L;

    static Field field;
    
    private static final String fieldName  = "roll";
    
    static { 
        try { field = HawcFrame.class.getField(fieldName); }
        catch(NoSuchFieldException e) {
            CRUSH.warning(null, HawcFrame.class.getSimpleName() + " has no field named '" + fieldName + "'.");
            CRUSH.trace(e);
        }
    }
    
    public RollResponse() { 
        super(field, true);
        setDerivative(2);
    }
    
}