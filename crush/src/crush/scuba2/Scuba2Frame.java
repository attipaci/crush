/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import crush.*;


public class Scuba2Frame extends HorizontalFrame {
	public int frameNumber;
	public float detectorT;
	
	public Scuba2Frame(Scuba2Scan parent) {
		super(parent);
		setSize(Scuba2.pixels);
	}
	
	/*
	// TODO see if there are any sanity checks...
	public final boolean isValid() {
		if(Double.isNaN(horizontal.x) && Double.isNaN(equatorial.x)) return false;
		return true;
	}
	*/
	
	public void parseData(int[][] DAC) {
		final int blankingValue = ((Scuba2Scan) scan).blankingValue;
		
		for(int bol=Scuba2.pixels; --bol >= 0; ) {
			int value = DAC[bol%40][bol/40];
			if(value != blankingValue) data[bol] = value;
			else sampleFlag[bol] |= Frame.SAMPLE_SKIP;
		}
	}
	
	
}
