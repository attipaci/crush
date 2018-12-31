/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.hawcplus;

import java.io.File;
import java.util.List;

import crush.CRUSH;
import crush.Channel;
import crush.Dependents;
import crush.Frame;
import crush.fits.HDURowReader;
import crush.telescope.sofia.SofiaChopperData;
import crush.telescope.sofia.SofiaIntegration;
import jnum.Unit;
import jnum.Util;
import jnum.astro.*;
import jnum.math.Vector2D;
import nom.tam.fits.*;
import nom.tam.util.ArrayDataInput;

public class HawcPlusIntegration extends SofiaIntegration<HawcPlus, HawcPlusFrame> {	
    /**
     * 
     */
    private static final long serialVersionUID = -3894220792729801094L;

    private boolean fixJumps = false, fixSubarray[] = new boolean[HawcPlus.subarrays];
    private int minJumpLevelFrames = 0;

    private Dependents driftParms;

    public HawcPlusIntegration(HawcPlusScan parent) {
        super(parent);
    }	

    @Override
    public HawcPlusFrame getFrameInstance() {
        return new HawcPlusFrame((HawcPlusScan) scan);
    }

    public double getMeanHWPAngle() {
        return 0.5 * (getFirstFrame().hwpAngle + getLastFrame().hwpAngle);
    }
  
    protected void read(List<BinaryTableHDU> dataHDUs) throws Exception {	
        int records = 0;
        for(BinaryTableHDU hdu : dataHDUs) records += hdu.getAxes()[0];

        instrument.info("Processing scan data:");
        info("Reading " + records + " frames from " + dataHDUs.size() + " HDU(s).");
        info("Sampling at " + Util.f2.format(1.0 / instrument.integrationTime) + " Hz ---> " 
                + Util.f1.format(instrument.samplingInterval * records / Unit.min) + " minutes.");

        clear();
        ensureCapacity(records);
        for(int t=records; --t>=0; ) add(null);

        for(int i=0; i<dataHDUs.size(); i++) 
            new HawcPlusRowReader(dataHDUs.get(i), ((HawcPlusScan) scan).fits.getStream()).read(1);	
    }

    class HawcPlusRowReader extends HDURowReader { 
        private int iSN=-1, iDAC=-1, iJump=-1, iTS=-1;
        private int iAZ=-1, iEL=-1, iRA=-1, iDEC=-1, iAVPA=-1, iTVPA=-1, iCVPA=-1;
        private int iLON=-1, iLAT=-1, iLST=-1, iPWV=-1, iORA=-1, iODEC=-1;
        private int iChopR=-1, iChopS=-1, iHWP=-1, iStat=-1;
        private int iLOS=-1, iRoll=-1;

        private boolean isLab;
        private boolean isConfigured = false;

        private boolean invertChop = false;

        private final HawcPlusScan hawcPlusScan = (HawcPlusScan) scan;

        public HawcPlusRowReader(BinaryTableHDU hdu, ArrayDataInput in) throws FitsException {
            super(hdu, in);

            isLab = hasOption("lab");

            invertChop = hasOption("chopper.invert");

            // The Sofia timestamp (decimal seconds since 0 UTC 1 Jan 1970...
            iTS = hdu.findColumn("Timestamp");   
            iSN = hdu.findColumn("FrameCounter");

            iJump = hdu.findColumn("FluxJumps");
            iDAC = hdu.findColumn("SQ1Feedback");

            // HWP may be used in the future if support is extended for
            // scan-mode polarimetry (or polarimetry, in general...
            iHWP = hdu.findColumn("hwpCounts");

            // Ignore coordinate info for 'lab' data...
            if(isLab) {
                info("Lab mode data reduction. Ignoring telescope data...");
                return;
            }

            iStat = hdu.findColumn("Flag");

            iAZ = hdu.findColumn("AZ");
            iEL = hdu.findColumn("EL");

            // The tracking center in the basis coordinates of the scan (usually RA/DEC)
            iRA = hdu.findColumn("RA");
            iDEC = hdu.findColumn("DEC");

            if(scan.isNonSidereal) {
                iORA = hdu.findColumn("NonSiderealRA");
                iODEC = hdu.findColumn("NonSiderealDec");
            }

            iLST = hdu.findColumn("LST");

            iAVPA = hdu.findColumn("SIBS_VPA");
            iTVPA = hdu.findColumn("TABS_VPA");
            iCVPA = hdu.findColumn("Chop_VPA");

            iLON = hdu.findColumn("LON");
            iLAT = hdu.findColumn("LAT");  

            iChopR = hdu.findColumn("sofiaChopR");
            iChopS = hdu.findColumn("sofiaChopS");

            iPWV = hdu.findColumn("PWV");
            
            iLOS = hdu.findColumn("LOS");
            iRoll = hdu.findColumn("ROLL");
        }

