/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Stroke;

import javax.swing.JComponent;
import javax.swing.JPanel;

// TODO dragging boundaries to adjust component sizes?

public class Plot<ContentType extends ContentLayer> extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1434685464605442072L;
	
	private PlotArea<? extends ContentType> plotArea;

	public PlotPane center, left, right, top, bottom;
	public PlotPane topLeft, topRight, bottomLeft, bottomRight;
	
	private Stroke stroke;
	
	// containers for each sub-panel...
	// getComponentAt/findComponentAt --> the top-most visible component at the position.
	// validate()? (does layout, but how is it different from paintComponent?...)
	
	// TODO
	// constructors:
	//   Plot(float[][])
	//   Plot(double[][])
	//   Plot(Data2D)
	//   Plot(GridImage<?>)
	//   ...
	
	// Top:
	//  * Title
	//  * AxisLabel
	//  * Ruler (adjustable)
	
	// Bottom:
	//	* Ruler (adjustable)
	//	* AxisLabel
	//  * (ColorBar.Vertical, ScaleBar, AxisLabel)
	//  * (...)
	
	// Left:
	//	* AxisLabel
	//  * Ruler (adjustable)
	
	// Right:
	//	* Ruler (adjustable)
	//  * AxisLabel
	//  * (ColorBar.Horizontal, ...)
	//  * (...)
	
	GridBagLayout layout = new GridBagLayout();
	
	public Plot() {
		setLayout(layout);

		// The central plot area
		center = new PlotPane(this);
		add(center, 1, 1, GridBagConstraints.BOTH, 1.0, 1.0);
	
		// The sides...
		left = new PlotPane(this);
		add(left, 0, 1, GridBagConstraints.VERTICAL, 0.0, 0.0);
					
		right = new PlotPane(this);
		add(right, 2, 1, GridBagConstraints.VERTICAL, 0.0, 0.0);
		
		top = new PlotPane(this);
		add(top, 1, 0, GridBagConstraints.HORIZONTAL, 0.0, 0.0);
		
		bottom = new PlotPane(this);
		add(bottom, 1, 2, GridBagConstraints.HORIZONTAL, 0.0, 0.0);
	
		// The corners...
		topLeft = new PlotPane(this);
		add(topLeft, 0, 0, GridBagConstraints.BOTH, 0.0, 0.0);	
		
		topRight = new PlotPane(this);
		add(topRight, 2, 0, GridBagConstraints.BOTH, 0.0, 0.0);
		
		bottomLeft = new PlotPane(this);
		add(bottomLeft, 0, 2, GridBagConstraints.BOTH, 0.0, 0.0);
	
		bottomRight = new PlotPane(this);
		add(bottomRight, 2, 2, GridBagConstraints.BOTH, 0.0, 0.0);
		
		defaults();
	}

	public PlotPane getCenterPane() { return center; }
	
	public PlotPane getLeftane() { return left; }
	
	public PlotPane getRightPane() { return right; }
	
	public PlotPane getTopPane() { return top; }
	
	public PlotPane getBottomPane() { return bottom; }
	
	public PlotPane getTopLeftPane() { return topLeft; }
	
	public PlotPane getTopRightPane() { return topRight; }
	
	public PlotPane getBottomLeftPane() { return bottomLeft; }
	
	public PlotPane getBottomRightPane() { return bottomRight; }
	
	
	
	public void defaults() {
		setFont(defaultFont);
	}
	
	
	public void setTransparent(boolean value) {
		for(Component c : getComponents()) if(c instanceof PlotPane) 
			((PlotPane) c).setTransparent(value);
	}
	
	@Override
	public void paintComponent(Graphics g) {
		validate();
		plotArea.setSize(center.getSize());
		super.paintComponent(g);
	}
	
	public PlotArea<? extends ContentType> getPlotArea() { return plotArea; }
	
	public void setPlotArea(PlotArea<? extends ContentType> area) { 
		if(this.plotArea != null) center.remove(this.plotArea);
		this.plotArea = area; 
		center.add(area);
	}
	
	private void add(JComponent component, int x, int y, int fill, double weightx, double weighty) {	
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = weightx;
        c.weighty = weighty;
        c.gridx = x;
        c.gridy = y;
        c.fill = fill;
        layout.setConstraints(component, c);
        add(component);
	}
	
	public Stroke getStroke() { return stroke; }

	public void setStroke(Stroke s) { this.stroke = s; }
	
	public float getFontSize() { return getFont().getSize2D(); }
	
	
	public void setFontSize(float size) {
		setFont(getFont().deriveFont(size));
	}
	
	public void setFontBold(boolean value) {
		int style = getFont().getStyle();
		if(value) style |= Font.BOLD;
		else style &= ~Font.BOLD;
		setFont(getFont().deriveFont(style));
	}
	
	public void setFontItalic(boolean value) {
		int style = getFont().getStyle();
		if(value) style |= Font.ITALIC;
		else style &= ~Font.ITALIC;
		setFont(getFont().deriveFont(style));
	}
	
	public boolean isFontItalic() {
		return (getFont().getStyle() & Font.ITALIC) != 0;
	}
	
	public boolean isFontBold() {
		return (getFont().getStyle() & Font.BOLD) != 0;
	}
	
	public static Font defaultFont = new Font("SansSerif", Font.BOLD, 15);
	
}
