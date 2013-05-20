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
package util.plot;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

import util.Range;
import util.ScaleDivisions;
import util.Vector2D;

public class RulerLayer extends PlotLayer {
	/**
	 * 
	 */
	private static final long serialVersionUID = -792580560937914048L;
	private Ruler left, right, top, bottom;
	
	private boolean mirrorX = true, mirrorY = true;
	
	public RulerLayer() {
		left = new Ruler(Plot.LEFT_SIDE);
		right = new Ruler(Plot.RIGHT_SIDE);
		top = new Ruler(Plot.TOP_SIDE);
		bottom = new Ruler(Plot.BOTTOM_SIDE);
	}
	
	@Override
	public void defaults() {
		ScaleDivisions xDivs = new ScaleDivisions(1);
		ScaleDivisions xSubs = new ScaleDivisions(10);
		ScaleDivisions yDivs = new ScaleDivisions(1);
		ScaleDivisions ySubs = new ScaleDivisions(10);
		
		left.setMainDivisions(yDivs);
		left.setSubdivisions(ySubs);
		
		right.setMainDivisions(yDivs);
		right.setSubdivisions(ySubs);
		
		top.setMainDivisions(xDivs);
		top.setSubdivisions(xSubs);
		
		bottom.setMainDivisions(xDivs);
		bottom.setSubdivisions(xSubs);
		
		updateDivisions();
	}
	
	public void updateDivisions() {
		Rectangle bounds = this.getContentArea().getBounds();
		Range xRange = new Range(bounds.getMinX(), bounds.getMaxX());
		Range yRange = new Range(bounds.getMinY(), bounds.getMaxY());
		
		left.setRange(xRange);
		top.setRange(yRange);

		if(!mirrorY) right.setRange(xRange);
		if(!mirrorX) bottom.setRange(yRange);
	}
	
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		// Make sure the rulers are the same size as the plot itself...
		Dimension size = getSize();
		
		left.setSize(size);
		right.setSize(size);
		top.setSize(size);
		bottom.setSize(size);
		
		// Now, go ahead and paint...
		left.paint(g);
		right.paint(g);
		top.paint(g);
		bottom.paint(g);
	}

	public class Ruler extends BasicRuler {
		/**
		 * 
		 */
		private static final long serialVersionUID = -3305810445444215892L;

		public Ruler(int edge) {
			super(edge);
		}
		
		@Override
		public void getPosition(double value, Point2D pos) {
			ContentArea<?> contentArea = getContentArea(); 
			Vector2D offset = contentArea.getContentLayer().getUserOffset();

			// set the value at the reference position of the other coordinate...
			if(isHorizontal()) pos.setLocation(offset.getX(), value);
			else if(isVertical()) pos.setLocation(value, offset.getY());
			
			// convert to display coordinates...
			contentArea.toDisplay(pos);
		}

		@Override
		public double getValue(Point2D pos) {
			ContentArea<?> contentArea = getContentArea(); 
			Vector2D ref = contentArea.getReferencePoint();
			
			// Move the position to the reference of the other axis...
			if(isHorizontal()) pos.setLocation(pos.getX(), ref.getY());
			else if(isVertical()) pos.setLocation(ref.getX(), pos.getY());	
			
			// convert to content coordinates
			contentArea.toCoordinates(pos);
			
			// return the value for the appropriate axis...
			if(isHorizontal()) return pos.getX();
			else if(isVertical()) return pos.getY();
			else return Double.NaN;
		}
		
		public void setPlot(Plot<?> plot) {
			// TODO Auto-generated method stub
			
		}
		
		
	}
}
