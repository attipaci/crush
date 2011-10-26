package util.data;

import util.SphericalCoordinates;
import util.SphericalProjection;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;

public class SphericalGrid extends Grid2D<SphericalCoordinates, SphericalProjection> {

	@Override
	public boolean isReverseX() { return projection.getReference().isReverseLongitude(); }
	
	@Override
	public boolean isReverseY() { return projection.getReference().isReverseLatitude(); }
	
	@Override
	public void parseProjection(Header header) throws HeaderCardException {
		String type = header.getStringValue("CTYPE1" + alt);
	
		try { projection = SphericalProjection.forName(type.substring(5, 8)); }
		catch(Exception e) { System.err.println("ERROR! Unknown projection " + type.substring(5, 8)); }
	}
	
	@Override
	public SphericalCoordinates getCoordinateInstanceFor(String type) throws InstantiationException, IllegalAccessException {
		Class<? extends SphericalCoordinates> coordClass = SphericalCoordinates.getFITSClass(type);
		return coordClass.newInstance();
	}
	
}
