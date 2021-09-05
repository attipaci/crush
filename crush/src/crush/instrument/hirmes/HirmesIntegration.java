/* *****************************************************************************
 * Copyright (c) 2021 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs  - initial API and implementation
 ******************************************************************************/

package crush.instrument.hirmes;

import java.util.List;
import java.util.stream.IntStream;

import crush.CRUSH;
import crush.Channel;
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

class HirmesIntegration extends SofiaIntegration<HirmesFrame> {    
    /**
     * 
     */
    private static final long serialVersionUID = -7898186476366677656L;
    

    HirmesIntegration(HirmesScan parent) {
        super(parent);
    }   
    
    @Override
    public HirmesScan getScan() { return (HirmesScan) super.getScan(); }

    @Override
    public Hirmes getInstrument() { return (Hirmes) super.getInstrument(); }
    
    @Override
    public HirmesFrame getFrameInstance() {
        return new HirmesFrame(this);
    }


    @SuppressWarnings("resource")
    void read(List<BinaryTableHDU> dataHDUs) throws Exception {   
        int records = 0;
        
        for(BinaryTableHDU hdu : dataHDUs) records += hdu.getAxes()[0];

        getInstrument().info("Processing scan data:");
        info("Reading " + records + " frames from " + dataHDUs.size() + " HDU(s).");
        info("Sampling at " + Util.f2.format(1.0 / getInstrument().integrationTime) + " Hz ---> " 
                + Util.f1.format(getInstrument().samplingInterval * records / Unit.min) + " minutes.");

        clear();
        ensureCapacity(records);
        IntStream.range(0, records).forEach(t -> add(null));
       
        for(int i=0; i<dataHDUs.size(); i++)
            new HirmesRowReader(dataHDUs.get(i), getScan().fits.getStream()).read(1); 
    }

    private class HirmesRowReader extends HDURowReader { 
        private int iSN=-1, iDAC=-1, iJump=-1, iTS=-1;
        private int iAZ=-1, iEL=-1, iRA=-1, iDEC=-1, iAVPA=-1, iTVPA=-1, iCVPA=-1;
        private int iLON=-1, iLAT=-1, iLST=-1, iPWV=-1, iORA=-1, iODEC=-1;
        private int iChopR=-1, iChopS=-1, iStat=-1, iCal = -1;
        private int iLOS=-1, iRoll=-1;

        private boolean isLab;
        private boolean isConfigured = false;

        private boolean invertChop = false;

        private final HirmesScan hirmesScan = getScan();

        HirmesRowReader(BinaryTableHDU hdu, ArrayDataInput in) throws FitsException {
            super(hdu, in);

            isLab = hasOption("lab");

            invertChop = hasOption("chopper.invert");

            // The Sofia timestamp (decimal seconds since 0 UTC 1 Jan 1970...
            iTS = findColumn("Timestamp");   
            iSN = findColumn("FrameCounter");

            iJump = findColumn("FluxJumps");
            iDAC = findColumn("SQ1Feedback");

            // Ignore coordinate info for 'lab' data...
            if(isLab) {
                info("Lab mode data reduction. Ignoring telescope data...");
                return;
            }

            iStat = findColumn("Flag");
            iCal = findColumn("calibratorChopState");
            
            iAZ = findColumn("AZ");
            iEL = findColumn("EL");

            // The tracking center in the basis coordinates of the scan (usually RA/DEC)
            iRA = findColumn("RA");
            iDEC = findColumn("DEC");

            if(hirmesScan.isNonSidereal) {
                iORA = findColumn("NonSiderealRA");
                iODEC = findColumn("NonSiderealDec");
            }

            iLST = findColumn("LST");

            iAVPA = findColumn("SIBS_VPA");
            iTVPA = findColumn("TABS_VPA");
            iCVPA = findColumn("Chop_VPA");

            iLON = findColumn("LON");
            iLAT = findColumn("LAT");  

            iChopR = findColumn("sofiaChopR");
            iChopS = findColumn("sofiaChopS");

            iPWV = findColumn("PWV");
            
            iLOS = findColumn("LOS");
            iRoll = findColumn("ROLL");
        }

