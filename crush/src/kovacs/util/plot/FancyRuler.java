/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of kovacs.util.
 * 
 *     kovacs.util is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     kovacs.util is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with kovacs.util.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
package kovacs.util.plot;

import java.awt.Font;
import java.awt.Graphics;
import java.text.NumberFormat;
import java.util.ArrayList;

import javax.swing.BoxLayout;
import javax.swing.JComponent;

import kovacs.util.Util;


public abstract class FancyRuler extends BasicRuler implements Arrangeable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 132926720323714435L;
	
	private Labels labels;
	private Title title;
	
	public FancyRuler(int edge) {
		super(edge);		
		labels = new Labels();
		title = new Title();
	}
	
	public Labels getLabels() { return labels; }
	
	public Title getTitle() { return title; }
	
	public void setNumberFormat(NumberFormat nf) {
		labels.setFormat(nf);
	}
	
	public void autoFormat() {
		labels.setFormat(null);
	}
	
	@Override
	public void setSide(int edge) {
		super.setSide(edge);
		
		BoxLayout layout = null;
		if(edge == Plot.TOP_SIDE || edge == Plot.BOTTOM_SIDE) layout = new BoxLayout(this, BoxLayout.Y_AXIS);
		else layout = new BoxLayout(this, BoxLayout.X_AXIS);
		setLayout(layout);	
		
		arrange();
	}
	
	public void arrange() {
		this.removeAll();
		
		int edge = getSide();
		
		if(edge == Plot.BOTTOM_SIDE || edge == Plot.RIGHT_SIDE) {
			add(getMarks());
			if(labels != null) add(labels);
			if(title != null) add(title);			
		}
		else if(edge == Plot.TOP_SIDE || edge == Plot.LEFT_SIDE) {
			if(title != null) add(title);
			if(labels != null) add(labels);
			add(getMarks());
		}			
	}
	
	
	public class Labels extends JComponent {
		/**
		 * 
		 */
		private static final long serialVersionUID = 6716865385705990104L;
		private NumberFormat nf;	// null if automatic...
		private double rotation = 0.0;
		private double delta = Double.NaN;
		
		public String format(double level) {
			setFont(defaultDivisionFont);
			
			if(nf == null) {
				if(Double.isNaN(delta)) calcDelta();
				return Util.getDecimalFormat(level / delta).format(level);
			}
			else return nf.format(level);
		}
		
		public void setRotation(double theta) { 
			this.rotation = theta; 
			setPreferredSize();
		}
		
		public double getRotation() { return rotation; }
		
		public void setFormat(NumberFormat nf) { 
			this.nf = nf; 
			setPreferredSize();
		}
		
		public NumberFormat getFormat() { return nf; }
		
		public void setPreferredSize() {
			Graphics g = getGraphics();
			// TODO
		}
		
		protected void calcDelta() {
			delta = Double.POSITIVE_INFINITY;
			final ArrayList<Double> divs = getMainDivisions().getDivisions();
			double last = divs.get(divs.size() - 1);
			for(int i=divs.size()-1; --i >= 0; ) {
				final double current = divs.get(i);
				final double d = Math.abs(last - current);
				if(d > 0.0) if(d < delta) delta = d;
				last = current;	
			}
		}
		
		
	}
	
	public class Title extends JComponent {
		/**
		 * 
		 */
		private static final long serialVersionUID = -2340066081374973607L;
		SimpleLabel simpleLabel;
		
		public Title() {
			simpleLabel.setFont(defaultTitleFont);
		}
		
		public void setTitle(String value) { 
			simpleLabel.setText(value); 
			//setPreferredSize();
		}
		
		@Override
		public void setFont(Font f) {
			simpleLabel.setFont(f);
			//setPreferredSize();
		}
		
		
		
		@Override
		public Font getFont() { return simpleLabel.getFont(); }
		
		public String getTitle() { return simpleLabel.getText(); }	
		
	}
	
	public final static Font defaultDivisionFont = new Font("Monospaced", Font.PLAIN, 10);
	public final static Font defaultTitleFont = new Font("Serif", Font.BOLD | Font.ITALIC, 12);


}
