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

import java.util.*;

import crush.Integration;
import nom.tam.fits.*;

public class HDUManager {
	Integration<?,?> integration;
	private final Vector<HDUReader> queue = new Vector<HDUReader>();
	private FitsException fitsException = null;
	
	public HDUManager(Integration<?,?> integration) {
		this.integration = integration;
	}
	
	protected synchronized void queue(HDUReader reader) {
		queue.add(reader);
	}
	
	protected synchronized void checkout(HDUReader reader) {
		queue.remove(reader);
		notifyAll();
	}
	
	protected synchronized void readException(FitsException e) {
		fitsException = e;
		for(Thread thread : queue) thread.interrupt();
		notifyAll();
	}
	
	public synchronized Vector<HDUReader> read(HDUReader readerTemplate) throws FitsException {
		final int frames = readerTemplate.hdu.getNRows();
		
		// Do not split less than 100 rows of data into threads...
		//final int threads = Math.min(crush.CRUSH.useCPUs, (int)Math.ceil(frames/100.0)); 
		// If the getRow() method is not thread safe (which appears to be the case), then restrict 
		// reading to a single thread...
		final int threads = 1;
		
		Vector<HDUReader> readers = new Vector<HDUReader>(threads);
		
		// Make sure that the integration is filled with elements than can be
		// replaced during the reading...
		for(int i=integration.size(); i<frames; i++) integration.add(null);
			
		final int step = (int)Math.ceil((double) frames / threads);
		
		for(int from = 0; from < frames; from += step) {
			final HDUReader reader = (HDUReader) readerTemplate.clone(); 
			reader.setManager(this);
			reader.setRange(from, Math.min(from + step, frames));
			readers.add(reader);	
			reader.start();
		}
		
		while(!queue.isEmpty() && fitsException == null) {
			try { wait(); }
			catch(InterruptedException e) {}
		}
		
		if(fitsException != null) throw fitsException;	
			
		return readers;
	}
	
}
