/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
package crush.cso;

import java.text.NumberFormat;
import java.util.Vector;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import crush.GroundBased;
import crush.InstantFocus;
import crush.Mount;
import crush.Scan;
import crush.array.Array;
import crush.array.Rotating;
import crush.array.SingleColorPixel;
import jnum.Unit;
import jnum.Util;
import jnum.data.DataPoint;
import jnum.text.TableFormatter;
import crush.array.SingleColorLayout;

public abstract class CSOArray<PixelType extends SingleColorPixel> extends Array<PixelType, PixelType> implements GroundBased, Rotating {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1931634231709037524L;

	public double rotatorAngle, rotatorZeroAngle, rotatorOffset;
	public String rotatorMode;
	
	public double focusX, focusY, focusZ;
	public double focusYOffset, focusZOffset;
	public String focusMode;
	
	public boolean dsosUsed;
	public String dsosVersion;
	
	public double excessLoad = 0.0;
	
	public CSOArray(String name, int size) {
		super(name, new SingleColorLayout<PixelType>(), size);
	}

	public CSOArray(String name) {
		super(name, new SingleColorLayout<PixelType>());
	}
	
	@Override
	public String getTelescopeName() {
		return "CSO";
	}
	

	public abstract double getLoadTemperature();
	
	@Override
	public void validate(Vector<Scan<?,?>> scans) throws Exception {
		
		final CSOScan<?,?> firstScan = (CSOScan<?,?>) scans.get(0);
		
		if(scans.size() == 1) if(firstScan.getObservingTime() < 3.3 * Unit.min) setPointing(firstScan);
		
		super.validate(scans);
	}
	
	@Override
	public void validate(Scan<?,?> scan) {
		if(hasOption("excessload")) excessLoad = option("excessload").getDouble() * Unit.K;
		super.validate(scan);
	}
	
	
	protected void checkRotation() {
		// Check the instrument rotation...
		if(hasOption("rot0")) rotatorZeroAngle = option("rot0").getDouble() * Unit.deg;
		if(hasOption("rotation")) rotatorAngle = option("rotation").getDouble() * Unit.deg;	
		
		if(mount == Mount.CASSEGRAIN) {
			System.out.println(" Rotator = " + Util.f1.format(rotatorAngle/Unit.deg) + " RotZero = " 
					+ Util.f1.format(rotatorZeroAngle/Unit.deg));
	
			if(Math.abs(rotatorAngle - rotatorZeroAngle) > 5.0 * Unit.deg) {
				System.err.println(" *****************************************************************************");
				warning(getName().toUpperCase() + " is in non-standard orientation. Will assume that pointing");
				if(hasOption("rcenter")) {
					System.err.println("          was performed in the horizontal orientation. To override this and to");
					System.err.println("          assume pointing in this rotation, use '-forget=rcenter'.");
				}
				else {
					System.err.println("          was performed in the same orientration. To override this and to");
					System.err.println("          assume pointing in horizontal orientation, set the 'rcenter' option.");
				}
				System.err.println(" *****************************************************************************");
			}
		}
		else System.out.println(" Mounted at " + Util.f1.format(rotatorZeroAngle/Unit.deg) + " deg.");	
	}

	
	@Override
	public double getRotation() {
		return (mount == Mount.CASSEGRAIN ? rotatorAngle : 0.0) - rotatorZeroAngle;
	}

	public void parseScanPrimaryHDU(BasicHDU<?> hdu) throws HeaderCardException, FitsException {
		Header header = hdu.getHeader();
		
		// Platform
		String platform = header.getStringValue("PLATFORM");
		if(platform == null) platform = "Cassegrain";
		
		mount =  platform.equalsIgnoreCase("NASMYTH") ? Mount.RIGHT_NASMYTH : Mount.CASSEGRAIN;
		
		System.err.println(" " + mount.name + " mount assumed.");
		
		rotatorZeroAngle = header.getDoubleValue("ROTZERO", Double.NaN) * Unit.deg;
		rotatorAngle = header.getDoubleValue("ROTATOR", rotatorZeroAngle / Unit.deg) * Unit.deg;
		rotatorOffset = header.getDoubleValue("ROTOFFST", 0.0) * Unit.deg;
		rotatorMode = header.getStringValue("ROTMODE");
	
		if(rotatorMode == null) rotatorMode = "Unknown";
			
		// Various fixes for premature FITS files, without valid rotator information
		// These typically have 1000 values.
		if(rotatorZeroAngle == 1000.0 * Unit.deg) {
			rotatorZeroAngle = 16.0 * Unit.deg;
			System.err.println(" >>> Fix: missing rotator zero angle set to 16.0 deg.");
		}
		if(rotatorAngle == 1000.0 * Unit.deg) {
			rotatorAngle = Double.NaN;
			System.err.println(" >>> Fix: missing rotator angle..");
		}
		if(rotatorOffset == 1000.0 * Unit.deg) {
			rotatorOffset = 0.0;
			System.err.println(" >>> Fix: assuming no rotator offset.");
		}
			
		// Focus
		focusX =  header.getDoubleValue("FOCUS_X") * Unit.mm;
		focusY =  header.getDoubleValue("FOCUS_Y") * Unit.mm;
		focusZ =  header.getDoubleValue("FOCUS_Z") * Unit.mm;

		focusYOffset =  header.getDoubleValue("FOCUS_YO") * Unit.mm;
		focusZOffset =  header.getDoubleValue("FOCUS_ZO") * Unit.mm;

		focusMode = header.getStringValue("FOCMODE");
		if(focusMode == null) focusMode = "Unknown";
		
		System.err.println(" Focus [" + focusMode + "]"
				+ " X=" + Util.f2.format(focusX / Unit.mm)
				+ " Y=" + Util.f2.format(focusY / Unit.mm)
				+ " Z=" + Util.f2.format(focusZ / Unit.mm)
				+ " Yoff=" + Util.f2.format(focusYOffset / Unit.mm) 
				+ " Zoff=" + Util.f2.format(focusZOffset / Unit.mm)
		);

		// DSOS
		dsosUsed = header.getBooleanValue("DSOS");
		dsosVersion = header.getStringValue("DSOSVER");
		
		if(dsosUsed) System.err.println(" DSOS version " + dsosVersion);
		
	}
	
