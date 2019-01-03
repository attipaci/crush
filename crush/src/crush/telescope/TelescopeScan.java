/*******************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope;


import crush.Integration;
import crush.Scan;
import jnum.Unit;
import jnum.Util;
import jnum.astro.CelestialCoordinates;
import jnum.astro.CoordinateEpoch;
import jnum.astro.EclipticCoordinates;
import jnum.astro.EquatorialCoordinates;
import jnum.astro.FocalPlaneCoordinates;
import jnum.astro.GalacticCoordinates;
import jnum.astro.JulianEpoch;
import jnum.astro.Precession;
import jnum.data.image.region.GaussianSource;
import jnum.fits.FitsToolkit;
import jnum.math.Offset2D;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.util.DataTable;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;
import nom.tam.util.Cursor;

public abstract class TelescopeScan<InstrumentType extends TelescopeInstrument<?>, IntegrationType extends Integration<InstrumentType, ? extends TelescopeFrame>> 
extends Scan<InstrumentType, IntegrationType> {
    
    /**
     * 
     */
    private static final long serialVersionUID = -2960364379817949696L;

    
    public EquatorialCoordinates equatorial, apparent;
    public Precession fromApparent, toApparent;
    
    public TelescopeScan(InstrumentType instrument) {
        super(instrument);
    }
    

    @Override
    public SphericalCoordinates getNativeCoordinates() { return equatorial; }

    @Override
    public double getObservingTime() {
        return getObservingTime(~(TelescopeFrame.CHOP_LEFT | TelescopeFrame.CHOP_RIGHT));
    }

    
    @Override
    public void validate() {
        if(!hasOption("lab")) {
            // Use J2000 coordinates
            if(!equatorial.epoch.equals(CoordinateEpoch.J2000)) precess(CoordinateEpoch.J2000);
            info("  Equatorial: " + equatorial.toString());

            // Calculate apparent and approximate horizontal coordinates.... 
            if(apparent == null) calcApparent();
        }
        
        super.validate();
    }
    
    
    public void precess(CoordinateEpoch epoch) {
        Precession toEpoch = new Precession(equatorial.epoch, epoch);
        toEpoch.precess(equatorial);
        for(Integration<?,? extends TelescopeFrame> integration : this) for(TelescopeFrame frame : integration) 
            if(frame != null) if(frame.equatorial != null) toEpoch.precess(frame.equatorial);   
        calcPrecessions(epoch);
    }
    
    public void calcPrecessions(CoordinateEpoch epoch) {
        JulianEpoch apparentEpoch = JulianEpoch.forMJD(getMJD());
        fromApparent = new Precession(apparentEpoch, epoch);
        toApparent = new Precession(epoch, apparentEpoch);
    }
        

    
    public void calcApparent() {
        apparent = (EquatorialCoordinates) equatorial.clone();
        if(toApparent == null) calcPrecessions(equatorial.epoch);
        toApparent.precess(apparent);
    }


    @Override
    public SphericalCoordinates getPositionReference(String system) {
        if(system.equals("native")) return getNativeCoordinates(); 
        else if(system.equals("focalplane")) return new FocalPlaneCoordinates(); 
        else if(isNonSidereal) return equatorial;
        else if(system.equals("ecliptic")) {
            EclipticCoordinates ecliptic = new EclipticCoordinates();
            ecliptic.fromEquatorial(equatorial);
            return ecliptic;
        }
        else if(system.equals("galactic")) {
            GalacticCoordinates galactic = new GalacticCoordinates();
            galactic.fromEquatorial(equatorial);
            return galactic;
        }
        else if(system.equals("supergalactic")) {
            EclipticCoordinates sg = new EclipticCoordinates();
            sg.fromEquatorial(equatorial);
            return sg;
        }
        else return equatorial;
    }
    

    public Vector2D getEquatorialPointing(GaussianSource source) {
        if(!source.getCoordinates().getClass().equals(sourceModel.getReference().getClass()))
            throw new IllegalArgumentException("pointing source is in a different coordinate system from source model.");
        
        
        EquatorialCoordinates sourceCoords = null;
        EquatorialCoordinates reference = null;
        
        if(source.getCoordinates() instanceof EquatorialCoordinates) {
            sourceCoords = (EquatorialCoordinates) source.getCoordinates();
            reference = (EquatorialCoordinates) sourceModel.getReference();
            if(!sourceCoords.epoch.equals(equatorial.epoch)) sourceCoords.precess(equatorial.epoch);
        }
        else {
            sourceCoords = (EquatorialCoordinates) equatorial.clone();
            reference = (EquatorialCoordinates) equatorial.clone();
            ((CelestialCoordinates) source.getCoordinates()).toEquatorial(sourceCoords);
            ((CelestialCoordinates) sourceModel.getReference()).toEquatorial(reference);
        }
            
        return sourceCoords.getOffsetFrom(reference);
    }
    
    
    @Override
    public Offset2D getNativePointing(GaussianSource source) {
        Offset2D pointing = getNativePointingIncrement(source);
        if(pointingCorrection != null) pointing.add(pointingCorrection);
        return pointing;
    }
    
    
    @Override
    public Offset2D getNativePointingIncrement(GaussianSource source) {
        if(!source.getCoordinates().getClass().equals(sourceModel.getReference().getClass()))
            throw new IllegalArgumentException("pointing source is in a different coordinate system from source model.");
        
        SphericalCoordinates sourceCoords = (SphericalCoordinates) source.getCoordinates();
        SphericalCoordinates nativeCoords = getNativeCoordinates();
        
        SphericalCoordinates reference = (SphericalCoordinates) sourceModel.getReference();
        
        if(sourceCoords.getClass().equals(nativeCoords.getClass())) {
            return new Offset2D(sourceModel.getReference(), sourceCoords.getOffsetFrom(reference));
        }
        else if(sourceCoords instanceof EquatorialCoordinates)
            return getNativeOffsetOf(new Offset2D(sourceModel.getReference(), sourceCoords.getOffsetFrom(reference)));
        else if(sourceCoords instanceof CelestialCoordinates) {
            EquatorialCoordinates sourceEq = ((CelestialCoordinates) sourceCoords).toEquatorial();
            EquatorialCoordinates refEq = ((CelestialCoordinates) sourceModel.getReference()).toEquatorial();
            return getNativeOffsetOf(new Offset2D(refEq, sourceEq.getOffsetFrom(refEq)));
        }
        else if(sourceCoords instanceof FocalPlaneCoordinates) {
            Vector2D offset = sourceCoords.getOffsetFrom(reference);
            offset.rotate(-0.5*(getFirstIntegration().getFirstFrame().getRotation() + getLastIntegration().getLastFrame().getRotation()));
            return new Offset2D(new FocalPlaneCoordinates(), offset);
        }
        
        return null;
    }
    
    public Offset2D getNativeOffsetOf(Offset2D equatorial) {
        if(!equatorial.getCoordinateClass().equals(EquatorialCoordinates.class))
            throw new IllegalArgumentException("not an equatorial offset");
        
        // Equatorial offset (RA/DEC)
        Vector2D offset = new Vector2D(equatorial);
        
        // Rotate to Horizontal...
        Vector2D from = (Vector2D) offset.clone();
        ((HorizontalFrame) getFirstIntegration().getFirstFrame()).equatorialToNative(from);
        Vector2D to = (Vector2D) offset.clone();
        ((HorizontalFrame) getLastIntegration().getLastFrame()).equatorialToNative(to);
        offset.setX(0.5 * (from.x() + to.x()));
        offset.setY(0.5 * (from.y() + to.y()));
        return new Offset2D(getNativeCoordinates(), offset);
    }

    
    // Inverse rotation from Native to Nasmyth...
    public Vector2D getNasmythOffset(Offset2D pointing) {
        SphericalCoordinates coords = getNativeCoordinates();
        if(!pointing.getCoordinateClass().equals(coords.getClass())) 
            throw new IllegalArgumentException("non-native pointing offset."); 
        
        double sinA = instrument.mount == Mount.LEFT_NASMYTH ? -coords.sinLat() : coords.sinLat();
        double cosA = coords.cosLat();
        
        
        // Inverse rotation from Native to Nasmyth...
        Vector2D nasmyth = new Vector2D();
        nasmyth.setX(cosA * pointing.x() + sinA * pointing.y());
        nasmyth.setY(cosA * pointing.y() - sinA * pointing.x());
        
        return nasmyth;
    }
    
    public void applyPointing() {
        Offset2D differential = getNativePointingIncrement(pointing);
        pointingAt(differential);
        
        Vector2D arcsecs = (Vector2D) differential.clone();
        arcsecs.scale(1.0 / Unit.arcsec);
        //debug("pointing at " + arcsecs);

        if(pointing.getCoordinates() instanceof EquatorialCoordinates) 
            pointing.setCoordinates(equatorial.clone());

        else ((CelestialCoordinates) pointing.getCoordinates()).fromEquatorial(equatorial);
        
        if(pointingCorrection == null) pointingCorrection = differential;
        else pointingCorrection.add(differential);
    }
    
    @Override
    public DataTable getPointingData() throws IllegalStateException {
        DataTable data = super.getPointingData();
        
        // Also print Nasmyth offsets if applicable...
        if(instrument.mount == Mount.LEFT_NASMYTH || instrument.mount == Mount.RIGHT_NASMYTH) {
            Offset2D relative = getNativePointingIncrement(pointing);
            Offset2D absolute = getNativePointing(pointing);
            Unit sizeUnit = instrument.getSizeUnit();
            
            Vector2D nasmyth = getNasmythOffset(relative);
            
            data.new Entry("dNasX", nasmyth.x(), sizeUnit);
            data.new Entry("dNasY", nasmyth.y(), sizeUnit);
            
            nasmyth = getNasmythOffset(absolute);
            data.new Entry("NasX", nasmyth.x(), sizeUnit);
            data.new Entry("NasY", nasmyth.y(), sizeUnit);
        }
        
        return data;
    }
     
    
    @Override
    protected String getPointingString(Offset2D nativePointing) {   
        String text = super.getPointingString(nativePointing);
        
        
        // Also print Nasmyth offsets if applicable...
        if(instrument.mount == Mount.LEFT_NASMYTH || instrument.mount == Mount.RIGHT_NASMYTH) {
            Vector2D nasmyth = getNasmythOffset(nativePointing);
            Unit sizeUnit = instrument.getSizeUnit();
            
            text += "\n  Offset: ";     
            text += Util.f1.format(nasmyth.x() / sizeUnit.value()) + ", " + Util.f1.format(nasmyth.y() / sizeUnit.value()) + " " 
                + sizeUnit.name() + " (nasmyth)";
        }
        
        return text;
        
    }
    
    @Override
    public void editScanHeader(Header header) throws HeaderCardException {
        super.editScanHeader(header);
        
        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        c.add(new HeaderCard("RADESYS", (equatorial.epoch instanceof JulianEpoch ? "FK5" : "FK4"), "World coordinate system id"));
        if(!Double.isNaN(equatorial.RA())) c.add(new HeaderCard("RA", Util.hf2.format(equatorial.RA()), "Human Readable Right Ascention"));
        if(!Double.isNaN(equatorial.DEC())) c.add(new HeaderCard("DEC", Util.af1.format(equatorial.DEC()), "Human Readable Declination"));
        c.add(new HeaderCard("EQUINOX", equatorial.epoch.getYear(), "Precession epoch"));   
    

        if(pointing != null) editPointingHeaderInfo(header);
    }
    
    public void editPointingHeaderInfo(Header header) throws HeaderCardException {
        if(pointing == null) return; 

        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        
        c.add(new HeaderCard("COMMENT", "<------ Fitted Pointing / Calibration Info ------>", false));
        
        Offset2D relative = getNativePointingIncrement(pointing);
        Unit sizeUnit = instrument.getSizeUnit();
        
        c.add(new HeaderCard("PNT_DX", relative.x() / sizeUnit.value(), "(" + sizeUnit.name() + ") pointing offset in native X."));
        c.add(new HeaderCard("PNT_DY", relative.y() / sizeUnit.value(), "(" + sizeUnit.name() + ") pointing offset in native Y."));
        
        pointing.editHeader(header, instrument.getSizeUnit());
        
    }
    
    
    @Override
    public String getASCIIHeader() {
        return super.getASCIIHeader() + 
                "# Equatorial: " + equatorial + "\n";
    }
    

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("RA")) return equatorial.RA() / Unit.timeAngle;
        if(name.equals("DEC")) return equatorial.DEC();
        if(name.equals("RAd")) return equatorial.RA() / Unit.deg;
        if(name.equals("RAh")) return ((equatorial.RA() + 2.0 * Math.PI) / Unit.hourAngle) % 24.0;
        if(name.equals("DECd")) return equatorial.DEC() / Unit.deg;
        if(name.equals("epoch")) return equatorial.epoch.toString();
        if(name.equals("epochY")) return equatorial.epoch.getYear();
        
        return super.getTableEntry(name);
    }
    
}
