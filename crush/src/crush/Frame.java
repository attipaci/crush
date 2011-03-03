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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import util.*;
import util.astro.CelestialProjector;
import util.astro.EquatorialCoordinates;
import util.astro.HorizontalCoordinates;


public abstract class Frame implements Cloneable {
	public Scan<?, ?> scan;
	public int index;
	
	public EquatorialCoordinates equatorial;
	public Vector2D chopperPosition = new Vector2D(); // in the native coordinate system, standard direction (e.g. -RAO, DECO)
	
	public double MJD, LST;
	public double sinA = Double.NaN, cosA = Double.NaN; // These are the projected array rotation...
	
	public double zenithTau = 0.0;
	public float transmission = 1.0F;
	
	public int flag = 0;
	public double dof = 1.0;
	public double dependents = 0.0;
	public float relativeWeight = 1.0F;
	public float tempC, tempWC, tempWC2;
	
	public float[] data;
	public byte[] sampleFlag;
	public int[] sourceIndex;
	
	public Frame(Scan<?, ?> parent) { 
		scan = parent; 
		index = parent.size();
	}
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
	public int hashCode() {
		int hash = HashCode.get(MJD);
		if(data != null) hash ^= HashCode.sampleFrom(data);
		return hash;
	}
	
	public void setSize(int size) {
		data = new float[size];
		sampleFlag = new byte[size];
	}
	
	public final boolean isFlagged(final int pattern) {
		return (flag & pattern) != 0;
	}
	
	public final boolean isUnflagged(final int pattern) {
		return (flag & pattern) == 0;
	}
	
	public final boolean isFlagged() {
		return flag != 0;
	}
		
	public final boolean isUnflagged() {
		return flag == 0;
	}
	
	public final void flag(final int pattern) {
		flag |= pattern;
	}
	
	public final void unflag(final int pattern) {
		flag &= ~pattern;
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
	
		
	public double getSourceGain(int mode) throws IllegalArgumentException {
		if(mode == TOTAL_POWER) return transmission;
		else throw new IllegalArgumentException(getClass().getSimpleName() + " does not define signal mode " + mode);
	}

	public void validate() {
		if(sampleFlag == null) sampleFlag = new byte[data.length];
		else if(sampleFlag.length != data.length) sampleFlag = new byte[data.length];
		
		switch(scan.instrument.mount) {
		case CASSEGRAIN: sinA = 0.0; cosA = 1.0; break;
		case LEFT_NASMYTH: {
			SphericalCoordinates nativeCoords = getNativeCoords();
			sinA = -nativeCoords.sinLat; cosA = nativeCoords.cosLat; break;
		}
		case RIGHT_NASMYTH: {
			SphericalCoordinates nativeCoords = getNativeCoords();
			sinA = nativeCoords.sinLat; cosA = nativeCoords.cosLat; break;
		}
		default: sinA = 0.0; cosA = 1.0;
		}		
	}
	
	public abstract void getEquatorial(final Vector2D position, final EquatorialCoordinates coords);
	
	public abstract void getHorizontal(final Vector2D position, final HorizontalCoordinates coords);
	
	public abstract void getHorizontalOffset(final Vector2D position, final Vector2D offset);
	
	public abstract void getEquatorialOffset(final Vector2D position, final Vector2D offset);
	
	public void getEquatorialOffset(Vector2D offset) {
		equatorial.getOffsetFrom(scan.equatorial, offset);		
	}
	
	public Vector2D getEquatorialOffset() {
		Vector2D offset = new Vector2D();
		getEquatorialOffset(offset);
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
		for(int i=data.length; --i >= 0; ) data[i] *= factor;	
	}
	
	public void invert() { scale(-1.0); }
	

	public double getRotation() {
		return Math.atan2(sinA, cosA);
	}
	
	public void setRotation(double angle) {
		sinA = Math.sin(angle);
		cosA = Math.cos(angle);
	}
	
	public final double getX(final Vector2D position) {
		return cosA * position.x - sinA * position.y;	
	}
	
	public final double getY(final Vector2D position) {
		return cosA * position.y + sinA * position.x;	
	}
	
	public final void project(final Vector2D position, final CelestialProjector projector) {
		if(projector.isHorizontal()) getHorizontalOffset(position, projector.offset);
		else {
			getEquatorial(position, projector.equatorial);
			projector.project();
		}
	}	
	
	// Native offsets are in standard directions (e.g. -RA, DEC)
	public abstract void nativeToEquatorial(Vector2D offset);
	
	// Native offsets are in standard directions (e.g. -RA, DEC)
	public abstract void equatorialToNative(Vector2D offset);
	
	public abstract void nativeToEquatorial(SphericalCoordinates coords, EquatorialCoordinates equatorial);
	
	public abstract void equatorialToNative(EquatorialCoordinates equatorial, SphericalCoordinates coords);
	
	public static int nextSampleFlag = 0;
	public static byte SAMPLE_SOURCE_BLANK = (byte) (1 << nextSampleFlag++);
	public static byte SAMPLE_SPIKE = (byte) (1 << nextSampleFlag++);
	public static byte SAMPLE_SPIKY_NEIGHBOUR = (byte) (1 << nextSampleFlag++);
	public static byte SAMPLE_SPIKY_FEATURE = (byte) (1 << nextSampleFlag++);
	public static byte SAMPLE_SPIKE_FLAGS = (byte) (SAMPLE_SPIKE | SAMPLE_SPIKY_NEIGHBOUR | SAMPLE_SPIKY_FEATURE);
	public static byte SAMPLE_SKIP = (byte) (1 << nextSampleFlag++);
	public static byte SAMPLE_PHOTOMETRY = (byte) (1 << nextSampleFlag++);
	
	public static int nextFlag = 0;
	public static int FLAG_WEIGHT = 1 << nextFlag++;
	public static int FLAG_SPIKY = 1 << nextFlag++;
	public static int FLAG_DOF = 1 << nextFlag++;
	

	public static int SKIP_SOURCE = 1 << nextFlag++;
	public static int SKIP_MODELS = 1 << nextFlag++;
	public static int SKIP_WEIGHTING = 1 << nextFlag++;
	//public static int SKIP_SYNCHING = 1 << nextFlag++;
	
	
	public static int CHOP_LEFT = 1 << nextFlag++;
	public static int CHOP_RIGHT = 1 << nextFlag++;
	public static int CHOP_TRANSIT = 1 << nextFlag++;
	public static int CHOP_FLAGS = CHOP_LEFT | CHOP_RIGHT | CHOP_TRANSIT;
	
	public static int NOD_LEFT = 1 << nextFlag++;
	public static int NOD_RIGHT = 1 << nextFlag++;

	public static int BAD_DATA = FLAG_SPIKY;
	public static int MODELING_FLAGS = SKIP_MODELS | BAD_DATA | FLAG_DOF | FLAG_WEIGHT;
	public static int SOURCE_FLAGS = SKIP_SOURCE | MODELING_FLAGS;
	public static int WEIGHTING_FLAGS = SKIP_WEIGHTING | MODELING_FLAGS;
	
	public static int TOTAL_POWER = 0;
}
