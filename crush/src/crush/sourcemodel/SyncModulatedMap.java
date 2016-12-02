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
// Copyright (c) 2009 Attila Kovacs 

package crush.sourcemodel;

import java.util.*;

import crush.*;
import jnum.Parallel;
import jnum.astro.AstroProjector;
import jnum.data.Index2D;


public class SyncModulatedMap extends ScalarMap {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -8251377808607977894L;

	public SyncModulatedMap(Instrument<?> instrument) {
		super(instrument);
	}
	
	public double product(float[] a, float[] b) {
		double sum = 0.0;
		for(int i=0; i<a.length; i++) sum += a[i] * b[i];
		return sum;
	}
	
	@Override
	protected int add(final Integration<?,?> integration, final List<? extends Pixel> pixels, final double[] sourceGain, final int signalMode) {
		final int excludeSamples = ~Frame.SAMPLE_SOURCE_BLANK;	
			
		final Periodic modulation = (Periodic) integration;
		
		final int waveSamples = modulation.getPeriod(signalMode);
		final float samplingInterval = (float) integration.instrument.samplingInterval;
		final float iI = 2.0F / waveSamples;
		final int mid = waveSamples >> 1;
		

		CRUSH.Fork<Integer> accumulator = integration.new BlockFork<Integer>(waveSamples) {
			private float[] channelValue;
			private AstroProjector projector;
			private Index2D index;
			private int goodFrames = 0;
			
			@Override
			protected void init() {
				super.init();
				
				channelValue = integration.instrument.getFloats();
				
				projector = new AstroProjector(getProjection());
				index = new Index2D();
			}
			
			@Override
			protected void cleanup() {
				super.cleanup();
				Instrument.recycle(channelValue);
			}
			
			@Override
			protected void process(int from, int to) {
				Arrays.fill(channelValue, 0.0F);
				
				Frame midFrame = null;
				
				int blockt = getBlockSize() - 1;
				
				// If there isn't data for the full waveform, then return!
				for(int t=from; t < to; blockt++) {
					final Frame exposure = integration.get(t);	
					if(exposure == null) return;
					if(exposure.isFlagged(Frame.SOURCE_FLAGS)) return;
				
					if(blockt == mid) midFrame = exposure;
					
					final float[] data = exposure.data;
					final byte[] sampleFlag = exposure.sampleFlag;
					final float fG = integration.gain * exposure.getSourceGain(signalMode);

					for(final Pixel pixel : pixels) for(final Channel channel : pixel) {
						final int c = channel.index;
						if((sampleFlag[c] & excludeSamples) == 0) channelValue[c] += fG * data[c];
						else channelValue[c] = Float.NaN;
					}
				}
		
				goodFrames += waveSamples;
				
				for(final Pixel pixel : pixels) {
					midFrame.project(pixel.getPosition(), projector);
					map.getIndex(projector.offset, index);
				
					final float fG = midFrame.getSourceGain(signalMode);
						
					for(final Channel channel : pixel) if(!Float.isNaN(channelValue[channel.index])) {
						channelValue[channel.index] *= iI;
						addPoint(index, channel, midFrame, fG * sourceGain[channel.index],  waveSamples * samplingInterval);
					}				
				}		
			}
			
			@Override
			public Integer getLocalResult() { return goodFrames; }
			
			@Override
			public Integer getResult() {
				int total = 0;
				for(Parallel<Integer> task : getWorkers()) total += task.getLocalResult();
				return total;
			}
			
		};
		
		accumulator.process();
			
		return accumulator.getResult();
	}
	
}
