package test;

import java.io.*;
import java.util.StringTokenizer;
import java.util.Vector;

import util.CoordinatePair;
import util.Parallel.Process;
import util.Range;
import util.SphericalCoordinates;
import util.Unit;
import util.Util;
import util.Vector2D;
import util.astro.CoordinateEpoch;
import util.astro.EquatorialCoordinates;
import util.data.CartesianGrid;
import util.data.Data2D.Task;
import util.data.DataPoint;
import util.data.GridMap;

import crush.astro.AstroMap;
import crush.sourcemodel.GaussianSource;

public class GDFStack {
	Vector<GaussianSource<SphericalCoordinates>> sources = new Vector<GaussianSource<SphericalCoordinates>>();
	AstroMap map, model;

	public static void main(String[] args) {
		String fileName = args[0];
		String catalogName = args[1];
			
		GDFStack stacker = new GDFStack();
		
		try {
			stacker.readMap(fileName);

			if(args.length > 2) {
				if(args[2].equalsIgnoreCase("rel")) {
					stacker.readMIPSSources(catalogName);
					catalogName += ".rel";
				}
				else {
					Range range = Range.parse(args[2], true);
					stacker.readMIPSSources(catalogName, range);
					catalogName += "." + range.min() + "--" + range.max();
				}
			}
			else stacker.readSources(catalogName);
			
			stacker.makeModel();
			GridMap<?> stack = stacker.getStack();			
			
			try { stack.write(catalogName + ".fits"); }
			catch(Exception e) { e.printStackTrace(); }
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public GridMap<CoordinatePair> getStack() {
		int size = 1 + 2 * (int)Math.ceil(3.0 * map.getImageFWHM() / map.getResolution().getX());
		GridMap<CoordinatePair> stack = new GridMap<CoordinatePair>(size, size);
		stack.setGrid(new CartesianGrid());
		stack.setResolution(map.getResolution().getX());
		stack.setName("stack");
		final int c = size / 2;
		stack.getGrid().setReferenceIndex(new Vector2D(c, c));
		stack.setReference(new Vector2D());
		
		for(int i=size; --i >= 0; ) for(int j=size; --j >=0; ) {
			DataPoint mean = getMeanFlux(i - c, j - c);		
			stack.setValue(i, j, mean.value());
			stack.setWeight(i, j, mean.weight());
			stack.unflag(i, j);
		}
		
		System.err.println("Mean = " + getMeanFlux(0, 0).toString(Util.e3) + " " + map.getUnit().name());
		
		return stack;
	}
	
	public DataPoint getMeanFlux(final int di, final int dj) {
		final double npts = map.getPointsPerSmoothingBeam();
		
		Task<DataPoint> stack = map.new Task<DataPoint>() {
			DataPoint mean;
			@Override 
			public void init() { mean = new DataPoint(); }
			
			@Override
			public void process(int i, int j) {
				final int i1 = i + di;
				if(i1 < 0) return;
				if(i1 >= map.sizeX()) return;
				
				final int j1 = j + dj;
				if(j1 < 0) return;
				if(j1 >= map.sizeY()) return;
				
				if(map.isFlagged(i1, j1)) return;
				
				double G = model.getValue(i, j);
				double wG = map.getWeight(i1, j1) / npts * G;
				mean.add(wG * map.getValue(i1, j1));
				mean.addWeight(wG * G);
			}
			
			@Override
			public DataPoint getPartialResult() {
				return mean;
			}
			
			@Override
			public DataPoint getResult() {
				DataPoint combined = new DataPoint();
				for(Process<DataPoint> task : getWorkers()) {
					DataPoint partial = task.getPartialResult();
					combined.add(partial.value());
					combined.addWeight(partial.weight());
				}
				combined.scaleValue(1.0 / combined.weight());
				return combined;
			}	
		};
		
		stack.process();
		DataPoint result = stack.getResult();
		result.scale(1.0 / map.getUnit().value());
		
		return result;
		
	}
	
	
	public void readMap(String fileName) throws Exception {
		map = new AstroMap();
		map.read(fileName);
	}
	
	public void readSources(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			GaussianSource<SphericalCoordinates> source = new GaussianSource<SphericalCoordinates>();

			source.setID(tokens.nextToken());
			source.setCoordinates(new EquatorialCoordinates(line.replace('+', ' ')));
			source.setPeak(1.0);
			source.setRadius(map.getImageFWHM());
			
			sources.add(source);
		}
		
		System.err.println(">>> " + sources.size() + " sources.");
	}
	
	
	public void readMIPSSources(String fileName, Range rangeS24) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			GaussianSource<SphericalCoordinates> source = new GaussianSource<SphericalCoordinates>();

			source.setID(tokens.nextToken());
			source.setCoordinates(new EquatorialCoordinates(
					Double.parseDouble(tokens.nextToken()) * Unit.deg,
					Double.parseDouble(tokens.nextToken()) * Unit.deg,
					CoordinateEpoch.J2000));
	
			source.setPeak(1.0);
			source.setRadius(map.getImageFWHM());
			
			tokens.nextToken();
			tokens.nextToken();
			
			DataPoint S24 = new DataPoint();
			S24.setValue(Double.parseDouble(tokens.nextToken()));
			S24.setRMS(Double.parseDouble(tokens.nextToken()));	
			
				
			if(rangeS24.contains(S24.value()))
				sources.add(source);
		}
		
		System.err.println(">>> " + sources.size() + " sources.");
	}
	
	public void readMIPSSources(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			GaussianSource<SphericalCoordinates> source = new GaussianSource<SphericalCoordinates>();

			source.setID(tokens.nextToken());
			source.setCoordinates(new EquatorialCoordinates(
					Double.parseDouble(tokens.nextToken()) * Unit.deg,
					Double.parseDouble(tokens.nextToken()) * Unit.deg,
					CoordinateEpoch.J2000));
	
			source.setPeak(1.0);
			source.setRadius(map.getImageFWHM());
			
			tokens.nextToken();
			tokens.nextToken();
			
			DataPoint S24 = new DataPoint();
			S24.setValue(Double.parseDouble(tokens.nextToken()));
			S24.setRMS(Double.parseDouble(tokens.nextToken()));	
			source.setPeak(S24.value() * 1e-6 * map.getUnit().value());
				
			sources.add(source);
		}
		
		System.err.println(">>> " + sources.size() + " sources.");
	}
	
	
	
	public void makeModel() throws Exception {
		 model = (AstroMap) map.copy();
		 model.setData(new double[model.sizeX()][model.sizeY()]);
		 //model.reset();
		 
		 for(GaussianSource<SphericalCoordinates> source : sources) {
			 source.addPoint(model);
			 //System.err.println("   " + source.coords);
		 }
		 
		 //model.smoothTo(map.smoothFWHM);
		 //model.clippingS2N = map.clippingS2N;
		 //model.filterBlanking = map.filterBlanking;
		 //model.filterAbove(map.extFilterFWHM);
		 
		 model.fileName = "model.fits";
	}
}
