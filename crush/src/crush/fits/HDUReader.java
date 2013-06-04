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
package crush.fits;

import kovacs.util.Parallel;
import nom.tam.fits.*;
import nom.tam.util.*;


public abstract class HDUReader {	
	protected TableHDU hdu;
	protected ColumnTable table;
	
	public HDUReader(TableHDU hdu) throws FitsException {
		this.hdu = hdu;
		this.table = (ColumnTable) hdu.getData().getData();
	}

	public abstract Reader getReader();
	
	public void read() throws Exception {
		//read(CRUSH.maxThreads);
		read(1);
	}
	
	
	public void read(int threadCount) throws Exception {
		getReader().process(threadCount);
	}

	
	public abstract class Task<ReturnType> extends Parallel<ReturnType> {
		@Override
		public void processIndex(int i, int threadCount) throws Exception {
			final int frames = hdu.getNRows();
			final int step = (int)Math.ceil((double) frames / threadCount);
			
			processRows(i*step, Math.min((i+1)*step, frames));
		}
		
		public void processRows(int from, int to) throws Exception {
			for(int i=from; i<to; i++) {
				if(isInterrupted()) return;
				processRow(i);
				Thread.yield();
			}
		}
		
		public abstract void processRow(int i) throws Exception;
	}
	
	public abstract class Reader extends Task<Void> {
		@Override
		public void processRow(int i) throws Exception {
			readRow(i);
		}
		public abstract void readRow(int i) throws FitsException;
	}
}
