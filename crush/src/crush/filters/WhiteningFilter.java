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
package crush.filters;

import java.util.Arrays;

import crush.Channel;
import crush.Frame;
import crush.Integration;
import jnum.Configurator;
import jnum.ExtraMath;
import jnum.data.DataPoint;
import jnum.data.Statistics;
import jnum.math.Range;

public class WhiteningFilter extends AdaptiveFilter {
    /**
     * 
     */
    private static final long serialVersionUID = 1954923341731840023L;

    double level = 1.2;
    double significance = 2.0;

    private int nF; // The number of stacked frequency components
    private int windows; // The number of stacked windows...
    private int whiteFrom, whiteTo; // The frequency channel indexes of the white-level measuring range

    private int oneOverFBin;
    private int whiteNoiseBin;

    private DataPoint[] A, temp; // The amplitude at reduced resolution, and a temp storage for median

    public WhiteningFilter(Integration<?> integration) {
        super(integration);
    }

    public WhiteningFilter(Integration<?> integration, float[] data) {
        super(integration, data);
    }

    @Override
    public WhiteningFilter clone() {
        WhiteningFilter clone = (WhiteningFilter) super.clone();
        if(A != null) clone.A = DataPoint.createArray(A.length);
        if(temp != null) clone.temp = DataPoint.createArray(temp.length);
        return clone;
    }

    @Override
    protected void setIntegration(Integration<?> integration) {	
        super.setIntegration(integration);
    }

    @Override
    public void updateConfig() {	
        super.updateConfig();

        setSize(ExtraMath.pow2ceil(integration.framesFor(integration.filterTimeScale)));

        // Specify critical level as power, but use as amplitude...
        if(hasOption("level")) level = Math.max(1.0, Math.sqrt(option("level").getDouble()));

        int windowSize = ExtraMath.pow2ceil(2 * nF);
        if(nt < windowSize) windowSize = nt;
        windows = nt / windowSize;

        Range probe = new Range(0.0, nF * dF);

        if(hasOption("proberange")) { 
            Configurator spec = option("proberange");
            if(spec.getValue().equalsIgnoreCase("auto")) {
                if(integration.isPhaseModulated()) probe = new Range(integration.getModulationFrequency(Frame.TOTAL_POWER), nF * dF);
                else {
                    // The frequency cutoff (half-max) of the typical point-source response....
                    double fPnt = 0.44 / integration.getPointCrossingTime();
                    probe = new Range(0.2 * fPnt, 1.14 * fPnt);
                }
            }
            else probe = option("proberange").getRange(true);
        }

        probe.scale(1.0 / dF);
        probe.intersectWith(0.0, nF);

        whiteFrom = Math.max(1, (int) Math.floor(probe.min()));
        whiteTo = Math.min(nF, (int) Math.ceil(probe.max()) + 1);

        oneOverFBin = Math.min(nF, hasOption("1overf.freq") ? Math.max(1, (int) Math.floor(option("1overf.freq").getDouble() / dF)) : 2);
        whiteNoiseBin = Math.min(nF, hasOption("1overf.ref") ? Math.max(1, (int) Math.floor(option("1overf.ref").getDouble() / dF)) : nF>>1);		

        // Make sure the probing range is contains enough channels
        // and that the range is valid...
        int minProbeChannels = hasOption("minchannels") ? option("minchannels").getInt() : 16;
        if(whiteFrom > whiteTo - minProbeChannels + 1) whiteFrom = whiteTo - minProbeChannels + 1;
        if(whiteFrom < 0) whiteFrom = 0;
        if(whiteFrom > whiteTo - minProbeChannels + 1) whiteTo = Math.min(minProbeChannels + 1, nF);

    }

    @Override
    protected void setSize(int nF) {
        if(this.nF == nF) return;

        super.setSize(nF);

        this.nF = nF;

        A = DataPoint.createArray(nF);
        temp = new DataPoint[nF];

    }	

    @Override
    public void updateProfile(Channel channel) {
        calcMeanAmplitudes(channel);
        whitenProfile(channel);
    }

    private void calcMeanAmplitudes(Channel channel) {
        final int c = channel.getIndex();
        
        // If the filterResponse array does not exist, create it...
        if(channelProfiles[c] == null) {
            channelProfiles[c] = new float[nF];
            Arrays.fill(channelProfiles[c], 1.0F);
        }

        final float[] data = getTempData();

        // Get the coarse average spectrum...
        for(int F=nF; --F >= 0; ) {
            final int fromf = Math.max(2, 2 * F * windows);
            final int tof = Math.min(fromf + 2 * windows, data.length);

            double sumP = 0.0;
            int pts = 0;

            // Sum the power inside the spectral window...
            // Skip over channels that have been killed by KillFilter...
            for(int f=tof; --f >= fromf; ) if(data[f] != 0.0) {
                sumP += data[f] * data[f];
                pts++;
            }

            // Set the amplitude equal to the rms power...
            // The full power is the sum of real and imaginary components...
            A[F].setValue(pts > 0 ? Math.sqrt(2.0 * sumP / pts) : 0.0);
            A[F].setWeight(pts);
        }	

        // Add the Nyquist component to the last bin...
        // Skip if it has been killed by KillFilter...
        if(data[nF - 1] != 0.0) {
            final DataPoint nyquist = A[nF-1];
            nyquist.setValue(nyquist.weight() * nyquist.value() * nyquist.value() + 2.0 * data[1] * data[1]);
            nyquist.addWeight(1.0);
            nyquist.setValue(Math.sqrt(nyquist.value() / nyquist.weight()));
        }		

    }

    private void whitenProfile(Channel channel) {
        final float[] profile = getProfile();

        Arrays.fill(profile, 1.0F); // To be safe initialize the scaling array here...
        final float[] lastProfile = channelProfiles[channel.getIndex()];

        // Calculate the median amplitude
        System.arraycopy(A, 0, temp, 0, A.length);	
        final double medA = Statistics.Inplace.median(temp, whiteFrom, whiteTo).value();
        final double critical = level * medA;

        // sigmaP = medP / sqrt(pts)
        // A = sqrt(P)
        // dA = 0.5 / sqrtP * dP
        // sigmaA = 0.5 / medA * sigmaP = 0.5 * medA / sqrt(pts)
        // wA = 4 * pts / (medA * medA) 
        final double weightScale = 4.0 / (medA * medA);
        for(int F=nF; --F >= 0; ) A[F].scaleWeight(weightScale);

        // Only whiten those frequencies which have a significant excess power
        // when compared to the specified level over the median spectral power.
        for(int F=nF; --F >= 1; ) {
            if(A[F].weight() > 0.0) {
                final double dev = (A[F].value() / lastProfile[F] - critical) / A[F].rms();

                // Check if there is excess/deficit power that needs filtering...
                if(dev > significance) profile[F] = (float) (medA / A[F].value());

                // If there is no significant deviation, undo the last filtering...
                else profile[F] = 1.0F / lastProfile[F];

                A[F].scale(profile[F]);
            }
            else A[F].setValue(medA);
        }

        channel.oneOverFStat = profile[whiteNoiseBin] / profile[oneOverFBin];

    }



    @Override
    public String getID() {
        return "wh";
    }

    @Override
    public String getConfigName() {
        return "filter.whiten";
    }


}
