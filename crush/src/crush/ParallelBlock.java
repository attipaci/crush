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
package crush;

import util.Parallel;
import java.util.*;

public abstract class ParallelBlock extends Parallel<Integer> {
	protected int blockSize = 1;
	protected ArrayList<Frame> frames;
	
	public ParallelBlock(int maxThreads) {
		super(maxThreads);
	}

	public synchronized void setBlockSize(int n) {
		blockSize = n;
	}

	public synchronized void process(ArrayList<Frame> frames) throws InterruptedException {
		this.frames = frames;
		
		final int nt = frames.size();
		final int nChunks = (int) Math.ceil((double) nt / blockSize);
		ArrayList<Integer> startIndexes = new ArrayList<Integer>(nChunks);
		
		for(int t=0; t<nt; t+=blockSize) startIndexes.add(t);
		
		process(startIndexes);
	}

	@Override
	public void process(Integer from, ProcessingThread thread) {
		process(from, Math.min(frames.size(), from+blockSize));		
	}
	 
	public abstract void process(int from, int to);
	
}
