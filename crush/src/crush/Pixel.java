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
import jnum.math.Metric;
import jnum.math.Vector2D;

/**
 * A class for representing a pixel or feed in an instrument, such as a directly illuminated continuum detector, 
 * or a antenna-coupled set of detector channels (e.g. in a heterodyne mixer, or multichroic pixel).
 * <p>
 * 
 * Each pixel has a 2D focal plane position, and may contain one or more detector {@link Channel}s that couple through
 * the same feed / pixel / antenna, or physical detector plane position. Moreover, similarly to <code>Channel</code>s, 
 * pixels also have fixed indices (between 0 and below {@link Instrument#maxPixels()}), and (optionally) unique 
 * {@link String} IDs, that enable lookup functionality.
 * <p>
 * 
 * The position of, and the association of channels to, a pixel/feed, is typically handled by a {@link PixelLayout}
 * implementation.
 * <p>
 * 
 * 
 * @author Attila Kovacs <attila@sigmyne.com>
 *
 */
public class Pixel extends ChannelGroup<Channel> implements Metric<Pixel> {

    /**
     * 
     */
    private static final long serialVersionUID = 8584438350327020673L;
    
    
    private Instrument<? extends Channel> instrument;
    private String id;
    private int index;
    private int fixedIndex;

    private Vector2D position;    
    private boolean isIndependent = false;
    
    public double coupling = 1.0;
    
    private transient Collection<Overlap<Pixel>> overlaps;  // Don't copy...
    

    /**
     * Constructs a new pixel for the specified instrument. The pixel will have a default ID equal to 1+<code>fixedIndex</code>.
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
    
    /**
     * Get a copy of this Pixel, with the same properties, but without populating the copy with channels.
     * 
     * 
     * @return  A copy of this pixel without channels associated to it.
     */
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
    
    /**
     * Returns the {@link String} ID of this pixel. 
     * 
     * @return  The ID of this pixel. (The default ID of a pixel is equal to 1+{@link #getFixedIndex()}, if not explicitly set
     *          with the constructor.
     */
    public final String getID() { return id; }
    
    /**
     * Returns the current index of this pixel in the parent {@link Instrument}'s {@link PixelLayout}.
     * 
     * @return  The current indes of this pixel in the parent instrument's layout.
     */
    public final int getIndex() {
        return index;
    }
    
    /**
     * Sets the index of this pixel in the parent instruments {@link PixelLayout}. This method should only be called
     * by {@link PixelLayout} to ensure that it remains current.  
     * 
     * @param i     The new index in the parent instrument's layout.
     */
    void setIndex(int i) {
        this.index = i;
    }

    /**
     * Returns the 0-based fixed index of this pixel.
     * 
     * @return  the 0-based fixed index of this pixel. 
     */
    public final int getFixedIndex() {
        return fixedIndex;
    }

    /**
     * Checks if this pixel is valid, i.e. has a proper finite position, and a non-zero index in the parent layout.
     * 
     * @return
     */
    public boolean isValid() {
        if(position == null) return false;
        if(position.isNaN()) return false;
        if(position.isInfinite()) return false;
        if(index < 0) return false;
        return true;
    }

    /**
     * Returns the 2D position of this pixel, as projected to the imaging plane.
     * 
     * @return
     */
    public final Vector2D getPosition() {
        return position;
    }
    
    /**
     * Sets the position of this pixel, as projected to the imaging plane.
     * 
     * @param v
     */
    public void setPosition(Vector2D v) {
        position = v;
    }

    /**
     * Returns the projected distance of this pixel to another in the imaging plane.
     * 
     * @param pixel     The other pixel.
     * @return          The projected distance to that other pixel in the imaging plane.
     */
    @Override
    public double distanceTo(Pixel pixel) {
        if(pixel == null) return Double.NaN;
        if(position == null) return Double.NaN;
        return position.distanceTo(pixel.getPosition());
    }

    /**
     * Returns the spacial resolution of this pixel in the projected imaging plane.
     * 
     * @return
     */
    public double getResolution() {
        return instrument.getResolution();
    }

    /**
     * Returns the number of readout channels contained within this pixel / feed.
     * 
     * @return  the number of channels in this pixel/feed.
     */
    public final int channels() {
        return size();
    }

    /**
     * Returns the t<sup>th</sup> channel in this pixel.
     * 
     * @param i     The index of the channel to retrieve.
     * @return      The channel at that index within this pixel.
     * 
     * @see #channels()
     */
    public final Channel getChannel(int i) {
        return get(i);
    }

    /**
     * Specify if this pixel has no overlap with any other pixel, so that it is safe to skip overlap calculation for it.
     * 
     * @param value     <code>true</code> if this pixels does not overlap with other pixels, otherwise <code>false</code>
     * 
     * @see #isIndependent()
     */
    public void setIndependent(boolean value) {
        isIndependent = value;
    }

    /**
     * Checks if this pixel is independent, i.e. if it has no possible overlaps with other pixels.
     * 
     * @return  <code>true</code> if this pixel never overlaps with other pixels, otherwise <code>false</code>
     * 
     * @see #setIndependent(boolean)
     */
    public final boolean isIndependent() { return isIndependent; }
    
    
    /**
     * Returns the APEX-style RPC entry for this pixel. 
     * 
     * @return  This pixel's line entry for an APEX-style RPC file.
     * 
     * @see PixelLayout#getRCPHeader()
     * @see PixelLayout#readRCP(String)
     * @see PixelLayout#printPixelRCP(java.io.PrintStream, String)
     */
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
    
  
    /**
     * Calculates the symmetric overlap of this pixel with another pixel, assuming a Gaussian response with FWHM = resolution.
     * The overlap is the relative response in one pixel when a point source (of <code>pointSize</code> in the projected image
     * plane) is placed over the other pixel. 
     * <p>
     * 
     * For now, CRUSH assumes symmetry, i.e. that (A overlap B) = (B overlap A), which is a good approximation for pixels
     * with similar and approximately Gaussian (or azymuthally symmetric) beam profiles.
     * <p>
     * 
     * 
     * @param pixel         The othe pixel.
     * @param pointSize     The projected size of a point source in the image plane.
     * @return              The fractional overlap, i.e. the relative response of the pixel when a point source is 
     *                      placed over the other pixel.
     *                      
     */
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
        if(overlaps == null) overlaps = new HashSet<>();
        overlaps.add(overlap);      
    }
    
    public void validate() {
        trimToSize();
    }

	
}
