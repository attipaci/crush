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


import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import crush.instrument.GridIndexed;
import crush.instrument.Rotating;
import jnum.Configurator;
import jnum.Constant;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.io.LineParser;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;
import jnum.text.TableFormatter;

/**
 * The abstract class that manages {@link Pixel}s in an {@link Instrument}. As such, the layout is integrally linked to
 * a specific instrument state. 
 * <p>
 * 
 * In general, it is advisable that 
 * implementations of this class perform all the pixel position calculations (so they are easily followed in one place), 
 * setting and updating pixel properties, and assigning the parent instrument's channels to pixels.
 * 
 * 
 * @author Attila Kovacs <attila@sigmyne.com>
 *
 */
public abstract class PixelLayout implements Cloneable, Serializable, TableFormatter.Entries {
    /**
     * 
     */
    private static final long serialVersionUID = 6144882903894123342L;

    private Instrument<? extends Channel> instrument;
    private double rotation = 0.0;
    
    private ArrayList<Pixel> pixels = new ArrayList<>();

    public PixelLayout(Instrument<? extends Channel> instrument) {
        this.instrument = instrument;
    }
    
    @Override
    public PixelLayout clone() {
        try { return (PixelLayout) super.clone(); }
        catch(CloneNotSupportedException e) { return null; }
    }
    
    public PixelLayout copyFor(Instrument<? extends Channel> instrument) {
        PixelLayout copy = clone();
             
        copy.instrument = instrument;
        copy.pixels = new ArrayList<>(pixels.size());
        
        final ChannelLookup<Channel> lookup = new ChannelLookup<>(instrument);
     
        for(int i=0; i<pixels.size(); i++) {
            Pixel p1 = pixels.get(i);
            Pixel p2 = p1.emptyCopy();
            
            for(Channel c1 : p1) {
                Channel c2 = lookup.get(c1.getFixedIndex());
                if(c2 != null) p2.add(c2);
            }
            
            p2.trimToSize();       
            copy.pixels.add(p2);
        }
        
        copy.reindex();
        
        return copy;
    }
    
    
    public void validate() {   
        reindex();
        
        setDefaultPixelPositions();
        
        pixels.parallelStream().forEach(p -> p.validate());
        
        if(hasOption("rcp")) {
            try { readRCP(option("rcp").getPath()); }
            catch(IOException e) { instrument.warning("Cannot update pixel RCP data. Using values from FITS."); }
        }

        // Apply instrument rotation...
        if(hasOption("rotation")) rotate(option("rotation").getDouble() * Unit.deg);
            
        // Instruments with a rotator should apply explicit rotation after pixel positions are finalized...
        if(instrument instanceof Rotating) rotate(((Rotating) instrument).getRotation());

        if(hasOption("scramble")) scramble();
        
        if(hasOption("uniform")) pixels.parallelStream().forEach(p -> p.coupling = 1.0);
    }
    
    
    
    public abstract void setDefaultPixelPositions();

    public Instrument <? extends Channel> getInstrument() { return instrument; }


    public Pixel getPixelInstance(int fixedIndex, String id) {
        return new Pixel(instrument, id, fixedIndex);
    }
    
    public void reset() {
        rotation = 0.0;
    }

    public boolean hasOption(String key) {
        return instrument.hasOption(key);
    }

    public Configurator option(String key) {
        return instrument.option(key);
    }

    public final int getPixelCount() {
        return pixels.size();
    }

    public final ArrayList<Pixel> getPixels() {
        return pixels;
    }

    public ArrayList<Pixel> getMappingPixels(int keepFlags) {
        int discardFlags = ~keepFlags;
        ArrayList<Pixel> mappingPixels = new ArrayList<>(pixels.size());
        for(Pixel p : pixels) {
            if(p.getPosition() == null) continue;
            if(p.isFlagged(discardFlags)) continue;
            
            for(Channel channel : p) if(channel.isUnflagged(discardFlags)) {
                mappingPixels.add(p);
                break;
            }
        }
        return mappingPixels;
    }


    public Hashtable<String, Pixel> getPixelLookup() {
        Hashtable<String, Pixel> lookup = new Hashtable<>(pixels.size());
        for(Pixel pixel : pixels) lookup.put(pixel.getID(), pixel);
        return lookup;
    }


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
        final List<? extends Pixel> mappingPixels = getMappingPixels(~instrument.getSourcelessChannelFlags());

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

        final ArrayList<Pixel> perimeter = new ArrayList<>(sections);
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
        if(Double.isNaN(angle)) return;
        if(angle == 0.0) return;
        
        instrument.info("Applying rotation at " + Util.f1.format(angle / Unit.deg) + " deg.");

        // Undo the prior rotation...
        Vector2D priorOffset = getPointingOffset(rotation);
        Vector2D newOffset = getPointingOffset(rotation + angle);

        for(Pixel pixel : getPixels()) if(pixel.getPosition() != null) {
            Vector2D position = pixel.getPosition();

            // Center positions on the rotation center...
            position.subtract(priorOffset);
            // Do the rotation...
            position.rotate(angle);
            // Re-center on the pointing center...
            position.add(newOffset);
        }
        
