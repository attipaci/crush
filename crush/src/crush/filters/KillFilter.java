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
package crush.filters;

import java.util.List;

import crush.Integration;
import jnum.Constant;
import jnum.math.Range;

public class KillFilter extends FixedFilter {
	/**
	 * 
	 */
	private static final long serialVersionUID = 580288477345827863L;
	
	protected boolean[] reject;
		
	public KillFilter(Integration<?> integration) {
		super(integration);
	}
	
	public KillFilter(Integration<?> integration, float[] data) {
		super(integration, data);
	}
	
	public boolean[] getRejectMask() { return reject; }
	
	public void setRejectMask(boolean[] mask) { reject = mask; }
	
	@Override
	protected void setIntegration(Integration<?> integration) {
		super.setIntegration(integration);
		reject = new boolean[nf+1];
	}
	
	public void kill(Range fRange) {
		int fromf = Math.max(0, (int)Math.floor(fRange.min() / df));
		int tof = Math.min(nf, (int)Math.ceil(fRange.max() / df));
		
		if(fromf > tof) return;
		
		for(int f=fromf; f<=tof; f++) reject[f] = true;
		
		autoDFT();
	}

	@Override
	public void updateConfig() {
		super.updateConfig();
		
		if(hasOption("bands")) {
			final List<String> ranges = option("bands").getList();
			for(String rangeSpec : ranges) kill(Range.from(rangeSpec, true));			
		}
	}
	
	public void autoDFT() {
		// DFT 51 ops per datum, per rejected complex frequency...
		int dftreq = 51 * (int) countParms() * integration.size();
		
		// 2xFFT (forth and back) with 31 ops each loop, 9.5 ops per datum, 34.5 ops per datum rearrange...
		int fftreq = 2 * (31 * (int) Math.round(Math.log(nt) / Constant.log2) * nt + 44 * nt); 
	
		setDFT(dftreq < fftreq);
	}
		
	@Override
	protected double responseAt(int fch) {
		if(fch == reject.length) return reject[1] ? 0.0 : 1.0;
		return reject[fch] ? 0.0 : 1.0;		
	}

	@Override
	protected double countParms() {
		int n = 0;
		for(int i=getHipassIndex(); i<reject.length; i++) if(reject[i]) n++;
		return n;
	}

	@Override
	public String getID() {
		return "K";
	}

	@Override
	public String getConfigName() {
		return "filter.kill";
	}

}
