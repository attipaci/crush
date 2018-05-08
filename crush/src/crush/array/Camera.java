/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/
package crush.array;

import java.io.*;
import java.util.*;

import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;
import crush.*;
import crush.instrument.ColorArrangement;
import crush.sourcemodel.*;
import crush.telescope.Mount;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.fits.FitsToolkit;
import jnum.io.LineParser;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;


public abstract class Camera<ChannelType extends Channel> extends Instrument<ChannelType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -707752417431510013L;
	
	protected double rotation = 0.0;
	
	public Camera(String name, ColorArrangement<? super ChannelType> layout) {
		super(name, layout);
	}
	
	public Camera(String name, ColorArrangement<? super ChannelType> layout, int size) {
		super(name, layout, size);
	}
	
	@Override
    protected void initModalities() {
		super.initModalities(); 
		
		CorrelatedMode common = (CorrelatedMode) modalities.get("obs-channels").get(0);
			
		CorrelatedMode gx = common.new CoupledMode(new SkyGradient.X());
		gx.name = "gradients:x";
		CorrelatedMode gy = common.new CoupledMode(new SkyGradient.Y());
		gy.name = "gradients:y";
		
		CorrelatedModality gradients = new CorrelatedModality("gradients", "G");
		gradients.add(gx);
		gradients.add(gy);
		
		addModality(gradients);
	}
	
	public void setReferencePosition(Vector2D position) {
		Vector2D referencePosition = position.copy();
		for(Pixel pixel : getPixels()) {
		    Vector2D v = pixel.getPosition();
		    if(v != null) v.subtract(referencePosition);
		}
	}
	
	public Hashtable<String, Pixel> getPixelLookup() {
		Hashtable<String, Pixel> table = new Hashtable<String, Pixel>();
		for(Pixel pixel : getPixels()) table.put(pixel.getID(), pixel);
		return table;
	}
		

	public abstract int maxPixels();
	
	@Override
	public SourceModel getSourceModelInstance(List<Scan<?,?>> scans) {
		if(hasOption("source.type")) {
			String type = option("source.type").getValue();
			if(type.equals("beammap")) return new BeamMap(this);
			return super.getSourceModelInstance(scans);
		}
		return super.getSourceModelInstance(scans);
	}
	
	@Override
    protected void loadChannelData() {	
		// Rotation is applied to calculated / default positions only.
		// RCP rotation is handled separately via 'rcp.rotate' option...
		rotation = 0.0;
		if(hasOption("rotation")) rotate(option("rotation").getDouble() * Unit.deg);
		
		if(hasOption("rcp")) {
			try { readRCP(option("rcp").getPath()); }
			catch(IOException e) { warning("Cannot update pixel RCP data. Using values from FITS."); }
		}
		
		// Instruments with a rotator should apply explicit rotation after pixel positions are finalized...
		if(this instanceof Rotating) {
			double angle = ((Rotating) this).getRotation();
			if(angle != 0.0) rotate(angle);
		}
		
		super.loadChannelData();
		
		
	}
		
	public final double getRotationAngle() {
		return rotation;
	}
	
	public void readRCP(String fileName)  throws IOException {		
		info("Reading RCP from " + fileName);
		
		// Channels not in the RCP file are assumed to be blind...
		for(ChannelType pixel : this) {
			pixel.flag(Channel.FLAG_BLIND);
		}
		
		final Hashtable<String, Pixel> idLookup = getPixelLookup(); 
		final boolean useGains = hasOption("rcp.gains");
			
		if(useGains) info("Initial Source Gains set from RCP file.");
		
		new LineParser() {

            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                int columns = tokens.countTokens();
                Pixel pixel = idLookup.get(tokens.nextToken());
                
                if(pixel == null) return false;
          
                if(pixel instanceof Channel) {
                    Channel channel = (Channel) pixel;
                    double sourceGain = tokens.nextDouble();
                    double coupling = (columns == 3 || columns > 4) ? sourceGain / tokens.nextDouble() : sourceGain / channel.gain;

                    if(useGains) channel.coupling = coupling;
                    if(sourceGain != 0.0) channel.unflag(Channel.FLAG_BLIND);
                }

                Vector2D position = pixel.getPosition();
                position.setX(tokens.nextDouble() * Unit.arcsec);
                position.setY(tokens.nextDouble() * Unit.arcsec);
                return true;
            }
		    
		}.read(fileName);
		
		
		flagInvalidPositions();
		
		if(hasOption("rcp.center")) {
			Vector2D offset = option("rcp.center").getVector2D();
			offset.scale(Unit.arcsec);
			for(Pixel pixel : getPixels()) pixel.getPosition().subtract(offset);
		}
		
		if(hasOption("rcp.rotate")) {
			double angle = option("rcp.rotate").getDouble() * Unit.deg;
			for(Pixel pixel : getPixels()) pixel.getPosition().rotate(angle);
		}
		
		if(hasOption("rcp.zoom")) {
			double zoom = option("rcp.zoom").getDouble();
			for(Pixel pixel : getPixels()) pixel.getPosition().scale(zoom);
		}
		
	}
	

	public String getRCPHeader() { return "ch\t[Gpnt]\t[Gsky]ch\t[dX\"]\t[dY\"]"; }
	
	public void printPixelRCP(PrintStream out, String header)  throws IOException {
		out.println("# CRUSH Receiver Channel Parameter (RCP) Data File.");
		out.println("#");
		if(header != null) out.println(header);
		out.println("#");
		out.println("# " + getRCPHeader());
		
		for(Pixel pixel : getMappingPixels(~sourcelessChannelFlags())) 
		    if(pixel.getPosition() != null) if(!pixel.getPosition().isNaN()) 
		        out.println(pixel.getRCPString());
	}

	public void generateRCPFrom(String rcpFileName, String pixelFileName) throws IOException {
		readRCP(rcpFileName);
		loadChannelData(pixelFileName);
		printPixelRCP(System.out, null);
	}
	
	public void flagInvalidPositions() {
		for(Pixel pixel : getPixels()) if(pixel.getPosition().length() > 1 * Unit.deg) 
			for(Channel channel : pixel) channel.flag(Channel.FLAG_BLIND);
	}
	
	protected void setPointing(Scan<?,?> scan) {
		if(hasOption("point")) return;
		info("Setting 'point' option to obtain pointing/calibration data.");
		setOption("point");
		scan.instrument.setOption("point");		
	}
	
	@Override
	public void parseImageHeader(Header header) {
		super.parseImageHeader(header);
		setResolution(header.getDoubleValue("BEAM", getResolution() / Unit.arcsec) * Unit.arcsec);
	}
	
	@Override
	public void editImageHeader(List<Scan<?,?>> scans, Header header) throws HeaderCardException {
		super.editImageHeader(scans, header);
		Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
		c.add(new HeaderCard("BEAM", getResolution() / Unit.arcsec, "The instrument FWHM (arcsec) of the beam."));
	}
	
	public Vector2D getPointingCenterOffset() { return new Vector2D(); }
	
	// Returns the offset of the pointing center from the the rotation center for a given rotation...
	private Vector2D getPointingOffset(double rotationAngle) {
		Vector2D offset = new Vector2D();
		
		final double sinA = Math.sin(rotationAngle);
		final double cosA = Math.cos(rotationAngle);
		
		if(mount == Mount.CASSEGRAIN) {
			Vector2D dP = getPointingCenterOffset();	
			offset.setX(dP.x() * (1.0 - cosA) + dP.y() * sinA);
			offset.setY(dP.x() * sinA + dP.y() * (1.0 - cosA));
		}
		return offset;
	}
		
	
	// How about different pointing and rotation centers?...
	// If assuming that pointed at rotation a0 and observing at a
	// then the pointing center will rotate by (a-a0) on the array rel. to the rotation
	// center... (dP is the pointing rel. to rotation vector)
	// i.e. the effective array offsets change by:
	//	dP - dP.rotate(a-a0)
	
	// For Cassegrain assume pointing at zero rotation (a0 = 0.0)
	// For Nasmyth assume pointing at same elevation (a = a0)
	
	public void rotate(double angle) {
		if(Double.isNaN(angle)) return;
		
		info("Applying rotation at " + Util.f1.format(angle / Unit.deg) + " deg.");
		
		// Undo the prior rotation...
		Vector2D priorOffset = getPointingOffset(rotation);
		Vector2D newOffset = getPointingOffset(rotation + angle);
		
		for(Pixel pixel : getPixels()) if(pixel.getPosition() != null) {
			Vector2D position = pixel.getPosition();
			
			// Center positions on the rotation center...
			position.subtract(priorOffset);
			// Do the rotation...
			position.rotate(angle);
			// Re-center on the pointing center...
			position.add(newOffset);
		}
		
		rotation += angle;
	}
	
	
	public static void addLocalFixedIndices(GridIndexed geometric, int fixedIndex, double radius, Collection<Integer> toIndex) {
		
		final int row = fixedIndex / geometric.cols();
		final int col = fixedIndex % geometric.cols();
		
		final Vector2D pixelSize = geometric.getPixelSize();
		final int dc = (int)Math.ceil(radius / pixelSize.x());
		final int dr = (int)Math.ceil(radius / pixelSize.y());
		
		final int fromi = Math.max(0, row - dr);
		final int toi = Math.min(geometric.rows()-1, row + dr);
		
		final int fromj = Math.max(0, col - dc);
		final int toj = Math.min(geometric.cols()-1, col + dc);
	
		for(int i=fromi; i<=toi; i++) for(int j=fromj; j<=toj; j++) if(!(i == row && j == col)) {
			final double r = ExtraMath.hypot((i - row) * pixelSize.y(), (j - col) * pixelSize.x());
			if(r <= radius) toIndex.add(i * geometric.cols() + j);
		}
		
	}
	
}
