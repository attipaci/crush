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

package crush;



import java.util.Collection;
import java.util.HashSet;

import crush.instrument.Overlap;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.math.Vector2D;

public class Pixel extends ChannelGroup<Channel> {

    /**
     * 
     */
    private static final long serialVersionUID = 8584438350327020673L;
    
    
    private Instrument<? extends Channel> instrument;
    private String id;
    private int index;
    private int fixedIndex;
    public double coupling = 1.0;

    private Vector2D position;    
    private boolean isIndependent = false;
    
    private transient Collection<Overlap<Pixel>> overlaps;  // Don't copy...
    

    /**
     * Constructs a new pixel for the specified instrument.
     * 
     * @param instrument    The instrument state to which this pixel belongs.
     * @param fixedIndex    An index that uniquely identifies the pixel between 0 and {@link Instrument#maxPixels()}.
     */
    public Pixel(Instrument<? extends Channel> instrument, int fixedIndex) {
        this(instrument, Integer.toString(fixedIndex+1), fixedIndex);
    }
     
    /**
     * Constructs a new pixel for the specified instrument.
     * 
     * @param instrument    The instrument state to which this pixel belongs.
     * @param id            The textual ID that uniquely identifies this pixel. (When the constructor without it
     *                      is called, the ID will be set to the string equivalent of <code>fixedIndex+1</code>
     * @param fixedIndex    An index that uniquely identifies the pixel between 0 and {@link Instrument#maxPixels()}.
     */
    public Pixel(Instrument<? extends Channel> instrument, String id, int fixedIndex) {
        super("pixel-" + id);
        this.instrument = instrument;
        this.id = id;
        this.fixedIndex = fixedIndex;
        this.index = fixedIndex;
        position = new Vector2D();
    }
    
    @Override
    public Pixel copy() {
        Pixel copy = (Pixel) super.copy();
        copy.position = position.copy();
        copy.overlaps = null;
        return copy;
    }
    
    public Pixel emptyCopy() {
        Pixel copy = (Pixel) super.copy();
        copy.clear();
        return copy;
    }
    
    @Override
    public boolean add(Channel channel) {
        if(!super.add(channel)) return false;
        channel.setPixel(this);
        return true;
    }
    
    @Override
    public void add(int index, Channel channel) {
        super.add(index, channel);
        channel.setPixel(this);
    }
    
    @Override
    public Channel set(int index, Channel channel) {
        Channel replaced = super.set(index, channel);
        channel.setPixel(this);
        return replaced;
    }
    
    
    public final String getID() { return id; }
    
    public final int getIndex() {
        return index;
    }
    
    public void setIndex(int i) {
        this.index = i;
    }

    public final int getFixedIndex() {
        return fixedIndex;
    }

    public boolean isValid() {
        if(position == null) return false;
        if(position.isNaN()) return false;
        if(position.isInfinite()) return false;
        if(index < 0) return false;
        return true;
    }

    public final Vector2D getPosition() {
        return position;
    }
    
    public void setPosition(Vector2D v) {
        position = v;
    }

    public double distanceTo(Pixel pixel) {
        if(pixel == null) return Double.NaN;
        if(position == null) return Double.NaN;
        return position.distanceTo(pixel.getPosition());
    }

    public double getResolution() {
        return instrument.getResolution();
    }

    public final int channels() {
        return size();
    }

    public final Channel getChannel(int i) {
        return get(i);
    }

    public void setIndependent(boolean value) {
        isIndependent = value;
    }

    public final boolean isIndependent() { return isIndependent; }
    
     
    public String getRCPString() {
        Vector2D position = getPosition();
        return getFixedIndex() + 
                "\t" + Util.f3.format(coupling) + 
                "\t" + Util.f3.format(1.0) + 
                "\t" + Util.f1.format(position.x() / Unit.arcsec) + 
                "  " + Util.f1.format(position.y() / Unit.arcsec);
    }

    
    /**
     * Checks if all channels of this pixel are flagged with any of the specified bit-wise flags, or if the
     * pattern contains checking for blinds and the pixel has zero coupling
     * 
     * 
     * @param pattern   The bit-wise flag pattern to check.
     * 
     * @return      <code>true</code> if all channels of this pixel are flagged with the specified pattern, 
     *              otherwise <code>false</code>.
     *              
     * @see #isUnflagged(int)
     */
    public boolean isFlagged(int pattern) {   
        if((pattern & Channel.FLAG_BLIND) != 0) if(coupling == 0.0) return true;
        for(Channel channel : this) if(channel.isUnflagged(pattern)) return false;
        return true;
    }

    /**
     * Checks if none of the channels of this pixel are flagged with any of the specified bit-wise flags.
     * 
     * 
     * @param pattern   The bit-wise flag pattern to check.
     * 
     * @return      <code>true</code> if none of the channels of this pixel are flagged with the specified pattern, 
     *              otherwise <code>false</code>.
     *              
     * @see #isFlagged(int)
     */
    public boolean isUnflagged(int pattern) {   
        return !isFlagged(pattern);
    }
    
  
    // Assume Gaussian response with FWHM = resolution;
    public double overlap(final Pixel pixel, double pointSize) {
        if(isIndependent) return 0.0;

        if(pixel.instrument != instrument) return 0.0;

        if(pixel.isFlagged(Channel.FLAG_BLIND | Channel.FLAG_DEAD)) return 0.0;
        if(isFlagged(Channel.FLAG_BLIND | Channel.FLAG_DEAD)) return 0.0;

        final double isigma = Constant.sigmasInFWHM / pointSize;
        
        final double dev = distanceTo(pixel) * isigma;
        if(!Double.isNaN(dev)) return Math.exp(-0.5 * dev * dev);
     
        // If other channel is not a pixel assume it is independent...
        return 0.0;
    }
    
    
    
    public Collection<Overlap<Pixel>> getOverlaps() { return overlaps; }
    
    public void setOverlaps(Collection<Overlap<Pixel>> overlaps) { this.overlaps = overlaps; }
    
    public void clearOverlaps() {
        if(overlaps != null) overlaps.clear();
    }
    
    public synchronized void addOverlap(Overlap<Pixel> overlap) {
        if(overlap.a != this && overlap.b != this) return;
        if(overlaps == null) overlaps = new HashSet<Overlap<Pixel>>();
        overlaps.add(overlap);      
    }
    
    public void validate() {
        trimToSize();
    }

	
}