	@Override
	public String getFocusString(InstantFocus focus) {
		String info = "";
		
		focus = new InstantFocus(focus);
		
		boolean largeLateral = false;
		boolean suggested = false;
		boolean suggestX = false;
		boolean suggestY = false;
		
		
		if(focus.getX() != null) {
			DataPoint x = focus.getX();
			//if(x.significance() > 2.0) largeLateral = true;
			x.add(focusX);
			info += "\n  UIP> x_position " + Util.f2.format(x.value() / Unit.mm) 
					+ "       \t[+-" + Util.f2.format(x.rms() / Unit.mm) + "]";	
			suggested = true;
			suggestX = true;
		}
		if(focus.getY() != null) {
			DataPoint dy = focus.getY();
			//if(dy.significance() > 2.0) largeLateral = true;
			dy.add(focusYOffset);
			info += "\n  UIP> y_position /offset " + Util.f2.format(dy.value() / Unit.mm)
					+ "\t[+-" + Util.f2.format(dy.rms() / Unit.mm) + "]";	
			suggested = true;
			suggestY = true;
		}
		if(focus.getZ() != null && !largeLateral) {			
			DataPoint dz = focus.getZ();
			dz.add(focusZOffset);
			info += "\n  UIP> focus /offset " + Util.f2.format(dz.value() / Unit.mm)
					+ "    \t[+-" + Util.f2.format(dz.rms() / Unit.mm) + "]";
			suggested = true;
		}
		
		if(suggested) info += "\n  UIP> focus \t\t\t!!! apply settings !!!";
		if(suggestX) info += "\n  UIP> focus /constant \t\t!!! IMPORTANT when x-focus is changed !!!";
		
		if(suggestX || suggestY) info +=  "\n\n" +
				"  WARNING! After changing lateral focus, do not forget to re-measure and\n" +
				"           adjust the pointing as necessary!!!";	
		
		if(suggested) {
			info += "\n\n";
			info += "  Note: The instant focus feature of CRUSH is very experimental.\n" +
				"        It may be used to guesstimate focus corrections on truly point-like\n" +
				"        sources (D < 4\"). You should always verify that the beam shape is\n" +
				"        round and symmetric after applying any of the suggested corrections.";
		}
			
		return info;
	}
	
	
	@Override
	public String getFormattedEntry(String name, String formatSpec) {
		NumberFormat f = TableFormatter.getNumberFormat(formatSpec);
	
		if(name.equals("dsos?")) return Boolean.toString(dsosUsed);
		else if(name.equals("foc.X")) return Util.defaultFormat(focusX / Unit.mm, f);
		else if(name.equals("foc.Y")) return Util.defaultFormat(focusY / Unit.mm, f);
		else if(name.equals("foc.Z")) return Util.defaultFormat(focusZ / Unit.mm, f);
		else if(name.equals("foc.dY")) return Util.defaultFormat(focusYOffset / Unit.mm, f);
		else if(name.equals("foc.dZ")) return Util.defaultFormat(focusZOffset / Unit.mm, f);
		else if(name.equals("foc.mode")) return focusMode;
		else if(name.equals("rot")) return Util.defaultFormat(rotatorAngle / Unit.deg, f);
		else if(name.equals("rot0")) return Util.defaultFormat(rotatorZeroAngle / Unit.deg, f);
		else if(name.equals("rotoff")) return Util.defaultFormat(rotatorOffset / Unit.deg, f);
		else if(name.equals("rotMode")) return rotatorMode;
		else if(name.equals("load")) return Util.defaultFormat(excessLoad / Unit.K, f);
		else return super.getFormattedEntry(name, formatSpec);
	}
	
}
