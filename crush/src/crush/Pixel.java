/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2010 Attila Kovacs

package crush;

import util.*;

public interface Pixel extends Iterable<Channel> {

	public int getIndex();
	
	public int getFixedIndex();

	public boolean isValid();
	
	public Vector2D getPosition();

	public double distanceTo(Pixel pixel);
	
	public double getResolution();	
		
	public int channels();
	
	public Channel getChannel(int i);
	
	public void setIndependent(boolean value);
	
	public String getRCPString();
	
	public final static int FLAG_XACCEL = 1 << 0;
	public final static int FLAG_YACCEL = 1 << 1;
	
	public final static int LAST_FLAG = FLAG_YACCEL;
}
