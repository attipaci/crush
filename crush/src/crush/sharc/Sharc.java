/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.sharc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import kovacs.math.Vector2D;
import kovacs.util.Unit;
import crush.Mount;
import crush.Scan;
import crush.SourceModel;
import crush.cso.CSOArray;
import crush.sourcemodel.MultiBeamMap;

public class Sharc extends CSOArray<SharcPixel> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7197932690442343651L;
	
	double pointingCenter = 12.5;
	
	public Sharc() {
		super("sharc", pixels);
		resolution = 8.5 * Unit.arcsec;
		mount = Mount.CASSEGRAIN;
	}
	
	@Override
	public double getLoadTemperature() {
		// TODO Auto-generated method stub
		return 0.0;
	}

	@Override
	public Vector2D getPointingCenterOffset() {
		return SharcPixel.getPosition(pointingCenter);
	}

	@Override
	public int maxPixels() {
		return pixels;
	}

	@Override
	public void readWiring(String fileName) throws IOException {
	}

	@Override
	public SharcPixel getChannelInstance(int backendIndex) {
		return new SharcPixel(this, backendIndex);
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new SharcScan(this, null);
	}

	@Override
	public Scan<?, ?> readScan(String descriptor, boolean readFully) throws IOException {
		if(!hasOption("file")) throw new FileNotFoundException("Unspecified data file. Use the 'file' option.");
		
		SharcFile file = null;
		String spec = option("file").getPath();
		
		try { file = SharcFile.get(this, spec); }
		catch(IOException e) {
			if(!hasOption("datapath")) throw e;
			file = SharcFile.get(this, option("datapath").getPath() + File.separator + spec);
		}
		
		if(descriptor.equalsIgnoreCase("list")) {
			System.err.println();
			System.exit(0);
		}
		
		StringTokenizer tokens = new StringTokenizer(descriptor, "-");
		int from = Integer.parseInt(tokens.nextToken());
		int to = tokens.hasMoreTokens() ? Integer.parseInt(tokens.nextToken()) : from;
		if(to < from) throw new IllegalStateException("Invalid scan number range: " + descriptor);
		
		ArrayList<SharcScan> scans = new ArrayList<SharcScan>(to - from + 1);
		for(int i=from; i<=to; i++) {
			SharcScan scan = file.get(i);
			if(scan == null) break;	
			scans.add(scan);
		}
		
		if(scans.isEmpty()) throw new FileNotFoundException("No such scans in file: " + descriptor);
		
		// Merge them into one scan...
		SharcScan scan = scans.get(0);
		for(int i=1; i<scans.size(); i++) {
			SharcIntegration integration = scans.get(i).get(0);
			scan.add(integration);
			integration.scan = scan;
			integration.integrationNo = i;
		}
		
		scan.instrument.validate(scan);
		for(SharcIntegration integration : scan) integration.instrument = (Sharc) scan.instrument.copy();
		
		scan.validate();
		
		return scan;
	}
	

	@Override
	public SourceModel getSourceModelInstance() {
		if(hasOption("deconvolve")) return new MultiBeamMap(this, SharcPixel.spacing);		
		else return super.getSourceModelInstance();
	}  
	
	
	public final static int pixels = 24;
	public final static int fileID = 10;
}
