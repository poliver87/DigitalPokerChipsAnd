package com.bidjee.digitalpokerchips;

import java.util.ArrayList;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import com.badlogic.gdx.Gdx;
import com.bidjee.digitalpokerchips.PlayerNetworkService.PlayerNetworkServiceBinder;
import com.bidjee.digitalpokerchips.c.DPCGame;
import com.bidjee.digitalpokerchips.c.ThisPlayer;
import com.bidjee.digitalpokerchips.i.IPlayerNetwork;
import com.bidjee.digitalpokerchips.m.ChipCase;
import com.bidjee.digitalpokerchips.m.ChipStack;
import com.bidjee.digitalpokerchips.m.DiscoveredTable;
import com.bidjee.digitalpokerchips.m.PlayerEntry;
import com.bidjee.util.Logger;

public class PlayerNetwork implements IPlayerNetwork {
	
	public static final String LOG_TAG = "DPCPlayerNetwork";
	
	////////////////////////////// Network Protocol Tags //////////////////////////////
	public static final String TAG_PLAYER_NAME_OPEN = "<PLAYER_NAME>";
	public static final String TAG_PLAYER_NAME_CLOSE = "<PLAYER_NAME/>";
	public static final String TAG_PLAYER_NAME_NEG_OPEN = "<PLAYER_NAME_NEG>";
	public static final String TAG_PLAYER_NAME_NEG_CLOSE = "<PLAYER_NAME_NEG/>";
	public static final String TAG_SUBMIT_MOVE_OPEN = "<SUBMIT_MOVE>";
	public static final String TAG_SUBMIT_MOVE_CLOSE = "<SUBMIT_MOVE/>";
	public static final String TAG_MOVE_OPEN = "<MOVE>";
	public static final String TAG_MOVE_CLOSE = "<MOVE/>";
	public static final String TAG_CHIPS_OPEN = "<CHIPS>";
	public static final String TAG_CHIPS_CLOSE = "<CHIPS/>";
	public static final String TAG_AZIMUTH_OPEN = "<AZIMUTH>";
	public static final String TAG_AZIMUTH_CLOSE = "<AZIMUTH/>";
	public static final String TAG_NUM_A_OPEN = "<NUM_A>";
	public static final String TAG_NUM_A_CLOSE = "<NUM_A/>";
	public static final String TAG_NUM_B_OPEN = "<NUM_B>";
	public static final String TAG_NUM_B_CLOSE = "<NUM_B/>";
	public static final String TAG_NUM_C_OPEN = "<NUM_C>";
	public static final String TAG_NUM_C_CLOSE = "<NUM_C/>";
	public static final String TAG_GOODBYE = "<GOODBYE/>";
	public static final String TAG_RECONNECT_FAILED = "<TAG_RECONNECT_FAILED/>";
	public static final String TAG_SETUP_ACK = "<DPC_SETUP_ACK/>";
	public static final String TAG_CHIPS_ACK = "<DPC_WIN_ACK/>";
	public static final String TAG_GOODBYE_ACK = "<DPC_GOODBYE_ACK/>";
	public static final String TAG_SEND_BELL_OPEN = "<BELL_OPEN>";
	public static final String TAG_SEND_BELL_CLOSE = "<BELL_OPEN/>";
	
	
	////////////////////////////// State Variables //////////////////////////////	
	/*
	 * tableConnected: to say whether we should be connected to table
	 * doingDiscover: to say whether we should be doing discover
	 * hostBytes: cached here for reconnect protocol
	 * playerName: cached here for discover and reconnect
	 * doingReconnect: to say whether we should be doing reconnect
	 * isActionable: to recover any lost messages after host or client drops out
	 * lastCommand: to check against host after host or client drops out
	 * lastReply: to resend if needed after host or client drops out
	 * game_key: for security when connecting via reconnect
	 */
	boolean connectServiceBound=false;
	boolean wifiEnabled;
	boolean tableConnected;
	boolean doingHostDiscover;
	byte[] hostBytes;
	String playerName;
	boolean doingReconnect;
	String lastCommand="";
	String lastReply="";
	String game_key="";
	////////////////////////////// Contained Objects //////////////////////////////
	PlayerNetworkService playerNetworkService;
	////////////////////////////// References //////////////////////////////
	ThisPlayer player;
	
