/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/

package crush.telescope.sofia;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import crush.CRUSH;
import jnum.text.TableFormatter;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.HeaderCardException;


public abstract class SofiaData implements Cloneable, TableFormatter.Entries {

    @Override
    public SofiaData clone() {
        try { return (SofiaData) super.clone(); }
        catch(CloneNotSupportedException e) { return null; }
    }
    
  
    
    public abstract void editHeader(Header header) throws HeaderCardException;


    public abstract String getLogID();

    public final String getLogPrefix() { return getLogID().toLowerCase() + "."; }

    @Override
    public Object getTableEntry(String name) {
        return null;
    }

    /*
    public static SofiaData extractFrom(Object from, Class<? extends SofiaData> type, Class<?> topClass) {  
        Class<?> cls = from.getClass();
        while(topClass.isAssignableFrom(cls)) {
            for(Field f : cls.getDeclaredFields()) if(!Modifier.isStatic(f.getModifiers())) if(type.isAssignableFrom(f.getType())) {
                try { return (SofiaData) f.get(from); } 
                catch (Exception e) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }
    */
    

    public static HeaderCard makeCard(String key, boolean value, String comment) throws HeaderCardException {
        return new HeaderCard(key, value, comment);
    }

    public static HeaderCard makeCard(String key, int value, String comment) throws HeaderCardException {
        return new HeaderCard(key, value, comment);
    }

    public static HeaderCard makeCard(String key, long value, String comment) throws HeaderCardException {
        return new HeaderCard(key, value, comment);
    }

    public static HeaderCard makeCard(String key, double value, String comment) throws HeaderCardException {
        return new HeaderCard(key, Double.isNaN(value) ? UNKNOWN_DOUBLE_VALUE : value, comment);
    }

    public static HeaderCard makeCard(String key, String value, String comment) throws HeaderCardException {
        return new HeaderCard(key, value == null ? UNKNOWN_STRING_VALUE : value, comment);
    }
    
    public void setStart(SofiaData first) {   
        if(!getClass().isAssignableFrom(first.getClass())) 
                throw new IllegalArgumentException("Class mismatch: " + getClass().getSimpleName() + " / " + first.getClass().getSimpleName());
       
        Class<?> cls = getClass();
        while(SofiaData.class.isAssignableFrom(cls)) {
            for(Field f : cls.getDeclaredFields()) {
                int mods = f.getModifiers();
                if(Modifier.isStatic(mods)) continue;
                if(Modifier.isPrivate(mods)) continue;
                if(BracketedValues.class.isAssignableFrom(f.getType())) {
                    try { 
                        f.set(this, new BracketedValues(
                            ((BracketedValues) f.get(first)).start, 
                            ((BracketedValues) f.get(this)).end)); 
                    }
                    catch (Exception e) { CRUSH.warning(this, e); }
                }
            }
            cls = cls.getSuperclass();
        } 
    }

    public void setEnd(SofiaData last) {
        if(!getClass().isAssignableFrom(last.getClass())) 
                throw new IllegalArgumentException("Class mismatch: " + getClass().getSimpleName() + " / " + last.getClass().getSimpleName());
       
        Class<?> cls = getClass();
        while(SofiaData.class.isAssignableFrom(cls)) {
            for(Field f : cls.getDeclaredFields()) {
                int mods = f.getModifiers();
                if(Modifier.isStatic(mods)) continue;
                if(Modifier.isPrivate(mods)) continue;
                if(BracketedValues.class.isAssignableFrom(f.getType())) {
                    try { 
                        f.set(this, new BracketedValues(
                            ((BracketedValues) f.get(this)).start, 
                            ((BracketedValues) f.get(last)).end)); 
                    }
                    catch (Exception e) { CRUSH.warning(this, e); }
                }
            }
            cls = cls.getSuperclass();
        } 
    }
    
    public static SofiaData getMerged(SofiaData first, SofiaData last) {
        SofiaData merged = first.clone();
        merged.setEnd(last);
        return merged;
    }
    
    public static final int UNKNOWN_INT_VALUE = -9999;
    public static final float UNKNOWN_FLOAT_VALUE = -9999.0F;
    public static final double UNKNOWN_DOUBLE_VALUE = -9999.0;
    public static final String UNKNOWN_STRING_VALUE = "UNKNOWN";
}
