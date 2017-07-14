/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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
package crush.fits;

import crush.CRUSH;
import jnum.parallel.ParallelTask;
import nom.tam.fits.*;
import nom.tam.util.*;


public abstract class HDUReader {	
	protected TableHDU<?> hdu;
	protected ColumnTable<?> table;
	
	public HDUReader(TableHDU<?> hdu) throws FitsException {
		this.hdu = hdu;
		this.table = (ColumnTable<?>) hdu.getData().getData();
	}

	public abstract Reader getReader();
	
	public void read() throws Exception {
		read(CRUSH.maxThreads);
	}
	
	private void read(int threadCount) throws Exception {
		if(CRUSH.executor != null) getReader().process(threadCount, CRUSH.executor);
		else getReader().process(threadCount);
	}

	
	public abstract class Reader extends ParallelTask<Void> {
		@Override
		public void processChunk(int i, int threadCount) throws Exception {
			final int frames = hdu.getNRows();
			for(; i<frames; i+=threadCount) {
				processRow(i);
				Thread.yield();
			}
		}
			
		public abstract void processRow(int i) throws Exception;
		
	}
	
}
