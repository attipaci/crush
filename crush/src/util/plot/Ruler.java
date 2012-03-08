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
import java.text.*;
import javax.swing.*;

import util.ScaleDivisions;
import util.Util;

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

public class Ruler extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 228962325800810360L;

		
	PlotArea<?> content;
	ScaleDivisions mainDivisions, subDivisions;
	
	Font font = new Font("Monospaced", Font.PLAIN, 10);

	Stroke divStroke = new BasicStroke();
	Stroke smallStroke = new BasicStroke();
	
	int divLength = 8;
	int subdivLength = 2; 
	
	boolean isLabeled = true;
	NumberFormat nf;	// null if automatic...
	
	private int height;
	private double delta = Double.NaN;


	public Ruler(PlotArea<?> forContent) {
		this.content = forContent;
		
		mainDivisions = new ScaleDivisions();
		subDivisions = new ScaleDivisions(10);
		
		setPreferredSize(new Dimension(width, 1));
		setBackground(Color.WHITE);

		//Border border = BorderFactory.createEtchedBorder();
		//setBorder(border);
	}

	public void setScale(double min, double max) {
		mainDivisions.update(min, max);
		subDivisions.update(min, max);
		// TODO recalculate component minimum size...
	}
	
	public void setNumberFormat(NumberFormat nf) {
		this.nf = nf;
	}
	
	public void autoFormat() {
		nf = null;
	}
	
	@Override
	public void paintComponent(Graphics g){
		//AffineTransform upright = new AffineTransform();
		//upright.rotate(Math.PI/2.0);
		//font = font.deriveFont(upright);
		
		super.paintComponent(g);
		
		FontMetrics fm = g.getFontMetrics(font);
		int fontheight=fm.getHeight();
		setFont(font);

		final ArrayList<Double> subdivs = subDivisions.getDivisions();
		
		height = getHeight();
		final double npix = height/subdivs.size();
		
		// Figure out how many of the subdivisions to actually draw...
		int subStep = 1;
		if(npix < 5 && npix > 2.5) subStep = 2; 
		else if(npix <= 2.5 ) subStep=5;
		
		final Graphics2D g2 = (Graphics2D) g;
		g2.setStroke(smallStroke);
		
		// Draw subdivisions only if they are separated by at least 2 pixels...
		if(npix > 0.4) for(int i=0; i<subdivs.size(); i += subStep) {
			final int y = getY(subdivs.get(i));
			g2.drawLine(0, y, subdivLength, y);
		}
		
		// TODO separate handling of CUSTOM subdivisions....
		
		final ArrayList<Double> divs = mainDivisions.getDivisions();
		g2.setStroke(divStroke);
		
		// Find what decimal resolution is necessary...
		delta = Double.NaN;

		int lastLabelY = -1;
		// TODO calculate what spacing is really necessary given some tilt...
		
		for(double level : divs) {
			int y = getY(level);
			g2.drawLine(0, y, divLength, y);
			
			// TODO use font metrics to calculate proper centering...
			if(isLabeled) if(y - lastLabelY > fontheight) {
				g2.drawString(format(level), 10, y + fontheight / 2);
				lastLabelY = y;
			}
		}	
		

	}

	public int getY(double level) {
		// TODO
		return 1;
	}
	
	public double getValue(int y) {
		double f = 1.0 - 1.0 * y / getHeight();
		return f;
	}
		
	protected void calcDelta() {
		delta = Double.POSITIVE_INFINITY;
		final ArrayList<Double> divs = mainDivisions.getDivisions();
		double last = divs.get(divs.size() - 1);
		for(int i=divs.size()-1; --i >= 0; ) {
			final double current = divs.get(i);
			final double d = Math.abs(last - current);
			if(d > 0.0) if(d < delta) delta = d;
			last = current;	
		}
	}
	
	public String format(double level) {
		if(nf == null) {
			if(Double.isNaN(delta)) calcDelta();
			return Util.getDecimalFormat(level / delta).format(level);
		}
		else return nf.format(level);
	}


}
