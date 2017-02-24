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
// Copyright (c) 2009,2010 Attila Kovacs

package crush.instrument;

import java.util.List;

import crush.Channel;
import crush.Instrument;
import crush.Pixel;
import crush.Scan;
import crush.SourceModel;
import jnum.Unit;

public class GenericInstrument extends Instrument<Channel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7286113017649967311L;
	private String telescope;
	
	public GenericInstrument(String name, int size) {
		super(name, null, size);
	}

	public GenericInstrument(String name) {
		super(name, null);
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
	public String getSizeName() {
		return "arcsec";
	}

	@Override
	public double getSizeUnitValue() {
		return Unit.arcsec;
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return null;
	}

	@Override
	public SourceModel getSourceModelInstance() {
		return null;
	}

	@Override
	public List<? extends Pixel> getMappingPixels(int keepFlags) {
		return null;
	}

	@Override
	public int getPixelCount() {
		return 0;
	}

	@Override
	public List<? extends Pixel> getPixels() {
		return null;
	}

}
