/* *****************************************************************************
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
 *     Attila Kovacs  - initial API and implementation
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
import jnum.data.localized.LocalAverage;
import jnum.data.localized.Locality;
import jnum.data.localized.LocalizedData;
import jnum.math.Range;
import jnum.math.Vector2D;
import jnum.util.LogFile;


public class PointingTable extends LocalAverage<PointingTable.Location, Vector2D> {
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
			try { 
				String date = row.get("id").getValue();
				AstroTime time = AstroTime.forFitsDate(date);			
				
				double MJD = Math.round(time.MJD()) + row.get("UTh").getDouble() * Unit.hour / Unit.day;
					
				HorizontalCoordinates horizontal = new HorizontalCoordinates(
						row.get("AZd").getDouble() * Unit.deg,
						row.get("ELd").getDouble() * Unit.deg
				);	
			
				try {
				    double s2n = row.get("src.peak").getDouble() / row.get("src.dpeak").getDouble();
				    if(s2n < minS2N) continue;
				    
				    double FWHM = row.get("src.FWHM").getDouble() * Unit.arcsec;
				    if(!sizeRange.contains(FWHM)) continue;
				    
				    Vector2D p = new Vector2D(row.get("pnt.X").getDouble(), row.get("pnt.Y").getDouble());
                    p.scale(Unit.arcsec);
                    
			
                    add(new LocalizedData<>(new Location(MJD, horizontal), p, 3.0 * Unit.arcsec));
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
		LocalizedData<Location, Vector2D> mean = getLocalAverage(new Location(MJD, horizontal), 1.0);
		
		Vector2D p = mean.getData();
		
		p.subtract(pointingModel.getCorrection(horizontal, (MJD % 1.0) * Unit.day, Tamb));

		CRUSH.info(this, "Incremental Pointing is " + 
				Util.f1.format(p.x() / Unit.arcsec) + "," +
				Util.f1.format(p.y() / Unit.arcsec) + 
				" (quality:" + Util.f2.format(mean.weight()) + ")."
				);
		
		return p;
	}
	

	class Location implements Locality {
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
        public double getSortingValue() {
		    return MJD * Unit.day / timeWindow;
		}
			
		@Override
		public String toString() { return Double.toString(MJD) + " : " + horizontal; }

	}


}


