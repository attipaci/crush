/*******************************************************************************
 * Copyright (c) 2018 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope.apex;

import crush.*;
import nom.tam.fits.*;

import java.io.*;

import crush.fits.HDUReader;
import crush.telescope.Chopper;
import crush.telescope.Chopping;
import crush.telescope.GroundBasedIntegration;
import crush.telescope.TelescopeFrame;
import jnum.LockedException;
import jnum.Unit;
import jnum.Util;
import jnum.astro.CelestialCoordinates;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.HorizontalCoordinates;
import jnum.data.WeightedPoint;
import jnum.fits.FitsToolkit;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;

public class APEXSubscan<FrameType extends APEXFrame> extends GroundBasedIntegration<FrameType> implements PhaseModulated, Chopping {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2929947229904002745L;
	public int nodPhase = 0;
	protected Thread thread;
	protected WeightedPoint[] tempPhase;
	private Chopper chopper;
	
	double pwv = Double.NaN;
	
	public APEXSubscan(APEXScan<? extends APEXSubscan<? extends FrameType>> parent) {
		super(parent);
	}
	
    @SuppressWarnings("unchecked")
    @Override
    public APEXScan<? extends APEXSubscan<? extends FrameType>> getScan() { 
        return (APEXScan<? extends APEXSubscan<? extends FrameType>>) super.getScan(); 
    }
    
    
    @Override
    public APEXInstrument<?> getInstrument() { return (APEXInstrument<?>) super.getInstrument(); }
    
    public APEXScan<APEXSubscan<FrameType>> getScanInstance() {
        return new APEXScan<APEXSubscan<FrameType>>(getInstrument());
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
			catch(ArrayIndexOutOfBoundsException ie) { 
			    if(!Double.isNaN(pwv)) {
			        warning("No skydip for this date. Using PWV to determine tau...");
			        setTau("pwv", pwv);
			    }
			    else warning("No skydip tau for this date!"); 
			}
			catch(IOException io) { warning("Tau interpolator table could not be read."); }
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
			catch(ArrayIndexOutOfBoundsException e) { 
			    warning("No calibration scaling for this date. Assuming 1.0..."); 
			}
			catch(IOException e) { warning("Calibration table could not be read."); }
		}
	}
	
	public void markChopped() {
		if(nodPhase == 0) 
			throw new IllegalStateException("Merged subscan contains mixed nod phases. Cannot process chopper.");
		
		// Flag pixels that chop on source
		// left and right are the pixel positions, where, if there's a pixel, it will have the source
		// in the left or right beams...
		//info("on phase is " + integration[i][k].onPhase);
		
		
		
		Vector2D left = new Vector2D(nodPhase == TelescopeFrame.CHOP_LEFT ? 0.0 : 2.0 * getChopper().amplitude, 0.0);
		Vector2D right = new Vector2D(nodPhase == TelescopeFrame.CHOP_LEFT ? -2.0 * chopper.amplitude : 0.0, 0.0);
		
		// 1/5 beams ~90% on the boundary
		// 1/4 beams ~85% on the boundary
		// 1/3 beams ~75% on the boundary
		double tolerance = getInstrument().getPointSize() / 5.0;
		if(hasOption("pointing.tolerance")) tolerance = option("pointing.tolerance").getDouble() * getInstrument().getPointSize();
		
		
		markChopped(left, right, tolerance);
		
		tempPhase = WeightedPoint.createArray(chopper.phases.size());		
	}
	
	
	public void markChopped(Vector2D left, Vector2D right, double tolerance) {
		// Flag pixels that chop on source
		// left and right are the pixel positions, where, if there's a pixel, it will have the source
		// in the left or right beams...
		//info("on phase is " + subscan[i][k].onPhase);
		
	    StringBuffer buf = new StringBuffer();
	    
		buf.append("Marking Chopper Phases... ");
	
		for(Pixel pixel : getInstrument().getPixels()) {
			Vector2D position = pixel.getPosition();
			
			if(position.distanceTo(left) < tolerance) for(Channel channel : pixel) {
				channel.sourcePhase |= TelescopeFrame.CHOP_LEFT;
				buf.append(" L" + channel.getID());
			}
			else if(position.distanceTo(right) < tolerance) for(Channel channel : pixel) {
				channel.sourcePhase |= TelescopeFrame.CHOP_RIGHT;
				buf.append(" R" + channel.getID()); 
			}
			else for(Channel channel : pixel) channel.sourcePhase &= ~TelescopeFrame.CHOP_FLAGS;
		}
		
		info(new String(buf));
		
		chopper.phases = new PhaseSet(this);
		
		int usable = 0;
		
		// Flag frames according to chopper phase ---> left, right, transit.
		PhaseData current = new PhaseData(this);

		int transitFlag = TelescopeFrame.CHOP_TRANSIT | Frame.SKIP_MODELING | Frame.SKIP_WEIGHTING | Frame.SKIP_SOURCE_MODELING;
		
		for(TelescopeFrame exposure : this) if(exposure != null) {
			exposure.unflag(TelescopeFrame.CHOP_FLAGS);
				
			if(Math.abs(exposure.chopperPosition.x() + chopper.amplitude) < tolerance) {
				exposure.flag(TelescopeFrame.CHOP_LEFT);
				if(current.phase != TelescopeFrame.CHOP_LEFT) {
					current = new PhaseData(this);
					current.phase = TelescopeFrame.CHOP_LEFT;
					//if(current.phase == nodPhase) current.flag |= PhaseOffsets.SKIP_GAINS;
					current.start = exposure;
					current.end = exposure;
					if(current.phase != 0) chopper.phases.add(current);
				}
				else current.end = exposure;
				usable++;
			}
			else if(Math.abs(exposure.chopperPosition.x() - chopper.amplitude) < tolerance) {
				exposure.flag(TelescopeFrame.CHOP_RIGHT);
				if(current.phase != TelescopeFrame.CHOP_RIGHT) {
					current = new PhaseData(this);
					current.phase = TelescopeFrame.CHOP_RIGHT;
					//if(current.phase == nodPhase) current.flag |= PhaseOffsets.SKIP_GAINS;
					current.start = exposure;
					current.end = exposure;
					if(current.phase != 0) chopper.phases.add(current);
				}
				else current.end = exposure;
				usable++;
			}
			else exposure.flag(transitFlag);
		}
		
		chopper.efficiency = ((double) usable / size());
		
		CRUSH.values(this, "Chopper parameters: " + chopper.toString());
		
		chopper.phases.validate();
		
		// Discard transit frames altogether...
		for(int i=size(); --i >=0; ) {
			final Frame exposure = get(i);
			if(exposure != null) if(exposure.isFlagged(TelescopeFrame.CHOP_TRANSIT)) set(i, null);
		}
		
		removeOffsets(false);
		
		// Get the initial phase data...
		updatePhases();
		
	}
	
	@Override
	public void validate() {
		super.validate();	
		if(chopper != null) markChopped();
	}
	
	
	/*
	public void readData(Fits fits) throws Exception {
	    readData((BinaryTableHDU) fits.getHDU(1), fits.getStream());
	    fits.close();
	}
	
	public void readData(BinaryTableHDU hdu, ArrayDataInput in) throws Exception {	
		new DataTable(hdu, in).read(1);
	}

	class DataTable extends HDURowReader {
	    private int iData;
	       
        public DataTable(BinaryTableHDU hdu, ArrayDataInput in) throws FitsException { 
            super(hdu, in); 
            iData = hdu.findColumn("DATA");
        }
       
        @Override
        public Reader getReader() {
            return new Reader() {
                @Override
                public void processRow(int index, Object[] row) throws Exception {
                    final APEXFrame exposure = get(index);
                    if(exposure != null) exposure.parse((float[][]) row[iData]);  
                }
            };
        }
    }
    */
	
	public void readData(Fits fits) throws Exception {
        readData((BinaryTableHDU) fits.getHDU(1));
        fits.close();
    }
    

	public void readData(BinaryTableHDU hdu) throws Exception { 
	    new DataTable(hdu).read();
	}

	class DataTable extends HDUReader {
	    private float[] data;
	    private int channels;

	    public DataTable(TableHDU<?> hdu) throws FitsException { 
	        super(hdu); 
	        int iData = hdu.findColumn("DATA");
	        channels = table.getSizes()[iData];
	        data = (float[]) table.getColumn(iData);
	    }

	    @Override
	    public Reader getReader() {
	        return new Reader() {
	            @Override
	            public void processRow(int t) throws FitsException {
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
			for(Channel channel : getInstrument()) data[channel.getFixedIndex()][0] = exposure.data[channel.index];
			hdu.setRow(exposure.index, row);
		}	
		
		FitsToolkit.write(fits, toName);
		fits.close();
	}
	
	public void readDataPar(Fits fits) throws Exception {
	    readDataPar((BinaryTableHDU) fits.getHDU(1));
	    fits.close();
	}
	
	public void readDataPar(BinaryTableHDU hdu) throws Exception {
		final Header header = hdu.getHeader();	
		final int frames = header.getIntValue("NAXIS2");
		
		clear();
		ensureCapacity(frames);
		for(int i=frames; --i >= 0; ) add(null);
		
		getInstrument().samplingInterval = getInstrument().integrationTime = ((double[]) hdu.getRow(0)[hdu.findColumn("INTEGTIM")])[0] * Unit.s;
		// Use the integrationTime to convert to data weights...
		getInstrument().sampleWeights();
		
		info("Sampling at " + Util.f3.format(1.0 / getInstrument().samplingInterval) + " Hz.");
	
		info(frames + " frames found (" + 
				Util.f1.format(frames * getInstrument().samplingInterval / Unit.min) + " minutes).");
	
		new DataParTable(hdu).read(); 
	}
	
	class DataParTable extends HDUReader {
		private double[] MJD, LST, X, Y, DX, DY, chop, objX, objY;
		private int[] phase;
		private boolean chopperIncluded;
		private final static double m900 = -900.0;
		
		public DataParTable(TableHDU<?> hdu) throws FitsException {
			super(hdu);

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

			int iOX = hdu.findColumn("MCRVAL1");
            int iOY = hdu.findColumn("MCRVAL2");
			
            if(iOX >= 0 && iOY >= 0) {
                objX = (double[]) table.getColumn(iOX);
                objY = (double[]) table.getColumn(iOY);
                
                for(int i=objX.length; --i >= 0; ) if(!Double.isNaN(objX[i])) if(objX[i] > m900) {
                    info("Non-sidereal tracking detected...");
                    getScan().isNonSidereal = true;
                    break;
                }
            }
            
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

			nodPhase = TelescopeFrame.CHOP_LEFT;

			if(phase1 != null) {
				if(phase1.equalsIgnoreCase("WON")) nodPhase = TelescopeFrame.CHOP_LEFT;
				else if(phase1.equalsIgnoreCase("WOFF")) nodPhase = TelescopeFrame.CHOP_RIGHT;
			}
			else if(phase2 != null) {
				if(phase2.equalsIgnoreCase("WON")) nodPhase = TelescopeFrame.CHOP_RIGHT;
				else if(phase2.equalsIgnoreCase("WOFF")) nodPhase = TelescopeFrame.CHOP_LEFT;
			}

			if(getScan().chopper != null) chopper = getScan().chopper.copy();

			if(chopper != null)
			    info("Nodding " + (nodPhase == TelescopeFrame.CHOP_LEFT ? "[LEFT]" : 
					(nodPhase == TelescopeFrame.CHOP_RIGHT ? "[RIGHT]" : "[???]")));		
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
					Class<? extends SphericalCoordinates> basisSystem = getScan().basisSystem;
					
					if(basisSystem != HorizontalCoordinates.class && basisSystem != EquatorialCoordinates.class) {
						try { basisCoords = (CelestialCoordinates) basisSystem.getConstructor().newInstance(); }
						catch(Exception e) {
							throw new IllegalStateException("Cannot instantiate " + basisSystem.getName() +
									": " + e.getMessage());
						}
					}					
				}
				
				@Override
				public void processRow(int t) throws FitsException {
				
					// Continue only if the basis coordinates are valid...
					// APEX uses -999 deg to mark invalid data...
					if(X[t] < m900) return;
					if(Y[t] < m900) return;

					// Continue only if the scanning offsets are valid...
					// APEX uses -999 deg to mark invalid data...
					if(DX[t] < m900) return;
					if(DY[t] < m900) return;
			
					// Create the frame object once the hurdles above are cleared...
					final FrameType exposure = getFrameInstance();
					exposure.index = t;

					exposure.MJD = MJD[t];
					exposure.LST = LST[t];
					
					boolean hasObjectCoords = objX == null ? false : getScan().isNonSidereal && objX[t] > m900 && objY[t] > m900;
					
					if(basisCoords != null) {
						basisCoords.set(X[t] * Unit.deg, Y[t] * Unit.deg);
						exposure.equatorial = basisCoords.toEquatorial();
						exposure.calcHorizontal();
					}	
					else if(getScan().basisSystem == EquatorialCoordinates.class) {
						exposure.equatorial = new EquatorialCoordinates(X[t] * Unit.deg, Y[t] * Unit.deg, getScan().equatorial.epoch);
						exposure.calcHorizontal();
					}
					else {
					    exposure.horizontal = new HorizontalCoordinates(X[t] * Unit.deg, Y[t] * Unit.deg);
					    exposure.calcEquatorial();
					}

                    exposure.calcParallacticAngle();
            
					// Equatorial offset to moving reference...
					if(hasObjectCoords) {
					    if(basisCoords != null) {
                            basisCoords.set(objX[t] * Unit.deg, objY[t] * Unit.deg);
                            exposure.horizontalOffset = exposure.equatorial.getNativeOffsetFrom(basisCoords.toEquatorial());
                            exposure.equatorialNativeToHorizontal(exposure.horizontalOffset);
                        }
					    else if(getScan().basisSystem == EquatorialCoordinates.class) { 
					        exposure.horizontalOffset = exposure.equatorial.getNativeOffsetFrom(
                                    new EquatorialCoordinates(objX[t] * Unit.deg, objY[t] * Unit.deg, getScan().equatorial.epoch)
                            );
					        exposure.equatorialNativeToHorizontal(exposure.horizontalOffset);
					    }
					    else {
					        exposure.horizontalOffset = exposure.horizontal.getOffsetFrom(
	                                new HorizontalCoordinates(objX[t] * Unit.deg, objY[t] * Unit.deg)
	                        );
					    }
					}
					// Else just rely on the scanning offsets...
					else {
					    exposure.horizontalOffset = new Vector2D(DX[t] * Unit.deg, DY[t] * Unit.deg);
					
					    if(getScan().nativeSystem == EquatorialCoordinates.class)
					        exposure.equatorialToHorizontal(exposure.horizontalOffset);
					}
					    
					exposure.zenithTau = (float) zenithTau;	
					
					exposure.chopperPhase = phase[t];          
                    exposure.nodFlag = nodPhase;
				    
					// Add the chopper offsets, if available...
					if(chop != null) if(chop[t] > m900) {			    
					    exposure.chopperPosition.setX(chop[t] * Unit.deg);
		
					    if(!chopperIncluded) {
					        // Add the chopping offsets to the horizontal coordinates and offsets
					        exposure.horizontal.addX(exposure.chopperPosition.x() / exposure.horizontal.cosLat());
					        exposure.horizontalOffset.addX(exposure.chopperPosition.x());
					        // Add to the equatorial coordinate also...
					        tempOffset.copy(exposure.chopperPosition);
					        exposure.horizontalToEquatorial(tempOffset);
					        exposure.equatorial.addOffset(tempOffset);
					    }
					}

					set(t, exposure);
				}		
			};
		}
	}
	
	public void readMonitor(Fits fits) throws IOException, FitsException, HeaderCardException {
	    readMonitor((BinaryTableHDU) fits.getHDU(1));
	    fits.close();
	}

	public void readMonitor(BinaryTableHDU hdu) throws IOException, FitsException, HeaderCardException {    
	    
	    if(hasOption("tau.pwv")) return;
	    
        final int n = hdu.getNRows();
        final int iLABEL = hdu.findColumn("MONPOINT");
        final int iVALUE = hdu.findColumn("MONVALUE");
        
        int N = 0;
        double sum = 0.0;
    
	    for(int i=0; i<n; i++) {
            Object[] row = hdu.getRow(i);
             
            if(((String) row[iLABEL]).equals("PWV")) {     
                sum += ((double[]) row[iVALUE])[0];
                N++;
            }
        }
        
	    if(N == 0) {
	        if(!hasOption("tau")) warning("PWV not recorded in FITS. Set tau explicitly or via lookup table.");
	        return;
	    }
	    
	    pwv = sum / N; 
	    
	    if(!hasOption("tau.pwv")) {
            try { getInstrument().getOptions().process("tau.pwv", pwv + ""); } 
            catch (LockedException e) {}
	    }
	    
        info("--> PWV = " + Util.f3.format(pwv) + " mm, from " + N + " entries.");
	}

	@SuppressWarnings("unchecked")
	@Override
	public FrameType getFrameInstance() {
		return (FrameType) new APEXFrame(getScan());
	}
			
	public void fitsRCP() {
		info("Using RCP data contained in the FITS.");
		for(APEXContinuumPixel pixel : getInstrument()) pixel.position = (Vector2D) pixel.fitsPosition.clone();
	}

	@Override
    public int getPhase() { return nodPhase; }
	
	@Override
	public PhaseSet getPhases() {
		Chopper chopper = getChopper();
		return chopper == null ? null : chopper.phases;
	}

	@Override
	public Chopper getChopper() {
		return chopper;
	}

	@Override
	public void setChopper(Chopper chopper) {
		this.chopper = chopper;
	}
}
