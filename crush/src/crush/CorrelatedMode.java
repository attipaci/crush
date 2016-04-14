/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush;

import java.lang.reflect.*;


public class CorrelatedMode extends Mode {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -8794899798480857476L;
	
	public int skipFlags = ~0;
	
	public CorrelatedMode() {}
	
	public CorrelatedMode(ChannelGroup<?> group) {
		super(group);
	}
	
	public CorrelatedMode(ChannelGroup<?> group, Field gainField) { 
		super(group, gainField);
	}
	
	// Always return normalized gains for use...
    @Override
    public float[] getGains(boolean validate) throws Exception {
		float[] G = super.getGains(validate);
		normalizeGains(G);
		return G;
	}
	
    public float normalizeGains() throws Exception {
        float[] G = super.getGains(true);
        float aveG = normalizeGains(G);
        super.setGains(G, false);
        return aveG;
    }
    	
    // When setting gains, normalize automatically also...
    @Override
    public boolean setGains(float[] gain, boolean flagNormalized) throws Exception {
        normalizeGains(gain);
        return super.setGains(gain, false);
    }

	private float normalizeGains(float[] gain) {
		final float aveG = (float) getChannels().getTypicalGainMagnitude(gain, skipFlags & ~gainFlag);
		if(aveG == 1.0) return 1.0F;
		
		for(int i=gain.length; --i >= 0; ) gain[i] /= aveG;
		return aveG;
	}

	public ChannelGroup<?> getValidChannels() {
		return getChannels().copyGroup().discard(skipFlags);
	}
	
	public void updateSignals(Integration<?, ?> integration, boolean isRobust) throws Exception {	
		CorrelatedSignal signal = (CorrelatedSignal) integration.getSignal(this);
		if(signal == null) signal = new CorrelatedSignal(this, integration);
		signal.update(isRobust); 	

		// Solve for the correlated phases also, if required
		if(integration.isPhaseModulated()) if(integration.hasOption("phases")) {
			PhaseSignal pSignal = ((PhaseModulated) integration).getPhases().signals.get(this);
			if(pSignal == null) pSignal = new PhaseSignal(((PhaseModulated) integration).getPhases(), this);
			pSignal.update(isRobust);
		}
	}	
	
	
}
