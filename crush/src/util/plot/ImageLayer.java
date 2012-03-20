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

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;


import util.*;
import util.plot.colorscheme.GreyScale;

public abstract class ImageLayer extends ContentLayer implements Transforming {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1451020511179557736L;
	
	
	BufferedImage buffer;
	int interpolationType = AffineTransformOp.TYPE_NEAREST_NEIGHBOR;
	public Vector2D referenceIndex = new Vector2D();
	public CoordinateSystem coordinateSystem;
	public ColorScheme colorScheme = new GreyScale();
	public Range range;

	public boolean verbose = false;
	
	public int i0 = 0, j0 = 0;	// The subarray offset to image
	
	private AffineTransform toCoordinates = new AffineTransform();
	private AffineTransform toIndex = new AffineTransform();

	public abstract Dimension getArraySize();
	
	public abstract double getValue(int i, int j);

	public AffineTransform getTransform() {
		return toCoordinates;
	}
	
	protected double getScaled(double value) {
		return (value - range.min) / range.span();
	}
	
	public int getRGB(double value) {
		return Double.isNaN(value) ? colorScheme.noData : colorScheme.getRGB(getScaled(value));
	}
	
	public void createBuffer(int width, int height) {
		buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	}

	
	protected void drawImage(Graphics g) {	
		Graphics2D g2 = (Graphics2D) g;
		AffineTransformOp op = new AffineTransformOp(plotArea.toDisplay, interpolationType);
		g2.drawImage(buffer, op, 0, 0);				
	}
	
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);		
		drawImage(g);
	}
	
	public void updateBuffer() {	
		for(int i=buffer.getWidth(); --i >= 0; ) for(int j=buffer.getHeight(); --j >= 0; ) 
			buffer.setRGB(i, j, getRGB(getValue(i, j)));
	}
	
	public Range getDataRange() {
		Range range = new Range();
		for(int i=buffer.getWidth(); --i >=0; ) for(int j=buffer.getHeight(); --j >=0; ) {
			final double value = getValue(i, j);
			if(!Double.isNaN(value)) range.include(value);
		}
		return range;	
	}
	
	public void autoscale() {
		range = getDataRange();
		if(verbose) System.err.println("Setting scale to " + range);
	}
	
	public void setSubarray(int fromi, int fromj, int toi, int toj) {
		referenceIndex.decrementX(fromi - i0);
		referenceIndex.decrementY(fromj - j0); 
		i0 = fromi;
		j0 = fromj;
		createBuffer(toi - fromi, toj - fromj);
		if(verbose) System.err.println("Selecting " + fromi + "," + fromj + " -- " + toi + "," + toj);	
	}
	
	@Override
	public void defaults() {
		setFullArray();
		autoscale();
	}
		
	public void setFullArray() {
		Dimension size = getArraySize();
		setSubarray(0, 0, size.width, size.height);
	}	
	
	public void setCoordinateTransform(AffineTransform transform) throws NoninvertibleTransformException  {
		toCoordinates = transform;
		toIndex = transform.createInverse();
	}
	
	public Point2D toCoordinates(Point2D point) {
		return toCoordinates.transform(point, point);
	}

	public Point2D toIndex(Point2D point) {
		return toIndex.transform(point, point);
	}	
	
	@Override
	public Vector2D getReferencePoint() {
		return new Vector2D(0.5 * buffer.getWidth(), 0.5 * buffer.getHeight());
	}

	@Override
	public Vector2D getPlotRanges() {
		Point2D c1 = new Point2D.Double(0, 0);
		Point2D c2 = new Point2D.Double(buffer.getWidth(), buffer.getHeight());
		toCoordinates.transform(c1, c1);
		toCoordinates.transform(c2, c2);
		return new Vector2D(Math.abs(c2.getX() - c1.getX()), Math.abs(c2.getY() - c1.getY()));
	}

}
