package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.util.encoders.Base64;

import chatserver.Channel;
import util.Keys;

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
					checkForTampering(response);
					
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
		} catch (InvalidKeyException e) {
			this.userPrintStream.println("Key not found!");

		} catch (NoSuchAlgorithmException e) {
			this.userPrintStream.println("HMAC Algorithm not found");
		}

	}
	
	public String generateHmac(String msg) throws IOException, NoSuchAlgorithmException, InvalidKeyException{
		String output=msg;
		String generalPath= System.getProperty("user.dir");
		String finalPath=generalPath+"\\keys\\hmac.key";
		//keys.readSecretKey actually returns a SecretKeyspec=>cast
		SecretKeySpec key=(SecretKeySpec) Keys.readSecretKey(new File(finalPath));
		//get instance of Algorithm and initialize with key
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(key);
		//sign in message-bytes
		mac.update(output.getBytes());
		//compute finalHash
		byte[] hashToSend=mac.doFinal();
		//add Base-64 encoding
		byte[] encodedHashToSend=Base64.encode(hashToSend);
		return("<"+new String(encodedHashToSend)+"> ! msg <"+ output+">");
	}

	public void checkForTampering(String message) throws NoSuchAlgorithmException, IOException, InvalidKeyException {
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
		//System.out.println(hmac + "\n" + msg);

		Mac macChecker = Mac.getInstance("HmacSHA256");
		String generalPathReciever = System.getProperty("user.dir");
		String finalPathReciever = generalPathReciever + "\\keys\\hmac.key";
		SecretKeySpec keyReciever = (SecretKeySpec) Keys.readSecretKey(new File(finalPathReciever));
		macChecker.init(keyReciever);
		//TamperedTest
		//msg=msg+"tampered";
		macChecker.update(msg.getBytes());
		byte[] hashToCheck=macChecker.doFinal();
		boolean validHash=MessageDigest.isEqual(hashToCheck, Base64.decode(hmac));
		//System.out.println(validHash);
		if(validHash){
			write(generateHmac("!ack"));
		}
		else{
			write(generateHmac("!tampered"));
		System.out.println("The recieved Message has been modified by a third party!!!");	
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
