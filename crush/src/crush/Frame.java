/* *****************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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
import java.util.Arrays;
import java.util.stream.IntStream;

import crush.polarization.StokesResponse;
import jnum.CopiableContent;
import jnum.data.Flagging;
import jnum.math.Angle;
import jnum.math.Coordinate2D;
import jnum.math.Inversion;
import jnum.math.Vector2D;
import jnum.projection.Projector2D;
import jnum.util.*;

/**
 * A class that represents a set of timestream measurements obtained at the same time from the {@link Channel}s in an {@link Instrument}.
 * Often referred to as an "exposure" also.
 * 
 * 
 * @see Integration
 * 
 * @author Attila Kovacs
 *
 */
public abstract class Frame implements Serializable, Cloneable, CopiableContent<Frame>, Flagging, Inversion {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6878330196774273680L;
	private Integration<?> integration;
	public int index;
	
	public double MJD;
	
	private int flag = 0;
	public double dof = 1.0;
	public double dependents = 0.0;
	public float relativeWeight = 1.0F;
	
	protected int sign = 1;   // The signal signature, used e.g. for jackknifing
	
	private Angle rotation;
	
	
	public transient float tempC, tempWC, tempWC2; // Some temporary fields to speed up some operations...
	
	public float[] data;
	public byte[] sampleFlag;
	public int[] sourceIndex;

	
	private boolean isValid = false;
	
	/**
	 * Constructs a frame for the specified integration, with initial index of -1. You must still add this frame to the integration
	 * explicitly using <code>Integration.add()</code>, which will set the index appropriately.
	 * 
	 * 
	 * @param parent   The integration to which this frame belongs
	 */
	public Frame(Integration<? extends Frame> parent) {
	    setParent(parent);
	    index = -1;
	}
	
	/**
	 * (Re)assign this frame to the specified integration. This method should only be called by
	 * the constructor and/or {@link Integration#add()} to ensure consistency.
	 * 
	 * 
	 * @param parent   The new parent integration for this frame.
	 */
	void setParent(Integration<? extends Frame> parent) {
	    integration = parent;
	}
	
	/**
	 * Returns the integration to which this frame belongs.
	 * 
	 * @return
	 */
	public Integration<?> getIntegration() { return integration; }
	
	/**
	 * Returns the scan to which this frame belongs.
	 * 
	 * @return The scan in which this frame resides.
	 */
	public Scan<?> getScan() { return getIntegration().getScan(); }
	
	/**
	 * Returns the instrument state for this frame. Same as {@link #getScan()#getInstrument()}.
	 * 
	 * @return
	 */
	public Instrument<?> getInstrument() { return getIntegration().getInstrument(); }
	
	/**
	 * The number of channel data points in this frame.
	 * 
	 * @return
	 */
	public final int size() { return data.length; }
	