        /**
         * Used for supporting both old simulation files (all caps column names) 
         * and new simulation and CDH files (mixed case column names).
         * 
         * 
         * @param colName   The standard mixed case column name used by CDH (e.g. <code>NonSiderealRA</code>).
         * @return          FITS column index (zero-based), or -1 if no column name matches.
         */
        private int findColumn(String colName) {   
            int idx = hdu.findColumn(colName);
            return idx < 0 ? hdu.findColumn(colName.toUpperCase()) : idx;
        }
        
        private synchronized void configure(Object[] row) {
            if(isConfigured) return;

            int storeRows = ((int[][]) row[iDAC]).length;
            int storeCols = ((int[][]) row[iDAC])[0].length;
            info("FITS has " + storeRows + "x" + storeCols + " arrays.");

            if(getScan().equatorial == null) 
                getScan().equatorial = new EquatorialCoordinates(((double[]) row[iRA])[0] * Unit.hourAngle, ((double[]) row[iDEC])[0] * Unit.deg, EquatorialSystem.FK5.J2000);

            if(iORA >= 0) if(Double.isNaN(((double[]) row[iORA])[0])) {
                iORA = iODEC = -1;
                if(hirmesScan.isNonSidereal) warning("Missing NonSiderealRA/NonSiderealDEC columns. Forcing sidereal mapping.");
                hirmesScan.isNonSidereal = false;        
            } 

            isConfigured = true;
        }


