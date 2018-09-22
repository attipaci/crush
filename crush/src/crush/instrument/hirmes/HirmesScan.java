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

package crush.instrument.hirmes;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.Header;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import crush.CRUSH;
import crush.telescope.sofia.GyroDrifts;
import crush.telescope.sofia.SofiaExtendedScanningData;
import crush.telescope.sofia.SofiaHeader;
import crush.telescope.sofia.SofiaScan;
import crush.telescope.sofia.SofiaScanningData;
import jnum.Unit;
import jnum.Util;
import jnum.astro.EquatorialCoordinates;
import jnum.math.Offset2D;
import jnum.math.Vector2D;

public class HirmesScan extends SofiaScan<Hirmes, HirmesIntegration> {    
    /**
     * 
     */
    private static final long serialVersionUID = 730005029452978874L;
      
    GyroDrifts gyroDrifts;
    boolean useBetweenScans;    

    double transitTolerance = Double.NaN;

    double focusTOffset;


    public HirmesScan(Hirmes instrument) {
        super(instrument);
        // Turn off warnings about multiple occurences of header keys...
        if(!CRUSH.debug) Logger.getLogger(Header.class.getName()).setLevel(Level.SEVERE);
    }

    @Override
    public HirmesIntegration getIntegrationInstance() {
        return new HirmesIntegration(this);
    }

    @Override
    protected boolean isFileNameMatching(String fileName, int flightNo, int scanNo) {
        if(super.isFileNameMatching(fileName, flightNo, scanNo)) return true;

        // TODO check... E.g. F0004_HC_IMA_0_HIR_RAW_109.fits
        String upperCaseName = fileName.toUpperCase();

        // 1. Check if the file name contains the instrument ID...
        if(!upperCaseName.contains("_" + instrument.getFileID().toUpperCase())) return false;

        // 2. Check if the file name starts with the flight ID...
        String oldFlightID = "F" + Util.d4.format(flightNo) + "_"; 
        if(!upperCaseName.startsWith(oldFlightID)) return false;

        // 3. Check if the file name contains the scans ID and a '.fits' extension...
        String oldScanID = "_" + Util.d3.format(scanNo) + ".FITS";
        return upperCaseName.contains(oldScanID);         
    }

    @Override
    protected SofiaScanningData getScanningDataInstance(SofiaHeader header) {
        return new SofiaExtendedScanningData(header);
    }    
    
    @Override
    public void parseHeader(SofiaHeader header) throws Exception {

        isNonSidereal = hasOption("rtoc");

        if(hasOption("OBJRA") && hasOption("OBJDEC")) 
            objectCoords = new EquatorialCoordinates(header.getHMSTime("OBJRA") * Unit.timeAngle, header.getDMSAngle("OBJDEC"), telescope.epoch);

        super.parseHeader(header);  
      
        focusTOffset = header.getDouble("FCSTOFF") * Unit.um;
        if(!Double.isNaN(focusTOffset)) info("Focus T Offset: " + Util.f1.format(focusTOffset / Unit.um));    

        gyroDrifts = new GyroDrifts(this);
        gyroDrifts.parse(header);
    }
    

    @Override
    protected EquatorialCoordinates guessReferenceCoordinates(SofiaHeader header) {
        if(isNonSidereal) {
            info("Referencing images to real-time object coordinates.");
            return null;
        }
        return super.guessReferenceCoordinates(header);
    }

    @Override
    public void addIntegrationsFrom(BasicHDU<?>[] HDU) throws Exception {
        ArrayList<BinaryTableHDU> dataHDUs = new ArrayList<BinaryTableHDU>();

        for(int i=1; i<HDU.length; i++) if(HDU[i] instanceof BinaryTableHDU) {
            Header header = HDU[i].getHeader();
            String extName = header.getStringValue("EXTNAME");
            if(extName.equalsIgnoreCase("Timestream")) dataHDUs.add((BinaryTableHDU) HDU[i]);
        }

        HirmesIntegration integration = this.getIntegrationInstance();
        integration.read(dataHDUs);
        add(integration);
    }
    

    @Override
    public void validate() {
        if(hasOption("chopper.tolerance")) transitTolerance = Math.abs(option("chopper.tolerance").getDouble());
        useBetweenScans = hasOption("betweenscans");

        super.validate();       
        
        if(isNonSidereal) {
            EquatorialCoordinates first = getFirstIntegration().getFirstFrame().objectEq;
            EquatorialCoordinates last = getLastIntegration().getLastFrame().objectEq;
            Vector2D offset = last.getOffsetFrom(first);
            if(offset.isNull()) {
                info("Scan appears to be sidereal with real-time object coordinates...");
                isNonSidereal = false;
            }
        }
    }


    @Override
    public Vector2D getNominalPointingOffset(Offset2D nativePointing) {
        Vector2D offset = super.getNominalPointingOffset(nativePointing); 
        offset.subtract(getFirstIntegration().getMeanChopperPosition());
        return offset;
    }    
    
    @Override
    public Object getTableEntry(String name) {
        if(name.equals("hirmes.dfoc")) return focusTOffset / Unit.um;
        if(name.equals("gyro.max")) return gyroDrifts.getMax() / Unit.arcsec;
        if(name.equals("gyro.rms")) return gyroDrifts.getRMS() / Unit.arcsec;
        return super.getTableEntry(name);
    }
}
