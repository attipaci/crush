/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2009 Attila Kovacs 

package util;

import nom.tam.fits.*;
import nom.tam.util.*;

import java.io.*;
import java.util.*;


public class Configurator implements Cloneable {
	private Configurator root;
	private String value;
	public boolean isEnabled = false;
	public boolean wasUsed = false;
	public int index;
	
	public Hashtable<String, Configurator> branches = new Hashtable<String, Configurator>();
	public Hashtable<String, Vector<String>> conditionals = new Hashtable<String, Vector<String>>();
	public Vector<String> blacklist = new Vector<String>();
	//public Vector<Class<?>> users = new Vector<Class<?>>(); // Not yet used...
	
	private static int counter = 0;	
	public static boolean verbose = false;
	
	public Configurator() { root = this; }
	
	public Configurator(Configurator root) { this.root = root; }
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@SuppressWarnings("unchecked")
	public Configurator copy() {
		Configurator copy = (Configurator) clone();
		copy.branches = new Hashtable<String, Configurator>();
		copy.blacklist = (Vector<String>) blacklist.clone();
		copy.conditionals = new Hashtable<String, Vector<String>>();
		for(String key : branches.keySet()) copy.branches.put(key, branches.get(key).copy());
		for(String key : conditionals.keySet()) copy.conditionals.put(key, (Vector<String>) conditionals.get(key).clone());
		return copy;
	}
	
	@Override
	public boolean equals(Object o) {
		if(value == null) return o == null;
		if(o instanceof String) return ((String) o).equalsIgnoreCase(value);
		else return super.equals(o);
	}
	
	@Override
	public int hashCode() {
		return value.hashCode();
	}
	
	public void parse(Vector<String> lines) {
		for(String line : lines) parse(line);		
	}
	
	public void parse(String line) {
		Entry entry = new Entry(line);
		if(entry != null) process(entry.key, entry.value);
	}
	
	
	/*
	protected String resolve(String value) {
		if(!value.contains("{#")) return value;
			
		StringBuffer resolved = new StringBuffer();
		
		int from = value.indexOf("{#");
		int to = from;
		resolved.append(value, 0, from);
		
		System.err.println("### from " + from);
		
		while(from >= 0) {
			to = value.indexOf("}", from);
			System.err.println("### from " + from);
			if(to < 0) {
				// If no closing bracket, then just quote the rest as literal, including
				// the opening bracket...
				System.err.println("### unclosed!");
				resolved.append(value, from, value.length());
				return new String(resolved);
			}
			String key = value.substring(from + 2, to);
			
			System.err.println("### resolving " + key);
			
			if(key.length() > 0) if(isConfigured(key)) {
				String substitute = get(key).getValue();
				System.err.println("### substitute " + substitute);
				if(substitute != null) resolved.append(substitute);
			}
			
			from = value.indexOf("{#", to);
		}
		resolved.append(value, to + 1, value.length());
		return new String(resolved);
	}
	*/
	
	protected String unalias(String key) {
		String branchName = getBranchName(key);
		String unaliased = branchName;
		
		// Check if the requested key branch is aliased. If so, process as such...
		if(containsExact("alias." + branchName)) {
			Configurator alias = getExact("alias." + branchName);
			if(alias.isEnabled) {
				unaliased = alias.value;
				if(verbose) System.err.println("<a> '" + branchName + "' -> '" + unaliased + "'");
			}
		}
		
		if(key.length() != branchName.length()) unaliased += getRemainder(key, branchName.length());
		
		return unaliased;
	}
	
	protected String unaliasedKey(String key) {
		key = unalias(key.toLowerCase());
		int pos = 0;
		for(; pos<key.length(); pos++) {
			switch(key.charAt(pos)) {
			case ' ':
			case '\t':
			case '=':
			case ':': return key.substring(0, pos);
			}
		}
		return key;
	}
	
