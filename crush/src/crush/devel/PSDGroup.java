package crush.devel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;

import crush.ChannelGroup;
import crush.Integration;
import jnum.data.WeightedPoint;
import jnum.data.samples.Samples1D;

public class PSDGroup {
    private Hashtable<String, WeightedPoint[]> table;
    private double df, samplingInterval;
    private int nf;
    
    
    public PSDGroup() {
        
        
    }
    
    public boolean accumulate(PSDSet psd) {
        
    }
    
    public boolean accumulate(Integration<?> integration, ChannelGroup<?> channels) {
        
    }
    
    public void clear() {
        table.clear();
    }
    
    public int getChannelCount() { return table.size(); }
    
    public boolean contains(String channelID) {
        return table.containsKey(channelID);
    }
    
    public float[] getSpectrum(String channelID) {
        WeightedPoint[] spectrum = table.get(channelID);
        if(spectrum == null) return null;
        float[] data = new float[spectrum.length];
        extract(spectrum, data);
        return data;
    }
    
    public void getSpectrum(String channelID, float[] data) throws IllegalArgumentException {
        WeightedPoint[] spectrum = table.get(channelID);
        if(spectrum == null) throw new IllegalArgumentException("No spectrum for channel: " + channelID);
        extract(spectrum, data);
    }
    
    private void extract(WeightedPoint[] spectrum, float[] data) throws IllegalArgumentException {
        int to = Math.min(spectrum.length, data.length);
        
        for(int i=to; --i >= 0; ) data[i] = (float) spectrum[i].value();
        if(to < data.length) Arrays.fill(data, to, data.length, Float.NaN);        
    }
    
    public double getFrequencyResolution() { 
        return df;
    }
    
    public ArrayList<String> getChannelIDs() {
        return new ArrayList<String>(table.keySet());
    }

    
}
