package crush.tools;


import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.math.Complex;
import jnum.math.Vector2D;

import java.io.*;
import java.util.*;

import crush.CRUSH;
import crush.astro.AstroMap;
import crush.devel.Histogram;
import jnum.data.fitting.AmoebaMinimizer;
import jnum.fft.DoubleFFT;
import nom.tam.fits.*;

public class SourceCounts {
    private AstroMap map, jackknife;
    private double noiseScaling = 1.0;

    String type = "power";
    double maxFlux;
    int bins = 50;
    double resolution = 0.25;
    int oversampling = 1;	
    double background = Double.NaN;

    double dBG = 0.0;
    boolean fixedNoise = false;
    boolean bounded = true;
    int mapPixels;
    double mapArea, mapRMS;
    int minTestSources = 100;

    //RandomGenerator random = new RandomGenerator2();
    Random random = new Random();

    public SourceCounts(AstroMap map) {
        this.map = map;
        //map.autoCrop();

        map.undoFilterCorrect();

        mapPixels = map.countPoints();
        mapArea = mapPixels * map.getPixelArea();

        System.err.println("Map area is " + Util.e3.format(mapArea/Unit.deg2) + " deg2.");


        map.reweight(true);
        noiseScaling = 1.0;

        double[][] rms = map.getRMS();
        double sumw = 0.0;
        int n= 0;
        
        for(int i=0; i<map.sizeX(); i++) for(int j=0; j<map.sizeY(); j++) if(map.isUnflagged(i, j)) {
            sumw += 1.0 / (rms[i][j] * rms[i][j]);
            n++;
        }
        
        
        mapRMS = Math.sqrt(n/sumw);
        maxFlux = map.getS2NImage().getMax() * mapRMS;
    }

    public static void main(String args[]) {

        try {
            AstroMap map = new AstroMap();
            map.read(args[args.length-1]);
            
            SourceCounts sc = new SourceCounts(map);

            for(int i=0; i<args.length-1; i++) sc.option(args[i]);

            sc.fit();
        }
        catch(Exception e) { e.printStackTrace(); }

    }

    public boolean option(String line) {
        if(line.charAt(0) != '-') return false;

        StringTokenizer tokens = new StringTokenizer(line.substring(1), "=:");
        String key = tokens.nextToken();
        String value = tokens.hasMoreTokens() ? tokens.nextToken() : "";

        if(key.equalsIgnoreCase("type")) {
            type = value;
        }
        else if(key.equalsIgnoreCase("max")) {
            maxFlux = Double.parseDouble(value) * Unit.Jy;
        }
        else if(key.equalsIgnoreCase("bins")) {
            bins = Integer.parseInt(value);
        }
        else if(key.equalsIgnoreCase("resolution")) {
            resolution = Double.parseDouble(value);
        }
        else if(key.equalsIgnoreCase("jackknife")) {
            try {
                jackknife = new AstroMap();
                jackknife.read(value);
                jackknife.setWeight(map.getWeight());
                jackknife.setWeightScale(map.getWeightScale());
                jackknife.setFlag(map.getFlag());
                noiseScaling = 1.0 / Math.sqrt(jackknife.getChi2(true));
                System.err.println("Jackknife noise: " + Util.f3.format(noiseScaling) + "x ");
                fixedNoise = true;
            }
            catch(Exception e) { e.printStackTrace(); }
        }
        else if(key.equalsIgnoreCase("noiseScale")) {
            System.err.println("Fixed noise scaling: " + Util.f3.format(noiseScaling) + "x ");
            noiseScaling = Double.parseDouble(value);
            fixedNoise = true;
        }
        else if(key.equalsIgnoreCase("background")) {
            StringTokenizer values = new StringTokenizer(value, "+-:;,()");
            background = Double.parseDouble(values.nextToken()) * Unit.Jy;
            dBG = values.hasMoreTokens() ? Double.parseDouble(values.nextToken()) * Unit.Jy : 0.1 * background;
            System.err.println("Background: " + Util.e3.format(background/Unit.Jy) + " +- " + Util.e3.format(dBG/Unit.Jy) + " Jy/deg2");
        }
        else if(key.equalsIgnoreCase("nobounds")) {
            bounded = false;
        }	
        else if(key.equalsIgnoreCase("oversample")) {
            oversampling = Integer.parseInt(value);
        }	
        else if(key.equalsIgnoreCase("sim")) {
            System.err.println("simulating!!!");
            try { simulate(value); }
            catch(Exception e) { e.printStackTrace(); }
            System.exit(0);
        }

        return true;
    }


    public void simulate(String fileName) throws IOException, FitsException, HeaderCardException {
        // Load the the counts data into fluxes and counts...
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
        String record = null;
        Vector<Vector2D> entries = new Vector<Vector2D>();

        while((record = in.readLine()) != null) if(record.length() > 0) if(record.charAt(0) != '#') {
            StringTokenizer columns = new StringTokenizer(record);
            Vector2D entry = new Vector2D();
            entry.setX(Double.parseDouble(columns.nextToken()) * Unit.Jy);
            entry.setY(Double.parseDouble(columns.nextToken()));
            entries.add(entry);
        }
        
        in.close();

        double[] fluxes = new double[entries.size()];
        double[] counts = new double[entries.size()];
        for(int i=0; i<entries.size(); i++) {
            Vector2D entry = entries.get(i);
            fluxes[i] = entry.x();
            counts[i] = entry.y();				
        }

        // Simulate the data
        AstroMap sim = getSimulated(fluxes, counts);

        // Write out the simulated
        sim.fileName = "sim.fits";
        sim.write();
    }

