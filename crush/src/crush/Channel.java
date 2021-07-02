/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/

package crush;


import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;

import crush.instrument.Overlap;
import jnum.Copiable;
import jnum.Util;
import jnum.data.Flagging;
import jnum.text.SmartTokenizer;
import jnum.util.FlagSpace;
import jnum.util.FlagBlock;


/**
 * A class that represents a detector channel in an {@link Instrument}, and its properties for the given <code>Instrument</code> state. 
 * Channels can be uniquely identified either by a fixed index (starting from zero, and less than {@link Instrument#storeChannels}), or optionally also by a String ID.
 * 
 * 
 * Channels of an <code>Instrument</code> can be grouped (see {@link ChannelGroup}) by their common properties (e.g. TES channels read out
 * through the same SQUID mux can constitute such a group). {@link Pixel}s are also such a group of channels, which share the same
 * physical location, and coupling. Thus, in monochromatic continuum cameras (e.g. SHARC-2, GISMO, or HAWC+) each pixel has exactly
 * one channel, whereas spectroscopic or multicolor, or multipol instruments can have several or many channels per <code>Pixel</code>.
 * The way channels are assigned to pixels, is managed by the {@link PixelLayout} class.
 * 
 * 
 * @author Attila Kovacs
 *
 *
 */
public abstract class Channel implements Serializable, Cloneable, Comparable<Channel>, Flagging, Copiable<Channel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5541239418892633654L;

	Instrument<?> instrument;
    int index;                                     // This pixel's index in the parent instrument.
	
	private Pixel pixel;	
	private transient Collection<Overlap<Channel>> overlaps;

	transient float temp, tempG, tempWG, tempWG2;  // these are used as associated temporary variables during reduction.
	
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
		this.fixedIndex = fixedIndex;
	}
	
	@Override
	public Channel clone() {
		try { return (Channel) super.clone(); }
		catch(CloneNotSupportedException e) { return null; }		
	}
	
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
	
	/**
	 * Returns the parent instrument's configuration/operating state to which this channel state belongs.
	 * 
	 * @return
	 */
	public Instrument<? extends Channel> getInstrument() { return instrument; }

		
	@Override
	public final boolean isFlagged(final long pattern) {
		return (flag & pattern) != 0;
	}
	
	@Override
	public final boolean isUnflagged(final long pattern) {
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
	public final void flag(final long pattern) {
		flag |= pattern;
	}
	
	@Override
	public final void unflag(final long pattern) {
		flag &= ~pattern;
	}

	@Override
	public final void unflag() {
		flag = 0;
	}
	
	@Override
    public final long getFlags() { return flag; }
	
	public double getFiltering(Integration<?> integration) {
		final double nDrifts = Math.ceil(integration.getDuration() / filterTimeScale);
		return directFiltering * (1.0 - nDrifts / integration.size());
	}
	
	/**
	 * Add the fractional dependents, i.e. the relative weight that this channel contributes to the estimate of some parameter(s).
	 * This is also equal, by definition, to the loss of degrees of freedom in that channel to to its contribution to the modeling.
	 *
	 * @param dp       The fractional dependence of some parameter(s) on this channel's contribution to their estimate.
	 * 
	 * @see Dependents
	 */
	public final synchronized void addDependents(double dp) {
		dependents += dp;
	}

	/**
     * Removes the fractional dependents, i.e. the relative weight that this channel contributes to the estimate of some parameter(s).
     * This is also equal, by definition, to the loss of degrees of freedom in that channel to to its contribution to the modeling.
     *
     * @param dp       The fractional dependence of some parameter(s) on this channel's contribution to their estimate.
     * 
     * @see Dependents
     */
	public final synchronized void removeDependents(double dp) {
		dependents -= dp;
	}
	
	/**
	 * Returns the current index of this channel in the parent {@link Instrument}.
	 * 
	 * @return     the current index of this channel in the parent instrument.
	 */
	public final int getIndex() { return index; }
	
	/**
     * Returns the universal 0-based index of this channel. The universal index of a channel ought to be smaller than the
     * the total number of channels possible in the instrument.
     * 
     * @return     the current index of this channel in the parent instrument.
     */
	public final int getFixedIndex() { return fixedIndex; }
	
	/**
	 * Returns the standard string ID of this channel. If the channel was constructed without an explicit string ID, 
	 * then it will be the automatic string ID that is equals to the decimal represenation of 1+{@link #getFixedIndex()}. I.e.
	 * the implicit string ID of channels is 1-based, so that channel with fixed index 0 will have an implicit ID of "1".
	 * 
	 * @return     The explicit or implicit string ID of this channel.
	 */
	public String getID() { return Integer.toString(getFixedIndex() + 1); }
	

	/**
	 * Returns the gain applied between the detector stage and the readout stage, if appropriate. By default, it will
	 * return {@link Instrument.gain}.
	 * 
	 * @return
	 */
	public double getReadoutGain() {
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
	
	/**
	 * Returns the center frequency (in standard SI units, i.e. Hz) of this channel.
	 * 
	 * @return     the center frequency (Hz) of this channel.
	 */
	public double getFrequency() { return instrument.getFrequency(); }
	
	/**
	 * Returns the pixel to which this channel belongs, if it has been assigned as such by a {@link PixelLayout}, or <code>null</code>.
	 * 
	 * @return     The pixel to which this channel is belongs, or <code>null</code> if this channel does not belong to a pixel.
	 */
	public final Pixel getPixel() { return pixel; }
	
	
	/** 
	 * Sets the pixel to which this channel belongs. Normally only {@link Pixel#add(Channel)} or {@link Pixel#add(int, Channel)} 
	 * should call this to ensure that the two-way references between a pixel and its channels are consistent.
	 * 
	 * @param pixel    The pixel to which this channel belongs.
	 */
	void setPixel(Pixel pixel) { this.pixel = pixel; }
	
	
	public Collection<Overlap<Channel>> getOverlaps() { return overlaps; }
	
	public void setOverlaps(Collection<Overlap<Channel>> overlaps) { this.overlaps = overlaps; }
	
	public void clearOverlaps() {
		if(overlaps != null) overlaps.clear();
	}
	
	public synchronized void addOverlap(Overlap<Channel> overlap) {
		if(overlap.a != this && overlap.b != this) return;
		if(overlaps == null) overlaps = new HashSet<>();
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
	
	/**
	 * Returns the spacial resolution of this pixel projected to the image plane.
	 * 
	 * @return     The underlying spacial resolution for this channel in the image plane. It is not to be confused with a 
	 *             pixel's size. Rather it is the diffraction limited beam-size for this channel.
	 */
	public double getResolution() { return instrument.getResolution(); }
	
	/**
	 * Reset all relative gains (such as extended gain and point source coupling) of this pixel to a default 1.0 value.
	 * 
	 */
	public void uniformGains() {
		gain = 1.0; 
		coupling = 1.0;
	}
	
	/**
	 * Returns the field in this channel that matches the name.
	 * 
	 * @param name     The name of a field in this channel.
	 * @return         The field object corresponding to the name, or <code>null</code> if this channel has no
	 *                 accessible field by that name to the caller.
	 */
	public Field getFieldFor(String name) {
        Class<?> channelClass = getClass();
        
        try { return channelClass.getField(name); } 
        catch (SecurityException e) {} 
        catch (NoSuchFieldException e) {}
        
        return null;
    }
    


	
	public static final FlagSpace<Integer> flagSpace = new FlagSpace.Integer("channel-flags");
	
	public static final FlagBlock<Integer> hardwareFlags = flagSpace.getFlagBlock(0, 8);
	public static final int FLAG_DEAD = hardwareFlags.next('X', "Dead").value();
	public static final int FLAG_BLIND = hardwareFlags.next('B', "Blind").value();
	public static final int HARDWARE_FLAGS = (int) hardwareFlags.getMask();
	
	public static final FlagBlock<Integer> softwareFlags = flagSpace.getFlagBlock(8, 31);
	public static final int FLAG_DISCARD = softwareFlags.next('d', "Discarded").value();
	public static final int FLAG_GAIN = softwareFlags.next('g', "Gain").value();
	public static final int FLAG_SENSITIVITY = softwareFlags.next('n', "Noisy").value();
	public static final int FLAG_DOF = softwareFlags.next('f', "Degrees-of-freedom.").value();
	public static final int FLAG_SPIKY = softwareFlags.next('s', "Spiky").value();
	public static final int FLAG_DAC_RANGE = softwareFlags.next('r', "Railing/Saturated").value();    
    public static final int FLAG_PHASE_DOF = softwareFlags.next('F', "Insufficient phase degrees-of-freedom").value();

	
	public static final int SOFTWARE_FLAGS = (int) softwareFlags.getMask();
	
	
	public static final double DEFAULT_VARIANCE = 1.0;
	public static final double DEFAULT_WEIGHT = 1.0;

	
}
 