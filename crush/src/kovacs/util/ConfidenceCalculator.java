/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of kovacs.util.
 * 
 *     kovacs.util is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     kovacs.util is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with kovacs.util.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/
// (C)2007 Attila Kovacs <attila@submm.caltech.edu>

package kovacs.util;

public final class ConfidenceCalculator {
	public static double accuracy = 1.0e-6;

	public static double getProbabilityOutsideOf(double x) {
		x = Math.abs(x);

		double dx = 2.476 * Math.sqrt(accuracy);

		double Q = 0.0, x1;
		for(x1=maxX; x1 > x; x1-=dx) Q += getdI(x1, dx);	   	    
		Q += getdI(x, x1+dx-x);	    

		return Q;
	}    

	public static double getConfidence(double x) {
		return 1.0 - getProbabilityOutsideOf(x);
	}


	public static double getSigma(double confidence) {

		double dx = 2.476 * Math.sqrt(accuracy);

		double I=0.0, x1 = maxX, dI = 0.0;
		while((1.0-I) > confidence) {
			dI = getdI(x1, dx);
			I += dI;
			x1 -= dx;
		}
		x1 += dx * (1.0 + (confidence-(1.0-I))/dI);

		return x1;
	}    

	// Exponential integral bits... 
	private static double getdI(double x, double dx) {
		return x < 1e-3 ? P(x) * dx : P(x) / x * (1.0 - Math.exp(-x*dx));
	}


	private static double P(double x) {
		return A * Math.exp(-x*x / 2.0);
	}

	private static double A = Math.sqrt(0.5 / Math.PI);
	private static double maxX = 38.0;
}
