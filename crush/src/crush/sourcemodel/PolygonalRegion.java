/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
package crush.sourcemodel;

import java.util.*;
import java.text.ParseException;

import util.*;
import util.data.Bounds;
import util.data.Grid2D;
import util.data.GridImage;
import util.data.Region;
import util.data.WeightedPoint;

public class PolygonalRegion<CoordinateType extends CoordinatePair> extends Region<CoordinateType> {
	Vector<CoordinateType> points = new Vector<CoordinateType>();
	
	//String name = "polygon";
	boolean isClosed = false;

	private Vector2D reuseFrom = new Vector2D(), reuseTo = new Vector2D();
	
	public PolygonalRegion() {}
	
	public PolygonalRegion(String fileName, int format, GridImage<CoordinateType> forImage) throws ParseException {
		parse(fileName, format, forImage); 
	}
	
	@Override
	public Object clone() {
		PolygonalRegion<?> polygon = (PolygonalRegion<?>) super.clone();
		polygon.reuseFrom = new Vector2D();
		polygon.reuseTo = new Vector2D();
		return polygon;
	}
	
	public void close() { isClosed = true; }
	

	@Override
	public boolean isInside(Grid2D<CoordinateType> grid, double i, double j) {
		Projection2D<CoordinateType> projection = grid.getProjection();
		int below = 0;

		final Vector2D from = reuseFrom;
		final Vector2D to = reuseTo;
		
		for(int n=points.size(); --n >= 0; ) {
			projection.project(points.get(n), from);
			projection.project(points.get((n+1) % points.size()), to);
			
			grid.toIndex(from);
			grid.toIndex(to);
			
			double mini = Math.min(from.getX(), to.getX());
			double maxi = Math.max(from.getX(), to.getX());
			double intersect = i < mini || i > maxi ? 
					Double.NaN : 
					from.getY() + (to.getY()-from.getY())*(i-from.getX())/(to.getX() - from.getX());

			if(intersect <= j) below++;
		}
		
		return below%2 == 1;
	}
	

	@Override
	public Bounds getBounds(GridImage<CoordinateType> image) {
		Vector2D min = (Vector2D) points.get(0).clone();
		Vector2D max = (Vector2D) points.get(0).clone();
		
		Vector2D vertex = reuseFrom;
		Projection2D<CoordinateType> projection = image.getProjection();
		
		for(CoordinateType coords : points) {
			projection.project(coords, vertex);
			
			if(vertex.getX() < min.getX()) min.setX(vertex.getX());
			else if(vertex.getX() > max.getX()) max.setX(vertex.getX());
			
			if(vertex.getY() < min.getY()) min.setY(vertex.getY());			
			else if(vertex.getY() > max.getY()) max.setY(vertex.getY());				
		}
		
		Vector2D delta = image.getGrid().getResolution();
		min.scaleX(1.0 / delta.getX());
		min.scaleY(1.0 / delta.getY());
		max.scaleX(1.0 / delta.getX());
		max.scaleY(1.0 / delta.getY());
		
		Bounds bounds = new Bounds();
		bounds.fromi = (int) Math.floor(Math.min(min.getX(), max.getX()));
		bounds.toi = (int) Math.ceil(Math.max(min.getX(), max.getX()));
		bounds.fromj = (int) Math.floor(Math.min(min.getY(), max.getY()));
		bounds.toj = (int) Math.ceil(Math.max(min.getY(), max.getY()));
		
		return bounds;
	}
	
	@Override
	public WeightedPoint getFlux(GridImage<CoordinateType> image) {
		WeightedPoint flux = new WeightedPoint();
		
		Bounds bounds = getBounds(image);
		
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(image.isUnflagged(i, j)) if(isInside(image.getGrid(), i, j)) {
				flux.add(image.getValue(i, j));
				flux.addWeight(image.getWeight(i, j));
			}
		
		flux.setWeight(1.0 / flux.weight());
		flux.scale(image.getPixelArea() / image.getImageBeamArea());
		
		return flux;
	}
	
	public double getInsideLevel(GridImage<CoordinateType> image) {
		double sum = 0.0, sumw = 0.0;
		Bounds bounds = getBounds(image);
		
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(image.isUnflagged(i, j)) if(isInside(image.getGrid(), i, j)) {
				final double weight = image.getWeight(i, j);
				sum += weight * image.getValue(i, j);
				sumw += weight;
			}
			
		return sum / sumw;			
	}
	
	public double getRMS(GridImage<CoordinateType> image) {
		double level = getInsideLevel(image);
		Bounds bounds = getBounds(image);
		
		double var = 0.0;
		int n = 0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(image.isUnflagged(i, j)) if(isInside(image.getGrid(), i, j)) {
				double value = image.getValue(i, j) - level;
				var += value * value;
				n++;
			}
		var /= (n-1);
		
		return Math.sqrt(var);
	}
	

	
	@Override
	// TODO for non spherical coordinates also...
	public void parse(String spec, int format, GridImage<CoordinateType> forImage) throws ParseException {
		points.clear();
		
		StringTokenizer tokens = new StringTokenizer(spec, ";\n");
		CoordinateType reference = forImage.getReference();
		
		while(tokens.hasMoreTokens()) {
			@SuppressWarnings("unchecked")
			CoordinateType coords = (CoordinateType) reference.clone();
			coords.parse(tokens.nextToken());
			points.add(coords);
		}
	}

	@Override
	public String toString(GridImage<CoordinateType> image) {
		// TODO Auto-generated method stub
		return null;
	}

}
