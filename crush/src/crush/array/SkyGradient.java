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
package crush.array;

import crush.Channel;
import crush.ZeroMeanGains;

public class SkyGradient extends ZeroMeanGains {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5605187091928172211L;
	private boolean horizontal = true;
	
	private SkyGradient(boolean isHorizontal) {
		this.horizontal = isHorizontal;
	}
	
	@Override
	public double getRelativeGain(Channel c) throws Exception {
		SingleColorPixel pixel = (SingleColorPixel) c;
		if(pixel.position == null) return Double.NaN;
		return (horizontal ? pixel.position.x() : pixel.position.y());
	}

	@Override
	public void setRawGain(Channel c, double value) throws Exception {
		throw new UnsupportedOperationException("Cannot change gradient gains.");
	}

	public static class X extends SkyGradient {
		/**
		 * 
		 */
		private static final long serialVersionUID = 627602461300041682L;

		public X() { super(true); }
	}
	
	public static class Y extends SkyGradient {
		/**
		 * 
		 */
		private static final long serialVersionUID = -2925386841948261256L;

		public Y() { super(false); }
	}

}
