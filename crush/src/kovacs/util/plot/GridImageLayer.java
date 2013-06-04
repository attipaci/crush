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
package kovacs.util.plot;

import java.awt.geom.NoninvertibleTransformException;

import kovacs.util.data.Grid2D;
import kovacs.util.data.GridImage;


public class GridImageLayer extends Data2DLayer {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5730801953668713086L;
	private Grid2D<?> grid;
	
	public GridImageLayer(GridImage<?> image) {
		super(image);
			
		grid = image.getGrid();
		
		try { setCoordinateTransform(grid.getLocalAffineTransform()); }
		catch(NoninvertibleTransformException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
		
	}
	
	public Grid2D<?> getGrid() { return grid; }
		
	public GridImage<?> getGridImage() { return (GridImage<?>) getData2D(); }
	
}