    public void fit() {

        RegularFit model;
        if(type.equalsIgnoreCase("power")) model = new PowerLawFit();
        else if(type.equalsIgnoreCase("broken")) model = new BrokenPowerLawFit();
        else if(type.equalsIgnoreCase("barger")) model = new BargerFit();
        else if(type.equalsIgnoreCase("exponential")) model = new ExponentialFit();
        else if(type.equalsIgnoreCase("schechter")) model = new SchechterFit();
        else if(type.equalsIgnoreCase("individual")) model = new IndividualFit();
        else throw new IllegalArgumentException("Uknown function type: " + type);

        System.err.println(" Function : " + model.getClass().getSimpleName());
        System.err.println(" Flux Bins: " + bins);
        System.err.println(" Max Flux : " + maxFlux / Unit.Jy + " Jy");
        System.err.println(" S/N bin  : " + resolution);
        System.err.println(" Oversamp.: " + oversampling);

        model.init(getMapHistogram(resolution), maxFlux, bins);
        model.verbose = false;

        model.fitAll();
        if(fixedNoise) model.fixedNoise();
        //if(!Double.isNaN(map.extFilterFWHM)) model.noOffset();

        model.minimize(3);

        //model.writeCounts(System.out);
        /*
		try { model.writeTemplates(); }
		catch(IOException e) {}
         */

        try { model.writeSourceFluxDistribution(); }
        catch(IOException e) { e.printStackTrace(); }

        System.err.println("Map Chi = " + Util.f5.format(model.parameter[model.getNoiseScaleIndex()]));
        if(Double.isNaN(map.getExtFilterFWHM())) {
            System.err.println("S2N Offset = " + Util.e3.format(model.parameter[model.getOffsetIndex()]));
            System.err.println("Map Level = " + Util.e3.format(model.backGround / map.getUnit().value()) + " " + map.getUnit().name());
        }
        System.err.println("Background = " + Util.f3.format(model.getBackground(model.parameter) / Unit.Jy) + " Jy/deg2.");
        System.err.println("Model Chi2 = " + Util.f3.format(Math.sqrt(model.getReducedChi2())));

    }




    public CharSpec[][] makeTemplates(double[] fluxes, int N, double s2nResolution, int bins, double fluxResolution) {
        fluxResolution *= map.getUnit().value();

        CharSpec[][] templates = new CharSpec[2][fluxes.length];

        double[][] s2nCounts = new double[fluxes.length][bins];
        double[][] fluxCounts = new double[fluxes.length][bins];


        AstroMap model = (AstroMap) map.copy(false);

        // If large-scale-structure filtering is used, then
        // assume average rms & weight in flagged areas.
        // and remove flags for the simulation...
        /*
		double averms = map.getTypicalRMS();
		double avew = 1.0 / (map.weightFactor * averms * averms * map.pointsPerBeam);
		if(!Double.isNaN(map.extFilterFWHM)) for(int i=0; i<map.sizeX; i++) for(int j=0; j<model.sizeY; j++) if(map.flag[i][j] != 0) {
			model.weight[i][j] = avew;
			model.rms[i][j] = averms;
			model.flag[i][j] = 0;
		}
         */
        model.setVerbose(false);

        double imageFWHM = map.getUnderlyingBeam().getCircularEquivalentFWHM();
        double fullArea = (map.sizeX() * map.getResolution().x() + imageFWHM) * (map.sizeY() * map.getResolution().y() + imageFWHM);

        // To avoid excessive overlapping (unless counts warrant it) put one source for every 10 beams...
        final int n = (int)Math.ceil(0.3 * fullArea / model.getImageBeamArea());
        System.err.println("Creating models with " + N + "+ simulated sources in steps of " + n + " sources/map. ");	

        N = Math.max(n, N);
        //System.err.println("Creating models with >=" + (int)(N * fullArea/mapArea) + " simulated sources in steps of " + n + " sources/map. ");		



        int sources = 0;
        int negs = 0;
        int points = 0;

        while(sources < N) {	
            // Start with a clear map...
            model.clear();		
            model.undoFilterCorrect();
            
            model.clearRegions();
            for(int i=0; i<n; i++) createRandomSource(model, 1.0);			

            sources += n;

            // Insert unfiltered sources. Filter afterwards...
            model.addPointSources();

            // Filter if necessary...
            if(!Double.isNaN(map.getExtFilterFWHM())) {
                model.level(true);
                model.filterAbove(map.getExtFilterFWHM());
                // The filtering rescales the weight map. So reinstate the original weights.
                model.setWeight(map.getWeight());
                model.setWeightScale(map.getWeightScale());
            }

            model.level(true);
            double[][] s2n = model.getS2N();

            for(int i=0; i<model.sizeX(); i++) for(int j=0; j<model.sizeY(); j++) if(map.isUnflagged(i,j)) {

                for(int k=0; k<s2nCounts.length; k++, points++) {

                    // Bin the signal-to-noise values...
                    int bin = (int) Math.round(fluxes[k] * s2n[i][j] / s2nResolution);
                    if(bin > bins/2 || bin <= -bins/2) System.err.println("WARNING! S/N Data outside of binning range."); 
                    else {
                        if(bin < 0) bin += bins;
                        s2nCounts[k][bin]++;
                    }	

                    // Now bin the flux values...
                    bin = (int) Math.round(fluxes[k] * model.getValue(i, j) / fluxResolution);
                    if(bin > bins/2 || bin <= -bins/2) System.err.println("WARNING! Flux Data outside of binning range."); 
                    else {
                        if(bin < 0) {
                            bin += bins;
                            negs++;
                        }
                        fluxCounts[k][bin]++;
                    }			

                }
            }
        }

        System.err.println(" Sanity check: " + Util.f3.format(100.0 * negs / points) + "% negative fluxes.");

        //System.err.println(CharSpec.toString(fluxCounts[fluxCounts.length-1]));

        double areaFactor = mapArea / fullArea;

        for(int k=0; k<s2nCounts.length; k++) {
            CharSpec s2nSpec = new CharSpec(s2nCounts[k]);
            s2nSpec.flux = fluxes[k];
            s2nSpec.testSources = areaFactor * n;			
            templates[0][k] = s2nSpec;

            CharSpec fluxSpec = new CharSpec(fluxCounts[k]);		
            fluxSpec.flux = fluxes[k];
            fluxSpec.testSources = areaFactor * n;			
            templates[1][k] = fluxSpec;
        }

        return templates;
    }

    public Histogram getMapHistogram(double resolution) {
        System.err.println("Calculating map histogram.");

        Histogram histogram = new Histogram(resolution);

        double[][] s2n = map.getS2N();
        for(int i=0; i<map.sizeX(); i++) for(int j=0; j<map.sizeY(); j++) if(map.isUnflagged(i, j)) {
            histogram.add(s2n[i][j]);
        }

        return histogram;
    }

