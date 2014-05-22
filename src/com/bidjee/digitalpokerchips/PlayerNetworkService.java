package com.bidjee.digitalpokerchips;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;

import com.bidjee.util.Logger;

public class PlayerNetworkService extends Service {
	
	public static final String LOG_TAG = "DPCPlayerNetworkService";
	
	// Network constants
	private static final int NEG_PORT_TX = 11111;
	private static final int NEG_PORT_RX = 11112;
	private static final int COMM_PORT = 11113;
	
	private static final int MAX_NEG_TIME = 9000;
	private static final int MAX_READS = 5;

	// Binder given to clients
	private final IBinder mBinder=new PlayerNetworkServiceBinder();
	// Reference to network interface //
	PlayerNetwork playerNetwork;
	// off switch for discover loop
	Thread currentDiscoverThread;
	boolean killNegThread=false;
	Thread currentListenThread;
	//
	DatagramSocket broadcastSocket;
	DatagramSocket respSocket;
	// communication socket
	private Socket commsSocket;
	// List of Streams
	private DataInputStream inputStream;
	private DataOutputStream outputStream;
	// reconnect
	Thread currentReconnectThread;
	
	private static final int DEBUG_SIMULATED_DELAY=3000;
	
	@Override
	public void onCreate() {
		currentDiscoverThread=null;
		currentListenThread=null;
		currentReconnectThread=null;
		broadcastSocket=null;
		respSocket=null;
		commsSocket=null;
		inputStream=null;
		outputStream=null;
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}
	
	public class PlayerNetworkServiceBinder extends Binder {
		PlayerNetworkService getService() {
			return PlayerNetworkService.this;
		}
	}
	
	public InetAddress getBroadcastAddress() throws UnknownHostException {
    	InetAddress broadcastAddress = null;
		WifiManager wm=(WifiManager)getSystemService(Context.WIFI_SERVICE);
		DhcpInfo dhcp=wm.getDhcpInfo();
		int broadcast=(dhcp.ipAddress&dhcp.netmask)|~dhcp.netmask;
		byte[] quads=new byte[4];
		for (int k=0;k<4;k++) {
			quads[k]=(byte)((broadcast>>k*8)&0xFF);
		}
		broadcastAddress=InetAddress.getByAddress(quads);
		Logger.log(LOG_TAG,"getBroadcastAddress() = "+broadcastAddress.toString());
    	return broadcastAddress;
    }
	
	Object discoverLock=new Object();
	
	public void stopDiscover() {
		Thread stopPlayerDiscoverThread=new Thread(new Runnable() {
			public void run() {
				synchronized (discoverLock) {
					Logger.log(LOG_TAG,"stopDiscover()");
					currentDiscoverThread=null;
					if (broadcastSocket!=null) {
						broadcastSocket.close();
					}
					if (respSocket!=null) {
						respSocket.close();
					}
				}
			}
		});
		stopPlayerDiscoverThread.start();
	}
	
	public void startDiscover(final String playerAnnounceStr) {
		synchronized (discoverLock) {
			currentDiscoverThread=new Thread(new discoverRunnable(playerAnnounceStr));
			currentDiscoverThread.start();
		}
	}
	
	class discoverRunnable implements Runnable {
		final String playerAnnounceStr;
		public discoverRunnable(String playerAnnounceStr) {
			this.playerAnnounceStr=playerAnnounceStr;
		}
		public void run() {
			Logger.log(LOG_TAG,"discoverRunnable()");
			try {
				// Setup the Datagram Sockets
				broadcastSocket=new DatagramSocket();
				broadcastSocket.setBroadcast(true);
				respSocket=new DatagramSocket(NEG_PORT_RX);
				respSocket.setSoTimeout(5000);
				try {
					// Setup the broadcast data
					InetAddress broadcastAddress = getBroadcastAddress();
					byte[] hostRespBuf = new byte[1024];
					DatagramPacket hostRespPkt = new DatagramPacket(hostRespBuf, hostRespBuf.length);
					String discoverHostData = playerAnnounceStr;
					DatagramPacket discoverHostPkt=null;
					discoverHostPkt=new DatagramPacket(discoverHostData.getBytes(),discoverHostData.length(), broadcastAddress, NEG_PORT_TX);
					// Start the broadcast-receive loop
					boolean sleepThread=false;
					while (Thread.currentThread()==currentDiscoverThread) {
						if (sleepThread) {
			    			try {Thread.sleep(2000);}catch (InterruptedException e1) {}
			    			sleepThread=false;
			    		}
			    		try {
			    			// Broadcast player's presence
			    			broadcastSocket.send(discoverHostPkt);
			    			Logger.log(LOG_TAG,"discoverRunnable() - sent broadcast");
							try {
								// Bug requires that this be set each time
								hostRespPkt.setLength(hostRespBuf.length);
								// Wait for a response
								respSocket.receive(hostRespPkt);
								Logger.log(LOG_TAG,"discoverRunnable() - broadcast response received");
								String rxMsg=new String(hostRespPkt.getData(),0,hostRespPkt.getLength());
								// When the response is received, check for the right message then notify activity
								byte[] hostBytes=hostRespPkt.getAddress().getAddress();
								Logger.log(LOG_TAG,"discoverRunnable() - discover response Rxd");
								playerNetwork.discoverResponseRxd(hostBytes,rxMsg);
								sleepThread=true;
							} catch (SocketTimeoutException e) {
								sleepThread=true;
							} catch (IOException e1) {
								sleepThread=true;
							}
						} catch (IOException e) {
							e.printStackTrace();
							sleepThread=true;
						}	    		
		    		} // end while (Thread.currentThread()==currentDiscoverThread)
				} catch (UnknownHostException e3) {
					Logger.log(LOG_TAG,"discoverRunnable() - couldn't find broadcast address");
					e3.printStackTrace();
				}
			} catch (SocketException e2) {
				e2.printStackTrace();
				Logger.log(LOG_TAG,"discoverRunnable() - couldn't open datagram sockets");
			}
			Logger.log(LOG_TAG,"discoverRunnable() - end");
		}// end run
	}
	
