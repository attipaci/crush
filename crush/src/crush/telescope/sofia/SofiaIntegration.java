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

package crush.telescope.sofia;


import crush.telescope.GroundBasedIntegration;
import jnum.LockedException;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;

public abstract class SofiaIntegration<FrameType extends SofiaFrame> extends GroundBasedIntegration<FrameType> {

    /**
     * 
     */
    private static final long serialVersionUID = -4771883165716694480L;


    protected SofiaIntegration(SofiaScan<? extends SofiaIntegration<? extends FrameType>> parent) {
        super(parent);
    }

    @SuppressWarnings("unchecked")
    @Override
    public SofiaScan<? extends SofiaIntegration<? extends FrameType>> getScan() { 
        return (SofiaScan<? extends SofiaIntegration<? extends FrameType>>) super.getScan(); 
    }

    @Override
    public SofiaInstrument<?> getInstrument() { return (SofiaInstrument<?>) super.getInstrument(); }

    @Override
    public double getModulationFrequency(int signalMode) {
        if(getScan().mode.isChopping) return getScan().chopper.frequency;
        return super.getModulationFrequency(signalMode);
    }


    protected double getMeanPWV() {
        return validParallelStream().mapToDouble(f -> f.PWV).filter(p -> !Double.isNaN(p)).average().orElse(Double.NaN);
    }

    //public double getMeanPWV() { return ((SofiaScan<?>) scan).environment.pwv.midPoint(); }

    protected double getModelPWV() {
        info("Estimating PWV based on altitude...");
        double pwv41k = hasOption("pwv41k") ? option("pwv41k").getDouble() * Unit.um : 29.0 * Unit.um;
        double b = 1.0 / (hasOption("pwvscale") ? option("pwvscale").getDouble() : 5.0);
        double altkf = getScan().aircraft.altitude.midPoint() / (1000.0 * Unit.ft);
        return pwv41k * Math.exp(-b * (altkf - 41.0));
    }

    @Override
    public void validate() {  
        validatePWV();    
        super.validate();
    }

    private void validatePWV() {
        double pwv = getMeanPWV();
        if(pwv == 0.0 || Double.isNaN(pwv)) {
            info("--> FIX: Using default PWV model...");
            pwv = getModelPWV(); 
        }

        info("PWV: " + Util.f1.format(pwv / Unit.um) + " um");

        if(!hasOption("tau.pwv")) {
            try { getOptions().process("tau.pwv", Double.toString(pwv / Unit.um)); }
            catch(LockedException e) {}
        }  
    }


    @Override
    public void setTau() throws Exception {
        if(!hasOption("tau")) return;

        String source = option("tau").getValue().toLowerCase();

        if(source.equals("atran")) setVaccaTau();
        else if(source.equals("pwvmodel")) setPWVModelTau();
        else super.setTau();
    }


    private void setVaccaTau() throws Exception {
        AtranModel model = new AtranModel(getOptions());
        double altitude = getScan().aircraft.altitude.midPoint();
        double elevation = 0.5 * (getFirstFrame().horizontal.EL() + getLastFrame().horizontal.EL());

        double C = model.getRelativeTransmission(altitude, elevation);

        info("Applying Bill Vacca's atmospheric correction: " + Util.f3.format(C));

        setTau(-Math.log(model.getReferenceTransmission() * C) * Math.sin(elevation));
    }

    private void setPWVModelTau() throws Exception {
        double pwv = getModelPWV() / Unit.um;

        info("Using PWV model to correct fluxes: PWV = " + Util.f1.format(pwv));  

        try { getOptions().process("tau.pwv", Double.toString(pwv)); }
        catch(LockedException e) {}

        this.setTau("pwv", pwv);
    }


    public Vector2D getMeanChopperPosition() {
        Vector2D mean = new Vector2D();
        int n = 0;

        for(SofiaFrame frame : this) if(frame != null) {
            mean.add(frame.chopperPosition);
            n++;
        }

        if(n > 0) mean.scale(1.0 / n);

        return mean;        
    }

}

