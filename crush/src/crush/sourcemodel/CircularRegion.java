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

package crush.sourcemodel;

import util.*;
import util.data.DataPoint;
import util.data.Index2D;
import util.text.AngleFormat;
import util.text.TableFormatter;

import java.text.*;
import java.util.*;

public class CircularRegion extends Region implements TableFormatter.Entries {
	public SphericalCoordinates coords;
	public DataPoint radius;
	
	private SphericalGrid useGrid = null;
	private Vector2D gridIndex = new Vector2D();

	public CircularRegion() {}

	public CircularRegion(AstroImage image, Vector2D offset, double r) {
		coords = (SphericalCoordinates) image.getReference().clone();
		image.getProjection().deproject(offset, coords);
		radius.value = r;
		radius.setRMS(Math.sqrt(image.getPixelArea()) / (AstroMap.fwhm2size * Util.sigmasInFWHM));
	}
	
	public CircularRegion(SphericalCoordinates coords, double r) {
		setCenter(coords);
		radius = new DataPoint();
		radius.value = r;
		radius.weight = 0.0;
	}
	
	public CircularRegion(String line, AstroImage forImage) throws ParseException { super(line, forImage); }
	
	@Override
	public Object clone() {
		CircularRegion clone = (CircularRegion) super.clone();
		clone.useGrid = null;
		clone.gridIndex = new Vector2D();
		return clone;
	}
	
	public Vector2D getIndex(SphericalGrid grid) {
		if(useGrid != grid) indexFor(grid);
		return gridIndex;
	}
	
	public void setCenter(SphericalCoordinates coords) {
		this.coords = coords;
		useGrid = null;
	}
	
	private void indexFor(SphericalGrid grid) {	
		grid.getIndex(coords, gridIndex);
		useGrid = grid;
	}
	
	@Override
	protected Bounds getBounds(AstroImage image) {
		Vector2D centerIndex = getIndex(image.grid);
		Bounds bounds = new Bounds();
		Vector2D resolution = image.getResolution();
		double deltaX = radius.value / resolution.x;
		double deltaY = radius.value / resolution.y;
		
		bounds.fromi = Math.max(0, (int)Math.floor(centerIndex.x - deltaX));
		bounds.toi = Math.min(image.sizeX()-1, (int)Math.ceil(centerIndex.x + deltaX));
		bounds.fromj = Math.max(0, (int)Math.floor(centerIndex.y - deltaY));
		bounds.toj = Math.min(image.sizeY()-1, (int)Math.ceil(centerIndex.y + deltaY));
		
		return bounds;
	}

	public double distanceTo(SphericalCoordinates pos) {
		return coords.distanceTo(pos);
	}

	@Override
	public boolean isInside(SphericalGrid grid, double i, double j) {
		Vector2D centerIndex = getIndex(grid);
		return Math.hypot(centerIndex.x - i, centerIndex.y - j) <= radius.value;
	}
	
	public void moveToPeak(AstroMap map) throws IllegalStateException {
		Bounds bounds = getBounds(map);
		Vector2D centerIndex = getIndex(map.grid);
		
		if(!map.containsIndex(centerIndex.x, centerIndex.y)) throw new IllegalStateException("Region falls outside of map.");
		Index2D index = new Index2D(centerIndex);
		
		double significance = map.getS2N(index.i, index.j);
		
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(map.flag[i][j] == 0) if(map.getS2N(i,j) > significance) if(isInside(map.grid, i, j)) {
				significance = map.getS2N(i,j);
				index.i = i;
				index.j = j;				
			}
		
		if(map.flag[index.i][index.j] != 0) throw new IllegalStateException("No valid peak in search area. ");
		
		centerIndex.x = index.i;
		centerIndex.y = index.j;
		
		if(isInside(map.grid, index.i+1, index.j)) if(isInside(map.grid, index.i-1, index.j)) 
			if(isInside(map.grid, index.i, index.j+1)) if(isInside(map.grid, index.i, index.j-1)) finetunePeak(map);
	}
	
