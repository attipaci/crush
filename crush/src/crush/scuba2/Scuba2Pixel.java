/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of the proprietary SCUBA-2 modules of crush.
 * 
 * You may not modify or redistribute this file in any way. 
 * 
 * Together with this file you should have received a copy of the license, 
 * which outlines the details of the licensing agreement, and the restrictions
 * it imposes for distributing, modifying or using the SCUBA-2 modules
 * of CRUSH-2. 
 * 
 * These modules are provided with absolutely no warranty.
 ******************************************************************************/
package crush.scuba2;

import kovacs.math.Vector2D;
import kovacs.util.*;
import crush.array.SimplePixel;

public class Scuba2Pixel extends SimplePixel {
	public int mux, pin, block=0;
	public double muxGain = 1.0, pinGain = 1.0;
	public double temperatureGain = 0.0;
	
	// 32 x 40 (rows x cols)
	
	public Scuba2Pixel(Scuba2 array, int zeroIndex) {
		super(array, zeroIndex+1);
		mux = zeroIndex / 40;
		pin = zeroIndex % 40;
		// TODO This is just a workaround...
		variance = 1.0;
	}
	
	
	@Override
	public int getCriticalFlags() {
		return FLAG_DEAD | FLAG_BLIND;
	}
	
	@Override
	public void uniformGains() {
		super.uniformGains();
		muxGain = 1.0;
		pinGain = 1.0;
	}
	
	public static Vector2D defaultSize = new Vector2D(5.7 * Unit.arcsec, 5.7 * Unit.arcsec);
	
	public final static int FLAG_MUX = 1 << nextSoftwareFlag++;
	public final static int FLAG_PIN = 1 << nextSoftwareFlag++;
	
	
}