    /*
	public Histogram getNoiseHistogram(double resolution, double offset) {	
		Histogram histogram = new Histogram(resolution);

		int N = (int)Math.ceil(8.0 / resolution);

		double lower = -(N+0.5) * resolution - offset;
		double below = ConfidenceCalculator.getOutsideProbability(-lower);

		for(int bin=-N; bin<=N; bin++) {
			double upper = lower + resolution;
			double inclusive = upper > 0.0 ? 
					1.0 - ConfidenceCalculator.getOutsideProbability(upper) 
					: ConfidenceCalculator.getOutsideProbability(-upper);

			double diff = inclusive - below;
			histogram.bins.put(bin, new Counter(diff));	

			lower = upper;
			below = inclusive;
		}

		return histogram;
	}
     */

    public void gaussianNoise(double noiseScale, double offset, double[] noisehist, double resolution) {
        int N = Math.min(noisehist.length >> 1, (int)Math.ceil(8.0 / (noiseScale * resolution)));
        Arrays.fill(noisehist, 0.0);
        double dev = offset / noiseScale;
        noisehist[0] = Math.exp(-0.5*dev*dev);
        for(int i=1; i<=N; i++) {
            dev = (i * resolution + offset) / noiseScale;
            noisehist[i] = Math.exp(-0.5 * dev * dev);
            dev -= 2.0 * offset / noiseScale;
            noisehist[noisehist.length - i] = Math.exp(-0.5 * dev * dev);
        }

        //System.err.println(CharSpec.toString(noisehist));
    }


    public void createRandomSource(AstroMap image, double flux) {
        double fwhm = image.getImageBeam().getCircularEquivalentFWHM() / Math.sqrt(image.getPixelArea());
        double i0 = (image.sizeX() + fwhm) * random.nextDouble() - fwhm/2.0;
        double j0 = (image.sizeY() + fwhm) * random.nextDouble() - fwhm/2.0;

        image.addRegion(image.dXofIndex(i0), image.dYofIndex(j0), image.getImageFWHM(), flux/Unit.jansky * image.janskyPerBeam.evaluate());
    }

    public AstroMap getSimulated(double[] fluxes, double[] counts) {
        AstroMap sim = (AstroMap) map.copy(false);

        sim.clear();

        System.err.println("Creating simulated map. ");

        boolean wasVerbose = sim.isVerbose();
        sim.setVerbose(false);

        // Add unfiltered point sources...
        sim.undoFilterCorrect();

        int N = 0;

        double imageFWHM = map.getImageBeam().getCircularEquivalentFWHM();
        
        double fullArea = (map.sizeX() * map.getResolution().x() + imageFWHM) * (map.sizeY() * map.getResolution().y() + imageFWHM);

         
        // Insert the desired number of sources...
        for(int i=0; i<fluxes.length; i++) {
            System.err.print("#");
            System.err.flush();

            double peak = fluxes[i];
            double binCounts = counts[i] * fullArea / mapArea;		

            for(int added=0; added < binCounts; added++, N++) {
                double x = (map.sizeX() + imageFWHM) * random.nextDouble() - 0.5 * imageFWHM;
                double y = (map.sizeY() + imageFWHM) * random.nextDouble() - 0.5 * imageFWHM;
                boolean addSource =  (added+1) < binCounts ? true : random.nextDouble() < binCounts - added;
                if(addSource) sim.addRegion(sim.dXofIndex(x), sim.dYofIndex(y), sim.getImageFWHM(), peak);
            }
            sim.addPointSources();
            sim.clearRegions();
        }

        System.err.println();
        System.err.println(N + " sources inserted.");
        sim.setVerbose(wasVerbose);

        // Write out a pure source map (unfiltered...);
        //sim.fileName = "sources.fits";
        //try { sim.write(); }
        //catch(Exception e) { e.printStackTrace(); }

        // If not using a jackknife then create a smoothed noise map...
        if(jackknife == null) {
            System.err.println("Adding simulated noise (" + noiseScaling + "x)...");
            AstroMap noise = (AstroMap) map.copy(false);
            noise.reset(true);
           
            for(int i=0; i<sim.sizeX(); i++) for(int j=0; j<sim.sizeY(); j++) if(noise.isUnflagged(i, j))
                noise.setValue(i, j, noiseScaling * random.nextGaussian() / Math.sqrt(noise.getWeight(i, j)));

            noise.smoothTo(map.getUnderlyingBeam().getCircularEquivalentFWHM());

            for(int i=0; i<sim.sizeX(); i++) for(int j=0; j<sim.sizeY(); j++) if(noise.isUnflagged(i,j)) 
                sim.signal[i][j] += noise.signal[i][j];

        }

        // Filter like in map & jackknife...
        if(!Double.isNaN(map.getExtFilterFWHM())) {
            sim.level(true);
            sim.filterAbove(map.getExtFilterFWHM());

            // The filtering rescales the weight map. So reinstate the original weights.
            sim.setWeight(map.getWeight());
            sim.setWeightScale(map.getWeightScale());
        }

        // Adding jackknife noise...
        if(jackknife != null) {
            AstroMap noise = (AstroMap) sim.copy();
            jackknife.regridTo(noise);
            System.err.println("Adding jackknifed noise to map...");
            for(int i=0; i<sim.sizeX(); i++) for(int j=0; j<sim.sizeY(); j++) if(sim.isUnflagged(i,j))
                sim.signal[i][j] += noise.signal[i][j];
        }

        sim.level(true);

        return sim;		
    }


    class BargerFit extends RegularFit {

        @Override
        public void init(Histogram mapHistogram, double maxFlux, int bins) {
            super.init(mapHistogram, maxFlux, bins);

            double S0 = 2.0 * dF;
            double N0 = guessN(S0);

            double[] initparms = { N0, 2*dF, 3.0, noiseScaling, 0.0 };
            startSize = new double[] { 0.1*N0, 0.1*dF, 0.1, 0.1, 0.01 };
            init(initparms);
        }

        @Override
        public double getCountsFor(double[] tryparm, int i) {
            double N1 = tryparm[0];
            double S0 = tryparm[1];
            double p = tryparm[2];

            double F = templates[i].flux;

            return N1 / (1.0 + Math.pow(F/S0, p));
        }

        @Override
        public double getCountsErrFor(double[] tryparm, int i) { return getStandardError(0, 0.1*parameter[0]) * getCountsFor(tryparm, i); }

