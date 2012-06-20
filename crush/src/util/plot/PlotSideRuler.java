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

import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import util.Vector2D;

public class PlotSideRuler extends FancyRuler {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3259644546352934697L;
	private Plot<?> plot;
	
	public PlotSideRuler(Plot<?> plot, int edge) {
		super(edge);
		setPlot(plot);
		//System.err.println("### created side ruler " + edge);
	}
	
	public Plot<?> getPlot() { return plot; }
	
	public void setPlot(Plot<?> plot) { 
		this.plot = plot; 
	}
	
	@Override
	public void validate() {
		//System.err.println("### validating side ruler...");
		
		Rectangle2D bounds = plot.getCoordinateBounds(getSide());
		//System.err.println("### bounds " + bounds);
		
		if(isHorizontal()) setRange(bounds.getMinX(), bounds.getMaxX());
		else if(isVertical()) setRange(bounds.getMinY(), bounds.getMaxY());
		
		super.validate();
	}
	
	
	@Override
	public void paint(Graphics g) {
		//System.err.println("### painting side ruler...");
		validate();
		super.paint(g);		
	}
	
	public ContentArea<?> getContentArea() { return getPlot().getContent(); }
	
	@Override
	public void getPosition(double value, Point2D pos) {
		ContentArea<?> contentArea = getContentArea(); 
			
		// set the value at the reference position of the other coordinate...
		Vector2D offset = contentArea.getContentLayer().getUserOffset();
		if(isHorizontal()) pos.setLocation(value, offset.getY());
		else if(isVertical()) pos.setLocation(offset.getX(), value);

		//System.err.println("### --> " + pos);
		
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
	
}
