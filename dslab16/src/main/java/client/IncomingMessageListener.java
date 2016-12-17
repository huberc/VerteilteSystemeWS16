package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IncomingMessageListener extends Thread {

	private Socket socket;
	private PrintStream userResponseStream;
	private String name;
	private Client client;
	private BufferedReader serverReader;

	private static final ThreadLocal<DateFormat> DATE_FORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("HH:mm:ss.SSS");
		}
	};

	public IncomingMessageListener(Socket socket, PrintStream userPrintStream, String name, Client client) {
		this.socket = socket;
		this.userResponseStream = userPrintStream;
		this.name = name;
		this.client = client;
	}

	public void run() {
		String response;
		Pattern pattern = Pattern
				.compile("\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}[:]\\d{1,5}|([\\w\\.\\-]+):(\\d{1,3})");
		Matcher matcher;
		try {
			// create a reader to retrieve messages send by the server
			this.serverReader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
			while (!(Thread.currentThread().isInterrupted()) && (response = this.serverReader.readLine()) != null) {
				matcher = pattern.matcher(response);
				if (matcher.find()) {
					String lastCommand = this.client.popLastCommand();
					String nextToLastCommand = this.client.popLastCommand();
					if (lastCommand.equals("lookup")) {
						if (nextToLastCommand == null || !nextToLastCommand.equals("msg")) {
							this.userResponseStream.println(String.format("%s: %s",name, response));
						} else if (nextToLastCommand.equals("msg")) {
							synchronized (this) {
								this.client.setPrivateAddressReceiver(matcher.group(0));
								notify();
							}
						}
					}

				} else if (response.contains("!public ")) {
					this.client.setLastPublicMessage(response.replace("!public ", ""));
					this.userResponseStream.println(String.format("%s: %s",name, response.replace("!public ", "")));
				} else {
					if (response.contains("not registered") || response.contains("successfully registered address")) {
						synchronized (this) {
							notify();
						}
					}
					this.userResponseStream.println(String.format("%s: %s%n",name, response));
				}
			}
		} catch (IOException e) {
			this.userResponseStream.println("Connection closed to chatserver");
		} finally {
			if (this.socket != null && !this.socket.isClosed())
				try {
					this.socket.close();
				} catch (IOException e) {
					// Ignored because we cannot handle it
				}
		}

	}
	
	public void close(){
		Thread.currentThread().interrupt();
		try {
			this.serverReader.close();
			this.userResponseStream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
