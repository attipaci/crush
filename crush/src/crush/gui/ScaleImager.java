/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
package crush.gui;

import crush.gui.colorscheme.*;
import util.*;

public abstract class ScaleImager extends AbstractImager {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6845728740095618762L;
	
	public ColorScheme colorScheme = new GreyScale();
	public Scale scale;
	
	public int getRGB(double value) {
		return Double.isNaN(value) ? colorScheme.noData : colorScheme.getRGB(scale.getScaled(value));
	}
	
}
