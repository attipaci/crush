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

import java.util.HashMap;

/**
 * A class that facilitates the lookup of {@link Channel}s in a group by their unique {@link String} IDs, or integer (0-based)
 * fixed indices. Its implementation is based on two internal {@link HashMap}s for efficient access.
 * 
 * 
 * @author Attila Kovacs <attila@sigmyne.com>
 *
 * @param <ChannelType>     The generic type of the channels contained in this lookup table.
 */
public class ChannelLookup<ChannelType extends Channel> {
    private HashMap<String, ChannelType> ids;
    private HashMap<Integer, ChannelType> fixedIndices;
    
    /**
     * Constructs a lookup table for the given group of channels.
     * 
     * @param channels  The group of channels for which to construct the lookup for.
     */
    public ChannelLookup(ChannelGroup<? extends ChannelType> channels) {
        ids = new HashMap<>(channels.size());
        fixedIndices = new HashMap<>(channels.size());
        
        for(ChannelType channel : channels) {
            String id = channel.getID();
            if(ids.containsKey(id)) CRUSH.warning(this, "Duplicate id " + id + " in lookup");
            else ids.put(id, channel);
            
            int indexID = channel.getFixedIndex();
            if(fixedIndices.containsKey(indexID)) CRUSH.warning(this, "Duplicate fixed index " + indexID + " in lookup");
            else fixedIndices.put(indexID, channel);  
        }
    }

    /**
     * Checks if a {@link Channel} by the given fixed index is in the lookup.
     * 
     * 
     * @param fixedIndex    The 0-based integer fixed index of the channel. 
     * @return              <code>true</code> id the lookup contains the channel by that index, otherwise <code>false</code>
     */
    public boolean contains(int fixedIndex) {
        return fixedIndices.containsKey(fixedIndex);
    }
    
    /**
     * Checks if a {@link Channel} by the given {@link String} ID is in the lookup. Channels are also always understood
     * to have an automatic 1-based String ID equals to 1+{@link Channel#getFixedIndex()}, i.e. channel 0 will always have an
     * automatically associated ID of "1" etc.
     * 
     * 
     * @param id    The unique String ID of the channel (case sensitive!)
     * @return      <code>true</code> id the lookup contains the channel by that ID, otherwise <code>false</code>
     */
    public boolean contains(String id) {
        if(ids.containsKey(id)) return true;
        
        // If the automatic id is a number, we can try to interpret is as an automatic 1-based index.
        try { return fixedIndices.containsKey(Integer.parseInt(id) - 1); }
        catch(NumberFormatException e) {}
        
        return false;
    }
    
    /**
     * Returns the {@link Channel} by the given {@link String}, or <code>null</code> if the lookup contains no
     * channel by the specified ID.
     * 
     * 
     * @param fixedIndex    The 0-based integer fixed index of the channel. 
     * @return              <code>true</code> id the lookup contains the channel by that index, otherwise <code>false</code>
     */
    public ChannelType get(int fixedIndex) {
        return fixedIndices.get(fixedIndex);       
    }
    
    /**
     * Returns the {@link Channel} by the given {@link String}, or <code>null</code> if the lookup contains no
     * channel by the specified ID. Channels are also always understood to have an automatic 1-based String ID equals to 
     * 1+{@link Channel#getFixedIndex()}, i.e. channel 0 will always have an associated ID of "1" etc.
     * 
     * 
     * @param id    The unique String ID of the channel (case sensitive!)
     * @return      <code>true</code> id the lookup contains the channel by that ID, otherwise <code>false</code>
     */
    public ChannelType get(String id) {
        ChannelType channel = ids.get(id);
        
        if(channel == null) {
            // If the id is a number, we can try to interpret is as an automatic 1-based index.
            try { return fixedIndices.get(Integer.parseInt(id) - 1); }
            catch(NumberFormatException e) {}
        }
        
        return channel;
    }
    
}
