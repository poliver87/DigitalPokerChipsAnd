package com.bidjee.digitalpokerchips;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import com.badlogic.gdx.Gdx;
import com.bidjee.digitalpokerchips.HostNetworkService.HostNetworkServiceBinder;
import com.bidjee.digitalpokerchips.c.DPCGame;
import com.bidjee.digitalpokerchips.c.Table;
import com.bidjee.digitalpokerchips.i.IHostNetwork;
import com.bidjee.digitalpokerchips.m.ChipCase;
import com.bidjee.digitalpokerchips.m.Player;
import com.bidjee.util.Logger;

public class HostNetwork implements IHostNetwork {
	
	/////////////// Network Protocol Tags ///////////////
	public static final String TAG_SEND_DEALER = "<DPC_DEALER/>";
	public static final String TAG_SEND_DEALER_ACK = "<DPC_DEALER_ACK/>";
	public static final String TAG_RECALL_DEALER = "<DPC_RECALL_DEALER/>";
	public static final String TAG_RECALL_DEALER_ACK = "<DPC_RECALL_DEALER_ACK/>";
	public static final String TAG_STATUS_MENU_UPDATE_OPEN = "<STATUS_MENU_UPDATE>";
	public static final String TAG_STATUS_MENU_UPDATE_CLOSE = "<STATUS_MENU_UPDATE/>";
	public static final String TAG_HOST_NAME_OPEN = "<HOST_NAME>";
	public static final String TAG_HOST_NAME_CLOSE = "<HOST_NAME/>";
	public static final String TAG_PLAYER_NAME_OPEN = "<PLAYER_NAME>";
	public static final String TAG_PLAYER_NAME_CLOSE = "<PLAYER_NAME/>";
	public static final String TAG_AMOUNT_OPEN = "<AMOUNT>";
	public static final String TAG_AMOUNT_CLOSE = "<AMOUNT/>";
	public static final String TAG_RESEND_OPEN = "<RESEND>";
	public static final String TAG_RESEND_CLOSE = "<RESEND/>";
	public static final String TAG_SETUP_INFO_OPEN = "<SETUP_INFO>";
	public static final String TAG_SETUP_INFO_CLOSE = "<SETUP_INFO/>";
	public static final String TAG_COLOR_OPEN = "<COLOR>";
	public static final String TAG_COLOR_CLOSE = "<COLOR/>";
	public static final String TAG_YOUR_BET_OPEN = "<YOUR_BET>";
	public static final String TAG_YOUR_BET_CLOSE = "<YOUR_BET/>";
	public static final String TAG_STAKE_OPEN = "<STAKE>";
	public static final String TAG_STAKE_CLOSE = "<STAKE/>";
	public static final String TAG_FOLD_ENABLED_OPEN = "<FOLD_ENABLED>";
	public static final String TAG_FOLD_ENABLED_CLOSE = "<FOLD_ENABLED/>";
	public static final String TAG_MESSAGE_OPEN = "<MESSAGE>";
	public static final String TAG_MESSAGE_CLOSE = "<MESSAGE/>";
	public static final String TAG_MESSAGE_STATE_CHANGE_OPEN = "<MESSAGE_STATE_CHANGE>";
	public static final String TAG_MESSAGE_STATE_CHANGE_CLOSE = "<MESSAGE_STATE_CHANGE/>";
	public static final String TAG_SEND_CHIPS_OPEN = "<WIN>";
	public static final String TAG_SEND_CHIPS_CLOSE = "<WIN/>";
	public static final String TAG_TEXT_MESSAGE_OPEN = "<TEXT_MESSAGE>";
	public static final String TAG_TEXT_MESSAGE_CLOSE = "<TEXT_MESSAGE/>";
	public static final String TAG_GOODBYE = "<GOODBYE/>";
	public static final String TAG_TABLE_NAME_OPEN = "<TABLE_NAME>";
	public static final String TAG_TABLE_NAME_CLOSE = "<TABLE_NAME/>";
	public static final String TAG_VAL_A_OPEN = "<VAL_A>";
	public static final String TAG_VAL_A_CLOSE = "<VAL_A/>";
	public static final String TAG_VAL_B_OPEN = "<VAL_B>";
	public static final String TAG_VAL_B_CLOSE = "<VAL_B/>";
	public static final String TAG_VAL_C_OPEN = "<VAL_C>";
	public static final String TAG_VAL_C_CLOSE = "<VAL_C/>";
	public static final String TAG_GAME_KEY_OPEN = "<GAME_KEY>";
	public static final String TAG_GAME_KEY_CLOSE = "<GAME_KEY/>";
	public static final String TAG_RECONNECT_TABLE_NAME_OPEN = "<RECONNECT_TABLE_NAME>";
	public static final String TAG_RECONNECT_TABLE_NAME_CLOSE = "<RECONNECT_TABLE_NAME/>";
	public static final String TAG_RECONNECT_SUCCESSFUL = "<RECONNECT_SUCCESSFUL/>";
	public static final String TAG_RECONNECT_FAILED = "<RECONNECT_FAILED/>";
	public static final String TAG_CONNECT_UNSUCCESSFUL = "<DPC_CONNECTION_UNSUCCESSFUL/>";
	public static final String TAG_SEND_BELL = "<SENDING_BELL/>";
	public static final String TAG_ENABLE_NUDGE_OPEN = "<ENABLE_NUDGE>";
	public static final String TAG_ENABLE_NUDGE_CLOSE = "<ENABLE_NUDGE/>";
	public static final String TAG_DISABLE_NUDGE = "<DISABLE_NUDGE/>";
	public static final String TAG_SHOW_CONNECTION = "<SHOW_CONNECTION/>";
	public static final String TAG_HIDE_CONNECTION = "<HIDE_CONNECTION/>";
	public static final String TAG_LOADED_GAME = "<TAG_LOADED_GAME/>";
	public static final String TAG_CANCEL_MOVE = "<TAG_CANCEL_MOVE/>";
	public static final String TAG_WAIT_NEXT_HAND = "<TAG_WAIT_NEXT_HAND/>";
	
