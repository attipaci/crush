package crush.sharc;

import java.util.StringTokenizer;

import kovacs.math.Vector2D;
import kovacs.util.Unit;
import crush.Channel;
import crush.array.SimplePixel;

public class SharcPixel extends SimplePixel {
	boolean isBad = false;
		
	public SharcPixel(Sharc instrument, int backendIndex) {
		super(instrument, backendIndex);
		position = getPosition(backendIndex);
	}
	
	static Vector2D getPosition(double backendIndex) {
		return new Vector2D(0.0, (backendIndex - 0.5 * (Sharc.pixels+1)) * spacing); 
	}

	@Override
	public void parseValues(StringTokenizer tokens, int criticalFlags) {
		super.parseValues(tokens, criticalFlags);
		if(isBad) flag(Channel.FLAG_BLIND);
	}
	
	public final static double spacing = 5.0 * Unit.arcsec;
}
