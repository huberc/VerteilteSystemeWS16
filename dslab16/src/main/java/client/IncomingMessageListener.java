package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import channel.Base64Channel;
import channel.Channel;
import channel.SecureChannel;
import org.bouncycastle.util.encoders.Base64;
import security.AuthenticationException;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class IncomingMessageListener extends Thread implements Channel {

	private Socket socket;
	private PrintStream userResponseStream;
	private String name;
	private Client client;
	private BufferedReader serverReader;
	private Channel decoratorChannel;

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
			if(this.client.isLoggedIn()) {
				while (!(Thread.currentThread().isInterrupted()) && (response = new String(read())) != null) {
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
						} else if (lastCommand.equals("authenticate")) {
							this.client.setLastMessageFromServer(response);
							synchronized (this) {
								notify();
							}

						} else {
							write(response);
						}

					}
				}
			}else{

				byte[] response1;

				while (!(Thread.currentThread().isInterrupted()) && (response1 = read()) != null) {
					this.client.setMessageFromServer(response1);
					synchronized (this) {
						notify();
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

	public void setDecoratorChannel(SecretKey secretKey, IvParameterSpec ivParameterSpec){
		try {
			this.decoratorChannel = new Base64Channel(new SecureChannel(this, secretKey, ivParameterSpec));
		} catch (AuthenticationException e) {
			e.printStackTrace();
		}
	}

	@Override
	public byte[] read() throws IOException {
		String input;
		input = this.serverReader.readLine();
		return (input != null ? Base64.decode(input) : null);
	}

	@Override
	public void write(String output) throws IOException {
		this.userResponseStream.println(String.format("%s: %s", name, output));
	}

}
