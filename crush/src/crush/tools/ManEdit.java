/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
package crush.tools;

import java.io.*;
import java.util.*;

public class ManEdit {
	String name;
	
	public static void main(String[] args) {
		ArrayList<String> lines = new ArrayList<>();
		ManEdit man = new ManEdit(args[0]);
		
		try(BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[0])))) {
			String line = null;
			while((line = in.readLine()) != null) lines.add(line);
			in.close();
			man.edit(lines);	
		} 
		catch(IOException e) { e.printStackTrace(); }
	}
	
	public ManEdit(String manName) {
		if(manName.contains(".")) this.name = manName.substring(0, manName.indexOf('.'));
		else this.name = manName;		
	}
	
	public void edit(ArrayList<String> lines) {
		int i=0;

		ArrayList<String> edited = new ArrayList<>();
		
		edited.add("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Frameset//EN\" \"http://www.w3.org/TR/html4/frameset.dtd\">");
		
		// Start at the <HTML> tag...
		for(; i<lines.size(); ) {
			String line = lines.get(i++);
			if(line.contains("<HTML>")) {
				int index = line.indexOf("<HTML>");
				edited.add(line.substring(index));
				break;
			}
		}
			
		// First edit the header...
		edited.add("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\">");
		edited.add("<meta name=\"author\" content=\"Attila Kov&aacute;cs\">");
		edited.add("<meta name=\"description\" content=\"Online Manual for " + name + " - part of the CRUSH Suite of Tools for Astronomical Data Reduction and Imaging.\">");
		edited.add("<meta name=\"google-site-verification\" content=\"aEd2DeoMQVvdlcp7KeNN6V3T-iqd1m1QMRbBGJ7EYrQ\" />");
		edited.add("<meta name=\"y_key\" content=\"57760f7ff87efc45\">");
		edited.add("<link rel=\"shortcut icon\" href=\"../../crush-tiny.ico\">");				
		edited.add("<link rel=\"stylesheet\" type=\"text/css\" href=\"../../crush.css\">");
		edited.add("<link rel=\"stylesheet\" type=\"text/css\" href=\"man.css\">");
		edited.add(lines.get(i++)); // </HEAD><BODY>		
		
		
		edited.add("");
		edited.add("<!-- Google Analytics -->");
		edited.add("<script>");
		edited.add("   (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){");
		edited.add("   (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),");
		edited.add("   m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)");
		edited.add("   })(window,document,'script','//www.google-analytics.com/analytics.js','ga');");
		edited.add("");
		edited.add("   ga('create', 'UA-16668543-1', 'caltech.edu');");
		edited.add("   ga('send', 'pageview');");
		edited.add("");
		edited.add("</script>");
		edited.add("<!-- End Google Analytics -->");
		edited.add("");
		
		// Remove between <HR> and <A NAME="lbAB">
		for(; i<lines.size(); ) {
			String line = lines.get(i++);
			if(!line.contains("<HR>")) edited.add(line);
			else {
				int index = line.indexOf("<HR>");
				edited.add(line.substring(0, index));
				edited.add("<p>");
				edited.add("&nbsp;");
				edited.add("&nbsp;");
				
				for(; i<lines.size(); ) {
					line = lines.get(i++);
					if(line.contains("<A NAME=\"lbAB\">")) {
						index = line.indexOf("<A NAME=\"lbAB\">");
						edited.add(line.substring(index));
						break;
					}
				}
				break;
			}
		}
			
		
		// Remove between <HR>'s
		for(; i<lines.size(); ) {
			String line = lines.get(i++);
			if(!line.contains("<HR>")) edited.add(line);
			else {
				int index = line.indexOf("<HR>");
				edited.add(line.substring(0, index));
				for(; i<lines.size(); ) {
					line = lines.get(i++);
					if(line.contains("<HR>")) {
						index = line.indexOf("<HR>") + "<HR>".length();
						for(int j=0; j<3; j++) edited.add("&nbsp;");
						edited.add("<HR>");
						edited.add(line.substring(index));
						break;
					}
				}
				break;
			}
		}

		
		edited.add("<small>");
		
		// Copy remaining...
		for(; i<lines.size(); i++) edited.add(lines.get(i));

		
		String lastLine = "";
		for(i=0; i<edited.size(); i++) {
			String line = edited.get(i);
				
			// Remove references to the index...
			line = line.replace("<A HREF=\"#index\">Index</A>", "");
			// Change java reference to regular boldface
			line = line.replace("<A HREF=\"../man1/java.1.html\">java</A>", "java");
			// Change 'created by' to 'created with the help of'
			line = line.replace("created by", "created with the help of");
			// Change java reference to regular boldface
			line = line.replace("<A HREF=\"http://localhost/cgi-bin/man/man2html\">man2html</A>", "<b>man2html</b>");			
			// forget about compact dl...
			line = line.replace("<DL COMPACT>", "<DL>");
			// Remove empty lists... (These are created when changing identation parameters...);
			line = line.replace("<DL><DT><DD>", "");
			// If a list entry has no title, then use only the list identation...
			line = line.replace("<DT><DD>", "");
			line = line.replace("<I>GLOSSARY</I>", "<a href=\"../../v2/GLOSSARY\">GLOSSARY</a>");
			line = line.replace("<I>README</I>", "<a href=\"../../md/README.html\">README</a>");
			line = line.replace("Attila Kovacs", "<a href=\"http://www.submm.caltech.edu/~attila\" rel=\"friend\" target=\"_blank\">Attila Kov&aacute;cs</a>");
			
			//line = line.replace(" --", " &mdash;");
			
			if(line.contains("</BODY>")) System.out.println("</small>");
			
			// Duplicate list ends are related to empty lists, which are removed
			// so remove the end marker too. Keep checking on this. It is possible that
			// a more sophisticated way to deal with empty lists is needed...
			if(lastLine.equals("</DL>")) if(line.equals("</DL>")) continue;
			
			// Do not float long title entries...
			if(line.contains("<DT>")) {
				int length = contentLength(line);
				if(length > 14) line = line.replace("<DT>", "<DT class=\"nofloat\">");
			}

			
			System.out.println(line);			
			lastLine = line;
		}
		
	}
	
	public static int contentLength(String line) {
		boolean open = false, expression = false;
		int length = 0;
		for(int i=0; i<line.length(); i++) {
			char c = line.charAt(i);
			if(open) {
				if(c == '>') open = false;
			}
			else if(c == '<') open = true;
			else if(c == '&') {
				expression = true;
				length++;
			}
			else if(expression) {
				if(c == ' ' || c == ';') expression = false;				
			}
			else length++;
		}
		return length;
	}
	
}
