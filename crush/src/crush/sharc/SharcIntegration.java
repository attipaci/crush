/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.sharc;

import java.io.DataInput;
import java.io.IOException;

import crush.cso.CSOIntegration;

public class SharcIntegration extends CSOIntegration<Sharc, SharcFrame> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1534830845454096961L;
	
	public SharcIntegration(SharcScan parent) {
		super(parent);
	}

	@Override
	public SharcFrame getFrameInstance() {
		return new SharcFrame((SharcScan) scan);
	}

	public double getModulationFrequency() {
	    return ((SharcScan) scan).chop_frequency;
	}
	
	public void readFrom(DataInput in) throws IOException {	
		SharcScan sharcScan = (SharcScan) scan;
		int cols = sharcScan.nsamples;
		ensureCapacity(cols);
		
		float iScale = 1.0F / sharcScan.scale_factor;
		
		//SharcFrame.scanIndexOffset = sharcScan.quadrature == 0 ? 2 : 4;
		
		for(int T=0, t=0; T<cols; T++) {
			SharcFrame frame = new SharcFrame(sharcScan);
			frame.readFrom(in, t++, iScale);								// the 'in-phase' data
			add(frame);
			
			if(sharcScan.quadrature != 0) {
				frame = new SharcFrame(sharcScan);
				frame.readFrom(in, t++, -iScale);							// the 'quadrature' data
				add(frame);
			}
		}
	}
	
}
