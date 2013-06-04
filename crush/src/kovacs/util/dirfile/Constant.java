/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of kovacs.util.
 * 
 *     kovacs.util is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     kovacs.util is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with kovacs.util.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
// Copyright (c) 2010 Attila Kovacs 

package kovacs.util.dirfile;

import java.io.IOException;


public class Constant extends DataStore<Number> {
	boolean floating = true;
	long iValue;
	double fValue = Double.NaN;
	
	public Constant(String name, String type, String value) {	
		super(name);
		
		type = type.toLowerCase();
	
		switch(type.charAt(0)) {
		case 'u' : iValue = Long.decode(value); break;
		case 's' : iValue = Long.decode(value); break; 
		case 'i' : iValue = Long.decode(value); break;
		case 'f' : fValue = Float.parseFloat(value); break; 
		case 'd' : fValue = Double.parseDouble(value); break;
		default : throw new IllegalArgumentException("No constant type for " + type);
		}
		
		floating = !Double.isNaN(fValue);
	}
	
	@Override
	public Number get(long n) throws IOException {
		return floating ? fValue : iValue;
	}
	@Override
	public int getSamples() {
		return 1;
	}
	@Override
	public long length() throws IOException {
		return 1L;
	}
	
	
	
}
