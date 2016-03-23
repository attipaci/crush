/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.laboca;

import crush.*;
import crush.apex.*;
import jnum.Configurator;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;
import jnum.text.TableFormatter;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;

import nom.tam.fits.*;

public class LabocaSubscan extends APEXArraySubscan<Laboca, LabocaFrame> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8919054480752078915L;

	double he3TimeScale = 3.0 * Unit.sec;		
	double blindTimeScale = 0.3 * Unit.sec;
	double rmsHe3 = 0.0;
	
	public LabocaSubscan(APEXArrayScan<Laboca, LabocaSubscan> parent) {
		super(parent);
	}

	
	@Override
	public void validate() {	
	    super.validate();
	    
		if(hasOption("he3")) {
			Configurator he3 = option("he3");
			if(he3.equals("blinds")) setBlindTemperatures();
			
			if(he3.equals("gains")) {
				for(LabocaPixel pixel : instrument) pixel.temperatureGain = 0.0;
			}
			else temperatureCorrect();
			
			if(he3.containsKey("maxrms")) {
				double maxrms = he3.get("maxrms").getDouble() * Unit.mK;
				if(rmsHe3 > maxrms) {
					System.err.println("Scan " + scan.getID() + " temperature fluctuations exceed limit. Removing from dataset.");
					clear();
				}
			}
		}
		
	}
	
	@Override
	public void setZenithTau(double value) {	
		super.setZenithTau(value);
		System.err.println("   --->"
				+ " tau(LOS):" + Util.f3.format(value / scan.horizontal.sinLat())
				+ ", PWV:" + Util.f2.format(getTau("pwv", zenithTau)) + "mm"
		);		
	}
	
	public void temperatureCorrect() {
		System.err.println("   Correcting for He3 temperature drifts.");
		
		Response mode = (Response) instrument.modalities.get("temperature").get(0);
		Signal signal = getSignal(mode);
		
		if(signal == null) {
		    signal = mode.getSignal(this);	
		    signal.level(false);
		}
		
		rmsHe3 = signal.getRMS();
		System.err.println("   RMS He3 temperature drift is " + Util.f3.format(rmsHe3 * instrument.gain / Unit.mK) + " mK.");
		
		final Signal temperatureSignal = signal;
	
		new Fork<Void>() {
			@Override
			protected void process(LabocaFrame exposure) {
				final double dT = temperatureSignal.valueAt(exposure);
				if(!Double.isNaN(dT)) for(LabocaPixel pixel : instrument) exposure.data[pixel.index] -= pixel.temperatureGain * dT;
			}			
		}.process();
		
	}
	
	public void writeTemperatureGains() throws IOException {
		// Now write to a file
		String fileName = CRUSH.workPath + File.separator + "he3-gains-" + scan.getID() + ".dat";
		try { instrument.writeTemperatureGains(fileName, getASCIIHeader()); }
		catch(IOException e) { e.printStackTrace(); }
	}

	@Override
	public void writeProducts() {
		super.writeProducts();
		if(hasOption("he3")) if(option("he3").equals("gains")) {
			try { writeTemperatureGains(); }
			catch(IOException e) { System.err.println("WARNING! Problem writing temperature gains."); }
		}
	}
	
	public void setBlindTemperatures() {
	     
		ChannelGroup<LabocaPixel> blindChannels = instrument.getBlindChannels();
		if(blindChannels.size() < 1) {
			System.err.println("   WARNING! No blind channels for temperature correction.");
			return;
		}
		
		// Remove DC offsets now, if has not been done already...
		if(!hasOption("level")) removeChannelDrifts(blindChannels, size(), false);
		
		CorrelatedMode blindMode = (CorrelatedMode) instrument.modalities.get("blinds").get(0);
		try { blindMode.setGainProvider(new FieldGainProvider(LabocaPixel.class.getField("temperatureGain"))); }
		catch(NoSuchFieldException e) { return; }
		
		blindMode.resolution = blindTimeScale;
		blindMode.fixedGains = true;
		
		System.err.println("   Calculating He3 temperatures from " + blindChannels.size() + " blind bolometer[s].");
		
		// Calculate the RMS temperature fluctuation...
		final CorrelatedSignal signal = new CorrelatedSignal(blindMode, this);
		
		try { blindMode.updateSignals(this, false); }
		catch(Exception e) { 
			e.printStackTrace(); 
			return;
		}
		
		for(LabocaFrame exposure : this) if(exposure != null) exposure.he3Temp = signal.valueAt(exposure); 
		
	}	
	
	
	@Override
	public void readMonitor(BinaryTableHDU hdu)  throws IOException, FitsException, HeaderCardException {
		// Don't read monitor table unless necessary...
		if(!hasOption("he3")) return;
		
		Configurator directive = option("he3");
		if(!(directive.equals("thermistor") || directive.equals("gains"))) return;
			
		System.err.println("   Parsing He3 temperatures from MONITOR table.");
		Header header = hdu.getHeader();
		int n = header.getIntValue("NAXIS2");
		
		ArrayList<Vector2D> table = new ArrayList<Vector2D>();
		final int iMJD = hdu.findColumn("MJD");
		final int iLABEL = hdu.findColumn("MONPOINT");
		final int iVALUE = hdu.findColumn("MONVALUE");
		
		for(int i=0; i<n; i++) {
			final Object[] row = hdu.getRow(i);	
		
			if(((String) row[iLABEL]).equals("LABOCA_HE3TEMP")) {
				final Vector2D entry = new Vector2D();
				entry.setX(((double[]) row[iMJD])[0]);
				entry.setY(((double[]) row[iVALUE])[0]);
				table.add(entry);
			}
		}
		
		interpolateHe3Temperatures(table);
	}
	
	// TODO reimplement with localized data...
	public void interpolateHe3Temperatures(final ArrayList<Vector2D> table) {
		System.err.print("   Interpolating He3 temperatures. ");
		// plus or minus in days;
		final double smoothFWHM = he3TimeScale / Unit.day;
		final double windowSize = 2.0 * smoothFWHM;
		final double sigma = smoothFWHM / Constant.sigmasInFWHM;
		final int nt = size();	
		
		int nFlagged = 0;
		int from = 0, to=0;
		for(int t = 0; t < nt; t++) {
			LabocaFrame exposure = get(t);
			if(exposure == null) continue;
			
			double MJD0 = exposure.MJD;

			// Adjust the smoothing window...
			while(MJD0 - table.get(from).x() > windowSize || from == table.size()-1) from++;
			while(table.get(to).x() - MJD0 < windowSize || to == table.size()-1) to++;

			double sum = 0.0, sumw = 0.0;

			for(int tt=from; tt<=to; tt++) {
				Vector2D entry = table.get(tt);
				double dev = (MJD0 - entry.x()) / sigma;
				double w = Math.exp(-0.5*dev*dev);
				sum += w * entry.y();
				sumw += w;
			}

			if(sumw > 0.3) exposure.he3Temp = (float) (sum/sumw);
			else {
				set(t, null);
				nFlagged++;
			}
		}

		System.err.println(nFlagged == 0 ? "All good :-)" : "Dropped " + nFlagged + " frames without reliable He3 data :-(");	

	}
	
	@Override
	public LabocaFrame getFrameInstance() {
		return new LabocaFrame((LabocaScan) scan);
	}
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("rmsHe3")) return Util.defaultFormat(rmsHe3 / Unit.mK, f);
		else return super.getFormattedEntry(name, formatSpec);
	}
}
