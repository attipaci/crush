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

package crush.instrument.sharc2;

import crush.Channel;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;


public class Sharc2Pixel extends Channel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1902854577318314033L;
	public int row = -1, col = -1, block = 0;
	public double rowGain = 1.0, muxGain;
	
	double biasV;
	short DAC;
	double G0 = 0.0, V0 = Double.POSITIVE_INFINITY, T0 = Double.POSITIVE_INFINITY; // Gain non-linearity constants...
	
	
	Sharc2Pixel(Sharc2 array, int zeroIndex) {
		super(array, zeroIndex);
		row = zeroIndex / 32;
		col = zeroIndex % 32;
		muxGain = col < 16 ? 1.0 : -1.0;
	}
	
	@Override
    public Sharc2 getInstrument() { return (Sharc2) super.getInstrument(); }
	
	@Override
	public double getReadoutGain() {
		return getInstrument().rowGain[row];
	}


		
	@Override
	public void parseValues(SmartTokenizer tokens, int criticalFlags) {
		super.parseValues(tokens, criticalFlags);
		coupling = tokens.nextDouble();
		rowGain = tokens.nextDouble();
	}
	
	@Override
	public String toString() {
		return super.toString() + "\t" + Util.f3.format(coupling) + "\t" + Util.f3.format(rowGain);
	}
 

	@Override
	public void uniformGains() {
		super.uniformGains();
		muxGain = 1.0;
		rowGain = 1.0;
	}
	
	
	public static Vector2D defaultSize = new Vector2D(4.89 * Unit.arcsec, 4.82 * Unit.arcsec);

	public final static int FLAG_13HZ = softwareFlags.next('^', "13Hz pickup").value();
	public final static int FLAG_ROW = softwareFlags.next('y', "Bad row gain").value();


  
}
