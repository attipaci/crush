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
// Copyright (c) 2009 Attila Kovacs 

package crush.sourcemodel;

import java.util.*;

import crush.*;
import util.astro.CelestialProjector;
import util.data.Index2D;


public class SyncModMap extends ScalarMap {
	
	public SyncModMap(Instrument<?> instrument) {
		super(instrument);
	}
	
	public double product(float[] a, float[] b) {
		double sum = 0.0;
		for(int i=0; i<a.length; i++) sum += a[i] * b[i];
		return sum;
	}
	
	@Override
	protected synchronized int add(final Integration<?,?> integration, final Collection<? extends Pixel> pixels, final double[] sourceGain, double filtering, int signalMode) {
		int goodFrames = 0;
		final int excludeSamples = ~Frame.SAMPLE_SOURCE_BLANK;
		final CelestialProjector projector = new CelestialProjector(projection);
		final Index2D index = new Index2D();
		
		final float[] value = new float[integration.instrument.size()];
		
		final Modulated modulation = (Modulated) integration;
		
		final float[] waveform = new float[modulation.getPeriod(signalMode)];
		final int last = integration.size() + 1 - waveform.length;
		
		double integral = 0.5 * waveform.length;
		double samplingInterval = integration.instrument.samplingInterval;
		
		for(int fromt=0; fromt<last; fromt += waveform.length) {
			Arrays.fill(value, 0.0F);
			int samples = 0;
			Frame midFrame = null;
			modulation.getWaveForm(signalMode, fromt, waveform);
		
			int mid = waveform.length >> 1;
		
			for(int blockt = 0; blockt < waveform.length; blockt++) {
				final Frame exposure = integration.get(fromt + blockt);
				
				if(exposure == null) break;
				if(exposure.isFlagged(Frame.SOURCE_FLAGS)) break;
			
				if(blockt == mid) midFrame = exposure;
				
				final float[] data = exposure.data;
				final byte[] sampleFlag = exposure.sampleFlag;
				final float fG = waveform[blockt] * integration.gain;

				for(final Pixel pixel : pixels) for(final Channel channel : pixel) {
					final int c = channel.index;
					if((sampleFlag[c] & excludeSamples) == 0) value[c] += fG * data[c];
					else value[c] = Float.NaN;
				}
				
				samples++;
			}
			
			if(samples == waveform.length) {
				for(final Pixel pixel : pixels) {
					midFrame.project(pixel.getPosition(), projector);
					map.getIndex(projector.offset, index);
			
					final float fGC = (isMasked(index) ? 1.0F : (float)filtering) * midFrame.transmission;
					
					for(final Channel channel : pixel) if(!Float.isNaN(value[channel.index])) {
						value[channel.index] /= integral;
						addPoint(index, channel, midFrame, fGC * sourceGain[channel.index],  waveform.length * samplingInterval);
					}
				}				
				goodFrames += waveform.length;
			}
		}
		
		return goodFrames;
	}
	
	
	@Override
	protected void sync(Integration<?,?> integration, Collection<? extends Pixel> pixels, double[] sourceGain, int signalMode) {
		final CelestialProjector projector = new CelestialProjector(projection);
		final Index2D index = new Index2D();
		
		final int last = integration.size();
		
		final Modulated modulation = (Modulated) integration;
		final float[] waveform = new float[modulation.getPeriod(signalMode)];
		
		
		for(int t=0; t<last; ) {
			modulation.getWaveForm(signalMode, t, waveform);
			int nt = Math.min(waveform.length, integration.size() - t);
			
			for(int blockt = 0; blockt < nt; blockt++, t++) {
				final Frame exposure = integration.get(t);	
				if(exposure == null) continue;
				
				final float fG = waveform[blockt] * integration.gain * exposure.transmission; 
				
				if(exposure != null) for(Pixel pixel : pixels) { 
					getIndex(exposure, pixel, projector, index);
					//exposure.project(pixel.getPosition(), projector);
					//map.getIndex(projector.offset, index);

					for(final Channel channel : pixel) if((exposure.sampleFlag[channel.index] & Frame.SAMPLE_SKIP) == 0) {
						// Do not check for flags, to get a true difference image...
						exposure.data[channel.index] -= getIncrement(index, channel, fG * integration.sourceSyncGain[channel.index], fG * sourceGain[channel.index]);

						// Do the blanking here...
						if(isMasked(index)) exposure.sampleFlag[channel.index] |= Frame.SAMPLE_SOURCE_BLANK;
						else exposure.sampleFlag[channel.index] &= ~Frame.SAMPLE_SOURCE_BLANK;
					}
				}
			}
		}
	}
	
	
	
}
