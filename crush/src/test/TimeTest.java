package test;

import util.astro.AstroTime;
import java.text.*;
import java.util.*;

public class TimeTest {

	public static void main(String[] args) {
		String date = "2000-01-01T11:58:55.816";
		DateFormat fitsFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		fitsFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		System.err.println(">>> " + AstroTime.millisJ2000);
		
		try {
			System.err.println("millis: " + fitsFormatter.parse(date).getTime());
		
			AstroTime time = AstroTime.forFitsTimeStamp(date);
			System.err.println("MJD: " + time.getMJD());
		}
		catch(Exception e) { e.printStackTrace(); }
		
		
	}
	
}