        @Override
        public double evaluate(double[] tryparm) {
            double S0 = tryparm[1];
            double chi2 = super.evaluate(tryparm);
            if(S0 < dF) return (1.0 + Math.pow(S0/dF - 1.0, 2.0)) * chi2;
            return chi2;
        }


        @Override
        public double minimize(int n) {
            double chi2 = super.minimize(n);

            double N = parameter[0]/dF * Unit.mJy * Unit.deg2 / mapArea;
            double dN = N/parameter[0] * getStandardError(0, 0.1*parameter[0]);
            double S0 = parameter[1] / Unit.Jy;
            double dS0 = getStandardError(1, 0.1*parameter[1]) / Unit.Jy;
            double p = parameter[2];
            double dp = getStandardError(2, 0.1);


            System.err.println("N0 = " + Util.e3.format(N) + " +- " + Util.e1.format(dN) + " #/mJy/deg2");
            System.err.println("S0 = " + Util.e3.format(S0) + " +- " + Util.e1.format(dS0) + " Jy");
            System.err.println("p = " + Util.f3.format(p) + " +- " + Util.f3.format(dp));

            return chi2;
        }

        @Override
        public int getNoiseScaleIndex() { return 3; }

        @Override
        public int getOffsetIndex() { return 4; }

    }


    class BrokenPowerLawFit extends RegularFit {

        @Override
        public void init(Histogram mapHistogram, double maxFlux, int bins) {
            super.init(mapHistogram, maxFlux, bins);

            double S1 = 5.0 * Unit.mJy;
            double N1 = guessN(S1);

            double[] initparms = { N1, 2*dF, S1, -3.0, -5.0, noiseScaling, 0.0 };
            startSize = new double[] { 0.1*N1, 0.3*dF, dF, 0.03, 0.03, 0.1, 0.1 };
            init(initparms);
        }

        @Override
        public double getCountsFor(double[] tryparm, int i) {
            double N1 = tryparm[0];
            double S0 = bounded ? tryparm[1] : 0.0;
            double S1 = tryparm[2];
            double p1 = tryparm[3];
            double p2 = tryparm[4];

            double F = templates[i].flux;

            double bottom = (i+1) * dF;
            double top = bottom + dF;

            if(top < S1) return getFillFraction(i, S0, p1) * N1 * Math.pow(F/S1, p1);
            else if(bottom > S1) return getFillFraction(i, S0, p2) * N1 * Math.pow(F/S1, p2);
            else {
                double N2 = N1 * Math.pow(F/S1, p2);
                double dN = N1 * (Math.pow(bottom, p1+1.0) - Math.pow(bottom, p2+1.0) + Math.pow(top, p2+1.0) - Math.pow(top, p1+1.0)) / (Math.pow(bottom, p2+1.0) - Math.pow(top, p2+1.0));
                return getFillFraction(i, S0, 0.5*(p1+p2)) * (N2 - dN);
            }
        }

        @Override
        public double getCountsErrFor(double[] tryparm, int i) { return getStandardError(0, 0.1*parameter[0]) * getCountsFor(tryparm, i); }

        @Override
        public double evaluate(double[] tryparm) {
            double S0 = bounded ? tryparm[1] : 0.0;
            double p1 = tryparm[3];

            double chi2 = super.evaluate(tryparm);
            if(S0 < dF) return (1.0 + Math.pow((S0/dF-1.0), 2.0)) * chi2;
            if(p1 > 0.0) chi2 *= (1.0 + p1*p1);
            return chi2;
        }

        @Override
        public double getBackground(double[] tryparm) {
            if(bounded) {
                double N0 = tryparm[0];
                double S0 = bounded ? tryparm[1] : 0.0;
                double p = tryparm[3];

                double delta = S0 < dF ? Math.pow(dF, 2.0 + p) - Math.pow(S0, 2.0 + p) : 0.0;
                delta *= N0 * Math.pow(5.0 * Unit.mJy, -p) / (2.0 - p);
                return delta + super.getBackground(tryparm);
            }
            return super.getBackground(tryparm);
        }

        @Override
        public double minimize(int n) {
            double chi2 = super.minimize(n);

            double N = parameter[0]/dF * Unit.mJy * Unit.deg2 / mapArea;
            double dN = N/parameter[0] * getStandardError(0, 0.1*parameter[0]);
            double S0 = parameter[1] / Unit.Jy;
            double dS0 = getStandardError(1, 0.01*parameter[1]) / Unit.Jy;
            double S1 = parameter[2] / Unit.Jy;
            double dS1 = getStandardError(2, 0.01*parameter[2]) / Unit.Jy;
            double p1 = parameter[3];
            double dp1 = getStandardError(3, 0.001);
            double p2 = parameter[4];
            double dp2 = getStandardError(4, 0.001);


            System.err.println("N(S1) = " + Util.e3.format(N) + " +- " + Util.e1.format(dN) + " #/mJy/deg2");
            if(bounded) System.err.println("S0 = " + Util.e3.format(S0) + " +- " + Util.e1.format(dS0) + " Jy");
            System.err.println("S1 = " + Util.e3.format(S1) + " +- " + Util.e1.format(dS1) + " Jy");
            System.err.println("a1 = " + Util.f3.format(p1) + " +- " + Util.f3.format(dp1));
            System.err.println("a2 = " + Util.f3.format(p2) + " +- " + Util.f3.format(dp2));

            return chi2;
        }

        @Override
        public int getNoiseScaleIndex() { return 5; }

        @Override
        public int getOffsetIndex() { return 6; }

    }



    class PowerLawFit extends RegularFit {

        @Override
        public void init(Histogram mapHistogram, double maxFlux, int bins) {
            super.init(mapHistogram, maxFlux, bins);

            double N0 = guessN(5.0 * Unit.mJy);

            double[] initparms = { N0, 2*dF, 3.0, noiseScaling, 0.0 };
            startSize = new double[] { 0.1*N0, 0.3*dF, 0.3, 0.1, 0.1 };

            init(initparms);
        }

        @Override
        public double getCountsFor(double[] tryparm, int i) {
            double N0 = tryparm[0];
            double S0 = bounded ? tryparm[1] : 0.0;
            double p = tryparm[2];
            double F = templates[i].flux;

            return getFillFraction(i, S0, -p) * N0 * Math.pow(F / (5.0*Unit.mJy), -p);
        }

