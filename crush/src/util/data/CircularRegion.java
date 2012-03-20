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

import util.*;
import util.text.AngleFormat;
import util.text.TableFormatter;

import java.text.*;
import java.util.*;

public class CircularRegion<CoordinateType extends CoordinatePair> extends Region<CoordinateType> implements TableFormatter.Entries {
	private CoordinateType coords;
	private DataPoint radius;
	
	private Grid2D<CoordinateType> useGrid = null;
	private Vector2D gridIndex = new Vector2D();

	public CircularRegion() {}

	@SuppressWarnings("unchecked")
	public CircularRegion(GridImage<CoordinateType> image, Vector2D offset, double r) {
		coords = (CoordinateType) image.getReference().clone();
		image.getProjection().deproject(offset, coords);
		radius.value = r;
		radius.setRMS(Math.sqrt(image.getPixelArea()) / (GridImage.fwhm2size * Util.sigmasInFWHM));
	}
	
	public CircularRegion(CoordinateType coords, double r) {
		setCenter(coords);
		radius = new DataPoint();
		radius.value = r;
		radius.weight = 0.0;
	}
	
	public CircularRegion(String line, GridImage<CoordinateType> forImage) throws ParseException { super(line, forImage); }
	
	@Override
	public Object clone() {
		CircularRegion<?> clone = (CircularRegion<?>) super.clone();
		clone.useGrid = null;
		clone.gridIndex = new Vector2D();
		return clone;
	}
	
	public Vector2D getIndex(Grid2D<CoordinateType> grid) {
		if(useGrid != grid) indexFor(grid);
		return gridIndex;
	}
	
	public void setCenter(CoordinateType coords) {
		this.coords = coords;
		useGrid = null;
	}
	
	private void indexFor(Grid2D<CoordinateType> grid) {	
		grid.getIndex(coords, gridIndex);
		useGrid = grid;
	}
	
	@Override
	public Bounds getBounds(GridImage<CoordinateType> image) {
		Vector2D centerIndex = getIndex(image.getGrid());
		Bounds bounds = new Bounds();
		Vector2D resolution = image.getResolution();
		double deltaX = radius.value / resolution.getX();
		double deltaY = radius.value / resolution.getY();
		
		bounds.fromi = Math.max(0, (int)Math.floor(centerIndex.getX() - deltaX));
		bounds.toi = Math.min(image.sizeX()-1, (int)Math.ceil(centerIndex.getX() + deltaX));
		bounds.fromj = Math.max(0, (int)Math.floor(centerIndex.getY() - deltaY));
		bounds.toj = Math.min(image.sizeY()-1, (int)Math.ceil(centerIndex.getY() + deltaY));
		
		return bounds;
	}

	public double distanceTo(Metric<CoordinateType> pos) {
		return pos.distanceTo(coords);
	}

	@Override
	public boolean isInside(Grid2D<CoordinateType> grid, double i, double j) {
		Vector2D centerIndex = getIndex(grid);
		return Math.hypot(centerIndex.getX() - i, centerIndex.getY() - j) <= radius.value;
	}
	
	public void moveToPeak(GridImage<CoordinateType> map) throws IllegalStateException {
		Bounds bounds = getBounds(map);
		Vector2D centerIndex = getIndex(map.getGrid());
		
		if(!map.containsIndex(centerIndex.getX(), centerIndex.getY())) throw new IllegalStateException("Region falls outside of map.");
		Index2D index = new Index2D(centerIndex);
		
		double significance = map.getS2N(index.i(), index.j());
		
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(map.isUnflagged(i, j)) if(map.getS2N(i,j) > significance) if(isInside(map.getGrid(), i, j)) {
				significance = map.getS2N(i,j);
				index.set(i, j);			
			}
		
		if(map.isFlagged(index.i(), index.j())) throw new IllegalStateException("No valid peak in search area. ");
		
		centerIndex.setX(index.i());
		centerIndex.setY(index.j());
		
		if(isInside(map.getGrid(), index.i()+1, index.j())) if(isInside(map.getGrid(), index.i()-1, index.j())) 
			if(isInside(map.getGrid(), index.i(), index.j()+1)) if(isInside(map.getGrid(), index.i(), index.j()-1)) finetunePeak(map);
	}
	
