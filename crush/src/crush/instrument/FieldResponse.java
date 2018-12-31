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
package crush.instrument;

import java.lang.reflect.*;

import crush.Frame;
import crush.Integration;
import crush.Signal;


public class FieldResponse extends Response {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4490473787009977735L;
	private Field field;
	private boolean isFloating = false;
	private int derivative = 0;
	
	public FieldResponse(Field field) {
		this.field = field;
	}
	
	public FieldResponse(Field field, boolean isFloating) {
		this(field);
		this.isFloating = isFloating;
	}
	
	public Field getField() { return field; }
	
	public boolean isFloating() { return isFloating; }
	
	public void setFloating(boolean value) { isFloating = value; }
	
	public void setDerivative(int n) {
	    derivative = n;
	}
	
	@Override
	public Signal getSignal(Integration<?, ?> integration) {
		float[] data = new float[integration.size()];	
		try {
			for(int t=data.length; --t >= 0; ) {
				final Frame exposure = integration.get(t);
				data[t] = exposure == null ? Float.NaN : field.getFloat(exposure);
			}
		}
		catch(Exception e) { integration.warning("No field named " + field.getName() + " for signal."); }
		Signal s = new Signal(this, integration, data, isFloating);
		for(int i=derivative; --i >= 0; ) s.differentiate();
		return s;
	}

}