	/////////////// State Variables ///////////////
	boolean wifiEnabled;
	String ipAddress;
	private boolean connectServiceBound;
	boolean doingAnnounce;
	boolean doingAccept;
	boolean doingReconnect;
	boolean playersConnected;
	boolean loadedGame;
	String tableName;
	ArrayList<String> playerNames;
	HashMap<String,String> replyPending;
	String game_key="";
	/////////////// Contained Objects ///////////////
	private HostNetworkService hostNetworkService;
	/////////////// References ///////////////
	Table table;
	
	public HostNetwork() {
		connectServiceBound=false;
		doingAnnounce=false;
		doingAccept=false;
		doingReconnect=false;
		playersConnected=false;
		loadedGame=false;
		replyPending=new HashMap<String,String>();
	}
	/////////////// Lifecycle Events ///////////////
	public void onSaveInstanceState(Bundle outState_) {
	}
	
	public void onRestoreInstanceState(Bundle savedInstanceState) {
	}
	
	public void onStart(Context c) {
		Logger.log(DPCGame.DEBUG_LOG_LIFECYCLE_TAG, "HostNetwork - onStart()");
		Intent hostConnectServiceIntent=new Intent(c,HostNetworkService.class);
		c.bindService(hostConnectServiceIntent,networkServiceConnection,Context.BIND_AUTO_CREATE);
	}
	
	public void onStop(Context c) {
		Logger.log(DPCGame.DEBUG_LOG_LIFECYCLE_TAG, "HostNetwork - onStop()");
		if (connectServiceBound) {
			hostNetworkService.stopAnnounce();
			hostNetworkService.stopAccept();
			hostNetworkService.stopReconnect();
			hostNetworkService.removeAll("");
			c.unbindService(networkServiceConnection);
			connectServiceBound=false;
		}
	}
	
	private ServiceConnection networkServiceConnection =
    		new ServiceConnection() {    	
    	public void onServiceConnected(ComponentName className, IBinder service) {
    		Logger.log(DPCGame.DEBUG_LOG_LIFECYCLE_TAG, "HostNetwork - onServiceConnected()");
    		HostNetworkServiceBinder binder = (HostNetworkServiceBinder)service;
    		hostNetworkService = binder.getService();
    		connectServiceBound=true;
    		hostNetworkService.hostNetwork=HostNetwork.this;
    		if (doingAnnounce) {
    			spawnAnnounce();
    		}
    		if (doingAccept) {
    			spawnAccept();
    		}
    		if (doingReconnect) {
    			spawnReconnect();
    		}
    	}    	
    	public void onServiceDisconnected(ComponentName arg0) {
    		Logger.log(DPCGame.DEBUG_LOG_LIFECYCLE_TAG, "HostNetwork - onServiceDisconnected()");
    		connectServiceBound=false;
    	}
    };
    
