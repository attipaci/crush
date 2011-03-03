/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import util.*;

import java.io.*;
import java.util.*;

public class EBEXBoard extends ArrayList<EBEXPixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4244603106971958980L;

	int number;
	EBEXTimeStamp<Double> ebexTime;
	long startTSIndex, endTSIndex;
	double startTS, samplingRate;
	
	
	// E.g.: board56_Time_timebase
	
	public void setEBEXTimeRange(double fromEBEXTime, double toEBEXTime) throws IOException {
		startTSIndex = ebexTime.getLowerIndex(fromEBEXTime);
		startTS = ebexTime.get(startTSIndex);
		
		long endTSIndex = ebexTime.getLowerIndex(toEBEXTime) + 1L;
	
		double[] intervals = new double[(int)(endTSIndex - startTSIndex)];
		long tsIndex = startTSIndex + 1L;
		double lastTS = startTS;
		
		for(int i=0; i < intervals.length; i++, tsIndex++) {
			double TS = ebexTime.get(tsIndex);
			intervals[i] = TS - lastTS;
			lastTS = TS;
		}
	
		samplingRate = 1.0 / ArrayUtil.median(intervals);
	}
	
}
