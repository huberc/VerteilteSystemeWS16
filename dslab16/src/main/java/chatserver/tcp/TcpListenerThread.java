package chatserver.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;

import chatserver.Chatserver;

/**
 * Thread to listen for incoming connections on the given socket.
 */
public class TcpListenerThread extends Thread {

	private PrintWriter writer;
	private Socket clientSocket;
	private String user;
	private Chatserver chatserver;
	private PrintStream userResponseStream;

	public TcpListenerThread(Socket socket, Chatserver chatserver, PrintStream userResponseStream) {
		this.clientSocket = socket;
		this.chatserver = chatserver;
		this.userResponseStream = userResponseStream;
	}

	@Override
	public void run() {

		try {
			// prepare the input reader for the socket
			BufferedReader reader = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));

			// prepare the writer for responding to clients requests
			this.writer = new PrintWriter(clientSocket.getOutputStream(), true);

			String request;
			// read client requests
			while ((request = reader.readLine()) != null) {
				String[] commandParts = request.split("\\s");
				String response = "";

				switch (commandParts[0]){
					case "!login":
						response = chatserver.loginUser(commandParts[1], commandParts[2], this.clientSocket);
						break;
					case "!logout":
						response = chatserver.logoutUser(this.clientSocket);
						break;
					case "!send":
						String msg = request.substring(commandParts[0].length() + 1, request.length());
						response = this.chatserver.sendPublicMessage(this.clientSocket,msg);
						break;
					case "!lookup":
						response = chatserver.lookup(commandParts[1],clientSocket);
						break;
					case "!register":
						response = chatserver.registerUserAddress(clientSocket, commandParts[1]);
						break;
					default:
						response = "Unknown request: " + request;
						break;
				}

				writer.println(response);
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
}
