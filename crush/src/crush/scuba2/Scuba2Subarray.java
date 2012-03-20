/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of the proprietary SCUBA-2 modules of crush.
 * 
 * You may not modify or redistribute this file in any way. 
 * 
 * Together with this file you should have received a copy of the license, 
 * which outlines the details of the licensing agreement, and the restrictions
 * it imposes for distributing, modifying or using the SCUBA-2 modules
 * of CRUSH-2. 
 * 
 * These modules are provided with absolutely no warranty.
 ******************************************************************************/
package crush.scuba2;

import util.*;

import java.util.*;

public class Scuba2Subarray implements Cloneable {
	String id;
	Vector2D pixelSize = Scuba2Pixel.defaultSize, apertureOffset = new Vector2D();
	double orientation = 0.0;
	boolean isMirrored = false;
	
	public Scuba2Subarray(String id) {
		this.id = id;
	}
	
	@Override 
	public Object clone() {
		try { return super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	public Scuba2Subarray copy() {
		Scuba2Subarray copy = (Scuba2Subarray) clone();
		if(pixelSize != null) copy.pixelSize = (Vector2D) pixelSize.clone();
		if(apertureOffset != null) copy.apertureOffset = (Vector2D) apertureOffset.clone();
		if(id != null) copy.id = new String(id);
		return copy;
	}

	public Vector2D getPixelPosition(double mux, double pin) {
		Vector2D position = new Vector2D();
		getPixelPosition(mux, pin, position);
		return position;
	}
	
	public void getPixelPosition(double mux, double pin, Vector2D position) {
		position.setX((isMirrored ? -1.0 : 1.0) * pixelSize.getX() * (pin - 19.5));
		position.setY(pixelSize.getY() * (mux - 15.5));
		position.rotate(orientation);
		position.add(apertureOffset);
	}
	
	public void setOptions(Scuba2 scuba2) {
		if(scuba2.hasOption(id + ".pixelsize")) {
			pixelSize = new Vector2D();
			StringTokenizer tokens = new StringTokenizer(scuba2.option(id + ".pixelsize").getValue(), " \t,:xX");
			pixelSize.setX(Double.parseDouble(tokens.nextToken()) * Unit.arcsec);
			pixelSize.setY(tokens.hasMoreTokens() ? Double.parseDouble(tokens.nextToken()) * Unit.arcsec : pixelSize.getX());
		}
		if(scuba2.hasOption(id + ".rotation"))
			orientation = scuba2.option(id + ".rotation").getDouble() * Unit.deg;
		if(scuba2.hasOption(id + ".position")) {
			apertureOffset = scuba2.option(id + ".position").getVector2D();
			apertureOffset.scale(scuba2.getDefaultSizeUnit());
		}
		isMirrored = scuba2.hasOption(id + ".mirror");
	}
}