    @Override
	public Frame clone() {
		try { return (Frame) super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
    @Override
    public Frame copy() {
        return copy(true);
    }
    
	@Override
    public Frame copy(boolean withContents) {
		Frame copy = clone();
				
		if(data != null) {
			copy.data = new float[data.length];
			if(withContents) System.arraycopy(data, 0, copy.data, 0, data.length);
		}
		
		if(sampleFlag != null) {
			copy.sampleFlag = new byte[sampleFlag.length];
			if(withContents) System.arraycopy(sampleFlag, 0, copy.sampleFlag, 0, sampleFlag.length);
		}
		
		if(sourceIndex != null) {
			copy.sourceIndex = new int[sourceIndex.length];
			if(withContents) System.arraycopy(sourceIndex, 0, copy.sourceIndex, 0, sourceIndex.length);
		}
		
		return copy;
	}
	
	
	protected void create(int size) {
		data = new float[size];
		sampleFlag = new byte[size];
	}
	
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
	
	public final synchronized void addDependents(double dp) {
		dependents += dp;
	}
	
	public final synchronized void removeDependents(double dp) {
		dependents -= dp;
	}

	public double getChannelFrequency(Channel channel) { return channel.getFrequency(); }

	
	public void getChannelStokesResponse(Channel channel, StokesResponse toStokes) {
	    toStokes.setNQUV(1.0, 0.0, 0.0, 0.0);
	}

	public void slimTo(Instrument<?> instrument) {
		float[] reduced = new float[instrument.size()];
		byte[] newSampleFlag = new byte[instrument.size()];
		
		sourceIndex = null; // discard old lookup table if it exists...
		
		for(int k=instrument.size(); --k >= 0; ) {
			final int oldk = instrument.get(k).index;
			reduced[k] = data[oldk];
			newSampleFlag[k] = sampleFlag[oldk];		
		}
			
		data = reduced;
		sampleFlag = newSampleFlag;
	}
	
	public void jackknife() {
		sign = Math.random() < 0.5 ? -1 : 1;
	}
	
	public float getSourceGain(final int mode) throws IllegalArgumentException {
	    return 1.0F;
	}
	
	public boolean validate() {
	    if(isValid) return true;
	    isValid = true;
	    
		if(sampleFlag == null) sampleFlag = new byte[data.length];
		else if(sampleFlag.length != data.length) sampleFlag = new byte[data.length];
		
		return true;
	}
	
	public void cloneReadout(Frame from) {
	    data = from.data;
	}
	
	public void invalidate() {
	    isValid = false;
	}

	
	
	/**
	 * Absolute spherical, incl. chopper, in the native coordinates of the telescope
	 * 
	 * @return
	 */
	public abstract Coordinate2D getNativeCoords();
	
	/**
	 *  Offset from tracking center, incl. chopper, in the native coordinates of the telescope.
	 *  e.g. RAO, DECO for equatorial, or AZO, ELO for horizontal...
	 * @return
	 */
	public abstract Vector2D getNativeOffset();
	

	public abstract void pointingAt(Vector2D offset);
	

    public double getNativeX(final Vector2D fpPosition) { 
        return rotation.cos() * fpPosition.x() - rotation.sin() * fpPosition.y();   
    }
    
    public double getNativeY(final Vector2D fpPosition) {
        return rotation.sin() * fpPosition.x() + rotation.cos() * fpPosition.y();   
    }
	
    public final double getRotation() {
        return rotation == null ? Double.NaN : rotation.value();
    }
    
    public void setRotation(double angle) {
        rotation = new Angle(angle);
    }
    
    public void getFocalPlaneOffset(final Vector2D fpPosition, final Vector2D offset) {
        getNativeOffset(offset);
        final double x = offset.x();
        offset.setX(fpPosition.x() + x * rotation.cos() + offset.y() * rotation.sin());
        offset.setY(fpPosition.y() + offset.y() * rotation.cos() - x * rotation.sin());
    }
    
    public abstract void getNativeOffset(final Vector2D offset);
    
    
    
	public void scale(double factor) {
	    if(factor == 1.0) return;
		if(factor == 0.0) Arrays.fill(data, 0.0F);
		
		final float fScale = (float) factor;
		IntStream.range(0, data.length).parallel().forEach(i -> data[i] *= fScale);
	}
	
	@Override
    public void flip() { 
	    IntStream.range(0, data.length).parallel().forEach(i -> data[i] = -data[i]);
	}
	
   
	public void addDataFrom(final Frame other, final double scaling) {
		if(scaling == 0.0) return;
		
		final float fScale = (float) scaling;
		
		IntStream.range(0, data.length).parallel()
		.peek(i -> data[i] += fScale * other.data[i])
		.forEach(i -> sampleFlag[i] |= other.sampleFlag[i]);
	}
	
	public void project(final Vector2D fpOffset, final Projector2D<?> projector) {
	    if(projector == null) return;
	    projector.setOffset(fpOffset);
	}
	
	
	public abstract Vector2D getPosition(final int type);

	
	public static final FlagBlock<Byte> sampleFlags = new FlagSpace.Byte("sample-flags").getDefaultFlagBlock();
	public static byte SAMPLE_SOURCE_BLANK = sampleFlags.next('B', "Blanked").value();
	public static byte SAMPLE_SPIKE = sampleFlags.next('s', "Spiky").value();
	public static byte SAMPLE_SKIP = sampleFlags.next('$', "Skip").value();
	public static byte SAMPLE_PHOTOMETRY = sampleFlags.next('P', "Photometry").value();
	
	public static final FlagBlock<Integer> frameFlags = new FlagSpace.Integer("frame-flags").getDefaultFlagBlock();
	public static int FLAG_WEIGHT = frameFlags.next('n', "Noise level").value();
	public static int FLAG_SPIKY = frameFlags.next('s', "Spiky").value();
	public static int FLAG_DOF = frameFlags.next('f', "Degrees-of-freedom").value();
	public static int FLAG_JUMP = frameFlags.next('J', "Jump").value();

	public static int SKIP_SOURCE_MODELING = frameFlags.next('$', "Skip Source").value();
	public static int SKIP_MODELING = frameFlags.next('M', "Skip Models").value();
	public static int SKIP_WEIGHTING = frameFlags.next('W', "Skip Weighting").value();
	

	
	public static int BAD_DATA = FLAG_SPIKY | FLAG_JUMP;
	public static int MODELING_FLAGS = SKIP_MODELING | BAD_DATA | FLAG_DOF | FLAG_WEIGHT;
	public static int SOURCE_FLAGS = SKIP_SOURCE_MODELING | MODELING_FLAGS;
	public static int CHANNEL_WEIGHTING_FLAGS = SKIP_WEIGHTING | MODELING_FLAGS;
	public static int TIME_WEIGHTING_FLAGS = SKIP_WEIGHTING | MODELING_FLAGS & ~(FLAG_WEIGHT | FLAG_DOF);
	
	public static int TOTAL_POWER = 0;
}