	protected String resolve(String argument, String marker, String endmarker) {
		int index = 0;
		
		// If these is nothing to resolve, just return the argument as is...
		if(!argument.contains(marker)) return argument;
		
		// Now for the hard part...
		StringBuffer resolved = new StringBuffer();
		
		for(;;) {
			if(index >= argument.length()) break;
			int i = argument.indexOf(marker, index);
			if(i < 0) {
				resolved.append(argument, index, argument.length());
				break;
			}				
			resolved.append(argument, index, i);
			
			int from = i + marker.length();
			int to = argument.indexOf(endmarker, from);
			
			if(to < 0) {
				resolved.append(argument, index, argument.length());
				break;
			}
			if(to == from) resolved.append(getValue());
			else {
				String key = argument.substring(from, to);
				String property = getProperty(key, marker);
				if(property != null) resolved.append(property);
				else resolved.append(argument, i, to + endmarker.length());
			}
			index = to + endmarker.length();			
		}
		return new String(resolved);
	}
	
	protected String getProperty(String name, String marker) {
		if(marker.charAt(0) != '{') return null;
		char c = marker.charAt(1);
		
		switch(c) {
		case '?' :
		case '&' :
			return getProperty(name.toLowerCase());
		case '@' :
			return System.getenv(name);
		case '#' :
			return System.getProperty(name);
		default : return null;
		}
	}
	
	public String getProperty(String name) {
		return containsKey(name) ? get(name).getValue() : null;		
	}
	
	public void process(String key, String argument) {	
		String substitute = unalias(key);
	
		if(!key.equals(substitute)) {
			key = new StringTokenizer(substitute.toLowerCase(), " \t=:").nextToken();
			if(substitute.length() > key.length()) argument = substitute.substring(key.length()+1) + argument;
			// TODO uncomment to support compound aliasing...
			// process(key, argument);
			// return;
		}
	
		argument = resolve(argument, "{&", "}"); // Resolve static references
		argument = resolve(argument, "{@", "}"); // Resolve environment variables.
		argument = resolve(argument, "{#", "}"); // Resolve Java properties.
		
		if(key.equals("forget")) for(String name : getList(argument)) forget(name);
		else if(key.equals("recall")) for(String name : getList(argument)) recall(name);
		else if(key.equals("enable")) for(String name : getList(argument)) forget(name);
		else if(key.equals("disable")) for(String name : getList(argument)) recall(name);
		else if(key.equals("blacklist")) {
			if(argument.length() == 0) pollBlacklist(null);
			for(String name : getList(argument)) blacklist(name);
		}
		else if(key.equals("whitelist")) for(String name : getList(argument)) whitelist(name);
		else if(key.equals("remove")) for(String name : getList(argument)) remove(name);
		else if(key.equals("restore")) for(String name : getList(argument)) restore(name);
		else if(key.equals("replace")) for(String name : getList(argument)) restore(name);
		else if(key.equals("config")) {
			try { readConfig(Util.getSystemPath(argument)); }
			catch(IOException e) { System.err.println("WARNING! Configuration file '" + argument + "' no found."); }
		}
		else if(key.equals("poll")) {
			poll(argument.length() > 0 ? unaliasedKey(argument) : null);
		}
		else if(key.equals("conditions")) {
			pollConditions(argument.length() > 0 ? argument : null);
		}
		else if(key.equals("echo")) {
			System.out.println(resolve(argument, "{?", "}"));
		}
		else {
			String branchName = getBranchName(key);
			if(verbose) System.err.println("<.> " + branchName);

			if(branchName.equals("*")) {
				for(String name : new ArrayList<String>(branches.keySet())) process(name + key.substring(1), argument);				
			}	
			else if(branchName.startsWith("[")) {
				String condition = branchName.substring(1, branchName.indexOf(']'));
				String setting = key.substring(condition.length() + 2).trim() + " " + argument;
				addCondition(condition, setting);
			}
			else if(!blacklist.contains(key)) set(branchName, key, argument);
		}
	}

	private void set(String branchName, String key, String argument) {
		setCondition(key, argument);
		Configurator branch = branches.containsKey(branchName) ? branches.get(branchName) : new Configurator(root);
		if(key.length() == branchName.length()) {
			if(verbose) System.err.println("<=> " + argument);
			branch.value = argument;
			branch.isEnabled = true;
			branch.index = counter++; // Update the serial index for the given key...
		}
		else branch.process(getRemainder(key, branchName.length() + 1), argument);
		branches.put(branchName, branch);		
	}
	
