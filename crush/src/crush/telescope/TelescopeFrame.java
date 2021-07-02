/* *****************************************************************************
 * Copyright (c) 2021 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope;

import crush.Frame;
import crush.Integration;
import crush.Signal;
import crush.motion.Motion;
import jnum.astro.AstroProjector;
import jnum.astro.EquatorialCoordinates;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.projection.Projector2D;

public abstract class TelescopeFrame extends Frame {
    /**
     * 
     */
    private static final long serialVersionUID = -3917292618625828297L;
   
    
    public boolean hasTelescopeInfo = true;
    
    public EquatorialCoordinates equatorial;
    public Vector2D chopperPosition = new Vector2D(); // in the native coordinate system, standard direction (e.g. -RAO, DECO)
    
    public double LST;

    private float transmission = 1.0F;

       
    protected TelescopeFrame(Integration<? extends TelescopeFrame> parent) {
        super(parent);
    }
    
    @Override
    public TelescopeFrame copy(boolean withContents) {
        TelescopeFrame copy = (TelescopeFrame) super.copy(withContents);
        
        if(equatorial != null) copy.equatorial = equatorial.copy();
        if(chopperPosition != null) copy.chopperPosition = chopperPosition.copy();

        return copy;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public TelescopeScan<? extends Integration<? extends TelescopeFrame>> getScan() { 
        return (TelescopeScan<? extends Integration<? extends TelescopeFrame>>) super.getScan(); 
    }
    
    @Override
    public TelescopeInstrument<?> getInstrument() { return (TelescopeInstrument<?>) getIntegration().getInstrument(); }
 
    
    public float getTransmission() { return transmission; }
    
    protected void setTransmission(float value) { transmission = value; }
    
    protected void setTransmission(double value) { setTransmission((float) value); }
    
    public float getTransmissionCorrection(Signal atm, float signal2emissivity) {
        return atm.valueAt(this) * signal2emissivity;
    }
    
    @Override
    public float getSourceGain(final int mode) throws IllegalArgumentException {
        if(mode == TOTAL_POWER) return sign * getTransmission();
        throw new IllegalArgumentException(getClass().getSimpleName() + " does not define signal mode " + mode);
    }
    
    
    @Override
    public boolean validate() {
        if(!super.validate()) return false;
        
        if(!hasTelescopeInfo) return true;
        
        // Set the platform rotation, unless the rotation was explicitly set already
        if(Double.isNaN(getRotation())) {
            switch(getInstrument().mount) {
            case CASSEGRAIN: 
            case GREGORIAN: 
            case NASMYTH_COROTATING:
            case PRIME_FOCUS: setRotation(0.0); break;
            case LEFT_NASMYTH: setRotation(-getNativeCoords().y()); break;
            case RIGHT_NASMYTH: setRotation(getNativeCoords().y()); break;
            default: setRotation(0.0);
            }       
        }
        
        return true;
    }
    
    
    public void getEquatorial(final Vector2D position, final EquatorialCoordinates coords) {
        coords.setNativeLongitude(equatorial.x() + getNativeX(position) / getScan().equatorial.cosLat());
        coords.setNativeLatitude(equatorial.y() + getNativeY(position));
    }
    
    public void getEquatorialOffset(final Vector2D position, final Vector2D offset) {
        getEquatorialOffset(offset);
        offset.setX(offset.x() + getNativeX(position));
        offset.setY(offset.y() + getNativeY(position));
    }
    
    @Override
    public SphericalCoordinates getNativeCoords() { return equatorial; }
        
    public void getNativeOffset(final Vector2D position, final Vector2D offset) {
        getEquatorialOffset(position, offset);
    }
    
    @Override
    public void getNativeOffset(final Vector2D offset) {
        getEquatorialOffset(offset);
    }
    
    
    public void getEquatorialOffset(Vector2D offset) {
        equatorial.getOffsetFrom(getScan().equatorial, offset);        
    }
    
    public Vector2D getEquatorialOffset() {
        Vector2D offset = new Vector2D();
        getEquatorialOffset(offset);
        return offset;
    }
    
    public void getApparentEquatorial(EquatorialCoordinates apparent) {
        apparent.copy(equatorial);
        getScan().toApparent.transform(apparent);
    }
    

    
    @Override
    public void pointingAt(Vector2D offset) {
        Vector2D nativeOffset = getNativeOffset();
        if(nativeOffset != null) nativeOffset.subtract(offset);
        SphericalCoordinates coords = getNativeCoords();
        if(coords != null) coords.subtractOffset(offset);
    }

    
    @Override
    public void project(final Vector2D fpOffset, final Projector2D<?> projector) {
        if(projector instanceof AstroProjector) project(fpOffset, (AstroProjector) projector);
    }
    
    private void project(final Vector2D fpOffset, final AstroProjector projector) {
        if(projector.isFocalPlane()) {
            projector.setReferenceCoords();
            // Deproject SFL focal plane offsets...
            final Vector2D offset = projector.getOffset();
            getFocalPlaneOffset(fpOffset, offset);
            projector.getCoordinates().addNativeOffset(offset);
            projector.reproject();
        }
        else if(getScan().isNonSidereal) {
            projector.setReferenceCoords();
            // Deproject SFL native offsets...
            final EquatorialCoordinates eq = projector.getEquatorial();
            Vector2D offset = projector.getOffset();
            getEquatorialOffset(fpOffset, offset);
            eq.addNativeOffset(offset);
            projector.setEquatorial(eq);
        }
        else {
            final EquatorialCoordinates eq = projector.getEquatorial();
            getEquatorial(fpOffset, eq);     
            projector.setEquatorial(eq);
        }
    
    }   
    

    public final void nativeToEquatorial(Vector2D offset) {}

    public final void equatorialToNative(Vector2D offset) {}
    
    public void nativeToEquatorial(SphericalCoordinates coords, EquatorialCoordinates equatorial) {
        equatorial.copy(coords);
    }
    
    public void equatorialToNative(EquatorialCoordinates equatorial, SphericalCoordinates coords) {
        coords.copy(equatorial);
    }
    
    
    @Override
    public Vector2D getPosition(final int type) {
        Vector2D pos = new Vector2D();
        
        // Telescope motion should be w/o chopper...
        // TELESCOPE motion with or w/o SCANNING and CHOPPER
        if((type & Motion.TELESCOPE) != 0) {
            SphericalCoordinates coords = new SphericalCoordinates();
            
            coords.copy(getNativeCoords());
            // Subtract the chopper motion if it is not requested...
            if((type & Motion.CHOPPER) == 0) coords.subtractNativeOffset(chopperPosition);
            pos.copy(coords);

            if((type & Motion.PROJECT_GLS) != 0) pos.scaleX(coords.cosLat());
        }

        // Scanning includes the chopper motion
        // SCANNING with or without CHOPPER
        else if((type & Motion.SCANNING) != 0) {
            getNativeOffset(pos);
            // Subtract the chopper motion if it is not requested...
            if((type & Motion.CHOPPER) == 0) pos.subtract(chopperPosition);
        }   

        // CHOPPER only...
        else if(type == Motion.CHOPPER) pos.copy(chopperPosition);
        
        return pos;
    }
    
    
    public static int CHOP_LEFT = frameFlags.next('L', "Chop Left").value();
    public static int CHOP_RIGHT = frameFlags.next('R', "Chop Right").value();
    public static int CHOP_TRANSIT = frameFlags.next('T', "Chop Transit").value();
    public static int CHOP_FLAGS = CHOP_LEFT | CHOP_RIGHT | CHOP_TRANSIT;
    
    public static int NOD_LEFT = frameFlags.next('<', "Nod Left").value();
    public static int NOD_RIGHT = frameFlags.next('>', "Nod Right").value();

}
