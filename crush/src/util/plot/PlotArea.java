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
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.OverlayLayout;


import util.CoordinatePair;
import util.Vector2D;

public class PlotArea<ContentType extends ContentLayer> extends TransparentPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5175315778375641551L;

	private Vector<PlotLayer> layers = new Vector<PlotLayer>();
	private ContentType contentLayer;
	
	private Vector2D referencePoint = new Vector2D(0.0, 1.0); // lower left corner...
	private Vector2D scale = new Vector2D();
	private Vector2D userOffset = new Vector2D(); // alignment...
	
	private double rotation = 0.0;
	private boolean flipX = false, flipY = false;
	
	private int zoomMode = ZOOM_STRETCH;
	
	private AffineTransform toDisplay, toCoordinates = null;
	
	private boolean initialized = false;
	private boolean verbose = false;
	
	public PlotArea() {}
	
	public PlotArea(ContentType content) {
		this();
		setContentLayer(content);
		setLayout(new OverlayLayout(this));
	}
	
	/**
	 * Called before the first rendering of the image. Otherwise, it can be called before rendering, allowing
	 * to override the rendering parameters (e.g. rendering size, data scaling, subarray selection etc.)
	 */
	public void defaults() {
		initialized = true;
	}
	
	public final AffineTransform toDisplay() { return toDisplay; }
	
	public final AffineTransform toCoordinates() { return toCoordinates; }
	
	public Vector2D getReferencePoint() { return referencePoint; }
	
	public void setReferencePoint(Vector2D v) { referencePoint = v; }
	
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
	
	public void addLayer(PlotLayer layer) {
		layer.setPlotArea(this);
		layers.add(layer);
	}
	
	public void insertLayer(PlotLayer layer, int pos) {
		layer.setPlotArea(this);
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
	
	public Vector2D getScale() { return scale; }
	
	public void setScale(CoordinatePair s) { setScale(s.getX(), s.getY()); }
	
	public void setScale(double x, double y) { this.scale.set(x, y); }
	
	public void setRenderSize(int width, int height) {
		if(verbose) System.err.println("Setting render size to " + width + "x" + height);
		Rectangle2D bounds = contentLayer.getCoordinateBounds();
		scale.set(width / bounds.getWidth(), height / bounds.getHeight());
	}
	
	public void moveReference(double dx, double dy) {
		referencePoint.subtractX(dx / getWidth());
		referencePoint.subtractY(dy / getHeight());
	}
	

	public int getZoomMode() { return zoomMode; }
	
	public void setZoomMode(int value) { zoomMode = value; }
	
	public void setZoom(double value) {
		if(verbose) System.err.println("Setting zoom to " + value);
		scale.set(value, value);
	}

	public void zoom(double relative) {
		if(verbose) System.err.println("Zooming by " + relative);
		scale.scale(relative);
	}
	
	protected void updateZoom() {
		Rectangle2D bounds = null;
		switch(zoomMode) {
		case ZOOM_FIT : 
			bounds = contentLayer.getCoordinateBounds();
			setZoom(Math.min((double) getWidth() / bounds.getWidth(), (double) getHeight() / bounds.getHeight()));
			center();
			break;
		case ZOOM_FILL : 
			bounds = contentLayer.getCoordinateBounds();
			setZoom(Math.min((double) getWidth() / bounds.getWidth(), (double) getHeight() / bounds.getHeight()));		
			center();
			break; 
		case ZOOM_STRETCH : setRenderSize(getWidth(), getHeight()); break;
		case ZOOM_FIXED : break;	// Nothing to do, it stays where it was set by setZoom or setRenderSize
		default : setRenderSize(300, 300);
		}		
	}
	
	public void fit() { 	
		setZoomMode(ZOOM_FIT);
		updateZoom();
	}
	

	public void fill() { 
		setZoomMode(ZOOM_FILL);
		updateZoom();
	}
	
	
	public void center() {	
		Rectangle2D bounds = contentLayer.getCoordinateBounds();
			
		// Get the nominal center as the middle of the bounding box
		// in the native coordinates...
		Vector2D center = new Vector2D(new Point2D.Double(
				bounds.getMinX() + 0.5 * bounds.getWidth(),
				bounds.getMinY() + 0.5 * bounds.getHeight()
		));
			
		center.subtract(new Vector2D(contentLayer.getCoordinateReference()));
		
		userOffset.copy(center);
	}
	
	public Vector2D getUserOffset() { return userOffset; }
	
	public void setUserOffset(Vector2D v) { this.userOffset = v; }
	
	public void align() { userOffset.zero(); }
	public void setRotation(double angle) {
		rotation = angle;
	}
	
	public void invertAxes(boolean x, boolean y) {
		flipX = x;
		flipY = y;
	}
	 
	public int getCenterX() {
		return (int) Math.round(0.5 * getWidth());
	}
	
	public int getCenterY() {
		return (int) Math.round(0.5 * getHeight());
	}

	public void updateTransforms() {
		toDisplay = new AffineTransform();
			
		updateZoom();
				
		toDisplay.translate(
				referencePoint.getX() * getWidth(), 
				referencePoint.getY() * getHeight()
		);	// Move image to the referencepoint of the panel.
			
		if(flipX) toDisplay.scale(-1.0, 1.0);	// invert axes as desired
		if(!flipY) toDisplay.scale(1.0, -1.0);	// invert axes as desired
		
		toDisplay.scale(scale.getX(), scale.getY());		// Rescale to image size
		toDisplay.rotate(rotation);				// Rotate by the desired amount
		
		if(contentLayer != null) {
			toDisplay.translate(-userOffset.getX(), -userOffset.getY()); // Move by the desired offset in user coordinates...
			
			if(contentLayer instanceof Transforming) {
				toDisplay.concatenate(((Transforming) contentLayer).getTransform());
			}
			
			Point2D contentRef = contentLayer.getCoordinateReference();
			toDisplay.translate(-contentRef.getX(), -contentRef.getY()); // Move to the reference point of the content layer
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
	    BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	    
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

	
	public boolean isVerbose() { return verbose; }
	
	public void setVerbose(boolean value) { verbose = value; }
	
	protected boolean isInitialized() { return initialized; }
	
	protected void setInitialized() { initialized = true; }
	
	public static final int ZOOM_FIXED = 0;
	public static final int ZOOM_FIT = 1;
	public static final int ZOOM_FILL = 2;
	public static final int ZOOM_STRETCH = 3;
	

	
}
