/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.gismo;

import crush.ChannelGroup;
import crush.FieldGainProvider;
import crush.Integration;
import crush.Response;
import crush.Signal;
import jnum.Unit;

public class SAEResponse extends Response {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 525169198737359678L;

	public SAEResponse(GismoPixel channel) throws NoSuchFieldException {
		name = "sae-" + channel.getFixedIndex();
		ChannelGroup<GismoPixel> g = new ChannelGroup<GismoPixel>("pixel-" + channel.getFixedIndex());
		g.add(channel);
		setChannels(g);
		gainProvider = new FieldGainProvider(GismoPixel.class.getField("saeGain"));
	}
	
	public void initSignal(Integration<?, ?> integration) {
		if(getChannelCount() < 1) return;
		
		final GismoIntegration gismoIntegration = (GismoIntegration) integration;
		final float[] data = new float[integration.size()];
		
		final GismoPixel pixel = (GismoPixel) getChannel(0);
		
		for(int i=integration.size(); --i >= 0; ) {
			final GismoFrame exposure = gismoIntegration.get(i);
			if(exposure == null) {
				data[i] = Float.NaN;
				continue;
			}
			data[i] = exposure.SAE[pixel.index];
		}
		
		Signal signal = new Signal(this, integration, data, true);
			
		if(integration.hasOption("sae.smooth")) {
			double resolution = integration.option("sae.smooth").getDouble() * Unit.s / integration.instrument.samplingInterval;
			if(resolution > 1.0) signal.smooth(resolution);
		}
		
		if(integration.hasOption("sae.hipass")) {
			int N = integration.framesFor(integration.option("sae.hipass").getDouble() * Unit.s);
			if(N<size()) signal.removeDrifts(N, false);
		}	
		
	}

	@Override
	public Signal getSignal(Integration<?, ?> integration) {
		return null;
	}
}
