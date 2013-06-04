/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
package crush.mako;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Set;
import java.util.StringTokenizer;
import java.io.*;

import kovacs.util.*;
import kovacs.util.data.AmoebaMinimizer;

import crush.array.DistortionModel;


public class MakoModel {
	double rotation = 0.0;
	DistortionModel distortion = new DistortionModel();
	Mako model = new Mako();
	Hashtable<Double, Vector2D> positions = new Hashtable<Double, Vector2D>();
	Vector2D offset = new Vector2D();
	int order = 1;
	//double tolerance = 8.0;
	
	public static void main(String[] args) {
		MakoModel model = new MakoModel();
		try {
			model.readRCP(args[0]);
			model.fit();
		}
		catch(Exception e) { e.printStackTrace(); }
	}

	public MakoModel() {
		for(int row = 0, index = 0; row < Mako.rows; row++) for(int col=0; col<Mako.cols; col++, index++) {
			MakoPixel pixel = new MakoPixel(model, index);
			pixel.row = row;
			pixel.col = col;
			model.add(pixel);
		}
	}
	
	public MakoPixel findNearest(Vector2D pixelPos) {
		MakoPixel nearest = null;
		double mind = Double.POSITIVE_INFINITY;
		for(MakoPixel pixel : model) {
			double d = pixel.getPosition().distanceTo(pixelPos);
			if(d < mind) {
				mind = d;
				nearest = pixel;
			}
		}
		return nearest;
	}
	
	private void recalc() {
		for(MakoPixel pixel : model) {
			pixel.calcPosition();
			pixel.getPosition().scale(1.0 / Unit.arcsec);
			distortion.distort(pixel.getPosition());
			pixel.getPosition().rotate(rotation);			
		}
	}
	
	public void readRCP(String fileName) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line = null;
		positions.clear();
		
		offset.zero();
		
		while((line = in.readLine()) != null) if(line.length() > 0) if(line.charAt(0) != '#') {
			StringTokenizer tokens = new StringTokenizer(line);
			for(int i=0; i<3; i++) tokens.nextToken();
			Vector2D pos = new Vector2D(Double.parseDouble(tokens.nextToken()), Double.parseDouble(tokens.nextToken()));
			Double f = Double.parseDouble(tokens.nextToken());
			positions.put(f, pos);
			offset.add(pos);
		}
		in.close();
		offset.scale(1.0 / positions.size());
		
		System.err.println(" Parsed " + positions.size() + " positions.");
		
		/*
		for(Vector2D pos : positions.values()) pos.subtract(offset);
		offset.zero();
		*/
	}
	
	private void setParms(double[] p) {
		rotation = p[0];
		
		for(int i=0, index = 1; i<=order; i++) for(int j=0; j<=order-i; j++) {
			distortion.setX(i, j, p[index++]);
			distortion.setY(i, j, p[index++]);
		}
		
		recalc();
	}
		
	public void fit() {
		
		for(int i=0; i<=order; i++) for(int j=0; j<=order-i; j++) distortion.set(i, j, 0.0, 0.0);
		
		AmoebaMinimizer opt = new AmoebaMinimizer() {

			@Override
			public double evaluate(double[] tryparms) {
				setParms(tryparms);
				
				int n = 0;
				double sum = 0.0;
				for(Vector2D pos : positions.values()) {
					MakoPixel pixel = findNearest(pos);
					double d = pixel.getPosition().distanceTo(pos);
					//if(d > tolerance) continue;
					sum += d*d;
					n++;					
				}
				//double e = Math.exp(positions.size() / n);
				
				return n > 0 ? sum / n : Double.POSITIVE_INFINITY;
			}
		};
		
		double[] parms = new double[2 * distortion.size() + 1];
		parms[1] = offset.getX();
		parms[2] = offset.getY();
		
		double[] initSize = new double[parms.length];
		
		initSize[0] = 0.01;
		for(int i=0, index = 1; i<=order; i++) for(int j=0; j<=order-i; j++) {
			double l = Math.pow(0.01, (i+j+1));
			initSize[index++] = l;
			initSize[index++] = l;
		}
		
		opt.init(parms);
 		opt.setStartSize(initSize);
		opt.verbose = true;
		opt.precision = 1e-6;
		opt.minimize(10);
	
		double[] p = opt.getFitParameters();
		setParms(p);
		
		System.out.println("# rms = " + Util.s4.format(Math.sqrt(opt.getChi2())) + " arcsec.");
		System.out.println("#");
		System.out.println("# rotation = " + Util.f3.format(-p[0] / Unit.deg) + "deg.");
		
		for(int i=0, index = 1; i<=order; i++) for(int j=0; j<=order-i; j++) {
			System.out.println("# " + i + "," + j + ":\t" + Util.s4.format(p[index++]) + "\t" + Util.s4.format(p[index++]));
		}
	
		System.out.println("#");
		
		Set<Double> keys = positions.keySet();
		ArrayList<Double> sorted = new ArrayList<Double>(keys);
		Collections.sort(sorted);
		
		for(Double f : sorted) {
			System.out.print(Util.f1.format(f) + "\t");
			MakoPixel pixel = findNearest(positions.get(f));
			System.out.print((pixel.row+1) + "\t" + (pixel.col+1) + "\t");
			System.out.println(Util.f1.format(pixel.getPosition().getX()) + "\t" + Util.f1.format(pixel.getPosition().getY()));
		}
	}
	
}
 
