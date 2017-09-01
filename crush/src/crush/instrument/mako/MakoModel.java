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
package crush.instrument.mako;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Set;
import java.io.*;

import crush.CRUSH;
import crush.array.DistortionModel;
import crush.instrument.mako2.Mako2;
import crush.instrument.mako2.Mako2Pixel;
import jnum.Unit;
import jnum.Util;
import jnum.data.fitting.ChiSquared;
import jnum.data.fitting.DownhillSimplex;
import jnum.data.fitting.Parameter;
import jnum.io.LineParser;
import jnum.math.Range;
import jnum.math.Vector2D;
import jnum.text.SmartTokenizer;


public class MakoModel<PixelType extends AbstractMakoPixel> {
	Parameter rotation = new Parameter("rotation", 0.0, Unit.deg);
		
	DistortionModel distortion = new DistortionModel();
	AbstractMako<PixelType> model;
	Hashtable<Double, Vector2D> positions = new Hashtable<Double, Vector2D>();
	Hashtable<Double, Double> sourceGains = new Hashtable<Double, Double>();
	int order = 2;
	//double tolerance = 8.0;
	
	public static void main(String[] args) {
		MakoModel<?> model = null;
		
		int ver = Integer.parseInt(args[0]);
		if(ver == 1) model = new MakoModel<MakoPixel>(new Mako());
		else if(ver == 2) model = new MakoModel<Mako2Pixel>(new Mako2());
		else {
		    CRUSH.warning(MakoModel.class, "Unknown MAKO version number: " + ver);
		    System.exit(1);
		}
		
		try {
			model.readRCP(args[1]);
			model.fit();
			model.print(System.out);
		}
		catch(Exception e) { CRUSH.error(MakoModel.class, e); }
	}

	public MakoModel(AbstractMako<PixelType> mako) {
		this.model = mako;
		for(int index=0; index< model.pixels; index++) model.add(model.getChannelInstance(index));
	}
	
	public AbstractMakoPixel findNearest(Vector2D pixelPos) {
		AbstractMakoPixel nearest = null;
		double mind = Double.POSITIVE_INFINITY;
		for(AbstractMakoPixel pixel : model) {
			double d = pixel.getPosition().distanceTo(pixelPos);
			if(d < mind) {
				mind = d;
				nearest = pixel;
			}
		}
		return nearest;
	}
	
	private void recalc() {
		for(AbstractMakoPixel pixel : model) {
			pixel.calcNominalPosition();
			pixel.getPosition().scale(1.0 / Unit.arcsec);
			distortion.distort(pixel.getPosition());
			pixel.getPosition().rotate(rotation.value());			
		}
	}
	
	public void readRCP(String fileName) throws IOException {
		positions.clear();
		
		final Range xRange = new Range();
		final Range yRange = new Range();
		
		new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                SmartTokenizer tokens = new SmartTokenizer(line);
                tokens.nextToken(); // serial
                double sourceGain = tokens.nextDouble();
                tokens.nextToken(); // sky gain
                Vector2D pos = new Vector2D(tokens.nextDouble(), tokens.nextDouble());
                double f = tokens.nextDouble();
                sourceGains.put(f, sourceGain);
                positions.put(f, pos);
                if(!Double.isNaN(pos.x())) xRange.include(pos.x());
                if(!Double.isNaN(pos.y()))yRange.include(pos.y());
                return true;
            }
		}.read(fileName);
			
		CRUSH.info(this, "Parsed " + positions.size() + " positions.");
		
		distortion.get(distortion.getTerm("x", 0, 0)).setRange(xRange);
		distortion.get(distortion.getTerm("y", 0, 0)).setRange(yRange);
		
	}
	
		
	public void fit() {
		
	    for(int i=0; i<=order; i++) for(int j=0; j<=order-i; j++) distortion.set(i, j, 0.0, 0.0);
	    
		ChiSquared chi2 = new ChiSquared() {
		    @Override
			public Double evaluate() {
		        recalc();
		        
				int n = 0;
				double sum = 0.0;
				for(Vector2D pos : positions.values()) {
					AbstractMakoPixel pixel = findNearest(pos);
					double d = pixel.getPosition().distanceTo(pos);
					//if(d > tolerance) continue;
					sum += d*d;
					n++;					
				}
				//double e = Math.exp(positions.size() / n);
				
				return n > 0 ? sum / n : Double.POSITIVE_INFINITY;
			}
		};
		
		ArrayList<Parameter> parameters = new ArrayList<Parameter>();
		
		parameters.add(rotation);
		parameters.addAll(distortion.values());
		
		DownhillSimplex opt = new DownhillSimplex(chi2, parameters);
		opt.minimize();
		opt.print("# ");
	}
	
	public void print(PrintStream out) {
		Set<Double> keys = positions.keySet();
		ArrayList<Double> sorted = new ArrayList<Double>(keys);
		Collections.sort(sorted);
		
		for(Double f : sorted) {
			out.print(Util.f1.format(f) + "\t");
			AbstractMakoPixel pixel = findNearest(positions.get(f));
			out.print((pixel.row+1) + "\t" + (pixel.col+1) + "\t");
			out.println(Util.f1.format(pixel.getPosition().x()) 
					+ "\t" + Util.f1.format(pixel.getPosition().y())
					+ "\t" + Util.f3.format(sourceGains.get(f)));
		}
	}
	
}
 
