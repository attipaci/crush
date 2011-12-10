/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2010 Attila Kovacs 

package crush.polka;

import util.Unit;
import util.Util;
import crush.*;
import crush.laboca.*;
import crush.polarization.*;

import java.io.*;

import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.FitsException;
import nom.tam.fits.HeaderCardException;

public class PolKa extends Laboca {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5407116500180513583L;

	float q0 = 0.0F, u0 = 0.0F; // The intrinsic polarization fraction of the instrument...
	double waveplateFrequency = 1.56 * Unit.Hz; // The default rotation frequency
	double jitter = 0.003;
	double referenceAngle = 0.0 * Unit.deg;
	double horizontalAngle = 0.0 * Unit.deg;
	double verticalAngle = 90.0 * Unit.deg;
	double incidence = 0.0 * Unit.deg;
	double incidencePhase = 0.0* Unit.deg;
	double cosi;
	
	boolean isVertical = false;
	boolean isCounterRotating = false;

	
	Channel offsetChannel, phaseChannel, frequencyChannel;
	
	public PolKa() {
		super();
		name = "polka";
	}
	
	@Override
	public void validate(Scan<?,?> scan) {
		System.err.println(" Parsing waveplate settings: ");
		
		if(hasOption("waveplate.frequency")) { 
			waveplateFrequency = option("waveplate.frequency").getDouble() * Unit.Hz;
			System.err.println("  --> Frequency = " + Util.f3.format(waveplateFrequency / Unit.Hz) + " Hz.");
		}
		else {	
			if(hasOption("waveplate.channel")) {
				int beIndex = option("waveplate.channel").getInt();
				phaseChannel = get(beIndex-1);
				if(phaseChannel != null) 
					System.err.println("  --> Angles from channel " + phaseChannel.storeIndex + ".");
			}
			
			if(hasOption("waveplate.fchannel")) {
				int beIndex = option("waveplate.fchannel").getInt();
				frequencyChannel = get(beIndex-1);
				if(frequencyChannel != null) 
					System.err.println("  --> Frequencies from channel " + frequencyChannel.storeIndex + ".");
			}
			
			if(hasOption("waveplate.tchannel")) {
				int beIndex = option("waveplate.tchannel").getInt();
				offsetChannel = get(beIndex-1);
				if(offsetChannel != null) 
					System.err.println("  --> Crossing times from channel " + offsetChannel.storeIndex + ".");
			}		
		}
		
		if(hasOption("waveplate.jitter")) { 
			jitter = option("waveplate.jitter").getDouble();
			System.err.println("  --> Jitter = " + Util.f2.format(100.0 * jitter) + "%.");
		}
		
		if(hasOption("waveplate.refangle")) referenceAngle = option("waveplate.refangle").getDouble();
		
		if(hasOption("waveplate.incidence")) {
			incidence = option("waveplate.incidence").getDouble() * Unit.deg;
			System.err.println("  --> incidence = " + Util.f1.format(incidence/Unit.deg) + " deg.");
		}
		
		if(hasOption("waveplate.incidence.phase")) {
			incidencePhase = option("waveplate.incidence.phase").getDouble() * Unit.deg;
			System.err.println("  --> incidence phase = " + Util.f1.format(incidencePhase/Unit.deg) + " deg.");
		}
		
		isCounterRotating = hasOption("waveplate.counter");
		
		if(hasOption("analyzer.h")) horizontalAngle = option("analyzer.h").getDouble() * Unit.deg;
		if(hasOption("analyzer.v")) verticalAngle = option("analyzer.v").getDouble() * Unit.deg;
		
		cosi = Math.cos(incidencePhase);
			
		super.validate(scan);
	}
	
	@Override
	public void readPar(BinaryTableHDU hdu) throws IOException, FitsException, HeaderCardException {
		super.readPar(hdu);
	
		int iAnalyzer = hdu.findColumn("POLTY");
		
		if(hasOption("analyzer")) {
			String value = option("analyzer").getValue().toUpperCase();
			setAnalyzer(value.charAt(0));
		}
		else if(iAnalyzer > 0) setAnalyzer(((String) hdu.getRow(0)[iAnalyzer]).charAt(0));
		else {
			System.err.println("ERROR! Analyzer position is not defined. Use 'analyzer' option to");
			System.err.println("       to manually set it to 'H' or 'V'.");
			System.exit(1);
		}
		
		System.err.println(" Analyzer grid orientation is " + (isVertical ? "V" : "H"));
	}
	
	public void setAnalyzer(char c) {
		switch(c) {
		case 'N' : 
			System.err.println();
			System.err.println("WARNING! It appears you are trying to reduce total-power data in");
			System.err.println("         polarization mode. You should use 'laboca' as your instrument");
			System.err.println("         instead of 'polka'. Otherwise, if you are reducing 2010 data");
			System.err.println("         use the 'analyzer' key to set 'H' or 'V' analyzer positions");
			System.err.println("         manually. Exiting.");
			System.err.println();
			System.exit(1);
			break;
		case 'V' : 
			isVertical = true; break;
		case 'H' :
			isVertical = false; break;
		default :
			System.err.println();
			System.err.println("WARNING! Polarization analyzer position is undefined. Set the");
			System.err.println("         'analyzer' option to 'H' or 'V' to specify. Exiting.");
			System.err.println();
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
		if(hasOption("source.synchronized")) return new SyncPolarMap(this);
		else return new PolarMap(this);
	}  
	
}
