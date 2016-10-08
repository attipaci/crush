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

package crush.sofia;

import jnum.Unit;
import jnum.Util;
import nom.tam.fits.Header;

public class SofiaHeader {
    private Header header;
    
    public SofiaHeader(Header h) {
        this.header = h;
    }
    
    public boolean containsKey(String value) {
        return header.containsKey(value);
    }
    
    public Header getFitsHeader() { return header; }
    
    public boolean getBoolean(String key) {
        return header.getBooleanValue(key);
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        return header.getBooleanValue(key, defaultValue);
    }
    
    public final int getInt(String key) {
        return getInt(key, UNKNOWN_INT_VALUE);
    }
    
    public int getInt(String key, int defaultValue) {
        int value = header.getIntValue(key, defaultValue);
        return value == UNKNOWN_INT_VALUE ? defaultValue : value;
    }
    
    public final long getLong(String key) {
        return getLong(key, UNKNOWN_INT_VALUE);
    }
    
    public long getLong(String key, long defaultValue) {
        long value = header.getLongValue(key, defaultValue);
        return value == UNKNOWN_INT_VALUE ? defaultValue : value;
    }
    
    public final float getFloat(String key) {
        return getFloat(key, Float.NaN);
    }
    
    public float getFloat(String key, float defaultValue) {
        float value = header.getFloatValue(key, defaultValue);
        return value == UNKNOWN_FLOAT_VALUE ? Float.NaN : value;
    }
    
    public final double getDouble(String key) {
        return getDouble(key, Double.NaN);
    }
    
    public double getDouble(String key, double defaultValue) {
        double value = header.getDoubleValue(key, defaultValue);
        return value == UNKNOWN_DOUBLE_VALUE ? Double.NaN : value;
    }
    
    public static double getHeaderValue(double value) {
        if(Double.isNaN(value)) return UNKNOWN_DOUBLE_VALUE;
        return value;
    }
    
    public static float getHeaderValue(float value) {
        if(Float.isNaN(value)) return UNKNOWN_FLOAT_VALUE;
        return (float) value;
    }
    
    public String getString(String key, String defaultValue) {
        if(!header.containsKey(key)) return defaultValue;
        String record = header.findCard(key).getValue();
        return record.equalsIgnoreCase(UNKNOWN_STRING_VALUE) ? defaultValue : record;
    }
    
    public String getString(String key) {
        return getString(key, UNKNOWN_STRING_VALUE);
    }
    
    public double getHMSTime(String key) {
        try { return Util.parseTime(getString(key, null)); }
        catch(Exception e) { return getDouble(key) * Unit.hour; }
    }
    
    public double getDMSAngle(String key) {
        try { return Util.parseAngle(getString(key, null)); }
        catch(Exception e) { return getDouble(key) * Unit.deg; }
    }
    
    public static boolean isValid(String headerValue) {
        if(headerValue == null) return false;
        return headerValue.length() > 0 && !headerValue.equalsIgnoreCase(UNKNOWN_STRING_VALUE);
    }
    
    public static boolean isValid(int headerValue) {
        return headerValue != UNKNOWN_INT_VALUE;
    }
    
    public static boolean isValid(float headerValue) {
        return headerValue != UNKNOWN_FLOAT_VALUE && !Float.isNaN(headerValue) && !Float.isInfinite(headerValue);
    }
    
    public static boolean isValid(double headerValue) {
        return headerValue != UNKNOWN_FLOAT_VALUE && !Double.isNaN(headerValue) && !Double.isInfinite(headerValue);
    }
    
    public final static int UNKNOWN_INT_VALUE = -9999;
    public final static float UNKNOWN_FLOAT_VALUE = -9999.0F;
    public final static double UNKNOWN_DOUBLE_VALUE = -9999.0;
    public final static String UNKNOWN_STRING_VALUE = "UNKNOWN";
    
}