	private void addCondition(String condition, String setting) {		
		if(isSatisfied(condition)) parse(setting);
		else {
			Vector<String> list = conditionals.containsKey(condition) ? conditionals.get(condition) : new Vector<String>();
			list.add(setting);
			
			// Remove leading spaces and replace assignments and other spaces with a single ?
			StringBuffer canonized = new StringBuffer(condition.length());
			boolean leadingSpace = true;
			boolean substituted = false;
			
			for(int i=0; i<condition.length(); i++) {
				char c = condition.charAt(i);
				
				if(c == ' ' || c == '\t') {
					if(leadingSpace) continue;
					
					if(!substituted) canonized.append('?');
					substituted = true;
				}		
				else if(c == '=') {
					if(!substituted) canonized.append('?');
					substituted = true;
				}
				else {
					canonized.append(c);
					substituted = false;
				}
				
				leadingSpace = false;
			}
	
			conditionals.put(new String(canonized), list);
		}
	}
	
	public boolean isSatisfied(String condition) {
		// If the conditional key is already defined, then simply parse the argument of the condition
		if(condition.contains("?")) {
			StringTokenizer pair = new StringTokenizer(condition, "?");
			String conditionKey = pair.nextToken().toLowerCase();
			if(isConfigured(conditionKey)) if(get(conditionKey).equals(pair.nextToken())) return true;
		}
		else if(isConfigured(condition.toLowerCase())) return true;
		
		return false;
	}
	
	private List<String> getList(String argument) {
		ArrayList<String> list = new ArrayList<String>();
		StringTokenizer tokens = new StringTokenizer(argument, " \t,");
		while(tokens.hasMoreTokens()) list.add(tokens.nextToken());
		return list;
	}
	
	
	public void setCondition(String key, String value) {
		setCondition(key);
		setCondition(key + "?" + value);
	}
	
	public void setCondition(String expression) {
		//expression.toLowerCase();
		//System.err.println("### " + expression);
		
		if(!conditionals.containsKey(expression)) return;
		else {
			if(verbose) System.err.println("[c] " + expression + " > " + conditionals.get(expression));
			parse(conditionals.get(expression));
		}
	}
	
	public void forget(String arg) {
		
		if(arg.equals("blacklist")) {
			blacklist.clear();
			for(String name : branches.keySet()) branches.get(name).forget(arg);
			return;
		}
		else if(arg.equals("conditions")) {
			conditionals.clear();
			for(String name : branches.keySet()) branches.get(name).forget(arg);
			return;
		}
		
		String branchName = getBranchName(arg);
		
		if(branchName.equals("*")) {
			for(String name : new ArrayList<String>(branches.keySet())) forget(name + getRemainder(arg, 1));
		}	
		else {
			String key = unaliasedKey(branchName);
			if(key.contains(".")) forget(key + getRemainder(arg, branchName.length()));
			else if(branches.containsKey(key)) { 
				Configurator branch = branches.get(key);
				if(arg.length() != branchName.length()) branch.forget(getRemainder(arg, branchName.length() + 1));
				else branch.isEnabled = false;
			}
		}
	}
	
	public void recall(String arg) {
		String branchName = getBranchName(arg);
		
		if(branchName.equals("*")) {
			for(String name : new ArrayList<String>(branches.keySet())) recall(name + getRemainder(arg, 1));
		}	
		else {
			String key = unaliasedKey(branchName);
			if(key.contains(".")) recall(key + getRemainder(arg, branchName.length()));
			else if(branches.containsKey(key)) { 
				Configurator branch = branches.get(key);
				if(arg.length() != branchName.length()) branch.forget(getRemainder(arg, branchName.length() + 1));
				else if(!blacklist.contains(key)) {
					Configurator option = branches.get(key);
					option.isEnabled = true;
					option.index = counter++;
					setCondition(arg, option.value);
				}
			}
		}
	}
	
	public void remove(String arg) {
		String branchName = getBranchName(arg);
		
		if(branchName.equals("*")) {
			for(String name : new ArrayList<String>(branches.keySet())) remove(name + getRemainder(arg, 1));
		}
		else if(branchName.startsWith("[") && branchName.endsWith("]")) {
			branchName = branchName.substring(1, branchName.length()-1).trim();
			for(String condition : new ArrayList<String>(conditionals.keySet())) if(condition.startsWith(branchName)) conditionals.remove(condition);
		}
		else {
			String key = unaliasedKey(branchName);
			if(key.contains(".")) remove(key + getRemainder(arg, branchName.length()));
			else if(branches.containsKey(key)) { 
				Configurator branch = branches.get(key);
				if(arg.length() != branchName.length()) branch.remove(getRemainder(arg, branchName.length() + 1));
				// Do not remove the removed key itself...
				else if(key.equals("removed")) return;
				else {
					if(verbose) System.err.println("<rm> " + key); 
					getRemoved().branches.put(key, branches.remove(key));
				}
			}
		}
	}
	
