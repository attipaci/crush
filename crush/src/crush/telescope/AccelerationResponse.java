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
package crush.telescope;

import crush.Integration;
import crush.Signal;

public class AccelerationResponse extends MotionResponse {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6674558238592018995L;

	public AccelerationResponse() {
		super();
		// TODO Auto-generated constructor stub
	}

	@Override
	public Signal getSignal(Integration<?> integration, Motion direction) {
		return integration.getAccelerationSignal(this, direction);
	}	
}
