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

package crush.gui;

import java.awt.*;

import util.*;

public class ColorBar extends FloatArrayImager {
	/**
	 * 
	 */
	private static final long serialVersionUID = 460851913543807978L;
	
	ScaleImager imager;
	int direction = VERTICAL;
	public static int defaultShades = 512;
	int width;
	
	public ColorBar(ScaleImager imager, int dir, int width) { this(imager, dir, width, defaultShades); }
	
	public ColorBar(ScaleImager imager, int dir, int width, int shades) {
		super(new float[1][shades]);
		this.imager = imager;
		this.direction = dir;
		this.width = width;
		
		setBorder(Math.max(1, imager.border));
		scale = new Scale(0.0, 1.0);
		
		setPadding(Math.min(5, imager.padHorizontal), imager.padVertical);		
		setRotation(0.0);
		
		for(int j=data[0].length, height=j; --j >=0; ) data[0][j] = (float) j/(height-1);	
	}

	@Override
	public void setPadding(int x, int y) {
		super.setPadding(x, y);
		Dimension size = null;
		if(direction == HORIZONTAL) size = new Dimension(1, width + 2 * padVertical);
		else if(direction == VERTICAL) size = new Dimension(width + 2 * padHorizontal, 1);	
		setMinimumSize(size);
		setPreferredSize(size);
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
		colorScheme = imager.colorScheme;
		super.paintComponent(g);
	}
	
	public static final int HORIZONTAL = 0;
	public static final int VERTICAL = 1;

}

