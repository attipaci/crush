/*******************************************************************************
 * Copyright (c) 2017 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush.telescope.cso;

import java.io.File;
import java.io.IOException;

import crush.CRUSH;
import crush.Integration;
import crush.Scan;
import crush.array.SingleColorPixel;
import crush.telescope.GroundBased;
import crush.telescope.HorizontalFrame;
import crush.telescope.jcmt.JCMTTauTable;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;


public abstract class CSOIntegration<InstrumentType extends CSOCamera<? extends SingleColorPixel>, FrameType extends HorizontalFrame> 
extends Integration<InstrumentType, FrameType> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8762250193431287809L;

	public CSOIntegration(Scan<InstrumentType, ?> parent) {
		super(parent);
	}
	
	@Override
	public void validate() {	
		if(!hasOption("nochopper")) if(!hasOption("lab")) {
			removeChopperDCOffset();

			for(FrameType frame : this) if(frame != null) {
				// Add chopper offset to the aggregated horizontal offset...
				frame.horizontalOffset.add(frame.chopperPosition);
				// Add the chopper offset to the absolute coordinates also...
				frame.horizontal.addOffset(frame.chopperPosition);
			}
		}
			
		super.validate();
	}
	
	private void removeChopperDCOffset() {
		double threshold = 0.4 * instrument.getMinBeamFWHM();
		double sumP = 0.0, sumM = 0.0;
		int nP = 0, nM = 0;
		
		
		
		for(FrameType frame : this) if(frame != null) {
			sumP += frame.chopperPosition.x();
			nP++;			
		}
		if(nP == 0) return;
		
		final double mean = sumP / nP;
		sumP = 0.0;
		nP = 0;
		
		for(FrameType frame : this) if(frame != null) {
			frame.chopperPosition.subtractX(mean);
			final double dx = frame.chopperPosition.x();
			
			if(dx > threshold) {
				sumP += dx;
				nP++;	
			}
			else if(dx < -threshold) {
				sumM += dx;
				nM++;				
			}
		}
		
		if(nP == 0 || nM == 0) {
			instrument.forget("detect.chopped");
			return;
		}
		
		info("Removing chopper signal DC offset.");
		
		final double level = 0.5 * (sumP / nP + sumM / nM);
		
		CRUSH.values(this, "--> mean: " + Util.f1.format(mean / Unit.arcsec) + "\", res: " + Util.f1.format(level / Unit.arcsec) + "\".");
		

		for(FrameType frame : this) if(frame != null) frame.chopperPosition.subtractX(level);
		
		return;
	}
	
	
	public void printEquivalentTaus(double value) {	
		CRUSH.values(this, "--->"
				+ " tau(225GHz):" + Util.f3.format(getTau("225ghz", value))
				+ ", tau(350um):" + Util.f3.format(getTau("350um", value))
				+ ", tau(LOS):" + Util.f3.format(value / scan.horizontal.sinLat())
				+ ", PWV:" + Util.f2.format(getTau("pwv", value)) + "mm"
		);		
	}
	
	
	public double getSkyLoadTemperature() {
		double transmission = 0.5 * (getFirstFrame().getTransmission() + getLastFrame().getTransmission());
		return (1.0 - transmission) * ((CSOScan<?,?>) scan).getAmbientKelvins();
	}
	
	
	@Override
	public void setTau() throws Exception {
		String source = option("tau").getValue().toLowerCase();
		
		if(source.equals("tables")) setTableTau();
		else if(source.equals("direct")) setZenithTau(getDirectTau());
		else if(source.equals("maitau")) setMaiTau();	
		else if(source.equals("jctables") && hasOption("tau.jctables")) setJCMTTableTau();
		else super.setTau();
		
		printEquivalentTaus(zenithTau);
		
		double tauLOS = zenithTau / scan.horizontal.sinLat();
		CRUSH.values(this, "Optical load is " + Util.f1.format(((CSOScan<?,?>) scan).ambientT * (1.0 - Math.exp(-tauLOS))) + " K.");
	
	}
	
	public void setMaiTau() throws Exception {
		info("Looking up MaiTau tables...");
		
		try {
			try { setTau("350um", getMaiTau("350um")); }
			catch(NumberFormatException no350) { setTau("225GHz", getMaiTau("225GHz")); }
		}	
		catch(Exception e) { fallbackTau("maitau", e); }	
	}
	
	public void setTableTau() throws Exception {
		String source = hasOption("tau.tables") ? option("tau.tables").getPath() : ".";
		String date = scan.getID().substring(0, scan.getID().indexOf('.'));
		String spec = date.substring(2, 4) + date.substring(5, 7) + date.substring(8, 10);
		
		File file = new File(source + File.separator + spec + ".dat");
		if(!file.exists()) {
			warning("No tau table found for " + date + ". Using default tau.");
			instrument.getOptions().remove("tau");
			setTau();
			return;
		}
		
		CSOTauTable table = CSOTauTable.get(((CSOScan<?,?>) scan).iMJD, file.getPath());
		table.setOptions(option("tau"));
		setTau("225GHz", table.getTau(getMJD()));	
	}
	
	public void setJCMTTableTau() throws Exception {
		String source = hasOption("tau.jctables") ? option("tau.jctables").getPath() : ".";
		String spec = scan.getShortDateString();
		String fileName = source + File.separator + spec + ".jcmt-183-ghz.dat";
		
		try {
			JCMTTauTable table = JCMTTauTable.get(((CSOScan<?,?>) scan).iMJD, fileName);
			table.setOptions(option("tau"));
			setTau("225gHz", table.getTau(getMJD()));	
		}
		catch(IOException e) { fallbackTau("jctables", e); }
	}
	
	private void fallbackTau(String from, Exception e) throws Exception {
		if(hasOption(from + ".fallback")) {
			warning("Tau lookup failed: " + e.getMessage());
			String source = option(from + ".fallback").getValue().toLowerCase();
			if(source.equals(from)) {
				warning("Deadlocked fallback tau option!");
				throw e;
			}	
			info("---> Falling back to '" + source + "'.");
			instrument.setOption("tau=" + source);
			setTau();
			return;
		}
		throw e;	
		
	}
	
	public double getMaiTau(String id) throws IOException {
	    id = id.toLowerCase();	
		double value = Double.NaN;

		
		if(!id.equals("225gHz") && !id.equalsIgnoreCase("350um")) 
			throw new IllegalArgumentException("No MaiTau lookup for '" + id + "'.");
		
		if(id.equals("225ghz")) {
		    if(hasOption("maitau.225ghz")) maitau225GHz.load(option("maitau.225ghz").getPath());
		    value = maitau225GHz.getTau(getMJD());
		}
		else if(id.equals("350um")) {
            if(hasOption("maitau.350um")) maitau350um.load(option("maitau.350um").getPath());
            value = maitau350um.getTau(getMJD());
        }
		
		if(Double.isNaN(value)) throw new NumberFormatException("No " + id + " value for date in MaiTau database.");
		
		return value;
	}

	
	@Override
	public String getASCIIHeader() {
	 
		double eps = hasOption("lab") ? 1.0 : 1.0 - Math.exp(-zenithTau / scan.horizontal.sinLat());
		double Tload = ((CSOScan<?, ?>) scan).getAmbientKelvins();
		
		return super.getASCIIHeader() + "\n" 
				+ "# tau(225GHz) = " + Util.f3.format(this.getTau("225ghz")) + "\n"
				+ "# T_amb = " + Util.f1.format((((CSOScan<?,?>) scan).getAmbientKelvins() - Constant.zeroCelsius) / Unit.K) + " C\n" 
				+ "# T_load = " + Util.f1.format(Tload * eps / Unit.K) + " K";
	}
	 

	public double getDirectTau() { 
		double eps = (instrument.getLoadTemperature() - instrument.excessLoad) / ((CSOScan<?,?>) scan).ambientT; 	
		return -Math.log(1.0-eps) * scan.horizontal.sinLat();
	}
	
	public final static float antennaTick = (float) (0.01 * Unit.s);
	public final static float tenthArcsec = (float) (0.1 * Unit.arcsec);

	static MaiTau maitau225GHz = new MaiTau();
	static MaiTau maitau350um = new MaiTau();

}
