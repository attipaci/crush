/*******************************************************************************
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/


package crush;

import java.lang.reflect.*;
import java.util.*;

import jnum.Copiable;
import jnum.data.Statistics;

/**
 * A class for groping channels together. For example, all channels read out through the same readout MUX may
 * constitute such a group. Also, pixels in an instrument, with one or more channels associated to them, are 
 * also an example of a <code>ChannelGroup</code>.
 * <p>
 * 
 * Channel groups which exhibit a correlated behavior, such as correlated noise or a common response to an
 * external signal (such as an electronic modulationm, temeprature drifts, or telescope motion), are associated
 * to an appropriate {@link Mode} (such as a {@link Response} or {@link CorrelatedMode}). 
 * <p>
 * 
 * Channel groups are typically created by {@link Instrument#createGroups()}. They can be hard-coded groups,
 * or else specified in the runtime configurations via the <code>group</code> option.
 * <p>
 * 
 * 
 * @see Pixel
 * @see Mode
 * @See ChannelDivision
 * 
 * 
 * @author Attila Kovacs <attila@sigmyne.com>
 *
 * @param <ChannelType>     The generic type of the channels contained in this group.
 * 
 * 
 */
public class ChannelGroup<ChannelType extends Channel> extends ArrayList<ChannelType> 
implements Copiable<ChannelGroup<ChannelType>> {
    /**
     * 
     */
    private static final long serialVersionUID = -922794075467674753L;

    private String name;
    private int parallelism = 1;

    public ChannelGroup(String name) {
        this.name = name;
    }

    public ChannelGroup(String name, int size) {
        super(size);
        this.name = name;
    }

    public ChannelGroup(String name, Collection<? extends ChannelType> channelList) {
        this(name);
        addAll(channelList);
    }

    public void setThreadCount(int threads) { parallelism = threads; }

    public int getThreadCount() { return parallelism; }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelGroup<ChannelType> copy() {
        ChannelGroup<ChannelType> copy = (ChannelGroup<ChannelType>) clone();
        copy.clear();
        for(int i=0; i<size(); i++) copy.add((ChannelType) get(i).copy());
        return copy;
    }

    public String getName() { return name; }

    public void setName(String value) { name = value; }

    public ChannelGroup<ChannelType> createGroup() {
        // All good channels
        ChannelGroup<ChannelType> channels = new ChannelGroup<>(name, size());
        channels.addAll(this);
        return channels;
    }

    public final int add(String spec, ChannelLookup<ChannelType> lookup) {
        return add(spec, lookup, 0);
    }
    
    public int add(String spec, ChannelLookup<ChannelType> lookup, int excludeFlags) {

        ChannelType channel = lookup.get(spec);
        
        if(channel != null) {
            if(channel.isUnflagged(excludeFlags)) {
                add(channel);
                return 1;
            }
        }
        
        else if(spec.contains("-")) {
            StringTokenizer tokens = new StringTokenizer(spec, "-");
            if(tokens.countTokens() != 2) return 0;
            
            String from = tokens.nextToken();
            String to = tokens.nextToken();
            
            if(!lookup.contains(from)) return 0;
            if(!lookup.contains(to)) return 0;
            
            int fromIndex = lookup.get(from).getFixedIndex();
            int toIndex = lookup.get(to).getFixedIndex();
            
            int n=0;
            
            for(int i=fromIndex; i <= toIndex; i++) {
                channel = lookup.get(i);
                if(channel != null) if(channel.isUnflagged(excludeFlags)) {
                    add(channel);
                    n++;
                }
            }
            
            return n;
        }
        
        return 0;
    }
    
    
    public ArrayList<String> getIDs() { 
        ArrayList<String> ids = new ArrayList<>(size());
        for(int i=0; i<size(); i++) ids.add(get(i).getID());
        return ids;
    }
    
    public ArrayList<Integer> getFixedIndices() {
        ArrayList<Integer> indices = new ArrayList<>(size());
        for(int i=0; i<size(); i++) indices.add(get(i).getFixedIndex());
        return indices;
    }
    
    /**
     * Flags channels as dead (with {@link Channel#FLAG_DEAD}) if they have any of the bit-wise flags enabled matching
     * the specified pattern.
     * 
     * @param flagPattern   The bit-wise flag pattern. Channels that have any of the specified flag bits enabled will be marked
     *                      with {@link Channel#FLAG_DDEAD}.
     */
    public void killFlagged(final int flagPattern) {
        for(Channel channel : this) if(channel.isFlagged(flagPattern)) channel.flag(Channel.FLAG_DEAD);
    }

    /**
     * Removes channels from this group whose flags match any of the bitwise flags in the specified pattern.
     * 
     * 
     * @param discardFlags  The bit-wise flag pattern. Channels that have any of the specified flag bits enabled 
     *                      will be removed from the group.
     * @return              <code>true</code> if matching channels have been found and removed, otherwise <code>false</code>.
     */
    public boolean removeFlagged(int discardFlags) {
        final int nc = size();
        
        boolean hasFlagged = false;
        for(ChannelType channel : this) if(channel.isFlagged(discardFlags)) {
            hasFlagged = true;
            break;
        }
       
        if(!hasFlagged) return false;
        
        ArrayList<ChannelType> keep = new ArrayList<>(nc);
        for(int i=0; i<nc; i++) if(get(i).isUnflagged(discardFlags)) keep.add(get(i));

        clear(); 
        addAll(keep);
        trimToSize();
        
        return true;
    }

    public void order(final Field field) {	
        Comparator<ChannelType> ordering = new Comparator<ChannelType>() {
            @Override
            public int compare(ChannelType c1, ChannelType c2) {
                try {
                    if(field.getInt(c1) < field.getInt(c2)) return -1;
                    else if(field.getInt(c1) > field.getInt(c2)) return 1;
                    else return 0;
                }
                catch(IllegalAccessException e) { CRUSH.error(this, e); }
                return 0;
            }				
        };

        Collections.sort(this, ordering);
    }

    public double getTypicalGainMagnitude(float[] G, int excludeFlag) {
        final double[] values = new double[size()];
        int n = 0;
        for(int k=size(); --k >= 0; ) if(get(k).isUnflagged(excludeFlag)) if(!Float.isNaN(G[k]))
            values[n++] = Math.log(1.0 + Math.abs(G[k]));

        // Use a robust mean (with 10% tails) to calculate the average gain...
        double aveG = Statistics.Inplace.robustMean(values, 0, n, 0.1);

        if(Double.isNaN(aveG)) return 1.0;

        aveG = Math.exp(aveG) - 1.0;
        return aveG > 0.0 ? aveG : 1.0;
    }


    public ChannelGroup<ChannelType> discard(int flagPattern) {
        return discard(flagPattern, DISCARD_ANY_FLAG);
    }


    public ChannelGroup<ChannelType> discard(int flagPattern, int criterion) {

        for(int i=size(); --i >= 0; ) {
            Channel channel = get(i);
            
            switch(criterion) {
            case DISCARD_ANY_FLAG:
                if(channel.isFlagged(flagPattern)) remove(i); break; 
            case DISCARD_ALL_FLAGS:
                if((channel.getFlags() & flagPattern) == flagPattern) remove(i); break; 
            case DISCARD_MATCH_FLAGS:
                if(channel.getFlags() == flagPattern) remove(i); break;
            case KEEP_ANY_FLAG:
                if(channel.isUnflagged(flagPattern)) remove(i); break; 
            case KEEP_ALL_FLAGS:
                if((channel.getFlags() & flagPattern) != flagPattern) remove(i); break; 
            case KEEP_MATCH_FLAGS:
                if(channel.getFlags() != flagPattern) remove(i); break;
            }	
        }

        trimToSize();
        return this;
    }

    public abstract class Fork<ReturnType> extends CRUSH.Fork<ReturnType> {	
        public Fork() { super(size(), getThreadCount()); }

        @Override
        protected final void processIndex(int index) { process(get(index)); }

        protected abstract void process(ChannelType channel);
    }


    public static final int DISCARD_ANY_FLAG = 0;
    public static final int DISCARD_ALL_FLAGS = 1;
    public static final int DISCARD_MATCH_FLAGS = 2;
    public static final int KEEP_ANY_FLAG = 3;
    public static final int KEEP_ALL_FLAGS = 4;
    public static final int KEEP_MATCH_FLAGS = 5;

}
