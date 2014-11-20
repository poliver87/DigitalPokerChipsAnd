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
import com.bidjee.digitalpokerchips.c.ThisPlayer;
import com.bidjee.digitalpokerchips.i.IPlayerNetwork;
import com.bidjee.digitalpokerchips.m.ChipCase;
import com.bidjee.digitalpokerchips.m.ChipStack;
import com.bidjee.digitalpokerchips.m.DiscoveredTable;
import com.bidjee.digitalpokerchips.m.Move;
import com.bidjee.digitalpokerchips.m.MovePrompt;
import com.bidjee.digitalpokerchips.m.PlayerEntry;
import com.bidjee.util.Logger;

public class PlayerNetwork implements IPlayerNetwork {
	
	public static final String LOG_TAG = "DPCPlayerNetwork";
	
	////////////////////////////// Network Protocol Tags //////////////////////////////
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
	////////////////////////////// Contained Objects //////////////////////////////
	PlayerNetworkService playerNetworkService;
	////////////////////////////// References //////////////////////////////
	ThisPlayer player;
	
	public PlayerNetwork() {
		tableConnected=false;
		doingHostDiscover=false;
		hostBytes=null;
		playerName="";
	}
	
	////////////////////////////// Lifecycle Events //////////////////////////////
	
	public void onSaveInstanceState(Bundle outState_) {
		outState_.putBoolean("tableConnected",tableConnected);
		outState_.putBoolean("doingHostDiscover",doingHostDiscover);
		outState_.putByteArray("hostBytes",hostBytes);
		outState_.putString("playerName",playerName);
	}
	
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		tableConnected=savedInstanceState.getBoolean("tableConnected");
		doingHostDiscover=savedInstanceState.getBoolean("doingHostDiscover");
		hostBytes=savedInstanceState.getByteArray("hostBytes");
		playerName=savedInstanceState.getString("playerName");
	}
	
	public void onStart(Context c_) {
		Intent playerConnectServiceIntent = new Intent(c_,PlayerNetworkService.class);
		c_.bindService(playerConnectServiceIntent,networkServiceConnection,Context.BIND_AUTO_CREATE);
	}
	
	public void onStop(Context c_) {
		if (connectServiceBound) {
			playerNetworkService.stopDiscover();
			playerNetworkService.stopListen();
			if (tableConnected) {
				playerNetworkService.disconnectCurrentGame();
				player.notifyConnectionLost();
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
		if (connectServiceBound&&wifiEnabled) {
			Logger.log(LOG_TAG,"requestInvitation()");
			String playerAnnounceStr=PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN+playerName+PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE;
    		playerNetworkService.requestInvitation(hostBytes,playerAnnounceStr);
    	}
	}
	
	@Override
	public void stopListen() {
		playerNetworkService.stopListen();
		if (tableConnected) {
			playerNetworkService.disconnectCurrentGame();
		}
	}
	
	////////////////////////////// Thread Spawners //////////////////////////////
	private void spawnDiscover() {
		Logger.log(LOG_TAG,"spawnDiscover()");
		String playerAnnounceStr=PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN+playerName+PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE;
		playerNetworkService.startDiscover(playerAnnounceStr);
	}
	
	private void spawnConnect(byte[] hostBytes,String playerName,int azimuth,int[] chipNumbers) {
		Logger.log(LOG_TAG,"spawnConnect()");
		String playerSetupString=PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN+playerName+PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE;
		playerSetupString+=PlayerNetwork.TAG_AZIMUTH_OPEN+azimuth+PlayerNetwork.TAG_AZIMUTH_CLOSE;
		if (chipNumbers!=null) {
			playerSetupString+=PlayerNetwork.TAG_NUM_A_OPEN+chipNumbers[ChipCase.CHIP_A]+PlayerNetwork.TAG_NUM_A_CLOSE;
			playerSetupString+=PlayerNetwork.TAG_NUM_B_OPEN+chipNumbers[ChipCase.CHIP_B]+PlayerNetwork.TAG_NUM_B_CLOSE;
			playerSetupString+=PlayerNetwork.TAG_NUM_C_OPEN+chipNumbers[ChipCase.CHIP_C]+PlayerNetwork.TAG_NUM_C_CLOSE;
		}
		playerNetworkService.playerConnect(hostBytes,playerSetupString);
	}
	
	////////////////////////////// Getters and Setters //////////////////////////////
	public void setWifiEnabled(boolean en_) {
		wifiEnabled=en_;
	}
	
	@Override
	public void setPlayer(ThisPlayer player) {
		this.player=player;
	}
	
	////////////////////////////// Helpers //////////////////////////////
	public boolean validateTableInfo(String msg) {
		return (msg.contains(HostNetwork.TAG_TABLE_NAME_OPEN)&&msg.contains(HostNetwork.TAG_TABLE_NAME_CLOSE));
	}

	public boolean validateTableACK(String ackMsg) {
		return (ackMsg.contains(HostNetwork.TAG_GAME_ACK));
	}
	
	////////////////////////////// Player sends Message to Table //////////////////////////////

	@Override
    public void requestConnect(DiscoveredTable table_,int azimuth_,int[] chipNumbers) {
		Logger.log(LOG_TAG,"requestConnect("+table_.getName()+","+azimuth_+")");
    	if (connectServiceBound&&wifiEnabled) {
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
    }

	@Override
    public void submitMove(Move move) {
		Logger.log(LOG_TAG,"submitMove("+move.moveType+","+move.chipString+")");
    	if (connectServiceBound) {
    		String msg=TAG_SUBMIT_MOVE_OPEN;
    		msg+=TAG_MOVE_OPEN+move.moveType+TAG_MOVE_CLOSE;
    		msg+=TAG_CHIPS_OPEN+move.chipString+TAG_CHIPS_CLOSE;
    		msg+=TAG_SUBMIT_MOVE_CLOSE;
    		playerNetworkService.sendToHost(msg);
    	}
    }

	@Override
    public void leaveTable() {
    	if (connectServiceBound) {
    		Logger.log(LOG_TAG,"leaveTable()");
    		tableConnected=false;
    		playerNetworkService.leaveTable(TAG_GOODBYE);
    	}
    }
	
	@Override
	public void sendBell(String hostName) {
		if (connectServiceBound) {
			//Logger.log(LOG_TAG,"sendBell()");
    		String msg=TAG_SEND_BELL_OPEN+hostName+TAG_SEND_BELL_CLOSE;
    		playerNetworkService.sendToHost(msg);
    	}
	}
	
	////////////////////////////// Table sends Message to Player //////////////////////////////
    
	public void discoverResponseRxd(final byte[] hostBytes,String rxMsg) {
		Logger.log(LOG_TAG,"discoverResponseRxd("+rxMsg+")");
		if (rxMsg.contains(HostNetwork.TAG_TABLE_NAME_OPEN)&&rxMsg.contains(HostNetwork.TAG_VAL_C_CLOSE)) {
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
			final boolean connectNow=rxMsg.contains(HostNetwork.TAG_CONNECT_NOW);
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					if (player!=null) {
						player.notifyTableFound(new DiscoveredTable(hostBytes,tableName,vals),connectNow);
					}
				}
			});
		}
	}
	
	public void notifyGameConnected(String msg) {
		Logger.log(LOG_TAG,"notifyGameConnected("+msg+")");
		int startIndex = msg.indexOf(HostNetwork.TAG_TABLE_NAME_OPEN) + HostNetwork.TAG_TABLE_NAME_OPEN.length();
		int endIndex = msg.indexOf(HostNetwork.TAG_TABLE_NAME_CLOSE);
		final String tableName = msg.substring(startIndex,endIndex);
		tableConnected=true;
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				if (player!=null) {
	    			player.notifyConnectResult(true,tableName);
	    			playerNetworkService.startListen();
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
	
	public void notifyConnectionLost() {
		Logger.log(LOG_TAG,"notifyConnectionLost()");
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				if (player!=null) {
					player.notifyConnectionLost();
				}
			}
		});
	}

	public void parseGameMessage(String msg) {
		if (!tableConnected) {
			// throw message away
		} else if (msg.contains(HostNetwork.TAG_COLOR_OPEN)&&msg.contains(HostNetwork.TAG_COLOR_CLOSE)) {
			int startIndex = msg.indexOf(HostNetwork.TAG_COLOR_OPEN) + HostNetwork.TAG_COLOR_OPEN.length();
			int endIndex = msg.indexOf(HostNetwork.TAG_COLOR_CLOSE);
			final int color = Integer.parseInt(msg.substring(startIndex, endIndex));
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.setColor(color);
				}
			});
		} else if (msg.contains(HostNetwork.TAG_STATUS_MENU_UPDATE_OPEN)&&msg.contains(HostNetwork.TAG_STATUS_MENU_UPDATE_CLOSE)) {
			int startMsg = msg.indexOf(HostNetwork.TAG_STATUS_MENU_UPDATE_OPEN) + HostNetwork.TAG_STATUS_MENU_UPDATE_OPEN.length();
			int endMsg = msg.indexOf(HostNetwork.TAG_STATUS_MENU_UPDATE_CLOSE);
			final String statusMenuMsg = msg.substring(startMsg,endMsg);			
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					ArrayList<PlayerEntry> playerList=new ArrayList<PlayerEntry>();
					String buffer=statusMenuMsg;
					while (buffer.contains(HostNetwork.TAG_PLAYER_NAME_OPEN)&&buffer.contains(HostNetwork.TAG_AMOUNT_CLOSE)) {
						int startIndex = buffer.indexOf(HostNetwork.TAG_PLAYER_NAME_OPEN) + HostNetwork.TAG_PLAYER_NAME_OPEN.length();
						int endIndex = buffer.indexOf(HostNetwork.TAG_PLAYER_NAME_CLOSE);
						String playerName = buffer.substring(startIndex,endIndex);
						startIndex = buffer.indexOf(HostNetwork.TAG_AMOUNT_OPEN) + HostNetwork.TAG_AMOUNT_OPEN.length();
						endIndex = buffer.indexOf(HostNetwork.TAG_AMOUNT_CLOSE);
						int amount = Integer.parseInt(buffer.substring(startIndex, endIndex));
						playerList.add(new PlayerEntry(playerName,amount));
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
			int startIndex = msg.indexOf(HostNetwork.TAG_STAKE_OPEN) + HostNetwork.TAG_STAKE_OPEN.length();
			int endIndex = msg.indexOf(HostNetwork.TAG_STAKE_CLOSE);
			final int betStake = Integer.parseInt(msg.substring(startIndex, endIndex));
			startIndex = msg.indexOf(HostNetwork.TAG_BLINDS_OPEN) + HostNetwork.TAG_BLINDS_OPEN.length();
			endIndex = msg.indexOf(HostNetwork.TAG_BLINDS_CLOSE);
			final int blinds = Integer.parseInt(msg.substring(startIndex, endIndex));
			final MovePrompt thisMovePrompt=new MovePrompt(betStake,blinds);
			final int chipAmount=HostNetwork.unwrapInt(msg,HostNetwork.TAG_SYNC_CHIPS_WITH_MOVE_OPEN,
					HostNetwork.TAG_SYNC_CHIPS_WITH_MOVE_CLOSE);
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.syncChips(chipAmount);
					player.promptMove(thisMovePrompt);
				}
			});
		} else if (msg.contains(HostNetwork.TAG_SYNC_CHIPS_OPEN)&&msg.contains(HostNetwork.TAG_SYNC_CHIPS_CLOSE)) {
			final int chipAmount=HostNetwork.unwrapInt(msg,HostNetwork.TAG_SYNC_CHIPS_OPEN,HostNetwork.TAG_SYNC_CHIPS_CLOSE);
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.syncChips(chipAmount);
				}
			});
		} else if (msg.contains(HostNetwork.TAG_SEND_CHIPS_BUYIN_OPEN)&&msg.contains(HostNetwork.TAG_SEND_CHIPS_BUYIN_CLOSE)) {
			int startIndex = msg.indexOf(HostNetwork.TAG_SEND_CHIPS_BUYIN_OPEN) + HostNetwork.TAG_SEND_CHIPS_BUYIN_OPEN.length();
			int endIndex = msg.indexOf(HostNetwork.TAG_SEND_CHIPS_BUYIN_CLOSE);
			final String chipString = msg.substring(startIndex, endIndex);
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.doWin(ChipStack.parseStack(chipString));
				}
			});
		} else if (msg.contains(HostNetwork.TAG_SEND_CHIPS_WIN_OPEN)&&msg.contains(HostNetwork.TAG_SEND_CHIPS_WIN_CLOSE)) {
			int startIndex = msg.indexOf(HostNetwork.TAG_SEND_CHIPS_WIN_OPEN) + HostNetwork.TAG_SEND_CHIPS_WIN_OPEN.length();
			int endIndex = msg.indexOf(HostNetwork.TAG_SEND_CHIPS_WIN_CLOSE);
			final String chipString = msg.substring(startIndex, endIndex);
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.doWin(ChipStack.parseStack(chipString));
				}
			});
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
		} else if (msg.contains(HostNetwork.TAG_SEND_BELL)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.doBell();
				}
			});
		} else if (msg.contains(HostNetwork.TAG_CANCEL_MOVE)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					player.cancelMove();
				}
			});
		} else if (msg.contains(HostNetwork.TAG_GOODBYE)) {
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
