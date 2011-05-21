/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
package crush.fits;

import nom.tam.fits.*;
import nom.tam.util.*;


public abstract class HDUReader extends Thread implements Cloneable {
	protected HDUManager manager;

	protected TableHDU hdu;
	protected ColumnTable table;
	protected int from, to;

	public HDUReader(TableHDU hdu) throws FitsException {
		this.hdu = hdu;
		this.table = (ColumnTable) hdu.getData().getData();
	}
	
	public void setManager(HDUManager m) { 
		this.manager = m; 
	}
	
	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
		
	public void setRange(int from, int to) {	
		this.from = from;
		this.to = to;
	}
		
	@Override
	public void start() {
		manager.queue(this);
		super.start();
	}
	
	@Override
	public void run() {
		try { 
			read(); 
			manager.checkout(this);
		}
		catch(FitsException e) { manager.readException(e); }
		catch(Exception e) { 
			System.err.println("WARNING! " + e.getMessage());
			e.printStackTrace(); 
		}
	}
	
	public abstract void read() throws FitsException;
}
