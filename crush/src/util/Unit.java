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

import java.util.*;

public class Unit implements Cloneable {
	protected static Hashtable<String,Unit> table = new Hashtable<String,Unit>();

	// Usage Examples:
	//    1. To convert radians to arcsecs : secAngle = radAngle / ArcSECONDS
	//    2. To convert arcsecs to radians : radAngle = secAngle * ArcSECONDS
	//    3. To convert degrees to hourangle : hourAngle = degAngle * DEGREES/HOURangle

	public String name = "";
	public double value = Double.NaN;

	public Unit() {}

	public Unit(boolean remember) { if(remember) add(name, this); }

	public Unit(String setName, double setValue) { value = setValue; name = setName; add(name, this); }

	public Unit(String setName, double setValue, boolean remember) { value = setValue; name = setName; if(remember) add(name, this); }

	public Unit(double setValue, String names) {
		StringTokenizer tokens = new StringTokenizer(names, ",; \t");
		value = setValue; name = tokens.nextToken();
		add(name, this);
		while(tokens.hasMoreTokens()) add(tokens.nextToken(), this);
	}

	public Unit(String text) { parse(text); }

	@Override
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }		
	}
	
	public void copy(Unit unit) {
		name = unit.name;
		value =	unit.value;
	}
	
	protected static void add(String id, Unit unit) {
		table.put(id, unit);
	}

	public static Unit get(String id) {
		return table.get(id);
	}

	// For now parser only knows units that have been explicitly set
	// and their multiples...
	public static Unit parseElemental(String expression) {
		Unit unit = get(expression);
		if(unit != null) return unit;
		unit = get(expression.substring(1));
		return unit == null ? null : new Unit(expression, getMultiplier(expression.charAt(0)) * unit.value, false);
	}

	// ---> need to implement....
	public void parse(String text) {

	}

	@Override
	public String toString() {
		return "[" + name + "] = " + value;
	}

	// Unit Prefixes
	public final static double deci = 0.1;
	public final static double centi = 1.0e-2;
	public final static double milli = 1.0e-3;
	public final static double micro = 1.0e-6;
	public final static double nano = 1.0e-9;
	public final static double pico = 1.0e-12;
	public final static double femto = 1.0e-15;
	public final static double atto = 1.0e-18;

	public final static double deka = 10.0;
	public final static double hecto = 100.0;
	public final static double kilo = 1.0e3;
	public final static double mega = 1.0e6;
	public final static double giga = 1.0e9;
	public final static double tera = 1.0e12;
	public final static double peta = 1.0e15;
	public final static double exa = 1.0e18;

	public static char getMultiplierChar(double value) {	
		if(value < 1e-15) return 'a';
		if(value < 1e-12) return 'f';
		if(value < 1e-9) return 'p';
		if(value < 1e-6) return 'n';
		if(value < 1e-3) return 'u';
		if(value < 1.0) return 'm';
		if(value < 1e3) return ' ';
		if(value < 1e6) return 'k';
		if(value < 1e9) return 'M';
		if(value < 1e12) return 'G';
		if(value < 1e15) return 'T';
		if(value < 1e18) return 'P';
		return 'E';
	}
	

	public static double getMultiplier(char c) {
		double multiple = Double.NaN;
		switch(c) {
		case ' ': multiple = 1.0; break;
		case 'd': multiple = deci; break; // could also be deka
		case 'c': multiple = centi; break;
		case 'm': multiple = milli; break;
		case 'u': multiple = micro; break;
		case 'n': multiple = nano; break;
		case 'p': multiple = pico; break;
		case 'f': multiple = femto; break;
		case 'a': multiple = atto; break;
		case 'h': multiple = hecto; break;
		case 'k': multiple = kilo; break;
		case 'M': multiple = mega; break;
		case 'G': multiple = giga; break;
		case 'T': multiple = tera; break;
		case 'P': multiple = peta; break;
		case 'E': multiple = exa; break;
		}
		return multiple;
	}

	// Basics (SI) and common scales

	public final static double metre = 1.0;
	public final static double meter = metre;
	public final static double m = metre;
	public final static double km = kilo * metre;
	public final static double dm = deci * metre;
	public final static double cm = centi * metre;
	public final static double mm = milli * metre;
	public final static double um = micro * metre;
	public final static double nm = nano * metre;

	public final static double m2 = m * m;    ;
	public final static double m3 = m2 * m;
	public final static double m4 = m3 * m;
	public final static double m5 = m4 * m;
	public final static double m6 = m5 * m;
	public final static double cm2 = cm * cm;
	public final static double cm3 = cm2 * cm;
	public final static double cm4 = cm3 * cm;
	public final static double cm5 = cm4 * cm;
	public final static double cm6 = cm5 * cm;


	public final static double kilogramm = 1.0;
	public final static double kg = kilogramm;
	public final static double kg2 = kg * kg;
	public final static double kg3 = kg2 * kg;
	public final static double kg4 = kg3 * kg;
	public final static double kg5 = kg4 * kg;
	public final static double kg6 = kg5 * kg;
	public final static double gramm = 0.001 * kg;
	public final static double g = gramm;
	public final static double g2 = g * g;
	public final static double g3 = g2 * g;
	public final static double g4 = g3 * g;
	public final static double g5 = g4 * g;
	public final static double g6 = g5 * g;
	public final static double dkg = deka * gramm;
	public final static double mg = milli * gramm;

	public final static double second = 1.0;
	public final static double s = second;
	public final static double sec = second;
	public final static double msec = milli * second;
	public final static double ms = msec;
	public final static double usec = micro * second;
	public final static double us = usec;
	public final static double nsec = nano * second;
	public final static double ns = nsec;
	public final static double psec = pico * second;
	public final static double ps = psec;
	public final static double fsec = femto * second;
	public final static double fs = fsec;

	public final static double sec2 = sec * sec;
	public final static double sec3 = sec2 * sec;
	public final static double sec4 = sec3 * sec;
	public final static double sec5 = sec4 * sec;
	public final static double sec6 = sec5 * sec;
	public final static double s2 = sec2;
	public final static double s3 = sec3;
	public final static double s4 = sec4;
	public final static double s5 = sec5;
	public final static double s6 = sec6;

	public final static double ampere = 1.0;
	public final static double amp = ampere;
	public final static double A = ampere;
	public final static double kA = kilo * ampere;
	public final static double mA = milli * ampere;
	public final static double uA = micro * ampere;
	public final static double nA = nano * ampere;
	public final static double pA = pico * ampere;

	public final static double kelvin = 1.0;
	public final static double K = kelvin;
	public final static double mK = milli * kelvin;
	public final static double uK = micro * kelvin;
	public final static double nK = nano * kelvin;
	public final static double K2 = K * K;
	public final static double K3 = K2 * K;
	public final static double K4 = K3 * K;
	public final static double K5 = K4 * K;
	public final static double K6 = K5 * K;

	public final static double mol = 1.0;
	public final static double mmol = milli * mol;
	public final static double umol = micro * 1.0;

	public final static double candela = 1.0;
	public final static double cd = candela;

	// Angles
	public final static double radian = 1.0;
	public final static double rad = radian;
	public final static double mrad = milli * rad;
	public final static double urad = micro * rad;

	public final static double steradian = 1.0;
	public final static double sr = steradian;
	public final static double rad2 = steradian;


	// other SI Units 
	public final static double hertz = 1.0 / sec;
	public final static double Hz = hertz;
	public final static double uHz = micro * hertz;
	public final static double mHz = milli * hertz;
	public final static double kHz = kilo * hertz;
	public final static double MHz = mega * hertz;
	public final static double GHz = giga * hertz;
	public final static double THz = tera * hertz;
	public final static double Hz2 = Hz * Hz;
	public final static double Hz3 = Hz2 * Hz;
	public final static double Hz4 = Hz3 * Hz;
	public final static double Hz5 = Hz4 * Hz;
	public final static double Hz6 = Hz5 * Hz;


	public final static double newton = kg * m / s2;
	public final static double N = newton;
	public final static double kN = kilo * newton;
	public final static double MN = mega * newton;
	public final static double mN = milli * newton;
	public final static double uN = micro * newton;
	public final static double nN = nano * newton;
	public final static double pN = pico * newton;
	public final static double N2 = N * N;
	public final static double N3 = N2 * N;
	public final static double N4 = N3 * N;
	public final static double N5 = N4 * N;
	public final static double N6 = N5 * N;


	public final static double pascal = N / m2;
	public final static double Pa = pascal;
	public final static double hPa = hecto * pascal;
	public final static double kPa = kilo * pascal;
	public final static double MPa = mega * pascal;

	public final static double joule = N * m;
	public final static double J = joule;
	public final static double kJ = kilo * joule;
	public final static double MJ = mega * joule;
	public final static double GJ = giga * joule;
	public final static double TJ = tera * joule;
	public final static double PJ = peta * joule;
	public final static double mJ = milli * joule;    
	public final static double uJ = micro * joule;
	public final static double nJ = nano * joule;
	public final static double pJ = pico * joule;
	public final static double fJ = femto * joule;


	public final static double watt = J / s;
	public final static double W = watt;
	public final static double kW = kilo * watt;
	public final static double MW = mega * watt;
	public final static double GW = giga * watt;
	public final static double TW = tera * watt;
	public final static double mW = milli * watt;
	public final static double uW = micro * watt;
	public final static double nW = nano * watt;
	public final static double pW = pico * watt;
	public final static double fW = femto * watt;

	public final static double coulomb = A * s;
	public final static double C = coulomb;

	public final static double volt = W / A;
	public final static double V = volt;
	public final static double mV = milli * volt;
	public final static double uV = micro * volt;
	public final static double nV = nano * volt;
	public final static double pV = pico * volt;
	public final static double kV = kilo * volt;

	public final static double farad = C / V;
	public final static double F = farad;
	public final static double mF = milli * farad;
	public final static double uF = micro * farad;
	public final static double nF = nano * farad;
	public final static double pF = pico * farad;
	public final static double fF = femto * farad;

	public final static double ohm = V / A;
	public final static double kohm = kilo * ohm;
	public final static double Mohm = mega * ohm;
	public final static double mohm = milli * ohm;
	public final static double uohm = micro * ohm;

	public final static double siemens = 1.0 / ohm;
	public final static double S = siemens;    

	public final static double weber = V * s;
	public final static double Wb = weber;

	public final static double tesla = Wb / m2;
	public final static double T = tesla;
	public final static double mT = milli * tesla;
	public final static double uT = milli * tesla;


	public final static double henry = Wb / A;
	public final static double H = henry;
	public final static double mH = milli * henry;
	public final static double uH = micro * henry;
	public final static double nH = nano * henry;
	public final static double pH = pico * henry;

	public final static double lumen = cd * sr;
	public final static double lm = lumen;

	public final static double lux = lm / m2;
	public final static double lx = lux;

	public final static double becquerel = 1.0 / sec;
	public final static double Bq = becquerel;

	public final static double gray = J / kg;
	public final static double Gy = gray;
	public final static double mGy = milli * gray;

	public final static double sievert = J / kg;
	public final static double Sv = sievert;


	// Angles (radians)
	public final static double degree = Math.PI/180.0;
	public final static double deg = degree;
	public final static double arcMinute = degree/60.0;
	public final static double arcmin = arcMinute;
	public final static double arcSecond = arcMinute/60.0;
	public final static double arcsec = arcSecond;
	public final static double mas = milli * arcSecond;

	public final static double hourAngle = 2.0*Math.PI/24.0;
	public final static double minuteAngle = hourAngle/60.0;
	public final static double secondAngle = minuteAngle/60.0;

	public final static double squareDegree = degree * degree;
	public final static double degree2 = squareDegree;
	public final static double sqdeg = squareDegree;

	public final static double deg2 = degree2;
	public final static double arcmin2 = arcmin * arcmin;
	public final static double arcsec2 = arcsec * arcsec;

	// non-SI derivatives
	// time:
	public final static double minute = 60.0 * sec;
	public final static double min = minute;
	public final static double hour = 60.0 * minute;
	public final static double day = 24.0 * hour;
	public final static double year = 365.24219879 * day;
	public final static double yr = year;
	public final static double century = 100.0 * year;
	public final static double julianCentury = 36525.0 * day;
	
	public final static double timeAngle = hourAngle / hour;

	// distances:
	public final static double angstrom = 1.0e-10 * m;
	public final static double Rsun = 696.0 * 1e6 * m;
	public final static double solarRadius = Rsun;
	public final static double Rearth = 6378140.0 * m;
	public final static double earthRadius = Rearth;
	public final static double AU = 149.6e6 * km;
	public final static double lightYear = 299792459.0 * year;
	public final static double lyr = lightYear;
	public final static double ly = lightYear;
	public final static double parsec = AU / arcsec;
	public final static double pc = parsec; 
	public final static double kpc = kilo * parsec;
	public final static double Mpc = mega * parsec;
	public final static double Gpc = giga * parsec;
	public final static double inch = 2.54  * cm;
	public final static double in = inch;
	public final static double mil = 1.0e-3 * inch;
	public final static double foot = 0.3048 * m;
	public final static double ft = foot;
	public final static double yard = 0.9144 * m;
	public final static double yd = yard;
	public final static double mile = 1.60935 * km;
	public final static double mi = mile;
	public final static double pt = 1/72.0 * in;

	// Areas
	public final static double barn = 1.0e-28 * m2;


	// Volumes
	public final static double litre = 1.0e-3 * m3;
	public final static double liter = litre;
	public final static double l = litre;
	public final static double L = litre;
	public final static double dl = deci * litre;
	public final static double dL = dl;
	public final static double cl = centi * litre;
	public final static double cL = cl;
	public final static double ml = milli * litre;
	public final static double mL = ml;
	public final static double gallon = 3.78543 * l;
	public final static double gal = gallon;
	public final static double quart = gal / 4.0;
	public final static double pint = quart / 2.0;

	public final static double cup = pint / 2.0;
	public final static double fluidOunce = cup / 8.0;
	public final static double fl_oz = fluidOunce;
	//public final static double tableSpoon = ?;
	//public final static double Tsp = tableSpoon;
	//public final static double teasSpoon = ?;
	//public final static double tsp = teaSpoon;

	public final static double englishPint = 20.0 * fluidOunce;


	// weigths
	public final static double Msun = 1.99e30 * kg;
	public final static double solarMass = Msun;
	public final static double Mearth = 5.9742e24 * kg;
	public final static double earthMass = Mearth;
	public final static double atomicUnit = 1.66057e-27 * kg;
	public final static double u = atomicUnit;
	public final static double pound = 0.4535924 * kg;
	public final static double lb = pound;
	public final static double ounce = pound / 16.0;
	public final static double oz = ounce;    


	// Electric
	public final static double debye = 3.334e-10 * C * m;
	public final static double biot = 10.0*A;
	public final static double Bi = biot;
	public final static double gauss = 1.0e-4*T;
	public final static double Gs = gauss;


	// Frequency
	public final static double rpm = 2.0 * Math.PI / min;
	public final static double radiansPerSecond = 1.0 / sec;
	public final static double radpersec = radiansPerSecond;
	public final static double waveNumber = Constant.h * Constant.c / metre;


	// Forces & Pressures
	public final static double dyn = 1.0e-5;
	public final static double psi = 6.89476e3 * Pa;
	public final static double atm = 1.01325e5 * Pa;
	public final static double bar = 1.0e5 * Pa;
	public final static double mbar = milli * bar;
	public final static double ubar = micro * bar;
	public final static double torr = 1.33322e2 * Pa;
	public final static double mmHg = torr;
	public final static double mTorr = milli * torr;


	// Energies (in Joules), Temperature, Power    
	public final static double erg = g * cm2 /s2;
	public final static double calorie = 4.1868 * J;
	public final static double cal = calorie;
	public final static double kcal = kilo * cal;
	public final static double therm = 1.05506e8 * J;
	public final static double BTU = therm;

	public final static double Celsius = K;
	public final static double Carenheit = 5.0/9.0 * K;
	public final static double eV =  1.6022e-19 * V;
	public final static double neV =  nano * eV;
	public final static double ueV =  micro * eV;
	public final static double meV =  milli * eV;
	public final static double keV = kilo * eV;
	public final static double MeV = mega * eV;
	public final static double GeV = giga * eV;
	public final static double TeV = tera * eV;

	public final static double Lsun = 3.8e26 * W;
	public final static double solarLuminosity = Lsun;
	public final static double horsePower = 7.457e2 * W;
	public final static double hp = horsePower;
	public final static double HP = horsePower;

	// Spectral Density
	public final static double jansky = 1.0e-26 * W / (m2 * Hz);
	public final static double Jy = jansky;
	public final static double mJy = milli * jansky;
	public final static double uJy = micro * jansky;
	public final static double kJy = kilo * jansky;

	// Various
	public final static double mpg = mile / gal;

	public final static Unit unity = new Unit(1.0, "counts");

	static {
		new Unit(1.0, "count, counts, ct, piece, pieces, pcs, 1");

		new Unit(m, "m, meter, metre");
		new Unit(g, "g, gramm");
		new Unit(dkg, "dkg, dekagramm");
		new Unit(s, "s, sec, second");
		new Unit(A, "A, amp, ampere");
		new Unit(K, "K, kelvin");
		new Unit(mol, "mol");
		new Unit(cd, "cd, candela");
		new Unit(rad, "rad, radian");
		new Unit(sr, "sr, steradian");
		new Unit(Hz, "Hz, hertz");
		new Unit(N, "N, newton");
		new Unit(Pa, "Pa, pascal");
		new Unit(J, "J, joule");
		new Unit(W, "W, watt");
		new Unit(C, "C, Coulomb");
		new Unit(V, "V, volt");
		new Unit(F, "F, farad");
		new Unit(ohm, "ohm");
		new Unit(S, "S, siemens");
		new Unit(Wb, "Wb, weber");
		new Unit(T, "T, tesla");
		new Unit(H, "H, henry");
		new Unit(lm, "lm, lumen");
		new Unit(lx, "lx, lux");
		new Unit(Bq, "Bq, bequerel");
		new Unit(Gy, "Gy, gray");
		new Unit(Sv, "Sv, sievert");

		new Unit(deg, "deg, degree");
		new Unit(arcmin, "arcmin, am");
		new Unit(arcsec, "arcsec, as");
		// ...
		new Unit(min, "min, minute");
		new Unit(hour, "hour");
		new Unit(day, "day");
		new Unit(year, "year, yr");
		new Unit(century, "century, cent");
		new Unit(julianCentury, "juliancentury");
		new Unit(angstrom, "angstrom");
		new Unit(Rsun, "Rsun, solarradius");
		new Unit(Rearth, "Rearth, earthradius");
		new Unit(AU, "AU, astronomicalunit");
		new Unit(ly, "ly, lyr, lightyear");
		new Unit(pc, "pc, parsec");
		new Unit(in, "in, inch");
		new Unit(mil, "mil");
		new Unit(ft, "ft, foot");
		new Unit(yd, "yd, yard");
		new Unit(mi, "mi, mile");
		new Unit(pt, "pt");
		new Unit(barn, "barn");
		new Unit(l, "l, L, litre, liter");
		new Unit(gal, "gal, gallon");
		new Unit(quart, "quart");
		new Unit(pint, "pint");
		new Unit(cup, "cup");
		new Unit(fl_oz, "fl.oz, floz, fluidounce");
		// ...
		new Unit(englishPint, "englishpint");
		new Unit(Msun, "Msun, solarmass");
		new Unit(Mearth, "Mearth, earthmass");
		new Unit(u, "u, atomicunit");
		new Unit(lb, "lb, lbs, pound");
		new Unit(oz, "oz, ounce");

		new Unit(debye, "debye");
		new Unit(Bi, "Bi, biot");
		new Unit(Gs, "Gs, gauss");
		new Unit(rpm, "rpm");
		new Unit(waveNumber, "wavenumber");
		new Unit(dyn, "dyn");
		new Unit(psi, "psi");
		new Unit(atm, "atm, atmosphere");
		new Unit(bar, "bar");
		new Unit(torr, "mmHg, torr");

		new Unit(erg, "erg");
		new Unit(cal, "cal, calorie");
		new Unit(BTU, "BTU, therm");

		new Unit(eV, "eV, electronvolt");
		new Unit(Lsun, "Lsun, solarluminosity");
		new Unit(hp, "hp, HP, horsepower");

		new Unit(Jy, "Jy, jansky");
		new Unit(mpg, "mpg, MPG");
	}

}



