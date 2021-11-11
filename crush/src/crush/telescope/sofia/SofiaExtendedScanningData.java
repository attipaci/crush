/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/

package crush.telescope.sofia;

import jnum.Unit;
import jnum.fits.FitsToolkit;
import jnum.math.Vector2D;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public class SofiaExtendedScanningData extends SofiaScanningData {
    String pattern, coordinateSystem;
    Vector2D amplitude = new Vector2D(Double.NaN, Double.NaN);
    double currentPositionAngle = Double.NaN, duration = Double.NaN, relFrequency = Double.NaN, relPhase = Double.NaN;
    double t0 = Double.NaN, gyroTimeWindow = Double.NaN;
    int iterations = UNKNOWN_INT_VALUE, subscans = UNKNOWN_INT_VALUE;
    
    double rasterLength = Double.NaN, rasterStep = Double.NaN;
    boolean isCrossScanning = false;
    int nSteps = UNKNOWN_INT_VALUE;
    int trackingEnabled = UNKNOWN_INT_VALUE;
    
    public BracketedValues positionAngle = new BracketedValues();
       

    public SofiaExtendedScanningData() {}

    public SofiaExtendedScanningData(SofiaHeader header) {
        this();
        parseHeader(header);
    }

    @Override
    public void parseHeader(SofiaHeader header) {
        super.parseHeader(header);
        
        pattern = header.getString("SCNPATT");
        coordinateSystem = header.getString("SCNCRSYS");
        
        amplitude = new Vector2D(header.getDouble("SCNAMPXL"), header.getDouble("SCNAMPEL"));
        amplitude.scale(Unit.arcsec);
        
        currentPositionAngle = header.getDouble("SCNANGLC") * Unit.deg;
        positionAngle = new BracketedValues(header.getDouble("SCNANGLS") * Unit.deg, header.getDouble("SCNANGLF") * Unit.deg);
         
        duration = header.getDouble("SCNDUR") * Unit.s;
        iterations = header.getInt("SCNITERS");
        subscans = header.getInt("SCNNSUBS");
        
        rasterLength = header.getDouble("SCNLEN") * Unit.arcsec;
        rasterStep = header.getDouble("SCNSTEP") * Unit.arcsec;
        nSteps = header.getInt("SCNSTEPS");
        isCrossScanning = header.getBoolean("SCNCROSS");
        
        relFrequency = header.getDouble("SCNFQRAT");
        relPhase = header.getDouble("SCNPHASE") * Unit.deg;
        t0 = header.getDouble("SCNTOFF") * Unit.s;
        gyroTimeWindow = header.getDouble("SCNTWAIT") * Unit.s;
        
        trackingEnabled = header.getInt("SCNTRKON");
    }
        
    @Override
    public void editHeader(Header header) throws HeaderCardException {
        super.editHeader(header);
        
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(HeaderCard.createCommentCard("<------ SOFIA Extra Scanning Data ------>"));
                
        c.add(makeCard("SCNPATT", pattern, "Scan pattern."));
        c.add(makeCard("SCNCRSYS", coordinateSystem, "Scan coordinate system."));
        c.add(makeCard("SCNANGLC", currentPositionAngle / Unit.deg, "(deg) current scan angle."));
        c.add(makeCard("SCNANGLS", positionAngle.start / Unit.deg, "(deg) initial scan angle."));
        c.add(makeCard("SCNANGLF", positionAngle.end / Unit.deg, "(deg) final scan angle."));
        c.add(makeCard("SCNDUR", duration / Unit.s, "(s) scan duration."));
        c.add(makeCard("SCNITERS", iterations, "scan iterations."));
        c.add(makeCard("SCNNSUBS", subscans, "number of subscans."));
        c.add(makeCard("SCNTRKON", trackingEnabled, "[0,1] Is tracking enabled?"));
        
        c.add(makeCard("SCNTWAIT", gyroTimeWindow / Unit.s, "(s) Track relock time window."));       
        c.add(makeCard("SCNLEN", rasterLength / Unit.arcsec, "(arcsec) Raster scan length."));
        c.add(makeCard("SCNSTEP", rasterStep / Unit.arcsec, "(arcsec) Raster scan step size."));
        c.add(makeCard("SCNSTEPS", nSteps, "Raster scan steps."));
        c.add(makeCard("SCNCROSS", isCrossScanning, "cross scanning?"));
        
        Vector2D v = amplitude == null ? new Vector2D(Double.NaN, Double.NaN) : amplitude;
        c.add(makeCard("SCNAMPXL", v.x() / Unit.arcsec, "(arcsec) cross-elevation amplitude."));
        c.add(makeCard("SCNAMPEL", v.y() / Unit.arcsec, "(arcsec) elevation amplitude."));
        c.add(makeCard("SCNFQRAT", relFrequency, "Lissajous y/x frequency ratio."));
        c.add(makeCard("SCNPHASE", relPhase / Unit.deg, "(deg) Lissajous y/x relative phase."));
        c.add(makeCard("SCNTOFF", t0 / Unit.s, "(s) Lissajous time offset."));
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("pattern")) return pattern;
        else if(name.equals("sys")) return coordinateSystem;
        else if(name.equals("PA")) return currentPositionAngle / Unit.deg;
        else if(name.equals("T")) return duration / Unit.s;
        else if(name.equals("iters")) return iterations;
        else if(name.equals("nsub")) return subscans;
        else if(name.equals("trk")) return trackingEnabled == UNKNOWN_INT_VALUE ? "?" : (trackingEnabled != 0);
        else if(name.equals("X")) return rasterLength / Unit.arcsec;
        else if(name.equals("dY")) return rasterStep / Unit.arcsec;
        else if(name.equals("strips")) return nSteps;
        else if(name.equals("cross?")) return isCrossScanning;
        else if(name.equals("Ax")) return amplitude.x() / Unit.arcsec;
        else if(name.equals("Ay")) return amplitude.y() / Unit.arcsec;
        else if(name.equals("frel")) return relFrequency;
        else if(name.equals("phi0")) return relPhase / Unit.deg;
        else if(name.equals("t0")) return t0 / Unit.s;
        return super.getTableEntry(name);
    }

    
}
