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
package crush;

public class ShadowScan extends Scan<Instrument<?>, Integration<Instrument<?>,?>> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3683079235950210496L;
	Scan<Instrument<?>,?> parent;
		
	public ShadowScan(Scan<Instrument<?>,?> parent) {
		super(parent.instrument);
		this.parent = parent;
	}
	
	@Override
	public Integration<Instrument<?>,?> getIntegrationInstance() {
		return parent.getIntegrationInstance();
	}

	@Override
	public void read(String descriptor, boolean readFully) throws Exception {
		throw new UnsupportedOperationException("Shadow scans cannot be read.");
	}


}
