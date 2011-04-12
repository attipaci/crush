/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package util.text;


public class QuoteTokenizer {
	int type = UNQUOTED;
	String line;
	int index = 0;


	public static void main(String[] args) {
		
		String line = "\"this is a 'quote'\" and 'this is another \"quote\"'";
		//String line = "";
		//String line = "what about 'this' and \"this unfinished one?";
		QuoteTokenizer quotes = new QuoteTokenizer(line);
		
		while(quotes.hasMoreElements()) {
			System.err.println(quotes.type + " : " + quotes.nextToken());
		}
		
		
	}
	
	
	public QuoteTokenizer(String line) {
		this.line = line;
		// If starting with a quote, then no need to return what precedes the quote...
		if(line.length() == 0) return;
			
		char first = line.charAt(0);
		
		// Initialize, in case starting with a quote right away.
		// S.t. isNextQuote methods return the correct values...
		if(first == '\'') {
			type = SINGLE_QUOTE;
			index++;
		}
		else if(first == '"') {
			type = DOUBLE_QUOTE;
			index++;
		}
		
	}
	
	public boolean isNextQuote() {
		return type != UNQUOTED;
	}
	
	public boolean isNextDoubleQuote() {
		return type == DOUBLE_QUOTE;
	}
	
	public boolean isNextSingleQuote() {
		return type == SINGLE_QUOTE;
	}
	
	public boolean hasMoreElements() {
		return index < line.length();
	}
	
	public String nextToken() {
		if(line.length() == 0) return line;

		int nextSingle = line.indexOf("'", index);
		int nextDouble = line.indexOf("\"", index);
		if(nextSingle < 0) nextSingle = line.length();
		if(nextDouble < 0) nextDouble = line.length();

		int nextQuote = Math.min(nextSingle, nextDouble);
		
		
		String value = null;
	
		
		// If a single quote, then return until the closing single quote.
		if(type == SINGLE_QUOTE) {
			value = line.substring(index, nextSingle);
			type = UNQUOTED;
			index = nextSingle + 1;
		}
		// If double quote, then return until the next
		else if(type == DOUBLE_QUOTE) {
			value = line.substring(index, nextDouble);
			type = UNQUOTED;
			index = nextDouble + 1;
		}
		// Otherwise we will start a new quote. Return whatever precedes it
		// if it's not zero length. Otherwise jump to the next quote..
		else {
			// Set the type of the next quote...
			type = nextSingle < nextDouble ? SINGLE_QUOTE : DOUBLE_QUOTE;
			// Do not return an empty unquoted string...
			if(nextQuote == index) return nextToken();
			value = line.substring(index, nextQuote);
			index = nextQuote + 1;
		}
		
		return value;
	}
	
	public final static int UNQUOTED = 0;
	public final static int SINGLE_QUOTE = 1;
	public final static int DOUBLE_QUOTE = 2;
	
}
