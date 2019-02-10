/*******************************************************************************
 * Copyright (c) 2019 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush;

import nom.tam.fits.*;
import nom.tam.util.*;
import crush.instrument.InstantFocus;
import crush.sourcemodel.*;

import java.io.*;
import java.util.*;
import jnum.Configurator;
import jnum.ExtraMath;
import jnum.LockedException;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroTime;
import jnum.data.*;
import jnum.data.image.Asymmetry2D;
import jnum.data.image.Map2D;
import jnum.data.image.Observation2D;
import jnum.data.image.region.CircularRegion;
import jnum.data.image.region.EllipticalSource;
import jnum.data.image.region.GaussianSource;
import jnum.fits.FitsToolkit;
import jnum.math.Coordinate2D;
import jnum.math.CoordinateSystem;
import jnum.math.Offset2D;
import jnum.math.Range;
import jnum.math.Range2D;
import jnum.math.SphericalCoordinates;
import jnum.math.Vector2D;
import jnum.reporting.BasicMessaging;
import jnum.text.TableFormatter;
import jnum.util.*;
import jnum.util.DataTable;

public abstract class Scan<IntegrationType extends Integration<? extends Frame>>
extends Vector<IntegrationType> implements Comparable<Scan<?>>, TableFormatter.Entries, BasicMessaging {
    /**
     * 
     */
    private static final long serialVersionUID = 8967822331907667222L;
    private Instrument<?> instrument;

    private int serialNo = -1;
    private double MJD = Double.NaN;
    private String sourceName;

    public String timeStamp;
    public String descriptor;
    public String observer, creator, project;

    public Vector2D pointingCorrection;

    public Range2D range;
    public boolean isTracking = false;
    public boolean isNonSidereal = false;

    public SourceModel sourceModel;
    public double weight = 1.0;

    public int sourcePoints = 0;
    public boolean isSplit = false;

    public GaussianSource pointing;

    // Creates a scan with an initialized copy of the instrument
    public Scan(Instrument<?> instrument) { 
        this.instrument = instrument.copy();
        this.instrument.setParent(this);
    }
    
    public Instrument<?> getInstrument() { return instrument; }

    public Configurator getOptions() { return instrument.getOptions(); }
    
    public void setOptions(Configurator options) { instrument.setOptions(options); }
    
    @Override
    public int compareTo(Scan<?> other) {
        return Double.compare(getMJD(), other.getMJD());
    }

    public void validate() {	
        info("Processing scan data:");

        if(hasOption("subscans.merge")) mergeIntegrations();

        if(hasOption("segment")) {
            double segmentTime = 60.0 * Unit.s;
            try { segmentTime = option("segment").getDouble() * Unit.s; }
            catch(NumberFormatException e) {}
            segmentTo(segmentTime);
        }

        isNonSidereal |= hasOption("moving");


        //debug("MJD: " + Util.f3.format(MJD) + " --> Epoch is J" + Util.f2.format(JulianEpoch.getYearForMJD(MJD)));
        //debug("LST: " + Util.f3.format(LST/Unit.hour));


        for(int i=0; i<size(); ) {
            Integration<?> integration = get(i);
            try { 
                info("Processing integration " + (i+1) + ":");
                integration.validate();
                i++;
                debug("Integration has " + integration.getFrameCount(~0) + " valid frames.");
            }
            catch(Exception e) {
                integration.warning("Integration " + (i+1) + " validation error (dropping from set):\n   --> " + e.getMessage());
                if(CRUSH.debug) CRUSH.trace(e);
                remove(i); 
            }
        }

        if(instrument.getOptions().containsKey("jackknife")) sourceName += "-JK";

        if(instrument.getOptions().containsKey("pointing")) pointingAt(getPointingCorrection(option("pointing")));	

        instrument.calcOverlaps(getPointSize());
    }

    public void setIteration(int i, int rounds) {
        CRUSH.setIteration(instrument.getOptions(), i, rounds);
        instrument.calcOverlaps(getPointSize());
        for(Integration<?> integration : this) if(integration.getInstrument() != instrument) integration.setIteration(i, rounds);	
    }

    public int getSerial() { return serialNo; }

    public void setSerial(int n) { 
        serialNo = n;
        instrument.setSerialOptions(n);
    }

    public double getMJD() {
        return MJD;
    }

    public void setMJD(double MJD) {
        this.MJD = MJD;
        instrument.setDateOptions(MJD);
    }

    public String getShortDateString() {
        AstroTime time = new AstroTime();
        time.setMJD(getMJD());
        return time.getFitsShortDate();
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String value) {
        sourceName = value;
        instrument.setObjectOptions(sourceName);
    }	

    public void setSuggestPointing() {
        if(hasOption("point")) return;
        info("Setting 'point' option to obtain pointing/calibration data.");
        instrument.setOption("point");    
    }
    

    public Vector2D getPointingCorrection(Configurator option) {
        Vector2D correction = option.isEnabled ? option.getVector2D() : new Vector2D();
        if(option.hasOption("offset")) correction.add(option.option("offset").getVector2D());
        correction.scale(instrument.getSizeUnit().value());
        return correction;
    }

    public void pointingAt(Vector2D correction) {
        if(correction == null) return;
        double sizeUnit = instrument.getSizeUnit().value();
        info("Adjusting pointing by " + 
                Util.f1.format(correction.x() / sizeUnit) + ", " + Util.f1.format(correction.y() / sizeUnit) +
                " " + instrument.getSizeUnit().name() + ".");

        for(Integration<?> integration : this) integration.pointingAt(correction);
        if(pointingCorrection == null) pointingCorrection = correction;
        else pointingCorrection.add(correction);
    }



    public IntegrationType getFirstIntegration() {
        return get(0); 
    }

    public IntegrationType getLastIntegration() {
        return get(size()-1);
    }

    public boolean hasOption(String key) {
        return instrument.hasOption(key);
    }

    public Configurator option(String key) {
        return instrument.option(key);
    }

    // Read should validate the instrument before instantiating integrations for reading...
    public abstract void read(String descriptor, boolean readFully) throws Exception;

    // The integration should carry a copy of the instrument s.t. the integration can freely modify it...
    // The constructor of Integration thus copies the Scan instrument for private use...
    public abstract IntegrationType getIntegrationInstance();

    public String getDataPath() {
        return hasOption("datapath") ? option("datapath").getPath() + File.separator : "";
    }



    @SuppressWarnings("unchecked")
    public void mergeIntegrations() {	
        if(size() < 2) return;

        // TODO What if different sampling intervals...
        info("Merging " + size() + " integrations...");

        final double maxDiscontinuity = hasOption("subscans.merge.maxgap") ? option("subscans.merge.maxgap").getDouble() * Unit.s : Double.NaN;
        final int maxGap = Double.isNaN(maxDiscontinuity) ? Integer.MAX_VALUE : (int) Math.ceil(maxDiscontinuity / instrument.samplingInterval);

        Integration<Frame> merged = (Integration<Frame>) get(0);
        merged.trimEnd();

        double lastMJD = merged.getLastFrame().MJD;

        ArrayList<IntegrationType> parts = new ArrayList<>();

        for(int i=1; i<size(); i++) {
            IntegrationType integration = get(i);

            // Remove null frames from the end;
            integration.trimEnd();

            // Skip null frames at the beginning...
            final int nt = integration.size();
            int from = 0;
            for( ; from < nt; from++) if(integration.get(from) != null) break;			

            int gap = (int) Math.round((integration.get(from).MJD - lastMJD) * Unit.day / instrument.samplingInterval) - 1;

            // Deal with any gaps between subscans here...
            if(gap > 0) {
                if(gap < maxGap) {
                    info("  > Padding with " + gap + " frames before integration " + integration.getID());
                    for(; --gap >= 0; ) merged.add(null);
                }
                else {
                    info("  > Large gap before integration " + integration.getID() + ". Starting new merge.");
                    parts.add((IntegrationType) merged);
                    merged = (Integration<Frame>) integration;
                }	
            }

            // Do the actual merge...
            for(int t=from; t<nt; t++) merged.add(integration.get(t));

            lastMJD = integration.getLastFrame().MJD;
        }

        merged.reindex();

        info("  > Total esposure time: " + Util.f1.format(merged.getExposureTime() / Unit.s) + "s.");

        clear();
        addAll(parts);
        add((IntegrationType) merged);
    }



    public BasicHDU<?> getSummaryHDU(Configurator global) throws FitsException, IOException {
        Object[][] table = new Object[size()][];
        boolean details = hasOption("write.scandata.details");

        LinkedHashMap<String, Object> first = null;

        for(int i=0; i<table.length; i++) {
            IntegrationType integration = get(i);
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            if(i == 0) first = data;
            integration.getFitsData(data);
            if(details) integration.addDetails(data);
            Object[] row = new Object[data.size()];
            int k=0;
            for(Object entry : data.values()) row[k++] = entry; 
            table[i] = row;
        }

        BasicHDU<?> hdu = Fits.makeHDU(table);
        Header header = hdu.getHeader();
        editScanHeader(header);

        Cursor<String, HeaderCard> cursor = header.iterator();

        int k=1;
        if(first != null) for(String name : first.keySet()) {
            cursor.setKey("TFORM" + k);
            cursor.add(new HeaderCard("TTYPE" + (k++), name, "The column name"));
        }


        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));
        c.add(new HeaderCard("COMMENT", " CRUSH scan-specific configuration section", false));
        c.add(new HeaderCard("COMMENT", " ----------------------------------------------------", false));

        instrument.getInitialOptions().difference(global).editHeader(header);	

        return hdu;
    }


    public void editScanHeader(Header header) throws HeaderCardException {	
        Locale.setDefault(Locale.US);

        Cursor<String, HeaderCard> c = FitsToolkit.endOf(header);
        c.add(new HeaderCard("EXTNAME", "Scan-" + getID(), "Scan data"));
        c.add(new HeaderCard("INSTRUME", instrument.getName(), "The instrument name"));
        c.add(new HeaderCard("SCANID", getID(), "Scan ID."));

        if(serialNo > 0) c.add(new HeaderCard("SCANNO", serialNo, "Serial number for the scan"));
        if(descriptor != null) FitsToolkit.addLongKey(c, "SCANSPEC", descriptor, "Scan descriptor");
        if(observer != null) c.add(new HeaderCard("OBSERVER", observer, "Name(s) of the observer(s)"));
        if(project != null) c.add(new HeaderCard("PROJECT", project, "Description of the project"));
        if(creator != null) c.add(new HeaderCard("CREATOR", creator, "Software that wrote the scan data"));
        if(timeStamp != null) c.add(new HeaderCard("DATE-OBS", timeStamp, "Start of observation"));

        double MJD = getMJD();
        if(!Double.isNaN(MJD)) c.add(new HeaderCard("MJD", MJD, "Modified Julian Day"));

        // The Source Descriptors
        c.add(new HeaderCard("OBJECT", sourceName, "Object catalog name"));


        c.add(new HeaderCard("WEIGHT", weight, "Relative source weight of the scan"));	
        c.add(new HeaderCard("TRACKIN", isTracking, "Was the telescope tracking during the observation?"));



        instrument.editScanHeader(header);

    }



    public void setSourceModel(SourceModel model) {
        sourceModel = model;
    }

    public double getObservingTime() {
        return getObservingTime(~0);
    }

    public double getObservingTime(int skipFlags) {
        double t = 0.0;
        for(IntegrationType integration : this) t += integration.getFrameCount(skipFlags) * integration.getInstrument().integrationTime;
        return t;
    }

    public int getFrameCount(int skipFlags) {
        int n = 0;
        for(IntegrationType integration : this) n += integration.getFrameCount(skipFlags);
        return n;
    }

    // separators: ' ' \t , =
    // entries:	name(format)
    //      format: e.g. d3, f1, e3, t:1 (time with colons, 1 decimal), ad2 (angle with symbols, 2 decimals)
    public void writeLog(Configurator option, String defaultFileName) throws IOException {
        // Map the optional argument to the 'file' sub-option.
        try { option.mapValueTo("file"); }
        catch(LockedException e) {}

        String fileName = option.hasOption("file") ? 
                option.option("file").getPath() : defaultFileName;

                String format = option.hasOption("format") ? 
                        option.option("format").getValue() : 
                            "date(yyyy-MM-dd)  id\tobject\tobsmins(f1)\tAZd(f1) ELd(f1)\tRAh(f1) DECd(f1)";

                        // Allow literal '\t' to represent a tab, like in C or Java 
                        format = Util.fromEscapedString(format);

                        int conflictPolicy = LogFile.CONFLICT_DEFAULT;
                        if(option.hasOption("conflict")) {
                            String policy = option.option("conflict").getValue().toLowerCase();
                            if(policy.equals("overwrite")) conflictPolicy = LogFile.CONFLICT_OVERWRITE;
                            else if(policy.equals("version")) conflictPolicy = LogFile.CONFLICT_VERSION;
                        }

                        LogFile log = new LogFile(fileName, format, conflictPolicy);
                        log.add(TableFormatter.format(this, format));

                        notify("Written log to " + log.getFileName());
    }


    @Override
    public Object getTableEntry(String name) {



        String sourceType = sourceModel == null ? "model." : sourceModel.getLoggingID();

        if(name.startsWith("?")) {
            name = name.substring(1).toLowerCase();
            if(!hasOption(name)) return null;

            String value = option(name).getValue();
            if(value.length() == 0) return "<true>";

            return value;			
        }
        if(name.startsWith(sourceType + ".")) {
            if(sourceModel == null) return null;
            return sourceModel.getTableEntry(name.substring(sourceType.length() + 1));
        }
        if(name.startsWith("pnt.")) {
            if(pointing == null) return null;
            return getPointingData().getTableEntry(name.substring(4));
        }
        if(name.startsWith("src.")) {
            if(pointing == null) return null;
            if(!(sourceModel instanceof IntensityMap)) return null;
            Map2D map = ((IntensityMap) sourceModel).map;
            return pointing.getRepresentation(map.getGrid()).getData(map, instrument.getSizeUnit()).getTableEntry(name.substring(4));
        }
        if(name.equals("object")) return sourceName;
        if(name.equals("id")) return getID();
        if(name.equals("serial")) return serialNo;
        if(name.equals("MJD")) return MJD;
        if(name.equals("UT")) return (MJD - Math.floor(MJD)) * Unit.day;
        if(name.equals("UTh")) return (MJD - Math.floor(MJD)) * 24.0;
        if(name.equals("PA")) return getPositionAngle();
        if(name.equals("PAd")) return getPositionAngle() / Unit.deg;
        if(name.equals("date")) {
            AstroTime time = new AstroTime();
            time.setMJD(MJD);
            return time.getDate();
        }
        if(name.equals("obstime")) return getObservingTime() / Unit.sec;
        if(name.equals("obsmins")) return getObservingTime() / Unit.min;
        if(name.equals("obshours")) return getObservingTime() / Unit.hour;
        if(name.equals("weight")) return weight;
        if(name.equals("frames")) return getFrameCount(~0); 
        if(name.equals("project")) return project;
        if(name.equals("observer")) return observer; 
        if(name.equals("descriptor")) return descriptor;
        if(name.equals("creator")) return creator;
        if(name.equals("integrations")) return size();
        if(name.equals("generation")) return getSourceGeneration();

        if(sourceModel != null) {
            Object modelEntry = sourceModel.getTableEntry(name);
            if(modelEntry != null) return modelEntry;
        }
        return getFirstIntegration().getTableEntry(name);
    }

    public int getSourceGeneration() {
        int max = 0;
        for(Integration<?> integration : this) max = Math.max(integration.sourceGeneration, max);
        return max;		
    }

    public void writeProducts() {
        for(Integration<?> integration : this) integration.writeProducts();

        if(!hasOption("lab")) {
            reportFocus();
            reportPointing();
        }

        if(hasOption("log")) {
            try { writeLog(option("log"), instrument.getOutputPath() + File.separator + instrument.getName() + ".log"); }
            catch(IOException e) {
                warning("Could not write log.");
                if(CRUSH.debug) CRUSH.trace(e);
            }
        }
    }

    public void reportFocus() {
        if(pointing != null) {
            CRUSH.result(this, "Instant Focus Results for Scan " + getID() + ":\n\n" + getFocusString());
        }
        else if(hasOption("pointing")) if(sourceModel.isValid()) {
            Configurator pointingOption = option("pointing");
            if(pointingOption.is("suggest") || pointingOption.is("auto")) 
                warning("Cannot suggest focus for scan " + getID() + ".");
        }
    }


    public void reportPointing() {
        if(pointing != null) {
            CRUSH.result(this, "Pointing Results for Scan " + getID() + ":\n\n" + getPointingString());
        }
        else if(hasOption("pointing")) if(sourceModel.isValid()) {
            Configurator pointingOption = option("pointing");
            if(pointingOption.is("suggest") || pointingOption.is("auto"))
                warning("Cannot suggest pointing for scan " + getID() + ".");
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ArrayList<Scan<IntegrationType>> split() {
        info("Splitting subscans into separate scans.");
        ArrayList<Scan<IntegrationType>> scans = new ArrayList<>();
        for(IntegrationType integration : this) {
            Scan<IntegrationType> scan = (Scan<IntegrationType>) clone();
            if(size() > 1) scan.isSplit = true;
            scan.clear();
            scan.instrument = integration.getInstrument();
            integration.scan = (Scan) scan;
            scan.add(integration);
            scans.add(scan);
        }
        return scans;
    }

    public void updateGains(final String modalityName) {		
        boolean isRobust = false;
        if(!hasOption("gains")) return;
        if(hasOption("correlated." + modalityName + ".nogains")) return;

        Configurator gains = option("gains");

        try { gains.mapValueTo("estimator"); }
        catch(LockedException e) {} // TODO...

        if(gains.hasOption("estimator")) if(gains.option("estimator").is("median")) isRobust = true; 

        Hashtable<Integer, WeightedPoint[]> phaseGains = new Hashtable<>();

        final boolean usePhases = hasOption("correlated." + modalityName + ".phasegains");

        // Derive the scan gains
        boolean gotGains = false;
        for(IntegrationType integration : this) {
            final int phase = usePhases ? integration.getPhase() : 0;
            WeightedPoint[] G = phaseGains.get(phase);

            if(G == null) {
                G = WeightedPoint.createArray(instrument.storeChannels);
                phaseGains.put(phase, G);
            }

            try {		
                Modality<?> modality = integration.getInstrument().modalities.get(modalityName);
                if(modality.trigger != null) if(!hasOption(modality.trigger)) continue;
                modality.averageGains(G, integration, isRobust);
                gotGains = true;
            }	
            catch(Exception e) { error(e); }	
        }

        if(!gotGains) return;

        try { processPhaseGains(phaseGains); }
        catch(Exception e) {
            warning(e);
            return;
        }

        // Apply the gain increment
        for(IntegrationType integration : this) {
            Modality<?> modality = integration.getInstrument().modalities.get(modalityName);
            boolean isFlagging = false; 

            WeightedPoint[] G = phaseGains.get(usePhases ? integration.getPhase() : 0);

            try { isFlagging |= modality.applyGains(G, integration); }
            catch(Exception e) { error(e); }

            if(isFlagging) {
                integration.getInstrument().census();
                integration.comments.append(integration.getInstrument().mappingChannels);
            }
        }
    }

    public void processPhaseGains(Hashtable<Integer, WeightedPoint[]> phaseGains) throws Exception {}

    public void decorrelate(String modalityName) {
        boolean isRobust = false;
        if(hasOption("estimator")) if(option("estimator").is("median")) isRobust = true;

        if(hasOption("correlated." + modalityName + ".span")) {
            for(IntegrationType integration : this) integration.decorrelateSignals(modalityName, isRobust);
            updateGains(modalityName);
        }
        else for(IntegrationType integration : this) integration.decorrelate(modalityName, isRobust);

        for(IntegrationType integration : this) if(integration.comments.charAt(integration.comments.length() - 1) != ' ') 
            integration.comments.append(" ");
    }

    public void perform(String task) { 
        if(task.startsWith("correlated.")) decorrelate(task.substring(task.indexOf('.')+1));
        else for(IntegrationType integration : this) integration.perform(task);
    }

    public String getID() { return Integer.toString(serialNo); }

    public abstract Coordinate2D getNativeCoordinates();

    public Coordinate2D getReferenceCoordinates() { return getNativeCoordinates(); }

    public Coordinate2D getPositionReference(String system) {
        return getNativeCoordinates(); 
    }

    /**
     * The angle between the reference coordinates (e.g. equatorial) and the native coordinate system of the telescope (e.g. horizontal).
     * 
     * 
     * @return
     */
    public double getPositionAngle() { return 0.0; }

    public Offset2D getNativePointing(GaussianSource source) {
        Offset2D pointing = getNativePointingIncrement(source);
        if(pointingCorrection != null) pointing.add(pointingCorrection);
        return pointing;
    }

    public Offset2D getNativePointingIncrement(GaussianSource source) {
        if(!source.getCoordinates().getClass().equals(sourceModel.getReference().getClass()))
            throw new IllegalArgumentException("pointing source is in a different coordinate system from source model.");

        Coordinate2D sourceCoords = source.getCoordinates();
        Coordinate2D nativeCoords = getNativeCoordinates();

        if(sourceCoords instanceof Vector2D) if(sourceCoords.getClass().equals(nativeCoords.getClass())) {
            Vector2D offset = new Vector2D(sourceModel.getReference());
            offset.subtract((Vector2D) sourceCoords);
            offset.invert();
            return new Offset2D(sourceModel.getReference(), offset);
        }

        return null;
    }


    public DataTable getPointingData() throws IllegalStateException {
        if(pointing == null) throw new IllegalStateException("No pointing data for scan " + getID());
        Offset2D relative = getNativePointingIncrement(pointing);
        Offset2D absolute = getNativePointing(pointing);

        DataTable data = new DataTable();

        Unit sizeUnit = instrument.getSizeUnit();

        data.new Entry("dX", relative.x(), sizeUnit);
        data.new Entry("dY", relative.y(), sizeUnit);

        if(relative.getReference() instanceof SphericalCoordinates) {
            try {
                CoordinateSystem system = ((SphericalCoordinates) relative.getReference()).getCoordinateSystem();

                String nameX = system.get(0).getShortLabel();
                String nameY = system.get(1).getShortLabel();

                data.new Entry("d" + nameX, relative.x(), sizeUnit);
                data.new Entry("d" + nameY, relative.y(), sizeUnit);

                data.new Entry(nameX, absolute.x(), sizeUnit);
                data.new Entry(nameY, absolute.y(), sizeUnit);
            }
            catch(Exception e) { error(e); }
        }

        Asymmetry2D asym = getSourceAsymmetry(pointing);
        data.new Entry("asymX", 100.0 * asym.getX().value(), "%");
        data.new Entry("asymY", 100.0 * asym.getY().value(), "%");
        data.new Entry("dasymX", 100.0 * asym.getX().rms(), "%");
        data.new Entry("dasymY", 100.0 * asym.getY().rms(), "%");

        if(pointing instanceof EllipticalSource) {
            EllipticalSource ellipse = (EllipticalSource) pointing;
            Unit deg = Unit.get("deg");

            DataPoint elongation = ellipse.getElongation();
            data.new Entry("elong", 100.0 * elongation.value(), "%");
            data.new Entry("delong", 100.0 * elongation.rms(), "%");

            DataPoint angle = ellipse.getPositionAngle();
            data.new Entry("angle", angle.value(), deg);
            data.new Entry("dangle", angle.value(), deg);

            DataPoint elongationX = getSourceElongationX(ellipse);
            data.new Entry("elongX", 100.0 * elongationX.value(), "%");
            data.new Entry("delongX", 100.0 * elongationX.rms(), "%");	
        }

        return data;
    }	

    public String getPointingString() {
        String info = "";

        if(sourceModel instanceof IntensityMap) {
            Observation2D map = ((IntensityMap) sourceModel).map;
            info += pointing.pointingInfo(map) + "\n";
        }

        info += getPointingString(getNativePointingIncrement(pointing));
        return info;
    }


    public Asymmetry2D getSourceAsymmetry(CircularRegion region) {
        if(!(sourceModel instanceof IntensityMap)) return null;

        Range radialRange = new Range(
                instrument.getPointSize(),
                (hasOption("focus.r") ? option("focus.r").getDouble() : 2.5) * instrument.getPointSize()
                );

        return getSourceAsymmetry(region, radialRange);
    }

    public Asymmetry2D getSourceAsymmetry(CircularRegion region, Range radialRange) {	
        if(!(sourceModel instanceof IntensityMap)) return null;

        IntensityMap source = ((IntensityMap) sourceModel);

        CircularRegion.Representation r = region.getRepresentation(source.getGrid());
        return r.getAsymmetry2D(source.map.getSignificance(), 0.0, radialRange);
    }

    public DataPoint getSourceElongationX(EllipticalSource ellipse) {
        DataPoint elongation = new DataPoint(ellipse.getElongation());
        DataPoint angle = new DataPoint(ellipse.getPositionAngle());

        angle.subtract(getPositionAngle());

        elongation.scale(Math.cos(2.0 * angle.value()));
        return elongation;		
    }

    protected String getFocusString() {
        Asymmetry2D asym = getSourceAsymmetry(pointing);

        DataPoint elongation = pointing instanceof EllipticalSource ? 
                getSourceElongationX((EllipticalSource) pointing) : null;

                return getFocusString(asym, elongation);
    }

    protected String getFocusString(Asymmetry2D asym, DataPoint elongation) {
        StringBuffer info = new StringBuffer(asym == null ? "" : asym.toString() + "\n");

        if(elongation != null) info.append("  Elongation: " + elongation.toString(Unit.get("%")) + "\n");			

        double relFWHM = pointing.getFWHM().value() / getPointSize();

        boolean force = hasOption("focus");

        if(force || (relFWHM > 0.8 && relFWHM <= 2.0)) {
            InstantFocus focus = new InstantFocus();
            focus.deriveFrom(asym, elongation, instrument.getOptions());
            info.append(instrument.getFocusString(focus));
        }
        else {
            info.append("\n");
            if(relFWHM <= 0.8) info.append("  WARNING! Source FWHM unrealistically low.\n");
            else {
                info.append("  WARNING! Source is either too extended or too defocused.\n");
                info.append("           No focus correction is suggested. You can force calculate\n");
                info.append("           suggested focus values by setting the 'focus' option when\n");
                info.append("           running CRUSH.\n");
            }
        }

        return new String(info);
    }

    public double getPointSize() {
        double pointSize = instrument.getPointSize();
        if(sourceModel != null) pointSize = Math.max(pointSize, sourceModel.getPointSize()); 
        return pointSize;
    }


    protected String getPointingString(Offset2D nativePointing) {	
        if(nativePointing == null) return "";
        // Print the native pointing offsets...
        String text = "";

        Unit sizeUnit = instrument.getSizeUnit();

        String nameX = "x";
        String nameY = "y";

        if(nativePointing.getReference() instanceof SphericalCoordinates) {
            try {
                CoordinateSystem system = ((SphericalCoordinates) nativePointing.getReference()).getLocalCoordinateSystem();
                nameX = system.get(0).getShortLabel();
                nameY = system.get(1).getShortLabel();
            }
            catch(Exception e) { error(e); }
        }

        text += "  Offset: ";
        text += Util.f1.format(nativePointing.x() / sizeUnit.value()) + ", " + Util.f1.format(nativePointing.y() / sizeUnit.value()) + " " 
                + sizeUnit.name() + " (" + nameX + "," + nameY + ")";


        return text;
    }	



    public String getASCIIHeader() {
        return 
                "# CRUSH version: " + CRUSH.getFullVersion() +
                "# Instrument: " + instrument.getName() + "\n" +
                "# Scan: " + getID() + "\n" +
                "# Object: " + getSourceName() + "\n" +
                "# Date: " + timeStamp + " (MJD: " + getMJD() + ")\n" +
                "# Project: " + project + "\n";
    }


    @Override
    public String toString() { return "Scan " + getID(); }

    @SuppressWarnings("unchecked")
    public void segmentTo(double segmentTime) {
        if(size() > 1) mergeIntegrations();

        IntegrationType merged = get(0);
        clear();

        int nT = merged.framesFor(segmentTime);
        int N = ExtraMath.roundupRatio(merged.size(), nT);

        if(N <= 1) return;

        ensureCapacity(N);

        info("Segmenting into " + N + " integrations.");

        clear();

        for(int i=0, t=0; i<N; i++) {
            Integration<Frame> integration = (Integration<Frame>) merged.cloneWithCopyOf(merged.getInstrument());
            integration.clear();
            integration.integrationNo = i;
            
            int nk = Math.min(merged.size() - t, nT);
            integration.ensureCapacity(nk);

            for(int k=0; k<nk; k++,t++) integration.add(merged.get(t));
            integration.reindex();
            add((IntegrationType) integration);
        }
    }


    @Override
    public void info(String message) { CRUSH.info(this, message); }

    @Override
    public void notify(String message) { CRUSH.notify(this, message); }

    @Override
    public void debug(String message) { CRUSH.debug(this, message); }

    @Override
    public void warning(String message) { CRUSH.warning(this, message); }

    @Override
    public void warning(Exception e, boolean debug) { CRUSH.warning(this, e, debug); }

    @Override
    public void warning(Exception e) { CRUSH.warning(this, e); }

    @Override
    public void error(String message) { CRUSH.error(this, message); }

    @Override
    public void error(Throwable e, boolean debug) { CRUSH.error(this, e, debug); }

    @Override
    public void error(Throwable e) { CRUSH.error(this, e); }


    public static Scan<?> getEarliest(Collection<Scan<?>> scans) {
        double firstMJD = Double.POSITIVE_INFINITY;
        Scan<?> first = null;

        for(Scan<?> scan : scans) if(scan != null) if(scan.getMJD() < firstMJD) {
            first = scan;
            firstMJD = scan.getMJD();
        }
        return first;
    }

    public static Scan<?> getLatest(Collection<Scan<?>> scans) {
        double lastMJD = Double.NEGATIVE_INFINITY;
        Scan<?> last = null;

        for(Scan<?> scan : scans) if(scan != null) if(scan.getMJD() > lastMJD) {
            last = scan;
            lastMJD = scan.getMJD();
        }
        return last;
    }

}
