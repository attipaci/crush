/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.mako2;

import crush.mako.Mako;
import crush.mako.AbstractMakoPixel;
import crush.resonators.FrequencyID;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;

public class Mako2Pixel extends AbstractMakoPixel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6292895881603515328L;

	public Mako2Pixel(Mako2 array, int zeroIndex) {
		super(array, zeroIndex);
		this.array = Mako2.ARRAY_350;
	}

	@Override
	public void setFrequencyID(FrequencyID id) {
		super.setFrequencyID(id);
		row = -1;
		col = -1;
		
		if(id != null) position = ((Mako2PixelID) id).position;
		
		if(position == null) flag(FLAG_UNASSIGNED);
		else unflag(FLAG_UNASSIGNED);
	}
	
	@Override
	public void setRowCol(int row, int col) {
		this.row = row;
		this.col = col;
		setFixedIndex(row * Mako.cols + col);
	}	
	

	@Override
	public void calcNominalPosition() {
		if(row == -1 || col == -1) return;		
		position = ((Mako2) instrument).getPixelPosition(((Mako2) instrument).pixelSize, array, row, col);
	}
	
	@Override
	public boolean isAssigned() {
		if(getFrequencyID() == null) return false;
		if(position == null) return false;
		return true;
	}

	@Override
	public void parseValues(SmartTokenizer tokens, int criticalFlags) {
		tokens.nextToken(); // fixed index -- set by pixel matching...
		tokens.nextToken(); // subarray string...
		super.parseValues(tokens, criticalFlags);
		if(tokens.hasMoreTokens()) coupling = tokens.nextDouble();
	}
	
	public String getSubarrayString() {
		if(array == Mako2.ARRAY_350) return "350um";
		else if(array == Mako2.ARRAY_850) return "850um";
		return "???";
	}
	
	@Override
	public String toString() {
		return getID() + "\t" + getSubarrayString() + "\t" + super.toString() + "\t" + Util.f3.format(coupling);
	}
	
	public static Vector2D defaultSize = new Vector2D(4.00 * Unit.arcsec, 3.46 * Unit.arcsec);
	
}

