/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of kovacs.util.
 * 
 *     kovacs.util is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     kovacs.util is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with kovacs.util.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
package kovacs.util;

import java.util.Hashtable;

import kovacs.util.text.TableFormatter;


public class DataTable extends Hashtable<String, Datum> implements TableFormatter.Entries {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2131139489959923852L;

	public void add(Datum datum) {
		put(datum.getName(), datum);		
	}
	
	public String getFormattedEntry(String name, String formatSpec) {
		if(!containsKey(name)) return TableFormatter.NO_SUCH_DATA;
		return Util.defaultFormat(get(name).getValue(), TableFormatter.getNumberFormat(formatSpec));
	}

}