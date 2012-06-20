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
// Copyright (c) 2007 Attila Kovacs 

package util.plot;


import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import javax.swing.*;

import util.Range;
import util.ScaleDivisions;
import util.Unit;
import util.Vector2D;

import java.util.*;

// TODO
//   * 4 orientations (H & V, with alignment...)
//   * update also content.getCoordinateLayer() if necessary for inward ticks
//   * Mouse over to draw grey ticks for dragging when outer ticks not drawn
//				-- inner ticks not redrawn until finished dragging)
//				-- move linked rulers (moveScale(double value))
//   * Mouse drag to rescale --> apply scale to content (content.getScale());
//							--> apply scale to linked rulers...
//
//	 * Right click to bring up scale dialog (also change units here...)
//	 * Click to change scale type (log or sqrt might be disabled)
//
//	 * Turn on/off numbering
//   * NumberFormatting
//   * Numbering orientation
//   * Numbering width...

public abstract class BasicRuler extends JComponent implements PlotSide {
	/**
	 * 
	 */
	private static final long serialVersionUID = 228962325800810360L;
	private ScaleDivisions mainDivisions, subDivisions;
	
	private int side = Plot.SIDE_UNDEFINED;
	private Unit unit = Unit.unity;
	private Marks marks;
	
	public BasicRuler(int edge) {
		mainDivisions = new ScaleDivisions(1);
		subDivisions = new ScaleDivisions(10);
		marks = new Marks();
		setSide(edge);
	}

	public void setSide(int edge) {
		this.side = edge;
		marks.setSide(edge);
	}
	
	public int getSide() { return side; }
	
	public Unit getUnit() { return unit; }
	
	public void setUnit(Unit u) { 
		this.unit = u; 
	}
	
	public boolean isHorizontal() { return side == Plot.TOP_SIDE || side == Plot.BOTTOM_SIDE; }
	
	public boolean isVertical() { return side == Plot.LEFT_SIDE || side == Plot.RIGHT_SIDE; }
	
	public Marks getMarks() { return marks; }
	
	public ScaleDivisions getMainDivisions() { return mainDivisions; }
	
	public ScaleDivisions getSubdivisions() { return subDivisions; }
	
	public void setMainDivisions(ScaleDivisions divs) { this.mainDivisions = divs; }
	
	public void setSubdivisions(ScaleDivisions divs) { this.subDivisions = divs; }
	
	// Get the component position for the given value
	// Note, that the position does not need to be aligned to the border. This will
	// be automatically done when drawing (by calling snapToSide())...
	public abstract void getPosition(double value, Point2D pos);
		
	// Get the value corresponding to the component position
	public abstract double getValue(Point2D pos);
	
	public void setRange(Range range) {
		setRange(range.min(), range.max());
	}
	
	public void setRange(double min, double max) {
		min /= unit.value();
		max /= unit.value();
		if(mainDivisions != null) mainDivisions.update(min, max);
		if(subDivisions != null) subDivisions.update(min, max);
	}
	
	@Override
	public void setBackground(Color color) {
		super.setBackground(color);
		if(marks != null) marks.setBackground(color);
	}
	
	
	public class Marks extends JComponent {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1221288634179934990L;
		private int divLength;
		private int subdivLength;
			
		private Stroke divStroke = new BasicStroke();
		private Stroke smallStroke = new BasicStroke();

		private Vector2D direction = new Vector2D();
		
		public Marks() {
			mainDivisions = new ScaleDivisions();
			subDivisions = new ScaleDivisions(10);
			setSide(side);
			setDivLength(8);
			setSubdivLength(3);
		}
	
		public void setSide(int side) {
			switch(side) {
			case Plot.TOP_SIDE: direction.set(0.0, -1.0); break;
			case Plot.BOTTOM_SIDE: direction.set(0.0, 1.0); break;
			case Plot.LEFT_SIDE: direction.set(-1.0, 0.0); break;
			case Plot.RIGHT_SIDE: direction.set(1.0, 0.0); break;
			default: direction.set(0.0, 0.0);
			}
			setPreferredSize();
		}
		
		protected void getAlignment(Vector2D v) {
			v.setX(0.5 * (1.0 - direction.getX()));
			v.setY(0.5 * (1.0 - direction.getY()));
		}
			
		public void setDivLength(int pixels) {
			divLength = pixels; 
			setPreferredSize();
		}
		
		public void setSubdivLength(int pixels) { 
			subdivLength = pixels; 
			setPreferredSize();
		}
		
		public int getDivLength() { return divLength; }
		
		public int getSubdivLength() { return subdivLength; }
		
		private void setPreferredSize() {		
			if(isVertical()) setPreferredSize(new Dimension(divLength, 0));
			else if(isHorizontal()) setPreferredSize(new Dimension(0, divLength));
			else setPreferredSize(new Dimension(0, 0));
		}
		
		// TODO separate handling of CUSTOM subdivisions....
		@Override
		public void paintComponent(Graphics g) {	
			super.paintComponent(g);
			
			int length = isHorizontal() ? getWidth() : getHeight();
			final double npix = (double) length / subDivisions.size();
			
			// Draw subdivisions only if there is enough room for them...
			if(npix > 0.4) {
				// Figure out how many of the subdivisions to actually draw...
				int subStep = 1;
				if(npix < 5 && npix > 2) subStep = 2; 
				else if(npix <= 2 ) subStep=5;
				
				// Set the stroke rendering
				((Graphics2D) g).setStroke(smallStroke);
				
				draw(g, subDivisions.getDivisions(), subStep, subdivLength);
			}
			
			// Now draw the main divisions...
			// Set the stroke rendering
			((Graphics2D) g).setStroke(divStroke);
			
			draw(g, mainDivisions.getDivisions(), 1, divLength);
		}
		
		private void draw(Graphics g, ArrayList<Double> divs, int step, int length) {
			Point2D pos = new Point2D.Double();
			Graphics2D g2 = (Graphics2D) g;
			
			int dx = (int) Math.round(length * direction.getX());
			int dy = (int) Math.round(length * direction.getY());
			
			for(int i=0; i<divs.size(); i += step) {
				getPosition(divs.get(i), pos);
				toPlotSide(pos);
				
				//System.err.println("### " + i + ": " + divs.get(i) + ",\t" + pos);
				
				g2.draw(new Line2D.Double(pos.getX(), pos.getY(), pos.getX() + dx, pos.getY() + dy));
			}	
		}
	}
	
	protected void toPlotSide(Point2D pos) {
		// snap to the appropriate display edge...
		switch(getSide()) {
		case Plot.TOP_SIDE: pos.setLocation(pos.getX(), getHeight()-1); break;
		case Plot.BOTTOM_SIDE: pos.setLocation(pos.getX(), 0); break;
		case Plot.LEFT_SIDE: pos.setLocation(getWidth()-1, pos.getY()); break;
		case Plot.RIGHT_SIDE: pos.setLocation(0, pos.getY()); break;
		default: pos.setLocation(Double.NaN, Double.NaN);
		}
	}
	
	

	
}
