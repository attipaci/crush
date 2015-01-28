/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.gismo;

import kovacs.math.Vector2D;
import kovacs.util.Unit;
import crush.Mount;

public class Gismo extends AbstractGismo {
	/**
	 * 
	 */
	private static final long serialVersionUID = -895333794392208436L;
	
	public Gismo() {
		super("gismo", pixels);
		resolution = 16.7 * Unit.arcsec;
		
		arrayPointingCenter = (Vector2D) defaultPointingCenter.clone();
		
		// TODO calculate this?
		integrationTime = samplingInterval = 0.1 * Unit.sec;
	
		mount = Mount.LEFT_NASMYTH;
	}
	
	@Override
	public int pixels() { return pixels; }
	
	@Override
	public Vector2D getDefaultPointingCenter() { return defaultPointingCenter; }
	
	// Array is 16x8 (rows x cols);
	public static final int pixels = 128;
	
	private static Vector2D defaultPointingCenter = new Vector2D(8.5, 4.5); // row, col
	
}
