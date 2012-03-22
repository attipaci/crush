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

import util.Range;

public class ColorBar extends ImageArea<ImageLayer> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 460851913543807978L;
	
	private PlotArea<? extends ImageLayer> imager;
	private int orientation = VERTICAL;
	
	private int shades;
	private int width;
	
	public ColorBar(PlotArea<? extends ImageLayer> imager, int dir) { 
		this(imager, dir, defaultWidth, defaultShades);
	}
	
	public ColorBar(PlotArea<? extends ImageLayer> imager, int dir, int width, int shades) {
		this.imager = imager;
		this.orientation = dir;
		this.width = width;
		setShades(shades);
		setRotation(0.0);
		setTransparent(false);
		setZoomMode(ImageArea.ZOOM_STRETCH);
	}
	
	public int getShades() { return shades; }
	
	public void setShades(int n) {
		this.shades = n;
		create();
	}
	
	public int getOrientation() { return orientation; }
	
	public void setOrientation(int value) { orientation = value; }
	
	private void create() {	
		float[][] data = new float[1][shades];
		for(int j=shades; --j >=0; ) data[0][j] = (float) j/(shades-1);
		
		ImageLayer.Float array = new ImageLayer.Float(data);
		array.defaults();
		array.setSpline();
		array.setRange(new Range(0.0, 1.0));
		
		setContentLayer(array);	
	}
	
	@Override
	public Dimension getPreferredSize() {
		if(orientation == VERTICAL) return new Dimension(width, 100);
		return new Dimension(100, width);
	}
	
	public void setWidth(int pixels) { this.width = pixels; }
	
	public void invert() {
		if(orientation == HORIZONTAL) invertAxes(true, false);
		else if(orientation == VERTICAL) invertAxes(false, true);
	}
	
	@Override
	public void setRotation(double angle) {
		double prerotate = orientation == HORIZONTAL ? 0.5 * Math.PI : 0.0;
		super.setRotation(prerotate + angle);		
	}
	
	@Override 
	public void setRenderSize(int width, int height) {
		if(orientation == HORIZONTAL) super.setRenderSize(height, width);
		else super.setRenderSize(width, height);
	}
	
	@Override
	public void paintComponent(Graphics g) {	
		getContentLayer().setColorScheme(imager.getContentLayer().getColorScheme());
		
		super.paintComponent(g);
		
		g.drawLine(0, 0, getWidth(), getHeight());
		g.drawLine(0, getHeight(), getWidth(), 0);
	}
	
	public static final int HORIZONTAL = 0;
	public static final int VERTICAL = 1;

	private static int defaultWidth = 20;
	private static int defaultShades = 256;
	

}

