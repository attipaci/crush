/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;

import crush.CRUSH;
import jnum.Configurator;


public class DRPMessenger extends Thread {
	private ArrayBlockingQueue<Message> queue;
	private InetSocketAddress address;
	private String senderID = "hawc.pipe.step.crush";
	private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
	private boolean isTimestamping = true;
	
	public DRPMessenger(Configurator options) throws IOException {
		String host = options.isConfigured("host") ? options.get("host").getValue() : DEFAULT_HOST;
		int port = options.isConfigured("port") ? options.get("port").getInt() : DEFAULT_DRP_PORT;
		
		if(options.isConfigured("timeout")) setTimeout((int) Math.ceil(1000.0 * options.get("timeout").getDouble()));
		if(options.isConfigured("id")) setSenderID(options.get("id").getValue());
		setTimestamping(options.isConfigured("timestamp"));
		
		int capacity = options.isConfigured("fifo") ? options.get("fifo").getInt() : DEFAULT_QUEUE_CAPACITY;
	
		address = new InetSocketAddress(host, port);
		queue = new ArrayBlockingQueue<Message>(capacity);
		
		info("Hello!");
		
		//setDaemon(true); 
		start();
	}
	
	public synchronized void setTimestamping(boolean value) { isTimestamping = value; }
	
	public boolean isTimestamping() { return isTimestamping; }
	
	public void setSenderID(String id) { senderID = id; }
	
	public String getSenderID() { return senderID; }
	
	public void setTimeout(int millis) { timeoutMillis = millis; }
	
	public int getTimeout() { return timeoutMillis; }
	
	public void critical(String message) {
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
	
	
	private synchronized void send(Message message) throws IOException {
		if(message == null) return;
				
		Socket socket = new Socket();
		socket.setReuseAddress(true);
		socket.setPerformancePreferences(2, 1, 0); // connection, latency, bandwidth
		socket.setTcpNoDelay(true);
		socket.setTrafficClass(0x10);	// low delay
		socket.setSoTimeout(timeoutMillis);
		socket.connect(address);
			
		@SuppressWarnings("resource")
        OutputStream out = socket.getOutputStream();
		String text = message.toString();
		
		out.write(text.getBytes());
		
		if(CRUSH.debug) CRUSH.debug(this, "DRP> " + text.getBytes());
		
		out.flush();
		socket.close();
	}
	
	public void clear() {
		queue.clear();
	}
	
	
	public void shutdown() {
		interrupt();
		try { this.join(); }
		catch(InterruptedException e) {}	
	}
	
	@Override
	public void run() {
		CRUSH.info(this, "Starting DRP messaging service.");
		
		CRUSH.add(new Reporter());
		
		try { while(!isInterrupted()) send(queue.take()); }
		catch(IOException e) { 
		    CRUSH.warning(this, "DRP messaging: " + e.getMessage());     
		}		
		catch(InterruptedException e) {
		    CRUSH.removeReporter(DRP_REPORTER_ID); // prevent the creation of new DRP messages...
			if(!queue.isEmpty()) CRUSH.info(this, "Sending queued DRP messages...");
			try { while(!queue.isEmpty()) send(queue.take()); }
			catch(InterruptedException e2) { CRUSH.warning(this, "DRP queue cleanup interrupted.");}
			catch(IOException e2) { CRUSH.warning(this, "DRP messaging: " + e2.getMessage()); }
		}
		
		CRUSH.removeReporter(DRP_REPORTER_ID);
		
		CRUSH.info(this, "DRP messaging stopped.");
		
		clear();
	}
	
	
	private class Message {
		String type;
		String text;
		long timestamp;
		
		private Message(String type, String message) {
			timestamp = System.currentTimeMillis();
			this.type = type;
			this.text = message.replace('\t', ' ');	// Replace tabs with spaces since tabs are message delimiters.
			
			try { queue.put(this); }
			catch(InterruptedException e) { CRUSH.warning(this, "DRP message creation was interrupted."); }
		}
		
		@Override
		public String toString() {
			String line = type + "\t" + senderID + "\t" + text;
			
			int maxLength = MAX_MESSAGE_BYTES;
			if(isTimestamping()) maxLength -= (timePrefix.length() + timeFormatSpec.length());
			if(line.length() > maxLength) line = line.substring(0, maxLength-3) + "...";
		
			return line + (isTimestamping() ? timePrefix + timeFormat.format(timestamp) : "");
		}
	}
	
	public final static String TYPE_CRITICAL = "CRIT";
	public final static String TYPE_ERROR = "ERR";
	public final static String TYPE_WARNING = "WARN";
	public final static String TYPE_INFO = "INFO";
	public final static String TYPE_DEBUG = "DEBUG";
	
	public final static String DEFAULT_HOST = "127.0.0.1";
	public final static int DEFAULT_DRP_PORT = 50747;
	public final static int DEFAULT_QUEUE_CAPACITY = 100;
	public final static int DEFAULT_TIMEOUT_MILLIS = 1000;
	public final static int MAX_MESSAGE_BYTES = 1000;
	
	private final static String timePrefix = " @ ";
	private final static String timeFormatSpec = "HH:mm:ss.SSS";
	private final static DateFormat timeFormat = new SimpleDateFormat(timeFormatSpec);
	
	private final static String DRP_REPORTER_ID = "HAWC-DRP";
	
	static { timeFormat.setTimeZone(TimeZone.getTimeZone("UTC")); }

	
	private class Reporter extends jnum.reporting.Reporter {
	    
	    private Reporter() {
	        super(DRP_REPORTER_ID);
	    }
	    
	    @Override
	    public void info(Object owner, String message) {
	        // TODO 
	    }

	    @Override
	    public void notify(Object owner, String message) {
	        if(owner != DRPMessenger.this) DRPMessenger.this.info(message);
	    }

	    @Override
        public void debug(Object owner, String message) {
            if(CRUSH.debug) if(owner != DRPMessenger.this) DRPMessenger.this.debug(message);
        }
	    
	    @Override
	    public void warning(Object owner, String message) {
	        if(owner != DRPMessenger.this) DRPMessenger.this.warning(message);
	    }

	    @Override
	    public void error(Object owner, String message) {
	        if(owner != DRPMessenger.this) DRPMessenger.this.error(message);
	    }

	    @Override
	    public void trace(Throwable e) { 
	        StringWriter text = new StringWriter();
	        e.printStackTrace(new PrintWriter(text));
	        DRPMessenger.this.debug(text.toString());
	    }

	    @Override
	    public void status(Object owner, String message) {
	        if(owner != DRPMessenger.this) DRPMessenger.this.info(message);
	    }

	    @Override
	    public void result(Object owner, String message) {
	        if(owner != DRPMessenger.this) DRPMessenger.this.info(message);
	    }

	    @Override
	    public void detail(Object owner, String message) {
	        // TODO
	    }

	    @Override
	    public void values(Object owner, String message) {
	        // TODO
	    }

	    @Override
	    public void suggest(Object owner, String message) {
	        if(owner != DRPMessenger.this) DRPMessenger.this.debug(message);
	    }

	}

	
}
