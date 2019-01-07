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

package crush;

import java.util.Hashtable;

public class ChannelLookup<ChannelType extends Channel> {
    private Hashtable<String, ChannelType> ids;
    private Hashtable<String, ChannelType> indices;
    
    public ChannelLookup(ChannelGroup<ChannelType> channels) {
        ids = new Hashtable<String, ChannelType>(channels.size());
        indices = new Hashtable<String, ChannelType>(channels.size());
        
        for(ChannelType channel : channels) {
            String id = channel.getID();
            if(ids.containsKey(id)) CRUSH.warning(this, "Duplicate channel id " + id + " in lookup");
            else ids.put(id, channel);
            
            String indexID = Integer.toString(channel.getFixedIndex() + 1);
            if(indices.containsKey(indexID)) CRUSH.warning(this, "Duplicate channel index " + indexID + " in lookup");
            else indices.put(indexID, channel);  
        }
    }
    
    public boolean contains(String id) {
        if(ids.contains(id)) return true;
        if(indices.contains(id)) return true;
        return false;
    }
    
    public ChannelType get(String id) {
        ChannelType channel = ids.get(id);
        if(channel == null) channel = indices.get(id);
        return channel;        
    }
    
}
