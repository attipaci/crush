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
// Copyright (c) 2009,2010 Attila Kovacs

package crush;

import java.lang.reflect.*;

public class FieldGradientMode extends CorrelatedMode {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6661319100565424381L;
	private Field field;

	public FieldGradientMode(Field field) {
		this.field = field;
	}
	
	public FieldGradientMode(Field field, ChannelGroup<?> channels) {
		this.field = field;
		setChannels(channels);
	}
	
	public Field getField() { return field; }

	@Override
	public void setChannels(ChannelGroup<?> channels) {
		super.setChannels(channels);
		name += ":" + field.getName();
	}
	
	@Override
	public float[] getGains(boolean validate) throws Exception {
		float[] gains = super.getGains(validate);
		
		double sumwg = 0.0, sumw = 0.0;
		for(Channel channel : getChannels()) if(channel.flag == 0) {
			sumwg += channel.weight * field.getDouble(channel);
			sumw += channel.weight;	
		}
		
		float aveg = sumw > 0.0 ? (float)(sumwg / sumw) : 0.0F;

		for(int c=size(); --c >= 0; ) {
			Channel channel = getChannel(c);
			gains[c] = field.getFloat(channel) - aveg;
		}

		return gains;
	}
}

