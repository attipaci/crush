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
package crush.instrument.laboca;

import crush.*;

import java.lang.reflect.*;

public class LabocaHe3Response extends FieldResponse {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4390450615227012414L;
	static Field temperatureField;
	
	private static final String he3FieldName = "he3Temp";
	
	static { 
		try { temperatureField = LabocaFrame.class.getField(he3FieldName); }
		catch(NoSuchFieldException e) {
			CRUSH.warning(null, LabocaFrame.class.getSimpleName() + " has no field named '" + he3FieldName + ".");
			CRUSH.trace(e);
		}
	}
	
	public LabocaHe3Response() { super(temperatureField, true); }
	
	
}
