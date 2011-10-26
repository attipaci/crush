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
// Copyright (c) 2009 Attila Kovacs 

package crush.sourcemodel;

import util.*;
import util.astro.*;
import util.data.SphericalGrid;

public class AstroImage extends SourceImage<SphericalGrid> implements Cloneable {

	public AstroImage() {
	}

	public AstroImage(int sizeX, int sizeY) {
		super(sizeX, sizeY);
	}

	public AstroImage(double[][] data) {
		super(data);
	}

	public AstroImage(double[][] data, int[][] flag) {
		super(data, flag);
	}

	public SphericalProjection getProjection() {
		return getGrid().getProjection();
	}

	public SphericalCoordinates getReference() {
		return getGrid().getReference();
	}

	public boolean isHorizontal() {
		return getReference() instanceof HorizontalCoordinates;
	}

	public boolean isEquatorial() {
		return getReference() instanceof EquatorialCoordinates;
	}

	public boolean isEcliptic() {
		return getReference() instanceof EclipticCoordinates;
	}

	public boolean isGalactic() {
		return getReference() instanceof GalacticCoordinates;
	}

	public boolean isSuperGalactic() {
		return getReference() instanceof SuperGalacticCoordinates;
	}


	public void setProjection(SphericalProjection projection) {
		getGrid().projection = projection;
	}

	public void flag(Region region) { flag(region, 1); }

	public void flag(Region region, int pattern) {
		final Bounds bounds = region.getBounds(this);
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(region.isInside(getGrid(), i, j)) flag(i, j, pattern);
	}

	public void unflag(Region region, int pattern) {
		final Bounds bounds = region.getBounds(this);
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(region.isInside(getGrid(), i, j)) unflag(i, j, pattern);
	}

	public double getIntegral(Region region) {
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(isUnflagged(i, j)) if(region.isInside(getGrid(), i, j)) sum += getValue(i, j);	
		return sum;			
	}

	public double getLevel(Region region) {
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0;
		int n = 0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(isUnflagged(i, j)) if(region.isInside(getGrid(), i, j)) {
				sum += getValue(i, j);
				n++;
			}
		return sum / n;			
	}

	public double getRMS(Region region) {
		final Bounds bounds = region.getBounds(this);
		double var = 0.0;
		int n = 0;
		double level = getLevel(region);

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(isUnflagged(i, j)) if(region.isInside(getGrid(), i, j))  {
				double value = getValue(i, j) - level;
				var += value * value;
				n++;
			}
		var /= (n-1);

		return Math.sqrt(var);
	}	
	
}

