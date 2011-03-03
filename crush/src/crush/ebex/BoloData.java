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
// Copyright (c) 2010 Attila Kovacs 

package crush.ebex;

import util.*;
import util.dirfile.*;

import java.io.IOException;
import java.util.*;

import crush.Frame;

public class BoloData extends DirFile {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7999674785447653288L;	

	ArrayList<EBEXBoard> boards = new ArrayList<EBEXBoard>();
	Hashtable<Integer, EBEXBoard> boardLookup = new Hashtable<Integer, EBEXBoard>();
	ArrayList<EBEXPixel> pixels = new ArrayList<EBEXPixel>();
	ArrayList<Integer> flagged;
	
	public double samplingRate;
	
	double startTS, endTS;
	
	public BoloData(String path) throws IOException {
		super(path);
		initBoards();
	}

	public void initBoards() {
		// TODO
		// fill boards and boardLookup...
		
	}
	
	public void getPixels() {
		
		// channels are named: bolo3900_b57_w0_c0.dat
		for(String name : keySet()) if(name.startsWith("bolo")) {
			StringTokenizer tokens = new StringTokenizer(name.substring(4), "_.");
			int backendIndex = Integer.parseInt(tokens.nextToken());
			if(!flagged.contains(backendIndex)) {
				EBEXPixel pixel = new EBEXPixel(backendIndex);
				pixel.board = Integer.parseInt(tokens.nextToken(), 1);
				pixel.wire = Integer.parseInt(tokens.nextToken(), 1);
				pixel.pin = Integer.parseInt(tokens.nextToken(), 1);
				pixel.dataIndex = EBEXPixel.getBackendIndex(pixel.board, pixel.wire, pixel.pin);
				boardLookup.get(pixel.board).add(pixel);
				pixels.add(pixel);
			}
		}
		
		Collections.sort(pixels);
		for(int i=0; i<pixels.size(); i++) pixels.get(i).index = i;
	}
	
	public void setEBEXTimeRange(double fromEBEXTime, double toEBEXTime) throws IOException {
		startTS = fromEBEXTime;
		endTS = toEBEXTime;
		for(EBEXBoard board : boards) board.setEBEXTimeRange(fromEBEXTime, toEBEXTime);
		double[] samplingRates = new double[boards.size()];
		for(int i=0; i<samplingRates.length; i++) samplingRates[i] = boards.get(i).samplingRate;
		samplingRate = ArrayUtil.median(samplingRates);
		System.err.println(" EBEX bolo sampling rate: " + Util.f3.format(samplingRate) + " Hz.");
		for(EBEXBoard board : boards) board.samplingRate = samplingRate;		
	}
	
	public EBEXFrame[] getData() {
		getPixels();
		for(EBEXBoard board : boards) Collections.sort(board);
		
		int frames = (int) Math.ceil((endTS - startTS) * samplingRate);
		EBEXFrame[] frame = new EBEXFrame[frames];
		
		for(int t=0; t<frame.length; t++) {
			EBEXFrame exposure = frame[t];
			exposure.data = new float[pixels.size()];
			exposure.sampleFlag = new byte[pixels.size()];
			exposure.ebexTime = startTS + t / samplingRate;
			Arrays.fill(exposure.sampleFlag, Frame.SAMPLE_SKIP);
		}
		
		for(EBEXBoard board : boards) {
			for(long i = board.startTSIndex; i<board.endTSIndex; i++) {
				boolean registered = false;
				try {
					int t = (int) Math.round((board.ebexTime.get(i) - startTS) * samplingRate);
					EBEXFrame exposure = frame[t];
					
					for(EBEXPixel pixel : board) {
						try { 
							if(!registered) {
								exposure.availableBoards++;
								registered = true;
							}
							exposure.data[pixel.index] = pixel.store.get(i);
							exposure.sampleFlag[pixel.index] &= ~Frame.SAMPLE_SKIP;
						}
						catch(IOException e) {}
					}
				}
				catch(IOException e) {}	
			}
		}
		
		
		for(int t=0; t<frame.length; t++) if(frame[t].availableBoards == 0) frame[t] = null;
		
		return frame;
	}
	

}
