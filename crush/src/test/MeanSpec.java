package test;

import java.io.*;
import java.util.*;

import util.Util;

public class MeanSpec {
	double binf = 0.25;
	
	public static void main(String args[]){
		MeanSpec meanSpec = new MeanSpec();
		try { meanSpec.calc(args[0]); }
		catch(IOException e) { e.printStackTrace(); }		
	}
	
	public void calc(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		
		double tof = binf;
		int n = 0;
		double[] data = null;
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
						
			if(data == null) {
				data = new double[tokens.countTokens() - 1]; 
				System.err.println("Found " + data.length + " channels.");
			}
			
			double f = Double.parseDouble(tokens.nextToken());
			
			if(f > tof) {
				double sumw = 0.0;
				for(int i=0; i<data.length; i++) {
					data[i] /= n;
					sumw += 1.0 / data[i];
				}
				System.out.println(tof + "\t" + Util.e3.format(Math.sqrt(1.0/sumw)));
				
				Arrays.fill(data, 0.0);
				tof += binf;
				n = 0;
			}
			else {
				for(int i=0; i<data.length; i++) data[i] += Double.parseDouble(tokens.nextToken());
				n++;
			}
		}
		in.close();
	}
	
	
	
}
