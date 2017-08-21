package test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.StringTokenizer;

import jnum.Util;
import jnum.data.image.FlatGrid2D;
import jnum.data.image.Grid2D;
import jnum.data.image.GridImage2D;
import jnum.math.Coordinate2D;
import jnum.math.Range;
import jnum.math.Vector2D;

public class FieldViewer {
	Hashtable <String, Vector2D> positions = new Hashtable<String, Vector2D>();
	Hashtable <String, Double> values = new Hashtable<String, Double>();
	
	
	public static void main(String[] args) {
		FieldViewer viewer = new FieldViewer();
		try {
			viewer.parseCol(args[0], Integer.parseInt(args[1]), args.length > 4);
			viewer.parseRCP(args[2]);
			viewer.getView(Double.parseDouble(args[3])).write("view.fits");
		}
		catch(Exception e) { e.printStackTrace(); }
		
	}
	
	public void parseCol(String fileName, int col, boolean isNoise) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			String id = tokens.nextToken();
			for(int i=2; i<col; i++) tokens.nextToken();
			double value = Double.parseDouble(tokens.nextToken());
			if(isNoise) value = 1.0 / Math.sqrt(value);
			values.put(id, value);		
		}
		in.close();
		System.err.println("Parsed " + values.size() + " pixel values.");
	}
		
	public void parseRCP(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			String id = tokens.nextToken();
			//tokens.nextToken();
			//tokens.nextToken();
			Vector2D pos = new Vector2D(Double.parseDouble(tokens.nextToken()), Double.parseDouble(tokens.nextToken()));
			positions.put(Util.f1.format(Double.parseDouble(id)), pos);
		}
		in.close();
		System.err.println("Parsed " + positions.size() + " positions.");
	}
	
	GridImage2D<?> getView(double res) {
		// get xrange and yrange...
		Range xRange = new Range();
		Range yRange = new Range();
		for(Vector2D pos : positions.values()) {
			xRange.include(pos.x());
			yRange.include(pos.y());
		}
		
		
		// create grid...
		GridImage2D<Coordinate2D> image = new GridImage2D<Coordinate2D>(new FlatGrid2D());
		image.setSize(1 + (int) Math.ceil(xRange.span() / res), 1 + (int) Math.ceil(yRange.span() / res));
		Grid2D<Coordinate2D> grid = image.getGrid();
		
		grid.setResolution(res);
		grid.setReference(new Vector2D());
		grid.setReferenceIndex(new Vector2D(0.5 - xRange.min() / res, 0.5 - yRange.min() / res));
		
		Vector2D index = new Vector2D();
	
		image.flag();
		
		int n[][] = new int[image.sizeX()][image.sizeY()];
		
		// place values on grid...
		for(String key : values.keySet()) {
			Vector2D pos = positions.get(key);
			if(pos == null) continue;
			
			grid.offsetToIndex(pos, index);
			int i = (int) Math.round(index.x());
			int j = (int) Math.round(index.y());
				
			image.set(i, j, image.get(i, j) + values.get(key));
			image.unflag(i, j);
			n[i][j]++;
		}
		
		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) 
			if(n[i][j] != 0) image.scaleValue(i, j, 1.0 / n[i][j]);
		
		return image;
	}
	
	
}