	public PlayerNetwork() {
		tableConnected=false;
		doingHostDiscover=false;
		hostBytes=null;
		playerName="";
		doingReconnect=false;
	}
	
	////////////////////////////// Lifecycle Events //////////////////////////////
	
	public void onSaveInstanceState(Bundle outState_) {
		outState_.putBoolean("tableConnected",tableConnected);
		outState_.putBoolean("doingHostDiscover",doingHostDiscover);
		outState_.putByteArray("hostBytes",hostBytes);
		outState_.putString("playerName",playerName);
		outState_.putBoolean("doingReconnect",doingReconnect);
	}
	
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		tableConnected=savedInstanceState.getBoolean("tableConnected");
		doingHostDiscover=savedInstanceState.getBoolean("doingHostDiscover");
		hostBytes=savedInstanceState.getByteArray("hostBytes");
		playerName=savedInstanceState.getString("playerName");
		doingReconnect=savedInstanceState.getBoolean("doingReconnect");
	}
	
	public void onStart(Context c_) {
		Intent playerConnectServiceIntent = new Intent(c_,PlayerNetworkService.class);
		c_.bindService(playerConnectServiceIntent,networkServiceConnection,Context.BIND_AUTO_CREATE);
		if (tableConnected) {
			player.notifyConnectionLost();
		}
	}
	
	public void onStop(Context c_) {
		if (connectServiceBound) {
			playerNetworkService.stopDiscover();
			playerNetworkService.stopListen();
			playerNetworkService.stopReconnect();
			if (tableConnected) {
				playerNetworkService.disconnectCurrentGame();
			}
			c_.unbindService(networkServiceConnection);
			connectServiceBound=false;
		}
	}
	
	private ServiceConnection networkServiceConnection=new ServiceConnection() {
    	public void onServiceConnected(ComponentName className,IBinder service) {
    		Logger.log(LOG_TAG,"onServiceConnected()");
    		PlayerNetworkServiceBinder binder=(PlayerNetworkServiceBinder)service;
    		playerNetworkService=binder.getService();
    		connectServiceBound=true;
    		playerNetworkService.playerNetwork=PlayerNetwork.this;
    		if (tableConnected) {
    			doingReconnect=true;
    			spawnReconnect();
    		}
    		if (doingHostDiscover&&wifiEnabled) {
    			spawnDiscover();
    		}
    	}    	
    	public void onServiceDisconnected(ComponentName arg0) {
    		Logger.log(LOG_TAG,"onServiceDisconnected()");
    		connectServiceBound=false;
    	}
    };
    
	@Override
    public void startRequestGames() {
		Logger.log(LOG_TAG,"startRequestGames()");
    	if (connectServiceBound&&wifiEnabled&&!doingHostDiscover) {
    		spawnDiscover();
    	}
    	doingHostDiscover=true;
    }

	@Override
    public void stopRequestGames() {
		Logger.log(LOG_TAG,"startRequestGames()");
    	if (connectServiceBound) {
    		playerNetworkService.stopDiscover();
    	}
    	doingHostDiscover=false;
    }
	
	@Override
	public void requestInvitation(byte[] hostBytes) {
		if (connectServiceBound) {
			Logger.log(LOG_TAG,"requestInvitation()");
			String playerAnnounceStr=PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN+playerName+PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE;
    		playerNetworkService.requestInvitation(hostBytes,playerAnnounceStr);
    	}
	}
	
	////////////////////////////// Thread Spawners //////////////////////////////
	public void spawnDiscover() {
		Logger.log(LOG_TAG,"spawnDiscover()");
		String playerAnnounceStr=PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN+playerName+PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE;
		playerNetworkService.startDiscover(playerAnnounceStr);
	}
	
	public void spawnConnect(byte[] hostBytes,String playerName,int azimuth,int[] chipNumbers) {
		Logger.log(LOG_TAG,"spawnConnect()");
		String playerSetupString=PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN+playerName+PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE;
		playerSetupString+=PlayerNetwork.TAG_AZIMUTH_OPEN+azimuth+PlayerNetwork.TAG_AZIMUTH_CLOSE;
		playerSetupString+=PlayerNetwork.TAG_NUM_A_OPEN+chipNumbers[ChipCase.CHIP_A]+PlayerNetwork.TAG_NUM_A_CLOSE;
		playerSetupString+=PlayerNetwork.TAG_NUM_B_OPEN+chipNumbers[ChipCase.CHIP_B]+PlayerNetwork.TAG_NUM_B_CLOSE;
		playerSetupString+=PlayerNetwork.TAG_NUM_C_OPEN+chipNumbers[ChipCase.CHIP_C]+PlayerNetwork.TAG_NUM_C_CLOSE;
		playerNetworkService.playerConnect(hostBytes,playerSetupString);
	}
	
	public void spawnReconnect() {
		Logger.log(LOG_TAG,"spawnReconnect()");
		String reconnectMsg=HostNetwork.TAG_GAME_KEY_OPEN+game_key+HostNetwork.TAG_GAME_KEY_CLOSE;
		playerNetworkService.startReconnect(hostBytes,reconnectMsg);
	}
	
	////////////////////////////// Getters and Setters //////////////////////////////
	public void setWifiEnabled(boolean en_) {
		wifiEnabled=en_;
		if (wifiEnabled) {
    		if (doingHostDiscover) {
    			if (connectServiceBound) {
    				spawnDiscover();
    	    	}
    		}
    	} else {
    		if (doingHostDiscover) {
    			if (connectServiceBound) {
    				playerNetworkService.stopDiscover();
    	    	}
    		}
    	}
	}
	
	@Override
	public void setPlayer(ThisPlayer player) {
		this.player=player;
	}
	
	////////////////////////////// Helpers //////////////////////////////
	public boolean validateTableInfo(String msg) {
		return (msg.contains(HostNetwork.TAG_TABLE_NAME_OPEN)&&msg.contains(HostNetwork.TAG_TABLE_NAME_CLOSE));
	}
	
	public boolean validateReconnectTableInfo(String msg) {
		return (msg.contains(HostNetwork.TAG_RECONNECT_TABLE_NAME_OPEN)&&msg.contains(HostNetwork.TAG_RECONNECT_TABLE_NAME_CLOSE));
	}

	public boolean validateTableACK(String ackMsg) {
		return (ackMsg.contains(HostNetwork.TAG_GAME_KEY_OPEN)&&ackMsg.contains(HostNetwork.TAG_GAME_KEY_CLOSE));
	}
	
	public boolean validateReconnectACK(String ackMsg) {
		return (ackMsg.contains(HostNetwork.TAG_RECONNECT_SUCCESSFUL));
	}
	
	////////////////////////////// Reconnect Protocol //////////////////////////////
	public void startReconnect() {
		if (tableConnected) {
			Logger.log(LOG_TAG,"startReconnect()");
			player.notifyConnectionLost();
			doingReconnect=true;
			spawnReconnect();
		}
	}
	
	public void notifyReconnected() {
		Logger.log(LOG_TAG,"notifyReconnected()");
		player.notifyReconnected();
		doingReconnect=false;
		playerNetworkService.sendToHost(TAG_PLAYER_NAME_OPEN+playerName+TAG_PLAYER_NAME_CLOSE);
	}
	
	////////////////////////////// Player sends Message to Table //////////////////////////////

	@Override
    public void requestConnect(DiscoveredTable table_,int azimuth_,int[] chipNumbers) {
		Logger.log(LOG_TAG,"requestConnect("+table_.getName()+","+azimuth_+")");
    	if (connectServiceBound) {
    		hostBytes=table_.getHostBytes();
    		spawnConnect(table_.getHostBytes(),playerName,azimuth_,chipNumbers);
    	} else {
    		player.notifyConnectResult(false,"");
    	}
    }

	@Override
    public void setName(String playerName) {
		Logger.log(LOG_TAG,"setName("+playerName+")");
    	this.playerName=playerName;
    	if (tableConnected) {
    		if (connectServiceBound&&!doingReconnect) {
    			String msg=TAG_PLAYER_NAME_OPEN+playerName+TAG_PLAYER_NAME_CLOSE;
    			playerNetworkService.sendToHost(msg);
    		}
    	}
    }

	@Override
    public void submitMove(int move,String chipString) {
		Logger.log(LOG_TAG,"submitMove("+move+","+chipString+")");
    	if (connectServiceBound&&!doingReconnect) {
    		String msg=TAG_SUBMIT_MOVE_OPEN;
    		msg+=TAG_MOVE_OPEN+move+TAG_MOVE_CLOSE;
    		msg+=TAG_CHIPS_OPEN+chipString+TAG_CHIPS_CLOSE;
    		msg+=TAG_SUBMIT_MOVE_CLOSE;
    		playerNetworkService.sendToHost(msg);
    		lastReply=msg;
    	}
    }

	@Override
    public void leaveTable() {
    	if (connectServiceBound) {
    		Logger.log(LOG_TAG,"leaveTable()");
    		tableConnected=false;
    		doingReconnect=false;
    		playerNetworkService.leaveTable(TAG_GOODBYE);
    	}
    }
	
	@Override
	public void sendBell(String hostName) {
		if (connectServiceBound&&!doingReconnect) {
			//Logger.log(LOG_TAG,"sendBell()");
    		String msg=TAG_SEND_BELL_OPEN+hostName+TAG_SEND_BELL_CLOSE;
    		playerNetworkService.sendToHost(msg);
    	}
	}
	
	////////////////////////////// Table sends Message to Player //////////////////////////////
    
	public void notifyTableFound(final byte[] hostBytes,final String rxMsg) {
		Logger.log(LOG_TAG,"notifyTableFound("+rxMsg+")");
		int startIndex=rxMsg.indexOf(HostNetwork.TAG_TABLE_NAME_OPEN) + HostNetwork.TAG_TABLE_NAME_OPEN.length();
		int endIndex=rxMsg.indexOf(HostNetwork.TAG_TABLE_NAME_CLOSE);
		final String tableName=rxMsg.substring(startIndex,endIndex);
		final int[] vals=new int[ChipCase.CHIP_TYPES];
		startIndex=rxMsg.indexOf(HostNetwork.TAG_VAL_A_OPEN) + HostNetwork.TAG_VAL_A_OPEN.length();
		endIndex=rxMsg.indexOf(HostNetwork.TAG_VAL_A_CLOSE);
		vals[ChipCase.CHIP_A]=Integer.parseInt(rxMsg.substring(startIndex,endIndex));
		startIndex=rxMsg.indexOf(HostNetwork.TAG_VAL_B_OPEN) + HostNetwork.TAG_VAL_B_OPEN.length();
		endIndex=rxMsg.indexOf(HostNetwork.TAG_VAL_B_CLOSE);
		vals[ChipCase.CHIP_B]=Integer.parseInt(rxMsg.substring(startIndex,endIndex));
		startIndex=rxMsg.indexOf(HostNetwork.TAG_VAL_C_OPEN) + HostNetwork.TAG_VAL_C_OPEN.length();
		endIndex=rxMsg.indexOf(HostNetwork.TAG_VAL_C_CLOSE);
		vals[ChipCase.CHIP_C]=Integer.parseInt(rxMsg.substring(startIndex,endIndex));
		final boolean loadedGame=rxMsg.contains(HostNetwork.TAG_LOADED_GAME);
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				if (player!=null) {
					player.notifyTableFound(new DiscoveredTable(hostBytes,tableName,vals),loadedGame);
				}
			}
		});
	}
	
	public void notifyGameConnected(String msg) {
		Logger.log(LOG_TAG,"notifyGameConnected("+msg+")");
		int startIndex = msg.indexOf(HostNetwork.TAG_TABLE_NAME_OPEN) + HostNetwork.TAG_TABLE_NAME_OPEN.length();
		int endIndex = msg.indexOf(HostNetwork.TAG_TABLE_NAME_CLOSE);
		final String tableName = msg.substring(startIndex, endIndex);
		startIndex = msg.indexOf(HostNetwork.TAG_GAME_KEY_OPEN) + HostNetwork.TAG_GAME_KEY_OPEN.length();
		endIndex = msg.indexOf(HostNetwork.TAG_GAME_KEY_CLOSE);
		final String game_key = msg.substring(startIndex, endIndex);
		tableConnected=true;
		this.game_key=game_key;
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				if (player!=null) {
	    			player.notifyConnectResult(true,tableName);
				}
			}
		});
	}
	
	public void notifyConnectFailed() {
		Logger.log(LOG_TAG,"notifyConnectFailed()");
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				if (player!=null) {
					player.notifyConnectResult(false,"");
				}
			}
		});
	}
	
	private void setLastCommand(String lastCommand) {
		this.lastCommand=lastCommand;
		this.lastReply="";
	}
	
	private void resendLast() {
		playerNetworkService.sendToHost(lastReply);
		Logger.log(LOG_TAG,"resendLast("+lastReply+")");
	}

	public void parseGameMessage(String msg) {
		boolean resendReply=false;
		Logger.log(LOG_TAG,"parseGameMessage("+msg+")");
		if (msg.contains(HostNetwork.TAG_RESEND_OPEN)&&msg.contains(HostNetwork.TAG_RESEND_CLOSE)) {
			// strip off re-send header
			int startIndex = msg.indexOf(HostNetwork.TAG_RESEND_OPEN) + HostNetwork.TAG_RESEND_OPEN.length();
			int endIndex = msg.indexOf(HostNetwork.TAG_RESEND_CLOSE);
			msg = msg.substring(startIndex, endIndex);
			if (msg.equals(lastCommand)&&!lastReply.equals("")) {
				resendReply=true;
			}
		}
		if (msg.contains(HostNetwork.TAG_SETUP_INFO_OPEN)&&msg.contains(HostNetwork.TAG_SETUP_INFO_CLOSE)) {
			// send ACK to host
			playerNetworkService.sendToHost(PlayerNetwork.TAG_SETUP_ACK);
			if (!resendReply) {
				setLastCommand(msg);
				int startIndex = msg.indexOf(HostNetwork.TAG_COLOR_OPEN) + HostNetwork.TAG_COLOR_OPEN.length();
				int endIndex = msg.indexOf(HostNetwork.TAG_COLOR_CLOSE);
				final int color = Integer.parseInt(msg.substring(startIndex, endIndex));
				startIndex = msg.indexOf(HostNetwork.TAG_SEND_CHIPS_OPEN) + HostNetwork.TAG_SEND_CHIPS_OPEN.length();
				endIndex = msg.indexOf(HostNetwork.TAG_SEND_CHIPS_CLOSE);
				final String setupString = msg.substring(startIndex,endIndex);
    			Gdx.app.postRunnable(new Runnable() {
    				@Override
    				public void run() {
    					player.setupChips(ChipStack.parseStack(setupString),color);
    				}
    			});
			}
		} else if (msg.contains(HostNetwork.TAG_STATUS_MENU_UPDATE_OPEN)&&msg.contains(HostNetwork.TAG_STATUS_MENU_UPDATE_CLOSE)) {
			int startMsg = msg.indexOf(HostNetwork.TAG_STATUS_MENU_UPDATE_OPEN) + HostNetwork.TAG_STATUS_MENU_UPDATE_OPEN.length();
			int endMsg = msg.indexOf(HostNetwork.TAG_STATUS_MENU_UPDATE_CLOSE);
			final String statusMenuMsg = msg.substring(startMsg,endMsg);			
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					ArrayList<PlayerEntry> playerList=new ArrayList<PlayerEntry>();
					String buffer=statusMenuMsg;
					while (buffer.contains(HostNetwork.TAG_HOST_NAME_OPEN)&&buffer.contains(HostNetwork.TAG_AMOUNT_CLOSE)) {
						int startIndex = buffer.indexOf(HostNetwork.TAG_HOST_NAME_OPEN) + HostNetwork.TAG_HOST_NAME_OPEN.length();
						int endIndex = buffer.indexOf(HostNetwork.TAG_HOST_NAME_CLOSE);
						String hostName = buffer.substring(startIndex,endIndex);
						startIndex = buffer.indexOf(HostNetwork.TAG_PLAYER_NAME_OPEN) + HostNetwork.TAG_PLAYER_NAME_OPEN.length();
						endIndex = buffer.indexOf(HostNetwork.TAG_PLAYER_NAME_CLOSE);
						String playerName = buffer.substring(startIndex,endIndex);
						startIndex = buffer.indexOf(HostNetwork.TAG_AMOUNT_OPEN) + HostNetwork.TAG_AMOUNT_OPEN.length();
						endIndex = buffer.indexOf(HostNetwork.TAG_AMOUNT_CLOSE);
						int amount = Integer.parseInt(buffer.substring(startIndex, endIndex));
						playerList.add(new PlayerEntry(hostName,playerName,amount));
						buffer=buffer.substring(endIndex+HostNetwork.TAG_AMOUNT_CLOSE.length());
					}
					player.syncStatusMenu(playerList);
				}
			});
		} else if (msg.contains(HostNetwork.TAG_WAIT_NEXT_HAND)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.notifyWaitNextHand();
				}
			});
		} else if (msg.contains(HostNetwork.TAG_YOUR_BET_OPEN)&&msg.contains(HostNetwork.TAG_YOUR_BET_CLOSE)) {
			if (resendReply) {
				resendLast();
			} else {
				setLastCommand(msg);
				int startIndex = msg.indexOf(HostNetwork.TAG_STAKE_OPEN) + HostNetwork.TAG_STAKE_OPEN.length();
				int endIndex = msg.indexOf(HostNetwork.TAG_STAKE_CLOSE);
				final int betStake = Integer.parseInt(msg.substring(startIndex, endIndex));
				startIndex = msg.indexOf(HostNetwork.TAG_FOLD_ENABLED_OPEN) + HostNetwork.TAG_FOLD_ENABLED_OPEN.length();
				endIndex = msg.indexOf(HostNetwork.TAG_FOLD_ENABLED_CLOSE);
				final boolean foldEnabled = Boolean.parseBoolean(msg.substring(startIndex, endIndex));
				startIndex = msg.indexOf(HostNetwork.TAG_MESSAGE_OPEN) + HostNetwork.TAG_MESSAGE_OPEN.length();
				endIndex = msg.indexOf(HostNetwork.TAG_MESSAGE_CLOSE);
				final String message = msg.substring(startIndex, endIndex);
				startIndex = msg.indexOf(HostNetwork.TAG_MESSAGE_STATE_CHANGE_OPEN) + HostNetwork.TAG_MESSAGE_STATE_CHANGE_OPEN.length();
				endIndex = msg.indexOf(HostNetwork.TAG_MESSAGE_STATE_CHANGE_CLOSE);
				final String messageStateChange = msg.substring(startIndex, endIndex);
				Gdx.app.postRunnable(new Runnable() {
					@Override
					public void run() {
						if (messageStateChange.equals("")) {
							player.promptMove(betStake,foldEnabled,message);
						} else {
							player.promptStateChange(messageStateChange,betStake,foldEnabled,message);
						}
					}
				});
			}
		} else if (msg.contains(HostNetwork.TAG_SEND_CHIPS_OPEN)&&msg.contains(HostNetwork.TAG_SEND_CHIPS_CLOSE)) {
			playerNetworkService.sendToHost(PlayerNetwork.TAG_CHIPS_ACK);
			if (!resendReply) {
				setLastCommand(msg);
				int startIndex = msg.indexOf(HostNetwork.TAG_SEND_CHIPS_OPEN) + HostNetwork.TAG_SEND_CHIPS_OPEN.length();
				int endIndex = msg.indexOf(HostNetwork.TAG_SEND_CHIPS_CLOSE);
				final String chipString = msg.substring(startIndex, endIndex);
				Gdx.app.postRunnable(new Runnable() {
					@Override
					public void run() {
						player.doWin(ChipStack.parseStack(chipString));
					}
				});
			}
		} else if (msg.contains(HostNetwork.TAG_SEND_DEALER)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.setDealer(true);
				}
			});
		} else if (msg.contains(HostNetwork.TAG_RECALL_DEALER)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.setDealer(false);
				}
			});
		} else if (msg.contains(HostNetwork.TAG_TEXT_MESSAGE_OPEN)&&msg.contains(HostNetwork.TAG_TEXT_MESSAGE_CLOSE)) {
			int startIndex = msg.indexOf(HostNetwork.TAG_TEXT_MESSAGE_OPEN) + HostNetwork.TAG_TEXT_MESSAGE_OPEN.length();
			int endIndex = msg.indexOf(HostNetwork.TAG_TEXT_MESSAGE_CLOSE);
			final String textMessage = msg.substring(startIndex,endIndex);
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.textMessage(textMessage);
				}
			});
		} else if (msg.contains(HostNetwork.TAG_SEND_BELL)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.doBell();
				}
			});
		} else if (msg.contains(HostNetwork.TAG_ENABLE_NUDGE_OPEN)&&msg.contains(HostNetwork.TAG_ENABLE_NUDGE_CLOSE)) {
			int startIndex = msg.indexOf(HostNetwork.TAG_ENABLE_NUDGE_OPEN) + HostNetwork.TAG_ENABLE_NUDGE_OPEN.length();
			int endIndex = msg.indexOf(HostNetwork.TAG_ENABLE_NUDGE_CLOSE);
			final String hostName = msg.substring(startIndex,endIndex);
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.enableNudge(hostName);
				}
			});
		} else if (msg.contains(HostNetwork.TAG_CANCEL_MOVE)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.cancelMove();
				}
			});
		} else if (msg.contains(HostNetwork.TAG_DISABLE_NUDGE)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.disableNudge();
				}
			});
		} else if (msg.contains(HostNetwork.TAG_SHOW_CONNECTION)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.showConnection();
				}
			});
		} else if (msg.contains(HostNetwork.TAG_HIDE_CONNECTION)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.hideConnection();
				}
			});
		} else if (msg.contains(HostNetwork.TAG_GOODBYE)) {
			playerNetworkService.sendToHost(PlayerNetwork.TAG_GOODBYE_ACK);
			if (!resendReply) {
				setLastCommand(msg);
    			leaveTable();
    			Gdx.app.postRunnable(new Runnable() {
    				@Override
    				public void run() {
    					player.notifyBootedByHost();
    				}
    			});
			}
		}
	}

}
