package com.bidjee.digitalpokerchips;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import com.badlogic.gdx.Gdx;
import com.bidjee.digitalpokerchips.c.DPCGame;
import com.bidjee.digitalpokerchips.c.Table;
import com.bidjee.util.Logger;

public class HostNetworkService extends Service {
	
	public static final String LOG_TAG = "DPCHostNetworkService";
	
	// Network constants
	private static final int NEG_PORT_RX = 11111;
	private static final int NEG_PORT_TX = 11112;
	private static final int COMM_PORT = 11113;
	private static final int RECONNECT_PORT = 11114;
	// maximums
	private static final int MAX_NEG_TIME = 9000;
	private static final int MAX_READS = 5;
	// Binder given to clients
	private final IBinder mBinder = new HostNetworkServiceBinder();
	// Reference to Network Interface
	HostNetwork hostNetwork;
	// off switch for host announce service
	Thread currentAnnounceThread;
	DatagramSocket announceSocket;
	// off switch for host connect service
	Thread currentAcceptThread;
	ServerSocket serverSocket;
	
	// List of Player Connections
	public ArrayList<PlayerConnection> playerConnections;
	
	// off switch for reconnecting player
	Thread currentReconnectThread;
	ServerSocket reconnectServerSocket;
	
	private static final int DEBUG_SIMULATED_DELAY=3000;
	
