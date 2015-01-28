package crush.devel;

import java.io.File;
import java.util.ArrayList;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.Fits;
import kovacs.data.WeightedPoint;
import kovacs.math.Range;
import kovacs.math.Vector2D;
import kovacs.util.Configurator;
import kovacs.util.Util;
import crush.CRUSH;
import crush.Frame;
import crush.Integration;
import crush.Scan;
import crush.mako.AbstractMako;
import crush.mako.Mako;
import crush.mako.MakoPixel;
import crush.mako.MakoFrame;
import crush.mako.MakoIntegration;
import crush.mako.MakoScan;

public class BeamMapper extends CRUSH {
	MakoScan<Mako> scan;
	Vector2D[] pos = null;
	
	
	public static void main(String[] args) {
		CRUSH.home = System.getenv("CRUSH");
		if(CRUSH.home == null) CRUSH.home = ".";
			
		BeamMapper mapper = new BeamMapper();
		mapper.init(args);
		try { mapper.analyze(); }
		catch(Exception e) { e.printStackTrace(); }
	}
	
	public BeamMapper() {
		super("mako");
		
		forget("aclip");
		forget("vclip");
		
		if(!hasOption("scan")) return;
	}
		
	@Override
	public void validate() {}
	
	public MakoScan<Mako> read(Mako mako, String fileName) throws Exception {
		Fits fits = new Fits(new File(fileName));
		MakoScan<Mako> scan = (MakoScan<Mako>) mako.getScanInstance();
		MakoIntegration<Mako> integration = (MakoIntegration<Mako>) scan.getIntegrationInstance();
		
		int xmin = hasOption("xmin") ? option("xmin").getInt() : 0;
		int t=0;
		
		BasicHDU[] hdu = fits.read();
		for(int i=0; i<hdu.length; i++) {
			String extName = hdu[i].getHeader().getStringValue("EXTNAME");
			if(extName == null) continue;
			extName = extName.toUpperCase();
			
			if(extName.startsWith("STREAM")) {
				BinaryTableHDU table = (BinaryTableHDU) hdu[i];
				int iCol = table.findColumn("Shift");
				for(int row = 0; row < table.getNRows(); row++) {
					if(t++ < xmin) continue;
					MakoFrame frame = new MakoFrame(scan);
					frame.data = (float[]) table.getRow(row)[iCol];
					frame.sampleFlag = new byte[frame.data.length];
					integration.add(frame);
				}
			}
		}
		scan.instrument = mako;
		integration.instrument = mako;
		scan.add(integration);
		
		if(hasOption("downsample")) integration.downsample(option("downsample").getInt());
		
		return scan;
		
	}
	
	public boolean hasOption(String key) { return isConfigured(key); }
	
	public Configurator option(String key) { return get(key); }
	
	public void analyze() throws Exception {
		Mako mako = new Mako();
		mako.setOptions(this);
		mako.initialize();
		
		scan = read(mako, option("scan").getValue());
		int channels = scan.get(0).get(0).data.length;
		for(int c=0; c<channels; c++) {
			MakoPixel pixel = new MakoPixel((Mako) mako, c);
			mako.add(pixel);
		}	
		
		mako.validate(scan);
		
		int rounds = hasOption("rounds") ? option("rounds").getInt() : 5;
		
		for(int i=0; i<rounds; i++) {
			System.err.println("Round " + (i+1));
			iterate();
 		}
		
		analyze(scan.get(0));
		
		print(mako, pos);
	}
	
	public void iterate() {
		for(MakoIntegration<?> integration : scan) {
			integration.removeOffsets(false);
			if(hasOption("decorrelate")) integration.decorrelate("array", false);
		}
	}
		
	public void print(AbstractMako<?> mako, Vector2D[] pos) {
		System.err.println("RESULTS(" + mako.size() + ")");
		
		for(int c=0; c<mako.size(); c++) {
			String id = mako.get(c).getID();
			System.out.println(c + "\t" + pos[c].x() + "\t" + pos[c].y());
		}
	}
	
