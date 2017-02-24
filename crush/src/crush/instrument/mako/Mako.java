/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.mako;

import java.io.IOException;
import java.util.Hashtable;
import java.util.StringTokenizer;

import crush.CRUSH;
import crush.Scan;
import crush.resonators.FrequencyID;
import crush.resonators.ResonatorList;
import jnum.Unit;
import jnum.io.LineParser;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;

public class Mako extends AbstractMako<MakoPixel> {
	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3325545547718854921L;
	public static int rows = 16;
	public static int cols = 27;

	double Tsky = Double.NaN;
	
	MakoPixelMatch identifier;
	
	public Mako() {
		super("mako", rows * cols);
		pixelSize = MakoPixel.defaultSize;
	}
	
	@Override
	public MakoPixel getChannelInstance(int backendIndex) {
		return new MakoPixel(this, backendIndex);
	}	
	
	@Override
	public Scan<?, ?> getScanInstance() {
		return new MakoScan<Mako>(this);
	}
	
	@Override
	protected Vector2D getDefaultArrayPointingCenter() {
		return new Vector2D((rows+1) / 2.0, (cols+1) / 2.0);
	}

	@Override
	protected Vector2D getDefaultArrayRotationCenter() {
		return getDefaultArrayPointingCenter();
	}

	@Override
	public int maxPixels() {
		return rows * cols;
	}
	

	public Vector2D getPixelPosition(Vector2D size, double row, double col) {
		return new Vector2D(size.x() * (col - 0.5 * (Mako.cols-1)), size.y() * (row - 0.5 * (Mako.rows-1)));
	}
	
	@Override
	public Vector2D getPointingCenterOffset() {
		// Update the rotation center...
		Vector2D arrayRotationCenter = getDefaultArrayRotationCenter();
		if(hasOption("rcenter")) arrayRotationCenter = option("rcenter").getVector2D();
	
		return getPixelPosition(pixelSize, arrayPointingCenter.x() - arrayRotationCenter.x(), arrayPointingCenter.y() - arrayRotationCenter.y());
	}
	
	
	@Override
    protected void loadChannelData() {
		if(size() == 0) return;
		
		if(hasOption("pixelid")) {
			try {
				identifier = new MakoPixelMatch(option("pixelid"));	
				double guessT = (hasOption("pixelid.guesst") ? option("pixelid.guesst").getDouble() : 150.0) * Unit.K;
				Tsky = identifier.match(new ResonatorList<MakoPixel>(getObservingChannels()), guessT);
			}
			catch(IOException e) {
				warning("Cannot identify tones from '" + option("pixelid").getValue() + "'."); 
				if(CRUSH.debug) CRUSH.trace(e);
			}
		}
				
		if(identifier != null && hasOption("assign")) {	
			try { assignPixels(option("assign").getValue()); }
			catch(IOException e) { 
				warning("Cannot assign pixels from '" + option("assign").getValue() + "'."); 
				if(CRUSH.debug) CRUSH.trace(e);
			}
		}
		else warning("Tones are not assigned to pixels. Cannot make regular maps.");
		
		
		// Do not flag unassigned pixels when beam-mapping...
		if(hasOption("source.type")) if(option("source.type").equals("beammap")) 
				for(AbstractMakoPixel pixel : this) pixel.unflag(AbstractMakoPixel.FLAG_UNASSIGNED);
		
		// Update the pointing center...
		if(hasOption("pcenter")) arrayPointingCenter = option("pcenter").getVector2D();
		
		Vector2D pixelSize = getDefaultPixelSize();
		
		// Set the pixel size...
		if(hasOption("pixelsize")) {
			pixelSize = new Vector2D();
			StringTokenizer tokens = new StringTokenizer(option("pixelsize").getValue(), " \t,:xX");
			pixelSize.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
			pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.x());
		}
		if(hasOption("mirror")) { pixelSize.scaleX(-1.0); }
		if(hasOption("zoom")) { pixelSize.scale(option("zoom").getDouble()); }
		if(hasOption("stretch")) { 
			double skew = option("stretch").getDouble();
			pixelSize.scaleX(skew);
			pixelSize.scaleY(1.0/skew);
		}
		
		calcPositions(pixelSize);
		
		checkRotation();
		
		super.loadChannelData();
		
	}

	public void assignPixels(String fileSpec) throws IOException {
		if(identifier == null) throw new IllegalStateException("Assigning pixels requires tone identifications first.");
		
		info("Loading pixel assignments from " + fileSpec);
	
		final ResonatorList<MakoPixel> associations = new ResonatorList<MakoPixel>(pixels);
		final double guessT = (hasOption("assign.guesst") ? option("assign.guesst").getDouble() : 300.0) * Unit.K;
		
		new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line, ", \t");
                MakoPixel pixelID = new MakoPixel(Mako.this, -1);
                
                pixelID.toneFrequency = tokens.nextDouble();
                pixelID.row = tokens.nextInt() - 1;
                pixelID.col = tokens.nextInt() - 1;

                associations.add(pixelID);
                return true;
            }
		}.read(fileSpec);
		

		info("Found pixel assignments for " + associations.size() + " resonances.");
		
		identifier.match(associations, guessT);
		setRowColFrom(associations);
	}
	
	private void setRowColFrom(ResonatorList<MakoPixel> associations) {
		int assigned = 0;
		
		Hashtable<FrequencyID, MakoPixel> lookup = new Hashtable<FrequencyID, MakoPixel>(associations.size());
		for(MakoPixel association : associations) if(association.getFrequencyID() != null) 
			lookup.put(association.getFrequencyID(), association);
		
		for(MakoPixel pixel : this) {
			MakoPixel association = lookup.get(pixel.getFrequencyID());
			if(association == null) continue;
			if(!association.isAssigned()) continue;
			
			pixel.setRowCol(association.row, association.col);
			pixel.unflag(AbstractMakoPixel.FLAG_UNASSIGNED);
		}	
		
		info("Assigned " + assigned + " of " + size() + " resonators to references.");		
	}

	
	// Assuming tone id's are at 4.2K load and maximum movement is measured at room temperature -- 22 C)
	@Override
	public double getLoadTemperature() {
		double Tcold = 4.2 * Unit.K;
		double Thot = 295.16 * Unit.K;
		return Tcold + Tsky * (Thot - Tcold);
	}
	
	@Override
	public Object getTableEntry(String name) {
		if(name.equals("Tres")) return Tsky / Unit.K;
		else return super.getTableEntry(name);
	}
	
	@Override
	public String getChannelDataHeader() {
		return "pixelid\t" + super.getChannelDataHeader() + "\teff";
	}

	

	@Override
	protected void parseDataHeader(Header header) throws HeaderCardException, FitsException {
		// Pointing Center
		arrayPointingCenter = new Vector2D();
		arrayPointingCenter.setX(header.getDoubleValue("CRPIX3", (rows + 1) / 2.0));
		arrayPointingCenter.setY(header.getDoubleValue("CRPIX2", (cols + 1) / 2.0));
	}

	@Override
	protected Vector2D getDefaultPixelSize() {
		return MakoPixel.defaultSize;
	}
	
	
}