	public void purge(String arg) {
		String branchName = getBranchName(arg);
		
		if(branchName.equals("*")) {
			for(String name : new ArrayList<String>(branches.keySet())) purge(name + getRemainder(arg, 1));
		}
		else if(branchName.startsWith("[") && branchName.endsWith("]")) {
			branchName = branchName.substring(1, branchName.length()-1).trim();
			for(String condition : new ArrayList<String>(conditionals.keySet())) if(condition.startsWith(branchName)) conditionals.remove(condition);
		}
		else {
			String key = unaliasedKey(branchName);
			if(key.contains(".")) purge(key + getRemainder(arg, branchName.length()));
			else if(branches.containsKey(key)) { 
				Configurator branch = branches.get(key);
				if(arg.length() != branchName.length()) branch.purge(getRemainder(arg, branchName.length() + 1));
				else {
					if(verbose) System.err.println("<pg> " + key); 
					branches.remove(key);
				}
			}
		}
		
	}
	
	public Configurator getRemoved() {
		if(!branches.containsKey("removed")) branches.put("removed", new Configurator(root));
		return branches.get("removed");
	}
	
	
	public void restore(String arg) {
		String branchName = getBranchName(arg);
		
		if(branchName.equals("*")) {
			if(arg.length() == 1) for(String name : getRemoved().branches.keySet()) restore(name);
			else for(String name : new ArrayList<String>(branches.keySet())) restore(name + getRemainder(arg, 1));
		}
		else {
			String key = unaliasedKey(branchName);
			if(key.contains(".")) restore(key + getRemainder(arg, branchName.length()));
			else if(branches.containsKey(key)) { 
				Configurator branch = branches.get(key);
				if(arg.length() != branchName.length()) branch.restore(getRemainder(arg, branchName.length() + 1));
				else {
					Hashtable<String, Configurator> removedBranches = getRemoved().branches;
					
					if(!removedBranches.containsKey(key)) return;			
					if(verbose) System.err.println("<r> " + key);

					Configurator removedBranch = removedBranches.remove(key);			
					branches.put(key, removedBranch);
					
					if(removedBranches.isEmpty()) branches.remove("removed");
					
					// Disable the branch root if it is on the blacklist...
					if(blacklist.contains(key)) removedBranch.isEnabled = false;
				}
			}
		}
	}
	
	public void blacklist(String arg) {		
		String branchName = getBranchName(arg);
		String key = unaliasedKey(branchName);
		
		if(key.contains(".")) blacklist(key + getRemainder(arg, branchName.length()));
		else {
			if(!branches.containsKey(key)) branches.put(key, new Configurator(root));
			Configurator branch = branches.get(key);
			if(arg.length() != branchName.length()) branch.blacklist(getRemainder(arg, branchName.length() + 1));
			else {
				if(verbose) System.err.println("<b> " + key);
				branch.isEnabled = false;
				blacklist.add(key);
			}
		}
	}	

	public void whitelist(String arg) {
		String branchName = getBranchName(arg);
			
		if(branchName.equals("*")) {
			if(arg.length() == 1) for(String key : blacklist) whitelist(key);
			else for(String branch : branches.keySet()) whitelist(branch + getRemainder(arg, 1));
		}
		else {
			String key = unaliasedKey(branchName);
			if(key.contains(".")) whitelist(key + getRemainder(arg, branchName.length()));
			else if(branches.containsKey(key)) { 
				Configurator branch = branches.get(key);
				if(arg.length() != branchName.length()) branch.whitelist(getRemainder(arg, branchName.length() + 1));
				else {
					if(!blacklist.contains(key)) return;	
					if(verbose) System.err.println("<w> " + key);
					blacklist.remove(key);
				}
			}
		}
	}
	
	public boolean isBlacklisted(String arg) {
		String branchName = getBranchName(arg);
		String key = unaliasedKey(branchName);
		if(key.contains(".")) return isBlacklisted(key + getRemainder(arg, branchName.length()));
		else if(branches.containsKey(key)) { 
			Configurator branch = branches.get(key);
			if(arg.length() != branchName.length()) return branch.isBlacklisted(getRemainder(arg, branchName.length() + 1));
			else return blacklist.contains(key);
		}
		else return blacklist.contains(key);
	}
	