        private synchronized void configure(Object[] row) {
            if(isConfigured) return;

            int storeRows = ((int[][]) row[iDAC]).length;
            int storeCols = ((int[][]) row[iDAC])[0].length;
            info("FITS has " + storeRows + "x" + storeCols + " arrays.");

            if(scan.equatorial == null) 
                scan.equatorial = new EquatorialCoordinates(((double[]) row[iRA])[0] * Unit.hourAngle, ((double[]) row[iDEC])[0] * Unit.deg, CoordinateEpoch.J2000);

            if(iORA >= 0) if(Double.isNaN(((double[]) row[iORA])[0])) {
                iORA = iODEC = -1;
                if(scan.isNonSidereal) warning("Missing NonSiderealRA/NonSiderealDEC columns. Forcing sidereal mapping.");
                scan.isNonSidereal = false;        
            } 

            isConfigured = true;
        }


        @Override
        public Reader getReader() {
            return new Reader() {   
                private AstroTime timeStamp;
                private EquatorialCoordinates apparent;
                private CoordinateEpoch epoch;

                @Override
                public void init() {
                    super.init();

                    timeStamp = new AstroTime();
                    apparent = new EquatorialCoordinates(); 
                    epoch = ((HawcPlusScan) scan).telescope.epoch;
                }

                @Override
                public void processRow(int i, Object[] row) {                    
                    HawcPlus hawc = scan.instrument;

                    // Create the frame object only if it cleared the above hurdles...
                    final HawcPlusFrame frame = new HawcPlusFrame(hawcPlusScan);
                    frame.index = i;
                    frame.isComplete = false;
                    frame.hasTelescopeInfo = !isLab;

                    // Read the pixel data (DAC and MCE jump counter)
                    frame.parseData((int[][]) row[iDAC], (short[][]) row[iJump]);
                    frame.mceSerial = iSN < 0 ? 0 : ((long[]) row[iSN])[0];

                    frame.utc = ((double[]) row[iTS])[0];
                    timeStamp.setUTC(frame.utc);
                    frame.MJD = timeStamp.getMJD();

                    frame.hwpAngle = iHWP < 0 ? 0.0F : (float) (((int[]) row[iHWP])[0] * HawcPlus.hwpStep - hawc.hwpTelescopeVertical);

                    set(i, frame);

                    if(frame.hasTelescopeInfo) frame.hasTelescopeInfo = !Double.isNaN(((double[]) row[iRA])[0]);

                    if(!frame.hasTelescopeInfo) {
                        if(isLab) frame.isComplete = true;
                        return;
                    }

                    // ======================================================================================
                    // Below here is telescope data only, which will be ignored for 'lab' mode reductions...
                    // Add the astrometry...
                    // ======================================================================================

                    frame.status = ((int[]) row[iStat])[0];       

                    if(!isConfigured) configure(row);

                    final double pwv = ((double[]) row[iPWV])[0];
                    frame.PWV = pwv < 0.0 ? Double.NaN : pwv * Unit.um;
                    
                    frame.site = new GeodeticCoordinates(
                            iLON < 0 ? 0.0 : ((double[]) row[iLON])[0] * Unit.deg, 
                            iLAT < 0 ? 0.0 :((double[]) row[iLAT])[0] * Unit.deg
                    );  
                    frame.LST = ((double[]) row[iLST])[0] * (float) Unit.hour;

                    frame.equatorial = new EquatorialCoordinates(
                            ((double[]) row[iRA])[0] * Unit.hourAngle, 
                            ((double[]) row[iDEC])[0] * Unit.deg, 
                            epoch
                            );                             

                    if(scan.isNonSidereal && iORA >= 0 && iODEC >= 0) {
                        frame.objectEq = new EquatorialCoordinates(
                                ((double[]) row[iORA])[0] * Unit.hourAngle, 
                                ((double[]) row[iODEC])[0] * Unit.deg, 
                                epoch
                                );
                    }

                    EquatorialCoordinates reference = scan.isNonSidereal ? frame.objectEq : scan.equatorial;

                    // I  -> T      rot by phi (instrument rotation)
                    // T' -> E      rot by -theta_ta
                    // T  -> H      rot by ROF
                    // H  -> E'     rot by PA
                    // I' -> E      rot by -theta_si
                    //
                    // T -> H -> E': theta_ta = ROF + PA
                    //
                    //    PA = theta_ta - ROF
                    //
                    // I -> T -> E': theta_si = phi - theta_ta
                    //
                    //    phi = theta_si - theta_ta
                    //
                    frame.instrumentVPA = ((double[]) row[iAVPA])[0] * Unit.deg;
                    frame.telescopeVPA = ((double[]) row[iTVPA])[0] * Unit.deg;
                    frame.chopVPA = ((double[]) row[iCVPA])[0] * Unit.deg;

                    // rotation from pixel coordinates to telescope coordinates...  
                    frame.setRotation(frame.instrumentVPA - frame.telescopeVPA);

                    // rotation from telescope coordinates to equatorial.
                    frame.setParallacticAngle(frame.telescopeVPA);

                    // Calculate the scanning offsets...
                    frame.horizontalOffset = frame.equatorial.getNativeOffsetFrom(reference);
                    frame.equatorialNativeToHorizontal(frame.horizontalOffset);

                    // In telescope XEL (phiS), EL (phiR)
                    frame.chopperPosition = new Vector2D(
                            iChopS < 0 ? 0.0 : -((float[]) row[iChopS])[0] * Unit.V, 
                            iChopR < 0 ? 0.0 : -((float[]) row[iChopR])[0] * Unit.V
                    );

                    // TODO empirical scaling...
                    frame.chopperPosition.scale(SofiaChopperData.volts2Angle);

                    if(invertChop) frame.chopperPosition.invert();
      
                    // Rotate the chopper offset into the TA frame...
                    // C -> E' rot by theta_cp
                    // T -> E' rot by theta_ta
                    // C -> T rot by theta_cp - theta_ta
                    frame.chopperPosition.rotate(frame.chopVPA - frame.telescopeVPA);
                   
                    // TODO if MCCS fixes alt/az inconsistency then we can just rely on their data...
                    //frame.horizontal = new HorizontalCoordinates(((double[]) row[iAZ])[0] * Unit.deg, ((double[]) row[iEL])[0] * Unit.deg);                
                    //frame.telescopeCoords = new TelescopeCoordinates(frame.horizontal);

                    // If the longitude/latitude data is missing then do not attempt to
                    // calculate horizontal coordinates...
                    if(!Double.isNaN(frame.site.longitude())) {
                        // Calculate AZ/EL -- the values in the table are noisy aircraft values...  
                        apparent.copy(frame.equatorial);
                        scan.toApparent.precess(apparent);
                        frame.horizontal = apparent.toHorizontal(frame.site, frame.LST);
                    }
                    else if(iAZ >= 0 && iEL >= 0) {
                        frame.horizontal = new HorizontalCoordinates(((double[]) row[iAZ])[0] * Unit.deg, ((double[]) row[iEL])[0] * Unit.deg);
                    }
                    
                    frame.LOS = iLOS < 0 ? 0.0F : (float) (((double[]) row[iLOS])[0] * Unit.deg);
                    frame.roll = iRoll < 0 ? 0.0F : (float) (((double[]) row[iRoll])[0] * Unit.deg);
                    
                    frame.isComplete = true;
                }

            };
        }
    }   


