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

import crush.apex.APEXArrayScan;
import crush.laboca.*;
import crush.polarization.*;

public class PolKaFrame extends LabocaFrame {
	// phases for Q and U demodulation
	float Q,U;
	float Qh,Uh;
	double wavePlateAngle = Double.NaN, wavePlateFrequency = Double.NaN;
	double MJD0;
	
	public PolKaFrame(APEXArrayScan<Laboca, LabocaSubscan> parent) {
		super(parent);
	}
	
	@Override
	public double getSourceGain(final int mode) {	
		if(mode == PolarModulation.N) return transmission;
		else if(mode == PolarModulation.Q) return Q * transmission;
		else if(mode == PolarModulation.U) return U * transmission;
		else return super.getSourceGain(mode);
	}
	
	@Override
	public void validate() {
		super.validate();
		
		// TODO Set the correct zero angle...
		MJD0 = scan.MJD;
		
		final PolKa polka = (PolKa) scan.instrument;
		
		if(Double.isNaN(wavePlateAngle)) wavePlateAngle = polka.getWavePlateAngle(MJD - MJD0);
		
		Qh = (float) Math.cos(4.0 * wavePlateAngle);
		Uh = (float) Math.sin(4.0 * wavePlateAngle);
			
		// calculate Q and U phases on sky based on the H and V phases...
		final float cos2PA = (float)(cosPA*cosPA - sinPA*sinPA);
		final float sin2PA = (float)(2.0 * sinPA * cosPA);
		
		Q = cos2PA * Qh - sin2PA * Uh;
		U = sin2PA * Qh + cos2PA * Uh;
		
		if(((PolKa) scan.instrument).isOrthogonal) {
			Q *= -1;
			U *= -1;
		}
	}

	@Override
	public void parse(float[][] fitsData) {
		super.parse(fitsData);
		final PolKa polka = (PolKa) scan.instrument;
		if(polka.wavePlateChannel != null) wavePlateAngle = data[polka.wavePlateChannel.index];	
		if(polka.frequencyChannel != null) wavePlateFrequency = data[polka.frequencyChannel.index];	
	}
	
	
}