        @Override
        public double evaluate(double[] tryparm) {
            double S0 = tryparm[1];
            double chi2 = super.evaluate(tryparm);
            if(S0 < dF) return (1.0 + Math.pow((S0/dF-1.0), 2.0)) * chi2;
            return chi2;
        }

        @Override
        public double getBackground(double[] tryparm) {
            if(bounded) {
                double N0 = tryparm[0];
                double S0 = tryparm[1];
                double p = -tryparm[2];

                double delta = S0 < dF ? Math.pow(dF, 2.0 + p) - Math.pow(S0, 2.0 + p) : 0.0;
                delta *= N0 * Math.pow(5.0 * Unit.mJy, -p) / (2.0 - p);
                return delta + super.getBackground(tryparm);
            }
            return super.getBackground(tryparm);	
        }


        @Override
        public double getCountsErrFor(double[] tryparm, int i) { return getStandardError(0, 0.01 * parameter[0]) * getCountsFor(tryparm, i); }

        @Override
        public double minimize(int n) {
            double chi2 = super.minimize(n);

            double N = parameter[0] / (mapArea/Unit.deg2) / (dF/Unit.mJy);
            double dN = N * getStandardError(0, 0.01*parameter[0]) / parameter[0];
            double S0 = parameter[1] / Unit.Jy;
            double dS0 = getStandardError(1, 0.01 * parameter[1]) / Unit.Jy;
            double alpha = parameter[2];
            double dalpha = getStandardError(2, 0.001);

            System.err.println("dN(5mJy) = " + Util.e3.format(N) + " +- " + Util.e1.format(dN) + " #/mJy/deg2");
            if(bounded) System.err.println("S0 = " + Util.e3.format(S0) + " +- " + Util.e1.format(dS0) + " Jy");
            System.err.println("alpha = " + Util.f3.format(alpha) + " +- " + Util.f3.format(dalpha));

            return chi2;
        }

        @Override
        public int getNoiseScaleIndex() { return 3; }


        @Override
        public int getOffsetIndex() { return 4; }

    }


    class ExponentialFit extends RegularFit {

        @Override
        public void init(Histogram mapHistogram, double maxFlux, int bins) {
            super.init(mapHistogram, maxFlux, bins);	

            double S1 = 0.2*maxFlux;
            double N0 = guessN(S1);

            double[] initparms = { N0, S1, noiseScaling, 0.0 };
            startSize = new double[] { 0.1*N0, dF, 0.1, 0.1 };
            init(initparms);
        }


        @Override
        public double getCountsFor(double[] tryparm, int i) {
            double N0 = tryparm[0];
            double S1 = tryparm[1];
            double F = templates[i].flux;

            return N0 * Math.exp(-F/S1);
        }

        @Override
        public double getCountsErrFor(double[] tryparm, int i) { return getStandardError(0, 0.1*parameter[0]) * getCountsFor(tryparm, i); }

        @Override
        public double evaluate(double[] tryparm) {
            double S0 = tryparm[1];
            double chi2 = super.evaluate(tryparm);
            if(S0 < 0.0) return (1.0 + Math.pow(S0 / dF, 2.0)) * chi2;
            return chi2;
        }

        @Override
        public double minimize(int n) {
            double chi2 = super.minimize(n);

            double N = parameter[0]/dF * Unit.mJy * Unit.deg2 / mapArea;
            double dN = N/parameter[0] * getStandardError(0, 0.01*parameter[0]);
            double S1 = parameter[1] / Unit.Jy;
            double dS1 = getStandardError(1, 0.01 * parameter[1]) / Unit.Jy;

            System.err.println("dN(S1) = " + Util.e3.format(N) + " +- " + Util.e1.format(dN) + " #/mJy/deg2");
            System.err.println("S1 = " + Util.e3.format(S1) + " +- " + Util.e1.format(dS1) + " Jy");

            return chi2;
        }


        @Override
        public int getNoiseScaleIndex() { return 2; }

        @Override
        public int getOffsetIndex() { return 3; }


    }

    class SchechterFit extends RegularFit {

        @Override
        public void init(Histogram mapHistogram, double maxFlux, int bins) {
            super.init(mapHistogram, maxFlux, bins);	

            double S1 = 0.2*maxFlux;
            double N0 = guessN(S1);

            double[] initparms = { N0, 2.0 * dF, S1, -3.0, noiseScaling, 0.0 };
            startSize = new double[] { 0.1*N0, 0.1*dF, 0.3*dF, 0.1, 0.1, 0.1 };
            init(initparms);
        }


        @Override
        public double getCountsFor(double[] tryparm, int i) {
            double N0 = tryparm[0];
            double S0 = bounded ? tryparm[1] : 0.0;
            double S1 = tryparm[2];
            double p = tryparm[3];
            double F = templates[i].flux;

            return getFillFraction(i, S0, p-F/S1) * N0 * Math.pow(F/S1, p) * Math.exp(-F/S1);
        }

        @Override
        public double getCountsErrFor(double[] tryparm, int i) { return getStandardError(0, 0.1*parameter[0]) * getCountsFor(tryparm, i); }

        @Override
        public double evaluate(double[] tryparm) {
            double S0 = tryparm[1];
            double chi2 = super.evaluate(tryparm);
            if(S0 < dF) return (1.0 + Math.pow((S0/dF-1.0), 2.0)) * chi2;
            return chi2;
        }

        @Override
        public double getBackground(double[] tryparm) {
            if(bounded) {
                double N0 = tryparm[0];
                double S0 = tryparm[1];
                double S1 = tryparm[2];
                double p = tryparm[3];
                double delta = S0 < dF ? Math.pow(dF, 2.0 + p) - Math.pow(S0, 2.0 + p) : 0.0;
                delta *= N0 * Math.pow(S1, -p) / (2.0 - p);
                return delta + super.getBackground(tryparm);
            }
            return super.getBackground(tryparm);
        }

