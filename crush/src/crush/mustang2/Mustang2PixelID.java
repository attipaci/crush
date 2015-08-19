/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.mustang2;

import kovacs.math.Vector2D;
import crush.resonators.FrequencyID;

public class Mustang2PixelID extends FrequencyID {
	Vector2D position;
	int flag;
	
	public Mustang2PixelID(int index) {
		super(index);
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
