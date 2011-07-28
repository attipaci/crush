/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package util.data;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import util.CoordinatePair;
import util.Vector2D;

public interface Grid2D<CoordinateType extends CoordinatePair> extends Cloneable {
	
	public boolean equals(Object o, double precision);
	
	public Grid2D<CoordinateType> copy();
	
	public CoordinateType getReference();
	
	public void setReference(CoordinateType c);
	
	public Vector2D getReferenceIndex();
	
	public void setReferenceIndex(Vector2D v);

	public double getPixelArea();
	
	public Vector2D getResolution();
	
	public double pixelSizeX();
	
	public double pixelSizeY();
	
	public void setResolution(double value);
	
	public void setResolution(double x, double y);
	
	public void toIndex(Vector2D v);
	
	public void toOffset(Vector2D v);
	
	public double[][] getTransform();
	
	public void setTransform(double[][] M);
	
	public void addCoordinateInfo(BasicHDU hdu) throws HeaderCardException;
	
	public void parseCoordinateInfo(Header header) throws Exception;
}
