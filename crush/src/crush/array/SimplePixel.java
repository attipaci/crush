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

import util.Unit;
import util.Util;
import util.Vector2D;

public class SimplePixel extends Channel implements Pixel {
	public Vector2D position;
	public Vector<? extends SimplePixel> neighbours;
	public boolean independent = false;
	
	public SimplePixel(Instrument<? extends SimplePixel> instrument, int backendIndex) { 
		super(instrument, backendIndex); 
	}
	
	@Override
	public Object clone() {
		SimplePixel clone = (SimplePixel) super.clone();
		if(position != null) clone.position = (Vector2D) position.clone();
		return clone;
	}
	
	
	public final Vector2D getPosition() { return position; }
	
	public final double distanceTo(final Pixel pixel) {
		return position.distanceTo(pixel.getPosition());
	}
	
	public void setIndependent(boolean value) {
		independent = value;
	}
	
	@Override
	// Assume Gaussian response with FWHM = resolution;
	public double overlap(final Channel channel, SourceModel model) {
		if(independent) return 0.0;
		
		double sourceSize = model == null ? instrument.resolution : model.getPointSize();
		
		if(channel instanceof Pixel) {
			double dev = distanceTo((Pixel) channel) * Util.sigmasInFWHM / sourceSize;
			return Math.exp(-0.5 * dev * dev);
		}
		// If other channel is not a pixel assume it is independent...
		return 0.0;
	}
	
	public final Iterator<Channel> iterator() {
		final Channel channel = this;
		
		return new Iterator<Channel>() {
			boolean unused = true;
			
			public final boolean hasNext() { return unused; }

			public Channel next() {
				unused = false;
				return channel;
			}

			public void remove() {}
		};			
	}

	public final int channels() {
		return 1;
	}

	public final Channel getChannel(int i) {
		return i == 0 ? this : null; 
	}
	
	public String getRCPString() {
		Vector2D position = getPosition();
		return getFixedIndex() + 
				"\t" + Util.f3.format(gain * coupling) + 
				"\t" + Util.f3.format(gain) + 
				"\t" + Util.f1.format(position.getX() / Unit.arcsec) + 
				"  " + Util.f1.format(position.getY() / Unit.arcsec);
	}
	
}
