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
package crush.devel;

import java.io.*;

public class ErrorMessage extends Message {
	Exception exception;
	
	public ErrorMessage(Object object, String message) {
		super(object, message);
		
		System.err.println("(!) ERROR " + object.getClass().getSimpleName() + "> " + message);
		
		try {
			PrintStream out = new PrintStream(new FileOutputStream("error.log", true));			
			out.println(object.getClass().getSimpleName() + "> " + message);
			out.flush();
			out.close();
		}
		catch(IOException elocal) {}		
	}
	
	public ErrorMessage(Object object, Exception e, boolean details) {
		super(object, e.getMessage());
		exception = e;
		
		System.err.println("(!) ERROR " + object.getClass().getSimpleName() + "> " + e.getClass().getSimpleName() + ": " + e.getMessage());
		if(details) e.printStackTrace();
		
		try {
			PrintStream out = new PrintStream(new FileOutputStream("error.log", true));			
			out.println(object.getClass().getSimpleName() + "> " + e.getClass().getSimpleName() + ": " + e.getMessage());
			if(details) e.printStackTrace(out);
			out.flush();
			out.close();
		}
		catch(IOException elocal) {}
	}
	
	public ErrorMessage(Object object, Exception e) {
		this(object, e, true);
	}
	
	
}
