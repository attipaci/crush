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
import java.util.Arrays;

import crush.polarization.StokesResponse;
import jnum.Flagging;
import jnum.astro.AstroProjector;
import jnum.astro.EquatorialCoordinates;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.util.*;


public abstract class Frame implements Serializable, Cloneable, Flagging {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6878330196774273680L;
	public Scan<?, ?> scan;
	public int index;
	
	public EquatorialCoordinates equatorial;
	public Vector2D chopperPosition = new Vector2D(); // in the native coordinate system, standard direction (e.g. -RAO, DECO)
	
	public double MJD, LST;
	public double sinA = Double.NaN, cosA = Double.NaN; // These are the projected array rotation...

	private int flag = 0;
	public double dof = 1.0;
	public double dependents = 0.0;
	public float relativeWeight = 1.0F;
	
	public int sign = 1;
	private float transmission = 1.0F;
	
	// Some temporary fields to speed up some operations...
	public transient float tempC, tempWC, tempWC2;
	
	public float[] data;
	public byte[] sampleFlag;
	public int[] sourceIndex;
	
	public boolean hasTelescopeInfo = true;
	
	private boolean isValid = false;
	
	public Frame(Scan<?, ?> parent) { 
		scan = parent; 
		index = parent.size();
	}
	
	
	public final int size() { return data.length; }
	
