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
package crush.gui;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import javax.swing.*;

public abstract class AbstractImager extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4866760493905479369L;

	BufferedImage buffer;
	
	protected AffineTransform transform, inverseTransform = null;

	private AffineTransform center = new AffineTransform();
	private AffineTransform rescale = new AffineTransform(), rotate = new AffineTransform();
	private AffineTransform flip = new AffineTransform();
	
	int interpolationType = AffineTransformOp.TYPE_NEAREST_NEIGHBOR;
	boolean initialized = false;
	boolean transparent = false;

	public int zoomMode = ZOOM_STRETCH;
	
	protected int border = 0;
	protected int padHorizontal = 0, padVertical = 0;
	
	Color imageBackground = null; 
	Color penColor = Color.BLACK;
	
	public AbstractImager() {
		flip.scale(1.0, -1.0);
		setBackground(Color.WHITE);
	}
	
	/**
	 * Called before the first rendering of the image. Otherwise, it can be called beforehand, allowing
	 * to override the rendering parameters (e.g. rendering size, data scaling, subarray selection etc.)
	 */
	public void defaults() {
		initialized = true;
	}
	
	public void createBuffer(int width, int height) {
		buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		double ic = 0.5 * width;
		double jc = 0.5 * height;
		center = new AffineTransform();
		center.translate(-ic, -jc);
	}
	
	public void setPadding(int x, int y) {
		padHorizontal = Math.max(0, x);
		padVertical = Math.max(0, y);
	}
	
	public void setBorder(int width) {
		border = Math.max(0, width);
	}
	
	public void setRenderSize(int width, int height) {
		System.err.println("Setting render size to " + width + "x" + height);
		rescale = new AffineTransform();
		rescale.scale((double) width / buffer.getWidth(), (double) height / buffer.getHeight());
		zoomMode = ZOOM_FIXED;
	}
	
	public void setZoom(double value) {
		System.err.println("Setting zoom to " + value);
		rescale = new AffineTransform();
		rescale.scale(value, value);
		zoomMode = ZOOM_FIXED;
	}

	public void zoom(double relative) {
		rescale.scale(relative, relative);
		zoomMode = ZOOM_FIXED;
	}
	
	public void fitInside() {
		double zoom = Math.min((double) getVisibleWidth() / buffer.getWidth(), (double) getVisibleHeight() / buffer.getHeight());
		setZoom(zoom);
		zoomMode = ZOOM_FIT;
	}
	
	public void fillInside() {
		double zoom = Math.max((double) getVisibleWidth() / buffer.getWidth(), (double) getVisibleHeight() / buffer.getHeight());
		setZoom(zoom);
		zoomMode = ZOOM_FILL;
	}
	
	public void setRotation(double angle) {
		rotate = new AffineTransform();
		rotate.rotate(angle);
	}
	
	public void invertAxes(boolean x, boolean y) {
		flip = new AffineTransform();
		flip.scale(x ? -1.0 : 1.0, y ? 1.0 : -1.0);
	}
	
	public void setTransparent(boolean value) {
		transparent = value;
	}
	 
	public void updateTransforms() {
		transform = new AffineTransform();
		transform.translate(0.5 * getWidth(), 0.5 * getHeight());	// Move image to the center of the panel.
		transform.concatenate(rotate);			// Rotate by the desired amount
		transform.concatenate(rescale);			// Rescale to image size
		transform.concatenate(flip);			// invert axes as desired
		transform.concatenate(center);			// Move origin to the array center
	
		try { inverseTransform = transform.createInverse(); }
		catch(NoninvertibleTransformException e) { inverseTransform = null; }
	}
	
	public int getVisibleWidth() { return getWidth() - 2 * padHorizontal; }
	
	public int getVisibleHeight() { return getHeight() - 2 * padVertical; }
	
	@Override
	public void paintComponent(Graphics g) {
		if(!transparent) super.paintComponent(g);
		
		if(!initialized) {
			defaults();
			updateBuffer();
			initialized = true;
		}
		
		setZoom();		
		updateTransforms();

		if(imageBackground != null) drawBackGround(g);
		drawImage(g);		
		drawBorder(g);
	}
	
	protected void setZoom() {
		switch(zoomMode) {
		case ZOOM_FIT : fitInside(); break;
		case ZOOM_FILL : fillInside(); break; 
		case ZOOM_STRETCH : setRenderSize(getVisibleWidth(), getVisibleHeight()); zoomMode = ZOOM_STRETCH; break;
		case ZOOM_FIXED : break;	// Nothing to do, it stays where it was set by setZoom or setRenderSize
		default : setRenderSize(buffer.getWidth(), buffer.getHeight());
		}		
	}
	
	protected void drawBackGround(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;	
		g2.setColor(imageBackground);
		g2.setStroke(new BasicStroke(0));
		Rectangle r = new Rectangle(padHorizontal, padVertical, getVisibleWidth(), getVisibleHeight());
		g2.fill(r);		
	}
	
	protected void drawImage(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;	
		AffineTransformOp op = new AffineTransformOp(transform, interpolationType);
		g2.drawImage(buffer, op, 0, 0);				
	}
	
	protected void drawBorder(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;	
		g2.setColor(penColor);
		g2.setStroke(new BasicStroke(border, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
		Rectangle r = new Rectangle(padHorizontal, padVertical, getVisibleWidth(), getVisibleHeight());
		g2.draw(r);
	}
	
	public abstract int getRGB(int i, int j);
	
	public void updateBuffer() {
		for(int i=buffer.getWidth(); --i >= 0; ) for(int j=buffer.getHeight(); --j >= 0; ) 
			buffer.setRGB(i, j, getRGB(i, j));
	}
	
	public Point2D toImageCoords(Point2D point) {
		return transform.transform(point, point);
	}

	public Point2D toDataIndex(Point2D point) {
		return inverseTransform.transform(point, point);
	}
	
	public static final int ZOOM_FIXED = 0;
	public static final int ZOOM_FIT = 1;
	public static final int ZOOM_FILL = 2;
	public static final int ZOOM_STRETCH = 3;
	
}
