package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class PrivateConnectionListener extends Thread {

	private String host;
	private int port;
	private PrintStream userPrintStream;
	private ServerSocket serverSocket;

	public PrivateConnectionListener(String host, int port, PrintStream userPrintStream) {
		this.host = host;
		this.port = port;
		this.userPrintStream = userPrintStream;
	}

	@Override
	public void run() {

		try {

			this.serverSocket = new ServerSocket();
			this.serverSocket.bind(new InetSocketAddress(this.host, this.port));

			while (!(Thread.currentThread().isInterrupted())) {
				Socket socket = this.serverSocket.accept();
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				// prepare the writer for responding to clients requests
				PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
				String response;

				while ((response = reader.readLine()) != null) {
					this.userPrintStream.println(response);
					writer.println("!ack");
				}
				reader.close();
				writer.close();
				socket.close();
			}

		} catch (UnknownHostException e) {
			this.userPrintStream.println("Wrong hostname specified");
		} catch (IOException e) {
			this.userPrintStream.println("Connection closed to chatserver");
		}

	}

	public void close() {
		Thread.currentThread().interrupt();
		try {
			this.serverSocket.close();
		} catch (IOException e) {
			this.userPrintStream.println(e.getMessage());
		}
	}

}
