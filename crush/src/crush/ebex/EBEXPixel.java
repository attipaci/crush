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
// Copyright (c) 2010 Attila Kovacs 

package crush.ebex;

import crush.Channel;
import crush.array.SimplePixel;
import util.dirfile.*;

public class EBEXPixel extends SimplePixel {
	DataStore<Integer> store;
	
	double frequency;
	
	int board, wire, pin;
	int wafer, row, col;
	int squidBoard, squid;
	int umnPad, ucbPad;
	
	String lcPad, comment;
	double fMUX, fMUXexp, warmR, C; 

	double boardGain = 1.0, wireGain = 1.0, squidGroupGain = 1.0, squidGain = 1.0;
	
	
	public EBEXPixel(int backendIndex) {
		super(backendIndex);
	}
	
	@Override
	public final double overlap(Channel channel) {
		return channel == this ? 1.0 : 0.0;
	}

	public static int getBackendIndex(int board, int wire, int channel) {
		return channel + wireChannels * (wire + boardWires * board); 
	}
	
	static int readoutBoards = 12, boardWires = 4, wireChannels = 8;

	public final static int FLAG_RESISTOR = 1 << nextHardwareFlag++;
	public final static int FLAG_ECCOSORB = 1 << nextHardwareFlag++;
	

	public final static int FLAG_BOARD = 1 << nextSoftwareFlag++;
	public final static int FLAG_CABLE = 1 << nextSoftwareFlag++;
	public final static int FLAG_SQUIDBOARD = 1 << nextSoftwareFlag++;
	public final static int FLAG_SQUID = 1 << nextSoftwareFlag++;
	
}
