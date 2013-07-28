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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;


import java.util.*;

import kovacs.text.TableFormatter;
import kovacs.util.*;

public abstract class SourceModel implements Cloneable, TableFormatter.Entries, CopiableContent<SourceModel> {
	private Instrument<?> instrument;
	private Configurator options; 

	public Vector<Scan<?,?>> scans;
	public int generation = 0;
	public boolean isReady = false;

	public String commandLine;
	public String id;	

	public SourceModel(Instrument<?> instrument) {
		setInstrument(instrument);
	}

	public void setInstrument(Instrument<?> instrument) {
		this.instrument = instrument;
	}

	public Instrument<?> getInstrument() { return instrument; }

	public void setOptions(Configurator options) {
		this.options = options;
	}

	public Configurator getOptions() {
		return options;
	}

	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}	

	public boolean hasOption(String name) {
		return options.isConfigured(name);
	}

	public Configurator option(String name) {
		return options.get(name);
	}

	public final SourceModel copy() { return copy(true); }
	
	public SourceModel copy(boolean copyContents) {
		SourceModel copy = (SourceModel) clone();
		return copy;
	}

	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		this.scans = new Vector<Scan<?,?>>(collection);
		for(Scan<?,?> scan : scans) scan.setSourceModel(this);		

		// TODO remove this if source.setInstrument works in Pipeline...
		double janskyPerBeam = scans.get(0).instrument.janskyPerBeam();
		for(Scan<?,?> scan : scans) {
			scan.setSourceModel(this);
			for(Integration<?,?> integration : scan)
				integration.gain *= integration.instrument.janskyPerBeam() / janskyPerBeam;
		}

	}

	public void reset(boolean clearContent) {
		isReady = false;
	}

	public abstract void add(SourceModel model, double weight);

	public abstract void add(Integration<?,?> integration);

	public abstract void process(Scan<?,?> scan);

	public void postprocess(Scan<?,?> scan) {}

	public abstract void sync(Integration<?,?> integration);

	public abstract void setBase();

	public abstract void write(String path) throws Exception;

	public abstract boolean isValid();


	public void suggestions() {
		int scansWithFewPixels = 0;
		for(Scan<?, ?> scan : scans) for(Integration<?, ?> integration : scan) if(!checkPixelCount(integration)) {
			scansWithFewPixels++;
			break;
		}
		if(scansWithFewPixels > 0) troubleshootFewPixels();
		else if(!isValid()) suggestMakeValid();
		else return; // no problems, so nothing left to do...
		
		System.err.println();
		System.err.println("          Please consult the README and/or the GLOSSARY for details.");
		System.err.println();
	}

	public void troubleshootFewPixels() {
		System.err.println(" WARNING! It seems that one or more scans contain too few valid pixels for");
		System.err.println("          contributing to the source model. This may be just fine, and probably");
		System.err.println("          indicates that something was sub-optimal with the affected scan(s).");
		System.err.println("          If you feel that CRUSH should try harder to use those scans also,");
		System.err.println("          you may try:");
		System.err.println();
		if(hasOption("deep")) System.err.println("            * Reduce with 'faint' instead of 'deep'.");
		else if(hasOption("faint")) System.err.println("            * Reduce with default settings instead of 'faint'.");
		else if(!hasOption("bright")) System.err.println("            * Reduce with 'bright'.");
	
		instrument.troubleshootFewPixels();
		
		if(hasOption("mappingpixels") || hasOption("mappingfraction")) {
			System.err.println("            * Adjust 'mappingpixels' or 'mappigfraction' to allow source ");
			System.err.println("              extraction with fewer pixels.");
		}
	}

	public void suggestMakeValid() {
		System.err.println("            * Check the console output for any problems when reading scans.");
	}
	
	public abstract void process(boolean verbose) throws Exception;


	public synchronized void sync() throws Exception {
		// Coupled with blanking...
		if(hasOption("source.nosync")) return;
		if(hasOption("source.coupling")) System.err.print("(coupling) ");

		System.err.print("(sync) ");

		final int nParms = countPoints();

		new IntegrationFork<Void>() {
			@Override
			public void process(Integration<?,?> integration) {
				sync(integration);
				integration.sourceGeneration++;
				integration.scan.sourcePoints = nParms;
			}	
		}.process();

		generation++;

		setBase();
	}

	public double getBlankingLevel() {
		return hasOption("blank") ? option("blank").getDouble() : Double.NaN;
	}

	public double getClippingLevel() {
		return hasOption("clip") ? option("clip").getDouble() : Double.NaN;
	}

	public double getPointSize() { return instrument.resolution; }

	public double getSourceSize() {
		double sourceSize = hasOption("sourcesize") ? 
				option("sourcesize").getDouble() * instrument.getSizeUnit() : instrument.resolution;
				if(sourceSize < instrument.resolution) sourceSize = instrument.resolution;
				return sourceSize;
	}


	public abstract String getSourceName();

	public abstract int countPoints();

	public abstract Unit getUnit();

	// Replace character sequences that are problematic in filenames with underscores "_"
	public String getCanonicalSourceName() {
		StringTokenizer tokens = new StringTokenizer(getSourceName(), " \t\r*?/\\\"");
		StringBuffer canonized = new StringBuffer();
		while(tokens.hasMoreTokens()) {
			canonized.append(tokens.nextToken());
			if(tokens.hasMoreTokens()) canonized.append('_');
		}
		return new String(canonized);
	}

	public String getDefaultCoreName() {
		Scan<?,?> first, last;
		first = last = scans.get(0);

		for(Scan<?,?> scan : scans) {
			if(scan.compareTo(first) < 0) first = scan;
			else if(scan.compareTo(last) > 0) last = scan;			
		}

		String name = getCanonicalSourceName() + "." + first.getID();
		if(scans.size() > 1) if(!first.getID().equals(last.getID())) name += "-" + last.getID();

		return name;
	}

	public ArrayList<Integration<?,?>> getIntegrations() {
		final ArrayList<Integration<?,?>> integrations = new ArrayList<Integration<?,?>>();
		for(Scan<?,?> scan : scans) for(Integration<?,?> integration : scan) integrations.add(integration);
		return integrations;		
	}

	public boolean checkPixelCount(Integration<?,?> integration) {
		Collection<? extends Pixel> pixels = integration.instrument.getMappingPixels();
		int nObs = integration.instrument.getObservingChannels().size();

		// If there aren't enough good pixels in the scan, do not generate a map...
		if(integration.hasOption("mappingpixels")) if(pixels.size() < integration.option("mappingpixels").getInt()) {
			integration.comments += "(!ch)";
			return false;
		}

		// If there aren't enough good pixels in the scan, do not generate a map...
		if(integration.hasOption("mappingfraction")) if(pixels.size() < integration.option("mappingfraction").getDouble() * nObs) {
			integration.comments += "(!ch%)";
			return false;
		}

		return true;
	}

	public abstract void noParallel();

	public abstract class ScanFork<ReturnType> extends Parallel<ReturnType> {	
		public ScanFork() {}

		public void process() throws Exception { process(CRUSH.maxThreads); }

		@Override
		public void processIndex(int index, int threadCount) throws Exception {
			for(int i=index; i<scans.size(); i += threadCount) {
				if(isInterrupted()) return;
				process(scans.get(i));
				Thread.yield();
			}
		}

		public abstract void process(Scan<?,?> scan) throws Exception;
	}

	public abstract class IntegrationFork<ReturnType> extends Parallel<ReturnType> {	
		public final ArrayList<Integration<?,?>> integrations = getIntegrations();

		public void process() throws Exception { process(CRUSH.maxThreads); }

		@Override
		public void processIndex(int index, int threadCount) throws Exception {	
			for(int i=index; i<integrations.size(); i += threadCount) {
				if(isInterrupted()) return;
				process(integrations.get(i));
				Thread.yield();
			}
		}

		public abstract void process(Integration<?,?> integration) throws Exception;
	}

	public String getASCIIHeader() {
		StringBuffer header = new StringBuffer(
			"# CRUSH version: " + CRUSH.getFullVersion() + "\n" +
			"# Instrument: " + instrument.getName() + "\n" +
			"# Object: " + getSourceName() + "\n");
		
		if(!scans.isEmpty()) {
			Scan<?,?> firstScan = scans.get(0);	
			header.append("# Equatorial: " + firstScan.equatorial + "\n");
		}
		
		header.append("# Scans: ");
		
		for(int i=0; i<scans.size(); i++) header.append(scans.get(i).getID() + " ");
		header.append("\n");
	
		return new String(header);
	}
	
	
	public String getFormattedEntry(String name, String formatSpec) {
		return null;
	}
}