    @Override
    public void writeProducts() {
        super.writeProducts();

        if(hasOption("write.flatfield")) {
            String fileName = option("write.flatfield").getValue();
            if(fileName.isEmpty()) fileName = instrument.getOutputPath() + File.separator + "flatfield-" + getDisplayID() + ".fits";
            try { instrument.writeFlatfield(fileName); }
            catch(Exception e) { error(e); }
        }
    }

    @Override
    public String getFullID(String separator) {
        return scan.getID();
    }

    @Override
    public void removeDrifts(final int targetFrameResolution, final boolean robust) {
        fixJumps = hasOption("fixjumps");

        fixSubarray[HawcPlus.R0] =  hasOption("fixjumps.r0");
        fixSubarray[HawcPlus.R1] =  hasOption("fixjumps.r1");
        fixSubarray[HawcPlus.T0] =  hasOption("fixjumps.t0");
        fixSubarray[HawcPlus.T1] =  hasOption("fixjumps.t1");

        minJumpLevelFrames = framesFor(10.0 * getPointCrossingTime());

        driftParms = getDependents("drifts");

        super.removeDrifts(targetFrameResolution, robust);
    }

    @Override
    public void validate() {  
        if(hasOption("chopper.shift")) shiftChopper(option("chopper.shift").getInt());
  
        flagZeroedChannels();
        checkJumps();
        
        if(hasOption("jumpdata")) correctJumps();
        
        if(hasOption("gyrocorrect")) ((HawcPlusScan) scan).gyroDrifts.correct(this);
        
        super.validate();
    }
    