        @Override
        public double minimize(int n) {
            double chi2 = super.minimize(n);

            double N = parameter[0]/dF * Unit.mJy * Unit.deg2 / mapArea;
            double dN = N/parameter[0] * getStandardError(0, 0.01*parameter[0]);
            double S0 = parameter[1] / Unit.Jy;
            double dS0 = getStandardError(1, 0.1*parameter[1]) / Unit.Jy;
            double S1 = parameter[2] / Unit.Jy;
            double dS1 = getStandardError(2, 0.01*parameter[2]) / Unit.Jy;
            double alpha = parameter[3];
            double dalpha = getStandardError(3, 0.001);


            System.err.println("N' = " + Util.e3.format(N) + " +- " + Util.e1.format(dN) + " #/mJy/deg2");
            if(bounded) System.err.println("S0 = " + Util.e3.format(S0) + " +- " + Util.e1.format(dS0) + " Jy");
            System.err.println("S1 = " + Util.e3.format(S1) + " +- " + Util.e1.format(dS1) + " Jy");
            System.err.println("p = " + Util.f3.format(alpha) + " +- " + Util.f3.format(dalpha));

            return chi2;
        }


        @Override
        public int getNoiseScaleIndex() { return 4; }

        @Override
        public int getOffsetIndex() { return 5; }


    }



    class IndividualFit extends RegularFit {
        int freeValues;


        @Override
        public void init(Histogram mapHistogram, double maxFlux, int bins) {	
            freeValues = bins;
            super.init(mapHistogram, maxFlux, bins);

            double[] initparms = new double[freeValues + 2];

            double N0 = guessN(5.0 * Unit.mJy);
            double p = 4.0;

            for(int i=0; i<freeValues; i++) initparms[i] = N0 * Math.pow(templates[i].flux / (5.0 * Unit.mJy), -p);	
            initparms[freeValues] = noiseScaling;
            initparms[freeValues + 1] = 0.0;
            init(initparms);

            startSize = new double[parameter.length];
            for(int i=0; i<freeValues; i++) startSize[i] = 0.1 * initparms[i];
            startSize[freeValues] = 0.1;
            startSize[freeValues + 1] = 0.1;
        }

        @Override
        public double minimize(int n) {
            double chi2 = super.minimize(n);

            double a = Unit.deg2 / mapArea * Unit.mJy / dF;

            for(int k=0; k<freeValues; k++) {
                System.err.println(Util.e3.format(templates[k].flux / Unit.Jy) + "\t"
                        + Util.e3.format(a * parameter[k]) + "  \t"
                        + Util.e3.format(a * getStandardError(k, 0.1*parameter[k]))
                        );
            }

            System.err.println();

            double[] integral = new double[freeValues];
            double[] dN = new double[freeValues];
            double sum = 0.0, var = 0.0;

            for(int k=freeValues-1; k>=0; k--) {
                sum += a * parameter[k];
                double rms = a * getStandardError(k, 0.1*parameter[k]);
                var += rms * rms;

                integral[k] = sum;
                dN[k] = Math.sqrt(var);
            }

            for(int k=0; k<freeValues; k++) {
                System.err.println(Util.e3.format((k+1)*dF / Unit.Jy) + "\t"
                        + Util.e3.format(integral[k]) + "  \t"
                        + Util.e3.format(dN[k])
                        );
            }

            System.err.println();

            return chi2;
        }

        @Override
        public double getCountsFor(double[] tryparm, int i) { 		
            int k = i;
            return tryparm[k];
        }

        @Override
        public double getCountsErrFor(double[] tryparm, int i) { 
            return getStandardError(i, 0.1);
        }

        @Override
        public int getNoiseScaleIndex() { return freeValues; }

        @Override
        public int getOffsetIndex() { return freeValues+1; }

    }


    abstract class RegularFit extends TemplateFit {
        public double dF;	

        public void init(Histogram mapHistogram, double maxFlux, int bins) {	
            double[] fluxes = new double[bins];
            dF = maxFlux / bins;
            double flux = dF;
            for(int i=0; i<fluxes.length; i++, flux+=dF) fluxes[i] = Math.sqrt(flux * (flux+dF)); 
            super.init(mapHistogram, fluxes);
        }

        @Override
        public double getdF(int i) { 
            return dF;
        }

        public double guessN(double S) {
            return super.guessN(S, dF);
        }

        public double getFillFraction(int bin, double S0, double index) {
            double bottom = dF * (bin+1);
            double top = bottom + dF;

            if(S0 < bottom) return 1.0;
            else if(S0 > top) return 0.0;
            else return (Math.pow(S0, index+1.0) - Math.pow(top, index+1.0)) / (Math.pow(bottom, index+1.0) - Math.pow(top, index+1.0));
        }

    }

    abstract class TemplateFit extends AmoebaMinimizer {
        public double resolution, fluxResolution;
        public double[] histogram, modelHist, noiseHist, hiresModel;
        public CharSpec model, noise;
        public CharSpec[] templates, sources, fluxSpecs;
        public double backGround = 0.0;
        public int points = 0;
        //public int oversampling = 1;

        public TemplateFit() {
            verbose = true;
        }

        public void init(Histogram mapHistogram, double[] fluxes) {
            resolution = mapHistogram.resolution;
            points = mapHistogram.size();

            double maxDev = mapHistogram.getMaxDev();
            System.err.println("Max histogram deviation is: " + maxDev);

            int bins = 2 * ExtraMath.pow2ceil((int) Math.ceil(4.0 * maxDev / resolution));

            double maxFlux = 0.0;
            for(int i=0; i<fluxes.length; i++) if(fluxes[i] > maxFlux) maxFlux = fluxes[i];
            fluxResolution = 8.0 * maxFlux / bins / oversampling;

            System.err.println("Using " + bins + " histogram bins.");

            resolution = mapHistogram.resolution;
            histogram = mapHistogram.toFFTArray(bins);
            modelHist = new double[bins];
            noiseHist = new double[oversampling * bins];
            hiresModel = new double[oversampling * bins];
            CharSpec[][] temps = makeTemplates(fluxes, 1000, resolution / oversampling, oversampling * bins, fluxResolution);
            templates = temps[0];
            fluxSpecs = temps[1];

            model = new CharSpec(new double[hiresModel.length]);
            model.clear();
            sources = new CharSpec[templates.length];
            for(int i=0; i<sources.length; i++) sources[i] = new CharSpec(model.size());
        }

        public String getCoreFileName() {
            return CRUSH.workPath + File.separator + getClass().getSimpleName();
        }
        
