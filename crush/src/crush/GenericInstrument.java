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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import java.io.IOException;
import java.util.Collection;

public class GenericInstrument extends Instrument<Channel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7286113017649967311L;
	public String telescope;
	
	public GenericInstrument(String name, int size) {
		super(name, size);
		// TODO Auto-generated constructor stub
	}

	public GenericInstrument(String name) {
		super(name);
		// TODO Auto-generated constructor stub
	}

	@Override
	public String getTelescopeName() {
		return telescope;
	}
	
	@Override
	public Channel getChannelInstance(int backendIndex) {
		return null;
	}

	@Override
	public String getDefaultSizeName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double getDefaultSizeUnit() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SourceModel getSourceModelInstance() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void readWiring(String fileName) throws IOException {
		// TODO Auto-generated method stub		
	}

	@Override
	public Collection<? extends Pixel> getMappingPixels() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getPixelCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Collection<? extends Pixel> getPixels() {
		// TODO Auto-generated method stub
		return null;
	}

}