	public DataPoint finetunePeak(AstroMap map) {
		Vector2D centerIndex = getIndex(map.grid);
		
		int i = (int) Math.round(centerIndex.x);
		int j = (int) Math.round(centerIndex.y);
		
		double a=0.0,b=0.0,c=0.0,d=0.0;
			
		double y0 = map.getS2N(i,j);
	
		if(i>0 && i<map.sizeX()-1) if((map.flag[i+1][j] | map.flag[i-1][j]) == 0) {
			a = 0.5 * (map.getS2N(i+1,j) + map.getS2N(i-1,j)) - y0;
			c = 0.5 * (map.getS2N(i+1,j) - map.getS2N(i-1,j));
		}
		
		if(j>0 && j<map.sizeY()-1) if((map.flag[i][j+1] | map.flag[i][j-1]) == 0) {
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
		
		centerIndex.x = i + di;
		centerIndex.y = j + dj;
		
		double peak = map.valueAtIndex(i+di, j+dj);
	
		return new DataPoint(peak, peak / significance);
	}

	
	@Override
	public String toString(AstroImage image) {
		return toString(image, FORMAT_CRUSH);
	}
	
	public String toString(AstroImage image, int format) {
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
	
	public SphericalCoordinates getCoordinates() {
		return coords;
	}
	
	
	public String toCrushString(AstroImage image) {
		SphericalCoordinates coords = getCoordinates();
		
		((AngleFormat) coords.coordinateSystem.get(0).format).colons();
		((AngleFormat) coords.coordinateSystem.get(1).format).colons();
		
		return id + "\t" + coords.toString() + "  " + Util.f1.format(radius.value/Unit.arcsec) + " # " + comment;
	}

	public String toGregString(AstroImage image) {
		Vector2D offset = new Vector2D();
		useGrid.projection.project(coords, offset);
		
		return "ellipse " + Util.f1.format(radius.value/Unit.arcsec) + " /user " +
		Util.f1.format(offset.x / Unit.arcsec) + " " + Util.f1.format(offset.y / Unit.arcsec);
	}
	
	public String toDS9String(AstroImage image) {
		SphericalCoordinates coords = getCoordinates();
	
		((AngleFormat) coords.coordinateSystem.get(0).format).colons();
		((AngleFormat) coords.coordinateSystem.get(1).format).colons();
		
		String line = "circle(" 
			+ coords.coordinateSystem.get(0).format(coords.x) + ","
			+ coords.coordinateSystem.get(1).format(coords.y) + ","
			+ Util.f3.format(radius.value / Unit.arcsec) + "\")";
		
		return line;
	}
	
	public String toOffsetString(AstroImage image) {
		Vector2D offset = new Vector2D();
		image.getProjection().project(coords, offset);
	
		SphericalCoordinates reference = useGrid.getReference();
		CoordinateAxis x = reference.localCoordinateSystem.get(0);
		CoordinateAxis y = reference.localCoordinateSystem.get(1);
		
		return x.label + " = " + x.format(offset.x) + "\t" + y.label + " = " + y.format(offset.y);
	}
	
	public String getComment() {
		return "";
	}
	
	@Override
	public void parse(String line, AstroImage forImage) throws ParseException {
		parseCrush(line, forImage);
	}
	
	public void parse(String line, int format, AstroImage forImage) {	
		switch(format) {
		case FORMAT_CRUSH : parseCrush(line, forImage); break;
		//case FORMAT_OFFSET : parseOffset(line); break;
		case FORMAT_GREG : parseGreg(line, forImage); break;
		case FORMAT_DS9 : parseDS9(line, forImage); break;
		}
	}
	
	public StringTokenizer parseCrush(String line, AstroImage forImage) {
		SphericalCoordinates coords = getCoordinates();
		
		((AngleFormat) coords.coordinateSystem.get(0).format).colons();
		((AngleFormat) coords.coordinateSystem.get(1).format).colons();
		
		StringTokenizer tokens = new StringTokenizer(line);
		id = tokens.nextToken();
		coords.parse(tokens.nextToken() + " " + tokens.nextToken() + " " + tokens.nextToken());
		setCenter(coords);
		radius.value = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
		radius.weight = 0.0;
		
		if(line.contains("#")) comment = line.substring(line.indexOf('#') + 2);
	
		return tokens;
	}

	public void parseGreg(String line, AstroImage forImage) {
		StringTokenizer tokens = new StringTokenizer(line);
		if(!tokens.nextToken().equalsIgnoreCase("ellipse"))
			throw new IllegalArgumentException("WARNING! " + getClass().getSimpleName() + " can parse 'ellipse' only.");
		
		radius.value = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
		radius.weight = 0.0;
		// TODO What if not '/user' coordinates?
		tokens.nextToken(); // Assumed to be '/user';
		
		Vector2D centerIndex = new Vector2D();
		centerIndex.x = -Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
		centerIndex.y = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
		
		forImage.grid.getCoords(centerIndex, coords);
	}
	
	public void parseDS9(String line, AstroImage forImage) {	
		SphericalCoordinates coords = getCoordinates();
	
		((AngleFormat) coords.coordinateSystem.get(0).format).colons();
		((AngleFormat) coords.coordinateSystem.get(1).format).colons();
	
		StringTokenizer tokens = new StringTokenizer(line, "(), \t");
		boolean isCircle = tokens.nextToken().equalsIgnoreCase("circle");
	
		coords.parse(tokens.nextToken() + " " + tokens.nextToken() + " (J2000)");
		
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

