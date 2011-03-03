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
import javax.swing.*;


public class AxisLabel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3484653302434799694L;
	Font font = new Font("SansSerif", Font.BOLD | Font.ITALIC, 15);
	int axis = 0;
	int thickness = 30;
	String label = "";

	final static int BOTTOM = 0;
	final static int LEFT = 1;
	final static int TOP = 2;
	final static int RIGHT = 3;

	public AxisLabel() { create(); }

	public AxisLabel(String text, int orientation) { 
		label = text;
		axis = orientation;
		create();
	}

	public void create() {
		int width = axis % 2 == 0 ? 1 : thickness;
		int height = axis % 2 == 0 ? thickness : 1;

		setPreferredSize(new Dimension(width, height));
		setBackground(Color.WHITE);
	}


	@Override
	public void paintComponent(Graphics g){
		super.paintComponent(g); 

		double angle = axis * Math.PI / 2.0;

		FontMetrics fm = g.getFontMetrics(font);
		setFont(font);

		int height = getHeight();
		int width = getWidth();

		Graphics2D g2 = (Graphics2D) g;
		Rectangle bounds = font.getStringBounds(label, g2.getFontRenderContext()).getBounds();	

		int x0 = (int)Math.round((getWidth() - bounds.width) / 2.0);
		int y0 = (int)Math.round((getHeight() - bounds.height) / 2.0 + fm.getAscent());

		g2.rotate(angle, width/2.0, height/2.0);	 
		g.drawString(label, x0, y0);
	}
}
