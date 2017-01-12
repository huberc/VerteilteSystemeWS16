package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import channel.Channel;
import org.bouncycastle.util.encoders.Base64;

public class IncomingMessageListener extends Thread implements Channel {

	private Socket socket;
	private PrintStream userResponseStream;
	private String name;
	private Client client;
	private BufferedReader serverReader;

	public IncomingMessageListener(Socket socket, PrintStream userPrintStream, String name, Client client) {
		this.socket = socket;
		this.userResponseStream = userPrintStream;
		this.name = name;
		this.client = client;
		// create a reader to retrieve messages send by the server
		try {
			this.serverReader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
		} catch (IOException e) {
			System.out.println("Failed to initialize IncomingMessageReader");
			e.printStackTrace();
		}
	}

	public void run() {
		String response;
		Pattern pattern = Pattern
				.compile("\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}[:]\\d{1,5}|([\\w\\.\\-]+):(\\d{1,3})");
		Matcher matcher;
		try {
			while (!(Thread.currentThread().isInterrupted()) && (response = read()) != null) {
				matcher = pattern.matcher(response);
				if (matcher.find()) {
					String lastCommand = this.client.popLastCommand();
					String nextToLastCommand = this.client.popLastCommand();

					if (lastCommand.equals("lookup")) {
						if (nextToLastCommand == null || !nextToLastCommand.equals("msg")) {
							// this.userResponseStream.println(String.format("%s:
							// %s",name, response));
							write(response);
						} else if (nextToLastCommand.equals("msg")) {
							synchronized (this) {
								this.client.setPrivateAddressReceiver(matcher.group(0));
								notify();
							}
						}
					}

				} else if (response.contains("!public ")) {
					this.client.setLastPublicMessage(response.replace("!public ", ""));
					response.replace("!public", "");
					// this.userResponseStream.println(String.format("%s:
					// %s",name, response.replace("!public ", "")));
					write(response);
				} else if (response.contains("Already logged in")) {
					this.client.setLoginStatus(false);
					write(response);
				} else {
					String lastCommand = this.client.popLastCommand();
					if (response.contains("not registered") || response.contains("successfully registered address")) {
						synchronized (this) {
							notify();
						}
						write(response);
					}else if(lastCommand.equals("authenticate")){
						this.client.setMessageFromServer(response);
						synchronized (this) {
							notify();
						}

					}else {
						write(response);
					}

				}
			}
		} catch (IOException e) {
			try {
				write("Connection closed");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			// this.userResponseStream.println("Connection closed to
			// chatserver");
		} finally {
			if (this.socket != null && !this.socket.isClosed())
				try {
					this.socket.close();
				} catch (IOException e) {
					// Ignored because we cannot handle it
				}
		}

	}

	public void close() {
		Thread.currentThread().interrupt();
		try {
			this.serverReader.close();
			this.userResponseStream.close();
		} catch (IOException e) {

			e.printStackTrace();
		}
	}

	@Override
	public String read() throws IOException {
		String input;
		input = this.serverReader.readLine();
		return new String(Base64.decode(input));
	}

	@Override
	public void write(String output) throws IOException {
		this.userResponseStream.println(String.format("%s: %s", name, output));
	}

}
