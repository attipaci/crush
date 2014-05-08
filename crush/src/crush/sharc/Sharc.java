package crush.sharc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import kovacs.math.Vector2D;
import kovacs.util.Unit;
import crush.Mount;
import crush.Scan;
import crush.SourceModel;
import crush.cso.CSOArray;
import crush.sourcemodel.DualBeamMap;

public class Sharc extends CSOArray<SharcPixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7197932690442343651L;
	
	double pointingCenter = 12.5;
	
	public Sharc() {
		super("sharc", pixels);
		resolution = 8.5 * Unit.arcsec;
		mount = Mount.CASSEGRAIN;
	}
	
	@Override
	public double getLoadTemperature() {
		// TODO Auto-generated method stub
		return 0.0;
	}

	@Override
	public Vector2D getPointingCenterOffset() {
		return SharcPixel.getPosition(pointingCenter);
	}

	@Override
	public int maxPixels() {
		return pixels;
	}

	@Override
	public void readWiring(String fileName) throws IOException {
	}

	@Override
	public SharcPixel getChannelInstance(int backendIndex) {
		return new SharcPixel(this, backendIndex);
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new SharcScan(this, null);
	}

	@Override
	public Scan<?, ?> readScan(String descriptor, boolean readFully) throws IOException {
		if(!hasOption("file")) throw new FileNotFoundException("Unspecified data file. Use the 'file' option.");
		
		SharcFile file = null;
		String spec = option("file").getPath();
		
		try { file = SharcFile.get(this, spec); }
		catch(IOException e) {
			if(!hasOption("datapath")) throw e;
			file = SharcFile.get(this, option("datapath").getPath() + File.separator + spec);
		}
		SharcScan scan = file.get(Integer.parseInt(descriptor));
		if(scan == null) throw new FileNotFoundException("Invalid scan index.");
		
		scan.instrument.validate(scan);
		for(SharcIntegration integration : scan) integration.instrument = (Sharc) scan.instrument.copy();
		
		scan.validate();
		
		
		
		return scan;
	}
	

	@Override
	public SourceModel getSourceModelInstance() {
		if(hasOption("source.type")) {
			String type = option("source.type").getValue();
			if(type.equals("deconvolved")) return new DualBeamMap(this);		
			else return super.getSourceModelInstance();
		}
		return super.getSourceModelInstance();
	}  
	
	
	public final static int pixels = 24;
	public final static int fileID = 10;
}
