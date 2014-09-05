/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.mako;

import java.util.StringTokenizer;

import kovacs.util.Util;


public class Mako1Pixel extends MakoPixel {
	
	public Mako1Pixel(Mako1 array, int zeroIndex) {
		super(array, zeroIndex);
		row = zeroIndex / Mako1.cols;
		col = zeroIndex % Mako1.cols;
		
		// TODO Auto-generated constructor stub
	}

	
	@Override
	public void setRowCol(int row, int col) {
		this.row = row;
		this.col = col;
		setFixedIndex(row * Mako1.cols + col);
	}	
	
	
	@Override
	public void calcNominalPosition() {
		position = ((Mako1) instrument).getPixelPosition(size, row, col);
	}
	
	
	@Override
	public void parseValues(StringTokenizer tokens, int criticalFlags) {
		tokens.nextToken(); // fixed index -- set by pixel matching...
		super.parseValues(tokens, criticalFlags);
		if(tokens.hasMoreTokens()) coupling = Double.parseDouble(tokens.nextToken());
	}
	
	@Override
	public String toString() {
		return getID() + "\t" + super.toString() + "\t" + Util.f3.format(coupling);
	}
	
}
