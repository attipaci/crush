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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.JPanel;


import util.Vector2D;

public class Plot<ContentType extends ContentLayer> extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5175315778375641551L;

	private Vector<PlotLayer> layers = new Vector<PlotLayer>();
	private ContentType contentLayer;
	
	Vector2D referencePoint = new Vector2D(0.0, 1.0); // lower left corner...
	Vector2D scale = new Vector2D();
	double rotation = 0.0;
	boolean flipX = false, flipY = false;
	
	public int zoomMode = ZOOM_STRETCH;	
	
	protected AffineTransform toDisplay, toCoordinates = null;
	
	boolean initialized = false;
	boolean verbose = false;
	
	protected int border = 0;
	protected int padLeft = 0, padRight = 0, padTop = 0, padBottom = 0;
	
	
	
	public Plot() {}
	
	public Plot(ContentType content) {
		this();
		setContentLayer(content);
	}
	
	/**
	 * Called before the first rendering of the image. Otherwise, it can be called beforehand, allowing
	 * to override the rendering parameters (e.g. rendering size, data scaling, subarray selection etc.)
	 */
	public void defaults() {
		initialized = true;
	}

	
	public void setPadding(int n) {
		setPadding(n, n);
	}
	
	public void setPadding(int horizontal, int vertical) {
		setPadding(horizontal, vertical, horizontal, vertical);
	}
	
	public void setPadding(int left, int top, int right, int bottom) {
		if(verbose) System.err.println("Setting padding to " + left + ", " + top + ", " + right + ", " + bottom);
		padTop = Math.max(0, top);
		padLeft = Math.max(0, left);
		padBottom = Math.max(0, bottom);
		padRight = Math.max(0, right);
	}
	
	public int getVisibleWidth() { return getWidth() - padLeft - padRight; }
	
	public int getVisibleHeight() { return getHeight() - padTop - padBottom; }
	
	public ContentType getContentLayer() {
		return contentLayer;		
	}
	
	public void setContentLayer(ContentType layer) {
		if(!contains(layer)) insertLayer(layer, 0); 
		contentLayer = layer;
	}
	
	public void setContentLayer(ContentType layer, int index) {
		if(contains(layer)) layers.remove(contentLayer);
		insertLayer(layer, index); 
		contentLayer = layer;
	}
	
	
	public Rectangle getPlotRectangle() {
		return new Rectangle(padLeft, padTop, getVisibleWidth(), getVisibleHeight());
	}
	
	
	public void addLayer(PlotLayer layer) {
		layer.plot = this;
		layers.add(layer);
	}
	
	public void insertLayer(PlotLayer layer, int pos) {
		layer.plot = this;
		layers.insertElementAt(layer, pos);
	}
	
	public void removeLayer(int index) {
		layers.remove(index);
	}
	
	public boolean contains(PlotLayer layer) {
		return layers.contains(layer);
	}
	
	public int indexOf(PlotLayer layer) {
		return layers.indexOf(layer);
	}
	
	public void remove(PlotLayer layer) {
		int index = indexOf(layer);
		if(index >= 0) removeLayer(index);
	}
	
	
	public void setRenderSize(int width, int height) {
		if(verbose) System.err.println("Setting render size to " + width + "x" + height);
		Vector2D range = contentLayer.getPlotRange();
		System.err.println("range is " + range);
		scale.x = width / range.x;
		scale.y = height / range.y;
		zoomMode = ZOOM_FIXED;
	}
	
	
	public void setZoom(double value) {
		if(verbose) System.err.println("Setting zoom to " + value);
		scale.x = value;
		scale.y = value;
		zoomMode = ZOOM_FIXED;
	}

	public void zoom(double relative) {
		if(verbose) System.err.println("Zooming by " + relative);
		scale.scale(relative);
		zoomMode = ZOOM_FIXED;
	}
	
	protected void updateZoom() {
		switch(zoomMode) {
		case ZOOM_FIT : fitInside(); break;
		case ZOOM_FILL : fillInside(); break; 
		case ZOOM_STRETCH : setRenderSize(getVisibleWidth(), getVisibleHeight()); zoomMode = ZOOM_STRETCH; break;
		case ZOOM_FIXED : break;	// Nothing to do, it stays where it was set by setZoom or setRenderSize
		default : setRenderSize(300, 300);
		}		
	}
	
	
	public void fitInside() {
		Vector2D range = contentLayer.getPlotRange();
		double zoom = Math.min((double) getVisibleWidth() / range.x, (double) getVisibleHeight() / range.y);
		setZoom(zoom);
		zoomMode = ZOOM_FIT;
	}
	
	public void fillInside() {
		Vector2D range = contentLayer.getPlotRange();
		double zoom = Math.max((double) getVisibleWidth() / range.x, (double) getVisibleHeight() / range.y);
		setZoom(zoom);
		zoomMode = ZOOM_FILL;
	}
	
	public void setRotation(double angle) {
		rotation = angle;
	}
	
	public void invertAxes(boolean x, boolean y) {
		flipX = x;
		flipY = y;
	}
	 

	public void updateTransforms() {
		toDisplay = new AffineTransform();
			
		updateZoom();
			
		toDisplay.translate(
				padLeft + referencePoint.x * getVisibleWidth(), 
				padTop + referencePoint.y * getVisibleHeight()
		);	// Move image to the referencepoint of the panel.
			
		//if(flipX) toDisplay.scale(-1.0, 1.0);	// invert axes as desired
		if(!flipY) toDisplay.scale(1.0, -1.0);	// invert axes as desired
		
		toDisplay.scale(scale.x, scale.y);		// Rescale to image size
		toDisplay.rotate(rotation);				// Rotate by the desired amount
		
		if(contentLayer != null) {
			if(contentLayer instanceof Transforming) {
				toDisplay.concatenate(((Transforming) contentLayer).getTransform());
			}
			Vector2D contentRef = contentLayer.getReferencePoint();
			toDisplay.translate(-contentRef.x, -contentRef.y); // Move to the reference point of the content layer
		}
		
		try { toCoordinates = toDisplay.createInverse(); }
		catch(NoninvertibleTransformException e) { toCoordinates = null; }
	}
	
	public Point2D toDisplay(Point2D point) {
		return toDisplay.transform(point, point);
	}

	public Point2D toCoordinates(Point2D point) {
		return toCoordinates.transform(point, point);
	}
	
	@Override
	public void paintComponent(Graphics g) {	
		if(!initialized) {
			defaults();
			contentLayer.initialize();
			initialized = true;
		}
	
		updateTransforms();

		super.paintComponent(g);
		
		for(PlotLayer layer : layers) layer.paintComponent(g);
	}
	
	// Returns a generated image.
	public RenderedImage getRenderedImage(int width, int height) {
		setSize(width, height);
		
		// Create a buffered image in which to draw
	    BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
	    
	    // Create a graphics contents on the buffered image
	    Graphics2D g2d = bufferedImage.createGraphics();  
	    
	    // Draw graphics
	    paintComponent(g2d);

	    // Graphics context no longer needed so dispose it
	    g2d.dispose();

	    return bufferedImage;
	}

	public void saveAs(String fileName, int width, int height) throws IOException {
		File file = new File(fileName);
		int iExt = fileName.lastIndexOf(".");
		String type = iExt > 0 && iExt < fileName.length() - 1 ? fileName.substring(iExt + 1) : "gif";
		ImageIO.write(getRenderedImage(width, height), type, file);
		System.err.println(" Written " + fileName);
	}
	
	public static final int ZOOM_FIXED = 0;
	public static final int ZOOM_FIT = 1;
	public static final int ZOOM_FILL = 2;
	public static final int ZOOM_STRETCH = 3;
	
}
