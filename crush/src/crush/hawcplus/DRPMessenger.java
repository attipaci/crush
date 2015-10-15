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

package crush.hawcplus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

import crush.CRUSH;


public class DRPMessenger extends Thread {
	private ArrayBlockingQueue<Message> queue;
	private InetSocketAddress address;
	private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
	
	public DRPMessenger(String host, int port) throws IOException {
		if(port < 0) port = DEFAULT_DRP_PORT;
		
		address = new InetSocketAddress(host, port);
		queue = new ArrayBlockingQueue<Message>(QUEUE_CAPACITY);
		
		info("Hello!");
		
		//setDaemon(true); 
		start();
	}
	
	public void setTimeout(int millis) { timeoutMillis = millis; }
	
	public int getTimeout() { return timeoutMillis; }
	
	public void crittical(String message) {
		new Message(TYPE_CRITICAL, message);
	}
	
	public void error(String message) {
		new Message(TYPE_ERROR, message);
	}

	public void warning(String message) {
		new Message(TYPE_WARNING, message);
	}

	public void info(String message) {
		new Message(TYPE_INFO, message);
	}
	
	public void debug(String message) {
		new Message(TYPE_DEBUG, message);
	}
	
	
	private void send(Message message) throws IOException {
		if(message == null) return;
		
		
		Socket socket = new Socket();
		socket.setReuseAddress(true);
		socket.setPerformancePreferences(2, 1, 0); // connection, latency, bandwidth
		socket.setTcpNoDelay(true);
		socket.setTrafficClass(0x10);	// low delay
		socket.setSoTimeout(timeoutMillis);
		socket.connect(address);
		//System.err.println("TCP Command connected to " + address.getHostName() + " port " + address.getPort());
		
		String line = message.type + "\t" + sender + "\t" + message.text;
		if(line.length() > MAX_MESSAGE_BYTES) line = line.substring(0, MAX_MESSAGE_BYTES-3) + "...";
	
		OutputStream out = socket.getOutputStream();
		out.write(line.getBytes());
		
		if(CRUSH.debug) System.err.println("DRP> " + line.getBytes());
		
		out.flush();
		
		socket.close();
	}
	
	public void clear() {
		queue.clear();
	}
	
	
	public void shutdown() {
		interrupt();
		try { 
			this.join();
		}
		catch(InterruptedException e) {}
		
	}
	
	@Override
	public void run() {
		System.err.println(" Starting DRP messaging service.");
		
		try { while(!isInterrupted()) send(queue.take()); }
		catch(IOException e) { CRUSH.warning("DRP messaging error: " + e.getMessage()); }		
		catch(InterruptedException e) {
			if(!queue.isEmpty()) System.err.println(" Sending queued DRP messages...");
			
			try { while(!queue.isEmpty()) send(queue.take()); }
			catch(InterruptedException e2) { CRUSH.warning("DRP queue cleanup interrupted.");}
			catch(IOException e2) { CRUSH.warning("DRP messaging error: " + e2.getMessage()); }
		}
		
		System.err.println(" DRP messaging stopped.");
		
		clear();
	}
	
	
	private class Message {
		String type;
		String text;
		
		private Message(String type, String message) {
			this.type = type;
			this.text = message.replace('\t', ' ');	// Replace tabs with spaces since tabs are message delimiters.
			
			try { queue.put(this); }
			catch(InterruptedException e) { CRUSH.warning("DRP message creation was interrupted."); }
		}
	}
	
	private static String sender = "hawc.pipe.step.crush";
	
	public final static String TYPE_CRITICAL = "CRIT";
	public final static String TYPE_ERROR = "ERR";
	public final static String TYPE_WARNING = "WARN";
	public final static String TYPE_INFO = "INFO";
	public final static String TYPE_DEBUG = "DEBUG";
	
	public final static String DEFAULT_HOST = "127.0.0.1";
	public final static int DEFAULT_DRP_PORT = 50747;
	public final static int QUEUE_CAPACITY = 100;
	public final static int DEFAULT_TIMEOUT_MILLIS = 1000;
	public final static int MAX_MESSAGE_BYTES = 1000;
	
}
