package crush.devel;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import jnum.ExtraMath;
import jnum.Util;
import jnum.data.FauxComplexArray;
import jnum.data.Statistics;
import jnum.fft.FloatFFT;
import jnum.math.Complex;
import jnum.math.Vector2D;

public class Lissajous {
    double fx, fy;
    Complex Ax, Ay;
    
    // TODO this is to try get around low frequency Lissajous data, by bootstrapping positions from
    // what's available...
    public void boostrap(String fileName) throws IOException {
        System.err.println("   Bootstrapping Lissajous positions...");
        
        BufferedReader in = Util.getReader(fileName);
        String line = null;
       
        ArrayList<Vector2D> pos = new ArrayList<Vector2D>();
        ArrayList<Double> timeStamps = new ArrayList<Double>();
        
        while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
            StringTokenizer tokens = new StringTokenizer(line, ", \t");
            timeStamps.add(Double.parseDouble(tokens.nextToken()));
            pos.add(new Vector2D(Double.parseDouble(tokens.nextToken()), Double.parseDouble(tokens.nextToken())));
        }
        in.close();
       
        int n = timeStamps.size();    
       
        float[] x = new float[ExtraMath.pow2ceil(n)];
        float[] y = new float[x.length];
        float[] ts = new float[x.length];
        float[] index = new float[x.length];
        
        double[] coeffs = Statistics.lineFit(index, ts);
        double dt = coeffs[1];
        double t0 = coeffs[0];
        
        System.err.println("   --> Data rate: " + Util.f1.format(1.0 / dt) + " Hz.");
        
        double sumx2 = 0.0;
        
        for(int i=0; i<n; i++) {
            double delta = (timeStamps.get(i) - i * dt - t0);
            sumx2 += delta * delta;
            Vector2D v = pos.get(i);
            x[i] = (float) v.x();
            y[i] = (float) v.y();
        }
        double rms = Math.sqrt(sumx2 / (n-1));
        
        System.err.println("   --> RMS timestamp jitter is " + Util.f3.format(rms));
        
        FloatFFT fft = new FloatFFT();
        fft.complexForward(x);
        fft.complexForward(y);
        
        double df = 1.0 / (n * dt);
        
        double cx = findPeakChannel(x);
        double cy = findPeakChannel(y);
        
        Ax = getAmplitude(x, cx);
        Ay = getAmplitude(y, cy);
        
        fx = cx * df;
        fy = cy * df;
        
        System.err.println("   --> fx = " + Util.f3.format(fx) + " Hz, Ax = " + Util.S3.format(Ax));
        System.err.println("   --> fy = " + Util.f3.format(fy) + " Hz, Ay = " + Util.S3.format(Ay));
    }
    
    
    private double findPeakChannel(float[] cSpectrum) {
        FauxComplexArray.Float spectrum = new FauxComplexArray.Float(cSpectrum);
        double max = 0.0;
        int maxIndex = 0;
        
        Complex z = new Complex();
        for(int i=spectrum.size(); --i > 0; ) {
            spectrum.get(i, z);
            double l = z.length();
            if(l > max) {
                max = l;
                maxIndex = i;
            }
        }
        
        // TODO quadratic peak refinement...
        
        return maxIndex;
    }
    
   
    private Complex getAmplitude(float[] cSpectrum, double f) {
        FauxComplexArray.Float spectrum = new FauxComplexArray.Float(cSpectrum);
        
        Complex z = new Complex();
        // TODO quadratic peak spec
        spectrum.get((int) Math.round(f), z);
        
        z.scale(1.0 / cSpectrum.length);
        return z;
    }
    
}
