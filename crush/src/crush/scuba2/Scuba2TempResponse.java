/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of the proprietary SCUBA-2 modules of crush.
 * 
 * You may not modify or redistribute this file in any way. 
 * 
 * Together with this file you should have received a copy of the license, 
 * which outlines the details of the licensing agreement, and the restrictions
 * it imposes for distributing, modifying or using the SCUBA-2 modules
 * of CRUSH-2. 
 * 
 * These modules are provided with absolutely no warranty.
 ******************************************************************************/
package crush.scuba2;

import crush.*;
import java.lang.reflect.*;

public class Scuba2TempResponse extends FieldResponse {

	static Field temperatureField;
	
	static { 
		try { temperatureField = Scuba2Frame.class.getField("detectorT"); }
		catch(NoSuchFieldException e) {
			System.err.println("WARNING! Scuba2Frame has no such field.");
			e.printStackTrace();
		}
	}
	
	public Scuba2TempResponse() { super(temperatureField); }
}

