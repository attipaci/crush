package crush.gismo;

import java.io.*;
import java.text.*;
import java.util.*;

import util.*;
import util.astro.AstroTime;
import util.data.DataPoint;
import util.data.WeightedPoint;
import crush.CRUSH;

public class IRAMTauTable extends ArrayList<IRAMTauTable.Entry> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3850376076747456359L;

	public String fileName = "";

	public double timeWindow = 15.0 * Unit.min;
	
	private static Hashtable<String, IRAMTauTable> tables = new Hashtable<String, IRAMTauTable>();

	public static IRAMTauTable get(String fileName) throws IOException {
		IRAMTauTable table = tables.get(fileName);
		if(table == null) {
			table = new IRAMTauTable(fileName);
			tables.put(fileName, table);
		}
		return table;
	}
	
		
	private IRAMTauTable(String fileName) throws IOException {
		read(fileName);
	}
	
	private void read(String fileName) throws IOException {
		if(fileName.equals(this.fileName)) return;
		
		System.err.print(" [Loading skydip tau values.]");
		if(CRUSH.debug) System.err.print(" >> " + fileName + " >> ");
		
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		
		SimpleDateFormat df = new SimpleDateFormat("hh:mm:ss yyyy-MM-dd");
		df.setTimeZone(TimeZone.getTimeZone("CET"));
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			
			if(tokens.countTokens() > 3) {
				Entry skydip = new Entry();
				String dateSpec = null;
				try { 
					dateSpec = tokens.nextToken() + " " + tokens.nextToken();
					Date date = df.parse(dateSpec);
					skydip.MJD = AstroTime.getMJD(date.getTime());			
					skydip.tau = new DataPoint(Double.parseDouble(tokens.nextToken()), Double.parseDouble(tokens.nextToken()));				
	
					add(skydip);
				}
				catch(ParseException e) {
					System.err.println("WARNING! Cannot parse date " + dateSpec);
				}
			}
		}
		in.close();
		
		this.fileName = fileName;
		
		Collections.sort(this);
		
		System.err.println(" -- " + size() + " valid records found.");	
	}
	
	public int indexBefore(double MJD) {
		for(int i=size(); --i >= 0; ) if(get(i).MJD < MJD) return i;
		return -1;
	}
	
	public double getTau(double MJD) {
		int i0 = indexBefore(MJD);
		if(i0 < 0) throw new IllegalStateException("No skydip data available for the specified time.");
		
		WeightedPoint tau = new WeightedPoint();
		
		double dMJD = 3.0 * timeWindow / Unit.day;
		int n = 0;
		
		for(int i = i0; i >= 0; i--) {
			if(MJD - get(i).MJD > dMJD) break;

			Entry skydip = get(i);
			DataPoint value = skydip.getTau();
			value.scaleWeight(getRelativeWeight(MJD - get(i).MJD));
			
			tau.average(value);
			n++;
		}
	
		for(int i = i0+1; i<size(); i++) {
			if(get(i).MJD - MJD > dMJD) break;
	
			Entry skydip = get(i);
			DataPoint value = skydip.getTau();
			value.scaleWeight(getRelativeWeight(MJD - get(i).MJD));
			
			tau.average(value);
			n++;
		}
		
		System.err.println(" Tau(225GHz) = " + tau.toString(Util.f3) + " (from " + n + " measurements)");
		
		return tau.value();
		
	}
	
	public double getRelativeWeight(double dMJD) {
		double devX = dMJD * Unit.day / timeWindow;		
		return Math.exp(-0.5 * (devX * devX));
	}

	class Entry implements Comparable<Entry> {
		double MJD;
		DataPoint tau;
		
		public int compareTo(Entry arg0) {
			return Double.compare(MJD, arg0.MJD);
		}
		
		DataPoint getTau() { return tau; }
	}	
	
	
	
}


