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
package crush.laboca;

import crush.*;
import java.lang.reflect.*;

public class LabocaHe3Response extends FieldResponse {

	static Field temperatureField;
	
	static { 
		try { temperatureField = LabocaFrame.class.getField("he3Temp"); }
		catch(NoSuchFieldException e) {
			System.err.println("WARNING! LabocaFrame has no such field.");
			e.printStackTrace();
		}
	}
	
	public LabocaHe3Response() { super(temperatureField, true); }
}