	public void analyze(Integration<?,?> integration) {
		int nc = integration.instrument.size();
		System.err.println("### nc " + nc);
		pos = new Vector2D[nc];
		
		
		WeightedPoint T = new WeightedPoint();
		for(int c=0; c<nc; c++) {
			ArrayList<Double> list = getPeaks(integration, c, getRange(integration, c));
			T.average(getT(list));
		}
		
		System.err.println("### T = " + T.value());
		
		if(hasOption("t")) T.setValue(option("T").getDouble());
		
		for(int c=0; c<nc; c++) {
			ArrayList<Double> list = getPeaks(integration, c, getRange(integration, c));
			pos[c] = analyze(list, T.value());
		}
	}
	
	public WeightedPoint getT(ArrayList<Double> list) {
		WeightedPoint T = new WeightedPoint();
		for(int i=list.size(); --i >= 4; ) T.average(list.get(i) - list.get(i-4), 1.0);
		return T;	
	}
	
	public Vector2D analyze(ArrayList<Double> list, double T) {
		double d[] = new double[4];
		int[] n = new int[4];
		
		if(list.size() < 4) return new Vector2D(Double.NaN, Double.NaN);
		
		for(int i=list.size(); --i > 0; ) {
			int k = (i-1) % 4;
			d[k] = list.get(i) - list.get(i-1);
			n[k]++;
		}
		for(int k=0; k<d.length; k++) d[k] /= n[k];
		double sumd = d[0] + d[1] + d[2] + d[3];
		
		System.err.print(Util.f1.format(d[0]) + "\t" + Util.f1.format(d[1]) + "\t" + Util.f1.format(d[2]) + "\t" + Util.f1.format(d[3]));
		System.err.println(" | " + Util.f1.format(sumd));
		
		//if(Math.abs(T - sumd) / T > 0.05) return new Vector2D(Double.NaN, Double.NaN);

		double y = 0.0;
		int ny = 0;
		for(int i=0; i<list.size(); i += 4) {
			y += list.get(i) - (i/4) * T;
			ny++;
		}
		y /= ny;
		
		return new Vector2D(d[0] + d[2], y);
		
	}
		
	public Range getRange(Integration<?,?> integration, int channel) {
		Range range = new Range();
		int nt = integration.size();
		
		for(int t=0; t<nt; t++) {
			Frame frame = integration.get(t);
			if(frame == null) continue;
			range.include(frame.data[channel]);
		}
		return range;
	}
	
	public ArrayList<Double> getPeaks(Integration<?,?> integration, int channel, Range range) {
		ArrayList<Double> list = new ArrayList<Double>(); 
		int nt = integration.size();

		double rel1 = 0.4;
		double rel2 = 0.4;
		
		float mid = (float) (0.5 * (range.min() + range.max()));
		range.setRange(range.min() - mid, range.max() - mid);
		//float mid = 0.0F;
		
		int lastSign = 0;
		float value = integration.getFirstFrame().data[channel] - mid;
		
		if(value > rel1 * range.max()) lastSign = 1;
		else if(value < rel2 * range.min()) lastSign = -1;

		int up = -1;
		int down = -1;
		
		for(int t=1; t<nt; t++) {
			Frame frame = integration.get(t);
			if(frame == null) continue;
			
			int sign = 0;
			value = frame.data[channel] - mid;
			
			if(value > rel2 * range.max()) sign = 1;
			else if(value < rel1 * range.min()) sign = -1;
			
			if(sign == lastSign) continue;
			
			if(sign < 0) if(sign * lastSign == -1) list.add(1.0 * t);
			
			/*
			if(sign > 0) up = t;
			else if(lastSign > 0) down = t;
			
			if(up > 0 && down > up) list.add(0.5 * (up + down));
			*/
			
			if(sign != 0) lastSign = sign;
		}
		
		return list;
	}
	
	
}
