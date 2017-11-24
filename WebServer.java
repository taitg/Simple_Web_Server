/*
 * CPSC 441 Assignment 2
 * Fall 2016
 * 
 * Geordie Tait		10013837
 * November 4, 2016
 */

import java.net.Socket;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;

/**
 * WebServer class
 * 
 * @author Geordie Tait
 *
 */
public class WebServer extends Thread {
	
	// flag for if the WebServer should continue the listening loop
	private volatile boolean shutdown = false;
	
	// port for the WebServer to listen on
	private int port = 2225;

	/**
	 * Constructor for WebServer
	 * 
	 * @param port	Port number to listen on
	 */
	public WebServer(int port) {
		// initialization
		this.port = port;
	}
	
	/* Main method for main WebServer thread which listens for incoming requests/connections
	 * 
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	public void run() {
		ServerSocket serverSock = null;
		try {
			// open server socket
			serverSock = new ServerSocket(port);
			
			// set socket timeout option using setSoTimeout(1000)
			serverSock.setSoTimeout(1000);

			// main loop for listening WebServer
			while (!shutdown) {
				try {
					// accept new connection
					Socket clientSock = serverSock.accept();
					
					// create worker thread to handle new connection
					WebServerWorker worker = new WebServerWorker(clientSock);
					(new Thread(worker)).start();
				}
				catch (SocketTimeoutException e) {
					// do nothing, this is OK
					// allows the process to check shutdown flag
				}
			}
			
			// clean up (e.g., close the socket)
			serverSock.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Signals the WebServer to shut down
	 */
	public void shutdown() {
		shutdown = true;
	}
	
	/**
	 * Class for worker threads which serve requests
	 * 
	 * @author Geordie Tait
	 *
	 */
	public class WebServerWorker implements Runnable {
		// socket for worker threads to connect through
		private Socket sock = null;
		
		/**
		 * Constructor for worker threads
		 * 
		 * @param sock	Socket to serve client requests over
		 */
		public WebServerWorker(Socket sock) {
			this.sock = sock;
		}

		/* Main worker thread method for serving requests
		 * 
		 * (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			boolean badRequest = false;
			boolean notFound = false;
			byte[] bytes = new byte[16384];
			String fileName = "";
			
			try {
				// parse HTTP request
				BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
				OutputStream out = sock.getOutputStream();
				
				String request = in.readLine();
				
				// ensure request is well formed
				String[] reqSplit = request.split("/");
				if (reqSplit.length != 3) {
					// bad request
					badRequest = true;
				}
				else {
					if (!reqSplit[0].trim().equals("GET")) {
						// bad request
						badRequest = true;
					}
					
					String[] fileNameSplit = reqSplit[1].split(" ");
					if (fileNameSplit.length != 2) {
						// bad request
						badRequest = true;
					}
					else {
						if (!fileNameSplit[1].trim().equals("HTTP")) {
							// bad request
							badRequest = true;
						}
						fileName = fileNameSplit[0];
						
						// default to index.html if filename is empty
						// THIS MAY BE WRONG TO DO HERE?
						if (fileName.equals("")) fileName = "index.html";
					}
				}
				
				// determine if requested object exists
				File f = new File(fileName);
				if (!f.exists()) {
					// not found
					notFound = true;
				}
				
				// transmit content over existing connection
				String header = generateHeader(badRequest, notFound, f);
				out.write(header.getBytes("US-ASCII"));
				out.flush();
				
				// send file if OK
				if (!badRequest && !notFound) {					
					FileInputStream fStream = new FileInputStream(f);
		            BufferedInputStream fBuffer = new BufferedInputStream(fStream);
		            int n;
		            
		            while ((n = fBuffer.read(bytes)) > 0) {
		                out.write(bytes, 0, n);
		                out.flush();
		            }
		            
		            fBuffer.close();
		            fStream.close();
				}
				
				// close connection
				in.close();
				out.close();
				sock.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		/**
		 * Generate HTTP response header
		 * 
		 * @param badRequest	True if bad request
		 * @param notFound	True if file not found
		 * @param f	File object
		 * @return
		 */
		private String generateHeader(boolean badRequest, boolean notFound, File f) {
			String header = "HTTP/1.0 ";
			
			if (badRequest) header += "400 Bad Request\r\n";
			else if (notFound) header += "404 Not Found\r\n";
			else header += "200 OK\r\n";
			
			header += "Server: WebServer/1.0\r\n";
			
			if (!badRequest && !notFound) {
				SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss zzz");
				header += "Last-Modified: " + sdf.format(f.lastModified()) + "\r\n";
				header += "Content-Length: " + f.length() + "\r\n";
			}
			
			header += "Connection: close\r\n\r\n";
			return header;
		}
	}
}
