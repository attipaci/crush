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
package crush.telescope;

import java.io.Serializable;

import jnum.Configurator;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.data.DataPoint;
import jnum.data.image.Asymmetry2D;

/*******************************************************************************
 * Copyright (c) 2012 Attila Kovacs <attila[AT]sigmyne.com>.
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

public class InstantFocus implements Serializable, Cloneable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4275858972947351486L;
	private DataPoint x, y, z;
	
	public InstantFocus() {	}
	
	public InstantFocus(InstantFocus other) {
		this();
		copy(other);		
	}
	
	public void copy(InstantFocus other) {
		x = other.x;
		y = other.y;
		z = other.z;
	}
	
	@Override
	public InstantFocus clone() {
		try { return (InstantFocus) super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public DataPoint getX() { return x; }
	
	public DataPoint getY() { return y; }
	
	public DataPoint getZ() { return z; }
	
	public void setX(DataPoint value) { this.x = value; }
	
	public void setY(DataPoint value) { this.y = value; }
	
	public void setZ(DataPoint value) { this.z = value; }
	
	public void deriveFrom(Asymmetry2D asym, DataPoint xElongation, Configurator options) {
		x = y = z = null;
		
		if(options.hasOption("focus.elong0")) 
			xElongation.add(-0.01 * options.option("focus.elong0").getDouble());
		
		double s2n = options.hasOption("focus.significance") ? options.option("focus.significance").getDouble() : 2.0;
			
		if(asym != null) {
			if(options.hasOption("focus.xcoeff")) if(asym.getX().significance() > s2n) {
				x = new DataPoint(asym.getX());
				x.scale(-Unit.mm / options.option("focus.xcoeff").getDouble());
				if(options.hasOption("focus.xscatter")) 
					x.setRMS(ExtraMath.hypot(x.rms(), options.option("focus.xscatter").getDouble() * Unit.mm));
			}
			if(options.hasOption("focus.ycoeff")) if(asym.getY().significance() > s2n) {
				y = new DataPoint(asym.getY());
				y.scale(-Unit.mm / options.option("focus.ycoeff").getDouble());			
				if(options.hasOption("focus.yscatter")) 
					y.setRMS(ExtraMath.hypot(y.rms(), options.option("focus.yscatter").getDouble() * Unit.mm));
			}
		}
		if(xElongation != null) if(xElongation.significance() > s2n) if(options.hasOption("focus.zcoeff")) {
			z = new DataPoint(xElongation);			
			z.scale(-Unit.mm / options.option("focus.zcoeff").getDouble());
			if(options.hasOption("focus.zscatter")) 
				z.setRMS(ExtraMath.hypot(z.rms(), options.option("focus.zscatter").getDouble() * Unit.mm));
		}	
	}
	
	public boolean isValid() { 
		if(x != null) return true;
		if(y != null) return true;
		if(z != null) return true;
		return false;
	}
	
	public boolean isComplete() {
		if(x == null) return false;
		if(y == null) return false;
		if(z == null) return false;
		return true;
	}
	
}
