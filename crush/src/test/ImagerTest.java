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
package test;

import java.awt.Graphics;

import crush.*;
import crush.sourcemodel.*;
import crush.gui.*;
import crush.gui.colorscheme.*;

import java.awt.*;
import javax.swing.*;

public class ImagerTest {

	public static void main(String[] args) {
		try {
			Instrument<?> instrument = new GenericInstrument("generic");
			AstroMap map = new AstroMap(args[0], instrument);
			
			
			final AstroImager imager = new AstroImager(map);
			imager.invertAxes(false, false);
			imager.colorScheme = new CameraLike();
			
			ColorBar colorbar = new ColorBar(imager, ColorBar.VERTICAL, 20);
			
			final JComponent cross = new JComponent() {
				public void paintComponent(Graphics g) {
					g.setColor(Color.RED);
					
					int width = getWidth();
					int height = getHeight();
					
					g.drawLine(0, height/2, width, height/2);
					g.drawLine(width/2, 0, width/2, height);
				}
			};
		
			//imager.setTransparent(true);
			
			JComponent root = new JComponent() {
				public void paintComponent(Graphics g) {
					imager.setSize(getSize());
					cross.setSize(getSize());
					super.paintComponent(g);
				}
			};
			
			
			root.add(imager);
			root.add(cross, 0);
			
			JFrame frame = new JFrame();
			frame.setSize(600, 600);
			frame.add(colorbar, "East");
			frame.add(root);
			
			frame.setVisible(true);
		}
		catch(Exception e) {
			e.printStackTrace();			
		}
	}
	
}
