/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila[AT]sigmyne.com>.
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
package crush.tools;
/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila[AT]sigmyne.com>.
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

// (C)2007 Attila Kovacs <attila[AT]sigmyne.com>

import java.util.*;

import crush.CRUSH;
import crush.astro.AstroMap;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.astro.SourceCatalog;
import jnum.data.Region;
import jnum.util.ConfidenceCalculator;


//noise-peaks
//N_beam * Q = N_max -- limiting the number of false detections
//Q = N_max / N_beam

public class DetectionTool {
	static String version = "0.1-1";
	
	AstroMap image;
	SourceCatalog<?> catalog;
	
	double significance = 4.0;
	
	boolean isOneSided = true;
	boolean gaussianStats = false;
	boolean iterateNoise = false;
	
	double extractionArea;
	boolean verbose;

	public static void main(String[] arg) {
		versionInfo();

		DetectionTool detectionTool = new DetectionTool();
		
		if(arg.length == 0) usage();

		try {
			ImageTool imageTool = new ImageTool(arg[arg.length-1]);
			detectionTool.setImage(imageTool.image);
		

			// Apply any other imagetool options...
			for(int i=1; i<arg.length-1; i++) {
				String option = arg[i];
				StringTokenizer tokens = new StringTokenizer(option, ":=");
				String key = tokens.nextToken();
				
				if(option.equalsIgnoreCase("-symmetric")) detectionTool.isOneSided = false;
				else if(option.equalsIgnoreCase("-gaussian")) detectionTool.gaussianStats = true;
				else if(option.equalsIgnoreCase("-dynamic")) detectionTool.iterateNoise = true;
				else if(key.equalsIgnoreCase("-area"))
					detectionTool.extractionArea = Double.parseDouble(tokens.nextToken()) * Unit.arcmin2; 
				
				else imageTool.option(option);
			}

			
			AstroMap image = detectionTool.image;
			
			//image.extFilterFWHM = Double.NaN;
			//image.clearRegions();
			
			extract(image, arg[0]);
			
			
			image.fileName = CRUSH.workPath + image.sourceName + ".residual.fits";
			
			image.write();
			
			image.writeSources();
		}
		catch(Exception e) { e.printStackTrace(); }
	}


	public void setImage(AstroMap map) {
		this.image = map;
		// Make sure map is convolved at least to beam
		image.smoothTo(map.instrument.resolution);
	}
	
	
	public boolean extract(AstroMap image, String optionString) {
		StringTokenizer tokens = new StringTokenizer(optionString, "=");

		String key = tokens.nextToken();

		if(key.equalsIgnoreCase("confidence")) {
			extract(DETECTION_CONFIDENCE, Double.parseDouble(tokens.nextToken()), isOneSided);
		}
		else if(key.equalsIgnoreCase("s2n")) {
			extract(SIGNAL_TO_NOISE, Double.parseDouble(tokens.nextToken()), isOneSided);
		}
		else if(key.equalsIgnoreCase("nofalse")) {
			extract(FALSE_DETECTION_CHANCE, 1.0 - Double.parseDouble(tokens.nextToken()), isOneSided);
		}
		else if(key.equalsIgnoreCase("false")) {
			extract(MAX_FALSE_DETECTIONS, Double.parseDouble(tokens.nextToken()), isOneSided);
		}
		else if(key.equalsIgnoreCase("-help")) usage();
		else return false;

		return true;    
	}

	//
	
