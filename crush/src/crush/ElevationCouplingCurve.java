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
// Copyright (c) 2007,2008,2009,2010 Attila Kovacs

package crush;

import java.io.*;

import jnum.Unit;
import jnum.data.Interpolator;
import jnum.io.LineParser;
import jnum.text.SmartTokenizer;


public class ElevationCouplingCurve extends Interpolator {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ElevationCouplingCurve(String fileName) throws IOException {
		super(fileName);		
	}
	
	@Override
	public void readData(String fileName) throws IOException {
	    new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                Interpolator.Data response = new Interpolator.Data();
                response.ordinate = tokens.nextDouble() * Unit.deg;
                response.value = tokens.nextDouble();
                add(response);
                return true;
            }     
	    }.read(fileName);
	}	
}
