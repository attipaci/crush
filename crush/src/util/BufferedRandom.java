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
package util;

import java.util.Random;


public class BufferedRandom extends Random {
	private int[][] buffer;
	
	public BufferedRandom() {
		this(100);
	}
	
	public BufferedRandom(int size) {
		buffer = new int[32][size];
		for(int bits=0; bits<32; bits++) for(int i=0; i<size; i++) buffer[bits][i] = super.next(bits);	
	}
	
	public void seedNow() {
		setSeed(System.currentTimeMillis());
	}
	
	@Override
	public int next(int bits) {
		final int i = (int) (buffer.length * Math.random());
		final int value = buffer[++bits][i];
		buffer[bits][i] = super.next(bits);
		return value;
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -8696559579537431957L;

}
