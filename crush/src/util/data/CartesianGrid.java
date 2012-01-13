package util.data;

import util.*;

import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;


public class CartesianGrid extends Grid2D<CoordinatePair> {

	public CartesianGrid() {
		projection = new DefaultProjection2D();
	}
	
	@Override
	public void parseProjection(Header header) throws HeaderCardException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Vector2D getCoordinateInstanceFor(String type) throws InstantiationException, IllegalAccessException {
		return new Vector2D();
	}

}