    @Override
    public void setTau() throws Exception {
        super.setTau();
        printEquivalentTaus(zenithTau);
    }
    
    private void printEquivalentTaus(double value) { 
        CRUSH.values(this, "--->"
                + " tau(" + Util.f0.format(instrument.instrumentData.wavelength/Unit.um) + "um):" + Util.f3.format(value)
                + ", tau(LOS):" + Util.f3.format(value / scan.horizontal.sinLat())
                + ", PWV:" + Util.f1.format(getTau("pwv", value)) + "um"
        );      
    }

    private void checkJumps() {
        final byte[] startCounter = getFirstFrame().jumpCounter;   

        if(startCounter == null) {
            warning("Scan has no jump counter data...");
            return;
        }
        
        info("Checking for flux jumps... ");
        
        new Fork<Void>() {        
            @Override
            protected void process(HawcPlusFrame frame) {
                for(int k=startCounter.length; --k >= 0; ) 
                    if(frame.jumpCounter[k] != startCounter[k]) instrument.get(k).hasJumps = true;
            }

        }.process();

        int jumpPixels = 0;
        for(HawcPlusPixel pixel : instrument) if(pixel.hasJumps) jumpPixels++;

        info("---> " + (jumpPixels > 0 ? "found jump(s) in " + jumpPixels + " pixels." : "All good!"));
    }

    @SuppressWarnings("cast")
    private void flagZeroedChannels() {
        info("Flagging zeroed channels... ");
        
        // TODO
        // This cast, while seemingly unnecessary, is needed to avoid VerifyError when compiling with javac.
        // Alas, Eclipse compiles is just fine without the explicit cast, as expected...
        ((HawcPlus) instrument).new Fork<Void>() {
            @Override
            protected void process(final HawcPlusPixel channel) {
                channel.flag(Channel.FLAG_DISCARD);
                for(final Frame exposure : HawcPlusIntegration.this) if(exposure != null) if(exposure.data[channel.index] != 0.0) {
                    channel.unflag(Channel.FLAG_DISCARD);
                    return;
                }
            }
        }.process();
        
        for(Channel channel : instrument) if(channel.isFlagged(Channel.FLAG_DISCARD)) channel.flag(Channel.FLAG_DEAD);
    }

    @Override
    protected boolean checkConsistency(final Channel channel, final int from, final int to, float[] frameParms) {
        boolean isOK = super.checkConsistency(channel, from, to, frameParms);

        HawcPlusPixel pixel = (HawcPlusPixel) channel;

        if(pixel.hasJumps) {
            if(fixJumps) isOK &= fixJumps(channel, from, to, frameParms);    
            else if(fixSubarray[pixel.sub]) isOK &= fixJumps(channel, from, to, frameParms);
        }

        return isOK;
    }

