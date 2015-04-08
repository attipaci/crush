/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.array;


import kovacs.math.Vector2D;
import kovacs.util.*;
import crush.*;

public abstract class RotatingArray<PixelType extends Pixel, ChannelType extends Channel> extends Array<PixelType, ChannelType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2271933035438315377L;
	
	public RotatingArray(String name, InstrumentLayout<? super ChannelType> layout) {
		super(name, layout);
	}
	
	public RotatingArray(String name, InstrumentLayout<? super ChannelType> layout, int size) {
		super(name, layout, size);
	}

	public abstract Vector2D getPointingCenterOffset();
	
	public double getRotation() {
		if(hasOption("rotation")) return option("rotation").getDouble() * Unit.deg;
		else return Double.NaN;
	}

	@Override
	public void loadChannelData() {
		appliedRotation = 0.0;
		super.loadChannelData();
		rotate(getRotation());
	}
	
	// Returns the offset of the pointing center from the the rotation center for a given rotation...
	private Vector2D getPointingOffset(double rotationAngle) {
		Vector2D offset = new Vector2D();
		
		final double sinA = Math.sin(rotationAngle);
		final double cosA = Math.cos(rotationAngle);
		
		if(mount == Mount.CASSEGRAIN) {
			Vector2D dP = getPointingCenterOffset();	
			offset.setX(dP.x() * (1.0 - cosA) + dP.y() * sinA);
			offset.setY(dP.x() * sinA + dP.y() * (1.0 - cosA));
		}
		return offset;
	}
	
	
	// How about different pointing and rotation centers?...
	// If assuming that pointed at rotation a0 and observing at a
	// then the pointing center will rotate by (a-a0) on the array rel. to the rotation
	// center... (dP is the pointing rel. to rotation vector)
	// i.e. the effective array offsets change by:
	//	dP - dP.rotate(a-a0)

	// For Cassegrain assume pointing at zero rotation (a0 = 0.0)
	// For Nasmyth assume pointing at same elevation (a = a0)
	protected double appliedRotation = 0.0;
	
	protected void rotate(double angle) {
		System.err.println(" Applying rotation at " + Util.f1.format(angle / Unit.deg) + " deg.");
		
		// Undo the prior rotation...
		Vector2D priorOffset = getPointingOffset(appliedRotation);
		Vector2D newOffset = getPointingOffset(angle);
		
		for(Pixel pixel : getPixels()) if(pixel.getPosition() != null) {
			Vector2D position = pixel.getPosition();
			
			// Center positions on the rotation center...
			position.subtract(priorOffset);
			// Do the rotation...
			position.rotate(angle - appliedRotation);
			// Re-center on the pointing center...
			position.add(newOffset);
		}
		
		appliedRotation = angle;
	}
	
}
