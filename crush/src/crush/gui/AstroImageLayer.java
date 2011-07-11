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
package crush.gui;

import java.awt.geom.NoninvertibleTransformException;

import util.Vector2D;
import util.plot.DoubleImageLayer;

import crush.sourcemodel.*;

public class AstroImageLayer extends DoubleImageLayer {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5730801953668713086L;
	
	AstroImage image;
	
	public AstroImageLayer(AstroImage image) {
		super(image.data);
		this.image = image;
		
		try { this.setCoordinateTransform(image.grid.getLocalAffineTransform()); }
		catch(NoninvertibleTransformException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	
		defaults();
	}
	
	@Override
	public double getValue(int i, int j) {
		if(image.flag[i+i0][j+j0] != 0) return Double.NaN;
		return super.getValue(i, j);
	}
	
	@Override
	public Vector2D getReferencePoint() {
		return image.grid.refIndex;
	}
	
}