        @Override
        public double minimize(int n) {
            System.err.println("Minimizing...");

            verbose = true;

            for(int i=0; i<3; i++) {
                if(i > 0) {
                    applyOffset(getOffset());
                    init(parameter);
                }
                super.minimize(n);	
                shrinkInitSize(0.3);
            }


            noiseScaling = parameter[getNoiseScaleIndex()];
   
            try { writeFit(new PrintStream(new FileOutputStream(getCoreFileName() + ".fit"))); }
            catch(IOException e) { e.printStackTrace(); }

            try { writeCounts(new PrintStream(new FileOutputStream(getCoreFileName() + ".cnt"))); }
            catch(IOException e) { e.printStackTrace(); }

            writeSimulated();

            /*
			double[][] C = getCovarianceMatrix(0.1);
			double[] sigma = new double[C.length];
			for(int i=0; i<C.length; i++) sigma[i] = Math.sqrt(Math.abs(C[i][i]));

			for(int i=0; i<C.length; i++) {
				for(int j=0; j<C.length; j++) {
					C[i][j] /= sigma[i] * sigma[j];
					System.err.print("\t" + Util.f2.format(C[i][j]));
				}
				System.err.println();
			}
             */

            return getChi2();
        }		

        public void setMapHistogram(Histogram mapHistogram) {
            histogram = mapHistogram.toFFTArray(modelHist.length);
            points = mapHistogram.size();
        }		

        public void setNoise(Histogram noiseHistogram) {
            setNoise(noiseHistogram.toFFTArray(modelHist.length));		
        }

        public void setNoise(double[] hist) {
            noise = new CharSpec(hist);
        }

        public void setResolution(double resolution) {
            this.resolution = resolution;
        }

        public abstract double getCountsFor(double[] tryparm, int i);

        public abstract double getCountsErrFor(double[] tryparm, int i);

        public abstract int getNoiseScaleIndex();

        public abstract double getdF(int i);

        public abstract int getOffsetIndex();

        public void fixedNoise() {
            fitList.remove(new Integer(getNoiseScaleIndex()));
        }

        private boolean fitOffset = true;		
        public void noOffset() {
            fitList.remove(new Integer(getOffsetIndex()));
            fitOffset = false;
        }

        @Override
        public void fitAll() {
            super.fitAll();
            fitOffset = true;
        }

        public double getNoiseScale() { return parameter[getNoiseScaleIndex()]; }

        public double getOffset() { return parameter[getOffsetIndex()]; }

        public void applyOffset(double s2nOffset) {
            double dBackGround = s2nOffset * mapRMS / Unit.Jy * map.getUnit().value();
            backGround += dBackGround;
            map.addValue(dBackGround);
            parameter[getOffsetIndex()] -= s2nOffset;
            if(saveparm != null) saveparm[getOffsetIndex()] -= s2nOffset;
        }

        public double getBackground(double[] tryparm) {
            double bg = 0.0;
            for(int i=0; i<templates.length; i++) bg += templates[i].flux * getCountsFor(tryparm, i);	
            bg *= Unit.deg2 / mapArea;
            //bg -= backGround / map.janskyPerBeam.value * Unit.Jy * Unit.deg2 / map.imageBeamArea;
            return bg;
        }

        // Assume 50 sources per sqdeg per mJy at 5 mJy...
        public double guessN(double S, double dF) {	
            return 50.0 * Math.pow(S/(5.0 * Unit.mJy), -4.0) * mapArea / Unit.deg2 * dF / Unit.mJy;
        }

        public double getReducedChi2() {
            return getChi2() / (points - fitList.size());			
        }

        @Override
        public double evaluate(double[] tryparm) {
            if(!fixedNoise) noiseScaling = tryparm[getNoiseScaleIndex()];

            gaussianNoise(noiseScaling, fitOffset ? tryparm[getOffsetIndex()] : 0.0, noiseHist, resolution / oversampling);
            setNoise(noiseHist);


            model.copy(noise);

            double factor = 1.0;
            double chi2 = 0.0; 

            for(int i=0; i<templates.length; i++) {
                double N = getCountsFor(tryparm, i);
                if(N <= 0.0) factor *= 1.0 - N;
                else {
                    templates[i].forSources(N, sources[i]);
                    model.multiplyBy(sources[i]);
                }
            }
            model.toProbabilities(hiresModel);



            Arrays.fill(modelHist, 0.0);
            for(int i=0, j=0; i<modelHist.length; i++) for(int k=0; k<oversampling; k++, j++) 
                modelHist[i] += hiresModel[j]; 

            double norm = 0.0;
            for(int i=0; i<modelHist.length; i++) norm += modelHist[i];
            for(int i=0; i<modelHist.length; i++) modelHist[i] /= norm;

            for(int i=0; i<histogram.length; i++) {
                modelHist[i] *= mapPixels;
                double var = modelHist[i] > 1e-6 ? modelHist[i] : 1e-6;
                double dev = histogram[i] - modelHist[i];
                chi2 += dev * dev / var;
            }

            if(!Double.isNaN(background)) {
                double dev = (getBackground(tryparm) - background) / dBG;
                chi2 += dev * dev;
            }

            return factor * factor * chi2;
        }

        public void writeTemplates() throws IOException {		
            for(int i=0; i<templates.length; i++) {
                PrintWriter out = new PrintWriter(new FileOutputStream(CRUSH.workPath + File.separator + "template-" + (i+1) + ".dat"));
                double[] p = new double[histogram.length];
                templates[i].toProbabilities(p);
                out.println(templates[i].toString(p));
                out.close();
            }
        }	


        public void writeSourceFluxDistribution() throws IOException {	
            CharSpec fluxSpec = fluxSpecs[0].copy();
            CharSpec temp = fluxSpecs[0].copy();

            fluxSpec.clear();

            for(int i=0; i<fluxSpecs.length; i++) {		
                fluxSpecs[i].forSources(Math.max(0.0, getCountsFor(parameter, i)), temp);
                fluxSpec.multiplyBy(temp);
            }

            String fileName = getCoreFileName() + ".dist";
            PrintWriter out = new PrintWriter(new FileOutputStream(fileName));
            double[] n = fluxSpec.toProbabilities();

            // Make sure it is a proper probability dsitribution with integral 1.0
            double renorm = 0.0;
            for(int i=0; i<n.length; i++) renorm += n[i];
            for(int i=0; i<n.length; i++) n[i] /= renorm;

            // TODO remove median from distribution...

            out.println("# Resolution = " + Util.e6.format(fluxResolution / Unit.Jy) + " Jy");
            //out.println("# Re-centered around Median of distribution...")
            out.println(fluxSpec.toString(n));
            out.close();
            //System.err.println("Written " + fileName);
        }	