	// Looks for first period outside of square brackets (used for conditions)...
	public String getBranchName(String key) {
		int i=0;
		int open = 0;
		for(; i<key.length(); i++) {
			char c = key.charAt(i);
			if(c == '[') open++;
			else if(c == ']') open--;
			else if(open == 0 && c == '.') break;
		}
		
		if(i < key.length()) return key.substring(0, i);
		else return key;
	}
	
	public String getRemainder(String key, int from) {
		if(key.length() <= from) return "";
		else return key.substring(from);	
	}
	
	public Configurator get(String key) {
		String branchName = getBranchName(key);
		if(branchName.length() == key.length()) return branches.get(unaliasedKey(key));
		else if(branches.containsKey(branchName)) return branches.get(branchName).get(getRemainder(key, branchName.length() + 1));
		else return null;
	}
	
	public Configurator getExact(String key) {
		String branchName = getBranchName(key);
		if(branchName.length() == key.length()) return branches.get(key);
		else if(branches.containsKey(branchName)) return branches.get(branchName).get(getRemainder(key, branchName.length() + 1));
		else return null;		
	}
	
	public boolean containsKey(String key) {
		String branchName = getBranchName(key);
		String unaliased = unaliasedKey(branchName);
		if(!branches.containsKey(unaliased)) return false;
		if(key.length() == branchName.length()) return true;
		return branches.get(unaliased).containsKey(getRemainder(key, branchName.length() + 1));
	}
	
	public boolean containsExact(String key) {
		String branchName = getBranchName(key);
		if(!branches.containsKey(branchName)) return false;
		if(key.length() == branchName.length()) return true;
		return branches.get(branchName).containsExact(getRemainder(key, branchName.length() + 1));
	}
	
	public boolean isConfigured(String key) {
		if(!containsKey(key)) return false;
		Configurator option = get(key);
		if(!option.isEnabled) return false;
		option.wasUsed = true;
		return option.value != null;
	}	
	
	public void mapValueTo(String branchName) {
		if(value != null) if(value.length() > 0) {
			if(containsKey(branchName)) get(branchName).value = value;
			else process(branchName, value);
		}
		value = "";
	}
	
	/*
	public boolean hasUser(Class<?> c) {
		return users.contains(c);		
	}
	
	public boolean hasUserInstanceOf(Class<?> c) {
		for(Class<?> user : users) if(c.isAssignableFrom(user)) return true;
		return false;
	}
	
	public void addUser(Class<?> c) {
		if(users.contains(c)) return;
		users.add(c);
	}
	*/
	
	public void addUser(Object o) {
		addUser(o.getClass());
	}
	
	public void intersect(Configurator options) {
		for(String key : getKeys()) {
			if(!options.containsKey(key)) purge(key);
			else {
				Configurator option = get(key);
				Configurator other = options.get(key);
				if(option.isEnabled && !other.isEnabled) option.isEnabled = false;
				else if(!option.value.equals(other.value)) option.isEnabled = false;
			}
		}
	}
	
	// TODO Difference conditionals and blacklists too...
	public Configurator difference(Configurator options) {
		Configurator difference = new Configurator(root);

		for(String key : getKeys()) {
			if(!options.containsKey(key)) difference.parse(key + " " + get(key).value);
			else {
				Configurator option = get(key);
				Configurator other = options.get(key);
			
				if(option.isEnabled && !other.isEnabled) difference.parse(key + " " + get(key).value);
				else if(!option.value.equals(other.value)) difference.parse(key + " " + get(key).value);
			}
		}
		return difference;
	}
	
	public void setIteration(int i, int rounds) {	
		if(!branches.containsKey("iteration")) return;
		Hashtable<String, Vector<String>> settings = branches.get("iteration").conditionals;

		// Parse explicit iteration settings
		if(settings.containsKey(i + "")) parse(settings.get(i + ""));		

		// Parse relative iteration settings
		for(String spec : settings.keySet()) if(spec.endsWith("%")) {
			int k = (int) Math.round(rounds * 0.01 * Double.parseDouble(spec.substring(0, spec.length()-1)));
			if(i == k) parse(settings.get(spec));
		}

		// Parse end-based settings
		String spec = "last" + (i==rounds ? "" : "-" + (rounds-i));
		if(settings.containsKey(spec)) parse(settings.get(spec));
	}
	
