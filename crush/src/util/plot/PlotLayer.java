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

package util.plot;

import java.awt.Graphics;
import java.awt.geom.AffineTransform;

import javax.swing.JComponent;


public abstract class PlotLayer extends JComponent {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1331038094866703152L;

	private PlotArea<?> plotArea;
	
	public abstract void defaults();
	
	public PlotArea<?> getPlotArea() { return plotArea; }
	
	public void setPlotArea(PlotArea<?> area) { this.plotArea = area; }
	
	public AffineTransform toDisplay() { return getPlotArea().toDisplay(); }
	
	public AffineTransform toCoordinates() { return getPlotArea().toCoordinates(); }
	
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);	
	}
	

}
