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
// Copyright (c) 2009 Attila Kovacs 

package crush.instrument.sharc2;

import crush.telescope.HorizontalFrame;

public class Sharc2Frame extends HorizontalFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1628223917919186907L;
	int frameNumber;
	int telescopeFlags = 0;
	double dspTime;
	
	public Sharc2Frame(Sharc2Scan parent) {
		super(parent);
		create(Sharc2.pixels);
	}
	
	public void parseData(float[][] value) {
		for(int bol=0; bol<Sharc2.pixels; bol++)
			data[bol] = value.length == 12 ? -value[bol/32][bol%32] : -value[bol/12][bol%12];		
	}

	public void parseData(float[] value, int from, int channels) {
		for(int c=channels; --c >= 0; ) data[c] = -value[from + c];
	}
}