	public DataPoint finetunePeak(GridImage<CoordinateType> map) {
		Vector2D centerIndex = getIndex(map.getGrid());
		Data2D.InterpolatorData ipolData = new Data2D.InterpolatorData();
		
		int i = (int) Math.round(centerIndex.getX());
		int j = (int) Math.round(centerIndex.getY());
		
		double a=0.0,b=0.0,c=0.0,d=0.0;
			
		double y0 = map.getS2N(i,j);
	
		if(i>0 && i<map.sizeX()-1) if((map.getFlag(i+1, j) | map.getFlag(i-1, j)) == 0) {
			a = 0.5 * (map.getS2N(i+1,j) + map.getS2N(i-1,j)) - y0;
			c = 0.5 * (map.getS2N(i+1,j) - map.getS2N(i-1,j));
		}
		
		if(j>0 && j<map.sizeY()-1) if((map.getFlag(i, j+1) | map.getFlag(i, j-1)) == 0) {
			b = 0.5 * (map.getS2N(i,j+1) + map.getS2N(i,j-1)) - y0;
			d = 0.5 * (map.getS2N(i,j+1) - map.getS2N(i,j-1));
		}
		
		double di = (a == 0.0) ? 0.0 : -0.5*c/a;
		double dj = (b == 0.0) ? 0.0 : -0.5*d/b;	
		
		if(Math.abs(di) > 0.5) di = 0.0;
		if(Math.abs(dj) > 0.5) dj = 0.0;
		
		final double significance = y0 + (a*di + c)*di + (b*dj + d)*dj;			
		
		if(Math.abs(di) > 0.5 || Math.abs(dj) > 0.5) 
			throw new IllegalStateException("Position is not an S/N peak.");
		
		centerIndex.setX(i + di);
		centerIndex.setY(j + dj);
		
		double peak = map.valueAtIndex(i+di, j+dj, ipolData);
	
		return new DataPoint(peak, peak / significance);
	}

	
	@Override
	public String toString(GridImage<CoordinateType> image) {
		return toString(image, FORMAT_CRUSH);
	}
	
	public String toString(GridImage<CoordinateType> image, int format) {
		String line = null;
		
		switch(format) {
		case FORMAT_CRUSH : line = toCrushString(image); break;
		case FORMAT_OFFSET : line = toOffsetString(image); break;
		case FORMAT_GREG : line = toGregString(image); break;
		case FORMAT_DS9 : line = toDS9String(image); break;
		}
		
		String comment = getComment();
		if(comment.length() > 0) line += "\t#" + comment; 
		
		return line;
	}
	
	public CoordinateType getCoordinates() {
		return coords;
	}
	
	public void setCoordinates(CoordinateType coords) {
		this.coords = coords;
	}
	
	public DataPoint getRadius() { return radius; }
	
	public void setRadius(DataPoint r) { this.radius = r; }
	
	public void setRadius(double r) { 
		if(radius == null) radius = new DataPoint();
		else radius.weight = 0.0;
		radius.value = r;
	}
	
	public String toCrushString(GridImage<CoordinateType> image) {
		CoordinateType coords = getCoordinates();	
		
		if(coords instanceof SphericalCoordinates) {
			SphericalCoordinates spherical = (SphericalCoordinates) coords;
			((AngleFormat) spherical.coordinateSystem.get(0).format).colons();
			((AngleFormat) spherical.coordinateSystem.get(1).format).colons();
			return getID() + "\t" + coords.toString() + "  " + Util.f1.format(radius.value/Unit.arcsec) + " # " + getComment();
		}
		else return getID() + "\t" + coords.getX() + "\t" + coords.getY() + "\t" + radius.value + "\t# " + getComment();
	}

	public String toGregString(GridImage<CoordinateType> image) {
		CoordinatePair offset = new CoordinatePair();
		useGrid.projection.project(coords, offset);
		
		return "ellipse " + Util.f1.format(radius.value/Unit.arcsec) + " /user " +
		Util.f1.format(offset.getX() / Unit.arcsec) + " " + Util.f1.format(offset.getY() / Unit.arcsec);
	}
	
	public String toDS9String(GridImage<CoordinateType> image) {
		CoordinateType coords = getCoordinates();
	
		if(coords instanceof SphericalCoordinates) {
			SphericalCoordinates spherical = (SphericalCoordinates) coords;
			((AngleFormat) spherical.coordinateSystem.get(0).format).colons();
			((AngleFormat) spherical.coordinateSystem.get(1).format).colons();
		
			return "circle(" 
				+ spherical.coordinateSystem.get(0).format(coords.getX()) + ","
				+ spherical.coordinateSystem.get(1).format(coords.getY()) + ","
				+ Util.f3.format(radius.value / Unit.arcsec) + "\")";
		}
		else return "circle(" + coords.getX() + "," + coords.getY() + "," + radius.value + ")";		
	}
	
	
	public String toOffsetString(GridImage<CoordinateType> image) {
		Vector2D offset = new Vector2D();
		image.getProjection().project(coords, offset);
	
		if(coords instanceof SphericalCoordinates) {
			SphericalCoordinates reference = (SphericalCoordinates) useGrid.getReference();
			CoordinateAxis x = reference.localCoordinateSystem.get(0);
			CoordinateAxis y = reference.localCoordinateSystem.get(1);
		
			return x.label + " = " + x.format(offset.getX()) + "\t" + y.label + " = " + y.format(offset.getY());
		}
		else return "dx = " + offset.getX() + "\tdy = " + offset.getY();
	}
	
