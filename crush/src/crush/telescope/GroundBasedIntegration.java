/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/


package crush.telescope;



import crush.CRUSH;
import crush.Integration;
import crush.motion.Chopper;
import crush.motion.Chopping;
import jnum.Util;
import jnum.math.Vector2D;


public abstract class GroundBasedIntegration<FrameType extends HorizontalFrame> extends Integration<FrameType> {


    /**
     * 
     */
    private static final long serialVersionUID = 5302265555895118823L;
    public double zenithTau = 0.0;
    
    
    protected GroundBasedIntegration(GroundBasedScan<? extends GroundBasedIntegration<? extends FrameType>> parent) {
        super(parent);
    }
  
    
    @SuppressWarnings("unchecked")
    @Override
    public GroundBasedScan<? extends GroundBasedIntegration<? extends FrameType>> getScan() { 
        return (GroundBasedScan<? extends GroundBasedIntegration<? extends FrameType>>) super.getScan(); 
    }
   
    @Override
    public TelescopeInstrument<?> getInstrument() { return (TelescopeInstrument<?>) super.getInstrument(); }
    
    
    @Override
    public void validate() {
        super.validate();
        
        if(hasOption("tau")) {
            try { setTau(); }
            catch(Exception e) { 
                warning("Problem setting tau: " + e.getMessage()); 
                if(CRUSH.debug) CRUSH.trace(e);
            }
        }     
    }

     
 
    // Try in this order:
    //   1. in-band value, e.g. "0.304"
    //   2. scaling relation, e.g. "225GHz", provided "tau.225GHz" is defined.
    //   
    public void setTau() throws Exception { 
        String spec = option("tau").getValue();

        try { setTau(Double.parseDouble(spec)); }
        catch(Exception notanumber) {
            String id = spec.toLowerCase();
            if(hasOption("tau." + id)) setTau(id, option("tau." + id).getDouble());
            else throw new IllegalArgumentException("Supplied tau is neither a number nor a known subtype.");
        }
    }

    public void setTau(String id, double value) {
        Vector2D t = getTauCoefficients(id);
        Vector2D inband = getTauCoefficients(getInstrument().getName());
        try { setZenithTau(inband.x() / t.x() * (value - t.y()) + inband.y()); }
        catch(Exception e) { 
            warning("Could not set zenith tau: " + e.getMessage()); 
            if(CRUSH.debug) CRUSH.trace(e);
        }
    }

    public double getTau(String id, double value) {
        Vector2D t = getTauCoefficients(id);
        Vector2D inband = getTauCoefficients(getInstrument().getName());
        return t.x() / inband.x() * (value - inband.y()) + t.y();
    }

    public double getTau(String id) {
        return getTau(id, zenithTau);
    }

    public Vector2D getTauCoefficients(String id) {
        String key = "tau." + id.toLowerCase();

        if(!hasOption(key + ".a")) throw new IllegalStateException(key + " has no scaling relation.");

        Vector2D coeff = new Vector2D();
        coeff.setX(option(key + ".a").getDouble());
        if(hasOption(key + ".b")) coeff.setY(option(key + ".b").getDouble());

        return coeff;
    }

    public void setTau(final double value) throws Exception {   
        try { setZenithTau(value); }
        catch(NumberFormatException e) {}
    }

    public void setZenithTau(final double value) {
        info("Setting zenith tau to " + Util.f3.format(value));
        zenithTau = value;
        validParallelStream().forEach(f ->f.setZenithTau(value));
    }
    
    
    @Override
    public Object getTableEntry(String name) {
        if(name.equals("zenithtau")) return zenithTau;
        if(name.equals("tau")) return zenithTau / Math.cos(getScan().horizontal.EL());
        if(name.startsWith("tau.")) return getTau(name.substring(4).toLowerCase());
        if(name.startsWith("chop") && this instanceof Chopping) {
            Chopper chopper = ((Chopping) this).getChopper();
            if(chopper == null) return null;
            return chopper.getTableEntry(name);
        }
        
        return super.getTableEntry(name);
    }

    
}
