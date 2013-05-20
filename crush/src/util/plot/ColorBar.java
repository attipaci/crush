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
// Copyright (c) 2007 Attila Kovacs 

package util.plot;

import java.awt.*;
import java.awt.geom.Point2D;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;

import util.Range;


public class ColorBar extends JComponent implements PlotSide {
	/**
	 * 
	 */
	private static final long serialVersionUID = 460851913543807978L;
	
	private ImageArea<?> imager;

	private Stripe stripe;
	private Ruler ruler;
	
	private int side = Plot.SIDE_UNDEFINED;
	
	
		
	public ColorBar(ImageArea<?> imager) { 
		this.imager = imager;
		stripe = new Stripe(defaultWidth);
		ruler = new Ruler();
		//TODO set the unit on the ruler to that of the image...
	}
	
	public boolean isHorizontal() { return side == Plot.TOP_SIDE || side == Plot.BOTTOM_SIDE; }
	
	public boolean isVertical() { return side == Plot.LEFT_SIDE || side == Plot.RIGHT_SIDE; }
	
	public int getSide() { return side; }
		
	public void setSide(int side) {
		if(side == this.side) return;
		this.side = side;	
		
		if(ruler != null) ruler.setSide(side);
			
		if(isHorizontal()) setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		else if(isVertical()) setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		
		arrange();
	}
	
	private void arrange() {
		removeAll();
		
		if(side == Plot.TOP_SIDE || side == Plot.LEFT_SIDE) {
			if(ruler != null) add(ruler);
			if(stripe != null) add(stripe);			
		}
		else if(side == Plot.BOTTOM_SIDE || side == Plot.RIGHT_SIDE) {
			if(stripe != null) add(stripe);
			if(ruler != null) add(ruler);
		}
	}
		
	
	public class Stripe extends JComponent {	
		/**
		 * 
		 */
		private static final long serialVersionUID = 5950901962993328368L;
		private int width;
		private boolean inverted = false;

		private Stripe(int width) {	
			this.width = width;
			setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		}
		
		@Override
		public void paintComponent(Graphics g) {
			super.paintComponents(g);
			
			if(isHorizontal()) drawHorizontal(g);
			else drawVertical(g);
		}
		
		private void drawVertical(Graphics g) {
			ColorScheme colors = imager.getContentLayer().getColorScheme();	
			double scale = 1.0 / getHeight();
			
			for(int y = getHeight() - 1; --y >= 0; ) {
				double value = inverted ? scale * y : 1.0 - scale * y;
				g.setColor(new Color(colors.getRGB(value)));
				g.drawRect(0, y, width, 1);
			}	
		}
		
		private void drawHorizontal(Graphics g) {
			ColorScheme colors = imager.getContentLayer().getColorScheme();	
			double scale = 1.0 / getWidth();
			
			for(int x = getWidth() - 1; --x >= 0; ) {
				double value = inverted ? 1.0 - scale * x : scale * x;
				g.setColor(new Color(colors.getRGB(value)));
				g.drawRect(x, 0, 1, width);
			}	
		}
		

		@Override
		public Dimension getPreferredSize() {
			if(isVertical()) return new Dimension(width, 2);
			else if(isHorizontal()) return new Dimension(2, width);
			else return super.getPreferredSize();
		}
		
		public void setWidth(int pixels) { this.width = pixels; }
		
		public void invert() {
			inverted = !inverted;
		}		
		
	}
	
	
	
	
	public class Ruler extends FancyRuler {
		/**
		 * 
		 */
		private static final long serialVersionUID = -7906137098891819994L;

		private Ruler() {
			super(ColorBar.this.getSide());
		}

		@Override
		public void getPosition(double value, Point2D pos) {
			Range range = imager.getContentLayer().getRange();
			double frac = (value - range.min()) / range.span();
			if(isHorizontal()) pos.setLocation(frac * getWidth(), 0);
			else if(isVertical()) pos.setLocation(0, getHeight() * (1.0 - frac));
		}

		@Override
		public double getValue(Point2D pos) {
			Range range = imager.getContentLayer().getRange();
			
			double frac = isHorizontal() ?
					pos.getX() / getWidth() :
					1.0 - pos.getY() / getHeight();
					
			return range.min() + frac * range.span();
		}	
	}
	
	
	
	
	private static int defaultWidth = 20;
	

}