	@Override
	public Frame clone() {
		try { return (Frame) super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public Frame copy(boolean withContents) {
		Frame copy = clone();
		
		if(equatorial != null) copy.equatorial = (EquatorialCoordinates) equatorial.copy();
		if(chopperPosition != null) copy.chopperPosition = chopperPosition.copy();
		
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

	/**
     * @param channel  
     */
	public void getChannelStokesResponse(Channel channel, StokesResponse toStokes) {
	    toStokes.setNQUV(1.0, 0.0, 0.0, 0.0);
	}
	
	public float getTransmission() { return transmission; }
	
	protected void setTransmission(float value) { transmission = value; }
	
	protected void setTransmission(double value) { setTransmission((float) value); }
	
	public float getTransmissionCorrection(Signal atm, float signal2emissivity) {
		return atm.valueAt(this) * signal2emissivity;
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
	    if(mode == TOTAL_POWER) return sign * getTransmission();
        throw new IllegalArgumentException(getClass().getSimpleName() + " does not define signal mode " + mode);
	}
	
	public boolean validate() {
	    if(isValid) return true;
	    isValid = true;
	    
		if(sampleFlag == null) sampleFlag = new byte[data.length];
		else if(sampleFlag.length != data.length) sampleFlag = new byte[data.length];
		
		if(!hasTelescopeInfo) return true;
		
		// Set the platform rotation, unless the rotation was explicitly set already
		if(Double.isNaN(cosA) || Double.isNaN(sinA)) {
			switch(scan.instrument.mount) {
			case CASSEGRAIN: 
			case GREGORIAN: 
			case NASMYTH_COROTATING:
			case PRIME_FOCUS: sinA = 0.0; cosA = 1.0; break;
			case LEFT_NASMYTH: {
				SphericalCoordinates nativeCoords = getNativeCoords();
				sinA = -nativeCoords.sinLat(); cosA = nativeCoords.cosLat(); break;
			}
			case RIGHT_NASMYTH: {
				SphericalCoordinates nativeCoords = getNativeCoords();
				sinA = nativeCoords.sinLat(); cosA = nativeCoords.cosLat(); break;
			}
			default: sinA = 0.0; cosA = 1.0;
			}		
		}
		
		return true;
	}
	
	public void cloneReadout(Frame from) {
	    data = from.data;
	}
	
	public void invalidate() {
	    isValid = false;
	}

	public void getEquatorial(final Vector2D position, final EquatorialCoordinates coords) {
		coords.setNativeLongitude(equatorial.x() + getNativeX(position) / scan.equatorial.cosLat());
		coords.setNativeLatitude(equatorial.y() + getNativeY(position));
	}
	
	public void getEquatorialNativeOffset(final Vector2D position, final Vector2D offset) {
		getEquatorialNativeOffset(offset);
		offset.setX(offset.x() + getNativeX(position));
		offset.setY(offset.y() + getNativeY(position));
	}
		
	public void getNativeOffset(final Vector2D position, final Vector2D offset) {
		getEquatorialNativeOffset(position, offset);
	}
	
	public void getNativeOffset(final Vector2D offset) {
		getEquatorialNativeOffset(offset);
	}
	
	public void getFocalPlaneOffset(final Vector2D position, final Vector2D offset) {
		getNativeOffset(offset);
		final double x = offset.x();
		offset.setX(position.x() + x * cosA + offset.y() * sinA);
		offset.setY(position.y() + offset.y() * cosA - x * sinA);
	}
	
	public void getEquatorialNativeOffset(Vector2D offset) {
		equatorial.getNativeOffsetFrom(scan.equatorial, offset);		
	}
	
	public Vector2D getEquatorialNativeOffset() {
		Vector2D offset = new Vector2D();
		getEquatorialNativeOffset(offset);
		return offset;
	}
	
	public void getApparentEquatorial(EquatorialCoordinates apparent) {
		apparent.copy(equatorial);
		scan.toApparent.precess(apparent);
	}
	
	// Absolute spherical, incl. chopper, in the native coordinates of the telescope
	public abstract SphericalCoordinates getNativeCoords();
	
	// Offset from tracking center, incl. chopper, in the native coordinates of the telescope
	// e.g. RAO, DECO for equatorial, or AZO, ELO for horizontal...
	public abstract Vector2D getNativeOffset();
	
	public void pointingAt(Vector2D offset) {
		Vector2D nativeOffset = getNativeOffset();
		if(nativeOffset != null) nativeOffset.subtract(offset);
		SphericalCoordinates coords = getNativeCoords();
		if(coords != null) coords.subtractOffset(offset);
	}

	public void scale(double factor) {
		if(factor == 0.0) Arrays.fill(data, 0.0F);
		else {
			final float fScale = (float) factor;
			for(int i=data.length; --i >= 0; ) data[i] *= fScale;	
		}
	}
	
	public void invert() { scale(-1.0); }
	

	public double getRotation() {
		return Math.atan2(sinA, cosA);
	}
	
	public void setRotation(double angle) {
		sinA = Math.sin(angle);
		cosA = Math.cos(angle);
	}
	
	
	public double getNativeX(final Vector2D fpPosition) {
		return cosA * fpPosition.x() - sinA * fpPosition.y();	
	}
	
	public double getNativeY(final Vector2D fpPosition) {
		return sinA * fpPosition.x() + cosA * fpPosition.y();	
	}
	
	public void addDataFrom(final Frame other, final double scaling) {
		if(scaling == 0.0) return;
		final float fScale = (float) scaling;
		
		for(int i=data.length; --i >=0; ) {
			data[i] += fScale * other.data[i];
			sampleFlag[i] |= other.sampleFlag[i];
		}
	}
	
	public void project(final Vector2D fpOffset, final AstroProjector projector) {
		if(projector.isFocalPlane()) {
			projector.setReferenceCoords();
			// Deproject SFL focal plane offsets...
			getFocalPlaneOffset(fpOffset, projector.offset);
			projector.getCoordinates().addNativeOffset(projector.offset);
			projector.project();
		}
		else if(scan.isNonSidereal) {
			projector.setReferenceCoords();
			// Deproject SFL native offsets...
			getEquatorialNativeOffset(fpOffset, projector.offset);
			projector.getEquatorial().addNativeOffset(projector.offset);
			projector.projectFromEquatorial();
		}
		else {
			getEquatorial(fpOffset, projector.getEquatorial());		
			projector.projectFromEquatorial();
		}
	
	}	
	
	
	// Native offsets are in standard directions (e.g. -RA, DEC)
	public void nativeToNativeEquatorial(Vector2D offset) {}
	
	public final void nativeToEquatorial(Vector2D offset) {
		nativeToNativeEquatorial(offset);
		offset.scaleX(-1.0);
	}
	
	// Native offsets are in standard directions (e.g. -RA, DEC)
	public void nativeEquatorialToNative(Vector2D offset) {}
	
	public final void equatorialToNative(Vector2D offset) {
		offset.scaleX(-1.0);
		nativeEquatorialToNative(offset);
	}
	
	public void nativeToEquatorial(SphericalCoordinates coords, EquatorialCoordinates equatorial) {
		equatorial.copy(coords);
	}
	
	public void equatorialToNative(EquatorialCoordinates equatorial, SphericalCoordinates coords) {
		coords.copy(equatorial);
	}
	
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
	
	public static int CHOP_LEFT = frameFlags.next('L', "Chop Left").value();
	public static int CHOP_RIGHT = frameFlags.next('R', "Chop Right").value();
	public static int CHOP_TRANSIT = frameFlags.next('T', "Chop Transit").value();
	public static int CHOP_FLAGS = CHOP_LEFT | CHOP_RIGHT | CHOP_TRANSIT;
	
	public static int NOD_LEFT = frameFlags.next('<', "Nod Left").value();
	public static int NOD_RIGHT = frameFlags.next('>', "Nod Right").value();
	
	public static int BAD_DATA = FLAG_SPIKY | FLAG_JUMP;
	public static int MODELING_FLAGS = SKIP_MODELING | BAD_DATA | FLAG_DOF | FLAG_WEIGHT;
	public static int SOURCE_FLAGS = SKIP_SOURCE_MODELING | MODELING_FLAGS;
	public static int CHANNEL_WEIGHTING_FLAGS = SKIP_WEIGHTING | MODELING_FLAGS;
	public static int TIME_WEIGHTING_FLAGS = SKIP_WEIGHTING | MODELING_FLAGS & ~(FLAG_WEIGHT | FLAG_DOF);
	
	public static int TOTAL_POWER = 0;
}
