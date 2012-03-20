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
// Copyright (c) 2007 Attila Kovacs 

package util;

// TODO Convert to Enum?
public class Constant {

    // Mathematical Constants
    public final static double PI = Math.PI;
    public final static double twoPI = 2.0 * PI;
    public final static double rightAngle = 0.5 * PI;
    public final static double sqrt2 = Math.sqrt(2.0);
    public final static double sqrt3 = Math.sqrt(3.0);
    public final static double sqrt5 = Math.sqrt(5.0);
    public final static double goldenRatio = 0.5 * (1.0 + sqrt5);
    public final static double euler = 0.577215664901533;
    
    public final double fibonacci(int n) {
    	return (Math.pow(goldenRatio, n) - Math.pow(1.0 - goldenRatio, n)) / sqrt5;
    }
    
    // Physical Constants
    public final static double Planck = 6.626076e-34 * Unit.J * Unit.s;
    public final static double h = Planck;
    public final static double h2 = h * h;
    public final static double h3 = h2 * h;
    public final static double h4 = h3 * h;
    public final static double h5 = h4 * h;
    public final static double h6 = h5 * h;

    public final static double Dirac = h / twoPI;
    public final static double hbar = Dirac;
    public final static double hbar2 = hbar * hbar;
    public final static double hbar3 = hbar2 * hbar;
    public final static double hbar4 = hbar3 * hbar;
    public final static double hbar5 = hbar4 * hbar;
    public final static double hbar6 = hbar5 * hbar;
    
    public final static double c = 299792458.0 * Unit.m/Unit.s;
    public final static double c2 = c * c;
    public final static double c3 = c2 * c;
    public final static double c4 = c3 * c;
    public final static double c5 = c4 * c;
    public final static double c6 = c5 * c;

    public final static double e = 1.6022e-19 * Unit.C;
    public final static double Qe = e;
    public final static double qe = e;
    public final static double Q_e = e;
    public final static double q_e= e;
    public final static double e2 = e * e;
    public final static double e3 = e2 * e;
    public final static double e4 = e3 * e;
    public final static double e5 = e4 * e;
    public final static double e6 = e5 * e;

    public final static double Boltzmann = 1.380658e-23 * Unit.J/Unit.K;
    public final static double k = Boltzmann;
    public final static double kB = k;
    public final static double k_B = k;

    public final static double mu0 = 4.0e-7 * PI * Unit.H / Unit.m;
    public final static double mu_0 = 4.0e-7 * PI * Unit.H / Unit.m;
    public final static double u0 = mu0;
    public final static double u_0 = mu0;
    public final static double epsilon0 = 1.0 / (u0 * c2);
    public final static double epsilon_0 = epsilon0;
    public final static double eps0 = epsilon0;
    public final static double eps_0 = epsilon0;


    public final static double me = 9.1093897e-31 * Unit.kg;
    public final static double m_e = me;

    public final static double mp = 1.6726231e-27 * Unit.kg;
    public final static double m_p = mp;
    
    public final static double Avogadro = 6.0221367e23 / Unit.mol;
    public final static double NA = Avogadro;
    public final static double N_A = NA;
    public final static double L = NA;

    public final static double Loschmidt = 2.686763e25 / Unit.m3;
    public final static double NL = Loschmidt;
    public final static double N_L = NL;
    public final static double n0 = NL;
    public final static double n_0 = NL;

    public final static double Rgas = NA * k;

    public final static double Faraday = NA * e;
    public final static double F = Faraday;

    public final static double StefanBoltzmann = 6.67051e-8 * Unit.W / Unit.m2 / Unit.K4;
    public final static double sigma = StefanBoltzmann;

    public final static double fineStructure = e2 / (4.0 * eps0 * h * c);
    public final static double alpha = fineStructure;

    public final static double Rydberg = me * e4 / (8.0 * eps0 * eps0 * h3 * c);
    public final static double R = Rydberg;
    public final static double RH = mp / (me + mp) * Rydberg;

    public final static double G = 6.67259e-11 * Unit.N * Unit.m2 / Unit.kg2;
    public final static double g = 9.80665 * Unit.m / Unit.s2;


    // some constants of questionable accuracy

    public static double h0 = 0.65;
    public static double h_0 = h0;
    public static double Hubble = 100.0 * h0 * Unit.km / Unit.s / Unit.Mpc;
    public static double H0 = Hubble;
    public static double H_0 = Hubble;
    
    public static double Omega = 1.0;
    public static double Omega_M = 0.3;
    public static double Omega_Lambda = 0.7;    

}


