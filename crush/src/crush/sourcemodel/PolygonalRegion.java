package crush.sourcemodel;

import java.util.*;
import java.text.ParseException;

import util.*;
import util.data.WeightedPoint;

public class PolygonalRegion extends Region {
	Vector<SphericalCoordinates> points = new Vector<SphericalCoordinates>();
	
	//String name = "polygon";
	boolean isClosed = false;

	private Vector2D reuseFrom = new Vector2D(), reuseTo = new Vector2D();
	
	public PolygonalRegion() {}
	
	public PolygonalRegion(String fileName, AstroImage forImage) throws ParseException {
		parse(fileName, forImage); 
	}
	
	@Override
	public Object clone() {
		PolygonalRegion polygon = (PolygonalRegion) super.clone();
		polygon.reuseFrom = new Vector2D();
		polygon.reuseTo = new Vector2D();
		return polygon;
	}
	
	public void close() { isClosed = true; }
	

	@Override
	public boolean isInside(SphericalGrid grid, double i, double j) {
		SphericalProjection projection = grid.projection;
		int below = 0;

		final Vector2D from = reuseFrom;
		final Vector2D to = reuseTo;
		
		for(int n=points.size(); --n >= 0; ) {
			projection.project(points.get(n), from);
			projection.project(points.get((n+1) % points.size()), to);
			
			grid.toIndex(from);
			grid.toIndex(to);
			
			double mini = Math.min(from.x, to.x);
			double maxi = Math.max(from.x, to.x);
			double intersect = i < mini || i > maxi ? Double.NaN : from.y + (to.y-from.y)*(i-from.x)/(to.x - from.x);

			if(intersect <= j) below++;
		}
		
		return below%2 == 1;
	}
	

	@Override
	protected Bounds getBounds(AstroImage image) {
		Vector2D min = (Vector2D) points.get(0).clone();
		Vector2D max = (Vector2D) points.get(0).clone();
		
		Vector2D vertex = reuseFrom;
		SphericalProjection projection = image.getProjection();
		
		for(SphericalCoordinates coords : points) {
			projection.project(coords, vertex);
			
			if(vertex.x < min.x) min.x = vertex.x;
			else if(vertex.x > max.x) max.x = vertex.x;
			
			if(vertex.y < min.y) min.y = vertex.y;			
			else if(vertex.y > max.y) max.y = vertex.y;				
		}
		
		Vector2D delta = image.grid.getResolution();
		min.x /= delta.x;
		min.y /= delta.y;
		max.x /= delta.x;
		max.y /= delta.y;
		
		Bounds bounds = new Bounds();
		bounds.fromi = (int) Math.floor(Math.min(min.x, max.x));
		bounds.toi = (int) Math.ceil(Math.max(min.x, max.x));
		bounds.fromj = (int) Math.floor(Math.min(min.y, max.y));
		bounds.toj = (int) Math.ceil(Math.max(min.y, max.y));
		
		return bounds;
	}
	
	public WeightedPoint getFlux(AstroImage image) {
		WeightedPoint flux = new WeightedPoint();
		
		Bounds bounds = getBounds(image);
		
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(image.flag[i][j] == 0) if(isInside(image.grid, i, j)) {
				flux.value += image.data[i][j];
				flux.weight += image.weightAt(i, j);
			}
		
		
		
		flux.weight = 1.0 / flux.weight;
		flux.scale(image.getPixelArea() / image.getImageBeamArea());
		
		return flux;
	}
	
	public double getInsideLevel(AstroImage image) {
		double sum = 0.0, sumw = 0.0;
		Bounds bounds = getBounds(image);
		
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(image.flag[i][j] == 0) if(isInside(image.grid, i, j)) {
				final double weight = image.weightAt(i, j);
				sum += weight * image.data[i][j];
				sumw += weight;
			}
			
		return sum / sumw;			
	}
	
	public double getRMS(AstroImage image) {
		double level = getInsideLevel(image);
		Bounds bounds = getBounds(image);
		
		double var = 0.0;
		int n = 0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(image.flag[i][j] == 0) if(isInside(image.grid, i, j)) {
				double value = image.data[i][j] - level;
				var += value * value;
				n++;
			}
		var /= (n-1);
		
		return Math.sqrt(var);
	}
	

	
	@Override
	public void parse(String line, AstroImage forImage) throws ParseException {
		points.clear();
		
		StringTokenizer tokens = new StringTokenizer(line, ";");
		SphericalCoordinates reference = forImage.getReference();
		
		while(tokens.hasMoreTokens()) {
			SphericalCoordinates coords = (SphericalCoordinates) reference.clone();
			coords.parse(tokens.nextToken());
			points.add(coords);
		}
	}

	@Override
	public String toString(AstroImage image) {
		// TODO Auto-generated method stub
		return null;
	}

}
