/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.mako;


import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;


public class MakoPixel extends AbstractMakoPixel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 224302807477613438L;



	public MakoPixel(Mako array, int zeroIndex) {
		super(array, zeroIndex);
		row = zeroIndex / Mako.cols;
		col = zeroIndex % Mako.cols;
	}
	
	@Override
	public void setRowCol(int row, int col) {
		this.row = row;
		this.col = col;
		setFixedIndex(row * Mako.cols + col);
	}	
	
	
	@Override
	public void calcNominalPosition() {
		position = ((Mako) instrument).getPixelPosition(((Mako) instrument).pixelSize, row, col);
	}
	
	
	@Override
	public void parseValues(SmartTokenizer tokens, int criticalFlags) {
		tokens.nextToken(); // fixed index -- set by pixel matching...
		super.parseValues(tokens, criticalFlags);
		if(tokens.hasMoreTokens()) coupling = tokens.nextDouble();
	}
	
	@Override
	public String toString() {
		return getID() + "\t" + super.toString() + "\t" + Util.f3.format(coupling);
	}
	
	
	
	public static Vector2D defaultSize = new Vector2D(3.86 * Unit.arcsec, 7.21 * Unit.arcsec);



	
}
