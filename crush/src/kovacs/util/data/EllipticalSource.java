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
package kovacs.util.data;

import java.text.ParseException;

import kovacs.util.*;
import kovacs.util.data.Bounds;
import kovacs.util.data.DataPoint;
import kovacs.util.data.GridImage;


public class EllipticalSource<CoordinateType extends CoordinatePair> extends GaussianSource<CoordinateType> {
	private DataPoint elongation = new DataPoint();
	private DataPoint angle = new DataPoint();
	
	public EllipticalSource() {
		super();
		// TODO Auto-generated constructor stub
	}
	
	public EllipticalSource(GridImage<CoordinateType> map, Vector2D offset, double a, double b, double angle) {
		super(map, offset, 0.5*(a+b));
		elongation.setValue((a-b) / (a+b));
		this.angle.setValue(angle);
		// TODO Auto-generated constructor stub
	}
	
	public EllipticalSource(CoordinateType coords, double a, double b, double angle) {
		super(coords, 0.5*(a+b));
		elongation.setValue((a-b) / (a+b));
		this.angle.setValue(angle);
		// TODO Auto-generated constructor stub
	}
	
	public EllipticalSource(String line, int format, GridImage<CoordinateType> forImage) throws ParseException {
		super(line, format, forImage);
		// TODO Auto-generated constructor stub
	}
	
	public DataPoint getElongation() { return elongation; }
	
	public DataPoint getAngle() { return angle; }
	
	@Override
	public void measureShape(GridImage<CoordinateType> map) {	
		super.measureShape(map);
		
		Vector2D center = getIndex(map.getGrid());
		Vector2D delta = map.getResolution();
		Bounds bounds = getBounds(map, 1.0 * map.getImageFWHM());
		
		double m0 = 0.0, m2c = 0.0, m2s = 0.0, sumw = 0.0;
		
		
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) if(map.isUnflagged(i, j)) {
			double w = map.getWeight(i, j);
			double wp = w * Math.abs(map.getS2N(i,j));
			double dx = (i - center.getX()) / delta.getX();
			double dy = (j - center.getY()) / delta.getY();
			double theta = 2.0 * Math.atan2(dy, dx);
			
			m2c += wp * Math.cos(theta);
			m2s += wp * Math.sin(theta);
			m0 += wp;
			sumw += w;
		}
		if(m0 > 0.0) {
			m2c *= 1.0 / m0;
			m2s *= 1.0 / m0;
			
			elongation.setValue(2.0 * Math.hypot(m2s, m2c));
			//elongation.setRMS(2.0 * Math.sqrt(sumw / m0));
			elongation.setRMS(2.0 * Math.abs(sumw / m0));
			
			angle.setValue(0.5 * Math.atan2(m2s, m2c));
			angle.setRMS(elongation.rms() / elongation.value());
		}
		else {
			angle.noData();
			elongation.noData();
		}
	}
	
	public Range getAxes() {
		Range axes = new Range();
		getAxes(axes);
		return axes;
	}
	
	public void getAxes(Range axes) {
		axes.setMin(getRadius().value() * (1.0 - elongation.value()));
		axes.setMax(getRadius().value() * (1.0 + elongation.value()));
		// Renormalize to keep area unchanged...
		axes.scale(1.0 / (1.0 - elongation.value() * elongation.value()));	
	}
	
	@Override
	public DataTable getData(GridImage<CoordinateType> map, String sizeName, double sizeUnit) {
		DataTable data = super.getData(map, sizeName, sizeUnit);
		Range axes = getAxes();
		
		double da = getRadius().weight() > 0.0 ? Math.hypot(getRadius().rms(), axes.max() * elongation.rms()) : Double.NaN;
		double db = getRadius().weight() > 0.0 ? Math.hypot(getRadius().rms(), axes.min() * elongation.rms()) : Double.NaN;
		
		data.add(new Datum("a", axes.max() / sizeUnit, sizeName));
		data.add(new Datum("b", axes.min() / sizeUnit, sizeName));
		data.add(new Datum("angle", angle.value() / sizeUnit, sizeName));
		data.add(new Datum("dangle", angle.rms() / sizeUnit, sizeName));
		
		data.add(new Datum("da", da / sizeUnit, sizeName));
		data.add(new Datum("db", db / sizeUnit, sizeName));
		
		return data;
	}
	
	@Override
	public String pointingInfo(GridImage<CoordinateType> map, String sizeName, double sizeUnit) {
		String info = super.pointingInfo(map, sizeName, sizeUnit);
		Range axes = getAxes();
		
		double da = getRadius().weight() > 0.0 ? Math.hypot(getRadius().rms(), axes.max() * elongation.rms()) : Double.NaN;
		double db = getRadius().weight() > 0.0 ? Math.hypot(getRadius().rms(), axes.min() * elongation.rms()) : Double.NaN;
		
			
		info += " (a="
				+ Util.f1.format(axes.max() / sizeUnit) + "+-" + Util.f1.format(da / sizeUnit) 
				+ ", b=" 
				+ Util.f1.format(axes.min() / sizeUnit) + "+-" + Util.f1.format(db / sizeUnit) 
				+ ", angle="
				+ Util.d1.format(angle.value() / Unit.deg) + "+-" + Util.d1.format(angle.rms() / Unit.deg)
				+ " deg)";

		return info;
	}
	
	
	
	// TODO Override add...
	
}