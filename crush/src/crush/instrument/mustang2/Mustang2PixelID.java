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

import crush.resonators.FrequencyID;
import jnum.math.Vector2D;

public class Mustang2PixelID extends FrequencyID {
	int readoutIndex;
	Vector2D position;
	double polarizationAngle = Double.NaN;
	int flag;
	
	public Mustang2PixelID(int readoutIndex, int index) {
		super(index);
		this.readoutIndex = readoutIndex;
	}

	public void flag(int pattern) {
		flag |= pattern;
	}
	
	public void unflag(int pattern) {
		flag &= ~pattern;
	}
	
	public boolean isFlagged(int pattern) {
		return (flag & pattern) != 0;
	}
	
	public boolean isUnflagged(int pattern) {
		return (flag & pattern) == 0;
	}
	
	public static int FLAG_BLIND = 1;
	public static int FLAG_UNUSED = 2;
	
	

}
