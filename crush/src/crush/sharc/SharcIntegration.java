package crush.sharc;

import java.io.DataInput;
import java.io.IOException;

import crush.cso.CSOIntegration;

public class SharcIntegration extends CSOIntegration<Sharc, SharcFrame> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1534830845454096961L;
	
	public SharcIntegration(SharcScan parent) {
		super(parent);
	}

	@Override
	public SharcFrame getFrameInstance() {
		return new SharcFrame((SharcScan) scan);
	}

	
	public void readFrom(DataInput in) throws IOException {	
		SharcScan sharcScan = (SharcScan) scan;
		int cols = sharcScan.nsamples;
		ensureCapacity(cols);
		
		float iScale = 1.0F / sharcScan.scale_factor;
		
		//SharcFrame.scanIndexOffset = sharcScan.quadrature == 0 ? 2 : 4;
		
		for(int T=0, t=0; T<cols; T++) {
			SharcFrame frame = new SharcFrame(sharcScan);
			frame.readFrom(in, t++, iScale);								// the 'in-phase' data
			add(frame);
			
			if(sharcScan.quadrature != 0) {
				frame = new SharcFrame(sharcScan);
				frame.readFrom(in, t++, -iScale);							// the 'quadrature' data
				add(frame);
			}
		}
	}
	
}
