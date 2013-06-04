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
package kovacs.util.astro;

import kovacs.util.Projection2D;
import kovacs.util.Projector2D;
import kovacs.util.SphericalCoordinates;


public class CelestialProjector extends Projector2D<SphericalCoordinates> {
	private EquatorialCoordinates equatorial;
	private CelestialCoordinates celestial;
	
	public CelestialProjector(Projection2D<SphericalCoordinates> projection) {
		super(projection);

		// The equatorial is the same as coords if that is itself equatorial
		// otherwise it's used for converting to and from equatorial...
		if(getCoordinates() instanceof EquatorialCoordinates) 
			equatorial = (EquatorialCoordinates) getCoordinates();
		
		// celestial is the same as coords if coords itself is celestial
		// otherwise celestial is null, indicating horizontal projection...
		else if(getCoordinates() instanceof CelestialCoordinates) {
			celestial = (CelestialCoordinates) getCoordinates();
			equatorial = new EquatorialCoordinates();
		}

	}
	
	public EquatorialCoordinates getEquatorial() { return equatorial; }
	
	public final boolean isHorizontal() {
		return getCoordinates() instanceof HorizontalCoordinates;
	}
	
	@Override
	public void setReferenceCoords() {
		super.setReferenceCoords();
		if(celestial != null) celestial.toEquatorial(equatorial);		
	}
	
	@Override
	public final void project() {
		if(celestial != null) celestial.fromEquatorial(equatorial);
		super.project();
	}
	
	@Override
	public final void deproject() {
		super.deproject();
		if(celestial != null) celestial.toEquatorial(equatorial);
	}
}
