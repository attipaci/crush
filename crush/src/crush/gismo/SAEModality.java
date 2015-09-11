/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.gismo;

import crush.Modality;

public class SAEModality extends Modality<SAEResponse> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1188101098766206152L;
	
	public SAEModality(AbstractGismo instrument) throws NoSuchFieldException {
		super("sae", "E");
		if(!instrument.hasOption("read.sae")) return;
		
		for(GismoPixel pixel : instrument) add(new SAEResponse(pixel));
		setGainFlag(GismoPixel.FLAG_SAE);
	}
	
	public void init(GismoIntegration integration) {
		for(int i=size(); --i >= 0; ) {
			SAEResponse r = get(i);
			if(r.getChannelCount() > 0) r.initSignal(integration);
			else remove(i);
		}
	}

}
