package channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;

import channel.Channel;
import chatserver.AuthenticateHelper;
import chatserver.Chatserver;
import chatserver.Usermanager;
import model.User;
import security.AuthenticationException;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Thread to listen for incoming connections on the given socket.
 */
public class TcpChannel extends Thread implements Channel {

	private PrintWriter writer;
	private Socket clientSocket;
	private String user;
	private Chatserver chatserver;
	private PrintStream userResponseStream;
	private BufferedReader reader;
	private AuthenticateHelper authenticateHelper;
	private Channel decoratedChannel;
	private Channel decoratedChannel1;
	private Usermanager usermanager;
	private SecretKey secretKey;
	private IvParameterSpec ivParameterSpec;

	public TcpChannel(Socket socket, Chatserver chatserver, PrintStream userResponseStream, Usermanager usermanager) {
		this.clientSocket = socket;
		this.chatserver = chatserver;
		this.userResponseStream = userResponseStream;
		this.usermanager = usermanager;
		this.authenticateHelper = new AuthenticateHelper(chatserver,usermanager, this);
		try {
			// prepare the input reader for the socket
			reader = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
			// prepare the writer for responding to clients requests
			this.writer = new PrintWriter(clientSocket.getOutputStream(), true);
		} catch (IOException e) {
			System.out.println("Failed to initialize TcpChannel (reader/writer)");
			e.printStackTrace();
		}
		this.decoratedChannel = new Base64Channel(this);


	}

	@Override
	public void run() {

		try {
			String request;
			byte[] request1;
			// read client requests
			User user = this.usermanager.getUserBySocket(this.clientSocket);
			if(user == null || user.getAuthState() < 2){
				while ((request1 = this.decoratedChannel.read()) != null) {
					String response = authenticateHelper.handleMessage(request1, this.clientSocket);
					this.decoratedChannel.write(response);
				}
			}else if(user.getAuthState() == 2){
				if(this.secretKey!= null && this.ivParameterSpec != null)
				this.decoratedChannel1 = new Base64Channel(new SecureChannel(this, this.secretKey, this.ivParameterSpec));
			}else {
				if (this.decoratedChannel1 != null) {
					while ((request = new String(this.decoratedChannel1.read())) != null) {

						String[] commandParts = request.split("\\s");
						String response = "";

						switch (commandParts[0]) {
							case "!login":
								response = chatserver.loginUser(commandParts[1], commandParts[2], this.clientSocket);
								break;
							case "!logout":
								response = chatserver.logoutUser(this.clientSocket);
								break;
							case "!send":
								String msg = request.substring(commandParts[0].length() + 1, request.length());
								response = this.chatserver.sendPublicMessage(this.clientSocket, msg);
								break;
							case "!lookup":
								response = chatserver.lookup(commandParts[1]);
								break;
							case "!register":
								response = chatserver.registerUserAddress(this.clientSocket, commandParts[1]);
								break;
							default:
								//Encrypted Authenticate call
								break;
						}
						this.decoratedChannel.write(response);
						// writer.println(response);
					}
				}
			}

		} catch (AuthenticationException e) {
			this.userResponseStream.println("Error during the authentication: " + e.getMessage());
			Thread.currentThread().interrupt();
			if (this.user != null) {
				chatserver.logoutUser(clientSocket);
			}
		} catch (IOException e) {
			this.userResponseStream.println("Connection closed to clients");
			Thread.currentThread().interrupt();
			if (this.user != null) {
				chatserver.logoutUser(clientSocket);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (this.clientSocket != null && !this.clientSocket.isClosed())
				try {
					this.clientSocket.close();
				} catch (IOException e) {
					// Ignored because we cannot handle it
				}
		}
	}

	public void setSecretKey(SecretKey secretKey) {
		this.secretKey = secretKey;
	}

	public void setIvParameterSpec(IvParameterSpec ivParameterSpec) {
		this.ivParameterSpec = ivParameterSpec;
	}

	@Override
	public byte[] read() throws IOException {
		String input;
		input=this.reader.readLine();
		return input.getBytes();
	}

	@Override
	public void write(String output) throws IOException {
		this.writer.println(output);
	}
}
