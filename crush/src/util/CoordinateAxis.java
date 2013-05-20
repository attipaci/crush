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
// Copyright (c) 2007 Attila Kovacs 

package util;

import nom.tam.fits.*;
import nom.tam.util.*;

import java.text.*;

//Add visualizable...

public class CoordinateAxis implements Cloneable {
	public String label;
	public String wcsName; // TODO newly added...
	public NumberFormat format;
	public boolean reverse = false;
	public double reverseFrom = 0.0;
	
	public double[] multiples; // The multiples of the fundamental tickunits that can be used.
	public boolean magnitudeScaling = true; // If the ticks can be scaled by powers of 10 also.
	public double majorTick, minorTick; // The actual ticks.
	
	public CoordinateAxis() { this("unspecified axis");}

	public CoordinateAxis(String text) { 
		defaults();
		setLabel(text); 
	}
	
	public CoordinateAxis(String text, String fitsID) { 
		this(text);
		wcsName = fitsID;
	}
	
	@Override
	public Object clone() { 
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public void defaults() {
		//unit = Unit.unity;
		reverse = false;
		multiples = new double[] { 1.0, 2.0, 5.0 }; // The multiples of the fundamental tickunits that can be used.
		magnitudeScaling = true;
	}

	public void setLabel(String text) { label = text; }

	public String getLabel() { return label; }

	public void setFormat(NumberFormat nf) { format = nf; }
	
	public NumberFormat getFormat() { return format; }
	
	public void setReverse(boolean value) { setReverse(value, 0.0); }
	
	public void setReverse(boolean value, double from) { reverse = value; reverseFrom = from; }
	
	public boolean isReverse() { return reverse; }
	
	public String format(double value) { return format.format(reverse ? reverseFrom - value : value); }
	
	public void edit(Cursor cursor, String id) throws HeaderCardException {
		cursor.add(new HeaderCard("CTYPE" + id, wcsName, "Description of coordinate axis."));
		//cursor.add(new HeaderCard("CUNIT" + id, unit.name, "Coordinate units used."));	
	}
	
	// TODO
	// does not read label and format information...
	// should use getDefaults("wcsName")?
	public void parse(Header header, String id) {
		wcsName = header.getStringValue("CTYPE" + id);
		//unit = Unit.get("CUNIT" + "id");
		//if(unit == null) unit = Unit.unity;
	}
	
}