    @Override
    public void createTable(String tableName) {
    	this.tableName=tableName;
    	game_key=genGameKey();
    	startReconnect();
    }
    
	@Override
	public void destroyTable() {
		removeAllPlayers();
		game_key="";
		tableName="";
		stopReconnect();
		stopAccept();
		stopAnnounce();
	}
    
	@Override
	public void startLobby(boolean loadedGame,ArrayList<String> playerNames) {
		Logger.log(DPCGame.DEBUG_LOG_LIFECYCLE_TAG, "HostNetwork - startLobby("+loadedGame+")");
		this.loadedGame=loadedGame;
		this.playerNames=playerNames;
		startAnnounce();
		startAccept();
	}
	
	@Override
	public void stopLobby() {
		Logger.log(DPCGame.DEBUG_LOG_LIFECYCLE_TAG, "HostNetwork - stopLobby()");
		loadedGame=false;
		playerNames=null;
		stopAnnounce();
		stopAccept();
	}
	
	/////////////// Host Sends Messages to Player ///////////////
	
	@Override
	public void sendSetupInfo(String hostName,int position,int color,String chipString) {
		String msg=getTimeStamp()+TAG_SETUP_INFO_OPEN;
		msg+=TAG_COLOR_OPEN+color+TAG_COLOR_CLOSE;
		msg+=TAG_SEND_CHIPS_OPEN+chipString+TAG_SEND_CHIPS_CLOSE;
		msg+=TAG_SETUP_INFO_CLOSE;
    	hostNetworkService.sendToPlayer(msg,hostName);
		replyPending.put(hostName,msg);
	}
	
	@Override
	public void promptWaitNextHand(String hostName) {
		String msg=TAG_WAIT_NEXT_HAND;
		hostNetworkService.sendToPlayer(msg,hostName);
	}
	
	@Override
    public void sendChips(String hostName,int position,String chipString) {
    	String msg=getTimeStamp()+TAG_SEND_CHIPS_OPEN;
    	msg+=chipString+TAG_SEND_CHIPS_CLOSE;
    	hostNetworkService.sendToPlayer(msg,hostName);
		replyPending.put(hostName,msg);
    }
	
	@Override
	public void sendDealerChip(String hostName) {
		String msg=getTimeStamp()+TAG_SEND_DEALER;
		hostNetworkService.sendToPlayer(msg,hostName);
	}
	
	@Override
	public void recallDealerChip(String hostName) {
		String msg=getTimeStamp()+TAG_RECALL_DEALER;
		hostNetworkService.sendToPlayer(msg,hostName);
	}
    
	@Override
	public void syncAllTableStatusMenu(ArrayList<Player> players) {
		String msg=TAG_STATUS_MENU_UPDATE_OPEN;
		for (Player player:players) {
			msg=msg+TAG_HOST_NAME_OPEN+player.hostName+TAG_HOST_NAME_CLOSE;
			msg=msg+TAG_PLAYER_NAME_OPEN+player.name.getText()+TAG_PLAYER_NAME_CLOSE;
			msg=msg+TAG_AMOUNT_OPEN+player.chipAmount+TAG_AMOUNT_CLOSE;
		}
		msg=msg+TAG_STATUS_MENU_UPDATE_CLOSE;
		hostNetworkService.sendToAll(msg);
	}

	@Override
	public void sendTextMessage(String hostName_,String message) {
		String msg=TAG_TEXT_MESSAGE_OPEN+message+TAG_TEXT_MESSAGE_CLOSE;
		hostNetworkService.sendToPlayer(msg,hostName_);
	}
	
	@Override
	public void promptMove(String hostName,int position,int stake,boolean foldEnabled,String message,String messageStateChange) {
		String msg=getTimeStamp()+TAG_YOUR_BET_OPEN;
		msg+=TAG_STAKE_OPEN+stake+TAG_STAKE_CLOSE;
		msg+=TAG_FOLD_ENABLED_OPEN+foldEnabled+TAG_FOLD_ENABLED_CLOSE;
		msg+=TAG_MESSAGE_OPEN+message+TAG_MESSAGE_CLOSE;
		msg+=TAG_MESSAGE_STATE_CHANGE_OPEN+messageStateChange+TAG_MESSAGE_STATE_CHANGE_CLOSE;
		msg+=TAG_YOUR_BET_CLOSE;		
		hostNetworkService.sendToPlayer(msg,hostName);
		replyPending.put(hostName,msg);
	}
	
