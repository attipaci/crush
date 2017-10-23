/*******************************************************************************
 * Copyright (c) 2017 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.telescope.cso;

import java.io.IOException;
import java.util.Hashtable;

import jnum.astro.AstroTime;
import jnum.io.LineParser;
import jnum.text.SmartTokenizer;

public class MaiTau extends Hashtable<String, MaiTau.Fit> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 9133286960505946234L;

	private String fileName;

	public void load(String fileName) throws IOException {
		if(fileName == null) throw new IOException("No file specified.");
		if(fileName.equals(this.fileName)) return; 
		this.fileName = fileName;

		clear();

		new LineParser() {
			@Override
			protected boolean parse(String line) throws Exception {
				SmartTokenizer tokens = new SmartTokenizer(line);

				Fit fit = new Fit();
				fit.date = tokens.nextToken();
				fit.from = tokens.nextDouble();
				fit.to = tokens.nextDouble();
				fit.coeff = new double[tokens.nextInt() + 1];

				for(int i=0; i<fit.coeff.length; i++) fit.coeff[i] = Double.parseDouble(tokens.nextToken());

				put(fit.date, fit);
				return true;
			}

		}.read(fileName);
	}

	protected double getTau(double MJD) throws IOException, IllegalStateException {    
		AstroTime time = new AstroTime();
		time.setMJD(MJD);
		String date = AstroTime.getDateFormat("yyyMMdd").format(time.getDate());

		Fit fit = get(date);  
		if(fit == null) throw new IllegalStateException("Invalid date or date not in database.");

		// The time of the tau request
		double t = MJD % 1.0;
		double extra = extrapolationMargin * (fit.to - fit.from);

		if(t < fit.from - extra || t > fit.to + extra) throw new IllegalStateException("Time outside of fitted range.");    

		return fit.getValue(t);
	}


	protected class Fit { 
		String date;
		double from, to;
		double[] coeff;

		public Fit() {}

		public double getValue(double fracOfDay) {
			double value = 0.0;
			double power = 1.0;

			for(int i=0; i<coeff.length; i++) {
				value += coeff[i] * power;
				power *= fracOfDay;
			}

			return value;
		}
	}

	static double extrapolationMargin = 0.0;   // Fractional extension of the tau range beyond the fit's range.

}
