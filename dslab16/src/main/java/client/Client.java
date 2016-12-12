package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import cli.Command;
import cli.Shell;
import util.Config;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private Shell shell;
	private Socket socket;
	private DatagramSocket datagramSocket;
	private PrintWriter serverWriter;
	private ExecutorService pool;
	private String lastPublicMessage;
	private String privateAddressReceiver;
	private IncomingMessageListener incomingMessageListener;
	private Stack<String> commandQueue = new Stack<String>();
	private PrivateConnectionListener privateConnectionListener;

	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Client(String componentName, Config config, InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		/*
		 * First, create a new Shell instance and provide the name of the
		 * component, an InputStream as well as an OutputStream. If you want to
		 * test the application manually, simply use System.in and System.out.
		 */
		this.shell = new Shell(componentName, userRequestStream, userResponseStream);
		/*
		 * Next, register all commands the Shell should support. In this example
		 * this class implements all desired commands.
		 */
		this.shell.register(this);

		this.pool = Executors.newFixedThreadPool(10);

	}

	@Override
	public void run() {
		/*
		 * Finally, make the Shell process the commands read from the
		 * InputStream by invoking Shell.run(). Note that Shell implements the
		 * Runnable interface. Thus, you can run the Shell asynchronously by
		 * starting a new Thread:
		 * 
		 * Thread shellThread = new Thread(shell); shellThread.start();
		 * 
		 * In that case, do not forget to terminate the Thread ordinarily.
		 * Otherwise, the program will not exit.
		 */
		// (new Thread(this.shell)).start();
		this.pool.execute(new Thread(this.shell));

		/*
		 * create a new tcp socket at specified host and port - make sure you
		 * specify them correctly in the client properties file(see
		 * client1.properties and client2.properties)
		 */
		try {
			this.socket = new Socket(this.config.getString("chatserver.host"), config.getInt("chatserver.tcp.port"));
			// create a writer to send messages to the server
			this.serverWriter = new PrintWriter(this.socket.getOutputStream(), true);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.userResponseStream.println(getClass().getName() + ":  up and waiting for commands!");
		this.incomingMessageListener = new IncomingMessageListener(this.socket, this.userResponseStream,
				this.componentName, this);
		this.pool.execute(incomingMessageListener);

	}

	@Override
	@Command
	public String login(String username, String password) throws IOException {
		this.commandQueue.add("login");
		this.serverWriter.println("!login " + username + " " + password);
		return null;
	}

	@Override
	@Command
	public String logout() throws IOException {
		this.commandQueue.add("logout");
		this.serverWriter.println("!logout");
		return null;
	}

	@Override
	@Command
	public String send(String message) throws IOException {
		this.commandQueue.add("send");
		this.serverWriter.println("!send " + message);
		return null;
	}

	@Override
	@Command
	public String list() throws IOException {
		this.commandQueue.add("list");
		byte[] buffer;
		DatagramPacket packet;
		this.datagramSocket = new DatagramSocket();
		buffer = "!list".getBytes();
		// create the datagram packet with all the necessary information
		// for sending the packet to the server
		packet = new DatagramPacket(buffer, buffer.length,
				InetAddress.getByName(this.config.getString("chatserver.host")),
				this.config.getInt("chatserver.udp.port"));

		// send request-packet to server
		this.datagramSocket.send(packet);

		buffer = new byte[1024];
		// create a fresh packet
		packet = new DatagramPacket(buffer, buffer.length);
		// wait for response-packet from server
		this.datagramSocket.receive(packet);

		return new String(packet.getData());
	}

	@Override
	@Command
	public String msg(String username, String message) throws IOException {
		this.commandQueue.add("msg");
		this.lookup(username);
		synchronized (this.incomingMessageListener) {
			try {
				this.incomingMessageListener.wait();
			} catch (InterruptedException e) {
				this.userResponseStream.println("Error in client server communication");
			}
		}
		if (this.privateAddressReceiver != null) {
			String[] address = (this.privateAddressReceiver.split("[:]"));
			String host = address[0];
			int port = Integer.parseInt(address[1]);

			try {
				Socket privateSocket = new Socket(host, port);

				PrintWriter privateSocketWriter = new PrintWriter(privateSocket.getOutputStream(), true);
				BufferedReader privateSocketReader = new BufferedReader(
						new InputStreamReader(privateSocket.getInputStream()));

				privateSocketWriter.println(message);
				String response;
				if ((response = privateSocketReader.readLine()) != null) {
					if (response.equals("!ack")) {
						this.userResponseStream.println(username + " responded with !ack");
					} else {
						this.userResponseStream.println("Something went wrong");
					}
				}
				privateSocketWriter.close();
				privateSocketReader.close();
				privateSocket.close();

			} catch (UnknownHostException e) {
				this.userResponseStream.println("Wrong hostname specified");
			} catch (IOException e) {
				this.userResponseStream.println("Connection closed to the other user");
			}

		}
		return null;
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {
		this.commandQueue.add("lookup");
		this.serverWriter.println("!lookup " + username);
		return null;
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {
		this.commandQueue.add("register");
		this.serverWriter.println("!register " + privateAddress);
		synchronized (this.incomingMessageListener) {
			try {
				this.incomingMessageListener.wait();
			} catch (InterruptedException e) {
				this.userResponseStream.println("Error in client server communication");
			}

		}
		if (privateAddress.matches("\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}[:]\\d{1,5}")) {
			String[] address = (privateAddress.split("[:]"));
			String host = address[0];
			int port = Integer.parseInt(address[1]);
			this.privateConnectionListener = new PrivateConnectionListener(host, port, this.userResponseStream);
			this.pool.execute(this.privateConnectionListener);
		}
		return null;
	}

	@Override
	@Command
	public String lastMsg() throws IOException {
		this.commandQueue.add("lastMsg");
		return (this.lastPublicMessage != null) ? this.lastPublicMessage : "No message received !";
	}

	@Override
	@Command
	public String exit() throws IOException {
		this.commandQueue.add("exit");
		// this.incomingMessageListener.close();
		if (this.privateConnectionListener != null)
			this.privateConnectionListener.close();
		this.shell.close();
		this.pool.shutdown();
		try {
			// Wait a while for existing tasks to terminate
			if (!this.pool.awaitTermination(60, TimeUnit.SECONDS)) {
				this.pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!this.pool.awaitTermination(60, TimeUnit.SECONDS))
					System.err.println("Pool did not terminate");
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			this.pool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
		this.userRequestStream.close();

		this.serverWriter.close();
		this.socket.close();
		if (this.datagramSocket != null) {
			this.datagramSocket.close();
		}
		this.userResponseStream.println("Successfully disconneted to chatserver");
		this.userResponseStream.close();
		return null;
	}

	public void setLastPublicMessage(String message) {
		this.lastPublicMessage = message;
	}

	public void setPrivateAddressReceiver(String privateAddressReceiver) {
		this.privateAddressReceiver = privateAddressReceiver;
	}

	public synchronized String popLastCommand() {
		if (!this.commandQueue.isEmpty())
			return this.commandQueue.pop();
		else
			return null;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) throws Exception {
		Client client = new Client(args[0], new Config("client"), System.in, System.out);
		new Thread((Runnable) client).start();

	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Override
	public String authenticate(String username) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