	public void requestInvitation(final byte[] hostBytes,final String playerAnnounceStr) {
		Thread requestInvitationThread=new Thread(new requestInvitationRunnable(hostBytes,playerAnnounceStr));
		requestInvitationThread.start();
	}
	
	class requestInvitationRunnable implements Runnable {
		final byte[] hostBytes;
		final String playerAnnounceStr;
		public requestInvitationRunnable(byte[] hostBytes,String playerAnnounceStr) {
			this.hostBytes=hostBytes;
			this.playerAnnounceStr=playerAnnounceStr;
		}
		public void run() {
			Logger.log(LOG_TAG,"requestInvitationRunnable()");
			try {
				// Setup the Datagram Socket
				DatagramSocket sendSocket=new DatagramSocket();
				try {
					// Setup the broadcast data
					InetAddress hostAddress = InetAddress.getByAddress(hostBytes);
					String discoverHostData = playerAnnounceStr;
					DatagramPacket discoverHostPkt=null;
					discoverHostPkt=new DatagramPacket(discoverHostData.getBytes(),discoverHostData.length(),hostAddress,NEG_PORT_TX);
					try {
		    			// Broadcast player's presence
						sendSocket.send(discoverHostPkt);
						Logger.log(LOG_TAG,"requestInvitationRunnable() - sent request");
					} catch (IOException e) {
						e.printStackTrace();
					}
				} catch (UnknownHostException e3) {
					Logger.log(LOG_TAG,"requestInvitationRunnable() - unknown host");
					e3.printStackTrace();
				}
				sendSocket.close();
			} catch (SocketException e2) {
				e2.printStackTrace();
				Logger.log(LOG_TAG,"requestInvitationRunnable() - couldn't open datagram sockets");
			}
		}// end run
	}
	
