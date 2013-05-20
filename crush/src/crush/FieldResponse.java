/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
package crush;

import java.lang.reflect.*;

public class FieldResponse extends Response {
	private Field field;
	private boolean isFloating = false;
	
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
	
	@Override
	public Signal getSignal(Integration<?, ?> integration) {
		double[] data = new double[integration.size()];	
		try {
			for(int t=data.length; --t >= 0; ) {
				final Frame exposure = integration.get(t);
				data[t] = exposure == null ? Double.NaN : field.getDouble(exposure);
			}
		}
		catch(Exception e) { System.err.println("WARNING! No field named " + field.getName() + " for signal."); }
		return new Signal(this, integration, data, isFloating);
	}

}
