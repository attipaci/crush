/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2010 Attila Kovacs 

package crush.devel;

import crush.*;
import crush.astro.AstroImage;
import crush.gui.*;
import crush.sourcemodel.*;
import kovacs.math.Range;
import kovacs.math.SphericalCoordinates;
import kovacs.math.Vector2D;
import kovacs.util.*;
import kovacs.util.data.Region;
import kovacs.util.plot.*;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;


//Display with value/coordinate readout
class MapPanel extends JComponent {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8619921669585536452L;
	
	JComponent root;
	
	ImageArea<GridImageLayer> imager;
	
	Box scaleBox;
	InfoPanel infobar;
	ColorBar colorScale;
	Ruler ruler;
	AxisLabel fluxLabel;
	
	Scale scale;
	
	Vector<ColorScheme> colorSchemes = new Vector<ColorScheme>();
	Vector<Region> regions = new Vector<Region>();
		
	double zoom=1.0;

	//boolean circled = true;
	//boolean masked = false;
	//boolean polygonDrawMode = false;

	public MapPanel(final AstroImage image) {
		// TODO create imager
		
		setMinimumSize(new Dimension(100, 100));
		setSize(200, 100);
		infobar = new InfoPanel(3);
		
		//setBorder(BorderFactory.createLineBorder(Color.black));
		setBackground(Color.LIGHT_GRAY);

		colorScale = new ColorBar(imager, ColorBar.VERTICAL, 20);
		ruler = new Ruler();
		scale = ruler.scale;
		fluxLabel = new AxisLabel(image.unit.name, AxisLabel.RIGHT);

		scaleBox = Box.createHorizontalBox();

		scaleBox.add(colorScale);
		scaleBox.add(ruler);
		scaleBox.add(fluxLabel);
	
		setDefaults();

		addMouseListener(new MouseAdapter() {
			Vector2D center = new Vector2D();

			@Override
			public void mouseEntered(MouseEvent evt) {
				setCursor(new java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR));
			}
			@Override
			public void mouseExited(MouseEvent evt) {
				setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
				infobar.clear();
			}
			
			/*
			public void mousePressed(MouseEvent evt) {			
				if(!polygonDrawMode) if(circled || masked) {
					center.x = getXOffset(evt.getX());
					center.y = getYOffset(evt.getY());			
				}
			}
			*/
			
			/*
			public void mouseReleased(MouseEvent evt) {
				if(!polygonDrawMode) if(circled || masked) if(evt.getButton() == MouseEvent.BUTTON1) {
					double radius = Math.pow(center.x - getXOffset(evt.getX()), 2.0);
					radius += Math.pow(center.y - getYOffset(evt.getY()), 2.0);
					radius = Math.sqrt(radius); 
					regions.add(new GaussianSource(map, center, radius));
					repaint();
				}
			}

			public void mouseClicked(MouseEvent evt) {
				if(polygonDrawMode) {
					center.x = getXOffset(evt.getX());
					center.y = getYOffset(evt.getY());	
					
					Polygon polygon = polygons.get(polygons.size() - 1);
					
					if(evt.getButton() == MouseEvent.BUTTON1) polygon.add(new Vector2D(map.fracIndexOfdX(centerX), map.fracIndexOfdY(centerY)));
					if(evt.getButton() == MouseEvent.BUTTON3) if(polygons.size() > 0) polygon.remove(polygon.size() - 1); 
					
					repaint();
				}
				
				else if(circled || masked) {
					if(evt.getButton() == MouseEvent.BUTTON2) 
						regions.add(new GaussianSource(map, center, map.getImageFWHM()));
					if(evt.getButton() == MouseEvent.BUTTON3) 
						regions.add(new GaussianSource(map, center, 2.0*map.getImageFWHM()));
					repaint();
				}
			}	
			*/				    
		});
		
		
		addMouseMotionListener(new MouseMotionAdapter() {
			Vector2D offset = new Vector2D();
			SphericalCoordinates coords = (SphericalCoordinates) image.getReference().clone();
			CoordinateSystem offsetSystem = coords.localCoordinateSystem;
			CoordinateAxis xAxis = offsetSystem.get(0);
			CoordinateAxis yAxis = offsetSystem.get(1);
			
			@Override
			public void mouseMoved(MouseEvent evt) {
				xAxis.setFormat(Util.f1);
				yAxis.setFormat(Util.f1);
				
				int i = (int)Math.round(geti(evt.getX()));
				int j = (int)Math.round(getj(evt.getY()));

				offset.x = getXOffset(evt.getX());
				offset.y = getYOffset(evt.getY());

				image.getProjection().deproject(offset, coords);
				offset.scale(1.0 / Unit.arcsec);
				
				infobar.lines[0] = "POS " + coords.toString();
				infobar.lines[1] = xAxis.label + " " + xAxis.format(offset.x) + "  " 
					+ yAxis.label + " " + yAxis.format(offset.y);
				infobar.lines[2] = image.getPixelInfo(i, j);

				
				infobar.repaint();
				
			}
		});
		