	public void playerConnect(final byte[] hostBytes_,final String playerSetupString) {
		Thread playerConnectThread = new Thread(new Runnable() {
			public void run() {
				
				Logger.log(LOG_TAG,"playerConnect()");
				
				final int STATE_FAILED = -1;
				final int STATE_NONE = 0;
				final int STATE_READ_TABLE_INFO = 1;
				final int STATE_WRITE_SETUP_INFO = 2;
				final int STATE_READ_ACK = 3;
				
				int state=STATE_NONE;
				
				Socket rxSocket=new Socket();
				DataInputStream inputStream=null;
				DataOutputStream outputStream=null;
				String buffer="";
				String tableInfo="";
				long negTimer=0;
				int reads=0;
				String errorMsg="";
				try {
					rxSocket.connect(new InetSocketAddress(InetAddress.getByAddress(hostBytes_),COMM_PORT),3000);
					negTimer=System.currentTimeMillis();
					inputStream=new DataInputStream(rxSocket.getInputStream());
					outputStream=new DataOutputStream(rxSocket.getOutputStream());
					rxSocket.setSoTimeout(4000);
					state=STATE_READ_TABLE_INFO;
					reads=0;
					Logger.log(LOG_TAG,"playerConnect() - read table info");
				} catch (IOException e) {
					state=STATE_FAILED;
					errorMsg="Couldn't open streams";
					Logger.log(LOG_TAG,"playerConnect() - "+errorMsg);
					e.printStackTrace();
				}
				while (state!=STATE_FAILED&&state!=STATE_NONE) {
					if (state==STATE_READ_TABLE_INFO) {
						try {
							byte[] byteBuffer=new byte[1024];
							int len=inputStream.read(byteBuffer);
							if (len>0) {
								String inText=new String(byteBuffer,"UTF-8");
								inText=inText.substring(0,len);
								buffer+=inText;
								while (buffer.contains("\n")) {
									int newlineIndex=buffer.indexOf("\n");
									String msg=buffer.substring(0,newlineIndex);
									if (playerNetwork.validateTableInfo(msg)) {
										tableInfo=msg;
										state=STATE_WRITE_SETUP_INFO;
										buffer="";
										Logger.log(LOG_TAG,"playerConnect() - write setup info");
									} else if (msg.contains(HostNetwork.TAG_CONNECT_UNSUCCESSFUL)) {
										state=STATE_FAILED;
										errorMsg="Table backed out";
										Logger.log(LOG_TAG,"playerConnect() - "+errorMsg+" - "+msg);
										buffer="";
									}
									if (buffer.length()>newlineIndex+1) {
										buffer=buffer.substring(newlineIndex+1);
									} else {
										buffer="";
									}
								}
								reads++;
								if (reads>MAX_READS) {
									buffer="";
									state=STATE_FAILED;
									errorMsg="Max reads exceeded";
									Logger.log(LOG_TAG,"playerConnect() - "+errorMsg);
								}
							}
						} catch (IOException e) {
							state=STATE_FAILED;
							errorMsg="Couldn't read Table info";
							Logger.log(LOG_TAG,"playerConnect() - "+errorMsg);
							e.printStackTrace();
						}
					} else if (state==STATE_WRITE_SETUP_INFO) {
						try {
							String msg=playerSetupString+"\n";
							outputStream.write(msg.getBytes());
							state=STATE_READ_ACK;
							reads=0;
							Logger.log(LOG_TAG,"playerConnect() - read ACK");
						} catch (IOException e) {
							state=STATE_FAILED;
							errorMsg="Couldn't write setup info";
							Logger.log(LOG_TAG,"playerConnect() - "+errorMsg);
							e.printStackTrace();
						}
					} else if (state==STATE_READ_ACK) {
						try {
							byte[] byteBuffer=new byte[1024];
							int len=inputStream.read(byteBuffer);
							if (len>0) {
								String inText=new String(byteBuffer,"UTF-8");
								inText=inText.substring(0,len);
								buffer+=inText;
								while (buffer.contains("\n")) {
									int newlineIndex=buffer.indexOf("\n");
									String ackMsg=buffer.substring(0,newlineIndex);
									if (playerNetwork.validateTableACK(ackMsg)) {
										if (!killNegThread) {
											commsSocket=rxSocket;
											PlayerNetworkService.this.inputStream=inputStream;
											PlayerNetworkService.this.outputStream=outputStream;
											playerNetwork.notifyGameConnected(tableInfo+ackMsg);
											buffer="";
											state=STATE_NONE;
											Logger.log(LOG_TAG,"playerConnect() - connection successful");
										} else {
											state=STATE_FAILED;
											errorMsg="kill neg thread requested";
											Logger.log(LOG_TAG,"playerConnect() - "+errorMsg+" - "+ackMsg); 
											buffer="";
										}
									} else if (ackMsg.contains(HostNetwork.TAG_CONNECT_UNSUCCESSFUL)) {
										state=STATE_FAILED;
										errorMsg="Table backed out";
										Logger.log(LOG_TAG,"playerConnect() - "+errorMsg+" - "+ackMsg); 
										buffer="";
									}
									if (buffer.length()>newlineIndex+1) {
										buffer=buffer.substring(newlineIndex+1);
									} else {
										buffer="";
									}
								}
								reads++;
								if (reads>MAX_READS) {
									buffer="";
									state=STATE_FAILED;
									errorMsg="Max reads exceeded";
									Logger.log(LOG_TAG,"playerConnect() - "+errorMsg);
								}
							}
						} catch (IOException e) {
							state=STATE_FAILED;
							errorMsg="Couldn't read ACK";
							Logger.log(LOG_TAG,"playerConnect() - "+errorMsg);
							e.printStackTrace();
						}
					}
					if (System.currentTimeMillis()-negTimer>MAX_NEG_TIME) {
						state=STATE_FAILED;
						errorMsg="Maximum neg time elapsed";
						Logger.log(LOG_TAG,"playerConnect() - "+errorMsg);
					}
				}
				// if the negotiation failed, close the socket and notify the activity
				if (state==STATE_FAILED) {
					if (outputStream!=null) {
						try {
							String msg=PlayerNetwork.TAG_GOODBYE+errorMsg+"\n";
							outputStream.write(msg.getBytes());
						} catch (IOException e) {e.printStackTrace();}
					}
					if (inputStream!=null) {
						try {inputStream.close();
						} catch (IOException e) {e.printStackTrace();}
					}
					if (outputStream!=null) {
						try {outputStream.close();
						} catch (IOException e) {e.printStackTrace();}
					}
					if (rxSocket!=null) {
						try {rxSocket.close();
						} catch (IOException e) {}
					}
					playerNetwork.notifyConnectFailed();
				} // end if (!gotACK)
			} // end run()
		});
		synchronized (listenLock) {
			killNegThread=false;
			playerConnectThread.start();
		}
	}
	