        rotation += angle;
    }


    public final Vector2D getPointingOffset(double angle) { return getInstrument().getPointingOffset(angle); }
    
    public double getRotation() { return rotation; }

    public void setRotation(double angle) { rotation = angle; }
    
    public void setReferencePosition(Vector2D position) {
        final Vector2D referencePosition = position.copy();
        pixels.parallelStream().map(p -> p.getPosition()).filter(pos -> pos != null).forEach(pos -> pos.subtract(referencePosition));
    }
    


    /**
     * Returns the offset of the pointing center w.r.t. the optical axis in the natural focal-plane system of the instrument.
     * 
     * @return          The focal plane offset of the pointing center from the optical axis in the natural coordinate system
     *                  of the instrument. 
     * 
     */
    public Vector2D getPointingCenterOffset() { return new Vector2D(); }

    
    public void scramble() {
        instrument.notify("!!! Scrambling pixel position data (noise map only) !!!");

        List<? extends Pixel> pixels = getPixels();

        Vector2D temp = null;

        int switches = (int) Math.ceil(pixels.size() * ExtraMath.log2(pixels.size()));

        for(int n=switches; --n >= 0; ) {
            int i = (int) (pixels.size() * Math.random());
            int j = (int) (pixels.size() * Math.random());
            if(i == j) continue;

            Vector2D pos1 = pixels.get(i).getPosition();
            if(pos1 == null) return;

            Vector2D pos2 = pixels.get(j).getPosition();
            if(pos2 == null) return;

            if(temp == null) temp = pos1.copy();
            else temp.copy(pos1);

            pos1.copy(pos2);
            pos2.copy(temp);
        }       
    }
    

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("rot")) return getRotation() / Unit.deg;
        return TableFormatter.NO_SUCH_DATA;        
    }

    public void reindex() {
        for(int i=pixels.size(); --i >= 0; ) pixels.get(i).setIndex(i);
    }
    
    public final boolean slim(int discardFlags) {
        boolean removedChannels = false;
        
        for(Pixel pixel : getPixels()) removedChannels |= pixel.removeFlagged(discardFlags);
        
        if(removedChannels) {
            final int np = pixels.size();
            final ArrayList<Pixel> slimmed = new ArrayList<>(np);
            
            for(int k=0; k<np; k++) {
                final Pixel pixel = pixels.get(k);
                if(!pixel.isEmpty()) slimmed.add(pixel);
            }
            
            slimmed.trimToSize();
            pixels.clear();
            pixels.addAll(slimmed);
            pixels.trimToSize();
            reindex();
        }
        
        return removedChannels;
    }
    


    public void readRCP(String fileName)  throws IOException {      
        instrument.info("Reading RCP from " + fileName);

        final Hashtable<String, Pixel> idLookup = getPixelLookup(); 
        final boolean useGains = hasOption("rcp.gains");

        if(useGains) instrument.info("Initial gains are from RCP file.");

        new LineParser() {

            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                int columns = tokens.countTokens();
                Pixel pixel = idLookup.get(tokens.nextToken());

                if(pixel == null) return false;

                double sourceGain = tokens.nextDouble();
                double coupling = (columns == 3 || columns > 4) ? sourceGain / tokens.nextDouble() : sourceGain;

                pixel.coupling = (sourceGain == 0.0) ? 0.0 : 1.0; // Default coupling gain...

                if(useGains) pixel.coupling = coupling; 

                Vector2D position = pixel.getPosition();
                position.setX(tokens.nextDouble() * Unit.arcsec);
                position.setY(tokens.nextDouble() * Unit.arcsec);
                return true;
            }

        }.read(fileName);


        instrument.flagInvalidPositions();

        if(hasOption("rcp.center")) {
            Vector2D offset = option("rcp.center").getVector2D();
            offset.scale(Unit.arcsec);
            pixels.parallelStream().map(p -> p.getPosition()).forEach(pos -> pos.subtract(offset));
        }

        if(hasOption("rcp.rotate")) {
            double angle = option("rcp.rotate").getDouble() * Unit.deg;
            pixels.parallelStream().map(p -> p.getPosition()).forEach(pos -> pos.rotate(angle));
        }

        if(hasOption("rcp.zoom")) {
            double zoom = option("rcp.zoom").getDouble();
            pixels.parallelStream().map(p -> p.getPosition()).forEach(pos -> pos.scale(zoom));
        }

    }


    
    public String getRCPHeader() { return "ch\t[Gpnt]\t[Gsky]ch\t[dX\"]\t[dY\"]"; }

    public void printPixelRCP(PrintStream out, String header)  throws IOException {
        out.println("# CRUSH Receiver Channel Parameter (RCP) Data File.");
        out.println("#");
        if(header != null) out.println(header);
        out.println("#");
        out.println("# " + getRCPHeader());

        for(Pixel pixel : getMappingPixels(~instrument.getSourcelessChannelFlags())) 
            if(pixel.getPosition() != null) if(!pixel.getPosition().isNaN()) 
                out.println(pixel.getRCPString());
    }


    

    public static void addLocalFixedIndices(GridIndexed geometric, int fixedIndex, double radius, Collection<Integer> toIndex) {

        final int row = fixedIndex / geometric.cols();
        final int col = fixedIndex % geometric.cols();

        final Vector2D pixelSize = geometric.getPixelSize();
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