	public void extract(final int criterion, final double value, boolean isOneSided) {	
		System.err.println();
	
		double significance = 3.0;	
		int priorSources = regions.size();
		boolean reporting = verbose && priorSources == 0;

		// Make room for sources...
		if(priorSources < 100) regions.ensureCapacity(100);
		
		// Remove possible prior corrections to fluxes, to analyze raw maps.
		// This is important because corrections are calculated individually for each source extracted...
		if(!Double.isNaN(image.fluxCorrectingFWHM) || image.fluxCorrectingFWHM <= 0.0) image.uncorrect();
		
		switch(criterion) {
		case SIGNAL_TO_NOISE: significance = value; break;
		case MAX_FALSE_DETECTIONS: significance = ConfidenceCalculator.getSigma(1.0 - value / image.countIndependentPoints()); break;
		case FALSE_DETECTION_CHANCE: {
			double Nfalse = -Math.log(1.0 - value);
			significance = ConfidenceCalculator.getSigma(1.0 - Nfalse / image.countIndependentPoints());
			break;
		}
		case SINGLE_BEAM_CONFIDENCE: significance = ConfidenceCalculator.getSigma(value); break;
		case DETECTION_CONFIDENCE: significance = 0.0; break;
		}
	
		if(reporting && significance > 0.0) System.out.println("# [extract] Searching for peaks at " + Util.f2.format(significance) + " sigma...");
		
		final double points = image.countIndependentPoints();
		final double instrumentBeams = image.countInstrumentBeams();
	
		if(reporting) System.out.println("# [extract] Search area is " + Util.e2.format(getArea()/Unit.deg2) + " sqdeg...");
		if(reporting) System.out.println("# [extract] Search area contains " + Util.f1.format(instrumentBeams) + " instrument beams...");
		
		// The chance for each independent point to be above the detection significance level...
		double Q = ConfidenceCalculator.getOutsideProbability(significance);
			
		// the expected number of false detections...
		double falses = Q * points;
		
		if(criterion == MAX_FALSE_DETECTIONS || criterion == FALSE_DETECTION_CHANCE) falses = value;
		
		if(reporting) if(criterion != DETECTION_CONFIDENCE){
			System.out.println("# [extract] Chance of at least one false detection is " + Util.f1.format(100.0 * (1.0-Math.exp(-falses))) + "%.");
			System.out.println("# [extract] Expected number of false detections is " + Util.f1.format(falses) + ".");
		}	
		
		if(reporting) {
			if(gaussianStats) System.out.println("# [extract] Assuming Gaussian noise statistics.");
			else System.out.println("# [extract] Adapting statistics for non-Gaussian noise.");
		}
			
		System.err.println();
		
		// Here's the source extraction loop...
		
		for(int k=1; ; k++) {
			System.err.println("[Extraction Round #" + k + "]");
			
			image.updateStats(isOneSided && priorSources > 0, iterateNoise || k == 1);
	
			// Remove anything that may have dropped below the search criterion...
			for(int i=0; i<regions.size(); ) {
				Region source = regions.from(i);
				final double S2N = source.peak / source.dpeak;
				
				if(S2N < significance) {
					source.unflag(FLAG_DETECTION);
					source.addPoint(true);
					regions.remove(i);
				}
				else i++;
			}

			System.err.println("Searching for sources...");

			for(Region source : findPeaksAbove(significance)) if(flag[indexOfdX(source.dX)][indexOfdY(source.dY)] == 0){			
				source.finetunePeak(); // Re-tune the peak. (may be necessary after the removal of a nearby source...)
				final double S2N = source.peak / source.dpeak;

				if(S2N > significance) {
					source.correct(beamFWHM);					
					source.subtractPoint(true);
					source.flag(FLAG_DETECTION);
					regions.add(source);
				}
			} 

			int detections = regions.size() - priorSources;
			
			if(detections != 0) {
				String lead = detections > 0 ? " +" : " ";
				System.err.println(lead + detections + " source candidate(s).");
			}
			
			if(detections <= 0) break;
			
			priorSources = regions.size();
		}
		
		/*
		 * OK, at this point the initial source list is ready. Now clean it up.
		 */
		
		if(regions.size() == 0) {
			System.err.println("No detections.");
			return;			
		}
		
		
		System.err.println("Searching for matching negative peaks...");
		// Count the corresponding negatives...
		Vector<Region> negatives = new Vector<Region>(100);
		for(Region neg : findPeaksBelow(-significance)) {
			if(flag[indexOfdX(neg.dX)][indexOfdY(neg.dY)] == 0) { 
				neg.flag(FLAG_NEG);
				negatives.add(neg);
			}		
		}
		
	
		System.err.println(" " + regions.size() + " source candidate(s) extracted.");
		System.err.println(" " + (negatives.size() > 0 ? negatives.size() + "" : "No") + " matching negative peaks.");
		
		// Sort sources in order of descending significance...
		System.err.println("Sorting detection w.r.t. detection significance.");
		Collections.sort(regions, new Comparator<Region>() {
			public int compare(Region o1, Region o2) {
				return Double.compare(o2.peak/o2.dpeak, o1.peak/o1.dpeak);
			}				
		});
		

		// Clear the flags around the negatives...
		for(Region neg : negatives) neg.unflag(FLAG_NEG);
		
		System.err.println("Revising list based on distribution of sources" + (gaussianStats ? "." : " and negatives."));
		
		double effectiveSources = 0.0;
		int kept = 0;
		double lastS2N = Double.NaN;
		
		for(int i=0; i<regions.size(); i++) {
			
			Region source = (Region) regions.get(i);
			
			final double S2N = source.peak / source.dpeak;
			lastS2N = S2N;
			
			Q = ConfidenceCalculator.getOutsideProbability(S2N);
			double expectedNegs = Q * points;
			
			int actualNegs = 0;
			for(Region neg : negatives) if(neg.peak/neg.dpeak < -S2N) actualNegs++;
			
			double Nfalse = expectedNegs;
			if(!gaussianStats) Nfalse += (actualNegs - expectedNegs) * Math.abs(Math.tanh((actualNegs - expectedNegs) / (2.0 * Math.sqrt(expectedNegs))));

			// The chance of more than n false detections above some significance is
			// 1 - sum_{i,0,n}(P(i)) where P is Poission
			if(Double.isNaN(Nfalse)) Nfalse = 0.0;
			
			// The chance it is a false detection given an all noise map, and the expected number of
			// false detections (or empirically corrected false detection estimate)...
			double Pfalse = 1.0 - Math.exp(-Nfalse);
			
			// scale by Nfalse / (Nfalse + Nreal) = Nfalse / (i+1) = 1 - Nsources / (i+1)
			if(effectiveSources > 0.0) Pfalse *= 1.0 - effectiveSources / (i+1);
			
			double Preal = 1.0 - Pfalse;
			
			effectiveSources += Preal;
			
			if(criterion == MAX_FALSE_DETECTIONS || criterion == FALSE_DETECTION_CHANCE) 
				if((i+1) - effectiveSources > value) break;
				
			boolean pass = S2N > significance;
			if(criterion == DETECTION_CONFIDENCE) if(Preal < value) pass = false;
				
			source.id = "[" + (i+1) + ":" + ((int) Math.round(100.0 * Preal)) + "%]  ";
			source.comment = Util.f2.format(S2N) + " sigma";
			//if(-mostNegative > minDeviation) region.id += "!";
			//if(S2N < -mostNegative) region.id += "?";
			if(pass) {
				source.flag(FLAG_DETECTION);
				
				if(i==0) {
					System.out.println("#");
					System.out.println("#[ID:conf.]     RA          DEC                    beam S          dS               S/N         falses");
					System.out.println("#-----------------------------------------------------------------------------------------------------");		
				}
				
				kept++;
				System.out.println(source.toString() + "\t" + Util.f3.format(Nfalse));
			}

			else source.addPoint(true);				
			
		}
		if(criterion != SIGNAL_TO_NOISE) System.err.println(" " + kept + " source(s) kept.");
		
		int actualNegs = 0;
		for(Region neg : negatives) if(neg.peak/neg.dpeak < -lastS2N) actualNegs++;
		System.err.println(" " + actualNegs + " matching negative peaks remaining.");
		
		detections = kept;
		falseDetections = actualNegs;
		
		
		if(verbose) {
			if(actualNegs > 0) System.out.println("# [extract] WARNING! " + actualNegs + " matching negative peaks.");
			else System.out.println("# [extract] No negatives below search significance.");
		}
			
		System.out.println();
		System.err.println();
		
		// Reapply flux corrections to the residuals...
		if(Double.isNaN(fluxCorrectingFWHM) || fluxCorrectingFWHM <= 0.0) correct(beamFWHM);
	}// Return them in descending order
	
	
	public Vector<Region> findPeaks(final int dir, double S2N) {
		// Estimate how much of the peak may be missed due to regridding...
		final double sigma = image.getImageFWHM() / Constant.sigmasInFWHM;
		final double halfpixeldev = delta / (2.0 * sigma);
		final double searchS2N = S2N * Math.exp(-halfpixeldev * halfpixeldev / 2.0); // maximum peak position error is a half-diagonal pixel.
		
		Vector<Region> points = new Vector<Region>(100);
		
		AstroImage s2n = image.getS2NImage();
		
		// Peak cannot be an edge pixel (not reliable)...
		final int toi = image.sizeX()-1, toj=image.sizeY()-1;
		for(int i=toi; --i >= 0; ) for(int j=toj; --j >= 1; ) if(image.flag[i][j] == 0) if(dir * Double.compare(s2n[i][j], searchS2N) > 0) {

			// check that it is a peak in the neighbourhood...			
			boolean isPeak = true;	
			for(int i1=i-1; i1<=i+1 && isPeak; i1++) for(int j1=j-1; j1<=j+1 && isPeak; j1++) 
				if(image.flag[i1][j1] == 0) if(dir * Double.compare(s2n.data[i1][j1], s2n.data[i][j]) > 0) isPeak = false;
		
			if(isPeak) {	
				Region point = new Region(dXofIndex(i), dYofIndex(j), beamFWHM, false);
				point.finetunePeak();
		
				if(dir * Double.compare(point.peak / point.dpeak, S2N) > 0) points.add(point);
			}
		}
				
		// Return peaks sorted from far to near...
		Collections.sort(points, new Comparator<Region>() {
			public int compare(Region a, Region b) {
				return dir * Double.compare(b.peak / b.dpeak, a.peak / a.dpeak);
			}
		});

		return points;
	}
	
	
	
	
	public static void versionInfo() {
		System.out.println("detect -- Source Detection Utility for CRUSH maps and more.");
		System.out.println("          Version: " + version);
		System.out.println("          part of crush " + CRUSH.getFullVersion());
		System.out.println("          http://www.submm.caltech.edu/~sharc/crush");
		System.out.println("          Copyright (C)2011 Attila Kovacs <attila[AT]sigmyne.com>");
		System.out.println();
	}

