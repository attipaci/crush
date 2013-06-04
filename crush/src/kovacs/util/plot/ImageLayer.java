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

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;

import kovacs.util.*;
import kovacs.util.data.Index2D;
import kovacs.util.plot.colorscheme.GreyScale;



public abstract class ImageLayer extends ContentLayer {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1451020511179557736L;
	
	private BufferedImage buffer;
	private int interpolationType = AffineTransformOp.TYPE_NEAREST_NEIGHBOR;
	private CoordinateSystem coordinateSystem;
	private ColorScheme colorScheme = new GreyScale();
	private Range range;
	private boolean verbose = false;	
	private Index2D startIndex = new Index2D();	// The subarray offset to image
	
	private AffineTransform indexToCoords = new AffineTransform();
	private AffineTransform coordsToIndex = new AffineTransform();
	
	public abstract Dimension getDataSize();
	
	public final Dimension getBufferSize() { 
		return new Dimension(getBufferedImage().getWidth(), getBufferedImage().getHeight());
	}
	
	public abstract double getValue(int i, int j);

	public AffineTransform indexToCoords() {
		return indexToCoords;
	}
	
	public AffineTransform coordsToIndex() {
		return coordsToIndex;
	}
	
	protected double getScaled(double value) {
		return (value - range.min()) / range.span();
	}
	
	public int getRGB(double value) {
		return java.lang.Double.isNaN(value) ? colorScheme.noData : colorScheme.getRGB(getScaled(value));
	}
	
	public void createBuffer(int width, int height) {
		buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	}

	
	protected void drawImage(Graphics g) {	
		Graphics2D g2 = (Graphics2D) g;
		
		if(getContentArea() == null) System.err.println("!!! content area"); 
		if(getContentArea().toDisplay() == null) System.err.println("!!! transform"); 
		
		AffineTransform indexToDisplay = new AffineTransform(getContentArea().toDisplay());
		indexToDisplay.concatenate(indexToCoords());
		AffineTransformOp op = new AffineTransformOp(indexToDisplay, interpolationType);
		g2.drawImage(buffer, op, 0, 0);				
	}
	
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		//System.err.println("### layer " + getSize());
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
			if(!java.lang.Double.isNaN(value)) range.include(value);
		}
		return range;	
	}
	
	public void autoscale() {
		range = getDataRange();
		if(verbose) System.err.println("Setting scale to " + range);
	}
	
	public void setSubarray(int fromi, int fromj, int toi, int toj) {
		startIndex.set(fromi, fromj);
		createBuffer(toi - fromi, toj - fromj);
		if(verbose) System.err.println("Selecting " + fromi + "," + fromj + " -- " + toi + "," + toj);	
	}
	
	@Override
	public void defaults() {
		setFullArray();
		autoscale();
	}
		
	public void setFullArray() {
		Dimension size = getDataSize();
		setSubarray(0, 0, size.width, size.height);
	}	
	
	public void setCoordinateTransform(AffineTransform transform) throws NoninvertibleTransformException  {
		indexToCoords = transform;
		coordsToIndex = transform.createInverse();
	}
	
	public Point2D indexToCoordinates(Point2D point) {
		return indexToCoords.transform(point, point);
	}

	public Point2D coordinatesToIndex(Point2D point) {
		return coordsToIndex.transform(point, point);
	}	
	
	@Override
	public Rectangle2D getCoordinateBounds() {	
		Point2D lb = indexToCoordinates(new Point2D.Double(0, buffer.getHeight()));
		Point2D lt = indexToCoordinates(new Point2D.Double(0, 0));
		Point2D rb = indexToCoordinates(new Point2D.Double(buffer.getWidth(), buffer.getHeight()));
		Point2D rt = indexToCoordinates(new Point2D.Double(buffer.getWidth(), 0));
		
		double minX = Math.min(lt.getX(), lb.getX());
		double maxX = Math.max(rt.getX(), rb.getX());
		double minY = Math.min(lb.getY(), rb.getY());
		double maxY = Math.max(lt.getY(), rt.getY());
		
		if(minX > maxX) { double temp = minX; minX = maxX; maxX = temp; }	
		if(minY > maxY) { double temp = minY; minY = maxY; maxY = temp; }
		
		return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
	}

	
	public BufferedImage getBufferedImage() { return buffer; }
	
	public void setBufferedImage(BufferedImage im) { this.buffer = im; }
	
	public int getInterpolationType() { return interpolationType; }
	
	public void setInterpolationType(int value) { interpolationType = value; }
	
	public void setPixelized() { setInterpolationType(AffineTransformOp.TYPE_NEAREST_NEIGHBOR); }

	public void setSpline() { setInterpolationType(AffineTransformOp.TYPE_BICUBIC); }
	
	public CoordinateSystem getCoordinateSystem() { return coordinateSystem; }
	
	public void setCoordinateSystem(CoordinateSystem c) { coordinateSystem = c; }
	
	public ColorScheme getColorScheme() { return colorScheme; }
	
	public void setColorScheme(ColorScheme scheme) { this.colorScheme = scheme; }
	
	public Range getRange() { return range; }
	
	public void setRange(Range r) { this.range = r; }
	
	public boolean isVerbose() { return verbose; }
	
	public void setVerbose(boolean value) { verbose = value; }
	
	public Index2D getSubarrayOffset() { return startIndex; }
	
	public void setSubarrayOffset(Index2D index) { this.startIndex = index; }
	
	public void setSubarrayOffset(int i, int j) { startIndex.set(i,  j); }
	

	public static class Double extends ImageLayer {
		/**
		 * 
		 */
		private static final long serialVersionUID = 6276800154911203125L;
		private double[][] data;
		
		public Double() {}
		
		public Double(double[][] data) {
			setData(data);
		}
		
		public double[][] getData() { return data; }
		
		public void setData(double[][] data) {
			this.data = data;
			defaults();
		}
		
		@Override
		public Dimension getDataSize() {
			return new Dimension(data.length, data[0].length);
		}

		@Override
		public double getValue(int i, int j) {
			return data[i + getSubarrayOffset().i()][j + getSubarrayOffset().j()];
		}

		@Override
		public void initialize() {
			updateBuffer();
		}
	}
	
	
	public static class Float extends ImageLayer {
		/**
		 * 
		 */
		private static final long serialVersionUID = -341608880761068245L;
		private float[][] data;
		
		public Float() {}
		
		public Float(float[][] data) {
			setData(data);
		}
		
		public float[][] getData() { return data; }
		
		public void setData(float[][] data) {
			this.data = data;
			defaults();
		}
		
		@Override
		public Dimension getDataSize() {
			return new Dimension(data.length, data[0].length);
		}

		@Override
		public double getValue(int i, int j) {
			return data[i + getSubarrayOffset().i()][j + getSubarrayOffset().j()];
		}

		@Override
		public void initialize() {
			updateBuffer();
		}

	}

}
