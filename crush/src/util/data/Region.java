/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2009 Attila Kovacs 

package util.data;

import java.text.*;

import util.CoordinatePair;

public abstract class Region<CoordinateType extends CoordinatePair> implements Cloneable {
	private String id;
	private String comment = "";
	
	public static int counter = 1;
	
	public Region() { id = "[" + (counter++) + "]"; }
	
	public Region(String line, GridImage<CoordinateType> forImage) throws ParseException { parse(line, forImage); }
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public String getID() { return id; }
	
	public void setID(String id) { this.id = id; }
	
	public String getComment() { return comment; }
	
	public void setComment(String value) { comment = value; }
	
	public void addComment(String value) { comment += value; }
	
	public abstract Bounds getBounds(GridImage<CoordinateType> image);
	
	public abstract boolean isInside(Grid2D<CoordinateType> grid, double i, double j);	
	
	public abstract void parse(String line, GridImage<CoordinateType> forImage) throws ParseException;
	
	public abstract String toString(GridImage<CoordinateType> image);
	
	public WeightedPoint getIntegral(GridImage<CoordinateType> map) {
		final Bounds bounds = getBounds(map);
		WeightedPoint sum = new WeightedPoint();
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) if(map.isUnflagged(i, j)) {
			sum.value += map.getValue(i, j);	
			sum.weight += 1.0 / map.getWeight(i, j);
		}
		sum.weight = 1.0 / sum.weight;	
		return sum;
	}
	
	public WeightedPoint getFlux(GridImage<CoordinateType> map) {
		WeightedPoint integral = getIntegral(map);
		integral.scale(map.getPixelArea() / map.getImageBeamArea());
		return integral;
	}
	
}