    private boolean fixJumps(final Channel channel, int from, final int to, float[] frameParms) { 
        //int clearFlag = ~HawcPlusFrame.SAMPLE_PHI0_JUMP;
        byte jumpStart = (byte) 0;   

        HawcPlusFrame first = getFirstFrameFrom(from);
        jumpStart = first.jumpCounter[channel.index];
        from = first.index;

        int n = 0;
        for(int t=from; t < to; t++) {
            final HawcPlusFrame exposure = get(t);
            if(exposure == null) continue;

            // Once flagged, stay flagged...
            //exposure.sampleFlag[channel.index] &= clearFlag;

            if(exposure.jumpCounter[channel.index] == jumpStart) continue;

            fixBlock(channel, from, t, frameParms);
            n++;

            // Make jumpStart ready for the next block
            from = t;
            jumpStart = exposure.jumpCounter[channel.index];
        }

        if(from != first.index) {
            fixBlock(channel, from, to, frameParms);
            n++;
        }

        return n == 0;
    }

    private void fixBlock(Channel channel, final int from, int to, float[] frameParms) {
        if(to-from < minJumpLevelFrames) flagBlock(channel, from, to, HawcPlusFrame.SAMPLE_PHI0_JUMP);
        else levelBlock(channel, from, to, frameParms);
    }  

    private void flagBlock(final Channel channel, final int from, int to, int pattern) {
        while(--to >= from) {
            final Frame exposure = get(to);
            if(exposure != null) exposure.sampleFlag[channel.index] |= pattern;
        }
    }

    private void levelBlock(final Channel channel, final int from, final int to, float[] frameParms) {
        double sum = 0.0, sumw = 0.0;

        int excludeSamples = ~Frame.SAMPLE_SOURCE_BLANK;

        for(int t=to; --t >= from; ) {
            final Frame exposure = get(t);
            if(exposure == null) continue;
            if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
            if((exposure.sampleFlag[channel.index] & excludeSamples) != 0) continue;

            sum += exposure.relativeWeight * exposure.data[channel.index];
            sumw += exposure.relativeWeight;
        }
        if(sumw == 0.0) return;

        driftParms.addAsync(channel, 1.0);

        final float ave = (float) (sum / sumw);

        for(int t=to; --t >= from; ) {
            final Frame exposure = get(t);
            if(exposure == null) continue;
            exposure.data[channel.index] -= ave;

            if(exposure.isFlagged(Frame.MODELING_FLAGS)) continue;
            if((exposure.sampleFlag[channel.index] & excludeSamples) != 0) continue;

            frameParms[t] += exposure.relativeWeight / sumw;
        }
    }

    @Override
    public Object getTableEntry(String name) {
        if(name.equals("hwp")) return getMeanHWPAngle();
        if(name.equals("pwv")) return getMeanPWV() / Unit.um;
        return super.getTableEntry(name);
    }
    

    private void shiftChopper(int n) {
        if(n == 0) return;

        info("Shifting chopper signal by " + n + " frames.");

        if(n > 0) {
            for(int t=size(); --t >= n; ) get(t).chopperPosition = get(t-n).chopperPosition;
            for(int t=n; --t >= 0; ) set(t, null);
        }
        else {
            final int nt = size();
            for(int t=0; t<nt; t++) get(t).chopperPosition = get(t+n).chopperPosition;
            for(int t=nt-n; t<nt; t++) set(t, null);
        }
    }


    private void correctJumps() {
        info("Flux-jumps correcting...");    
        
        final HawcPlusFrame first = getFirstFrame();
        final int maxJump = HawcPlusFrame.JUMP_RANGE >> 1;
        
        
        new Fork<Void>() {
            @Override
            protected void process(HawcPlusFrame frame) {
                for(HawcPlusPixel pixel : instrument) if(pixel.jump != 0.0) {
                    int nJumps = frame.jumpCounter[pixel.index] - first.jumpCounter[pixel.index];
                    // Check for wraparound...
                    if(nJumps > maxJump) nJumps -= HawcPlusFrame.JUMP_RANGE;
                    else if(nJumps < -maxJump) nJumps += HawcPlusFrame.JUMP_RANGE;
                    
                    frame.data[pixel.index] -= pixel.jump * nJumps;
                }
            } 
        }.process();
        
    }

  
  
}
