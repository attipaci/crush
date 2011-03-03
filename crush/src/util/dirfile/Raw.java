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
// Copyright (c) 2010 Attila Kovacs 

package util.dirfile;

import java.io.*;
import util.*;

// Reads Little-Endian from stream...
public abstract class Raw<Type extends Number> extends DataStore<Type> {
	RandomAccessFile file;
	String path;
	byte[] buf = new byte[8];
	protected int bytes;
	int samples;
	boolean isBigEndian = false;
	
	public Raw(String path, String name, int arraySize) {
		super(name);
		this.path = path;
		samples = arraySize;
	}
	
	public void open() throws IOException {
		file = new RandomAccessFile(getFile(), "r");		
	}
	
	public void close() throws IOException {
		file.close();
		file = null;
	}
	
	public File getFile() {
		return new File(path + File.separator + name);		
	}
	
	@Override
	public int getSamples() {
		return samples;
	}

	@Override
	public long length() throws IOException {
		if(file != null) return file.length() / bytes;
		else return getFile().length() / bytes;
	}
	
	protected byte getByte(long n) throws IOException {
		if(file == null) open();
		file.seek(n);
		return file.readByte();
	}
	
	protected short getUnsignedByte(long n) throws IOException {
		return Util.unsigned(getByte(n));
	}
	
	protected short getShort(long n) throws IOException {
		if(isBigEndian) return file.readShort();
		return (short) getUnsignedShort(n);
	}
	
	protected int getUnsignedShort(long n) throws IOException {
		if(file == null) open();
		file.seek(n<<1);
		if(isBigEndian) return Util.unsigned(file.readShort());
		file.read(buf, 0, 2);
		return ((buf[1] & 0xff)<<8 | (buf[0] & 0xff));
	}
	
	protected int getInt(long n) throws IOException {
		if(file == null) open();
		file.seek(n << 2);
		if(isBigEndian) return file.readInt();
		file.read(buf, 0, 4);
		return (buf[3] & 0xff)<<24 | (buf[2] & 0xff)<<16 | (buf[1] & 0xff)<<8 | (buf[0] & 0xff);
	}
	
	protected long getUnsignedInt(long n) throws IOException {
		return Util.unsigned(getInt(n));
	}

	protected long getLong(long n) throws IOException {
		if(file == null) open();
		file.seek(n << 3);
		if(isBigEndian) return file.readLong();
		file.read(buf);
		return ((long)(buf[3] & 0xff)<<56 | (long)(buf[2] & 0xff)<<48 | (long)(buf[1] & 0xff)<<40 | (long)(buf[0] & 0xff)<<32 |
					  (long)(buf[3] & 0xff)<<24 | (long)(buf[2] & 0xff)<<16 | (long)(buf[1] & 0xff)<<8 | (buf[0] & 0xff));
	}
	
	protected long getUnsignedLong(long n) throws IOException {
		return Util.pseudoUnsigned(getLong(n));
	}
	
	protected float getFloat(long n) throws IOException {
		return Float.intBitsToFloat(getInt(n));
	}
	
	protected double getDouble(long n) throws IOException {
		return Double.longBitsToDouble(getLong(n));
	}
	
	protected char getChar(long n) throws IOException {
		if(file == null) open();
		file.seek(n << 1);
		if(isBigEndian) return file.readChar();
		file.read(buf, 0, 2);
		return (char) ((buf[1] & 0xff)<<8 | (buf[0] & 0xff));
	}
	

	public static Raw<?> forSpec(String path, String name, String type, int elements) {
			
		if(type.length() == 1) switch(type.charAt(0)) {
		case 'u' : return new UShortStore(path, name, elements); 
		case 'U' : return new UIntegerStore(path, name, elements);
		case 's' : return new ShortStore(path, name, elements);
		case 'S' : return new IntegerStore(path, name, elements);
		case 'i' : return new IntegerStore(path, name, elements);
		case 'c' : return new UByteStore(path, name, elements);
		case 'f' : return new FloatStore(path, name, elements);
		case 'd' : return new DoubleStore(path, name, elements);
		default : return null;
		}
		
		type = type.toLowerCase();
		
		if(type.equals("float")) return new FloatStore(path, name, elements);
		else if(type.equals("double")) return new DoubleStore(path, name, elements);
		else if(type.startsWith("uint")) {
			int bits = Integer.parseInt(type.substring(4));
			switch(bits) {
			case 8 : return new UByteStore(path, name, elements); 
			case 16 : return new UShortStore(path, name, elements); 
			case 32 : return new UIntegerStore(path, name, elements); 
			case 64 : return new ULongStore(path, name, elements);
			default : return null;
			}
		}
		else if(type.startsWith("int")) {
			int bits = Integer.parseInt(type.substring(3));
			switch(bits) {
			case 8 : return new ByteStore(path, name, elements); 
			case 16 : return new ShortStore(path, name, elements); 
			case 32 : return new IntegerStore(path, name, elements); 
			case 64 : return new LongStore(path, name, elements); 
			default : return null;
			}
		}
		else if(type.startsWith("float")) {
			int bits = Integer.parseInt(type.substring(5));
			switch(bits) {
			case 32 : return new FloatStore(path, name, elements);
			case 64 : return new DoubleStore(path, name, elements);
			default : return null;
			}
		}
		else return null;
	}

}


