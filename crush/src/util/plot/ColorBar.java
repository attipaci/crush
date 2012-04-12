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
import java.awt.geom.Point2D;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;

import util.Range;


public class ColorBar extends TransparentPanel implements PlotSide {
	/**
	 * 
	 */
	private static final long serialVersionUID = 460851913543807978L;
	
	private ImageArea<?> imager;
	private Stripe stripe;
	private int side = Plot.SIDE_UNDEFINED;
	//private Ruler ruler;
	
		
	public ColorBar(ImageArea<?> imager) { 
		this.imager = imager;
		stripe = new Stripe(defaultWidth, defaultShades);
		//ruler = new Ruler();
		//TODO set the unit on the ruler to that of the image...
	}
	
	public boolean isHorizontal() { return side == Plot.TOP_SIDE || side == Plot.BOTTOM_SIDE; }
	
	public boolean isVertical() { return side == Plot.LEFT_SIDE || side == Plot.RIGHT_SIDE; }
	
	public int getSide() { return side; }
		
	public void setSide(int side) {
		if(side == this.side) return;
		this.side = side;
		
		if(stripe != null) stripe.create();		
		
		//if(ruler != null) ruler.setSide(side);
			
		if(isHorizontal()) setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		else if(isVertical()) setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		
		arrange();
	}
	
	private void arrange() {
		removeAll();
		
		if(side == Plot.TOP_SIDE || side == Plot.LEFT_SIDE) {
			//add(ruler);
			if(stripe != null) add(stripe);			
			add(new JPanel());
		}
		else if(side == Plot.BOTTOM_SIDE || side == Plot.RIGHT_SIDE) {
			add(new JPanel());
			if(stripe != null) add(stripe);
			//add(ruler);
		}
	}
	
	public int getShades() { return stripe.getShades(); }
	
	public void setShades(int n) { stripe.setShades(n); }
	
	
	
	
	public class Stripe extends ImageArea<ImageLayer> {	
		/**
		 * 
		 */
		private static final long serialVersionUID = 5950901962993328368L;
		private int shades;
		private int width;

		private Stripe(int width, int shades) {	
			this.width = width;
			setShades(shades);
			setRotation(0.0);
			setTransparent(false);
			setZoomMode(ImageArea.ZOOM_STRETCH);
			setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		}
		
		@Override
		public void paintComponent(Graphics g) {
			//System.err.println(getSize());
			getContentLayer().setColorScheme(imager.getContentLayer().getColorScheme());
			updateTransforms();	
			//System.err.println(toDisplay());	
			super.paintComponents(g);
		}
		
		public int getShades() { return shades; }
		
		public void setShades(int n) {
			if(shades == n) return;
			shades = n;
			if(side != Plot.SIDE_UNDEFINED) create();
		}
		
		protected void create() {
			float[][] data = isHorizontal() ? getHorizontalData() : getVerticalData();
			
			ImageLayer.Float image = new ImageLayer.Float(data);
			image.defaults();
			image.center();
			image.setSpline();
			image.setRange(new Range(0.0, 1.0));
			
			setContentLayer(image);	
		}
		
		private float[][] getVerticalData() {	
			float[][] data = new float[1][shades];
			for(int j=shades; --j >=0; ) data[0][j] = (float) j/(shades-1);
			return data;
		}
		
		private float[][] getHorizontalData() {	
			float[][] data = new float[shades][1];
			for(int j=shades; --j >=0; ) data[j][0] = (float) j/(shades-1);
			return data;
		}
		
		@Override
		public Dimension getPreferredSize() {
			if(isVertical()) return new Dimension(width, 2);
			else if(isHorizontal()) return new Dimension(2, width);
			else return super.getPreferredSize();
		}
		
		public void setWidth(int pixels) { this.width = pixels; }
		
		public void invert() {
			if(isHorizontal()) invertAxes(true, false);
			else if(isVertical()) invertAxes(false, true);
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
					(double) pos.getX() / getWidth() :
					1.0 - (double) pos.getY() / getHeight();
					
			return range.min() + frac * range.span();
		}	
	}
	
	private static int defaultWidth = 20;
	private static int defaultShades = 256;
	

}

