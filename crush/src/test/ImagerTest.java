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

import java.awt.*;
import java.awt.event.*;

import crush.*;
import crush.astro.AstroMap;
import crush.gui.*;

import javax.swing.*;

import util.Unit;
import util.Vector2D;
import util.plot.ImageLayer;
import util.plot.PlotLabel;
import util.plot.ImageArea;
import util.plot.colorscheme.*;

public class ImagerTest {

	public static void main(String[] args) {
		try {
			Instrument<?> instrument = new GenericInstrument("generic");
			AstroMap map = new AstroMap("/home/pumukli/data/sharc2/images/VESTA.8293.fits", instrument);
			
			float[][] data = new float[10][10];
			for(int i=data.length; --i >= 0; ) for(int j=data[0].length; --j >= 0; )
				data[i][j] = (float) Math.random();
			
			
			GridImageLayer image = new GridImageLayer(map);
			final ImageArea<GridImageLayer> imager = new ImageArea<GridImageLayer>();
			
			//ImageLayer image = new ImageLayer.Float(data);
			//image.defaults();
			//final ImageArea<ImageLayer> imager = new ImageArea<ImageLayer>();
			
			
			image.setColorScheme(new Colorful());
			imager.setContentLayer(image);
			//imager.invertAxes(false, false);
			
			//ColorBar colorbar = new ColorBar(imager, ColorBar.VERTICAL, 20);
			final PlotLabel plotLabel = new PlotLabel("Test Label") {
				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				@Override
				public Vector2D getPosition() {
					return new Vector2D(0.5 * getWidth(), 0.5 * getHeight());
				}
			};
			
			plotLabel.setHorizontalTextAlign(PlotLabel.ALIGN_CENTER);
			plotLabel.setVerticalTextAlign(PlotLabel.ALIGN_MIDRISE);
			plotLabel.setRotation(45.0 * Unit.deg);
			
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
					// Set sizes of all subcomponents to make sure they are the same...
					imager.setSize(getSize());
					cross.setSize(getSize());
					plotLabel.setSize(getSize());
					
					// Set before rendering, otherwise not guaranteed
					setComponentZOrder(plotLabel, 0);
					setComponentZOrder(cross, 1);
					setComponentZOrder(imager, 2);
					
					//Turn on/off subcomponent visibility...
					//imager.setVisible(false);
					
					super.paintComponent(g);
				}
			};
			
			root.add(plotLabel);
			root.add(cross);
			root.add(imager);
			
			/*
			root.setComponentZOrder(label, 0);
			root.setComponentZOrder(cross, 1);
			root.setComponentZOrder(imager, 2);
			*/
			
			JFrame frame = new JFrame();
			frame.setSize(600, 600);
			

			frame.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					System.err.println();
					System.err.println();
					System.exit(0);
				}
			});	
			
			//Box box = Box.createHorizontalBox();
			//box.add(colorbar);
			//box.add(label);	
			
			frame.add(root, "Center");
			//frame.add(box, "East");
		
			frame.setVisible(true);
		}
		catch(Exception e) {
			e.printStackTrace();			
		}
	}
	
}
