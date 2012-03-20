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

import java.awt.Color;
import java.awt.Graphics;

import crush.*;
import crush.astro.AstroMap;
import crush.gui.*;

import javax.swing.*;

import util.plot.AxisLabel;
import util.plot.ColorBar;
import util.plot.ImageArea;
import util.plot.colorscheme.*;

public class ImagerTest {

	public static void main(String[] args) {
		try {
			Instrument<?> instrument = new GenericInstrument("generic");
			AstroMap map = new AstroMap("/home/pumukli/data/sharc2/images/VESTA.8293.fits", instrument);
			
			GridImageLayer image = new GridImageLayer(map);
			image.colorScheme = new Colorful();
			
			final ImageArea<GridImageLayer> imager = new ImageArea<GridImageLayer>();
			imager.setContentLayer(image);
			//imager.invertAxes(false, false);
			
			//ColorBar colorbar = new ColorBar(imager, ColorBar.VERTICAL, 20);
			AxisLabel label = new AxisLabel(map.unit.name, ColorBar.VERTICAL);
			
			final JComponent cross = new JComponent() {
				/**
				 * 
				 */
				private static final long serialVersionUID = 6303247317754045021L;

				@Override
				public void paintComponent(Graphics g) {
					g.setColor(Color.RED);
					
					int width = getWidth();
					int height = getHeight();
					
					g.drawLine(0, height/2, width, height/2);
					g.drawLine(width/2, 0, width/2, height);
				}
			};
		
			imager.setTransparent(true);
			
			JComponent root = new JComponent() {
				/**
				 * 
				 */
				private static final long serialVersionUID = -3036536847348729404L;

				@Override
				public void paintComponent(Graphics g) {
					imager.setSize(getSize());
					cross.setSize(getSize());
					super.paintComponent(g);
				}
			};
			
			root.add(cross);
			root.add(imager);
			
			JFrame frame = new JFrame();
			frame.setSize(600, 600);
			
			Box box = Box.createHorizontalBox();
			//box.add(colorbar);
			box.add(label);	
			
			frame.add(root, "Center");
			frame.add(box, "East");
		
			frame.setVisible(true);
		}
		catch(Exception e) {
			e.printStackTrace();			
		}
	}
	
}
