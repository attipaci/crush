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
package crush.telescope.cso;

import java.util.Vector;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import crush.Channel;
import crush.Scan;
import crush.instrument.Rotating;
import crush.telescope.Mount;
import crush.telescope.TelescopeInstrument;
import jnum.Unit;
import jnum.Util;

public abstract class CSOInstrument<ChannelType extends Channel> extends TelescopeInstrument<ChannelType> implements Rotating {
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

    protected CSOInstrument(String name, int size) {
        super(name, size);
    }

    protected CSOInstrument(String name) {
        super(name);
    }

    @Override
    public CSOInstrument<ChannelType> copy() {
        CSOInstrument<ChannelType> copy = (CSOInstrument<ChannelType>) super.copy();
        return copy;
    }

    @Override
    public String getTelescopeName() {
        return "CSO";
    }

    public abstract double getLoadTemperature();

    @Override
    public void configure() {
        if(hasOption("excessload")) excessLoad = option("excessload").getDouble() * Unit.K;        
        checkRotation();
        super.configure();
    }


    protected void checkRotation() {
        // Check the instrument rotation...
        if(hasOption("rot0")) rotatorZeroAngle = option("rot0").getDouble() * Unit.deg;
        if(hasOption("rotation")) rotatorAngle = option("rotation").getDouble() * Unit.deg;	

        if(mount == Mount.CASSEGRAIN) {
            info("Rotator = " + Util.f1.format(rotatorAngle/Unit.deg) + " RotZero = " 
                    + Util.f1.format(rotatorZeroAngle/Unit.deg));

            StringBuffer buf = new StringBuffer();

            if(Math.abs(rotatorAngle - rotatorZeroAngle) > 5.0 * Unit.deg) {
                buf.append("\n");
                buf.append("**************************************************************************\n");		

                buf.append(getName().toUpperCase() + " is in non-standard orientation. "
                        + "Will assume that pointing was performed ");

                if(hasOption("rcenter"))
                    buf.append("in the horizontal orientation. To override this and to assume pointing in this "
                            + "rotation, use '-forget=rcenter'.\n");
                else
                    buf.append("in the same orientration. To override this and to assume pointing in horizontal "
                            + "orientation, set the 'rcenter' option.\n");	

                buf.append("**************************************************************************\n");

                warning(new String(buf));
            }
        }
        else info("Mounted at " + Util.f1.format(rotatorZeroAngle/Unit.deg) + " deg.");	
    }


    @Override
    public double getRotation() {
        return (mount == Mount.CASSEGRAIN ? rotatorAngle : 0.0) - rotatorZeroAngle;
    }


    public void parseScanPrimaryHDU(BasicHDU<?> hdu) throws FitsException {
        Header header = hdu.getHeader();

        // Platform
        String platform = header.getStringValue("PLATFORM");
        if(platform == null) platform = "Cassegrain";

        mount =  platform.equalsIgnoreCase("NASMYTH") ? Mount.RIGHT_NASMYTH : Mount.CASSEGRAIN;

        info(mount.name + " mount assumed.");

        rotatorZeroAngle = header.getDoubleValue("ROTZERO", Double.NaN) * Unit.deg;
        rotatorAngle = header.getDoubleValue("ROTATOR", rotatorZeroAngle / Unit.deg) * Unit.deg;
        rotatorOffset = header.getDoubleValue("ROTOFFST", 0.0) * Unit.deg;
        rotatorMode = header.getStringValue("ROTMODE");

        if(rotatorMode == null) rotatorMode = "Unknown";

        // Various fixes for premature FITS files, without valid rotator information
        // These typically have 1000 values.
        if(rotatorZeroAngle == 1000.0 * Unit.deg) {
            rotatorZeroAngle = 16.0 * Unit.deg;
            info(">>> Fix: missing rotator zero angle set to 16.0 deg.");
        }
        if(rotatorAngle == 1000.0 * Unit.deg) {
            rotatorAngle = Double.NaN;
            info(">>> Fix: missing rotator angle..");
        }
        if(rotatorOffset == 1000.0 * Unit.deg) {
            rotatorOffset = 0.0;
            info(">>> Fix: assuming no rotator offset.");
        }

        // Focus
        focusX =  header.getDoubleValue("FOCUS_X") * Unit.mm;
        focusY =  header.getDoubleValue("FOCUS_Y") * Unit.mm;
        focusZ =  header.getDoubleValue("FOCUS_Z") * Unit.mm;

        focusYOffset =  header.getDoubleValue("FOCUS_YO") * Unit.mm;
        focusZOffset =  header.getDoubleValue("FOCUS_ZO") * Unit.mm;

        focusMode = header.getStringValue("FOCMODE");
        if(focusMode == null) focusMode = "Unknown";

        info("Focus [" + focusMode + "]"
                + " X=" + Util.f2.format(focusX / Unit.mm)
                + " Y=" + Util.f2.format(focusY / Unit.mm)
                + " Z=" + Util.f2.format(focusZ / Unit.mm)
                + " Yoff=" + Util.f2.format(focusYOffset / Unit.mm) 
                + " Zoff=" + Util.f2.format(focusZOffset / Unit.mm)
                );

        // DSOS
        dsosUsed = header.getBooleanValue("DSOS");
        dsosVersion = header.getStringValue("DSOSVER");

        if(dsosUsed) info("DSOS version " + dsosVersion);

    }
    

    @Override
    public void validate(Vector<Scan<?>> scans) throws Exception {
        final CSOScan<?> firstScan = (CSOScan<?>) scans.get(0);
        if(scans.size() == 1) if(firstScan.getObservingTime() < 3.3 * Unit.min) firstScan.setSuggestPointing();
        super.validate(scans);
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("dsos?")) return dsosUsed;
        if(name.equals("foc.X")) return focusX / Unit.mm;
        if(name.equals("foc.Y")) return focusY / Unit.mm;
        if(name.equals("foc.Z")) return focusZ / Unit.mm;
        if(name.equals("foc.dY")) return focusYOffset / Unit.mm;
        if(name.equals("foc.dZ")) return focusZOffset / Unit.mm;
        if(name.equals("foc.mode")) return focusMode;
        if(name.equals("rot")) return rotatorAngle / Unit.deg;
        if(name.equals("rot0")) return rotatorZeroAngle / Unit.deg;
        if(name.equals("rotoff")) return rotatorOffset / Unit.deg;
        if(name.equals("rotMode")) return rotatorMode;
        if(name.equals("load")) return excessLoad / Unit.K;
        return super.getTableEntry(name);
    }

}
