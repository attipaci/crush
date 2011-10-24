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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JComponent;
import javax.swing.JPanel;

// TODO dragging boundaries to adjust component sizes?

public class Plot<ContentType extends ContentLayer> extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1434685464605442072L;
	
	PlotArea<? extends ContentType> content;
	
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
	
	public Plot() {
		
	}
	
	private void add(JComponent component, int x, int y, int fill) {
		GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        
        c.gridx = x;
        c.gridy = y;
        c.fill = fill;
        gridbag.setConstraints(component, c);
        add(component);
	}
	
	public void addCenter(JComponent component) {
		add(component, 1, 1, GridBagConstraints.BOTH);
	}
	
	public void addTop(JComponent component) {
		add(component, 1, 0, GridBagConstraints.HORIZONTAL);
	}
	
	public void addBottom(JComponent component) {
		add(component, 1, 2, GridBagConstraints.HORIZONTAL);
	}
	
	public void addLeft(JComponent component) {
		add(component, 0, 1, GridBagConstraints.VERTICAL);
	}
	
	public void addRight(JComponent component) {
		add(component, 2, 1, GridBagConstraints.VERTICAL);
	}
	
	public void addTopLeft(JComponent component) {
		add(component, 0, 0, GridBagConstraints.BOTH);
	}
	
	public void addTopRight(JComponent component) {
		add(component, 2, 0, GridBagConstraints.BOTH);
	}
	
	public void addBottomLeft(JComponent component) {
		add(component, 0, 2, GridBagConstraints.BOTH);
	}
	
	public void addBottomRight(JComponent component) {
		add(component, 0, 2, GridBagConstraints.BOTH);
	}
	
	
}