		ruler.addMouseListener(new MouseAdapter() {
			
			
			
			public void mouseEntered(MouseEvent evt) {
				setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
				infobar.lines[0] = "You can drag the zero point to change level";
				infobar.lines[1] = "Drag ruler elsewhere to change dynamic range";
				infobar.lines[2] = "Click ruler to toggle linear/log/sqrt scaling";
			}
			
			public void mouseExited(MouseEvent evt) {
				infobar.clear();
			}
			
			public void mouseClicked(MouseEvent evt) {
				if(evt.getButton() == MouseEvent.BUTTON1) {
					if(scale.logarithmic) scale.sqrt();
					else if(scale.sqrt) scale.linear();
					else scale.log();
					setDefaults();
					repaint(); 
				}
				
			}
			
			public void mousePressed(MouseEvent evt) {		
				fromScale = (Scale) scale.clone();
				fromScaled = ruler.getValue(evt.getY());
				double zeroScaled = scale.getScaled(0.0);
				
				if(Math.abs(fromScaled - zeroScaled) < 0.05) 
					setCursor(new java.awt.Cursor(java.awt.Cursor.MOVE_CURSOR));
				else 
					setCursor(new java.awt.Cursor(java.awt.Cursor.N_RESIZE_CURSOR));
			}
			
			public void mouseReleased(MouseEvent evt) {
				setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
				repaint();
			}
			
		});
		
