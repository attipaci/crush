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

package crush.mustang2;

import crush.Channel;
import crush.array.SingleColorPixel;
import crush.resonators.FrequencyID;
import crush.resonators.Resonator;

public class Mustang2Pixel extends SingleColorPixel implements Resonator {
	public Mustang2PixelID id;
	public double polarizationAngle = Double.NaN;
	public int polarizationIndex = -1;
	public int readoutIndex;				// COL
	public int muxIndex;					// ROW
	public double frequency;
	public double attenuation;
	
	public double polarizationGain = 1.0, readoutGain = 1.0;
		

	public Mustang2Pixel(Mustang2 instrument, int backendIndex) {
		super(instrument, backendIndex);
	}
	
	
	@Override
	public double getFrequency() {
		return frequency;
	}
	
	@Override
	public FrequencyID getFrequencyID() {
		return id;
	}
	
	@Override
	public void setFrequencyID(FrequencyID id) {
		Mustang2PixelID mustangID = (Mustang2PixelID) id;
		
		this.id = mustangID;
		
		if(id == null) {
			flagID();
			return;
		}
		
		unflagID();
		
		setFixedIndex(mustangID.readoutIndex * Mustang2.maxReadoutChannels + mustangID.index);
		
		if(mustangID.isFlagged(Mustang2PixelID.FLAG_UNUSED)) flag(Channel.FLAG_DISCARD);

		if(mustangID.isFlagged(Mustang2PixelID.FLAG_BLIND)) {
			position = null;
			flag(Channel.FLAG_BLIND);
		}
		else {
			position = mustangID.position;
			polarizationAngle = mustangID.polarizationAngle;
			
			if(!Double.isNaN(polarizationAngle)) {
				polarizationIndex = (int) Math.round(polarizationAngle / POLARIZATION_STEP);
			}
			else polarizationIndex = (mustangID.index & 1) << 1;
			
			if(position != null) unflag(Channel.FLAG_BLIND);
		}
	}
	
	@Override
	public Channel getChannel() {
		return this;
	}
	
	@Override
	public void flagID() {
		flag(FLAG_NOTONEID);
	}
	
	@Override
	public void unflagID() {
		unflag(FLAG_NOTONEID);
	}
	
	@Override
	public boolean isAssigned() {
		if(id == null) return false;
		if(position == null) return false;
		return true;
	}
	
	public final static int FLAG_POL = 1 << nextSoftwareFlag++;
	public final static int FLAG_MUX = 1 << nextSoftwareFlag++;
	public final static int FLAG_NOTONEID = 1 << nextSoftwareFlag++;
	
	public final static int N_POLARIZATIONS = 4;
	public final static double POLARIZATION_STEP = Math.PI / N_POLARIZATIONS;
	
	
}
