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
// Copyright (c) 2009 Attila Kovacs

package crush.apex;

import crush.*;
import nom.tam.fits.*;
import nom.tam.util.*;

import java.io.*;

import kovacs.astro.CelestialCoordinates;
import kovacs.astro.EquatorialCoordinates;
import kovacs.astro.HorizontalCoordinates;
import kovacs.data.WeightedPoint;
import kovacs.math.Vector2D;
import kovacs.util.*;

import crush.fits.HDUReader;

public class APEXArraySubscan<InstrumentType extends APEXArray<?>, FrameType extends APEXFrame> 
extends Integration<InstrumentType, FrameType> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2929947229904002745L;
	public int nodPhase = 0;
	protected Thread thread;
	
	protected WeightedPoint[] tempPhase;
	
	public APEXArraySubscan(APEXArrayScan<InstrumentType, ?> parent) {
		super(parent);
	}
	
	@Override
	public void setTau() throws Exception {	
		try { super.setTau(); }
		catch(IllegalArgumentException e) {
			String tauName = option("tau").getPath();
			try { 
				APEXTauTable tauTable = APEXTauTable.get(tauName);
				if(hasOption("tau.window")) tauTable.timeWindow = option("tau.window").getDouble() * Unit.hour;
				setTau(tauTable.getTau(getMJD()));
			}
			catch(ArrayIndexOutOfBoundsException ie) { System.err.println("     WARNING! " + e.getMessage()); }
			catch(IOException io) { System.err.println("WARNING! Tau interpolator table could not be read."); }
		}
	}
	
	@Override
	public void setScaling() throws Exception {
		try { super.setScaling(); }
		catch(NumberFormatException noworry) {
			String calName = option("scale").getPath();
			try { 
				APEXCalibrationTable calTable = APEXCalibrationTable.get(calName);
				if(hasOption("scale.window")) calTable.timeWindow = option("scale.window").getDouble() * Unit.hour;
				setScaling(calTable.getScaling(getMJD())); 
			}
			catch(ArrayIndexOutOfBoundsException e) { System.err.println("     WARNING! " + e.getMessage()); }
			catch(IOException e) { System.err.println("WARNING! Calibration table could not be read."); }
		}
	}
	
	public void markChopped() {
		if(nodPhase == 0) 
			throw new IllegalStateException("Merged subscan contains mixed nod phases. Cannot process chopper.");
		
		// Flag pixels that chop on source
		// left and right are the pixel positions, where, if there's a pixel, it will have the source
		// in the left or right beams...
		//System.err.println("on phase is " + integration[i][k].onPhase);
		
		
		Vector2D left = new Vector2D(nodPhase == Frame.CHOP_LEFT ? 0.0 : 2.0 * chopper.amplitude, 0.0);
		Vector2D right = new Vector2D(nodPhase == Frame.CHOP_LEFT ? -2.0 * chopper.amplitude : 0.0, 0.0);
		double tolerance = instrument.resolution / 5.0;
		
		markChopped(left, right, tolerance);
		
		tempPhase = new WeightedPoint[chopper.phases.size()];
		for(int i=tempPhase.length; --i >=0; ) tempPhase[i] = new WeightedPoint();		
	}
	
	
	public void markChopped(Vector2D left, Vector2D right, double tolerance) {
		// Flag pixels that chop on source
		// left and right are the pixel positions, where, if there's a pixel, it will have the source
		// in the left or right beams...
		//System.err.println("on phase is " + subscan[i][k].onPhase);
		
		System.err.print("   Marking Chopper Phases: ");
	
		
		for(Pixel pixel : instrument.getPixels()) {
			Vector2D position = pixel.getPosition();
			
			if(position.distanceTo(left) < tolerance) for(Channel channel : pixel) {
				channel.sourcePhase |= Frame.CHOP_LEFT;
				System.err.print(" L" + channel.getFixedIndex());
			}
			else if(position.distanceTo(right) < tolerance) for(Channel channel : pixel) {
				channel.sourcePhase |= Frame.CHOP_RIGHT;
				System.err.print(" R" + channel.getFixedIndex()); 
			}
			else for(Channel channel : pixel) channel.sourcePhase &= ~Frame.CHOP_FLAGS;
		}
		
		System.err.println();
		
		chopper.phases = new PhaseSet(this);
		
		int usable = 0;
		
		// Flag frames according to chopper phase ---> left, right, transit.
		PhaseOffsets current = new PhaseOffsets(this);
		int phases = 0;
		int transitFlag = Frame.CHOP_TRANSIT | Frame.SKIP_MODELS | Frame.SKIP_WEIGHTING | Frame.SKIP_SOURCE;
		
		for(Frame exposure : this) if(exposure != null) {
			exposure.flag &= ~Frame.CHOP_FLAGS;
			
			if(Math.abs(exposure.chopperPosition.getX() + chopper.amplitude) < tolerance) {
				exposure.flag |= Frame.CHOP_LEFT;
				if(current.phase != Frame.CHOP_LEFT) {
					current = new PhaseOffsets(this);
					current.phase = Frame.CHOP_LEFT;
					//if(current.phase == nodPhase) current.flag |= PhaseOffsets.SKIP_GAINS;
					current.start = exposure;
					current.end = exposure;
					if(current.phase != 0) chopper.phases.add(current);
					phases++;
				}
				else current.end = exposure;
				usable++;
			}
			else if(Math.abs(exposure.chopperPosition.getX() - chopper.amplitude) < tolerance) {
				exposure.flag |= Frame.CHOP_RIGHT;
				if(current.phase != Frame.CHOP_RIGHT) {
					current = new PhaseOffsets(this);
					current.phase = Frame.CHOP_RIGHT;
					//if(current.phase == nodPhase) current.flag |= PhaseOffsets.SKIP_GAINS;
					current.start = exposure;
					current.end = exposure;
					if(current.phase != 0) chopper.phases.add(current);
					phases++;
				}
				else current.end = exposure;
				usable++;
			}
			else exposure.flag |= transitFlag;
		}
		
		chopper.efficiency = ((double) usable / size());
		
		System.err.println("   Chopper parameters: " + chopper.toString());
		
		chopper.phases.validate();
		
		// Discard transit frames altogether...
		for(int i=size(); --i >=0; ) {
			final Frame exposure = get(i);
			if(exposure != null) if(exposure.isFlagged(Frame.CHOP_TRANSIT)) set(i, null);
		}
		
		removeOffsets(true, false);
		
		// Get the initial phase data...
		updatePhases();
		
		filterTimeScale = size() * instrument.integrationTime / phases; 
		
	}
	
	@Override
	public void validate() {
		super.validate();	
		if(chopper != null) markChopped();
	}
		
	public void readData(BinaryTableHDU hdu) throws Exception {	
		new DataTable(hdu).read();
	}
	
	class DataTable extends HDUReader {
		private float[] data;
		private int channels;
		
		public DataTable(TableHDU hdu) throws FitsException { 
			super(hdu); 
			int iData = hdu.findColumn("DATA");
			channels = table.getSizes()[iData];
			data = (float[]) table.getColumn(iData);
		}
		
		@Override
		public Reader getReader() {
			return new Reader() {
				@Override
				public void readRow(int t) throws FitsException {
					final APEXFrame exposure = get(t);
					if(exposure != null) exposure.parse(data, t * channels, channels);			
				}
			};
		}
	}
	
	public void writeData(String fromName, String toName) throws IOException, FitsException, HeaderCardException {
		Fits fits = new Fits(new File(fromName), fromName.endsWith(".gz"));
		BinaryTableHDU hdu = (BinaryTableHDU) fits.getHDU(1);
			
		final int iData = hdu.findColumn("DATA");
		
		for(Frame exposure : this) {
			final Object[] row = hdu.getRow(exposure.index);
			final float[][] data = (float[][]) row[iData];
			for(int c=0; c<data.length; c++) data[c][0] = 0.0F;
			for(Channel channel : instrument) data[channel.getFixedIndex()-1][0] = exposure.data[channel.index];
			hdu.setRow(exposure.index, row);
		}	
			
		fits.write(new BufferedDataOutputStream(new FileOutputStream(toName)));
	}
	
	
	public void readDataPar(BinaryTableHDU hdu)  throws Exception {
		final Header header = hdu.getHeader();	
		final int frames = header.getIntValue("NAXIS2");
		
		clear();
		ensureCapacity(frames);
		for(int i=frames; --i >= 0; ) add(null);
	
		instrument.integrationTime = ((double[]) hdu.getRow(0)[hdu.findColumn("INTEGTIM")])[0] * Unit.s;
		instrument.samplingInterval = instrument.integrationTime;
		// Use the integrationTime to convert to data weights...
		instrument.dataWeights();
		
		System.err.println("   Sampling at " + Util.f3.format(1.0/instrument.samplingInterval) + " Hz.");
	
		System.err.println("   " + frames + " frames found (" + 
				Util.f1.format(frames * instrument.samplingInterval / Unit.min) + " minutes).");

		if(hasOption("scramble")) System.err.println("   !!! Scrambling position data (noise map only) !!!");

		
		new DataParTable(hdu).read(); 
	}
		
	class DataParTable extends HDUReader {
		private double[] MJD, LST, X, Y, DX, DY, chop;
		private int[] phase;
		private boolean chopperIncluded;
		private final APEXArrayScan<InstrumentType, ?> apexScan = (APEXArrayScan<InstrumentType, ?>) scan;
		private final static double m900deg = -900.0 * Unit.deg;
		
		public DataParTable(TableHDU hdu) throws FitsException {
			super(hdu);

			final APEXArrayScan<InstrumentType, ?> apexScan = (APEXArrayScan<InstrumentType, ?>) scan;
			final Header header = hdu.getHeader();		

			MJD = (double[]) table.getColumn(hdu.findColumn("MJD"));
			LST = (double[]) table.getColumn(hdu.findColumn("LST"));
			//AZ = (double[]) table.getColumn(hdu.findColumn("AZIMUTH"));
			//EL = (double[]) table.getColumn(hdu.findColumn("ELEVATIO"));
			X = (double[]) table.getColumn(hdu.findColumn("BASLONG"));
			Y = (double[]) table.getColumn(hdu.findColumn("BASLAT"));
			DX = (double[]) table.getColumn(hdu.findColumn("LONGOFF"));
			DY = (double[]) table.getColumn(hdu.findColumn("LATOFF"));
			phase = (int[]) table.getColumn(hdu.findColumn("PHASE"));

			/*
			int iRA = hdu.findColumn("RA");
			int iDEC = hdu.findColumn("DEC");

			if(iRA < 0 && apexScan.isEquatorial) {
				iRA = hdu.findColumn("BASLONG");
				iDEC = hdu.findColumn("BASLAT");
			}

			RA = iRA > 0 ? (double[]) table.getColumn(iRA) : null;
			DEC = iDEC > 0 ? (double[]) table.getColumn(iDEC) : null;
			 */

			int iChop = hdu.findColumn("WOBDISLN");
			chop = iChop > 0 ? (double[]) table.getColumn(iChop) : null;

			chopperIncluded = header.getBooleanValue("WOBCOORD", false);
			// TODO this is an override, which should not be necessary if the data files are fixed.
			chopperIncluded = false;

			final String phase1 = header.getStringValue("PHASE1");	
			final String phase2 = header.getStringValue("PHASE2");

			nodPhase = Frame.CHOP_LEFT;

			if(phase1 != null) {
				if(phase1.equalsIgnoreCase("WON")) nodPhase = Frame.CHOP_LEFT;
				else if(phase1.equalsIgnoreCase("WOFF")) nodPhase = Frame.CHOP_RIGHT;
			}
			else if(phase2 != null) {
				if(phase2.equalsIgnoreCase("WON")) nodPhase = Frame.CHOP_RIGHT;
				else if(phase2.equalsIgnoreCase("WOFF")) nodPhase = Frame.CHOP_LEFT;
			}

			if(apexScan.chopper != null) chopper = apexScan.chopper.copy();

			if(chopper != null)
				System.err.println("   Nodding " + (nodPhase == Frame.CHOP_LEFT ? "[LEFT]" : 
					(nodPhase == Frame.CHOP_RIGHT ? "[RIGHT]" : "[???]")));		
		}	
		
		
		@Override
		public Reader getReader() {
			return new Reader() {				
				private Vector2D tempOffset;
				private CelestialCoordinates basisCoords;

				@Override
				public void init() {
					super.init();
					tempOffset = new Vector2D();
					
					if(apexScan.basisSystem != HorizontalCoordinates.class && apexScan.basisSystem != EquatorialCoordinates.class) {
						try { basisCoords = (CelestialCoordinates) apexScan.basisSystem.newInstance(); }
						catch(Exception e) {
							throw new IllegalStateException("Error instantiating " + apexScan.basisSystem.getName() +
									": " + e.getMessage());
						}
					}					
				}
				
				@Override
				public void readRow(int t) throws FitsException {
					set(t, null);
					
					// Continue only if the basis coordinates are valid...
					// APEX uses -999 deg to mark invalid data...
					final double x = X[t] * Unit.deg;
					if(x < m900deg) return;
					
					final double y = Y[t] * Unit.deg;
					if(y < m900deg) return;

					// Continue only if the scanning offsets are valid...
					// APEX uses -999 deg to mark invalid data...
					final double dx = DX[t] * Unit.deg;
					if(dx < m900deg) return;
					
					final double dy = DY[t] * Unit.deg;
					if(dy < m900deg) return;
			
					// Create the frame object once the hurdles above are cleared...
					final FrameType exposure = getFrameInstance();
					exposure.index = t;

					exposure.MJD = MJD[t];
					exposure.LST = LST[t];
					
					if(basisCoords != null) {
						basisCoords.set(x, y);
						exposure.equatorial = basisCoords.toEquatorial();
						exposure.calcHorizontal();
					}	
					else if(apexScan.basisSystem == EquatorialCoordinates.class) {
						exposure.equatorial = new EquatorialCoordinates(x, y, scan.equatorial.epoch);
						exposure.calcHorizontal();
					}
					else exposure.horizontal = new HorizontalCoordinates(x, y);

					exposure.horizontalOffset = new Vector2D(dx, dy);
					exposure.calcParallacticAngle();

					// Make scanning offsets always horizontal...
					if(apexScan.nativeSystem == EquatorialCoordinates.class) 
						exposure.equatorialToHorizontal(exposure.horizontalOffset);

					if(chop != null) exposure.chopperPosition.setX(chop[t] * Unit.deg);
					exposure.chopperPhase = phase[t];			
					exposure.zenithTau = (float) zenithTau;
					exposure.nodFlag = nodPhase;

					if(chopper != null) if(!chopperIncluded) {
						// Add the chopping offsets to the horizontal coordinates and offsets
						exposure.horizontal.addX(exposure.chopperPosition.getX() / exposure.horizontal.cosLat());
						exposure.horizontalOffset.addX(exposure.chopperPosition.getX());
						// Add to the equatorial coordinate also...
						tempOffset.copy(exposure.chopperPosition);
						exposure.horizontalToEquatorial(tempOffset);
						exposure.equatorial.addOffset(tempOffset);
					}

					// Scrambling produces a messed-up map, which is suitable for  studying the noise properties
					// If the scanning is more or less centrally symmetric, the resulting 'noise' map is
					// representative of the non-scrambled map...
					if(hasOption("scramble")) {
						exposure.horizontalOffset.invert();
						exposure.chopperPosition.scaleX(-1.0);
						exposure.sinPA *= -1.0;
						exposure.cosPA *= -1.0;
						exposure.calcEquatorial();
					}		

					set(t, exposure);
				}		
			};
		}
	}

	public void readMonitor(BinaryTableHDU hdu) throws IOException, FitsException, HeaderCardException {}

	@SuppressWarnings("unchecked")
	@Override
	public FrameType getFrameInstance() {
		return (FrameType) new APEXFrame((APEXArrayScan<APEXArray<?>, APEXArraySubscan<APEXArray<?>, FrameType>>) scan);
	}
			
	public void fitsRCP() {
		System.err.println("   Using RCP data contained in the FITS.");
		for(APEXPixel pixel : instrument) pixel.position = (Vector2D) pixel.fitsPosition.clone();
	}
}
