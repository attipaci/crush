/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/
// Copyright (c) 2009,2010 Attila Kovacs

package crush.instrument;

import java.util.List;

import crush.Channel;
import crush.Instrument;
import crush.PixelLayout;
import crush.Scan;
import crush.SourceModel;

public class GenericInstrument extends Instrument<Channel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7286113017649967311L;
	private String telescope;
	
	private int maxPixels;
	
	public GenericInstrument(String name, int size) {
		super(name, size);
		maxPixels = size;
	}

	public GenericInstrument(String name) {
		super(name);
	}

	public void setMaxPixels(int n) {
	    maxPixels = n;
	}
	
	public void setTelescopeName(String value) { telescope = value; }
	
	@Override
	public String getTelescopeName() {
		return telescope;
	}
	
	@Override
	public Channel getChannelInstance(int backendIndex) {
		return null;
	}


	@Override
	public Scan<?> getScanInstance() {
		return null;
	}

	@Override
	public SourceModel getSourceModelInstance(List<Scan<?>> scans) {
		return null;
	}
	

    @Override
    public int maxPixels() {
        return maxPixels;
    }

    @Override
    protected PixelLayout getLayoutInstance() {
        return null; // TODO
    }


}
