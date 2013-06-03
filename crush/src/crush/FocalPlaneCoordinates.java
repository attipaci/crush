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
package crush;

import util.CoordinateAxis;
import util.CoordinateSystem;
import util.SphericalCoordinates;
import util.text.AngleFormat;

public class FocalPlaneCoordinates extends SphericalCoordinates {
	static CoordinateAxis xAxis, yAxis, xOffsetAxis, yOffsetAxis;
	static CoordinateSystem defaultCoordinateSystem, defaultLocalCoordinateSystem;

	static {
		defaultCoordinateSystem = new CoordinateSystem("Focal Plane Coordinates");
		defaultLocalCoordinateSystem = new CoordinateSystem("Focal Plane Offsets");

		xAxis = new CoordinateAxis("X", "ALON");
		yAxis = new CoordinateAxis("Y", "ALAT");
		xOffsetAxis = new CoordinateAxis("dX", xAxis.wcsName);
		yOffsetAxis = new CoordinateAxis("dY", yAxis.wcsName);
		
		defaultCoordinateSystem.add(xAxis);
		defaultCoordinateSystem.add(yAxis);
		defaultLocalCoordinateSystem.add(xOffsetAxis);
		defaultLocalCoordinateSystem.add(yOffsetAxis);
		
		AngleFormat af = new AngleFormat(3);
		
		for(CoordinateAxis axis : defaultCoordinateSystem) axis.setFormat(af);
	}

	public FocalPlaneCoordinates() {}

	public FocalPlaneCoordinates(String text) { super(text); } 

	public FocalPlaneCoordinates(double x, double y) { super(x, y); }

	@Override
	public void setDefaultCoordinates() {
		setCoordinateSystem(defaultCoordinateSystem);
		setLocalCoordinateSystem(defaultLocalCoordinateSystem);		
	}

}