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


import java.awt.Color;
import java.awt.Component;

import javax.swing.BoxLayout;
import javax.swing.JComponent;

public class PlotSidePane extends PlotPane implements PlotSide {
	/**
	 * 
	 */
	private static final long serialVersionUID = 940220609694011545L;
	private int side = Plot.SIDE_UNDEFINED;

	private PlotSideRuler ruler;
	private JComponent center, far;
	
	public PlotSidePane(Plot<?> plot, int side) {
		super(plot);
		ruler = new PlotSideRuler(plot, side);
		setSide(side);
	}
	
	public int getSide() { return side; }
	
	public void setSide(int side) {
		if(this.side == side) return;
		
		this.side = side;
		
		ruler.setSide(side);
		if(center != null) if(center instanceof PlotSide) ((PlotSide) center).setSide(side);
		if(far != null) if(far instanceof PlotSide) ((PlotSide) far).setSide(side);
		
		if(isHorizontal()) setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		else if(isVertical()) setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		
		arrange();
	}
	
	private void arrange() {
		removeAll();
		
		if(side == Plot.TOP_SIDE || side == Plot.LEFT_SIDE) {
			if(far != null) add(far);
			if(center != null) add(center);
			if(ruler != null) add(ruler);
		}
		else if(side == Plot.BOTTOM_SIDE || side == Plot.RIGHT_SIDE) {
			if(ruler != null) add(ruler);
			if(center != null) add(center);
			if(far != null) add(far);
		}
	}
	
	/*
	@Override
	public void setSize(int w, int h) {
		super.setSize(w, h);
		
		if(isHorizontal()) for(Component c : getComponents()) 
			c.setSize(c.getPreferredSize().width, h);
		else if(isVertical()) for(Component c : getComponents()) 
			c.setSize(w, c.getPreferredSize().height);
	}
	*/
	
	public JComponent getCenter() { return center; }
	
	public JComponent setCenter(JComponent c) {
		JComponent old = this.center;
		this.center = c;
		c.setBackground(getBackground());
		if(c instanceof PlotSide) ((PlotSide) c).setSide(getSide());
		arrange();
		return old;		
	}
	
	public JComponent getFar() { return far; }
	
	public JComponent setFar(JComponent c) {
		JComponent old = this.far;
		this.far = c;
		c.setBackground(getBackground());
		if(c instanceof PlotSide) ((PlotSide) c).setSide(getSide());
		arrange();
		return old;		
	}
	
	public boolean isHorizontal() { return side == Plot.TOP_SIDE || side == Plot.BOTTOM_SIDE; }
	
	public boolean isVertical() { return side == Plot.LEFT_SIDE || side == Plot.RIGHT_SIDE; }

	@Override
	public void setBackground(Color color) {
		super.setBackground(color);
		for(Component c : getComponents()) c.setBackground(color);
	}
	
}
