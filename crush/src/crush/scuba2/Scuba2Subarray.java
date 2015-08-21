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

package crush.scuba2;



import crush.Channel;
import kovacs.math.Vector2D;
import kovacs.util.*;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.FitsException;

public class Scuba2Subarray implements Cloneable {
	Scuba2 scuba2;
	String id;
	int channelOffset;
	
	Vector2D focalPlanePixelOffset = new Vector2D();
	double orientation = 0.0;
	boolean isMirrored = false;

	double scaling = 1.0;
	

	
	
	public Scuba2Subarray(Scuba2 parent, String id) {
		scuba2 = parent;
		this.id = id;
		setOptions();
	}
	
	@Override 
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public Scuba2Subarray copy() {
		Scuba2Subarray copy = (Scuba2Subarray) clone();
		if(focalPlanePixelOffset != null) copy.focalPlanePixelOffset = (Vector2D) focalPlanePixelOffset.clone();
		if(id != null) copy.id = new String(id);
		return copy;
	}

	public Vector2D getPhysicalPixelPosition(double row, double col) {
		Vector2D position = new Vector2D();
		getPhysicalPixelPosition(row, col, position);
		return position;
	}
	
	public void getPhysicalPixelPosition(double row, double col, Vector2D position) {
		position.set((isMirrored ? -1.0 : 1.0) * col, row);
		position.rotate(orientation);
		position.add(focalPlanePixelOffset);
		position.scale(scuba2.physicalPixelSize);
	}
	
	public void setOptions() {
		if(scuba2.hasOption(id + ".rotation")) orientation = scuba2.option(id + ".rotation").getDouble() * Unit.deg;
		if(scuba2.hasOption(id + ".position")) focalPlanePixelOffset = scuba2.option(id + ".position").getVector2D();
		isMirrored = scuba2.hasOption("mirror");
	}
	
	public Scuba2Pixel getPixel(int subarrayIndex) {
		return scuba2.get(channelOffset + subarrayIndex);
	}
	
	protected void parseFlatcalHDU(BinaryTableHDU hdu) throws FitsException {
		int col = hdu.findColumn("DATA");
		if(col < 0) {
			System.err.println(" WARNING! flatfield data not found...");
			return;
		}
		double[][][] data = (double[][][]) hdu.getRow(0)[col];
		
		for(int c=Scuba2Subarray.PIXELS; --c >= 0; ) {
			Scuba2Pixel pixel = getPixel(c);
			if(Double.isNaN(data[0][pixel.col % Scuba2.COLS][pixel.row % Scuba2.ROWS])) pixel.flag(Channel.FLAG_DEAD);
		}
		
	}
	
	public static int PIXELS = Scuba2.ROWS * Scuba2.COLS;
}
