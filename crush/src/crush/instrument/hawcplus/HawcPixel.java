/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.hawcplus;


import crush.Channel;
import crush.telescope.sofia.SofiaChannel;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;

public class HawcPixel extends SofiaChannel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5898856651596856837L;
	
	public int pol = -1, sub = -1, subrow = -1, col = -1, mux = -1, row = -1, biasLine = -1, seriesArray = -1;
	public double subGain = 1.0, muxGain = 1.0, pinGain = 1.0, biasGain = 1.0, seriesGain = 1.0;
	
	int fitsIndex, fitsRow, fitsCol;
	
	float jumpLevel = 0.0F;
	boolean hasJumps = false;
	
	HawcPixel(Hawc array, int zeroIndex) {
		super(array, zeroIndex);
		
		col = zeroIndex % Hawc.subarrayCols;
		row = zeroIndex / Hawc.subarrayCols;
		sub = zeroIndex / Hawc.subarrayPixels;
		pol = (sub>>1);
		
		fitsRow = subrow = row % Hawc.rows;
		biasLine = row >>> 1;
		
		fitsCol = mux = sub * Hawc.subarrayCols + col;
		seriesArray = mux >>> 2;
		
		fitsIndex = fitsRow * HawcFrame.FITS_COLS + fitsCol;
		
		// Flag the dark squids as such...
		if(subrow == Hawc.DARK_SQUID_ROW) flag(FLAG_BLIND);
	}
	
	@Override
    public Hawc getInstrument() { return (Hawc) super.getInstrument(); }
	
	void calcSIBSPosition() {
	    if(isFlagged(FLAG_BLIND)) getPixel().setPosition(null);
	    getPixel().setPosition(getInstrument().getLayout().getSIBSPosition(sub, subrow, col));
	}
		
	@Override
	public int getCriticalFlags() {
		return FLAG_DEAD;
	}
	
	@Override
	public void uniformGains() {
		super.uniformGains();
		muxGain = 1.0;
	}
	
	@Override
	public String toString() {
		return super.toString() 
		        + "\t" + Util.f3.format(coupling) 
		        + "\t" + Util.f3.format(getInstrument().subarrayGainRenorm[sub])
		        + "\t" + Util.f3.format(muxGain) 
		        + "\t" + getFixedIndex() 
		        + "\t" + sub 
		        + "\t" + subrow 
		        + "\t" + col;
	}
	
	@Override
	public void parseValues(SmartTokenizer tokens, int criticalFlags) {	
		super.parseValues(tokens, criticalFlags);
		
		if(tokens.hasMoreTokens()) coupling = tokens.nextDouble();
		if(tokens.hasMoreTokens()) tokens.nextToken();
		if(tokens.hasMoreTokens()) muxGain = tokens.nextDouble();
		
		if(coupling < 0.3) flag(Channel.FLAG_DEAD);
		else if(coupling > 3.0) flag(Channel.FLAG_DEAD);
		else if(coupling == 1.0) flag(Channel.FLAG_DEAD);
		
		if(gain < 0.3) flag(Channel.FLAG_DEAD);
        else if(gain > 3.0) flag(Channel.FLAG_DEAD);
			
		if(isFlagged(Channel.FLAG_DEAD)) coupling = 0.0;
	}
	
	@Override
    public String getID() {
	    return Hawc.polID[pol] + (sub & 1) + "[" + subrow + "," + col + "]";
	}
	
	public static final Vector2D physicalSize = new Vector2D(1.132 * Unit.mm, 1.132 * Unit.mm);
	
	//public final static int FLAG_POL = softwareFlags.next('p', "Bad polarray gain").value();
	public final static int FLAG_SUB = softwareFlags.next('@', "Bad subarray gain").value();
	public final static int FLAG_BIAS = softwareFlags.next('b', "Bad TES bias gain").value();
	public final static int FLAG_MUX = softwareFlags.next('m', "Bad MUX gain").value();
	public final static int FLAG_ROW = softwareFlags.next('R', "Bad detector row gain").value();
	public final static int FLAG_SERIES_ARRAY = softwareFlags.next('M', "Bad series array gain").value();
	public final static int FLAG_FLICKER = softwareFlags.next('T', "Flicker noise").value();


}
