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
package util.data;

import util.Util;

public class GaussianPSF {

	public static double[][] getBeam(double FWHM, Grid2D<?> grid) {
		return getBeam(FWHM, grid, 3.0);
	}	

	public static double[][] getBeam(double FWHM, Grid2D<?> grid, double nBeams) {
		int sizeX = 2 * (int)Math.ceil(nBeams * FWHM/grid.pixelSizeX()) + 1;
		int sizeY = 2 * (int)Math.ceil(nBeams * FWHM/grid.pixelSizeY()) + 1;
		
		final double[][] beam = new double[sizeX][sizeY];
		final double sigma = FWHM / Util.sigmasInFWHM;
		final double Ax = -0.5 * grid.pixelSizeX() * grid.pixelSizeX() / (sigma * sigma);
		final double Ay = -0.5 * grid.pixelSizeY() * grid.pixelSizeY() / (sigma * sigma);
		final double centerX = (sizeX-1) / 2.0;
		final double centerY = (sizeY-1) / 2.0;
		
		for(int i=sizeX; --i >= 0; ) for(int j=sizeY; --j >= 0; ) {
			double dx = i - centerX;
			double dy = j - centerY;

			beam[i][j] = Math.exp(Ax*dx*dx + Ay*dy*dy);
		}
		return beam;
		
	}
	
}
