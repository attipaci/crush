/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.laboca;

import crush.*;
import crush.instrument.ChannelGroup;
import crush.instrument.FieldGainProvider;
import crush.instrument.Response;
import crush.telescope.apex.*;
import jnum.Configurator;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;

import java.io.*;
import java.util.*;

import nom.tam.fits.*;

public class LabocaSubscan extends APEXSubscan<LabocaFrame> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8919054480752078915L;

	double he3TimeScale = 3.0 * Unit.sec;		
	double blindTimeScale = 0.3 * Unit.sec;
	double rmsHe3 = 0.0;
	
	public LabocaSubscan(APEXScan<LabocaSubscan> parent) {
		super(parent);
	}
	
	@Override
    public LabocaScan getScan() { return (LabocaScan) super.getScan(); }
	
	@Override
    public Laboca getInstrument() { return (Laboca) super.getInstrument(); }

	
	@Override
	public void validate() {	
	    super.validate();
	    
		if(hasOption("he3")) {
			Configurator he3 = option("he3");
			if(he3.is("blinds")) setBlindTemperatures();
			
			if(he3.is("gains")) {
				for(LabocaPixel pixel : getInstrument()) pixel.temperatureGain = 0.0;
			}
			else temperatureCorrect();
			
			if(he3.containsKey("maxrms")) {
				double maxrms = he3.option("maxrms").getDouble() * Unit.mK;
				if(rmsHe3 > maxrms) {
					warning("Scan " + getScan().getID() + " temperature fluctuations exceed limit. Removing from dataset.");
					clear();
				}
			}
		}
		
	}
	
	@Override
	public void setZenithTau(double value) {	
		super.setZenithTau(value);
		CRUSH.values(this, "--->"
				+ " tau(LOS):" + Util.f3.format(value / getScan().horizontal.sinLat())
				+ ", PWV:" + Util.f2.format(getTau("pwv", zenithTau)) + "mm"
		);		
	}
	
	public void temperatureCorrect() {
		info("Correcting for He3 temperature drifts.");
		
		Response mode = (Response) getInstrument().modalities.get("temperature").get(0);
		Signal signal = getSignal(mode);
		
		if(signal == null) {
		    signal = mode.getSignal(this);	
		    signal.level(false);
		}
		
		rmsHe3 = signal.getRMS();
		info("RMS He3 temperature drift is " + Util.f3.format(1000.0 * rmsHe3 * getInstrument().gain) + " uK.");
		
		final Signal temperatureSignal = signal;
	
		new Fork<Void>() {
			@Override
			protected void process(LabocaFrame exposure) {
				final double dT = temperatureSignal.valueAt(exposure);
				if(!Double.isNaN(dT)) for(LabocaPixel pixel : getInstrument()) exposure.data[pixel.index] -= pixel.temperatureGain * dT;
			}			
		}.process();
		
	}
	
	public void writeTemperatureGains(String path) throws IOException {
		// Now write to a file
		String fileName = path + File.separator + "he3-gains-" + getScan().getID() + ".dat";
		getInstrument().writeTemperatureGains(fileName, getASCIIHeader());
	}

	@Override
	public void writeProducts() {
		super.writeProducts();
		if(hasOption("he3")) if(option("he3").is("gains")) {
			try { writeTemperatureGains(getInstrument().getOutputPath()); }
			catch(IOException e) { warning("Problem writing temperature gains."); }
		}
	}
	
	public void setBlindTemperatures() {
	     
		ChannelGroup<LabocaPixel> blindChannels = getInstrument().getBlindChannels();
		if(blindChannels.size() < 1) {
			warning("No blind channels for temperature correction.");
			return;
		}
		
		// Remove DC offsets now, if has not been done already...
		if(!hasOption("level")) removeChannelDrifts(blindChannels, size(), false);
		
		CorrelatedMode blindMode = (CorrelatedMode) getInstrument().modalities.get("blinds").get(0);
		try { blindMode.setGainProvider(new FieldGainProvider(LabocaPixel.class.getField("temperatureGain"))); }
		catch(NoSuchFieldException e) { return; }
		
		blindMode.resolution = blindTimeScale;
		blindMode.fixedGains = true;
		
		info("Calculating He3 temperatures from " + blindChannels.size() + " blind bolometer[s].");
		
		// Calculate the RMS temperature fluctuation...
		final CorrelatedSignal signal = new CorrelatedSignal(blindMode, this);
		
		try { blindMode.updateSignals(this, false); }
		catch(Exception e) { 
			error(e); 
			return;
		}
		
		for(LabocaFrame exposure : this) if(exposure != null) exposure.he3Temp = signal.valueAt(exposure); 
		
	}	
	
	// TODO broken, FITS libs do not handle curled row structure well...
	@Override
	public void readMonitor(BinaryTableHDU hdu)  throws IOException, FitsException, HeaderCardException {
	    
	    super.readMonitor(hdu);
	    
		// Don't read monitor table unless necessary...
		if(!hasOption("he3")) return;
		
		Configurator directive = option("he3");
		if(!(directive.is("thermistor") || directive.is("gains"))) return;
			
		info("Parsing He3 temperatures from MONITOR table... ");
	
		final int n = hdu.getNRows();
		
		final ArrayList<Vector2D> table = new ArrayList<Vector2D>();
		final int iMJD = hdu.findColumn("MJD");
		final int iLABEL = hdu.findColumn("MONPOINT");
		final int iVALUE = hdu.findColumn("MONVALUE");
				
		for(int i=0; i<n; i++) {
		    Object[] row = hdu.getRow(i);
		     
			if(((String) row[iLABEL]).equals("LABOCA_HE3TEMP")) {
				final Vector2D entry = new Vector2D();
				entry.setX(((double[]) row[iMJD])[0]);
				entry.setY(((double[]) row[iVALUE])[0]);
				table.add(entry);
			}
		}
		
		CRUSH.detail(this, "--> Found " + table.size() + " He3 temperature entries.");
		
		if(table.size() > 1) interpolateHe3Temperatures(table);
	}
	
	
	// TODO reimplement with localized data...
	public void interpolateHe3Temperatures(final ArrayList<Vector2D> table) {
		
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

		info("Interpolating He3 temperatures. " + (nFlagged == 0 ? "All good :-)" : "Dropped " + nFlagged + " frames without reliable He3 data :-("));	

	}
	
	@Override
	public LabocaFrame getFrameInstance() {
		return new LabocaFrame(getScan());
	}
	
	@Override
	public Object getTableEntry(String name) {
		if(name.equals("rmsHe3")) return rmsHe3 / Unit.mK;
		return super.getTableEntry(name);
	}
}
