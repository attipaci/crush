/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
package crush.tools;

import java.io.*;
import java.util.*;

public class ManEdit {
	
	public static void main(String[] args) {
		ArrayList<String> lines = new ArrayList<String>();
		
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[0])));
			String line = null;
			while((line = in.readLine()) != null) lines.add(line);
			edit(lines);			
		} 
		catch(IOException e) { e.printStackTrace(); }
	}
	
	public static void edit(ArrayList<String> lines) {
		int i=0;

		ArrayList<String> edited = new ArrayList<String>();
		
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
		edited.add("<meta name=\"description\" content=\"CRUSH Tool Manual.\">");
		edited.add("<meta name=\"google-site-verification\" content=\"aEd2DeoMQVvdlcp7KeNN6V3T-iqd1m1QMRbBGJ7EYrQ\" />");
		edited.add("<meta name=\"y_key\" content=\"57760f7ff87efc45\">");
		edited.add("<link rel=\"shortcut icon\" href=\"../../crush-tiny.ico\">");				
		edited.add("<link rel=\"stylesheet\" type=\"text/css\" href=\"../../crush.css\">");
		edited.add("<link rel=\"stylesheet\" type=\"text/css\" href=\"man.css\">");
		edited.add(lines.get(i++)); // </HEAD><BODY>		
		
		edited.add("");
		edited.add("<script type=\"text/javascript\">");
		edited.add("");
		edited.add("  var _gaq = _gaq || [];");
		edited.add("  _gaq.push(['_setAccount', 'UA-16668543-1']);");
		edited.add("  _gaq.push(['_trackPageview']);");
		edited.add("");
		edited.add("  (function() {");
		edited.add("    var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;");
		edited.add("    ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';");
		edited.add("    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);");
		edited.add("  })();");
		edited.add("");
		edited.add("</script>");
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
			line = line.replace("<I>README</I>", "<a href=\"../../v2/README\">README</a>");
			
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
