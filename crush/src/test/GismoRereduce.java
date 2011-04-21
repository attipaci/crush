package test;

import java.io.*;
import java.util.*;

public class GismoRereduce {
	String crush = "./crush";
	String options = "-point -log -log.file=../images/rereduce.log -write.png";
	
	public static void main(String[] args) {
		GismoRereduce reducer = new GismoRereduce();
		try { reducer.rereduce(args[0]); }
		catch(IOException e) {}
	}
	
	public void rereduce(String logFileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(logFileName)));
		
		System.out.println("CRUSH=\"" + crush + "\"");
		System.out.println("OPTIONS=\"" + options + "\"");
		
		String line = null;
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			if(tokens.countTokens() > 1) {	
				String id = tokens.nextToken();
				String sourceName = tokens.nextToken();
			
				StringTokenizer idbits = new StringTokenizer(id, ".");
				String date = idbits.nextToken();
				String scanNo = idbits.nextToken();
				
				String cmdLine = "$CRUSH gismo $OPTIONS -object=" + sourceName + " -date=" + date + " " + scanNo;
				Runtime runtime = Runtime.getRuntime();
				
				System.out.println(cmdLine);
				//runtime.exec(cmdLine);
			}
		}
	}
	
	
	
}
