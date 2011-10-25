/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

public class CartesianImage extends GridImage<CartesianGrid2D> {

	CartesianGrid2D grid;

	
	public CartesianImage() {
		super();
	}

	public CartesianImage(double[][] data, int[][] flag) {
		super(data, flag);
	}

	public CartesianImage(double[][] data) {
		super(data);
	}

	public CartesianImage(int sizeX, int sizeY) {
		super(sizeX, sizeY);
	}

	@Override
	public CartesianGrid2D getGrid() {
		return grid;
	}

	@Override
	public void setGrid(CartesianGrid2D grid) {
		this.grid = grid;
		double fwhm = Math.sqrt(grid.getPixelArea()) / GridImage.fwhm2size;
		if(smoothFWHM < fwhm) smoothFWHM = fwhm;
	}

}
