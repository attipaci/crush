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
package crush.mako;

import crush.Channel;
import crush.array.SingleColorPixel;
import crush.resonators.FrequencyID;
import crush.resonators.Resonator;
import jnum.Unit;
import jnum.Util;



public abstract class AbstractMakoPixel extends SingleColorPixel implements Resonator {
	public int array = AbstractMako.DEFAULT_ARRAY;
	public int row, col;
	
	public int toneIndex;
	public int toneBin;
	public int validCalPositions;
	public double toneFrequency;
	public double calError;
	
	public FrequencyID id;
	
	public AbstractMakoPixel(AbstractMako<?> array, int zeroIndex) {
		super(array, zeroIndex+1);
		toneIndex = zeroIndex;
		flag(FLAG_NOTONEID | FLAG_UNASSIGNED);
		row = -1;
		col = -1;
	}
	
	@Override
	public String getID() {
		return id == null ? Util.f1.format(toneFrequency) : Util.f1.format(id.freq);
	}
	
	@Override
	public double getHardwareGain() {
		return 1.0;
	}
	
	public abstract void calcNominalPosition();
	
	
	public double getAreaFactor() {
		return ((AbstractMako<?>) instrument).getAreaFactor();
	}
	
	@Override
	public String getRCPString() {
		return getID() + "\t" + Util.f1.format(getPosition().x() / Unit.arcsec) + "\t" + Util.f1.format(getPosition().y() / Unit.arcsec)
				+ "\t" + Util.f3.format(coupling);
		
		//return super.getRCPString() + "\t" + getID();
	}

	public abstract void setRowCol(int row, int col);
	
	@Override
	public Channel getChannel() { return this; }
	
	@Override
	public double getFrequency() { return toneFrequency; }
	
	@Override
	public FrequencyID getFrequencyID() { return id; }
	
	@Override
	public void setFrequencyID(FrequencyID id) { 
		this.id = id; 
		if(id == null) flag(FLAG_NOTONEID); 
		else unflag(FLAG_NOTONEID);
	}
		
	@Override
	public void flagID() { flag(FLAG_NOTONEID); }
	
	@Override
	public void unflagID() { unflag(FLAG_NOTONEID); }
	
	@Override
	public boolean isAssigned() {
		if(getFrequencyID() == null) return false;
		if(row < 0) return false;
		if(col < 0) return false;
		return true;
	}
	
	

	public final static int FLAG_NOTONEID = 1 << nextSoftwareFlag++;
	public final static int FLAG_UNASSIGNED = 1 << nextSoftwareFlag++;
	
	
}
