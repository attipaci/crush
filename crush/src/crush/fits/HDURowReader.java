
/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

import java.io.IOException;

import kovacs.util.Parallel;
import nom.tam.fits.*;
import nom.tam.util.*;


public abstract class HDURowReader {	
	protected BinaryTableHDU hdu;
	protected BinaryTable table;
	protected ArrayDataInput in;
	
	public HDURowReader(BinaryTableHDU hdu, ArrayDataInput in) throws FitsException {
		this.hdu = hdu;
		this.table = (BinaryTable) hdu.getData();
		this.in = in;
	}

	public abstract Reader getReader() throws FitsException;
	
	public void read() throws Exception {
		// TODO not thread safe...
		//read(CRUSH.maxThreads);
		read(1);
	}
	
	
	public void read(int threadCount) throws Exception {
		if(!table.reset()) throw new FitsException("Cannot locate beginning of FITS binary table.");	// Go to the beginning
		nextRow = 0;		
		getReader().process(threadCount);
	}

	private int nextRow = 0;
	
	private synchronized int getNextRow(Object[] data) throws IOException {
		if(nextRow >= table.getNRows()) return -1;
		long bytes = in.readLArray(data);
		if(bytes == 0) return -1;
		return nextRow++;
	}
	
	public abstract class Reader extends Parallel<Void> {
		private Object[] row;
			
		public Reader() {
			setDefaults();
		}
		
		public void setDefaults() {}
		
		@Override
		public void init() {
			super.init();
			row = table.getModelRow();
		}
		
		protected final int next(Object[] row) throws IOException {
			return getNextRow(row);
		}
		
		@Override
		public void processIndex(int i, int threadCount) throws Exception {
			int index;
			while((index = next(row)) >= 0) {
				if(isInterrupted()) return;				
				processRow(index, row);
				Thread.yield();
			}
		}
		
		public abstract void processRow(int index, Object[] row) throws Exception;
		
	}
	
}
