/*******************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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
package crush.instrument;

import java.lang.reflect.*;

import crush.Frame;

public class FieldResponse extends FrameResponse<Frame> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4490473787009977735L;
	private Field field;
    
    public FieldResponse(Field field) {
        this(field, false);
    }
    
    public FieldResponse(Field field, boolean isFloating) {
        super(isFloating);
        this.field = field;
    }
    
    public Field getField() { return field; }

    @Override
    protected double getValue(Frame exposure) throws Exception {
        return field.getDouble(exposure);
    }

}
