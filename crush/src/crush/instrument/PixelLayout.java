/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import crush.Channel;
import crush.Instrument;
import crush.Pixel;
import crush.array.GridIndexed;
import jnum.Configurator;
import jnum.Constant;
import jnum.ExtraMath;
import jnum.math.Vector2D;
import jnum.text.TableFormatter;

public abstract class PixelLayout<ChannelType extends Channel> implements Serializable, Cloneable, TableFormatter.Entries {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6144882903894123342L;
	
	private Instrument<? extends ChannelType> instrument;
		
	public void setInstrument(Instrument<? extends ChannelType> instrument) {
		this.instrument = instrument;
	}
	
	public Instrument <? extends ChannelType> getInstrument() { return instrument; }
	
	@SuppressWarnings("unchecked")
    @Override
	public PixelLayout<ChannelType> clone() {
		try { return (PixelLayout<ChannelType>) super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public PixelLayout<ChannelType> copyFor(Instrument<? extends ChannelType> i)  {
		PixelLayout<ChannelType> copy = clone();
		copy.instrument = i;
		return copy;
	}
	
	public void initialize() {}
	
	public boolean hasOption(String key) {
		return instrument.hasOption(key);
	}
	
	public Configurator option(String key) {
		return instrument.option(key);
	}
	
	public void setDefaults() {   

	}
	
	public void validate(Configurator options) {
		if(hasOption("beam")) instrument.setResolution(option("beam").getDouble() * instrument.getSizeUnit().value());
	}
	
	public abstract int getPixelCount();
	
	public abstract List<? extends Pixel> getPixels();

	
    
    public Hashtable<String, Pixel> getPixelLookup() {
        Hashtable<String, Pixel> table = new Hashtable<String, Pixel>();
        for(Pixel pixel : getPixels()) table.put(pixel.getID(), pixel);
        return table;
    }
        
	
	
	public abstract List<? extends Pixel> getMappingPixels(int keepFlags);
	

    public final List<? extends Pixel> getPerimeterPixels() { 
        int sections = 0;
        

        if(hasOption("perimeter")) {
            if(option("perimeter").getValue().equalsIgnoreCase("auto")) {
                // n ~ pi r^2   --> r ~ sqrt(n / pi)
                // np ~ 2 pi r ~ 2 sqrt(pi n) ~ 3.55 sqrt(n)
                // Add factor of ~2 margin --> np ~ 7 sqrt(n)
                sections = (int) Math.ceil(7 * Math.sqrt(getPixelCount()));
            }
            else sections = option("perimeter").getInt();
        }
        return getPerimeterPixels(sections); 
    }
    

    public final List<? extends Pixel> getPerimeterPixels(int sections) { 
        final List<? extends Pixel> mappingPixels = getMappingPixels(~instrument.sourcelessChannelFlags());
        
        if(sections <= 0) return mappingPixels;

        if(mappingPixels.size() < sections) return mappingPixels;

        final Pixel[] sPixel = new Pixel[sections];
        final double[] maxd = new double[sections];
        Arrays.fill(maxd, Double.NEGATIVE_INFINITY);

        final Vector2D centroid = new Vector2D();
        for(Pixel p : mappingPixels) centroid.add(p.getPosition());
        centroid.scale(1.0 / mappingPixels.size());

        final double dA = Constant.twoPi / sections;
        final Vector2D relative = new Vector2D();

        for(Pixel p : mappingPixels) {
            relative.setDifference(p.getPosition(), centroid);

            final int bin = (int) Math.floor((relative.angle() + Math.PI) / dA);

            final double d = relative.length();
            if(d > maxd[bin]) {
                maxd[bin] = d;
                sPixel[bin] = p;
            }
        }

        final ArrayList<Pixel> perimeter = new ArrayList<Pixel>(sections);
        for(int i=sections; --i >= 0; ) if(sPixel[i] != null) perimeter.add(sPixel[i]);

        return perimeter;
    }
		

    


    // How about different pointing and rotation centers?...
    // If assuming that pointed at rotation a0 and observing at a
    // then the pointing center will rotate by (a-a0) on the array rel. to the rotation
    // center... (dP is the pointing rel. to rotation vector)
    // i.e. the effective array offsets change by:
    //  dP - dP.rotate(a-a0)
    
    // For Cassegrain assume pointing at zero rotation (a0 = 0.0)
    // For Nasmyth assume pointing at same elevation (a = a0)
    
    public void rotate(double angle) {
               
        // Undo the prior rotation...
        Vector2D priorOffset = instrument.getPointingOffset(instrument.getRotationAngle());
        Vector2D newOffset = instrument.getPointingOffset(instrument.getRotationAngle() + angle);

        
        for(Pixel pixel : getPixels()) if(pixel.getPosition() != null) {
            Vector2D position = pixel.getPosition();
            
            // Center positions on the rotation center...
            position.subtract(priorOffset);
            // Do the rotation...
            position.rotate(angle);
            // Re-center on the pointing center...
            position.add(newOffset);
        }
    }
    
    

    public void setReferencePosition(Vector2D position) {
        Vector2D referencePosition = position.copy();
        for(Pixel pixel : getPixels()) {
            Vector2D v = pixel.getPosition();
            if(v != null) v.subtract(referencePosition);
        }
    }

    @Override
    public Object getTableEntry(String name) {
        return TableFormatter.NO_SUCH_DATA;        
    }
   
    
    public static void addLocalFixedIndices(GridIndexed geometric, int fixedIndex, double radius, Collection<Integer> toIndex) {
        
        final int row = fixedIndex / geometric.cols();
        final int col = fixedIndex % geometric.cols();
        
        final Vector2D pixelSize = geometric.getSIPixelSize();
        final int dc = (int)Math.ceil(radius / pixelSize.x());
        final int dr = (int)Math.ceil(radius / pixelSize.y());
        
        final int fromi = Math.max(0, row - dr);
        final int toi = Math.min(geometric.rows()-1, row + dr);
        
        final int fromj = Math.max(0, col - dc);
        final int toj = Math.min(geometric.cols()-1, col + dc);
    
        for(int i=fromi; i<=toi; i++) for(int j=fromj; j<=toj; j++) if(!(i == row && j == col)) {
            final double r = ExtraMath.hypot((i - row) * pixelSize.y(), (j - col) * pixelSize.x());
            if(r <= radius) toIndex.add(i * geometric.cols() + j);
        }
        
    }

    
}