	@Override
	public void cancelMove(String hostName) {
		String msg=getTimeStamp()+TAG_CANCEL_MOVE;
		hostNetworkService.sendToPlayer(msg,hostName);
		replyPending.remove(hostName);
	}
	
	@Override
	public void enableNudge(String dstHostName,String nudgableHostName) {
		String msg=TAG_ENABLE_NUDGE_OPEN+nudgableHostName+TAG_ENABLE_NUDGE_CLOSE;
		hostNetworkService.sendToPlayer(msg,dstHostName);
	}
	
	@Override
	public void disableNudge() {
		String msg=TAG_DISABLE_NUDGE;
		hostNetworkService.sendToAll(msg);
	}
	
	@Override
	public void sendBell(String hostName) {
		String msg=TAG_SEND_BELL;
		hostNetworkService.sendToPlayer(msg,hostName);
	}
	
	@Override
	public void showConnection(String hostName) {
		String msg=TAG_SHOW_CONNECTION;
		hostNetworkService.sendToPlayer(msg,hostName);
	}
	
	@Override
	public void hideConnection(String hostName) {
		String msg=TAG_HIDE_CONNECTION;
		hostNetworkService.sendToPlayer(msg,hostName);
	}
	
	@Override
	public void removePlayer(String hostName) {
		replyPending.remove(hostName);
		playersConnected=hostNetworkService.removePlayer(hostName);
	}

	public void removeAllPlayers() {
		hostNetworkService.removeAll("<GOODBYE/>");
		replyPending.clear();
		playersConnected=false;
	}
	
