/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope.sofia;


import jnum.text.TableFormatter;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;


public abstract class SofiaData implements Cloneable, TableFormatter.Entries {

    @Override
    public Object clone() {
        try { return super.clone(); }
        catch(CloneNotSupportedException e) { return null; }
    }

    public abstract void editHeader(Header header) throws HeaderCardException;

    public HeaderCard makeCard(String key, boolean value, String comment) throws HeaderCardException {
        return new HeaderCard(key, value, comment);
    }

    public HeaderCard makeCard(String key, int value, String comment) throws HeaderCardException {
        return new HeaderCard(key, value, comment);
    }

    public HeaderCard makeCard(String key, long value, String comment) throws HeaderCardException {
        return new HeaderCard(key, value, comment);
    }

    public HeaderCard makeCard(String key, float value, String comment) throws HeaderCardException {
        return new HeaderCard(key, Float.isNaN(value) ? SofiaHeader.UNKNOWN_FLOAT_VALUE : value, comment);
    }

    public HeaderCard makeCard(String key, double value, String comment) throws HeaderCardException {
        return new HeaderCard(key, Double.isNaN(value) ? SofiaHeader.UNKNOWN_DOUBLE_VALUE : value, comment);
    }

    public abstract String getLogID();

    public final String getLogPrefix() { return getLogID().toLowerCase() + "."; }

    @Override
    public Object getTableEntry(String name) {
        return null;
    }



}
