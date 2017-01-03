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

import chatserver.Channel;

public class PrivateConnectionListener extends Thread implements Channel {

	private String host;
	private int port;
	private PrintStream userPrintStream;
	private ServerSocket serverSocket;
	private BufferedReader reader;
	private PrintWriter writer;

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
				String response;
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				writer = new PrintWriter(socket.getOutputStream(), true);
				while ((response = reader.readLine()) != null) {
					this.userPrintStream.println(response);
					// writer.println("!ack");
					write("!ack");
				}
				reader.close();
				writer.close();
				socket.close();
			}

		} catch (UnknownHostException e) {
			this.userPrintStream.println("Wrong hostname specified");
			// userPrintStream+Printwriter both cant be generalized atm
			// will writer suffice? (security concerns)
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

	@Override
	public void write(String output) throws IOException {
		writer.println(output);
	}

	@Override
	public String read() throws IOException {
		String input;
		input = reader.readLine();
		return input;
	}

}
