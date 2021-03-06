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
package crush.telescope;

import java.io.IOException;
import java.util.*;
import java.text.ParseException;

import crush.CRUSH;
import crush.telescope.iram.IRAMPointingModel;
import jnum.ExtraMath;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroTime;
import jnum.astro.HorizontalCoordinates;
import jnum.data.LocalAverage;
import jnum.data.Locality;
import jnum.data.LocalizedData;
import jnum.data.WeightedPoint;
import jnum.math.Range;
import jnum.math.Vector2D;
import jnum.util.LogFile;


public class PointingTable extends LocalAverage<PointingTable.Entry> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -416191292991301334L;

	public String fileName = "";
	
	public double minS2N = 10.0;
	public Range sizeRange = new Range(15.0 * Unit.arcsec, 25.0 * Unit.arcsec);
	
	public double searchRadius = 15.0 * Unit.deg;
	public double timeWindow = 1.0 * Unit.hour;
	
	private static Hashtable<String, PointingTable> tables = new Hashtable<>();
	
	public static PointingTable get(String fileName) throws IOException {
		PointingTable table = tables.get(fileName);
		if(table == null) {
			table = new PointingTable(fileName);
			tables.put(fileName, table);
		}
		return table;
	}
	
		
	private PointingTable(String fileName) throws IOException {
		read(fileName);
	}
	
	private void read(String fileName) throws IOException {
		if(fileName.equals(this.fileName)) return;
		
		
		for(LogFile.Row row : LogFile.read(fileName)) {
			Entry pointing = new Entry();
			try { 
				pointing.id = row.get("id").getValue();
				AstroTime time = AstroTime.forStandardDate(pointing.id);			
				
				double MJD = Math.round(time.getMJD()) + row.get("UTh").getDouble() * Unit.hour / Unit.day;
					
				HorizontalCoordinates horizontal = new HorizontalCoordinates(
						row.get("AZd").getDouble() * Unit.deg,
						row.get("ELd").getDouble() * Unit.deg
				);	
				
				pointing.location = new Location(MJD, horizontal);
				
				try {
					pointing.dx.setValue(row.get("pnt.X").getDouble() * Unit.arcsec);
					pointing.dy.setValue(row.get("pnt.Y").getDouble() * Unit.arcsec);
					
					pointing.dx.setWeight(1.0);
					pointing.dy.setWeight(1.0);
						
					pointing.significance = row.get("src.peak").getDouble() / row.get("src.dpeak").getDouble();

					pointing.FWHM = row.get("src.FWHM").getDouble() * Unit.arcsec;
					
					if(pointing.significance > minS2N) if(sizeRange.contains(pointing.FWHM)) 
						add(pointing);
				}
				catch(NumberFormatException e) {}
				
			}
			catch(ParseException e) {
				CRUSH.warning(this, "Cannot parse date " + row.get("date").getValue());
			}
		}
		
		this.fileName = fileName;
		
		Collections.sort(this);
		
		CRUSH.info(this, "[Loading pointing table.] -- " + size() + " valid records found.");
		if(CRUSH.debug) CRUSH.detail(this, " >> " + fileName + " >> ");
		
	}
	
	
	public Vector2D getIncrement(double MJD, double Tamb, HorizontalCoordinates horizontal, IRAMPointingModel pointingModel) {		
		Entry mean = getLocalAverage(new Location(MJD, horizontal), new Model(pointingModel, Tamb));
		
		CRUSH.info(this, "Incremental Pointing is " + 
				Util.f1.format(mean.dx.value() / Unit.arcsec) + "," +
				Util.f1.format(mean.dy.value() / Unit.arcsec) + 
				" (quality:" + Util.f2.format(mean.dx.weight()) + ")."
				);
		
		return new Vector2D(mean.dx.value(), mean.dy.value());
		
	}
	
	class Model {
		IRAMPointingModel pointingModel;
		double ambientT;
		
		public Model(IRAMPointingModel pointingModel, double ambientT) {
			this.pointingModel = pointingModel;
			this.ambientT = ambientT;			
		}
			
		public Vector2D getCorrection(Entry pointing) {			
			return pointingModel.getCorrection(pointing.location.horizontal, (pointing.location.MJD % 1.0) * Unit.day, ambientT);
		}
	}

	class Location extends Locality {
		double MJD;
		HorizontalCoordinates horizontal;
		
		public Location(double MJD, HorizontalCoordinates horizontal) { 
			this.MJD = MJD; 
			this.horizontal = horizontal;
		}
		
		@Override
		public double distanceTo(Locality other) {
			double devT = (((Location) other).MJD - MJD) * Unit.day / timeWindow;
			double devX = ((Location) other).horizontal.distanceTo(horizontal) / searchRadius;
			
			return ExtraMath.hypot(devT, devX);
		}

		@Override
		public int compareTo(Locality o) {
			return Double.compare(MJD, ((Location) o).MJD);
		}
		
		@Override
		public String toString() { return Double.toString(MJD) + " : " + horizontal; }

		@Override
		public double sortingDistanceTo(Locality other) {
			return Math.abs((((Location) other).MJD - MJD) * Unit.day / timeWindow);
		}
	}

	
	class Entry extends LocalizedData {
		/**
		 * 
		 */
		private static final long serialVersionUID = 6919207692208960243L;
		Location location;
		String id;
		
		WeightedPoint dx = new WeightedPoint();
		WeightedPoint dy = new WeightedPoint();
		double significance;
		double FWHM;
		
		
		@Override
		public Locality getLocality() {
			return location;
		}
		@Override
		public void setLocality(Locality loc) {
			location = (Location) loc;
		}
		@Override
		protected void averageWidth(LocalizedData other, Object env, double relativeWeight) {
			Entry entry = (Entry) other;
			
			Vector2D corr = env == null ? new Vector2D() : ((Model) env).getCorrection(entry);
			
			dx.average(entry.dx.value() - corr.x(), relativeWeight * entry.dx.weight());
			dy.average(entry.dy.value() - corr.y(), relativeWeight * entry.dy.weight());
		}
	
		
	}


	@Override
	public Entry getLocalizedDataInstance() {
		return new Entry();
	}	
	
}