	Object listenLock=new Object();
	
	public void stopListen() {
		synchronized (listenLock) {
			Logger.log(LOG_TAG,"stopListen()");
			killNegThread=true;
			currentListenThread=null;
		}
	}
	
	public void startListen() {
		synchronized (listenLock) {
			currentListenThread=new Thread(new ListenRunnable());
			currentListenThread.start();
		}
	}
	
	class ListenRunnable implements Runnable {
		public void run() {
			Logger.log(LOG_TAG,"ListenRunnable()");
			boolean stopListen_=false;
			String buffer_="";
			try {
				commsSocket.setSoTimeout(8000);
			} catch (SocketException e1) {
				e1.printStackTrace();
				stopListen_=true;
			}
			while (!stopListen_&&currentListenThread==Thread.currentThread()) {
				try {
					byte[] inBuffer=new byte[1024];
					int len=inputStream.read(inBuffer);
					if (len>0) {
						String inText_=new String(inBuffer,"UTF-8");
						inText_=inText_.substring(0, len);
						buffer_+=inText_;
						while (buffer_.contains("\n")) {
							// iterate over buffer and process each line
							String msg_="";
							int newlineIndex_=buffer_.indexOf("\n");
							msg_+=buffer_.substring(0,newlineIndex_);
							if (buffer_.length()>newlineIndex_+1) {
								buffer_=buffer_.substring(newlineIndex_+1);
							} else {
								buffer_="";
							}
							if (!msg_.equals(PlayerConnection.TAG_KEEPALIVE)) {
								playerNetwork.parseGameMessage(msg_);
							}
							
						}
					} else {
						// disconnect everything and start reconnect protocol
						stopListen_=true;
					}
				} catch (SocketTimeoutException ste) {
					// disconnect everything and start reconnect protocol
					Logger.log(LOG_TAG,"ListenRunnable() - socket timeout");
					stopListen_=true;
				} catch (EOFException eofe) {
					Logger.log(LOG_TAG,"ListenRunnable() - EOF");
					eofe.printStackTrace();
					// disconnect everything and start reconnect protocol
					stopListen_=true;
				} catch (IOException e) {
					Logger.log(LOG_TAG,"ListenRunnable() - IO Exception");
					e.printStackTrace();
					// disconnect everything and start reconnect protocol
					stopListen_=true;
				}
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} // end while
			if (stopListen_&&currentListenThread!=null) {
				disconnectCurrentGame();
				playerNetwork.notifyConnectionLost();
			}
		} // end run
	}
	
	public void sendToHost(String msg) {
		Thread sendToHostThread = new Thread(new sendToHostRunnable(msg));
		sendToHostThread.start();
	}
	
	public class sendToHostRunnable implements Runnable {		
		String msg;
		public sendToHostRunnable(String msg_) {
			msg=msg_;
			if (msg.length()>0)
				msg+="\n";
		}
		public void run() {
			Logger.log(LOG_TAG,"sendToHostRunnable("+msg+")");
			try {
				if (outputStream!=null) {
					outputStream.write(msg.getBytes());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void leaveTable(String msg) {
		Thread leaveTableThread = new Thread(new leaveTableRunnable(msg));
		leaveTableThread.start();
	}
	
	private class leaveTableRunnable implements Runnable {
		String msg;
		public leaveTableRunnable(String msg_) {
			msg=msg_+"\n";
		}
		public void run() {
			Logger.log(LOG_TAG,"leaveTableRunnable()");
			try {
				if (outputStream!=null) {
					outputStream.write(msg.getBytes());
				}
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
			stopListen();
			disconnectCurrentGame();
		}
	}
	
	public void disconnectCurrentGame() {
		Logger.log(LOG_TAG,"disconnectCurrentGame()");
		if (inputStream!=null) {
			try {inputStream.close();}
			catch (IOException e) {e.printStackTrace();}
		}
		if (outputStream!=null) {
			try {outputStream.close();}
			catch (IOException e) {e.printStackTrace();}
		}
		if (commsSocket!=null) {
			try {commsSocket.close();}
			catch (IOException e) {e.printStackTrace();}
		}
	}
    
	private static void simulateDelay() {
		try{Thread.sleep(DEBUG_SIMULATED_DELAY);}catch(InterruptedException e){}
	}
    
}