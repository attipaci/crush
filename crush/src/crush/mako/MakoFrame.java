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
package crush.mako;

import crush.*;

public class MakoFrame extends HorizontalFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3492248390542871495L;
	int frameNumber;
	int telescopeFlags = 0;
	
	public MakoFrame(MakoScan<?> parent) {
		super(parent);
	}
	
	public void parseData(float[] value, int from, AbstractMako<?> mako) {
		setSize(mako.size());
		
		for(int c=mako.size(); --c >= 0; ) {
			data[c] = -value[from + ((AbstractMakoPixel) mako.get(c)).toneIndex];
			if(Float.isNaN(data[c])) {
				data[c] = 0.0F;
				sampleFlag[c] = Frame.SAMPLE_SKIP;
			}
		}
	}
}
