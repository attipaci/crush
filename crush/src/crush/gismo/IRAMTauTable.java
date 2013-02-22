package crush.gismo;

import java.io.*;
import java.text.*;
import java.util.*;

import util.*;
import util.astro.AstroTime;
import util.data.DataPoint;
import util.data.LocalAverage;
import util.data.Locality;
import util.data.LocalizedData;
import crush.CRUSH;

public class IRAMTauTable extends LocalAverage<IRAMTauTable.Entry> {
	
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
					skydip.timeStamp = new TimeStamp(AstroTime.getMJD(date.getTime()));			
					skydip.tau.setValue(Double.parseDouble(tokens.nextToken()));
					skydip.tau.setRMS(Double.parseDouble(tokens.nextToken()));				
	
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
	
	public double getTau(double MJD) {
		Entry mean = getLocalAverage(new TimeStamp(MJD));
		System.err.println(" Local average tau(225GHz) = " + mean.tau.toString(Util.f3) + " (from " + mean.measurements + " measurements)");
		return mean.tau.value();
	}
	
	
	class TimeStamp extends Locality {
		double MJD;
		
		public TimeStamp(double MJD) { this.MJD = MJD; }
		
		public double distanceTo(Locality other) {
			return(Math.abs((((TimeStamp) other).MJD - MJD) * Unit.day / timeWindow));
		}

		public int compareTo(Locality o) {
			return Double.compare(MJD, ((TimeStamp) o).MJD);
		}
		
		@Override
		public String toString() { return Double.toString(MJD); }

		@Override
		public double sortingDistanceTo(Locality other) {
			return distanceTo(other);
		}
	}

	class Entry extends LocalizedData {
		TimeStamp timeStamp;
		DataPoint tau = new DataPoint();
		
		@Override
		public Locality getLocality() {
			return timeStamp;
		}

		@Override
		public void setLocality(Locality loc) {
			timeStamp = (TimeStamp) loc;
		}

		@Override
		protected void averageWidth(LocalizedData other, Object env, double relativeWeight) {
			Entry point = (Entry) other;	
			tau.average(point.tau.value(), relativeWeight * point.tau.weight());
		}
	}

	@Override
	public Entry getLocalizedDataInstance() {
		return new Entry();
	}

	
	
}


