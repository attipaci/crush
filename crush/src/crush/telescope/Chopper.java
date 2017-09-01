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

import crush.PhaseSet;
import jnum.Copiable;
import jnum.Unit;
import jnum.Util;
import jnum.math.*;


public class Chopper implements Serializable, Cloneable, Copiable<Chopper> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2142368367368186753L;
	public int positions = 0; // 0 for indeterminate, -1 for sweeping mode
	public double frequency = Double.NaN;
	public double amplitude = 0.0;
	public double efficiency = Double.NaN;
	public double angle = Double.NaN;
	public Vector2D offset = new Vector2D();
	public PhaseSet phases;
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
	public Chopper copy() {
		Chopper copy = (Chopper) clone();
		copy.phases = null;
		copy.offset = offset.copy();
		return copy;
	}
	
	public double stareDuration() {
		return efficiency / (positions * frequency);		
	}
	
	@Override
	public String toString() {
		return "+/-" + Util.f1.format(amplitude / Unit.arcsec) + "\" at " 
		+ Util.f1.format(angle / Unit.deg) + " deg, "
		+ Util.f3.format(frequency / Unit.Hz) + " Hz, " 
		+ Util.f1.format(100.0 * efficiency) + "%";
	}
}
