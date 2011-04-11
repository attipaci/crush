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
// Copyright (c) 2009 Attila Kovacs 

package crush.sourcemodel;

import util.*;
import util.astro.EquatorialCoordinates;
import util.data.WeightedPoint;
 
import java.io.*;
import java.util.*;

import crush.Instrument;
import crush.Integration;
import crush.Scan;
import crush.SourceModel;

public abstract class Photometry extends SourceModel<Instrument<?>, Scan<?,?>> {
	String sourceName;
	String type;
	EquatorialCoordinates position;
	WeightedPoint flux;
	double integrationTime;
	double baseFlux = 0.0;
	
	
	public Photometry(Instrument<?> instrument) {
		super(instrument);
	}
	
	@Override
	public void createFrom(Collection<? extends Scan<?,?>> collection) {
		super.createFrom(collection);
	}
	
	@Override
	public void add(SourceModel<?,?> model, double weight) {
		Photometry other = (Photometry) model;
		flux.average(other.flux);
		integrationTime += other.integrationTime;
	}

	@Override
	public abstract void add(Integration<?,?> integration);

	@Override
	public void setBase() {
		baseFlux = 0.0;
	}

	@Override
	public void process(Scan<?,?> scan) {
		flux.value /= flux.weight;
	}

	@Override
	public void sync(Integration<?,?> integration) {
		// TODO Sync with point source profile

	}

	@Override
	public void write(String path) throws Exception {
		System.out.println(this);
		
		String fileName = path + File.separator;
		if(hasOption("name")) fileName += option("name").getValue();
		else fileName += sourceName + ".dat";
		
		PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(fileName)));
		out.println(this);
		out.close();		
	}

	
	@Override
	public String toString() {
		String typeString = "";
		
		Unit unit = getUnit();
		
		if(type != null) if(type.length() > 0) typeString = " (" + type + ")";
		String text = "Photometry" + typeString + " for " + sourceName;
		text += "  Position   = " + position.toString();
		text += "  Instrument = " + instrument.name;
		text += "  Flux       = " + Util.e3.format(flux.value / unit.value) + 
				" +- " + Util.e3.format(1.0 / (Math.sqrt(flux.weight) * unit.value)) + " " + unit.name;
		text += "  Int. Time  = " + Util.f1.format(integrationTime / Unit.s) + " s";
		
		return text;
	}

	@Override
	public String getSourceName() {
		return sourceName;
	}

	@Override
	public Unit getUnit() {
		return new Unit("Jy/beam", scans.get(0).instrument.janskyPerBeam());
	}
	
	
}
