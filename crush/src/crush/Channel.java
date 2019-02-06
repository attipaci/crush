/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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


import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;

import crush.instrument.Overlap;
import jnum.Copiable;
import jnum.Flagging;
import jnum.Util;
import jnum.text.SmartTokenizer;
import jnum.util.FlagSpace;
import jnum.util.FlagBlock;



public abstract class Channel implements Serializable, Cloneable, Comparable<Channel>, Flagging, Copiable<Channel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5541239418892633654L;

	public Instrument<?> instrument;
	private Pixel pixel;
	
	private transient Collection<Overlap<Channel>> overlaps;
	
	transient float temp, tempG, tempWG, tempWG2;
	
	public int index;
	private int fixedIndex;
	private int flag = 0;
	public int sourcePhase = 0;
	
	public double offset = 0.0; // At the readout stage! (as of 2.03)
	public double gain = 1.0;
	public double nonlinearity = 0.0;
	public double coupling = 1.0;
	public double weight = DEFAULT_WEIGHT;
	public double variance = DEFAULT_VARIANCE;
	public double dof = 1.0;
	
	public double dependents = 0.0;
	
	public double sourceFiltering = 1.0; // filtering on source...
	public double directFiltering = 1.0; // The effect of FFT filters on source.
	
	public double filterTimeScale = Double.POSITIVE_INFINITY;
	public double oneOverFStat = Double.NaN;
	
	public int spikes = 0;
	public int inconsistencies = 0; // such as jumps...
	
    /**
     * Constructs a new pixel for the specified instrument.
     * 
     * @param instrument    The instrument state to which this pixel belongs.
     * @param fixedIndex    An index that uniquely identifies the pixel between 0 and {@link Instrument#maxPixels()}.
     */
	public Channel(Instrument<?> instrument, int fixedIndex) {
		this.instrument = instrument;
		setFixedIndex(fixedIndex);
	}
	
	@Override
	public Channel clone() {
		try { return (Channel) super.clone(); }
		catch(CloneNotSupportedException e) { return null; }		
	}
	
	// By default, channels can be sorted by backend-index
	@Override
	public int compareTo(Channel channel) {
		if(channel.fixedIndex == fixedIndex) return 0;
		return fixedIndex < channel.fixedIndex ? -1 : 1;
	}
	
	@Override
	public Channel copy() {
		Channel copy = clone();
		copy.pixel = null;    // TODO pixel assignment does not copy over, since its channels are decoupled after copy...
		copy.overlaps = null; // TODO copies aren't perfect since overlaps don't copy over...
		return copy;
	}
	
	public boolean isValid() { return isFlagged(); }
		
	@Override
	public final boolean isFlagged(final int pattern) {
		return (flag & pattern) != 0;
	}
	
	@Override
	public final boolean isUnflagged(final int pattern) {
		return (flag & pattern) == 0;
	}

	@Override
	public final boolean isFlagged() {
		return flag != 0;
	}
	
	@Override
	public final boolean isUnflagged() {
		return flag == 0; 
	}
	
	@Override
	public final void flag(final int pattern) {
		flag |= pattern;
	}
	
	@Override
	public final void unflag(final int pattern) {
		flag &= ~pattern;
	}

	@Override
	public final void unflag() {
		flag = 0;
	}
	
	public final int getFlags() { return flag; }
	
	public double getFiltering(Integration<?> integration) {
		final double nDrifts = Math.ceil(integration.getDuration() / filterTimeScale);
		return directFiltering * (1.0 - nDrifts / integration.size());
	}
	
	public final synchronized void addDependents(double dp) {
		dependents += dp;
	}
	
	public final synchronized void removeDependents(double dp) {
		dependents -= dp;
	}
	
	public final int getIndex() { return index; }
	
	public final int getFixedIndex() { return fixedIndex; }
	
	public final void setFixedIndex(int i) { fixedIndex = i; }
	
	public String getID() { return Integer.toString(getFixedIndex() + 1); }
	
	public double getHardwareGain() {
		return instrument.gain;
	}
	
	// Write backendIndex plus whatever information....
	@Override
	public String toString() {
		String text = getID() + "\t" +
			Util.f3.format(gain) + " \t" +
			Util.e3.format(weight) + " \t" +
			flagSpace.toString(flag);
		return text;
	}
	
	public double getFrequency() { return instrument.getFrequency(); }
	
	public final Pixel getPixel() { return pixel; }
	
	public void setPixel(Pixel pixel) { this.pixel = pixel; }
	
	public Collection<Overlap<Channel>> getOverlaps() { return overlaps; }
	
	public void setOverlaps(Collection<Overlap<Channel>> overlaps) { this.overlaps = overlaps; }
	
	public void clearOverlaps() {
		if(overlaps != null) overlaps.clear();
	}
	
	public synchronized void addOverlap(Overlap<Channel> overlap) {
		if(overlap.a != this && overlap.b != this) return;
		if(overlaps == null) overlaps = new HashSet<Overlap<Channel>>();
		overlaps.add(overlap);		
	}
	
	public double overlap(Channel other, double pointSize) {
	    if(pixel == null) return 0.0;
	    Pixel p2 = other.getPixel();
	    if(p2 == null) return 0.0;
	    return pixel.overlap(p2, pointSize) * colorOverlap(other);
	}
	
	public double colorOverlap(Channel other) { return 1.0; }
	
	public int getCriticalFlags() {
		return FLAG_DEAD | FLAG_BLIND | FLAG_GAIN;
	}
	
	public final void parseValues(SmartTokenizer tokens) {
		parseValues(tokens, getCriticalFlags());
	}
	
	public void parseValues(SmartTokenizer tokens, int criticalFlags) {
		gain = tokens.nextDouble();
		weight = tokens.nextDouble();

		// Add flags from pixel data file on top of those already specified...
		flag(criticalFlags & flagSpace.parse(tokens.nextToken()));	
	}
	
	public double getResolution() { return instrument.getResolution(); }
	
	public void uniformGains() {
		gain = 1.0; 
		coupling = 1.0;
	}
	
	public Field getFieldFor(String name) {
        Class<?> channelClass = getClass();
        
        try { return channelClass.getField(name); } 
        catch (SecurityException e) {} 
        catch (NoSuchFieldException e) {}
        
        return null;
    }
    


	
	public static final FlagSpace<Integer> flagSpace = new FlagSpace.Integer("channel-flags");
	
	public static final FlagBlock<Integer> hardwareFlags = flagSpace.getFlagBlock(0, 8);
	public final static int FLAG_DEAD = hardwareFlags.next('X', "Dead").value();
	public final static int FLAG_BLIND = hardwareFlags.next('B', "Blind").value();
	public final static int HARDWARE_FLAGS = (int) hardwareFlags.getMask();
	
	public static final FlagBlock<Integer> softwareFlags = flagSpace.getFlagBlock(8, 31);
	public final static int FLAG_DISCARD = softwareFlags.next('d', "Discarded").value();
	public final static int FLAG_GAIN = softwareFlags.next('g', "Gain").value();
	public final static int FLAG_SENSITIVITY = softwareFlags.next('n', "Noisy").value();
	public final static int FLAG_DOF = softwareFlags.next('f', "Degrees-of-freedom.").value();
	public final static int FLAG_SPIKY = softwareFlags.next('s', "Spiky").value();
	public final static int FLAG_DAC_RANGE = softwareFlags.next('r', "Railing/Saturated").value();    
    public final static int FLAG_PHASE_DOF = softwareFlags.next('F', "Insufficient phase degrees-of-freedom").value();

	
	public final static int SOFTWARE_FLAGS = (int) softwareFlags.getMask();
	
	
	public final static double DEFAULT_VARIANCE = 1.0;
	public final static double DEFAULT_WEIGHT = 1.0;

	
}
 