	@Override
	public void parse(String line, GridImage<CoordinateType> forImage) throws ParseException {
		parseCrush(line, forImage);
	}
	
	public void parse(String line, int format, GridImage<CoordinateType> forImage) {	
		switch(format) {
		case FORMAT_CRUSH : parseCrush(line, forImage); break;
		//case FORMAT_OFFSET : parseOffset(line); break;
		case FORMAT_GREG : parseGreg(line, forImage); break;
		case FORMAT_DS9 : parseDS9(line, forImage); break;
		}
	}
	
	public StringTokenizer parseCrush(String line, GridImage<CoordinateType> forImage) {
		CoordinateType coords = getCoordinates();
		
		StringTokenizer tokens = new StringTokenizer(line);
		setID(tokens.nextToken());
		
		if(coords instanceof SphericalCoordinates) {
			SphericalCoordinates spherical = (SphericalCoordinates) coords;
			((AngleFormat) spherical.coordinateSystem.get(0).format).colons();
			((AngleFormat) spherical.coordinateSystem.get(1).format).colons();
		
			coords.parse(tokens.nextToken() + " " + tokens.nextToken() + " " + tokens.nextToken());
			setCenter(coords);
			radius.value = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
			
		}
		else {
			coords.setX(Double.parseDouble(tokens.nextToken()));
			coords.setY(Double.parseDouble(tokens.nextToken()));			
		}
		
		
		radius.weight = 0.0;	
		
		if(line.contains("#")) setComment(line.substring(line.indexOf('#') + 2));
	
		return tokens;
	}

	public void parseGreg(String line, GridImage<CoordinateType> forImage) {
		StringTokenizer tokens = new StringTokenizer(line);
		if(!tokens.nextToken().equalsIgnoreCase("ellipse"))
			throw new IllegalArgumentException("WARNING! " + getClass().getSimpleName() + " can parse 'ellipse' only.");
		
		radius.value = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
		radius.weight = 0.0;
		// TODO What if not '/user' coordinates?
		tokens.nextToken(); // Assumed to be '/user';
		
		Vector2D centerIndex = new Vector2D();
		centerIndex.setX(-Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
		centerIndex.setY(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
		
		forImage.getGrid().getCoords(centerIndex, coords);
	}
	
	public void parseDS9(String line, GridImage<CoordinateType> forImage) {	
		CoordinateType coords = getCoordinates();
	
		StringTokenizer tokens = new StringTokenizer(line, "(), \t");
		boolean isCircle = tokens.nextToken().equalsIgnoreCase("circle");
		
		if(coords instanceof SphericalCoordinates) {
			SphericalCoordinates spherical = (SphericalCoordinates) coords;
		
			((AngleFormat) spherical.coordinateSystem.get(0).format).colons();
			((AngleFormat) spherical.coordinateSystem.get(1).format).colons();
	
			coords.parse(tokens.nextToken() + " " + tokens.nextToken() + " (J2000)");
		}
		else {
			coords.setX(Double.parseDouble(tokens.nextToken()));
			coords.setY(Double.parseDouble(tokens.nextToken()));
		}
				
		if(isCircle) {
			String R = tokens.nextToken();
			char unit = R.charAt(R.length() - 1);
			radius.value = Double.parseDouble(R.substring(0, R.length()-1));
			if(unit == '\'') radius.value *= Unit.arcmin;
			else if(unit == '"') radius.value *= Unit.arcsec;
			radius.weight = 0.0;
		}
		else radius.value = Double.NaN;
	}
	
	
	
	
	public final static int FORMAT_CRUSH = 0;
	public final static int FORMAT_GREG = 1;
	public final static int FORMAT_DS9 = 2;
	public final static int FORMAT_OFFSET = 3;
	public final static int FORMAT_POINTING = 4; // TODO
	public final static int FORMAT_DISPLAY = 5; // TODO
	public final static int FORMAT_LONG = 6; // TODO

	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat nf = TableFormatter.getNumberFormat(formatSpec);
		
		if(name.equals("r")) return nf.format(radius.value);
		else if(name.equals("dr")) return nf.format(radius.rms());
		if(name.equals("dr")) return nf.format(radius.rms());
		else return TableFormatter.NO_SUCH_DATA;
		
	}
}

