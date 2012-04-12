/*******************************************************************************
 * Copyright (c) 2012 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package util.plot;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import util.Vector2D;


public abstract class ContentLayer extends PlotLayer {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5434391089909200423L;
	
	private Vector2D userOffset = new Vector2D();
	
	public Vector2D getUserOffset() { return userOffset; }
	
	public void setUserOffset(Vector2D v) { this.userOffset = v; }
	
	public void center() {	
		Rectangle2D bounds = getCoordinateBounds();
			
		// Get the nominal center as the middle of the bounding box
		// in the native coordinates...
		setUserOffset(new Vector2D(new Point2D.Double(
				bounds.getMinX() + 0.5 * bounds.getWidth(),
				bounds.getMinY() + 0.5 * bounds.getHeight()
		)));
	}
	

	public void align() { userOffset.zero(); }
	
	public abstract Rectangle2D getCoordinateBounds();
	
	public abstract void initialize();

}
