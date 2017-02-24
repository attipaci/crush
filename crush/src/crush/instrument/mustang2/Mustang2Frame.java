/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.mustang2;

import crush.telescope.HorizontalFrame;

public class Mustang2Frame extends HorizontalFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3339115022179473683L;

	public Mustang2Frame(Mustang2Scan parent) {
		super(parent);
	}

	public void parseData(float[] values, int from, int channels) {
		create(channels);
		System.arraycopy(values, from, data, 0, channels);
	}
	
}