	public String getValue() {
		return root.resolve(resolve(getRawValue(), "{?", "}"), "{?", "}"); 
	}
	
	public String getRawValue() {
		return value;
	}
	
	public double getDouble() {
		return Double.parseDouble(getValue());
	}
	
	public float getFloat() {
		return Float.parseFloat(getValue());
	}
	
	public int getInt() {
		return Integer.decode(getValue());
	}
	
	public boolean getBoolean() {
		return Util.parseBoolean(getValue());
	}
	
	public String getPath() {
		return Util.getSystemPath(getValue());
	}
	
	public Range getRange() {
		return Range.parse(getValue());		
	}
	
	public Range getRange(boolean nonNegative) {
		return Range.parse(getValue(), nonNegative);		
	}
	
	public Vector2D getVector2D() {
		return new Vector2D(getValue());		
	}
	
	
	public List<String> getList() {
		ArrayList<String> list = new ArrayList<String>();
		StringTokenizer tokens = new StringTokenizer(getValue(), " \t,");
		while(tokens.hasMoreTokens()) list.add(tokens.nextToken());
		return list;
	}
	
	public List<String> getLowerCaseList() {
		ArrayList<String> list = new ArrayList<String>();
		StringTokenizer tokens = new StringTokenizer(getValue(), " \t,");
		while(tokens.hasMoreTokens()) list.add(tokens.nextToken().toLowerCase());
		return list;		
	}
	
	public List<Double> getDoubles() {
		List<String> list = getList();
		ArrayList<Double> doubles = new ArrayList<Double>(list.size());	
		for(String entry : list) {
			try { doubles.add(Double.parseDouble(entry)); }
			catch(NumberFormatException e) { doubles.add(Double.NaN); }
		}
		return doubles;
	}
	
	public List<Float> getFloats() {
		List<String> list = getList();
		ArrayList<Float> floats = new ArrayList<Float>(list.size());	
		for(String entry : list) {
			try { floats.add(Float.parseFloat(entry)); }
			catch(NumberFormatException e) { floats.add(Float.NaN); }
		}
		return floats;
	}
	
	// Also takes ranges...
	public List<Integer> getIntegers() {
		List<String> list = getList();
		ArrayList<Integer> ints = new ArrayList<Integer>(list.size());	
		for(String entry : list) {
			try { ints.add(Integer.decode(entry)); }
			catch(NumberFormatException e) {
				Range range = Range.parse(entry, true);
				if(Double.isInfinite(range.min()) || Double.isInfinite(range.max())) throw e;
				int from = (int)Math.ceil(range.min());
				int to = (int)Math.floor(range.max());
				for(int i=from; i<=to; i++) ints.add(i);	
			}
		}
		return ints;
	}
	
	public List<String> getKeys() {
		ArrayList<String> keys = new ArrayList<String>();
		for(String branchName : branches.keySet()) {
			Configurator option = branches.get(branchName);	
			if(option.isEnabled) keys.add(branchName);
			for(String key : option.getKeys()) keys.add(branchName + "." + key);			
		}	
		return keys;
	}
	
	public List<String> getForgottenKeys() {
		ArrayList<String> keys = new ArrayList<String>();
		for(String branchName : branches.keySet()) {
			Configurator option = branches.get(branchName);
			if(!option.isEnabled) if(option.value != null) if(option.value.length() > 0) keys.add(branchName);
			for(String key : option.getForgottenKeys()) keys.add(branchName + "." + key);			
		}		
		return keys;		
	}
	