	/////////////// Host Receives Messages from Player ///////////////
	public void notifyPlayerConnected(final String hostName,final String msg) {
		int startIndex=msg.indexOf(PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN) + PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN.length();
		int endIndex=msg.indexOf(PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE);
		final String playerName=msg.substring(startIndex, endIndex);
		startIndex=msg.indexOf(PlayerNetwork.TAG_AZIMUTH_OPEN) + PlayerNetwork.TAG_AZIMUTH_OPEN.length();
		endIndex=msg.indexOf(PlayerNetwork.TAG_AZIMUTH_CLOSE);
		final int azimuth=Integer.parseInt(msg.substring(startIndex, endIndex));
		final int[] chipNumbers=new int[ChipCase.CHIP_TYPES];
		startIndex=msg.indexOf(PlayerNetwork.TAG_NUM_A_OPEN) + PlayerNetwork.TAG_NUM_A_OPEN.length();
		endIndex=msg.indexOf(PlayerNetwork.TAG_NUM_A_CLOSE);
		chipNumbers[ChipCase.CHIP_A]=Integer.parseInt(msg.substring(startIndex,endIndex));
		startIndex=msg.indexOf(PlayerNetwork.TAG_NUM_B_OPEN) + PlayerNetwork.TAG_NUM_B_OPEN.length();
		endIndex=msg.indexOf(PlayerNetwork.TAG_NUM_B_CLOSE);
		chipNumbers[ChipCase.CHIP_B]=Integer.parseInt(msg.substring(startIndex,endIndex));
		startIndex=msg.indexOf(PlayerNetwork.TAG_NUM_C_OPEN) + PlayerNetwork.TAG_NUM_C_OPEN.length();
		endIndex=msg.indexOf(PlayerNetwork.TAG_NUM_C_CLOSE);
		chipNumbers[ChipCase.CHIP_C]=Integer.parseInt(msg.substring(startIndex,endIndex));
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				playersConnected=true;
				if (table!=null) {
					table.notifyPlayerConnected(hostName,playerName,azimuth,chipNumbers);
				}
			}
		});
	}
	
	public void notifyPlayerReconnected(final String hostName,final String msgStr) {
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				if (replyPending.containsKey(hostName)) {
					resend(hostName);
				}
				table.notifyPlayerReconnected(hostName);
			}
		});
	}

    public void parsePlayerMessage(final String hostName,final String msg) {
    	Logger.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "HostNetwork - ParsePlayerMessage() - "+msg);
		if (msg.contains(PlayerNetwork.TAG_PLAYER_NAME_OPEN)&&msg.contains(PlayerNetwork.TAG_PLAYER_NAME_CLOSE)) {
			int startIndex = msg.indexOf(PlayerNetwork.TAG_PLAYER_NAME_OPEN) + PlayerNetwork.TAG_PLAYER_NAME_OPEN.length();
			int endIndex = msg.indexOf(PlayerNetwork.TAG_PLAYER_NAME_CLOSE);
			final String playerName = msg.substring(startIndex,endIndex);
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					table.setPlayerName(hostName,playerName);
				}
			});
		} else if (msg.contains(PlayerNetwork.TAG_SUBMIT_MOVE_OPEN)&&msg.contains(PlayerNetwork.TAG_SUBMIT_MOVE_CLOSE)) {
    		int startIndex = msg.indexOf(PlayerNetwork.TAG_MOVE_OPEN) + PlayerNetwork.TAG_MOVE_OPEN.length();
			int endIndex = msg.indexOf(PlayerNetwork.TAG_MOVE_CLOSE);
			final int move = Integer.parseInt(msg.substring(startIndex, endIndex));
			startIndex = msg.indexOf(PlayerNetwork.TAG_CHIPS_OPEN) + PlayerNetwork.TAG_CHIPS_OPEN.length();
			endIndex = msg.indexOf(PlayerNetwork.TAG_CHIPS_CLOSE);
			final String chipString = msg.substring(startIndex, endIndex);
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					replyPending.clear();
					table.moveRxd(hostName,move,chipString);
				}
			});
		} else if (msg.contains(PlayerNetwork.TAG_GOODBYE)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					table.exitFromTable(hostName);
				}
			});
		} else if (msg.contains(PlayerNetwork.TAG_SETUP_ACK)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					replyPending.remove(hostName);
					table.setupACKEd(hostName);
				}
			});
		} else if (msg.contains(PlayerNetwork.TAG_CHIPS_ACK)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					replyPending.remove(hostName);
					table.chipsACKed(hostName);
				}
			});			
		} else if (msg.contains(PlayerNetwork.TAG_GOODBYE_ACK)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					;
				}
			});
		} else if (msg.contains(PlayerNetwork.TAG_SEND_BELL_OPEN)&&msg.contains(PlayerNetwork.TAG_SEND_BELL_CLOSE)) {
    		int startIndex = msg.indexOf(PlayerNetwork.TAG_SEND_BELL_OPEN) + PlayerNetwork.TAG_SEND_BELL_OPEN.length();
			int endIndex = msg.indexOf(PlayerNetwork.TAG_SEND_BELL_CLOSE);
			final String destHostName = msg.substring(startIndex, endIndex);			
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					table.bellRxd(destHostName);
				}
			});
		}
    }
    
	/////////////// Private Helper Methods ///////////////
    private static String genGameKey() {
    	String game_key_="";
		for (int i=0;i<10;i++) {
			game_key_+=(int)(Math.random()*9.99);
		}
		return game_key_;
    }
    
    private String getTimeStamp() {
    	Calendar c = Calendar.getInstance();
    	SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    	return sdf.format(c.getTime());
    }
	
	private void resend(String hostName) {
		hostNetworkService.sendToPlayer(TAG_RESEND_OPEN+replyPending.get(hostName)+TAG_RESEND_CLOSE,hostName);
		Logger.log(DPCGame.DEBUG_LOG_NETWORK_TAG, "HostNetwork - resending: "+replyPending.get(hostName));
	}
	
	public boolean requestGamePermission(String rxMsg) {
		boolean permission=false;
		if (!loadedGame) {
			permission=true;
		} else {
			int startIndex=rxMsg.indexOf(PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN) + PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN.length();
			int endIndex=rxMsg.indexOf(PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE);
			final String playerName=rxMsg.substring(startIndex, endIndex);
			if (playerNames!=null) {
				if (playerNames.contains(playerName)) {
					permission=true;
				}
			}
		}
		return permission;
	}
	
	public boolean validatePlayerInfo(String msg) {
		boolean result=false;
		if (msg.contains(PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN)&&msg.contains(PlayerNetwork.TAG_NUM_C_CLOSE)) {
			if (loadedGame) {
				int startIndex=msg.indexOf(PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN) + PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN.length();
				int endIndex=msg.indexOf(PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE);
				String playerName=msg.substring(startIndex, endIndex);
				if (playerNames.contains(playerName)) {
					result=true;
				}
			} else {
				result=true;
			}
		}
		return result;
	}
	
	public boolean validateGameKey(String gameKeyStr) {
		boolean validated=false;
		if (gameKeyStr.contains(HostNetwork.TAG_GAME_KEY_OPEN)&&gameKeyStr.contains(HostNetwork.TAG_GAME_KEY_CLOSE)) {
			int startIndex=gameKeyStr.indexOf(HostNetwork.TAG_GAME_KEY_OPEN)+HostNetwork.TAG_GAME_KEY_OPEN.length();
			int endIndex=gameKeyStr.indexOf(HostNetwork.TAG_GAME_KEY_CLOSE);
			String key=gameKeyStr.substring(startIndex,endIndex);
			if (key.equals(game_key)) {
				validated=true;
			}
		}
		return validated;
	}
	
	private void startAnnounce() {
		doingAnnounce=true;
		if (connectServiceBound) {
			spawnAnnounce();
		}
	}
	
	private void stopAnnounce() {
		hostNetworkService.stopAnnounce();
		doingAnnounce=false;
	}
	
	private void startAccept() {
		doingAccept=true;
		if (connectServiceBound) {
			spawnAccept();
		}
	}
	
	private void stopAccept() {
		hostNetworkService.stopAccept();
		doingAccept=false;
	}
	
	private void startReconnect() {
		doingReconnect=true;
		if (connectServiceBound) {
			spawnReconnect();
		}
	}
	
	private void stopReconnect() {
		hostNetworkService.stopReconnect();
		doingReconnect=false;
	}
	
	public void spawnAnnounce() {
		Logger.log(DPCGame.DEBUG_LOG_LIFECYCLE_TAG, "HostNetwork - spawnAnnounce()");
		String hostAnnounceStr=HostNetwork.TAG_TABLE_NAME_OPEN+tableName+HostNetwork.TAG_TABLE_NAME_CLOSE;
		if (loadedGame) {
			hostAnnounceStr+=HostNetwork.TAG_LOADED_GAME;
		}
		hostAnnounceStr+=HostNetwork.TAG_VAL_A_OPEN+ChipCase.values[ChipCase.CHIP_A]+HostNetwork.TAG_VAL_A_CLOSE;
		hostAnnounceStr+=HostNetwork.TAG_VAL_B_OPEN+ChipCase.values[ChipCase.CHIP_B]+HostNetwork.TAG_VAL_B_CLOSE;
		hostAnnounceStr+=HostNetwork.TAG_VAL_C_OPEN+ChipCase.values[ChipCase.CHIP_C]+HostNetwork.TAG_VAL_C_CLOSE;
		hostNetworkService.startAnnounce(hostAnnounceStr);
	}
	
	public void spawnAccept() {
		Logger.log(DPCGame.DEBUG_LOG_LIFECYCLE_TAG, "HostNetwork - spawnAccept()");
		String tableNameMsg=HostNetwork.TAG_TABLE_NAME_OPEN+tableName+HostNetwork.TAG_TABLE_NAME_CLOSE;
		String gameKeyMsg=HostNetwork.TAG_GAME_KEY_OPEN+game_key+HostNetwork.TAG_GAME_KEY_CLOSE;
		String failedStr=HostNetwork.TAG_CONNECT_UNSUCCESSFUL;
		hostNetworkService.startAccept(tableNameMsg,gameKeyMsg,failedStr,loadedGame);
	}
	
	public void spawnReconnect() {
		Logger.log(DPCGame.DEBUG_LOG_LIFECYCLE_TAG, "HostNetwork - spawnReconnect()");
		String tableNameStr=TAG_RECONNECT_TABLE_NAME_OPEN+tableName+TAG_RECONNECT_TABLE_NAME_CLOSE;
		String ackStr=TAG_RECONNECT_SUCCESSFUL;
		String failedStr=TAG_RECONNECT_FAILED;
		hostNetworkService.startReconnect(tableNameStr,ackStr,failedStr);
		doingReconnect=true;
	}
	
	/////////////// Getters and Setters ///////////////
	public void setWifiEnabled(boolean en_,String ipAddress_) {
    	wifiEnabled=en_;
    	ipAddress=ipAddress_;
    	// TODO shutdown/restart network threads where needed
	}
	@Override
	public boolean getWifiEnabled() {
		return wifiEnabled;
	}
	@Override
	public String getIpAddress() {
		return ipAddress;
	}
	@Override
	public String getTableName() {
		return tableName;
	}
	@Override
	public void setTable(Table table) {
		this.table=table;
	}

}

