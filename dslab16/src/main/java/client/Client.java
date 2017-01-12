package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.*;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.util.encoders.Base64;

import cli.Command;
import cli.Shell;
import security.Base64Helper;
import security.RSA;
import security.RSAException;
import util.Config;
import util.Keys;
import security.RandomNumberHelper;

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
	private Config userConfig;
	private boolean loggedIn = false;
	private boolean haveBeenLoggedIn = false;
	private byte[] messageFromServer;
	private String lastMessageFromServer;

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
	public Client(String componentName, Config config, InputStream userRequestStream, Config userConfig,
			PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		this.userConfig = userConfig;
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
		this.pool.execute(new Thread(this.shell));

		/*
		 * create a new tcp socket at specified host and port - make sure you
		 * specify them correctly in the client properties file(see
		 * client1.properties and client2.properties)
		 */
		this.userResponseStream.println(getClass().getName() + ":  up and waiting for commands!");

	}

	@Override
	@Command
	public String login(String username, String password) throws IOException {
		if (!this.loggedIn) {
			this.commandQueue.add("login");
			String pw = "";
			try {
				pw = userConfig.getString(username + ".password");
			} catch (Exception e) {
				return "Wrong username or password.";
			}
			if (pw == null) {
				return "Wrong username or password.";
			} else if (password.equals(pw)) {
				if (this.haveBeenLoggedIn) {
					this.loggedIn = true;
					this.haveBeenLoggedIn = true;
					this.serverWriter.println(new String(encodeBase64("!login " + username + " " + password)));
				} else {
					this.loggedIn = true;
					this.haveBeenLoggedIn = true;
					try {
						this.socket = new Socket(this.config.getString("chatserver.host"),
								config.getInt("chatserver.tcp.port"));
						// create a writer to send messages to the server
						this.serverWriter = new PrintWriter(this.socket.getOutputStream(), true);
						this.incomingMessageListener = new IncomingMessageListener(this.socket, this.userResponseStream,
								this.componentName, this);
						this.pool.execute(incomingMessageListener);
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					this.serverWriter.println(new String(encodeBase64("!login " + username + " " + password)));
				}
			} else {
				return "Wrong username or password.";
			}

			return null;
		} else {
			return "Already logged in.";
		}
	}

	@Override
	@Command
	public String logout() throws IOException {
		if (this.loggedIn) {
			this.commandQueue.add("logout");
			this.serverWriter.println(new String(encodeBase64("!logout")));
			this.loggedIn = false;
			return null;
		} else {
			return "Not logged in.";
		}

	}

	@Override
	@Command
	public String send(String message) throws IOException {
		if (this.loggedIn) {
			this.commandQueue.add("send");
			this.serverWriter.println(new String(encodeBase64("!send " + message)));
			return null;
		} else {
			return "Not logged in.";
		}

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

		if (this.loggedIn) {
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
					String output = message + "|from: " + username;
					String generalPath = System.getProperty("user.dir");
					String finalPath = generalPath + "\\keys\\hmac.key";
					// keys.readSecretKey actually returns a SecretKeyspec=>cast
					SecretKeySpec key = (SecretKeySpec) Keys.readSecretKey(new File(finalPath));
					// get instance of Algorithm and initialize with key
					Mac mac = Mac.getInstance("HmacSHA256");
					mac.init(key);
					// sign in message-bytes
					mac.update(output.getBytes());
					// compute finalHash
					byte[] hashToSend = mac.doFinal();
					// add Base-64 encoding
					byte[] encodedHashToSend = Base64.encode(hashToSend);
					privateSocketWriter.println("<" + new String(encodedHashToSend) + "> ! msg <" + output + ">");
					// privateSocketWriter.println("!tampered"+ new
					// String(encodedHashToSend));
					String response;
					if ((response = privateSocketReader.readLine()) != null) {
						if (checkForTampering(response).equals("!ack")) {
							this.userResponseStream.println(username + " responded with !ack");
						}
						if (checkForTampering(response).equals("!tampered")) {
							this.userResponseStream
									.println("Sending" + "<" + hashToSend + "> ! tampered <" + output + ">");
						}
					}
					privateSocketWriter.close();
					privateSocketReader.close();
					privateSocket.close();

				} catch (UnknownHostException e) {
					this.userResponseStream.println("Wrong hostname specified");
				} catch (IOException e) {
					this.userResponseStream.println("Connection closed to the other user");
				} catch (NoSuchAlgorithmException e) {
					this.userResponseStream.println("HMAC Algorithm not found");
				} catch (InvalidKeyException e) {
					this.userResponseStream.println("HMAC-Key not found");
				}

			}
			return null;
		} else {
			return "Not logged in.";
		}

	}

	public String checkForTampering(String message) throws NoSuchAlgorithmException, IOException, InvalidKeyException {
		String hmac = "";
		String msg = "";
		char[] check = message.toCharArray();
		int i = 0;
		char checkMe = check[i];

		while (checkMe != '<') {
			i = i + 1;
			checkMe = check[i];
		}
		while (checkMe != '>') {
			i = i + 1;
			checkMe = check[i];
			hmac = hmac + checkMe;
		}
		while (checkMe != '<') {
			i = i + 1;
			checkMe = check[i];
		}

		while (checkMe != '>') {
			i = i + 1;
			checkMe = check[i];
			msg = msg + checkMe;
		}
		hmac = hmac.substring(0, hmac.length() - 1);
		msg = msg.substring(0, msg.length() - 1);
		// System.out.println(hmac + "\n" + msg);

		Mac macChecker = Mac.getInstance("HmacSHA256");
		String generalPathReciever = System.getProperty("user.dir");
		String finalPathReciever = generalPathReciever + "\\keys\\hmac.key";
		SecretKeySpec keyReciever = (SecretKeySpec) Keys.readSecretKey(new File(finalPathReciever));
		macChecker.init(keyReciever);
		// TamperedTest
		// msg=msg+"tampered";
		macChecker.update(msg.getBytes());
		byte[] hashToCheck = macChecker.doFinal();
		boolean validHash = MessageDigest.isEqual(hashToCheck, Base64.decode(hmac));
		// System.out.println(validHash);
		if (validHash) {
			return "!ack";
		} else {
			return "!tampered";
		}
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {
		if (this.loggedIn) {
			this.commandQueue.add("lookup");
			this.serverWriter.println(new String(encodeBase64("!lookup " + username)));
			return null;
		} else {
			return "Not logged in.";
		}
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {
		if (this.loggedIn) {
			this.commandQueue.add("register");
			this.serverWriter.println(new String(encodeBase64("!register " + privateAddress)));
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
			} else {
				return "Wrong adress specified, it should be in the format like 127.0.0.1:1234";
			}
			return null;
		} else {
			return "Not logged in.";
		}
	}

	@Override
	@Command
	public String lastMsg() throws IOException {
		if (this.loggedIn) {
			this.commandQueue.add("lastMsg");
			return (this.lastPublicMessage != null) ? this.lastPublicMessage : "No message received!";
		} else {
			return "Not logged in.";
		}
	}

	@Override
	@Command
	public String exit() throws IOException {

		if (this.loggedIn)
			logout();

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
		if (userRequestStream != null)
			this.userRequestStream.close();
		if (serverWriter != null)
			this.serverWriter.close();
		if (serverWriter != null)
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

	public synchronized void setLoginStatus(boolean status) {
		this.loggedIn = status;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) throws Exception {
		Client client = new Client(args[0], new Config("client"), System.in, new Config("user"), System.out);
		new Thread((Runnable) client).start();

	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Command
	@Override
	public String authenticate(String username) throws IOException {

		if (!loggedIn) {

			// Generate client-challenge
			SecureRandom secureRandom = new SecureRandom();
			final byte[] number = new byte[32];
			secureRandom.nextBytes(number);
			byte[] clientChallengeBase64 = Base64.encode(number);

			// 1st Message
			String message = "!authenticate " + username + " " + new String(clientChallengeBase64);

			// Get Server public Key
			String finalPath = config.getString("chatserver.key");
			RSAPublicKey serverPublicKey = (RSAPublicKey) Keys.readPublicPEM(new File(finalPath));



				// Encrypt
				try {
					Cipher cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");

					cipher.init(Cipher.ENCRYPT_MODE, serverPublicKey);
					byte[] encryptedMessage = cipher.doFinal(message.getBytes());
					// Encode
					encryptedMessage = encodeBase64(encryptedMessage);

					// Send 1. message to server
					if(!this.haveBeenLoggedIn) {
						this.socket = new Socket(this.config.getString("chatserver.host"), config.getInt("chatserver.tcp.port"));
						this.incomingMessageListener = new IncomingMessageListener(this.socket, this.userResponseStream,
								this.componentName, this);
						this.pool.execute(incomingMessageListener);
					}
					this.serverWriter = new PrintWriter(this.socket.getOutputStream(), true);


					this.commandQueue.add("authenticate");
					this.serverWriter.println(new String(encryptedMessage));
					synchronized (this.incomingMessageListener) {
						try {
							this.incomingMessageListener.wait();
						} catch (InterruptedException e) {
							this.userResponseStream.println("Error in client server communication");
						}

					}

					byte[] messageDecoded = this.messageFromServer;

					// Get Server public Key
					String finalPath1 = config.getString("keys.dir")+"/"+username+".pem";
					RSAPrivateKey userPrivateKey = (RSAPrivateKey) Keys.readPrivatePEM(new File(finalPath1));

					cipher.init(Cipher.DECRYPT_MODE, userPrivateKey);
					byte[] messageDecrypted = cipher.doFinal(messageDecoded);
					// Encode

					String[] message1 = new String(messageDecrypted).split(" ");
					if(message1.length == 5 && message1[0].equals("!ok")){
						byte challenge[] = Base64.decode(message1[1].getBytes());
						if(Arrays.equals(number, challenge)){
							byte serverChallenge[] = Base64.decode(message1[2].getBytes());
							SecretKey key = new SecretKeySpec(Base64.decode(message1[3].getBytes()), "AES");
							IvParameterSpec ivSpec = new IvParameterSpec(Base64.decode(message1[4].getBytes()));

							Cipher cipher1 = Cipher.getInstance("AES/CTR/NoPadding");
							cipher1.init(Cipher.ENCRYPT_MODE, key, ivSpec);
							byte[] encryptedLastMessage = cipher1.doFinal(serverChallenge);

							byte encodedLastMessage[] = Base64.encode(encryptedLastMessage);

							this.commandQueue.add("authenticate");
							this.serverWriter.println(new String(encodedLastMessage));

							synchronized (this.incomingMessageListener) {
								try {
									this.incomingMessageListener.wait();
								} catch (InterruptedException e) {
									this.userResponseStream.println("Error in client server communication");
								}

							}


							if(this.lastMessageFromServer.equals("Succesfully authenticated with the chatserver")){
								this.loggedIn = true;
								this.userResponseStream.println(this.messageFromServer);
							}

						}else {
							return "Something went wrong during the first authentication state";
						}
					}


				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				} catch (NoSuchPaddingException e) {
					e.printStackTrace();
				} catch (InvalidKeyException e) {
					e.printStackTrace();
				} catch (BadPaddingException e) {
					e.printStackTrace();
				} catch (IllegalBlockSizeException e) {
					e.printStackTrace();
				} catch (InvalidAlgorithmParameterException e) {
					e.printStackTrace();
				}
			
		}else{
			return "Already logged in!";
		}

		return null;
	}

	public synchronized void setMessageFromServer(byte[] message){
		this.messageFromServer = message;
	}
	public synchronized void setLastMessageFromServer(String message){
		this.lastMessageFromServer = message;
	}

	private byte[] encodeBase64(String message){
		return Base64.encode(message.getBytes());
	}
	private byte[] encodeBase64(byte[] message){
		return Base64.encode(message);
	}

	private byte[] decodeBase64(String message){
		return Base64.decode(message.getBytes());
	}
	private byte[] decodeBase64(byte[] message){
		return Base64.decode(message);
	}

	public boolean isLoggedIn(){
		return this.loggedIn;
	}

}
