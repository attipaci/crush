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


import util.*;

public class ColorBar extends PlotArea<FloatImageLayer> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 460851913543807978L;
	
	PlotArea<? extends ImageLayer> imager;
	int direction = VERTICAL;
	public static int defaultShades = 512;
	int width;
	
	public ColorBar(PlotArea<? extends ImageLayer> imager, int dir, int width) { this(imager, dir, width, defaultShades); }
	
	public ColorBar(PlotArea<? extends ImageLayer> imager, int dir, int width, int shades) {
		
		FloatImageLayer bar = new FloatImageLayer(new float[1][shades]);
		setContentLayer(bar);
		this.imager = imager;
		this.direction = dir;
		this.width = width;
		
		//setBorder(Math.max(1, imager.border));
		bar.scale = new Scale(0.0, 1.0);
		
		setRotation(0.0);
		
		for(int j=bar.data[0].length, height=j; --j >=0; ) bar.data[0][j] = (float) j/(height-1);	
	}
	
	public void invert() {
		if(direction == HORIZONTAL) invertAxes(true, false);
		else if(direction == VERTICAL) invertAxes(false, true);
	}
	
	@Override
	public void setRotation(double angle) {
		double prerotate = 0.0;
		if(direction == HORIZONTAL) prerotate = 0.5 * Math.PI;
		super.setRotation(prerotate + angle);		
	}
	
	@Override 
	public void setRenderSize(int width, int height) {
		if(direction == HORIZONTAL) super.setRenderSize(height, width);
		else super.setRenderSize(width, height);
	}
	
	@Override
	public void paintComponent(Graphics g) {
		getContentLayer().colorScheme = imager.getContentLayer().colorScheme;
		super.paintComponent(g);
	}
	
	public static final int HORIZONTAL = 0;
	public static final int VERTICAL = 1;

}

