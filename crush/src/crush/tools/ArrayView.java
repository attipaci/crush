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

package crush.tools;

import java.io.*;
import java.util.*;
import java.text.*;

import crush.*;
import crush.array.SimplePixel;
import crush.sourcemodel.SkyMap;

import util.*;


public class ArrayView extends SkyMap {

	public SimplePixel[] pixel;

	// Takes two arguments: <rcp-file> [pixel-data-file]
	public static void main(String[] args) {
		ArrayView view = new ArrayView();
		try { 
			//view.gain(args[0]);
			view.nefd(args[0]);
		}
		catch(Exception e) { e.printStackTrace(); }

	}

	public void nefd(String pixelFileName) throws Exception {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(pixelFileName)));
		String line;


		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			int c = Integer.parseInt(tokens.nextToken()) - 1;
			double gain = Double.parseDouble(tokens.nextToken());
			Double.parseDouble(tokens.nextToken());
			double weight = Double.parseDouble(tokens.nextToken());
			// Noise excess is around 1.25...
			// 5.27 Jy/uV
			double nefd = 5.3e6 * Math.sqrt(0.04/weight)/gain;

			if(weight > 0.0 && gain > 0.0) {
				System.out.print((c+1) + "\t");
				System.out.print(Util.f3.format(gain) + "\t");
				System.out.println(Util.f3.format(nefd));

				drawCircle(pixel[c].position.x, pixel[c].position.y, 9.0*Unit.arcsec, nefd);
			}
		}

		fitsUnit = new Unit("Jy sqrt(s)", 1.0);

		normalize();
		sourceName = "nefd";

		write();
	}

	public void gain(String pixelFileName) throws Exception {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(pixelFileName)));
		String line;

		DecimalFormat df = new DecimalFormat("000.0");
		DecimalFormat ef = new DecimalFormat("0.00E0");

		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			int c = Integer.parseInt(tokens.nextToken()) - 1;
			double gain = Double.parseDouble(tokens.nextToken());
			Double.parseDouble(tokens.nextToken());
			double weight = Double.parseDouble(tokens.nextToken());	    
			double sensitivity = 1.3 * 2.0 * 5.27e6 * Math.sqrt(0.04/weight)/gain;

			if(weight > 0.0) {
				System.out.print((c+1) + "\t");
				System.out.print(df.format(pixel[c].position.x/Unit.arcsec) + "\t");
				System.out.print(df.format(pixel[c].position.y/Unit.arcsec) + "\t");
				System.out.println(ef.format(sensitivity));

				drawCircle(pixel[c].position.x, pixel[c].position.y, 9.0*Unit.arcsec, gain);
			}
		}

		fitsUnit = new Unit("1.0", 1.0);

		normalize();
		sourceName = "gains";

		write();
	}

	public ArrayView() { 
		super(1000,1000); 
		i0 = 500.0;
		j0 = 500.0;
		equatorial = new EquatorialCoordinates(0.0, 0.0, "J2000");
		horizontal = new HorizontalCoordinates(0.0, 0.0);
		
		delta = 1000.0*Unit.arcsec/sizeX;
		isHorizontal = true;
		Integration scan = new Integration();
		scan.horizontal = new HorizontalCoordinates(0.0, 0.0);
		scans.add(scan);
		init();
		clear();
		projection = new Gnomonic();
		projection.setReference(horizontal);

		pixel = new LabocaPixel[320];
		for(int i=0; i<pixel.length; i++) pixel[i] = new LabocaPixel(i);
		
		try { LabocaPixel.readPixelRCP(pixel, "laboca/master.rcp", pixel[317]); }
		catch(IOException e) { e.printStackTrace(); }
	}


	public void drawCircle(double dX, double dY, double r, double value) {
		if(Math.abs(dX) > 900.0 * Unit.arcsec) return;
		if(Math.abs(dY) > 900.0 * Unit.arcsec) return;
		
		double ic = fracIndexOfdX(dX);
		double jc = fracIndexOfdY(dY);

		double d = r/delta;

		int fromi = (int)Math.floor(ic - r/delta);
		int toi = (int)Math.ceil(ic + r/delta);
		int fromj = (int)Math.floor(jc - r/delta);
		int toj = (int)Math.ceil(jc + r/delta);

		Vector2D offset = new Vector2D();
		
		for(int i=fromi; i<=toi; i++) for(int j=fromj; j<=toj; j++) {
			offset.x = i-ic;
			offset.y = j-jc;			
			if(offset.length() < d) addPointAt(i, j, value, 1.0, 1.0, 1.0);
		}
	} 

}