	@Override
	public void onCreate() {
		currentAnnounceThread=null;
		currentAcceptThread=null;
		playerConnections = new ArrayList<PlayerConnection>();
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {return START_STICKY;}

	@Override
	public IBinder onBind(Intent arg0) {return mBinder;}
	
	public class HostNetworkServiceBinder extends Binder {
		HostNetworkService getService() {return HostNetworkService.this;}
	}
	
    Object announceLock=new Object();
    
	public void stopAnnounce() {
		Thread stopAnnounceThread_=new Thread(new Runnable() {
			public void run() {
				synchronized (announceLock) {
					Logger.log(LOG_TAG,"stopAnnounce()");
					currentAnnounceThread=null;
					if (announceSocket!=null) {
						announceSocket.close();
					}
				}
			}
		});
		stopAnnounceThread_.start();
	}
	
	public void startAnnounce(final String hostAnnounceStr) {
		synchronized (announceLock) {
			Logger.log(LOG_TAG,"startAnnounce()");
			currentAnnounceThread=new Thread(new AnnounceRunnable(hostAnnounceStr));
			currentAnnounceThread.start();
		}
	}
	
	class AnnounceRunnable implements Runnable {
		String hostAnnounceStr;
		public AnnounceRunnable(String hostAnnounceStr) {
			this.hostAnnounceStr=hostAnnounceStr;
		}
		@Override
		public void run() {
    		try {
    			Logger.log(LOG_TAG,"AnnounceRunnable()");
    			// Setup the Datagram socket
    			announceSocket=new DatagramSocket(NEG_PORT_RX);
    			announceSocket.setSoTimeout(6000);
    			// Setup the read buffer and response data
        		byte[] playerLookingBuf=new byte[1024];
        		DatagramPacket playerLookingPkt=new DatagramPacket(playerLookingBuf,playerLookingBuf.length);
        		DatagramPacket decHostPkt=new DatagramPacket(hostAnnounceStr.getBytes(),hostAnnounceStr.length(),null,NEG_PORT_TX);
        		// host waits for a packet from the player
        		boolean error=false;
        		while (!error&&currentAnnounceThread==Thread.currentThread()) {
        			try {
        				playerLookingPkt.setLength(playerLookingBuf.length);
        				// Wait for a message from a player
        				announceSocket.receive(playerLookingPkt);
        				Logger.log(LOG_TAG,"AnnounceRunnable() - broadcast received");
        				InetAddress playerAddress=playerLookingPkt.getAddress();
        				String rxMsg=new String(playerLookingPkt.getData(),0,playerLookingPkt.getLength());
    					// host has received a packet from the player, sends a packet to declare that it's a host
        				if (rxMsg.contains(PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN)&&rxMsg.contains(PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE)) {
    						if (hostNetwork.requestGamePermission(rxMsg)) {
    							decHostPkt.setAddress(playerAddress);
    							try {
    								announceSocket.send(decHostPkt);
    								Logger.log(LOG_TAG,"AnnounceRunnable() - response sent to player");
    							} catch (IOException e) {
    								e.printStackTrace();
    								error=true;
    							}
    						} else {
    							Logger.log(LOG_TAG,"AnnounceRunnable() - player permission denied");
    						}
    					}
        			} catch (SocketTimeoutException e) {
        				//e.printStackTrace();
        			} catch (IOException e) {
        				e.printStackTrace();
        				error=true;
        			}
        		} // end while
			} catch (IOException e1) {
				e1.printStackTrace();
				Gdx.app.log("DPC", "Couldn't open Datagram Socket");
			}
		}
	}
	
	Object acceptLock=new Object();
	
	public void stopAccept() {
		Thread stopAcceptThread=new Thread(new Runnable() {
			public void run() {
				synchronized (acceptLock) {
					Logger.log(LOG_TAG,"stopAccept()");
					currentAcceptThread=null;
					if (serverSocket!=null) {
						try {
							serverSocket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		});
		stopAcceptThread.start();
	}
	
	public void startAccept(final String tableNameMsg,final String gameKeyMsg,final String failedStr,final boolean loadedGame_) {
		synchronized (acceptLock) {
			Logger.log(LOG_TAG,"startAccept()");
			currentAcceptThread=new Thread(new acceptRunnable(tableNameMsg,gameKeyMsg,failedStr,loadedGame_));
			currentAcceptThread.start();
		}
	}
	
	class acceptRunnable implements Runnable {
		String tableNameMsg;
		String gameKeyMsg;
		String failedStr;
		boolean loadedGame;
		public acceptRunnable(String tableNameMsg,String gameKeyMsg,String failedStr,boolean loadedGame_) {
			this.tableNameMsg=tableNameMsg;
			this.gameKeyMsg=gameKeyMsg;
			this.failedStr=failedStr;
			loadedGame=loadedGame_;
		}
		public void run() {
			Logger.log(LOG_TAG,"acceptRunnable()");
			boolean noExceptions_=true;
			try {
				serverSocket=new ServerSocket(COMM_PORT);
				serverSocket.setSoTimeout(5000);
			} catch (IOException e1) {
				e1.printStackTrace();
				noExceptions_=false;
			}
    		while (currentAcceptThread==Thread.currentThread()&&noExceptions_) {
    			int numPlayers_;
    			synchronized (playerConnections) {
					numPlayers_=playerConnections.size();
				}
    			if (numPlayers_<Table.NUM_SEATS) {
    				try {
    					Socket localSocket_=serverSocket.accept();
    					ConnectNegThread connectNegThread_=new ConnectNegThread(localSocket_,tableNameMsg,gameKeyMsg,failedStr,loadedGame);
    					connectNegThread_.start();
    				} catch (SocketTimeoutException ste) {
    					;
    				} catch (IOException e) {
    					e.printStackTrace();
    					noExceptions_=false;
    				}
    			} else {
    				try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
    			}
    		} // end while 
		}
	}
	
	class ConnectNegThread extends Thread {
		static final int STATE_FAILED = -1;
		static final int STATE_NONE = 0;
		static final int STATE_WRITE_TABLE_INFO = 1;
		static final int STATE_READ_PLAYER_INFO = 2;
		static final int STATE_CREATE_CONNECTION = 3;
		
		int state;
		
		Socket localSocket;
		String tableNameMsg;
		String gameKeyMsg;
		boolean loadedGame;
		String failedStr;
		public ConnectNegThread(Socket localSocket,String tableNameMsg,String gameKeyMsg,String failedStr,boolean loadedGame_) {
			state=STATE_NONE;
			this.localSocket=localSocket;
			this.tableNameMsg=tableNameMsg;
			this.gameKeyMsg=gameKeyMsg;
			this.failedStr=failedStr;
			loadedGame=loadedGame_;
		}
		public void run() {
			Logger.log(LOG_TAG,"ConnectNegThread()");
			DataOutputStream outputStream=null;
			DataInputStream inputStream=null;
			String buffer="";
			String playerInfo="";
			long negTime=0;
			int reads=0;
			String errorMsg="";
			try {
				outputStream=new DataOutputStream(localSocket.getOutputStream());
				inputStream=new DataInputStream(localSocket.getInputStream());
				state=STATE_WRITE_TABLE_INFO;
				negTime=System.currentTimeMillis();
				Logger.log(LOG_TAG,"ConnectNegThread() - write table info");
			} catch (IOException e) {
				state=STATE_FAILED;
				errorMsg="Couldn't open streams";
				Logger.log(LOG_TAG,"ConnectNegThread() - "+errorMsg);
			}
			while (state!=STATE_FAILED&&state!=STATE_NONE) {
				if (state==STATE_WRITE_TABLE_INFO) {
					try {
						String tableMsg=tableNameMsg+"\n";
						outputStream.write(tableMsg.getBytes());
						localSocket.setSoTimeout(4000);
						state=STATE_READ_PLAYER_INFO;
						reads=0;
						Logger.log(LOG_TAG,"ConnectNegThread() - read player info");
					} catch (IOException e) {
						state=STATE_FAILED;
						errorMsg="Couldn't write table info";
						Logger.log(LOG_TAG,"ConnectNegThread() - "+errorMsg);
					}
				} else if (state==STATE_READ_PLAYER_INFO) {
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
								if (hostNetwork.validatePlayerInfo(msg)) {
									playerInfo=msg;
									state=STATE_CREATE_CONNECTION;
									buffer="";
									Logger.log(LOG_TAG,"ConnectNegThread() - create connection");
								} else if (msg.contains(PlayerNetwork.TAG_GOODBYE)) {
									state=STATE_FAILED;
									buffer="";
									errorMsg="Player backed out";
									Logger.log(LOG_TAG,"ConnectNegThread() - "+errorMsg+" - "+msg);
								} else {
									errorMsg="Player info validation failed";
									Logger.log(LOG_TAG,"ConnectNegThread() - "+errorMsg+" - "+msg);
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
								Logger.log(LOG_TAG,"ConnectNegThread() - "+errorMsg);
							}
						}
					} catch (IOException e) {
						state=STATE_FAILED;
						errorMsg="Couldn't read player info";
						Logger.log(LOG_TAG,"ConnectNegThread() - "+errorMsg);
					}
				} else if (state==STATE_CREATE_CONNECTION) {
					synchronized (playerConnections) {
						if (playerConnections.size()<Table.NUM_SEATS) {
							try {
								String ackString=gameKeyMsg+"\n";
								outputStream.write(ackString.getBytes());
								InetAddress address=localSocket.getInetAddress();
		    					String hostName=address.getHostName();
		    					for (int i=0;i<playerConnections.size();i++) {
									if (playerConnections.get(i).hostName.equals(hostName)) {
										playerConnections.get(i).disconnect("");
										playerConnections.remove(i);
									}
								}
		    					PlayerConnection newPlayerConnection=new PlayerConnection(localSocket,inputStream,outputStream,hostName,hostNetwork);
		    					newPlayerConnection.startKeepAlive();
		    					newPlayerConnection.startListen();
		    					playerConnections.add(newPlayerConnection);
		    					hostNetwork.notifyPlayerConnected(hostName,playerInfo);
								state=STATE_NONE;
								Logger.log(LOG_TAG,"ConnectNegThread() - connection successful");
							} catch (IOException e) {
								state=STATE_FAILED;
								errorMsg="Couldn't write ACK";
								Logger.log(LOG_TAG,"ConnectNegThread() - "+errorMsg);
							}
						}
					}
				}
				if (System.currentTimeMillis()-negTime>MAX_NEG_TIME) {
					state=STATE_FAILED;
					errorMsg="Max neg time elapsed";
					Logger.log(LOG_TAG,"ConnectNegThread() - "+errorMsg);
				}
			}
			// close the socket if neg was not successful
			if (state==STATE_FAILED) {
				String failedMsg=failedStr+errorMsg+"\n";
				if (outputStream!=null) {
					try {outputStream.write(failedMsg.getBytes());}
					catch (IOException e) {e.printStackTrace();}
				}
				if (inputStream!=null) {
					try {inputStream.close();}
					catch (IOException e) {e.printStackTrace();}
				}
				if (outputStream!=null) {
					try {outputStream.close();}
					catch (IOException e) {e.printStackTrace();}
				}
				if (localSocket!=null) {
					try {localSocket.close();}
					catch (IOException e) {e.printStackTrace();}
				}
			}
		}
	}
	
	// This is to reconnect all players after host activity stopped
	Object reconnectLock=new Object();
	public void stopReconnect() {
		Thread stopReconnectThread=new Thread(new Runnable() {
			public void run() {
				synchronized (reconnectLock) {
					Logger.log(LOG_TAG,"stopReconnect()");
					currentReconnectThread=null;
					if (reconnectServerSocket!=null) {
						try {reconnectServerSocket.close();}
						catch (IOException e) {e.printStackTrace();}
					}
				}
			}
		});
		stopReconnectThread.start();
	}
	
	public void startReconnect(final String tableNameStr,final String ackStr,final String failedStr) {
		synchronized (reconnectLock) {
			Logger.log(LOG_TAG,"startReconnect()");
			currentReconnectThread=new Thread(new reconnectRunnable(tableNameStr,ackStr,failedStr));
			currentReconnectThread.start();
		}
	}
	
	class reconnectRunnable implements Runnable {
		String tableNameStr;
		String ackStr;
		String failedStr;
		public reconnectRunnable(String tableNameStr,String ackStr,String failedStr) {
			this.tableNameStr=tableNameStr;
			this.ackStr=ackStr;
			this.failedStr=failedStr;
		}
		public void run() {
			Logger.log(LOG_TAG,"reconnectRunnable()");
    		// Wait for a TCP connection request
			reconnectServerSocket = null;
			boolean noExceptions_=true;
			try {
				reconnectServerSocket=new ServerSocket(RECONNECT_PORT);
				reconnectServerSocket.setSoTimeout(5000);
			} catch (IOException e1) {
				e1.printStackTrace();
				noExceptions_=false;
			}
			while (currentReconnectThread==Thread.currentThread()&&noExceptions_) {
				try {
					Socket localSocket=reconnectServerSocket.accept();
					ReconnectNegThread reconnectNegThread_=new ReconnectNegThread(localSocket,tableNameStr,ackStr,failedStr);
					reconnectNegThread_.start();
				} catch (SocketTimeoutException ste) {
					;
				} catch (IOException e) {
					e.printStackTrace();
					noExceptions_=false;
				}
    		} // end while
		}
	}
	
	class ReconnectNegThread extends Thread {
		static final int STATE_FAILED = -1;
		static final int STATE_NONE = 0;
		static final int STATE_WRITE_TABLE_INFO = 1;
		static final int STATE_READ_GAME_KEY = 2;
		static final int STATE_CREATE_CONNECTION = 3;
		
		int state;
		
		Socket localSocket;
		String tableNameMsg;
		String ackMsg;
		String failedStr;
		public ReconnectNegThread(Socket localSocket,String tableNameStr,String ackStr,String failedStr) {
			state=STATE_NONE;
			this.localSocket=localSocket;
			this.tableNameMsg=tableNameStr+"\n";
			this.ackMsg=ackStr+"\n";
			this.failedStr=failedStr;
		}
		public void run() {
			Logger.log(LOG_TAG,"ReconnectNegThread()");
			DataOutputStream outputStream=null;
			DataInputStream inputStream=null;
			String buffer="";
			String playerInfo="";
			long negTime=0;
			int reads=0;
			String errorMsg="";
			
			try {
				outputStream=new DataOutputStream(localSocket.getOutputStream());
				inputStream=new DataInputStream(localSocket.getInputStream());
				state=STATE_WRITE_TABLE_INFO;
				negTime=System.currentTimeMillis();
				Logger.log(LOG_TAG,"ReconnectNegThread() - write table info");
			} catch (IOException e) {
				state=STATE_FAILED;
				errorMsg="Couldn't open streams";
				Logger.log(LOG_TAG,"ReconnectNegThread() - "+errorMsg);
			}
			while (state!=STATE_FAILED&&state!=STATE_NONE) {
				if (state==STATE_WRITE_TABLE_INFO) {
					try {
						outputStream.write(tableNameMsg.getBytes());
						localSocket.setSoTimeout(4000);
						state=STATE_READ_GAME_KEY;
						reads=0;
						Logger.log(LOG_TAG,"ReconnectNegThread() - read game key");
					} catch (IOException e) {
						state=STATE_FAILED;
						errorMsg="Couldn't write table info";
						Logger.log(LOG_TAG,"ReconnectNegThread() - "+errorMsg);
					}
				} else if (state==STATE_READ_GAME_KEY) {
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
								if (hostNetwork.validateGameKey(msg)) {
									playerInfo=msg;
									state=STATE_CREATE_CONNECTION;
									buffer="";
									Logger.log(LOG_TAG,"ReconnectNegThread() - create connection");
								} else if (msg.contains(PlayerNetwork.TAG_GOODBYE)) {
									state=STATE_FAILED;
									buffer="";
									errorMsg="Player backed out";
									Logger.log(LOG_TAG,"ReconnectNegThread() - "+errorMsg+" - "+msg);
								} else {
									errorMsg="Game key validation failed";
									Logger.log(LOG_TAG,"ReconnectNegThread() - "+errorMsg+" - "+msg);
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
								Logger.log(LOG_TAG,"ReconnectNegThread() - "+errorMsg);
							}
						}
					} catch (IOException e) {
						state=STATE_FAILED;
						errorMsg="Couldn't read player info";
						Logger.log(LOG_TAG,"ReconnectNegThread() - "+errorMsg);
					}
				} else if (state==STATE_CREATE_CONNECTION) {
					synchronized (playerConnections) {
						try {
							outputStream.write(ackMsg.getBytes());
							InetAddress address=localSocket.getInetAddress();
	    					String hostName=address.getHostName();
	    					synchronized (playerConnections) {
								// search for pre-existing connection to this player and remove it
	    						Logger.log(LOG_TAG,"ReconnectNegThread() - reconnection successful");
								for (int i=0;i<playerConnections.size();i++) {
									if (playerConnections.get(i).hostName.equals(hostName)) {
										playerConnections.get(i).disconnect("");
										playerConnections.remove(i);
										Logger.log(LOG_TAG,"ReconnectNegThread() - removed pre-existing connection");
									}
								}
								// add this connection to our list and initiate comms protocols
								PlayerConnection newPlayerConnection=new PlayerConnection(
		    							localSocket,inputStream,outputStream,hostName,hostNetwork);
								newPlayerConnection.startKeepAlive();
								newPlayerConnection.startListen();
		    					playerConnections.add(newPlayerConnection);
		    					// notify activity that player has reconnected
		    					hostNetwork.notifyPlayerReconnected(hostName,playerInfo);
							}
							state=STATE_NONE;
						} catch (IOException e) {
							state=STATE_FAILED;
							errorMsg="Couldn't write ACK";
							Logger.log(LOG_TAG,"ReconnectNegThread() - "+errorMsg);
						}
					}
				}
				if (System.currentTimeMillis()-negTime>MAX_NEG_TIME) {
					state=STATE_FAILED;
					errorMsg="Max neg time elapsed";
					Logger.log(LOG_TAG,"ReconnectNegThread() - "+errorMsg);
				}
			}
			// close the socket if neg was not successful
			if (state==STATE_FAILED) {
				String failedMsg=failedStr+errorMsg+"\n";
				if (outputStream!=null) {
					try {outputStream.write(failedMsg.getBytes());}
					catch (IOException e) {e.printStackTrace();}
				}
				if (inputStream!=null) {
					try {inputStream.close();}
					catch (IOException e) {e.printStackTrace();}
				}
				if (outputStream!=null) {
					try {outputStream.close();}
					catch (IOException e) {e.printStackTrace();}
				}
				if (localSocket!=null) {
					try {localSocket.close();}
					catch (IOException e) {e.printStackTrace();}
				}
			}
		}
	}
	
	public void sendToAll(String msg) {
		synchronized (playerConnections) {
			Logger.log(LOG_TAG,"sendToAll("+msg+")");
			for (PlayerConnection playerConnection:playerConnections) {
				playerConnection.send(msg);
			}
		}
	}
	
	public void sendToPlayer(String msg, String hostName) {
		synchronized (playerConnections) {
			Logger.log(LOG_TAG,"sendToPlayer("+msg+","+hostName+")");
			for (PlayerConnection playerConnection:playerConnections) {
				if (playerConnection.hostName.equals(hostName)) {
					playerConnection.send(msg);
				}
			}
		}
	}
	
	public boolean removePlayer(String _hostName) {
		boolean playersLeft_;
		synchronized (playerConnections) {
			Logger.log(LOG_TAG,"removePlayer("+_hostName+")");
			for (int i=0;i<playerConnections.size();i++) {
				if (playerConnections.get(i).hostName.equals(_hostName)) {
					playerConnections.get(i).disconnect(HostNetwork.TAG_GOODBYE);
					playerConnections.remove(i);
				}
			}
			playersLeft_=playerConnections.size()>0;
		}
		return playersLeft_;
	}
	
	public void removeAll(String exitMsg_) {
		Logger.log(LOG_TAG,"removeAll()");
		synchronized (playerConnections) {
			for (PlayerConnection playerConnection : playerConnections) {
				playerConnection.disconnect(exitMsg_);
			}
			playerConnections.clear();
		}
	}
	
	private static void simulateDelay() {
		try{Thread.sleep(DEBUG_SIMULATED_DELAY);}catch(InterruptedException e){}
	}
	
	private static void simulateDelay(int delay) {
		try{Thread.sleep(delay);}catch(InterruptedException e){}
	}
}