/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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

import java.lang.reflect.Field;

import crush.Channel;
import crush.Mode;

public class FieldGainProvider implements GainProvider {
	private Field gainField;
	
	public FieldGainProvider(Field f) {
		this.gainField = f;
	}
	
	@Override
	public double getGain(Channel c) throws IllegalAccessException {
		return gainField.getDouble(c);
	}
	
	@Override
	public void setGain(Channel c, double value) throws IllegalAccessException {
		Class<?> fieldClass = gainField.getClass();
		if(fieldClass.equals(float.class)) gainField.setFloat(c, (float) value);
		else gainField.setDouble(c, value);
	}
	
	@Override
	public void validate(Mode mode) throws Exception {}
	
}
