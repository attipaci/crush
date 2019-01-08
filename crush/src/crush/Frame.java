/*******************************************************************************
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/

package crush;

import java.io.Serializable;
import java.util.Arrays;

import crush.polarization.StokesResponse;
import jnum.Flagging;
import jnum.math.Angle;
import jnum.math.Coordinate2D;
import jnum.math.Vector2D;
import jnum.projection.Projector2D;
import jnum.util.*;


public abstract class Frame implements Serializable, Cloneable, Flagging {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6878330196774273680L;
	private Scan<?> scan;
	public int index;
	
	public double MJD;
	
	private int flag = 0;
	public double dof = 1.0;
	public double dependents = 0.0;
	public float relativeWeight = 1.0F;
	
	public int sign = 1;
	
	private Angle rotation;

	
	// Some temporary fields to speed up some operations...
	public transient float tempC, tempWC, tempWC2;
	
	public float[] data;
	public byte[] sampleFlag;
	public int[] sourceIndex;

	
	private boolean isValid = false;
	
	public Frame(Scan<?> parent) { 
		scan = parent; 
		index = parent.size();
	}
	
	public Scan<?> getScan() { return scan; }
	
	public Instrument<?> getInstrument() { return getScan().getInstrument(); }
	
	public final int size() { return data.length; }
	
    @Override
	public Frame clone() {
		try { return (Frame) super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
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
		if(factor == 0.0) Arrays.fill(data, 0.0F);
		else {
			final float fScale = (float) factor;
			for(int i=data.length; --i >= 0; ) data[i] *= fScale;	
		}
	}
	
	public void invert() { scale(-1.0); }
	
   
	public void addDataFrom(final Frame other, final double scaling) {
		if(scaling == 0.0) return;
		final float fScale = (float) scaling;
		
		for(int i=data.length; --i >=0; ) {
			data[i] += fScale * other.data[i];
			sampleFlag[i] |= other.sampleFlag[i];
		}
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