		ruler.addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseDragged(MouseEvent evt) {
				toScaled = ruler.getValue(evt.getY());

				double zeroScaled = fromScale.getScaled(scale.logarithmic ? 1.0 : 0.0);
				
				scale.min = fromScale.min;
				scale.max = fromScale.max;
				
				if(Math.abs(fromScaled - zeroScaled) < 0.05) {
					offsetScale(fromScaled - toScaled);
				}
				else {
					double stretch = (toScaled - zeroScaled) / (fromScaled - zeroScaled);
					if(stretch < 0.0) {
						fromScaled = 2.0*zeroScaled - fromScaled;
						inverted = !inverted;
						colorScale.invert();
						stretch *= -1;
					}
					expandScale(1.0 / stretch);
				}
					
				ruler.repaint();	
			}
		});
		
		
		
		
		fluxLabel.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent evt) {	
				//if(evt.getClickCount() == 1) return;
				
				int index = units.indexOf(displayUnit);
				index++;
				if(index >= units.size()) index = 0;
				Unit oldUnit = displayUnit;
				displayUnit = units.get(index);
				Scale scale = display.scale;
				scale.setScale(scale.min * oldUnit.value/displayUnit.value, scale.max * oldUnit.value/displayUnit.value);
					
				display.fluxLabel.label = map == s2n ? "S/N" : displayUnit.name;
				
				fluxLabel.repaint();
				ruler.repaint();
			}	
			

			public void mouseEntered(MouseEvent evt) {
				infobar.lines[0] = "Click on label to change unit.";
			}
			
			public void mouseExited(MouseEvent evt) {
				infobar.clear();
			}
			
		});
		
		colorScale.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent evt) {
				int n = evt.getButton();
				
				if(n == MouseEvent.BUTTON1) {
					useScheme=(useScheme+1)%colorSchemes.size();  
					repaint();
				}
				else if(n == MouseEvent.BUTTON3) {
					inverted = !inverted;
					colorScale.invert();
					repaint();
				}
			}	
			
			public void mouseEntered(MouseEvent evt) {
				infobar.lines[0] = "Click mouse to change color scale.";
				infobar.lines[1] = "Right-click to invert colors.";
			}
			
			public void mouseExited(MouseEvent evt) {
				infobar.clear();
			}
			
		});
		
		infobar.addMouseListener(new MouseAdapter() {
			
			public void mouseEntered(MouseEvent evt) {
				infobar.lines[0] = "Press 'h' for help on action keys.";
				infobar.lines[1] = "Press '?' for image summary.";
			}
			
			public void mouseExited(MouseEvent evt) {
				infobar.clear();
			}
			
		});
		
	}	
	

	
	public void setDefaults() {
		Range range = image.getRange();
		range.scale(1.0 / image.unit.value);
		if(scale.logarithmic) scale.setScale(range.min, range.max);
		else scale.setScale(range.min, range.max);				
	}	
	
	private double fromScaled = 0.0, toScaled = 0.0;
	private Scale fromScale;
	


	public void expandScale(double factor) {
		if(scale.logarithmic) scale.setScale(Math.pow(scale.min, factor), Math.pow(scale.max, factor));
		else if(scale.sqrt) scale.setScale(factor*factor * scale.min, factor*factor * scale.max);
		else { scale.setScale(factor*scale.min, factor*scale.max); }
	}
	
	public void offsetScale(double relativeToRange) {
		double offset = scale.range * relativeToRange;
		
		if(scale.logarithmic) scale.setScale(scale.min * Math.pow(10.0, offset), scale.max * Math.pow(10.0, offset));
		else if(scale.sqrt) {
			// range ~ Smax * sqrt(|max|) - Smin * sqrt(|min|);
			// zero ~ -Smin * sqrt(|min|)
			// sqrt(|min|) ---> sqrt(|min|) + offset
			double min = Math.sqrt(Math.abs(scale.min)) - offset;
			double max = Math.sqrt(Math.abs(scale.max)) + offset;
			scale.setScale(Math.signum(scale.min) * min * min, Math.signum(scale.max) * max * max);
		}
		else scale.setScale(scale.min + offset, scale.max + offset);
	}
	

	public void paintComponent(Graphics g) {
		super.paintComponent(g);

		colorScale.colorScheme = colorScheme;
		
		// Repaint the scales
		scaleBox.repaint();
		colorScale.repaint();
		ruler.repaint();

		fluxLabel.label = image.contentType.equals("S/N") ? "S/N" : image.unit.name;
		fluxLabel.repaint();
	}






	Font font = new Font("Monospaced", Font.PLAIN, 10);
	
	/*
	private void maskRegion(Region region) {
		double a = dx * region.radius / delta;
		double b = dy * region.radius / delta;				
		double x = dx * fracIndexOfdX(region.dX);
		double y = imageHeight - dy * fracIndexOfdY(region.dY);
		
		int x0 = (int)Math.round(x - 0.5 * a);
		int y0 = (int)Math.round(y - 0.5 * b);

		graphics.setColor(new Color(1.0F, 1.0F, 1.0F, 0.5F));
		graphics.fillOval(x0, y0, (int)Math.round(a), (int)Math.round(b));
	}
	*/
	
	/*
	private void idRegion(Region region) {
		double a = dx * region.radius / delta;
		double b = dy * region.radius / delta;				
		double x = dx * fracIndexOfdX(region.dX);
		double y = imageHeight - dy * fracIndexOfdY(region.dY);
		
		setFont(font);
		
		graphics.drawString(region.id, (int)Math.round(x + a + 3), (int)Math.round(y - b - 3));
	}
	*/

	/*
	private void circleRegion(Region region) {
		graphics.setColor(((ColorScheme)colorSchemes.get(useScheme)).getHighlight());
		
		double a = dx * 2.0 * region.radius / delta;
		double b = dy * 2.0 * region.radius / delta;					
		double x = dx * (0.5 + fracIndexOfdX(region.dX));
		double y = imageHeight - dy * (0.5 + fracIndexOfdY(region.dY));
		
		int x0 = (int)Math.round(x - 0.5 * a);
		int y0 = (int)Math.round(y - 0.5 * b);
		int xc = (int)Math.round(x);
		int yc = (int)Math.round(y);

		graphics.drawOval(x0, y0, (int)Math.round(a), (int)Math.round(b));
		
		graphics.drawLine(xc - 5, yc, xc + 5, yc);
		graphics.drawLine(xc, yc - 5, xc, yc + 5);
	}
	*/
	
	/*
	private void drawPolygon(Polygon polygon, boolean closed) {
		getGraphics().setColor(((ColorScheme)colorSchemes.get(useScheme)).getHighlight());
		
		int size = closed ? polygon.size() : polygon.size() - 1; 
		
		for(int i=0; i<size; i++) {
			Vector2D from = polygon.get(i);
			Vector2D to = polygon.get((i+1)%polygon.size());
			
			int fromx = (int) Math.round(dx * (0.5 + from.x));
			int fromy = (int) Math.round(imageHeight - dy * (0.5 + from.y));
			int tox = (int) Math.round(dx * (0.5 + to.x));
			int toy = (int) Math.round(imageHeight - dy * (0.5 + to.y));
			
			graphics.drawLine(fromx, fromy, tox, toy);
		}
		
	}
	*/
	
	public double getXOffset()

}
