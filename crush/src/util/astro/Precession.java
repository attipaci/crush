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
// TODO precess with proper motion...
// TODO nutation correction...


package util.astro;

import util.SimpleMatrix;
import util.Unit;

public class Precession {
	private CoordinateEpoch fromEpoch, toEpoch;
	private float[][] P = new float[3][3];
	private float[] v = new float[3];
	private double[] l = new double[3];
	private static float year2Century = (float) (Unit.year / Unit.julianCentury);
	private static float arcsec = (float) Unit.arcsec;
	
	public Precession(double fromJulianEpoch, double toJulianEpoch) {
		this(new JulianEpoch(fromJulianEpoch), new JulianEpoch(toJulianEpoch));
	}
	
	public Precession(CoordinateEpoch from, CoordinateEpoch to) {
		fromEpoch = from;
		toEpoch = to;
		if(fromEpoch.equals(toEpoch)) P = null;
		else getMatrix();
	}

	//  Precession from Lederle & Schwan, Astronomy and Astrophysics, 134, 1-6 (1984)
	private void getMatrix() {
		float fromJulianYear = (float) fromEpoch.getJulianYear();
		float toJulianYear = (float) toEpoch.getJulianYear();
		
		final float tau = (fromJulianYear - 2000.0F) * year2Century;
		final float t = (toJulianYear - fromJulianYear) * year2Century;

		final float eta = (2305.6997F + (1.39744F + 0.000060F * tau) * tau 
				+ (0.30201F - 0.000270F * tau + 0.017996F * t) * t) * t * arcsec;

		final float z = (2305.6997F + (1.39744F + 0.000060F * tau) * tau 
				+ (1.09543F + 0.000390F * tau + 0.018326F * t) * t) * t * arcsec;

		final float theta = (2003.8746F - (0.85405F + 0.000370F * tau) * tau
				- (0.42707F + 0.000370F * tau + 0.041803F * t) * t) * t * arcsec;	

		P = R3(-z).dot(R2(theta)).dot(R3(-eta)).value;
	}
	
	
	public void precess(EquatorialCoordinates equatorial) {		
		if(P == null) return;
		
		v[0] = (float) (equatorial.cosLat * Math.cos(equatorial.RA())); 
		v[1] = (float) (equatorial.cosLat * Math.sin(equatorial.RA()));
		v[2] = (float) equatorial.sinLat;

		for(int i=l.length; --i >= 0; ) {
			float sum = 0.0F;
			final float[] Pi = P[i];
			for(int j=v.length; --j >= 0; ) sum += Pi[j] * v[j];
			l[i] = sum;
		}
	
		double ml = Math.hypot(l[0], l[1]);

		equatorial.setRA(Math.atan2(l[1], l[0]));
		equatorial.setDEC(Math.atan2(l[2], ml));

		equatorial.epoch = toEpoch;
	}

	/*
	private FloatMatrix R1(double phi) {
		final float c = (float) Math.cos(phi);
		final float s = (float) Math.sin(phi);

		float[][] R = { {  1, 0, 0 }, 
				{  0, c, s },
				{  0, -s, c } };	
		return new FloatMatrix(R);
	}
	*/
	
	private SimpleMatrix R2(final double phi) {
		final float c = (float) Math.cos(phi);
		final float s = (float) Math.sin(phi);

		float[][] R = { {  c, 0,-s }, 
				{  0, 1, 0 },
				{  s, 0, c } };	
		return new SimpleMatrix(R);
	}

	private SimpleMatrix R3(double phi) {
		final float c = (float) Math.cos(phi);
		final float s = (float) Math.sin(phi);

		float[][] R = { {  c, s, 0 }, 
				{ -s, c, 0 },
				{  0, 0, 1 } };	
		return new SimpleMatrix(R);		
	}
	
	
}
