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

import com.badlogic.gdx.Gdx;
import com.bidjee.digitalpokerchips.c.DPCGame;

public class PlayerNetworkService extends Service {
	
	// Network constants
	private static final int NEG_PORT_TX = 11111;
	private static final int NEG_PORT_RX = 11112;
	private static final int COMM_PORT = 11113;
	private static final int RECONNECT_PORT = 11114;
	
	private static final int MAX_NEG_TIME = 9000;
	private static final int MAX_READS = 5;

	// Binder given to clients
	private final IBinder mBinder=new PlayerNetworkServiceBinder();
	// Reference to network interface //
	PlayerNetwork playerNetwork;
	// off switch for discover loop
	Thread currentDiscoverThread;
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
    	return broadcastAddress;
    }
	
	Object discoverLock=new Object();
	
	public void stopDiscover() {
		Thread stopPlayerDiscoverThread=new Thread(new Runnable() {
			public void run() {
				synchronized (discoverLock) {
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
			Gdx.app.log("DPC","startPlayerDiscover Started");
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
			    			Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - discoverRunnable - Sent broadcast");
							try {
								// Bug requires that this be set each time
								hostRespPkt.setLength(hostRespBuf.length);
								// Wait for a response
								respSocket.receive(hostRespPkt);
								Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - discoverRunnable - Broadcast repsonse received");
								String rxMsg=new String(hostRespPkt.getData(),0,hostRespPkt.getLength());
								// When the response is received, check for the right message then notify activity
								if (rxMsg.contains(HostNetwork.TAG_TABLE_NAME_OPEN)&&rxMsg.contains(HostNetwork.TAG_VAL_C_CLOSE)) {
									byte[] hostBytes=hostRespPkt.getAddress().getAddress();
									playerNetwork.notifyTableFound(hostBytes,rxMsg);
									sleepThread=true;
									Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - discoverRunnable - Table discovered");
								}
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
					Gdx.app.log("DPC","Couldn't find Broadcast Address");
					e3.printStackTrace();
				}
			} catch (SocketException e2) {
				e2.printStackTrace();
				Gdx.app.log("DPC","Couldn't open DatagramSockets");
			}
    		Gdx.app.log("DPC","startPlayerDiscover Finished");
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
			Gdx.app.log("DPC","requestInvitationRunnable Started");
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
		    			Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - requestInvitationRunnable - Sent request invitation");
					} catch (IOException e) {
						e.printStackTrace();
					}
				} catch (UnknownHostException e3) {
					Gdx.app.log("DPC","Couldn't find host address");
					e3.printStackTrace();
				}
				sendSocket.close();
			} catch (SocketException e2) {
				e2.printStackTrace();
				Gdx.app.log("DPC","Couldn't open DatagramSockets");
			}
		}// end run
	}
	
	public void playerConnect(final byte[] hostBytes_,final String playerSetupString) {
		Thread playerConnectThread = new Thread(new Runnable() {
			public void run() {
				
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
					Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_READ_TABLE_INFO");
				} catch (IOException e) {
					state=STATE_FAILED;
					errorMsg="Couldn't open streams";
					Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_FAILED: "+errorMsg);
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
										Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_WRITE_SETUP_INFO");
									} else if (msg.contains(HostNetwork.TAG_CONNECT_UNSUCCESSFUL)) {
										state=STATE_FAILED;
										errorMsg="Table backed out";
										Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_FAILED: "+errorMsg+": "+msg);
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
									Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_FAILED: "+errorMsg);
								}
							}
						} catch (IOException e) {
							state=STATE_FAILED;
							errorMsg="Couldn't read Table info";
							Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_FAILED: "+errorMsg);
							e.printStackTrace();
						}
					} else if (state==STATE_WRITE_SETUP_INFO) {
						try {
							String msg=playerSetupString+"\n";
							outputStream.write(msg.getBytes());
							state=STATE_READ_ACK;
							reads=0;
							Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_READ_ACK");
						} catch (IOException e) {
							state=STATE_FAILED;
							errorMsg="Couldn't write setup info";
							Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_FAILED: "+errorMsg);
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
										commsSocket=rxSocket;
										PlayerNetworkService.this.inputStream=inputStream;
										PlayerNetworkService.this.outputStream=outputStream;
										startListen();
										playerNetwork.notifyGameConnected(tableInfo+ackMsg);
										buffer="";
										state=STATE_NONE;
										Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_NONE");
									} else if (ackMsg.contains(HostNetwork.TAG_CONNECT_UNSUCCESSFUL)) {
										state=STATE_FAILED;
										errorMsg="Table backed out";
										Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_FAILED: "+errorMsg+": "+ackMsg); 
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
									Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_FAILED: "+errorMsg);
								}
							}
						} catch (IOException e) {
							state=STATE_FAILED;
							errorMsg="Couldn't read ACK";
							Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_FAILED: "+errorMsg);
							e.printStackTrace();
						}
					}
					if (System.currentTimeMillis()-negTimer>MAX_NEG_TIME) {
						state=STATE_FAILED;
						errorMsg="Maximum neg time elapsed";
						Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - playerConnectThread - STATE_FAILED: "+errorMsg);
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
		playerConnectThread.start();
	}
	
	Object listenLock=new Object();
	
	public void stopListen() {		
		Thread stopListenThread=new Thread(new Runnable() {
			public void run() {
				synchronized (listenLock) {
					currentListenThread=null;
				}
			}
		});
		stopListenThread.start();
	}
	
	public void startListen() {
		synchronized (listenLock) {
			currentListenThread=new Thread(new ListenRunnable());
			currentListenThread.start();
		}
	}
	
	class ListenRunnable implements Runnable {
		public void run() {
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
					stopListen_=true;
				} catch (EOFException eofe) {
					eofe.printStackTrace();
					// disconnect everything and start reconnect protocol
					stopListen_=true;
				} catch (IOException e) {
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
				playerNetwork.startReconnect();
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
			try {
				if (outputStream!=null) {
					outputStream.write(msg.getBytes());
				}
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
			stopListen();
			stopReconnect();
			disconnectCurrentGame();
		}
	}
	
	public void disconnectCurrentGame() {
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
    
    Object reconnectLock=new Object();
    
    public void stopReconnect() {
    	Thread stopReconnectThread=new Thread(new Runnable() {
			public void run() {
				synchronized (reconnectLock) {
					currentReconnectThread=null;
				}
			}
		});
    	stopReconnectThread.start();
    }
    
    public void startReconnect(final byte[] hostBytes,final String reconnectStr) {
    	synchronized (reconnectLock) {
    		stopListen();
    		disconnectCurrentGame();
			currentReconnectThread=new Thread(new reconnectRunnable(hostBytes,reconnectStr));
			currentReconnectThread.start();
		}
    }
    
	public class reconnectRunnable implements Runnable {
		byte[] hostBytes;
		String reconnectMsg;
		public reconnectRunnable(byte[] hostBytes,String reconnectStr) {
			this.hostBytes=hostBytes;
			this.reconnectMsg=reconnectStr+"\n";
		}
		public void run() {
			final int STATE_FAILED = -1;
			final int STATE_NONE = 0;
			final int STATE_POLL_CONNECT = 1;
			final int STATE_READ_TABLE_INFO = 2;
			final int STATE_WRITE_GAME_KEY = 3;
			final int STATE_READ_ACK = 4;
			
			int state=STATE_POLL_CONNECT;
			
			Socket rxSocket=null;
			DataInputStream inputStream=null;
			DataOutputStream outputStream=null;
			String buffer="";
			long negTimer=0;
			int reads=0;
			String errorMsg="";
			while (currentReconnectThread==Thread.currentThread()&&state!=STATE_NONE) {
				if (state==STATE_POLL_CONNECT) {
					try {
						rxSocket=new Socket();
						rxSocket.connect(new InetSocketAddress(InetAddress.getByAddress(hostBytes),RECONNECT_PORT),5000);
						inputStream=new DataInputStream(rxSocket.getInputStream());
						outputStream=new DataOutputStream(rxSocket.getOutputStream());
						rxSocket.setSoTimeout(4000);
						state=STATE_READ_TABLE_INFO;
						buffer="";
						negTimer=System.currentTimeMillis();
						reads=0;
						Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_READ_TABLE_INFO");
					} catch (IOException e) {
						e.printStackTrace();
						Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - Connect attempt failed");
						try {Thread.sleep(3000);}
						catch (InterruptedException e1) {e1.printStackTrace();}
					}
				} else if (state==STATE_READ_TABLE_INFO) {
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
								if (playerNetwork.validateReconnectTableInfo(msg)) {
									state=STATE_WRITE_GAME_KEY;
									buffer="";
									Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_WRITE_GAME_KEY");
								} else if (msg.contains(HostNetwork.TAG_RECONNECT_FAILED)) {
									state=STATE_FAILED;
									errorMsg="Table backed out";
									Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_FAILED: "+errorMsg+": "+msg);
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
								Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_FAILED: "+errorMsg);
							}
							if (System.currentTimeMillis()-negTimer>MAX_NEG_TIME) {
								state=STATE_FAILED;
								errorMsg="Maximum neg time elapsed";
								Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_FAILED: "+errorMsg);
							}
						}
					} catch (IOException e) {
						state=STATE_FAILED;
						errorMsg="Couldn't read Table info";
						Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_FAILED: "+errorMsg);
						e.printStackTrace();
					}
				} else if (state==STATE_WRITE_GAME_KEY) {
					try {
						outputStream.write(reconnectMsg.getBytes());
						state=STATE_READ_ACK;
						reads=0;
						negTimer=System.currentTimeMillis();
						Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_READ_ACK");
					} catch (IOException e) {
						state=STATE_FAILED;
						errorMsg="Couldn't write setup info";
						Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_FAILED: "+errorMsg);
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
								if (playerNetwork.validateReconnectACK(ackMsg)) {
									commsSocket=rxSocket;
									PlayerNetworkService.this.inputStream=inputStream;
									PlayerNetworkService.this.outputStream=outputStream;
									startListen();
									playerNetwork.notifyReconnected();
									buffer="";
									state=STATE_NONE;
									Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_NONE");
								} else if (ackMsg.contains(HostNetwork.TAG_RECONNECT_FAILED)) {
									state=STATE_FAILED;
									errorMsg="Table backed out";
									Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_FAILED: "+errorMsg+": "+ackMsg); 
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
								Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_FAILED: "+errorMsg);
							}
							if (System.currentTimeMillis()-negTimer>MAX_NEG_TIME) {
								state=STATE_FAILED;
								errorMsg="Maximum neg time elapsed";
								Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_FAILED: "+errorMsg);
							}
						}
					} catch (IOException e) {
						state=STATE_FAILED;
						errorMsg="Couldn't read ACK";
						Gdx.app.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "PlayerNetworkService - reconnectRunnable - STATE_FAILED: "+errorMsg);
						e.printStackTrace();
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
					state=STATE_POLL_CONNECT;
				}
			}
		} // end run()
	}
    
	private static void simulateDelay() {
		try{Thread.sleep(DEBUG_SIMULATED_DELAY);}catch(InterruptedException e){}
	}
    
}