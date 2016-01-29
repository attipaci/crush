package test;

import java.io.*;
import java.util.StringTokenizer;

import crush.astro.AstroImage;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.data.Interpolator;
import jnum.math.Vector2D;

public class ProfileSource {
	Interpolator profile;
	AstroImage image;
	
	
	public static void main(String[] args) {
		try { 
			ProfileSource source = new ProfileSource(); 
			double dx = args.length > 2 ? Double.parseDouble(args[2]) * Unit.arcsec : 0.0;
			double dy = args.length > 3 ? Double.parseDouble(args[3]) * Unit.arcsec : 0.0;
			
			System.err.println("# " + dx + ", " + dy);
			
			source.process(args[0], args[1], dx, dy);
		}
		catch(Exception e) { e.printStackTrace(); }
		
	}
	
	public void process(String fileName, String profileName, double dx, double dy) throws Exception {
		image = new AstroImage(fileName);
		image.reset(true);
		final double imageUnit = image.getUnit().value();
	
		profile = new Interpolator(profileName) {

			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
			protected void readData(String fileName) throws IOException {
				BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
				String line = null;
				
				while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
					StringTokenizer tokens = new StringTokenizer(line);
					Data entry = new Data();
					entry.ordinate = Double.parseDouble(tokens.nextToken()) * Unit.arcsec;
					entry.value = Double.parseDouble(tokens.nextToken()) * imageUnit;
					add(entry);
				}
				
				in.close();
				System.err.println("Found " + size() + " entries.");
			}
		};
		
		final Vector2D resolution = image.getResolution(); 
		
		double i0 = image.getGrid().refIndex.x() - dx/resolution.x();
		double j0 = image.getGrid().refIndex.y() - dy/resolution.y();
		
		
		
		for(int i=image.sizeX(); --i >=0; ) for(int j=image.sizeY(); --j >= 0; ) {
			double r = ExtraMath.hypot((i - i0) * resolution.x(), (j - j0) * resolution.y());
			try { image.setValue(i,  j, profile.getValue(r)); }
			catch(Exception e) { image.setValue(i, j, 0.0); }
			image.unflag(i, j);
		}
		
		image.write("profile.fits");
		
	}
	

	
	
}