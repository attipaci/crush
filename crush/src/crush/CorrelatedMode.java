/*******************************************************************************
 * Copyright (c) 2018 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush;

import java.lang.reflect.*;

/**
 * A class for correlated common mode signals, which do not have a known external driving source (in contrast to 
 * {@link crush.instrument.Response}).
 * <p>
 * 
 * The removal of correlated common modes, will always involve an estimation of the common mode signal from the
 * associated detector timestreams themselves, using the current set of relative detector gains (normalized to
 * a robust mean of 1). Optionally, it may also involve the estimation of the relative detector channel gains 
 * themselves, to that common mode, similarly to {@link crush.instrument.Response}s. When gain estimation is
 * enabled, the derived gains will be explicitly renormalized to a robust mean of 1.
 * <p>
 * 
 * Similar correlated modes can be group together into a {@link CorrelatedModality}, similarly to how 
 * similar {@link ChannelGroup}s constitute a {@link ChannelDivision}.
 * 
 * 
 * @author Attila Kovacs <attila@sigmyne.com>
 *
 */
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
		return getChannels().createGroup().discard(skipFlags);
	}
	
	public void updateSignals(Integration<?> integration, boolean isRobust) throws Exception {	
		CorrelatedSignal signal = (CorrelatedSignal) integration.getSignal(this);
		if(signal == null) signal = new CorrelatedSignal(this, integration);

		signal.update(isRobust); 	
		
		// Solve for the correlated phases also, if required...
		if(integration.isPhaseModulated()) if(integration.hasOption("phases"))
			((PhaseModulated) integration).getPhases().getSignal(this).update(isRobust);
	}	
	
	
}
