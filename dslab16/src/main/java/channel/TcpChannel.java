package channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;

import channel.Channel;
import chatserver.Chatserver;

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

	public TcpChannel(Socket socket, Chatserver chatserver, PrintStream userResponseStream) {
		this.clientSocket = socket;
		this.chatserver = chatserver;
		this.userResponseStream = userResponseStream;
		try {
			// prepare the input reader for the socket
			reader = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
			// prepare the writer for responding to clients requests
			this.writer = new PrintWriter(clientSocket.getOutputStream(), true);
		} catch (IOException e) {
			System.out.println("Failed to initialize TcpChannel (reader/writer)");
			e.printStackTrace();
		}

	}

	@Override
	public void run() {

		try {
			String request;
			// read client requests
			while ((request = read())!= null) {
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
					response = "Unknown request: " + request;
					break;
				}
				write(response);
				// writer.println(response);
			}

		} catch (IOException e) {
			this.userResponseStream.println("Connection closed to clients");
			Thread.currentThread().interrupt();
			if (this.user != null) {
				chatserver.logoutUser(clientSocket);
			}
		} finally {
			if (this.clientSocket != null && !this.clientSocket.isClosed())
				try {
					this.clientSocket.close();
				} catch (IOException e) {
					// Ignored because we cannot handle it
				}

		}
	}

	@Override
	public String read() throws IOException {
		String input;
		input=this.reader.readLine();
		return input;
	}

	@Override
	public void write(String output) throws IOException {
		this.writer.println(output);
		
	}
}
