/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.mako2;

import crush.SourceModel;
import crush.instrument.mako.MakoScan;

public class Mako2Scan extends MakoScan<Mako2> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 9146779622501912860L;


	public Mako2Scan(Mako2 instrument) {
		super(instrument);
	}
	
	
	@Override
	public void setSourceModel(SourceModel model) {
		super.setSourceModel(model);
		sourceModel.setID(hasOption("850um") ? "850um" : "350um");
	}	
	
}
