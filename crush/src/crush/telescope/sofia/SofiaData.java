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

package crush.telescope.sofia;


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import crush.CRUSH;
import jnum.Util;
import jnum.math.Range;
import jnum.math.Range2D;
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
        return new HeaderCard(key, Float.isNaN(value) ? UNKNOWN_FLOAT_VALUE : value, comment);
    }

    public HeaderCard makeCard(String key, double value, String comment) throws HeaderCardException {
        return new HeaderCard(key, Double.isNaN(value) ? UNKNOWN_DOUBLE_VALUE : value, comment);
    }
    
    public HeaderCard makeCard(String key, String value, String comment) throws HeaderCardException {
        return new HeaderCard(key, value == null ? UNKNOWN_STRING_VALUE : value, comment);
    }

    public abstract String getLogID();

    public final String getLogPrefix() { return getLogID().toLowerCase() + "."; }

    @Override
    public Object getTableEntry(String name) {
        return null;
    }


    public void merge(SofiaData other, boolean isSameFlight) {
        if(other == null) return;   // TODO throw exception?
       
        for(Field f : getClass().getDeclaredFields()) {
            int mods = f.getModifiers();
            
            if(Modifier.isStatic(mods)) continue;
            if(Modifier.isPrivate(mods)) continue;
            if(Modifier.isTransient(mods)) continue;
            
            try { f.set(this, merge(f.get(this), f.get(other), isSameFlight)); } 
            catch (Exception e) { CRUSH.error(this, e); }
        }      
    }

    private Object merge(Object former, Object latter, boolean isSameFlight) {
        if(former == null) return null;
        if(latter == null) return null;
        
        if(!former.getClass().isAssignableFrom(latter.getClass())) throw new IllegalArgumentException("Cannot merge two different types: "
                + former.getClass().getSimpleName() + " / " + latter.getClass().getSimpleName());
                    
        if(former instanceof BracketedValues) {
            if(!isSameFlight) return null;
            return new BracketedValues(((BracketedValues) former).start, ((BracketedValues) latter).end); 
        }  
        else if(former instanceof Range) {
            Range merged = ((Range) former).copy();
            merged.include((Range) latter);
            return merged;
        }
        else if(former instanceof Range2D) {
            Range2D merged = ((Range2D) former).copy();
            merged.include((Range2D) latter);
            return merged;
        }
        else if(former instanceof Object[]) {
            Object[] A = (Object[]) former;
            Object[] B = (Object[]) latter;
            if(A.length != B.length) return null;
            for(int i=A.length; --i >= 0; ) A[i] = merge(A[i], B[i], isSameFlight);
        }
        else if(former instanceof boolean[]) return null;    // No default merges for some types...
        else if(former instanceof byte[]) return null;
        else if(former instanceof char[]) return null;
        else if(former instanceof short[]) {
            short[] A = (short[]) former;
            short[] B = (short[]) latter;
            short[] merged = new short[A.length];
            if(A.length != B.length) return null;
            for(int i=A.length; --i >= 0; ) merged[i] = A[i] == B[i] ? A[i] : (short) UNKNOWN_INT_VALUE;
            return merged;
        }
        else if(former instanceof int[]) {
            int[] A = (int[]) former;
            int[] B = (int[]) latter;
            int[] merged = new int[A.length];
            if(A.length != B.length) return null;
            for(int i=A.length; --i >= 0; ) merged[i] = A[i] == B[i] ? A[i] : UNKNOWN_INT_VALUE;
            return merged;
        }
        else if(former instanceof long[]) {
            long[] A = (long[]) former;
            long[] B = (long[]) latter;
            long[] merged = new long[A.length];
            if(A.length != B.length) return null;
            for(int i=A.length; --i >= 0; ) merged[i] = A[i] == B[i] ? A[i] : UNKNOWN_INT_VALUE;
            return merged;
        }
        else if(former instanceof float[]) {
            float[] A = (float[]) former;
            float[] B = (float[]) latter;
            float[] merged = new float[A.length];
            if(A.length != B.length) return null;
            for(int i=A.length; --i >= 0; ) merged[i] = A[i] == B[i] ? A[i] : Float.NaN;
            return merged;
        }
        else if(former instanceof double[]) {
            double[] A = (double[]) former;
            double[] B = (double[]) latter;
            double[] merged = new double[A.length];
            if(A.length != B.length) return null;
            for(int i=A.length; --i >= 0; ) merged[i] = A[i] == B[i] ? A[i] : Double.NaN;
            return merged;
        }
        else if(!Util.equals(former, latter)) {
            if(former instanceof Number) {
                if(former instanceof Double) return Double.NaN;
                if(former instanceof Float) return Float.NaN;
                if(former instanceof Long) return UNKNOWN_INT_VALUE;
                if(former instanceof Integer) return UNKNOWN_INT_VALUE;
                if(former instanceof Short) return (short) UNKNOWN_INT_VALUE;
                return former;
            }
            if(former instanceof Boolean) return ((Boolean) former) | ((Boolean) latter);
            return null;
        }
        
        return former;
    }
    
    public final static int UNKNOWN_INT_VALUE = -9999;
    public final static float UNKNOWN_FLOAT_VALUE = -9999.0F;
    public final static double UNKNOWN_DOUBLE_VALUE = -9999.0;
    public final static String UNKNOWN_STRING_VALUE = "UNKNOWN";
}
