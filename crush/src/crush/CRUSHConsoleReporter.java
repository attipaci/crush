/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush;

import jnum.data.LocalAverage;
import jnum.reporting.ConsoleReporter;

public class CRUSHConsoleReporter extends ConsoleReporter {

    public CRUSHConsoleReporter(String id) {
        super(id);
    }
    
    @Override
    public String getPrefix(Object owner) { 
        if(owner == null) return "";
        else if(owner instanceof CRUSH) return " ";
        else if(owner instanceof Scan) return " ";
        else if(owner instanceof Integration || owner instanceof LocalAverage) return "   ";
        else return " ";
    }

    @Override
    public void status(Object owner, String message) {
        if(CRUSH.debug) super.status(owner, message);
    }
    
}
