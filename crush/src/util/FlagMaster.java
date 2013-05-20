/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
package util;

import java.util.Hashtable;
import java.util.Vector;

public class FlagMaster {
	Class<?> ownerType;
	
	public int register(Class<?> owner, String name, byte id, boolean isHardwareFlag) {
		Flag flag = new Flag(this, owner, name, id, isHardwareFlag);
		list.add(flag);
		return flag.value;
	}
	
	public String toString(Flagging object) {
		Class<?> channelType = object.getClass();
	
		String code = "";
		int value = 1;
		for(int i=0; i<64; i++) if(object.isFlagged(value)) code += forValue(channelType, value).id;
			
		if(code.length() == 0) return "-";
		return code;
	}
	
	public void parse(Flagging object, String code) {
		Class<?> ownerType = object.getClass();
		
		object.unflag();
		
		for(byte c : code.getBytes()) {
			Flag flag = forID(ownerType, c);
			if(flag != null) object.flag(flag.value);
			else System.err.println("WARNING! Unknown flag type '" + c + "' for " + ownerType.getSimpleName() + ".");
		}
	}
	
	public ClassLookup getFlags(Class<?> ownerClass) {
		if(!registry.containsKey(ownerClass))
			registry.put(ownerClass, new ClassLookup(ownerClass));
		return registry.get(ownerClass);		
	}
		
	
	public int getHardwareFlags() { return hardwareFlags; }
	public int getSoftwareFlags() { return softwareFlags; }
	
	
	public boolean containsID(Class<?> type, byte id) {
		for(Class<?> ownerType: registry.keySet())
			if(ownerType.isAssignableFrom(type)) if(registry.get(ownerType).contains(id)) return true;				
		return false;
	}
	
	public boolean containsName(Class<?> type, String name) {
		for(Class<?> ownerType: registry.keySet())
			if(ownerType.isAssignableFrom(type)) if(registry.get(ownerType).contains(name)) return true;				
		return false;
	}
	
	public boolean containsValue(Class<?> type, int value) {
		for(Class<?> ownerType: registry.keySet())
			if(ownerType.isAssignableFrom(type)) if(registry.get(ownerType).contains(value)) return true;				
		return false;
	}
	
	public Flag forID(Class<?> type, byte id) {
		for(Class<?> ownerType: registry.keySet())
			if(ownerType.isAssignableFrom(type)) {
				ClassLookup flags = registry.get(ownerType);
				if(flags.contains(id)) 
					return flags.get(id);
			}
		return null;
	}
	
	public Flag forName(Class<?> type, String name) {
		for(Class<?> ownerType: registry.keySet())
			if(ownerType.isAssignableFrom(type)) {
				ClassLookup flags = registry.get(ownerType);
				if(flags.contains(name)) 
					return flags.get(name);
			}
		return null;
	}
	
	public Flag forValue(Class<?> type, int value) {
		for(Class<?> ownerType: registry.keySet())
			if(ownerType.isAssignableFrom(type)) {
				ClassLookup flags = registry.get(ownerType);
				if(flags.contains(value)) 
					return flags.get(value);
			}
		return null;
	}
	
	
	public class Flag {
		byte id;
		String name;
		int value;
		boolean isHardwareFlag;
		
		Class<?> owner;
		
		private Flag(FlagMaster manager, Class<?> owner, String name, byte id, boolean isHardwareFlag) {
			this.owner = owner;	
			
			if(containsID(owner, id))
				throw new IllegalArgumentException("Flag id '" + id + "' is already reserved for " + owner.getSimpleName() + ".");
			if(containsName(owner, name))
				throw new IllegalArgumentException("Flag name '" + name + "' is already reserved for " + owner.getSimpleName() + ".");
			
			this.id = id;
			this.name = name;
			this.isHardwareFlag = isHardwareFlag;
				
			value = 1<<(nextBit++);
			
			if(isHardwareFlag) hardwareFlags |= value;
			else softwareFlags |= value;
			
			getFlags(owner).register(this);
		}
	}
		
		
	public class ClassLookup {
		private Class<?> channelType;

		private Hashtable<Integer, Flag> valueLookup = new Hashtable<Integer, Flag>();
		private Hashtable<Byte, Flag> idLookup = new Hashtable<Byte, Flag>();
		private Hashtable<String, Flag> nameLookup = new Hashtable<String, Flag>();
		
		public void register(Flag flag) {
			valueLookup.put(flag.value, flag);
			idLookup.put(flag.id, flag);
			nameLookup.put(flag.name, flag);		
		}
		
		public ClassLookup(Class<?> ownerType) {
			this.channelType = ownerType;
		}
		
		public boolean contains(byte id) { return idLookup.containsKey(id); }
		
		public boolean contains(String name) { return nameLookup.containsKey(name); }
		
		public boolean contains(int value) { return valueLookup.containsKey(value); }
		
		public Flag get(byte id) { return idLookup.get(id);	}
		
		public Flag get(String name) { return nameLookup.get(name); }
		
		public Flag get(int value) { return valueLookup.get(value); }
		
		
		public Class<?> getChannelType() { return channelType; }
		
	}

	
	
	private Vector<Flag> list = new Vector<Flag>();
	
	private Hashtable<Class<?>, ClassLookup> registry = new Hashtable<Class<?>, ClassLookup>();
	private int hardwareFlags = 0;
	private int softwareFlags = 0;
	
	private int nextBit = 0;
}
