package test;

import util.Unit;
import util.data.Data2D;
import crush.astro.AstroMap;

public class RegridTest {

	public static void main(String[] args) {
		try {
			AstroMap map = new AstroMap();
			map.read("/home/pumukli/data/sharc2/images/VESTA.8293.fits");
			
			map.setVerbose(true);
			map.setInterpolationType(Data2D.BICUBIC_SPLINE);
			map.regrid(1.0 * Unit.arcsec);
			//map.clean();
			map.fileName = "test.fits";
			map.write();
			
		}
		catch(Exception e) {
			e.printStackTrace();			
		}
	}
	
	
}
