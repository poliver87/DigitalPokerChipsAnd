package com.bidjee.digitalpokerchips;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import com.bidjee.digitalpokerchips.c.Table;

public class PlayerConnection {
	
	public static final String TAG_KEEPALIVE = "<DPC_STILL_HERE_BRUZ/>";
	
	Socket commsSocket;
	DataInputStream inputStream;
	DataOutputStream outputStream;
	String hostName;
	private KeepAliveThread currentKeepAliveThread;
	
	HostNetwork network;
	
	PlayerConnection(Socket commsSocket_,DataInputStream inputStream_,DataOutputStream outputStream_,
			String hostName_,HostNetwork network) {
		commsSocket=commsSocket_;
		inputStream=inputStream_;
		outputStream=outputStream_;
		hostName=hostName_;
		currentKeepAliveThread=null;
		this.network=network;
	}
	
	public void startListen() {
		ListenThread listenThread = new ListenThread();
		listenThread.start();
	}
	
	Object keepAliveLock=new Object();
	public void startKeepAlive() {
		synchronized (keepAliveLock) {
			currentKeepAliveThread=new KeepAliveThread();
			currentKeepAliveThread.start();
		}
	}
	
	public void stopKeepAlive() {
		synchronized (keepAliveLock) {
			currentKeepAliveThread=null;
		}
	}
	
	public void send(String msg) {
		Thread sendThread = new Thread(new sendRunnable(msg));
		sendThread.start();
	}
	
	public class sendRunnable implements Runnable {		
		String msg;	
		public sendRunnable(String msg_) {
			msg=msg_+"\n";
		}		
		public void run() {
			synchronized (this) {
				try {outputStream.write(msg.getBytes());}
				catch (IOException e) {e.printStackTrace();}
			}
		}
	}
	
	class KeepAliveThread extends Thread {
		public void run() {
			while (currentKeepAliveThread==Thread.currentThread()) {
				send(PlayerConnection.TAG_KEEPALIVE);
				try {Thread.sleep(4000);}
				catch (InterruptedException e) {e.printStackTrace();}
			}
		}
	}
	
	class ListenThread extends Thread {		
		public void run() {
			String text_=null;
			boolean keepReading_=true;
			try {commsSocket.setSoTimeout(0);}
			catch (SocketException e1) {e1.printStackTrace();}
			while (keepReading_) {
				try {
					byte[] inBuffer_=new byte[1024];
					// TODO break this into lines
					int len_=inputStream.read(inBuffer_);
					if (len_>0) {
						text_=new String(inBuffer_,"UTF-8");
						text_=text_.substring(0, len_);
						synchronized (this) {
							network.parsePlayerMessage(hostName,text_);
						}
					} else {
						keepReading_=false;
					}
				} catch (IOException e) {
					keepReading_=false;
					e.printStackTrace();
				}								
			} // end while
		} // end run
	}
	
	public void disconnect(String exitMsg_) {
		Thread disconnectThread = new Thread(new disconnectRunnable(exitMsg_));
		disconnectThread.start();
	}
	
	class disconnectRunnable implements Runnable {
		String msg;
		disconnectRunnable(String exitMsg_) {
			msg=exitMsg_;
			if (msg.length()>0)
				msg+="\n";
		}
		public void run() {
			stopKeepAlive();
			if (!msg.equals("")) {
				try {outputStream.write(msg.getBytes());}
				catch (IOException e) {e.printStackTrace();}
			}			
			try {inputStream.close();}
			catch (IOException e) {e.printStackTrace();}
			try {outputStream.close();}
			catch (IOException e) {e.printStackTrace();}
			try {commsSocket.close();}
			catch (IOException e) {e.printStackTrace();}
		}
	}
	
	static boolean[] assigned=new boolean[Table.NUM_SEATS];
	
	public static int assign() {
		int ID=-1;
		for (int i=0;i<Table.NUM_SEATS;i++) {
			if (!assigned[i]) {
				ID=i;
				assigned[i]=true;
				break;
			}
		}
		return ID;
	}
	
	public static void free(int ID) {
		assigned[ID]=false;
	}

}