        @Override
        public Reader getReader() {
            return new Reader() {   
                private AstroTime timeStamp;
                private EquatorialCoordinates apparent;
                private EquatorialSystem system;

                @Override
                public void init() {
                    super.init();

                    timeStamp = new AstroTime();
                    apparent = new EquatorialCoordinates(); 
                    system = hirmesScan.telescope.system;
                }

                @Override
                public void processRow(int i, Object[] row) {
                    // Create the frame object only if it cleared the above hurdles...
                    final HirmesFrame frame = getFrameInstance();
                    frame.index = i;
                    frame.isComplete = false;
                    frame.hasTelescopeInfo = !isLab;

                    // Read the pixel data (DAC and MCE jump counter)
                    frame.parseData((int[][]) row[iDAC], iJump < 0 ? null : (short[][]) row[iJump]);
                    frame.mceSerial = iSN < 0 ? 0L : ((long[]) row[iSN])[0];
   
                    frame.utc = iTS < 0 ? i * getInstrument().samplingInterval : ((double[]) row[iTS])[0];
                    timeStamp.setUNIXTime(frame.utc);
                    frame.MJD = timeStamp.MJD();
                        
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

                    frame.status = iStat < 0 ? 0 : ((int[]) row[iStat])[0];
                    frame.qclState = iCal < 0 ? false : ((byte[]) row[iCal])[0] > 0;

                    if(!isConfigured) configure(row);

                    final double pwv = iPWV < 0 ? 0.0 : ((double[]) row[iPWV])[0];
                    frame.PWV = pwv < 0.0 ? Double.NaN : pwv * Unit.um;
                    
                    frame.site = new GeodeticCoordinates(
                            iLON < 0 ? 0.0 : ((double[]) row[iLON])[0] * Unit.deg, 
                            iLAT < 0 ? 0.0 : ((double[]) row[iLAT])[0] * Unit.deg,
                            0.0                                                    // TODO actual altitude?
                    );
                            
                    frame.LST = ((double[]) row[iLST])[0] * (float) Unit.hour;

                    frame.equatorial = new EquatorialCoordinates(
                            ((double[]) row[iRA])[0] * Unit.hourAngle, 
                            ((double[]) row[iDEC])[0] * Unit.deg, 
                            system
                            );                             

                    if(hirmesScan.isNonSidereal && iORA >= 0 && iODEC >= 0) {
                        frame.objectEq = new EquatorialCoordinates(
                                ((double[]) row[iORA])[0] * Unit.hourAngle, 
                                ((double[]) row[iODEC])[0] * Unit.deg, 
                                system
                                );
                    }

                    EquatorialCoordinates reference = hirmesScan.isNonSidereal ? frame.objectEq : getScan().equatorial;
                    
                    // I  -> T      rot by phi (instrument rotation)
                    // T  -> E'     rot by -theta_ta
                    // T  -> H      rot by ROF   
                    // H  -> E'     rot by PA
                    // I  -> E'     rot by -theta_si
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
                    frame.setApparentParallacticAngle(frame.telescopeVPA);

                    // Calculate the scanning offsets...
                    frame.horizontalOffset = frame.equatorial.getOffsetFrom(reference);
                    frame.equatorialToHorizontal(frame.horizontalOffset);

                    // In telescope XEL (phiS), EL (phiR)
                    frame.chopperPosition = new Vector2D(
                            iChopS < 0 ? 0.0 : -((float[]) row[iChopS])[0] * Unit.V, 
                            iChopR < 0 ? 0.0 : -((float[]) row[iChopR])[0] * Unit.V
                    );

                    // TODO empirical scaling...
                    frame.chopperPosition.scale(SofiaChopperData.volts2Angle);

                    if(invertChop) frame.chopperPosition.flip();
      
                 // Rotate the chopper offset into the TA frame...
                    // C -> E' rot by -theta_cp
                    // E'-> T rot by theta_ta
                    // C -> T rot by heta_ta - theta_cp
                    frame.chopperPosition.rotate(frame.telescopeVPA - frame.chopVPA);
                   
                    // TODO if MCCS fixes alt/az inconsistency then we can just rely on their data...
                    //frame.horizontal = new HorizontalCoordinates(((double[]) row[iAZ])[0] * Unit.deg, ((double[]) row[iEL])[0] * Unit.deg);                
                    //frame.telescopeCoords = new TelescopeCoordinates(frame.horizontal);

                    // If the longitude/latitude data is missing then do not attempt to
                    // calculate horizontal coordinates...
                    if(!Double.isNaN(frame.site.longitude())) {
                        // Calculate AZ/EL -- the values in the table are noisy aircraft values...  
                        apparent.copy(frame.equatorial);
                        getScan().toApparent.transform(apparent);
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
    public String getFullID(String separator) {
        return getScan().getID();
    }

 
    @Override
    public void validate() {   
        flagZeroedChannels();
        checkJumps();       
        
        if(hasOption("gyrocorrect")) getScan().gyroDrifts.correct(this);
        
        super.validate(); 
    }
    
    @Override
    public void setTau() throws Exception {
        super.setTau();
        printEquivalentTaus(zenithTau);
    }
    
    private void printEquivalentTaus(double value) { 
        CRUSH.values(this, "--->"
                + " tau(" + Util.f0.format(getInstrument().instrumentData.wavelength/Unit.um) + "um):" + Util.f3.format(value)
                + ", tau(LOS):" + Util.f3.format(value / getScan().horizontal.sinLat())
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
            protected void process(HirmesFrame frame) {
                IntStream.range(0, startCounter.length).parallel()
                .filter(k -> frame.jumpCounter[k] != startCounter[k])
                .forEach(k -> getInstrument().get(k).hasJumps = true);
            }

        }.process();

        int jumpPixels = (int) getInstrument().stream().filter(p -> p.hasJumps).count();

        info("---> " + (jumpPixels > 0 ? "found jump(s) in " + jumpPixels + " pixels." : "All good!"));
    }


    private void flagZeroedChannels() {
        info("Flagging zeroed channels... ");
        
        getInstrument().new Fork<Void>() {
            @Override
            protected void process(final HirmesPixel channel) {
                channel.flag(Channel.FLAG_DISCARD);
                Frame bad = stream().filter(f -> f != null).filter(f -> f.data[channel.getIndex()] != 0.0).findFirst().orElse(null);
                if(bad == null) channel.unflag(Channel.FLAG_DISCARD);
                
            }
        }.process();
        
        getInstrument().parallelStream().filter(c -> c.isFlagged(Channel.FLAG_DISCARD)).forEach(c -> c.flag(Channel.FLAG_DEAD));
    }

  
    @Override
    public Object getTableEntry(String name) {
        if(name.equals("pwv")) return getMeanPWV() / Unit.um;
        return super.getTableEntry(name);
    }
  
  
}
