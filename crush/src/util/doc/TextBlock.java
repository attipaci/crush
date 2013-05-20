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
package util.doc;

import java.util.*;

public class TextBlock {
	ArrayList<String> text = new ArrayList<String>();
	int identFrom, identTo;
	int type = -1;
	
	
	public TextBlock(ArrayList<String> text) {
		this.text = text;
		
		String firstLine = getFirstLine();
		
		identTo = identFrom = getIdent(firstLine);
		
		firstLine = firstLine.substring(identFrom);
		
		// Check if title...
		if(firstLine.startsWith("***")) {
			text.set(0, firstLine)
			
		}
		
	}
	
	String getFirstLine() { return text.get(0); }
	
	String getLastLine() { return text.get(text.size() - 1); }
	
	public int relativeTo(TextBlock other) {
		if(identFrom > other.identTo) return 1;
		else if(identTo > other.identFrom) return -1;
		else return 0;	
	}
	
	
	// -1 for empty line, identation otherwise
	public static int getIdent(String line) {
		int ident = 0;
		for(int i=0; i<line.length(); i++) {
			char c = line.charAt(i);
			if(c == ' ') ident++;
			else if(c == '\r') ident=0;
			else if(c == '\t') ident += 8 - ident % 8;
			else return (i < line.length() - 1) ? ident : -1;
		}
		return -1;
	}
	
	final static int PARAGRAPH = 0;
	final static int TITLE = 1;
	final static int SECTION = 2;
	final static int SUBSECTION = 3;
	final static int SUBSUBSECTION = 4;
	final static int QUOTE = 5;
	final static int LISTITEM = 6;
	final static int DEFINITION = 7;
	
}
