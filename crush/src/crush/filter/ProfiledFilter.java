/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.filter;

import crush.Integration;

public abstract class ProfiledFilter extends Filter {
	float[] profile;
	int rounds = 0;
	
	public ProfiledFilter(Integration<?, ?> integration) {
		super(integration);
		// TODO Auto-generated constructor stub
	}
	
	protected ProfiledFilter(Integration<?,?> integration, float[] data) {
		super(integration, data);
	}
	
	public void setProfile(float[] profile) {
		this.profile = profile;
	}

	@Override
	public void filter() {
		rounds++;
		super.filter();
	}
	
	@Override
	public double throughputAt(int fch) {
		if(profile == null) return 1.0;
		return Math.pow(profile[(int) Math.round((double) fch / (nf+1) * profile.length)], rounds);
	}

	@Override
	public double countParms() {
		final int minf = getMinIndex();
		if(profile == null) return 0.0;
		double parms = 0.0;
		for(int f=profile.length; --f >= minf; ) parms += 1.0 - profile[f] * profile[f];
		return parms;
	}

	

	
}