	public static void usage() {
		System.out.println("Usage: detect criterion [-options] <filename>");
		System.out.println();
		System.out.println("Criteria:");
		System.out.println();
		System.out.println("    confidence=  Minimum confidence level for each detection.");
		System.out.println("    s2n=         S/N level for detections.");
		System.out.println("    nofalse=     The confidence level that there are no false detections.");
		System.out.println("    false=       The expected number of false detections.");
		System.out.println("    help         Provides this help screen.");
		System.out.println();
		System.out.println("Options:");
		System.out.println();
		System.out.println("  All options of 'imagetool' are available. Additionally, 'detect' defines the");
		System.out.println("  following options:");
		System.out.println();
		System.out.println("    -area=       Specify the extraction area (in sq-armin) if it is different");
		System.out.println("                 from the area of the supplied map.");
		System.out.println("    -dynamic     Re-estimate the noise with every extraction round.");
		System.out.println("    -gaussian    Use strictly Gaussian statistics for calculating cumulative");
		System.out.println("                 false detections and confidence levels.");
		System.out.println("    -symmetric   Always use symmetric noise estimates (default is one-sided).");
		System.out.println();
		
		//ImageTool.optionsSummary();
		
		System.exit(0);
	}
	

	final static int SIGNAL_TO_NOISE = 0;
	final static int MAX_FALSE_DETECTIONS = 1;
	final static int FALSE_DETECTION_CHANCE = 2;
	final static int SINGLE_BEAM_CONFIDENCE = 3;
	final static int DETECTION_CONFIDENCE = 4;

	
}
