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

import util.Scale;

import java.util.*;

public class Ruler extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 228962325800810360L;
	Scale scale;
	Font font = new Font("Monospaced", Font.PLAIN, 10);
	
	int width = 50;

	private int height;

	public Ruler() {
		scale = new Scale();
		setPreferredSize(new Dimension(width, 1));
		setBackground(Color.WHITE);

		//Border border = BorderFactory.createEtchedBorder();
		//setBorder(border);
	}

	public void setScale(double min, double max) {
		setRuler(new Scale(min, max));
	}

	public void setRuler(Scale setscale) {
		scale=setscale;
		repaint();
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

		height = getHeight();
	
		int keepEvery=1;
		double smalldiv=(height/(10*scale.range/scale.bigdiv));
		if(smalldiv < 5 && smalldiv > 2.5) keepEvery = 2; 
		if(smalldiv <= 2.5 ) keepEvery=5;
		
		Vector<Double> divs = scale.smallDivisions;
		
		for(int i=0; i<divs.size(); i += keepEvery) {
			int y = getY(divs.get(i));
			g.drawLine(0, y, 2, y);
		}
		
		divs = scale.bigDivisions;

		for(int i=0; i<divs.size(); i++) {
			double level = divs.get(i);
			int y = getY(level);
			g.drawLine(0, y, 8, y);
			g.drawString(format(level), 10, y + fontheight / 2);
		}	

	}
	
	public int getY(double level) {
		return (int) Math.round(height * (1.0 - scale.getScaled(level)));
	}
	
	public double getValue(int y) {
		double f = 1.0 - 1.0 * y / getHeight();
		return f;
	}
		
	public String format(double level) {
		double L = Math.abs(level);
		if(L == 0.0) return i1.format(level);
		if(L < 10000.0 && L >= 10.0) return i1.format(level);
		if(L >= 10000.0) e2.format(level);
		if(L < 10.0 && L >= 0.1) return f1.format(level);
		if(L < 0.1 && L >= 0.01) return f2.format(level);
		if(L < 0.01 && L >= 0.001) return f3.format(level);
		else return e1.format(level);
	}


	final static DecimalFormat e1 = new DecimalFormat("0.0E0");
	final static DecimalFormat e2 = new DecimalFormat("0.00E0");
	final static DecimalFormat i1 = new DecimalFormat("0");
	final static DecimalFormat f1 = new DecimalFormat("0.0");    
	final static DecimalFormat f2 = new DecimalFormat("0.00");
	final static DecimalFormat f3 = new DecimalFormat("0.000");
}