	public List<String> getBlacklist() {
		ArrayList<String> keys = new ArrayList<String>();
		keys.addAll(blacklist);
		
		for(String branchName : branches.keySet()) {
			Configurator option = branches.get(branchName);
			for(String key : option.getBlacklist()) keys.add(branchName + "." + key);		
		}		
		return keys;		
	}
	
	
	public Hashtable<String, Vector<String>> getConditions(boolean isBracketed) {
		Hashtable<String, Vector<String>> conditions = new Hashtable<String, Vector<String>>();
		for(String key : conditionals.keySet()) {
			conditions.put(isBracketed ? "[" + key + "]" : key, conditionals.get(key));
		}
		
		for(String branchName : branches.keySet()) {
			Hashtable<String, Vector<String>> branchConditions = branches.get(branchName).getConditions(isBracketed);	
			if(!branchConditions.isEmpty())
			for(String key : branchConditions.keySet()) conditions.put(branchName + "." + key, branchConditions.get(key));			
		}	
		return conditions;
	}
	

	
	public List<String> getTimeOrderedKeys() {		
		List<String> keys = getKeys();	
		Collections.sort(keys,
			new Comparator<String>() {
				public int compare(String key1, String key2) {
					int i1 = get(key1).index;
					int i2 = get(key2).index;
					if(i1 == i2) return 0;
					return i1 > i2 ? 1 : -1;
				}
		});
		return keys;
	}
	
	public List<String> getAlphabeticalKeys() {
		List<String> keys = getKeys();
		Collections.sort(keys);
		return keys;
	}
	
	public void print(PrintStream out) {
		poll(null, out, "#");
	}
	
	public void poll(String pattern) {
		poll(pattern, System.out, "");
		pollForgotten(pattern, System.out, "");
		System.out.println();
	}
	
	public void poll(String pattern, PrintStream out, String prefix) {
	
		if(pattern != null) {
			pattern = pattern.toLowerCase();
			while(pattern.endsWith("*")) pattern = pattern.substring(0, pattern.length()-1);
		}
		
		out.println();
		
		if(pattern == null) out.println(prefix + " Current configuration is: ");
		else System.out.println(prefix + " Currently set keys starting with '" + pattern + "': ");

		out.println(prefix + " --------------------------------------------------------------------");
		
		for(String key : getAlphabeticalKeys()) {
			if(pattern != null) if(!key.startsWith(pattern)) continue;
			
			out.print("   " + key);
			Configurator option = get(key);
			String value = option.getValue();
			if(value.length() > 0) out.print(" = " + value);
			out.println();
		}
		
		out.println(prefix + " --------------------------------------------------------------------");
	}
	
	public void pollForgotten(String pattern, PrintStream out, String prefix) {
		
		if(pattern != null) {
			pattern = pattern.toLowerCase();
			while(pattern.endsWith("*")) pattern = pattern.substring(0, pattern.length()-1);
		}
		
		List<String> list = getForgottenKeys();
		if(list.isEmpty()) return;
		
		out.println();

		if(pattern == null) out.println(prefix + " Recallable configuration keys are: ");
		else out.println(prefix + " Recallable keys starting with '" + pattern + "': ");
		
		out.println(prefix + " --------------------------------------------------------------------");
		
		
		Collections.sort(list);
		
		for(String key : list) {
			if(pattern != null) if(!key.startsWith(pattern)) continue;
			
			out.print("   (" + key);
			String value = get(key).value;
			if(value.length() > 0) out.print(" = " + value);
			out.print(")");
			if(isBlacklisted(key)) out.print(" --blacklisted--");
			out.println();
		}
		
		out.println(prefix + " --------------------------------------------------------------------");
	}
	
	public void pollBlacklist(String pattern) {
		pollBlacklist(pattern, System.out, "");
		System.out.println();
	}
	
	public void pollBlacklist(String pattern, PrintStream out, String prefix) {
		
		if(pattern != null) {
			pattern = pattern.toLowerCase();
			while(pattern.endsWith("*")) pattern = pattern.substring(0, pattern.length()-1);
		}
		
		out.println();
		
		if(pattern == null) out.println(prefix + " Blacklisted configuration keys are: ");
		else out.println(prefix + " Blacklisted keys starting with '" + pattern + "': ");
		
		out.println(prefix + " --------------------------------------------------------------------");
		
		List<String> list = getBlacklist();
		Collections.sort(list);
		
		for(String key : list) {
			if(pattern != null) if(!key.startsWith(pattern)) continue;
			out.println("   " + key);
		}
		
		out.println(prefix + " --------------------------------------------------------------------");
	}
	
	public void pollConditions(String pattern) {
		pollConditions(pattern, System.out, "");
		System.out.println();
	}
	
