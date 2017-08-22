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

package crush.instrument.polka;

import crush.Channel;
import crush.instrument.laboca.*;
import crush.polarization.*;
import crush.telescope.apex.APEXScan;
import jnum.Constant;
import jnum.Unit;
import jnum.astro.Stokes;

public class PolKaFrame extends LabocaFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 913725861256419630L;
	// phases for Q and U demodulation
	float Q,U;
	float Qh,Uh;
	float unpolarizedGain;
	double waveplateOffset = Double.NaN, waveplateAngle = Double.NaN, waveplateFrequency = Double.NaN;
	
	public PolKaFrame(APEXScan<Laboca, LabocaSubscan> parent) {
		super(parent);
	}
	
	@Override
    public void getChannelStokesResponse(Channel channel, Stokes toStokes) {  
        toStokes.setNQUV(0.5 * unpolarizedGain, 0.5 * Q, 0.5 * U, 0.0);
    }
	
	
	@Override
	public float getSourceGain(final int mode) {	
		switch(mode) {
		case PolarModulation.N : return 0.5F * unpolarizedGain * super.getSourceGain(TOTAL_POWER);
		case PolarModulation.Q : return 0.5F * Q * super.getSourceGain(TOTAL_POWER);
		case PolarModulation.U : return 0.5F * U * super.getSourceGain(TOTAL_POWER);
		default: return super.getSourceGain(mode);
		}
	}
	
	
	public void loadWaveplateData() {
		final PolKa polka = (PolKa) scan.instrument;
		
		if(polka.frequencyChannel != null) waveplateFrequency = data[polka.frequencyChannel.index];	
		else waveplateFrequency = polka.waveplateFrequency;		
		
		if(polka.phaseChannel != null) waveplateAngle = data[polka.phaseChannel.index];
		else waveplateAngle = Constant.twoPi * (MJD - 54000.0) * Unit.day * waveplateFrequency;
		
		if(polka.offsetChannel != null) waveplateOffset = data[polka.offsetChannel.index];	
		else waveplateOffset = Double.NaN;			
	}
	
	@Override
	public boolean validate() {

        
	    if(!super.validate()) return false;
	    
		final PolKa polka = (PolKa) scan.instrument;
		
		if(polka.isCounterRotating) waveplateAngle *= -1.0;
		
		final double iPhase = polka.incidencePhase;		
		double vPlateAngle = polka.referenceAngle + waveplateAngle;
		double projected = iPhase + Math.atan2(Math.sin(vPlateAngle - iPhase), polka.cosi * Math.cos(vPlateAngle - iPhase));
		double vPolAngle = 4.0 * projected;
		
		if(polka.analyzerPosition == PolKa.ANALYZER_H) vPolAngle += 2.0 * polka.analyzerDifferenceAngle;
			
		// The negative sign reflects the direction of rotation on sky...
		Qh = (float) Math.cos(-vPolAngle);
		Uh = (float) Math.sin(-vPolAngle);
		
		if(polka.isHorizontalPolarization) {
		    Q = Qh;
		    U = Uh;
		    unpolarizedGain = 1.0F + Qh * polka.etaQh + Uh * polka.etaUh;
		}
		else {
		    // calculate Q and U phases on sky based on the horizontal orientation...
		    final float cos2PA = (float)(cosPA * cosPA - sinPA * sinPA);
		    final float sin2PA = (float)(2.0 * sinPA * cosPA);
		
		    // Rotate by PA 
		    Q = cos2PA * Qh - sin2PA * Uh;
		    U = sin2PA * Qh + cos2PA * Uh;
		    
		    final float etaQ = cos2PA * polka.etaQh - sin2PA * polka.etaUh;
            final float etaU = sin2PA * polka.etaQh + cos2PA * polka.etaUh;
            
            unpolarizedGain = 1.0F + Q * etaQ + U * etaU;
		}
	
		return true;
		
	}
}
