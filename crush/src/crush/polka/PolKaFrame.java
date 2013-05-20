/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
import crush.apex.APEXArrayScan;
import crush.laboca.*;
import crush.polarization.*;

public class PolKaFrame extends LabocaFrame {
	// phases for Q and U demodulation
	float Q,U;
	float Qh,Uh;
	double waveplateOffset = Double.NaN, waveplateAngle = Double.NaN, waveplateFrequency = Double.NaN;
	
	public PolKaFrame(APEXArrayScan<Laboca, LabocaSubscan> parent) {
		super(parent);
	}
	
	@Override
	public double getSourceGain(final int mode) {	
		switch(mode) {
		case PolarModulation.N : return 0.5 * super.getSourceGain(TOTAL_POWER);
		case PolarModulation.Q : return 0.5 * Q * super.getSourceGain(TOTAL_POWER);
		case PolarModulation.U : return 0.5 * U * super.getSourceGain(TOTAL_POWER);
		default: return super.getSourceGain(mode);
		}
	}
	
	public void loadWaveplateData() {
		final PolKa polka = (PolKa) scan.instrument;
		
		if(polka.frequencyChannel != null) waveplateFrequency = data[polka.frequencyChannel.index];	
		else waveplateFrequency = polka.waveplateFrequency;		
		
		if(polka.phaseChannel != null) waveplateAngle = data[polka.phaseChannel.index];
		else waveplateAngle = 2.0 * Math.PI * (MJD - 54000.0) * Unit.day * waveplateFrequency;
		
		if(polka.offsetChannel != null) waveplateOffset = data[polka.offsetChannel.index];	
		else waveplateOffset = Double.NaN;			
	}
	
	@Override
	public void validate() {
		super.validate();
		
		final PolKa polka = (PolKa) scan.instrument;
		
		if(polka.isCounterRotating) waveplateAngle *= -1.0;
		
		final double dA = waveplateAngle - polka.referenceAngle - polka.incidencePhase;
		final double projected = polka.incidencePhase + Math.atan2(-Math.sin(dA), polka.cosi * Math.cos(dA));
		final double theta = 4.0 * projected - 2.0 * (polka.isVertical ? polka.verticalAngle : polka.horizontalAngle);
		
		Qh = (float) Math.cos(theta);
		Uh = (float) Math.sin(theta);
		
		// calculate Q and U phases on sky based on the horizontal orientation...
		final float cos2PA = (float)(cosPA*cosPA - sinPA*sinPA);
		final float sin2PA = (float)(2.0 * sinPA * cosPA);
		
		// Rotate by PA 
		Q = cos2PA * Qh - sin2PA * Uh;
		U = sin2PA * Qh + cos2PA * Uh;
	}
}