	public void pollConditions(String pattern, PrintStream out, String prefix) {
		
		if(pattern != null) {
			pattern = pattern.toLowerCase();
			while(pattern.endsWith("*")) pattern = pattern.substring(0, pattern.length()-1);
		}
		
		out.println();
		
		if(pattern == null) out.println(prefix + " Active conditions are: ");
		else out.println(prefix + " Active conditions starting with '" + pattern + "': ");

		// Add all the conditionals...
		Hashtable<String, Vector<String>> conditions = getConditions(true);
		ArrayList<String> conditionKeys = new ArrayList<String>(conditions.keySet());	
		Collections.sort(conditionKeys);
		
		out.println(prefix + " --------------------------------------------------------------------");
		
		for(String key : conditionKeys) {
			if(pattern != null) if(!key.startsWith(pattern)) continue;
			
			StringBuilder values = new StringBuilder();
			for(String value : conditions.get(key)) {
				if(values.length() > 0) values.append(';');
				values.append(value);
			}
			out.println("   " + key + " " + new String(values));
		}	
		
		out.println(prefix + " --------------------------------------------------------------------");
	}
	
	public void readConfig(String fileName) throws IOException {
		File configFile = new File(fileName);
		if(configFile.exists()) {
			System.err.println("Reading configuration from " + fileName);
			
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(configFile)));
			String line = null;
			while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') parse(line);
			in.close();
		}
		else throw new FileNotFoundException(fileName);
	}

	public void editHeader(Cursor cursor) throws FitsException, HeaderCardException {
		// Add all active configuration keys...
		for(String key : getAlphabeticalKeys()) {
			Configurator option = get(key);
			if(option.isEnabled) Util.addLongHierarchKey(cursor, key, option.value);
		}
		
		// Add all the conditionals...
		Hashtable<String, Vector<String>> conditions = getConditions(true);
		ArrayList<String> conditionKeys = new ArrayList<String>(conditions.keySet());	
		Collections.sort(conditionKeys);
		
		for(String condition : conditionKeys) {
			StringBuilder values = new StringBuilder();
			for(String value : conditions.get(condition)) {
				if(values.length() > 0) values.append(';');
				values.append(value);
			}
			Util.addLongHierarchKey(cursor, condition, new String(values));
		}
		
		// Write the blacklist....
		StringBuilder keys = new StringBuilder();
		for(String key : blacklist) {
			if(keys.length() > 0) keys.append(',');
			keys.append(key);
		}
		
		if(!blacklist.isEmpty()) Util.addLongHierarchKey(cursor, "blacklist", new String(keys));
	}	

	class Entry {
		String key;
		String value;
		
		public Entry() {}
		
		public Entry(String key, String value) {
			this();
			this.key = key;
			this.value = value;
		}
		
		public Entry (String line) {
			this();
			parse(line);
		}
		
		public void parse(String line) {
			final StringBuffer keyBuffer = new StringBuffer();
			
			int openCurved = 0;
			int openCurly = 0;
			int openSquare = 0;
			
			line = line.trim();
			
			int index = 0;
					
			boolean foundSeparator = false;
			
			for(; index < line.length(); index++) {
				final char c = line.charAt(index);
				switch(c) {
				case '(' : openCurved++; break;
				case ')' : openCurved--; break;
				case '{' : openCurly++; break;
				case '}' : openCurly--; break;
				case '[' : openSquare++; break;
				case ']' : openSquare--; break;
				default :
					if(c == ' ' || c == '\t' || c == ':' || c == '=') {
						if(openCurved <= 0 && openCurly <= 0 && openSquare <= 0) {
							foundSeparator = true;
							key = new String(keyBuffer).toLowerCase();
							break;
						}
					}	
				}
				if(foundSeparator) break;
				else keyBuffer.append(c);
			}
			
			// If it's just a key without an argument, then return an entry with an empty argument...
			if(index == line.length()) {
				key = new String(keyBuffer).toLowerCase();
				value = "";
				return;
			}
		
			// Otherwise, skip trailing spaces and assigners after the key... 
			for(; index < line.length(); index++) {
				char c = line.charAt(index);
				if(c != ' ') if(c != '\t') if(c != '=') if(c != ':') break;
			}
			
			// The remaining is the 'raw' argument...
			value = line.substring(index).trim();
		
			// Remove quotes from around the argument
			if(value.length() == 0);
			else if(value.charAt(0) == '"' && value.charAt(value.length()-1) == '"')
				value = value.substring(1, value.length() - 1);
			else if(value.charAt(0) == '\'' && value.charAt(value.length()-1) == '\'')
				value = value.substring(1, value.length() - 1);	
		}
		
	}

}


