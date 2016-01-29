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

import java.util.*;

import crush.*;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;


public abstract class SingleColorPixel extends Channel implements Pixel {
	public Vector2D position;
	public Vector<? extends SingleColorPixel> neighbours;
	public boolean isIndependent = false;
	
	public SingleColorPixel(Instrument<? extends SingleColorPixel> instrument, int backendIndex) { 
		super(instrument, backendIndex); 
	}
	
	@Override
	public Object clone() {
		SingleColorPixel clone = (SingleColorPixel) super.clone();
		if(position != null) clone.position = (Vector2D) position.clone();
		return clone;
	}
	
	
	@Override
	public final Vector2D getPosition() { return position; }
	
	@Override
	public final double distanceTo(final Pixel pixel) {	
		Vector2D position = getPosition();
		if(position == null) return Double.NaN;
		
		Vector2D other = pixel.getPosition();
		if(other == null) return Double.NaN;
		
		return getPosition().distanceTo(pixel.getPosition());
	}
	
	@Override
	public void setIndependent(boolean value) {
		isIndependent = value;
	}
	
	@Override
	// Assume Gaussian response with FWHM = resolution;
	public double overlap(final Channel channel, double pointSize) {
		if(isIndependent) return 0.0;
		
		final double isigma = Constant.sigmasInFWHM / pointSize;
		
		if(channel instanceof Pixel) {
			final double dev = distanceTo((Pixel) channel) * isigma;
			if(!Double.isNaN(dev)) return Math.exp(-0.5 * dev * dev);
		}
		// If other channel is not a pixel assume it is independent...
		return 0.0;
	}
	
	@Override
	public final Iterator<Channel> iterator() {
		final Channel channel = this;
		
		return new Iterator<Channel>() {
			boolean unused = true;
			
			@Override
			public final boolean hasNext() { return unused; }

			@Override
			public Channel next() {
				unused = false;
				return channel;
			}

			@Override
			public void remove() {}
		};			
	}

	@Override
	public final int channels() {
		return 1;
	}

	@Override
	public final Channel getChannel(int i) {
		return i == 0 ? this : null; 
	}
	
	@Override
	public String getRCPString() {
		Vector2D position = getPosition();
		return getFixedIndex() + 
				"\t" + Util.f3.format(gain * coupling) + 
				"\t" + Util.f3.format(gain) + 
				"\t" + Util.f1.format(position.x() / Unit.arcsec) + 
				"  " + Util.f1.format(position.y() / Unit.arcsec);
	}
	
}
