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
	private Socket socket;
	private String user;
	private boolean loggedIn = false;
	private Chatserver chatserver;
	private PrintStream userResponseStream;

	public TcpListenerThread(Socket socket, Chatserver chatserver, PrintStream userResponseStream) {
		this.socket = socket;
		this.chatserver = chatserver;
		this.userResponseStream = userResponseStream;
	}

	@Override
	public void run() {

		try {

			this.chatserver.addThread(this);

			// prepare the input reader for the socket
			BufferedReader reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
			// prepare the writer for responding to clients requests
			this.writer = new PrintWriter(this.socket.getOutputStream(), true);

			String request;
			// read client requests
			while ((request = reader.readLine()) != null) {
				String[] commandParts = request.split("\\s");
				if (commandParts[0].equals("!login") && commandParts.length == 3) {
					this.writer.println(this.login(commandParts[1], commandParts[2]));
				} else if (commandParts[0].equals("!logout")) {
					this.writer.println(this.logout());
				} else if (commandParts[0].equals("!send")) {
					if (this.loggedIn)
						this.writer.println((this.sendPublicMessage(request.replace("!send ", "")) == true)
								? "Public message has been sent to all connected clients" : "Something went wrong!");
					else
						this.writer.println("You have to log in first!");
				} else if (commandParts[0].equals("!lookup")) {
					String response = this.chatserver.getPrivateAddress(commandParts[1]);
					this.writer.println((response != null) ? response : "Wrong username or user not registered .");

				} else if (commandParts[0].equals("!register")) {
					if (this.loggedIn) {
						this.writer.println(this.chatserver.registerClientIpAddress(this.user,
								request.replace(commandParts[0] + " ", "")));
					} else {
						this.writer.println("You have to log in first!");
					}

				}
			}

		} catch (IOException e) {
			this.userResponseStream.println("Connection closed to clients");
			Thread.currentThread().interrupt();
			if (this.user != null) {
				this.chatserver.clearLoggedInUsers(this.user);
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

	private String login(String username, String password) {
		if (this.loggedIn == false && (!this.chatserver.checkAlreadyLoggedIn(username))) {
			boolean succesful = this.chatserver.checkUserCredentials(username, password);
			if (succesful) {
				this.user = username;
				this.loggedIn = true;
				return "Successfully logged in.";
			} else {
				return "Wrong username or password .";
			}
		} else {
			return "Already logged in.";
		}
	}

	private String logout() {

		if (this.loggedIn) {
			this.loggedIn = false;
			this.chatserver.logout(this.user);
			this.user = "";
			return "Successfully logged out .";
		} else {
			return "Not logged in.";
		}
	}

	private boolean sendPublicMessage(String message) {
		return this.chatserver.sendPublicMessage(message, this.user);

	}

	public void sendMessage(String message) {
		synchronized (this.writer) {
			this.writer.println(message);
		}
	}

	public String getUser() {
		return this.user;
	}
}
