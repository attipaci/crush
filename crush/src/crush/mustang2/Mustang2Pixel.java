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

package crush.mustang2;

import crush.array.SingleColorPixel;

public class Mustang2Pixel extends SingleColorPixel {
	public int readoutIndex;				// COL
	public int muxIndex;					// ROW
	public double frequency;
	public double attenuation;
	
	public double readoutGain = 1.0;
		

	public Mustang2Pixel(Mustang2 instrument, int backendIndex) {
		super(instrument, backendIndex);
	}
	
	public final static int FLAG_MUX = 1 << nextSoftwareFlag++;

}
