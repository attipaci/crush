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
package crush.sourcemodel;

import crush.*;
import jnum.Unit;
import jnum.Util;
import jnum.data.*;
import jnum.math.Coordinate2D;

import java.util.*;
import java.util.concurrent.ExecutorService;

public abstract class Photometry extends SourceModel {
    /**
     * 
     */
    private static final long serialVersionUID = -8495366629075732092L;

    public String sourceName;
    public double integrationTime;
    
    
    private Hashtable<String, DataPoint> fluxes;


    public Photometry(Instrument<?> instrument) {
        super(instrument);
        fluxes = new Hashtable<>(instrument.storeChannels);
    }


    @Override
    public Photometry copy(boolean withContents) {
        Photometry copy = (Photometry) super.copy(withContents);
        if(fluxes != null) {
            copy.fluxes = new Hashtable<>(getInstrument().size());
            if(withContents) for(Map.Entry<String, DataPoint> entry : fluxes.entrySet()) copy.fluxes.put(entry.getKey(), entry.getValue().copy());
        }

        return copy;
    }

    
    public Hashtable<String, DataPoint> getFluxes() {
        return fluxes;
    }

    public final boolean contains(Channel channel) {
        return contains(channel.getID());
    }
    
    public boolean contains(String id) {
        return fluxes.contains(id);
    }
    
    public final DataPoint getFlux(Channel channel) {
        return getFlux(channel.getID());
    }
    
    public DataPoint getFlux(String id) {
        return fluxes.get(id);
    }
    
    
    
    public synchronized void averageFlux(String id, DataPoint other, double scaling) {
        DataPoint F = fluxes.get(id);
        if(F != null) F.average(scaling * other.value(), other.weight() / (scaling * scaling));
        else fluxes.put(id, new DataPoint(scaling * other.value(), scaling * other.rms()));
    }
    
    
    @Override
    public void createFrom(Collection<? extends Scan<?>> collection) throws Exception {
        super.createFrom(collection);
        sourceName = getFirstScan().getSourceName();
    }

    @Override
    public Coordinate2D getReference() {
        return getFirstScan().getReferenceCoordinates();
    }

    @Override
    public void addModelData(SourceModel model, double weight) {
        Photometry other = (Photometry) model;
        
        double renorm = getInstrument().janskyPerBeam() / other.getInstrument().janskyPerBeam();
        
        for(Map.Entry<String, DataPoint> entry : other.fluxes.entrySet())
            averageFlux(entry.getKey(), entry.getValue(), renorm);
        
        integrationTime += other.integrationTime;
    }

    @Override
    public void add(Integration<?> integration) {
        if(!integration.isPhaseModulated()) return;

        integration.comments.append("[Phot]");
        Instrument<?> instrument = integration.getInstrument();
        final PhaseSet phases = ((PhaseModulated) integration).getPhases();

        int frames = 0;
        for(PhaseData phase : phases) frames += phase.end.index - phase.start.index;

        integrationTime += frames * instrument.integrationTime;
    }

    @Override
    public void process() throws Exception {
        super.sync();
    }

    @Override
    public void sync(Integration<?> integration) {	
        // Nothing to do here...
    }


    @Override
    public void setBase() {
    }

    @Override
    public void resetProcessing() {
        super.resetProcessing();
        integrationTime = 0.0;
    }
    
    @Override
    public void clearContent() {
        if(fluxes != null) fluxes.clear();
    }

   



    public String getCommentedChi2(double chi2) {
        double chi = Math.sqrt(chi2);

        if(chi <= 1.0) return "<= 1      [excellent!]   ;-)";

        String value = Util.s3.format(Math.sqrt(chi2));

        if(chi < 1.2) return value + "      [good!]   :-)";
        if(chi < 1.5) return value + "      [OK]   :-|";
        if(chi < 2.0) return value + "      [highish...]   :-o";
        if(chi < 3.0) return value + "      [high!]   :-(";
        return value + "      [ouch!!!]   :-/";
    }

  

    @Override
    public String getSourceName() {
        return sourceName;
    }
    
    @Override
    public String getLoggingID() { return "phot"; }
    


    @Override
    public Unit getUnit() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void noParallel() {
        // TODO Auto-generated method stub

    }

    @Override
    public void setParallel(int threads) {
        // TODO Auto-generated method stub
    }

    @Override
    public int countPoints() {
        return fluxes.size();
    }


    @Override
    public void setExecutor(ExecutorService executor) {
        // TODO Auto-generated method stub

    }

    @Override
    public ExecutorService getExecutor() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getParallel() {
        // TODO Auto-generated method stub
        return 0;
    }

}