        public void writeCounts(PrintStream out) {
            out.println("# flux\tparm\t\terr");

            double totalCounts = 0.0;

            for(int i=templates.length-1; i>=0; i--) {
                double binCounts = getCountsFor(parameter, i);
                out.println(Util.e3.format(templates[i].flux / Unit.Jy) + "\t" 
                        + Util.e3.format(binCounts) + "\t" 
                        + Util.e3.format(getCountsErrFor(parameter, i)) + "\t"
                        + Util.e3.format(totalCounts)
                        );
                totalCounts += binCounts;
            }
            out.flush();
        }

        public void writeFit(PrintStream out) {
            out.println("# data\tmodel\tdiff");
            int n = histogram.length >> 1;
            for(int i=n+1; i<histogram.length; i++) 
                out.println(Util.f2.format(resolution*(i - histogram.length)) + 
                        "\t" + Util.e3.format(histogram[i]) +
                        "\t" + Util.e3.format(modelHist[i]) +
                        "\t" + Util.e3.format(modelHist[i] - histogram[i])
                        );
            for(int i=0; i<n; i++)
                out.println(Util.f2.format(resolution*(i)) + 
                        "\t" + Util.e3.format(histogram[i]) +
                        "\t" + Util.e3.format(modelHist[i]) +
                        "\t" + Util.e3.format(modelHist[i] - histogram[i])
                        );
            out.close();			
        }

        public void writeSimulated() {
            double[] fluxes = new double[templates.length];
            double[] counts = new double[templates.length];
            for(int i=0; i<templates.length; i++) {
                fluxes[i] = templates[i].flux;
                counts[i] = getCountsFor(parameter, i);				
            }

            SkyMap sim = getSimulated(fluxes, counts);

            // Write out the simulated
            sim.fileName = getCoreFileName() + ".sim.fits";
            try { sim.write(); }
            catch(Exception e) { e.printStackTrace(); }
        }
    }
 
    
    
    class CharSpec implements Cloneable {
        double flux = Double.NaN;
        double testSources = Double.NaN;
        private Complex[] d; // A(x) = 1 + d(x); the zero index stores the Nyquist component...
        private DoubleFFT fft = new DoubleFFT();
        
        public CharSpec(int size) {
            d = new Complex[size];
            for(int i=size; --i >= 0; ) d[i] = new Complex();
        }
        
        public CharSpec(double[] histogram) {
            double[] p1 = new double[histogram.length];
            double norm = 0.0;
            int nf = histogram.length >>> 1;
            
            for(int i=histogram.length; --i >= 0; ) norm += histogram[i];
            norm /= nf;
            
            for(int i=histogram.length; --i >= 0; ) p1[i] = histogram[i] / norm;
            p1[0] -= nf; 
            
            fft.real2Amplitude(p1);
            
            d = new Complex[nf];
            d[0] = new Complex(p1[1], 0.0);
            
            for(int i=nf; --i > 0; ) {
                int j = i<<1;
                d[i] = new Complex(p1[j], p1[j | 1]);
            }
           
        }
        
        @Override
        public Object clone() {
            try { return super.clone(); }
            catch(CloneNotSupportedException e) { return null; }        
        }
        
        public CharSpec copy() {
            CharSpec copy = (CharSpec) clone();
            copy.d = new Complex[d.length];
            for(int i=d.length; --i >= 0; ) copy.d[i] = (Complex) d[i].clone();
            return copy;
        }
        
        public void clear() {
            for(int i=d.length; --i >= 0; ) d[i].zero();
        }
        
        public int size() { return d.length; }
        
        public void forSources(double n, CharSpec spec) {
            
            // (1+1/n)^(n) = e  when n >> 1
            // (1+a/n)^(n/a) = e
            // (1+a/n)^n = e^a
            // (1+x)^n = e^(nx) when nx << 1
            // x' = e^(nx) - 1 ~ nx when nx << 1;
            
            n /= testSources;
             
            for(int i=d.length; --i >= 0; ) {
                final Complex z = spec.d[i];
                
                z.copy(d[i]);
                
                if(n > 100.0) {
                    if(n * d[i].length() < 1e-5) z.scale(n);
                    else {
                        z.scale(n);
                        z.expm1();
                    }
                }
                else if(n < 1e-5) z.scale(n);
                else {      
                    z.addX(1.0);
                    z.pow(n);
                    z.subtractX(1.0);
                }
            }
        }
        
        public double[] toProbabilities() {
            double[] counts = new double[d.length<<1];
            toProbabilities(counts);
            return counts;
        }
        
        public void toProbabilities(double[] counts) {
            int nf = d.length;  
          
            counts[0] = 0.0;
            counts[1] = d[0].x();
            
            for(int i=nf; --i > 0; ) {
                int j = i<<1;
                counts[j] = d[i].x();
                counts[j | 1] = d[i].y();
            }
            
            fft.amplitude2Real(counts);
         
            for(int i=counts.length; --i >= 0; ) counts[i] /= nf;
            counts[0] += 1.0;
        }
        

        public void copy(CharSpec f) {
            for(int i=d.length; --i >= 0; ) d[i].copy(f.d[i]);        
        }

        // (1+x) * (1+y) = 1 + x + y + xy;
        // d = x + y + xy
        public void multiplyBy(CharSpec f) {
            Complex xy = new Complex();
            for(int i=d.length; --i >= 0; ) {
                xy.copy(d[i]);
                xy.math('*', f.d[i]);
                d[i].add(f.d[i]);
                d[i].add(xy);
            }
        }
        
        public String toString(double[] bins) {
            String text = "# Bin data\n";
            int n = bins.length >> 1;
            for(int i=n+1; i<bins.length; i++) text += (i - bins.length) + "\t" + Util.e3.format(bins[i]) + "\n";
            for(int i=0; i<n; i++) text += i + "\t" + Util.e3.format(bins[i]) + "\n";
            return text;
        }
        
        @Override
        public String toString() {
            String text = "# Flux = " + Util.e3.format(flux / Unit.Jy) + "Jy\n";
            text += "# Test sources = " + Util.e3.format(testSources) + "\n";
            for(int i=0; i<d.length; i++) text += Util.e3.format(d[i].length()) + "\n";
            return text;
        }
        
    }
    
    
    
}