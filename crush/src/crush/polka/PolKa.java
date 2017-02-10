/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
// Copyright (c) 2010 Attila Kovacs 

package crush.polka;

import crush.*;
import crush.laboca.*;
import crush.polarization.*;
import jnum.Unit;
import jnum.Util;

import java.io.*;
import java.util.Vector;

import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.FitsException;
import nom.tam.fits.HeaderCardException;

public class PolKa extends Laboca implements Oscillating {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5407116500180513583L;

	float etaQh = 0.0F, etaUh = 0.0F; // The intrinsic polarization fraction of the instrument...
	double waveplateFrequency; // The default rotation frequency
	double jitter = 0.003;
	double referenceAngle = 0.0 * Unit.deg;
	double horizontalAngle = 0.0 * Unit.deg;
	double verticalAngle = 90.0 * Unit.deg;
	double incidence = 0.0 * Unit.deg;
	double incidencePhase = 0.0* Unit.deg;
	double cosi;
	
	boolean hasAnalyzer = false;
	boolean isVertical = false;
	boolean isCounterRotating = false;
	boolean isHorizontalPolarization = false;
	
	Channel offsetChannel, phaseChannel, frequencyChannel;
	
	public PolKa() {
		super();
		setName("polka");
	}
	
	@Override
	public Object getTableEntry(String name) {
		if(name.equals("wpfreq")) return waveplateFrequency / Unit.Hz;
		else if(name.equals("wpjitter")) return jitter;
		else if(name.equals("wpdir")) return isCounterRotating ? "-" : "+";
		else if(name.equals("analyzer")) {
			if(!hasAnalyzer) return "-";
			else return isVertical ? "V" : "H";
		}
		else return super.getTableEntry(name);
	}

	@Override
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		super.validate(scans);
		
		final PolKaScan firstScan = (PolKaScan) scans.get(0);
		final PolKa polka = (PolKa) firstScan.instrument;
		
		if(polka.hasAnalyzer) info("Polarized reduction mode (H or V analyzer).");
		else info("Total-power reduction mode (no analyzer grid).");
			
		// Make sure the rest of the list conform to the first scan...
		for(int i=scans.size(); --i > 0; ) {
			PolKaScan scan = (PolKaScan) scans.get(i);
			if(((PolKa) scan.instrument).hasAnalyzer != polka.hasAnalyzer) {
				warning("Scan " + scan.getID() + " is " 
						+ (polka.hasAnalyzer ? "total-power (no analyzer)" : "is polarized")
						+ ". Dropping from dataset.");
				scans.remove(i);
			}
		}
		
	}
	
	
	@Override
	public void validate(Scan<?,?> scan) {
		info("Parsing waveplate settings: ");
		
		if(hasOption("waveplate.frequency")) { 
			waveplateFrequency = option("waveplate.frequency").getDouble() * Unit.Hz;
			info(" --> Frequency = " + Util.f3.format(waveplateFrequency / Unit.Hz) + " Hz.");
		}
		else {	
			if(hasOption("waveplate.channel")) {
				int beIndex = option("waveplate.channel").getInt();
				phaseChannel = get(beIndex-1);
				if(phaseChannel != null) 
					info(" --> Angles from channel " + phaseChannel.getID() + ".");
			}
			
			if(hasOption("waveplate.fchannel")) {
				int beIndex = option("waveplate.fchannel").getInt();
				frequencyChannel = get(beIndex-1);
				if(frequencyChannel != null) 
					info(" --> Frequencies from channel " + frequencyChannel.getID() + ".");
			}
			
			if(hasOption("waveplate.tchannel")) {
				int beIndex = option("waveplate.tchannel").getInt();
				offsetChannel = get(beIndex-1);
				if(offsetChannel != null) 
					info(" --> Crossing times from channel " + offsetChannel.getID() + ".");
			}		
		}
		
		if(hasOption("waveplate.jitter")) { 
			jitter = option("waveplate.jitter").getDouble();
			info(" --> Jitter = " + Util.f2.format(100.0 * jitter) + "%.");
		}
		
		if(hasOption("waveplate.refangle")) referenceAngle = option("waveplate.refangle").getDouble();
		
		if(hasOption("waveplate.incidence")) {
			incidence = option("waveplate.incidence").getDouble() * Unit.deg;
			info(" --> incidence = " + Util.f1.format(incidence/Unit.deg) + " deg.");
		}
		
		if(hasOption("waveplate.incidence.phase")) {
			incidencePhase = option("waveplate.incidence.phase").getDouble() * Unit.deg;
			info(" --> incidence phase = " + Util.f1.format(incidencePhase/Unit.deg) + " deg.");
		}
		
		isCounterRotating = hasOption("waveplate.counter");
		
		if(hasOption("analyzer.h")) horizontalAngle = option("analyzer.h").getDouble() * Unit.deg;
		if(hasOption("analyzer.v")) verticalAngle = option("analyzer.v").getDouble() * Unit.deg;
		
		cosi = Math.cos(incidencePhase);
		
		isHorizontalPolarization = hasOption("polarization.horizontal");
			
		etaQh = hasOption("polarization.q0") ? option("polarization.q0").getFloat() : 0.0F;
		etaUh = hasOption("polarization.u0") ? option("polarization.u0").getFloat() : 0.0F;
		
		super.validate(scan);
	}
	
	@Override
	public void readPar(BinaryTableHDU hdu) throws IOException, FitsException, HeaderCardException {
		super.readPar(hdu);
	
		int iAnalyzer = hdu.findColumn("POLTY");
		
		if(hasOption("analyzer")) setAnalyzer(option("analyzer").getValue().charAt(0));
		else if(iAnalyzer > 0) setAnalyzer(((String) hdu.getRow(0)[iAnalyzer]).charAt(0));
		else {
			hasAnalyzer = false;
			warning("Analyzer position is not defined. Assuming LABOCA total-power mode.");
			CRUSH.suggest(this, "         * Use 'analyzer' option to to manually set 'H' or 'V'.");
			
		}
		
		if(hasAnalyzer) info("Analyzer grid orientation is " + (isVertical ? "V" : "H"));
	}
	
	public void setAnalyzer(char c) {
		switch(Character.toUpperCase(c)) {
		case 'N' : 
			hasAnalyzer = false;
			warning("Total-power data. You really should use 'laboca' as your instrument\n" +
			    "          However, 'polka' will try its best to reduce it anyway...");
			break;
		case 'V' :
		case 'Y' :
			hasAnalyzer = true; isVertical = true; break;
		case 'H' :
		case 'X' :
			hasAnalyzer = true; isVertical = false; break;
		default :
			error("Polarization analyzer position is undefined.");
			CRUSH.suggest(this, "     * Set the 'analyzer' option to 'H' or 'V' to specify. Exiting.");
			System.exit(1);
			break;
		}
	}
	
	@Override
	public LabocaScan getScanInstance() {
		return new PolKaScan(this);
	}
	
	@Override
	public SourceModel getSourceModelInstance() {
		if(!hasAnalyzer) return super.getSourceModelInstance();
		else if(hasOption("source.synchronized")) return new SyncPolarMap(this);
		else return new PolarMap(this);
	}

    @Override
    public double getFrequency() {
        return waveplateFrequency;
    }

    @Override
    public double getQ() {
        return 1.0 / jitter;
    }  
	
}
