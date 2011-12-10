package test;

import java.io.*;
import java.util.StringTokenizer;
import java.util.Vector;

import util.SphericalCoordinates;
import util.Unit;
import util.astro.CoordinateEpoch;
import util.astro.EquatorialCoordinates;
import util.data.DataPoint;

import crush.GenericInstrument;
import crush.Instrument;
import crush.astro.AstroMap;
import crush.sourcemodel.GaussianSource;

public class GDFStack {
	Vector<GaussianSource<SphericalCoordinates>> sources = new Vector<GaussianSource<SphericalCoordinates>>();
	AstroMap map, model;

	public static void main(String[] args) {
		String fileName = args[0];
		String catalogName = args[1];
		double minS24 = Double.parseDouble(args[2]);
		
		GDFStack stack = new GDFStack();
		
		try {
			stack.readMap(fileName);
			stack.testSource();
			stack.readSources(catalogName, minS24);
			stack.makeModel();
			stack.model.write();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	
	public void readMap(String fileName) throws Exception {
		//Instrument<?> instrument = new GenericInstrument("generic");
		//map = new AstroMap(fileName, instrument);
		map = new AstroMap();
		map.read(fileName);
	}
	
	public void testSource() throws Exception {
		GaussianSource<SphericalCoordinates> source = new GaussianSource<SphericalCoordinates>();
		source.coords = new EquatorialCoordinates();
		source.coords.parse("12:36:55.12 62:14:10.0 (J2000.0)");
		source.id = "test";
		source.peak = new DataPoint();
		source.peak.value = 1.0;
		//source.radius = new DataPoint();
		sources.add(source);
	}
	
	public void readSources(String fileName, double minS24) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			GaussianSource<SphericalCoordinates> source = new GaussianSource<SphericalCoordinates>();

			source.id = tokens.nextToken();
			source.coords = new EquatorialCoordinates(
					Double.parseDouble(tokens.nextToken()) * Unit.deg,
					Double.parseDouble(tokens.nextToken()) * Unit.deg,
					CoordinateEpoch.J2000);
			source.peak = new DataPoint();
			source.peak.value = 1.0;
			source.radius = new DataPoint();
			source.radius.value = map.getImageFWHM();
			
			tokens.nextToken();
			tokens.nextToken();
			
			DataPoint S24 = new DataPoint();
			S24.value = Double.parseDouble(tokens.nextToken());
			S24.setRMS(Double.parseDouble(tokens.nextToken()));	
			
			if(S24.value > minS24) {
				sources.add(source);
				System.err.println("   " + source.toCrushString(map));
			}
		}
		
		System.err.println(">>> " + sources.size() + " sources.");
	}
	
	public void makeModel() throws Exception {
		 model = (AstroMap) map.copy();
		 model.setData(new double[model.sizeX()][model.sizeY()]);
		 //model.reset();
		 
		 for(GaussianSource<SphericalCoordinates> source : sources) {
			 source.addPoint(model);
			 break;
		 }
		 
		 //model.smoothTo(map.smoothFWHM);
		 //model.clippingS2N = map.clippingS2N;
		 //model.filterBlanking = map.filterBlanking;
		 //model.filterAbove(map.extFilterFWHM);
		 
		 model.fileName = "model.fits";
	}
}
