package com.bidjee.digitalpokerchips;

import java.util.ArrayList;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import com.badlogic.gdx.Gdx;
import com.bidjee.digitalpokerchips.HostNetworkService.HostNetworkServiceBinder;
import com.bidjee.digitalpokerchips.c.Table;
import com.bidjee.digitalpokerchips.i.IHostNetwork;
import com.bidjee.digitalpokerchips.m.ChipCase;
import com.bidjee.digitalpokerchips.m.MovePrompt;
import com.bidjee.digitalpokerchips.m.Player;
import com.bidjee.util.Logger;

public class HostNetwork implements IHostNetwork {
	
	public static final String LOG_TAG = "DPCHostNetwork";
	
	/////////////// Network Protocol Tags ///////////////
	public static final String TAG_GAME_ACK="<TAG_GAME_ACK/>";
	public static final String TAG_SEND_DEALER = "<DPC_DEALER/>";
	public static final String TAG_RECALL_DEALER = "<DPC_RECALL_DEALER/>";
	public static final String TAG_SYNC_CHIPS_OPEN = "<TAG_SYNC_CHIPS>";
	public static final String TAG_SYNC_CHIPS_CLOSE = "<TAG_SYNC_CHIPS/>";
	public static final String TAG_SYNC_CHIPS_WITH_MOVE_OPEN = "<TAG_SYNC_CHIPS_WITH_MOVE>";
	public static final String TAG_SYNC_CHIPS_WITH_MOVE_CLOSE = "<TAG_SYNC_CHIPS_WITH_MOVE/>";
	public static final String TAG_STATUS_MENU_UPDATE_OPEN = "<STATUS_MENU_UPDATE>";
	public static final String TAG_STATUS_MENU_UPDATE_CLOSE = "<STATUS_MENU_UPDATE/>";
	public static final String TAG_PLAYER_NAME_OPEN = "<PLAYER_NAME>";
	public static final String TAG_PLAYER_NAME_CLOSE = "<PLAYER_NAME/>";
	public static final String TAG_AMOUNT_OPEN = "<AMOUNT>";
	public static final String TAG_AMOUNT_CLOSE = "<AMOUNT/>";
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
	public static final String TAG_CONNECT_UNSUCCESSFUL = "<DPC_CONNECTION_UNSUCCESSFUL/>";
	public static final String TAG_SEND_BELL = "<SENDING_BELL/>";
	public static final String TAG_ENABLE_NUDGE_OPEN = "<ENABLE_NUDGE>";
	public static final String TAG_ENABLE_NUDGE_CLOSE = "<ENABLE_NUDGE/>";
	public static final String TAG_DISABLE_NUDGE = "<DISABLE_NUDGE/>";
	public static final String TAG_ARRANGE = "<ARRANGE/>";
	public static final String TAG_SELECT_DEALER = "<SELECT_DEALER/>";
	public static final String TAG_CANCEL_MOVE = "<TAG_CANCEL_MOVE/>";
	public static final String TAG_WAIT_NEXT_HAND = "<TAG_WAIT_NEXT_HAND/>";
	public static final String TAG_CONNECT_NOW = "<TAG_CONNECT_NOW/>";
	
	/////////////// State Variables ///////////////
	boolean wifiEnabled;
	String ipAddress;
	private boolean connectServiceBound;
	boolean doingAnnounce;
	boolean doingAccept;
	boolean playersConnected;
	String tableName;
	/////////////// Contained Objects ///////////////
	private HostNetworkService hostNetworkService;
	/////////////// References ///////////////
	Table table;
	
	public HostNetwork() {
		connectServiceBound=false;
		doingAnnounce=false;
		doingAccept=false;
		playersConnected=false;
	}
	/////////////// Lifecycle Events ///////////////
	public void onSaveInstanceState(Bundle outState_) {
	}
	
	public void onRestoreInstanceState(Bundle savedInstanceState) {
	}
	
	public void onStart(Context c) {
		Intent hostConnectServiceIntent=new Intent(c,HostNetworkService.class);
		c.bindService(hostConnectServiceIntent,networkServiceConnection,Context.BIND_AUTO_CREATE);
	}
	
	public void onStop(Context c) {
		if (connectServiceBound) {
			hostNetworkService.stopAnnounce();
			hostNetworkService.stopAccept();
			hostNetworkService.removeAll("");
			c.unbindService(networkServiceConnection);
			connectServiceBound=false;
		}
	}
	
	private ServiceConnection networkServiceConnection =
    		new ServiceConnection() {    	
    	public void onServiceConnected(ComponentName className, IBinder service) {
    		Logger.log(LOG_TAG,"onServiceConnected()");
    		HostNetworkServiceBinder binder = (HostNetworkServiceBinder)service;
    		hostNetworkService = binder.getService();
    		connectServiceBound=true;
    		hostNetworkService.hostNetwork=HostNetwork.this;
    		if (doingAnnounce&&wifiEnabled) {
    			spawnAnnounce();
    		}
    		if (doingAccept&&wifiEnabled) {
    			spawnAccept();
    		}
    	}    	
    	public void onServiceDisconnected(ComponentName arg0) {
    		Logger.log(LOG_TAG,"onServiceDisconnected()");
    		connectServiceBound=false;
    	}
    };
    
    @Override
    public void createTable(String tableName) {
    	Logger.log(LOG_TAG,"createTable("+tableName+")");
    	this.tableName=tableName;
    	startAccept();
    	startAnnounce();
    }
    
	@Override
	public void destroyTable() {
		Logger.log(LOG_TAG,"destroyTable()");
		removeAllPlayers();
		tableName="";
		stopAccept();
		stopAnnounce();
	}
	
	/////////////// Host Sends Messages to Player ///////////////
	
	@Override
	public void setColor(String playerName,int color) {
		Logger.log(LOG_TAG,"sendSetupInfo("+playerName+")");
		String msg=TAG_COLOR_OPEN+color+TAG_COLOR_CLOSE;
    	hostNetworkService.sendToPlayer(msg,playerName);
	}
	
	@Override
	public void promptWaitNextHand(String playerName) {
		Logger.log(LOG_TAG,"promptWaitNextHand("+playerName+")");
		String msg=TAG_WAIT_NEXT_HAND;
		hostNetworkService.sendToPlayer(msg,playerName);
	}
	
	@Override
    public void sendChips(String playerName,String chipString) {
		Logger.log(LOG_TAG,"sendChips("+playerName+")");
    	String msg=TAG_SEND_CHIPS_OPEN;
    	msg+=chipString+TAG_SEND_CHIPS_CLOSE;
    	hostNetworkService.sendToPlayer(msg,playerName);
    }
	
	@Override
	public void sendDealerChip(String playerName) {
		Logger.log(LOG_TAG,"sendDealerChip("+playerName+")");
		String msg=TAG_SEND_DEALER;
		hostNetworkService.sendToPlayer(msg,playerName);
	}
	
	@Override
	public void recallDealerChip(String playerName) {
		Logger.log(LOG_TAG,"recallDealerChip("+playerName+")");
		String msg=TAG_RECALL_DEALER;
		hostNetworkService.sendToPlayer(msg,playerName);
	}
    
	@Override
	public void syncAllTableStatusMenu(ArrayList<Player> players) {
		// TODO break this up at the Table level - pending players will get this message
		Logger.log(LOG_TAG,"syncAllTableStatusMenu()");
		String msg=TAG_STATUS_MENU_UPDATE_OPEN;
		for (Player player:players) {
			msg=msg+TAG_PLAYER_NAME_OPEN+player.name.getText()+TAG_PLAYER_NAME_CLOSE;
			msg=msg+TAG_AMOUNT_OPEN+player.chipAmount+TAG_AMOUNT_CLOSE;
		}
		msg=msg+TAG_STATUS_MENU_UPDATE_CLOSE;
		hostNetworkService.sendToAll(msg);
	}

	@Override
	public void sendTextMessage(String playerName,String message) {
		Logger.log(LOG_TAG,"syncAllTableStatusMenu("+playerName+","+message+")");
		String msg=TAG_TEXT_MESSAGE_OPEN+message+TAG_TEXT_MESSAGE_CLOSE;
		hostNetworkService.sendToPlayer(msg,playerName);
	}
	
	@Override
	public void promptMove(String playerName,MovePrompt movePrompt,int chipAmount) {
		Logger.log(LOG_TAG,"promptMove("+playerName+","+movePrompt.message+")");
		String msg=TAG_YOUR_BET_OPEN;
		msg+=TAG_STAKE_OPEN+movePrompt.stake+TAG_STAKE_CLOSE;
		msg+=TAG_FOLD_ENABLED_OPEN+movePrompt.foldEnabled+TAG_FOLD_ENABLED_CLOSE;
		msg+=TAG_MESSAGE_OPEN+movePrompt.message+TAG_MESSAGE_CLOSE;
		msg+=TAG_MESSAGE_STATE_CHANGE_OPEN+movePrompt.messageStateChange+TAG_MESSAGE_STATE_CHANGE_CLOSE;
		msg+=TAG_SYNC_CHIPS_WITH_MOVE_OPEN+chipAmount+TAG_SYNC_CHIPS_WITH_MOVE_CLOSE;
		msg+=TAG_YOUR_BET_CLOSE;
		hostNetworkService.sendToPlayer(msg,playerName);
	}
	
	@Override
	public void cancelMove(String playerName) {
		Logger.log(LOG_TAG,"cancelMove("+playerName+")");
		String msg=TAG_CANCEL_MOVE;
		hostNetworkService.sendToPlayer(msg,playerName);
	}
	
	@Override
	public void syncPlayersChips(String playerName,int chipAmount) {
		Logger.log(LOG_TAG,"syncPlayersChips("+playerName+","+chipAmount+")");
    	String msg=TAG_SYNC_CHIPS_OPEN+chipAmount+TAG_SYNC_CHIPS_CLOSE;
    	hostNetworkService.sendToPlayer(msg,playerName);
	}
	
	@Override
	public void enableNudge(String dstPlayerName,String nudgablePlayerName) {
		Logger.log(LOG_TAG,"enableNudge("+dstPlayerName+","+nudgablePlayerName+")");
		String msg=TAG_ENABLE_NUDGE_OPEN+nudgablePlayerName+TAG_ENABLE_NUDGE_CLOSE;
		hostNetworkService.sendToPlayer(msg,dstPlayerName);
	}
	
	@Override
	public void disableNudge() {
		Logger.log(LOG_TAG,"disableNudge()");
		String msg=TAG_DISABLE_NUDGE;
		hostNetworkService.sendToAll(msg);
		// TODO split this up at Table level
	}
	
	@Override
	public void sendBell(String playerName) {
		//Logger.log(LOG_TAG,"sendBell()");
		String msg=TAG_SEND_BELL;
		hostNetworkService.sendToPlayer(msg,playerName);
	}
	
	@Override
	public void arrange() {
		Logger.log(LOG_TAG,"arrange()");
		String msg=TAG_ARRANGE;
		hostNetworkService.sendToAll(msg);
	}
	
	@Override
	public void selectDealer() {
		Logger.log(LOG_TAG,"selectDealer()");
		String msg=TAG_SELECT_DEALER;
		hostNetworkService.sendToAll(msg);
	}
	
	@Override
	public void removePlayer(String playerName) {
		Logger.log(LOG_TAG,"removePlayer("+playerName+")");
		playersConnected=hostNetworkService.removePlayer(playerName);
	}

	private void removeAllPlayers() {
		Logger.log(LOG_TAG,"removeAllPlayers()");
		hostNetworkService.removeAll("<GOODBYE/>");
		playersConnected=false;
	}
	
	/////////////// Host Receives Messages from Player ///////////////
	public void notifyPlayerConnected(final String playerName,final String msg) {
		Logger.log(LOG_TAG,"notifyPlayerConnected("+playerName+")");
		final int azimuth=unwrapInt(msg,PlayerNetwork.TAG_AZIMUTH_OPEN,PlayerNetwork.TAG_AZIMUTH_CLOSE);
		int[] chipNumbers=null;
		if (msg.contains(PlayerNetwork.TAG_NUM_A_OPEN)) {
			chipNumbers=new int[ChipCase.CHIP_TYPES];
			chipNumbers[ChipCase.CHIP_A]=unwrapInt(msg, PlayerNetwork.TAG_NUM_A_OPEN, PlayerNetwork.TAG_NUM_A_CLOSE);
			chipNumbers[ChipCase.CHIP_B]=unwrapInt(msg, PlayerNetwork.TAG_NUM_B_OPEN, PlayerNetwork.TAG_NUM_B_CLOSE);
			chipNumbers[ChipCase.CHIP_C]=unwrapInt(msg, PlayerNetwork.TAG_NUM_C_OPEN, PlayerNetwork.TAG_NUM_C_CLOSE);
		}
		final int[] chipNumbersFinal=chipNumbers;
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				playersConnected=true;
				if (table!=null) {
					table.notifyPlayerConnected(playerName,azimuth,chipNumbersFinal);
				}
			}
		});
	}

	@Override
    public void parsePlayerMessage(final String playerName,final String msg) {
    	Logger.log(LOG_TAG,"parsePlayerMessage("+playerName+","+msg+")");
		if (msg.contains(PlayerNetwork.TAG_SUBMIT_MOVE_OPEN)&&msg.contains(PlayerNetwork.TAG_SUBMIT_MOVE_CLOSE)) {
    		int startIndex = msg.indexOf(PlayerNetwork.TAG_MOVE_OPEN) + PlayerNetwork.TAG_MOVE_OPEN.length();
			int endIndex = msg.indexOf(PlayerNetwork.TAG_MOVE_CLOSE);
			final int move = Integer.parseInt(msg.substring(startIndex, endIndex));
			startIndex = msg.indexOf(PlayerNetwork.TAG_CHIPS_OPEN) + PlayerNetwork.TAG_CHIPS_OPEN.length();
			endIndex = msg.indexOf(PlayerNetwork.TAG_CHIPS_CLOSE);
			final String chipString = msg.substring(startIndex, endIndex);
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					table.moveRxd(playerName,move,chipString);
				}
			});
		} else if (msg.contains(PlayerNetwork.TAG_GOODBYE)) {
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					table.notifyPlayerLeft(playerName);
				}
			});		
		} else if (msg.contains(PlayerNetwork.TAG_SEND_BELL_OPEN)&&msg.contains(PlayerNetwork.TAG_SEND_BELL_CLOSE)) {
    		int startIndex = msg.indexOf(PlayerNetwork.TAG_SEND_BELL_OPEN) + PlayerNetwork.TAG_SEND_BELL_OPEN.length();
			int endIndex = msg.indexOf(PlayerNetwork.TAG_SEND_BELL_CLOSE);
			final String destPlayerName = msg.substring(startIndex, endIndex);			
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					table.bellRxd(destPlayerName);
				}
			});
		}
    }
    
	/////////////// Private Helper Methods ///////////////    
	
	public boolean checkPlayerConnected(String rxMsg) {
		int startIndex=rxMsg.indexOf(PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN) + PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN.length();
		int endIndex=rxMsg.indexOf(PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE);
		final String playerName=rxMsg.substring(startIndex, endIndex);
		boolean connected=table.checkPlayerConnected(playerName);
		Logger.log(LOG_TAG,"checkPlayerConnected() = "+connected);
		return connected;
	}
	
	public boolean validatePlayerInfo(String msg) {
		boolean result=false;
		if (msg.contains(PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN)&&msg.contains(PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE)) {
			result=true;
		}
		return result;
	}
	
	private void startAnnounce() {
		Logger.log(LOG_TAG,"startAnnounce()");
		if (connectServiceBound&&!doingAnnounce&&wifiEnabled) {
			spawnAnnounce();
		}
		doingAnnounce=true;
	}
	
	private void stopAnnounce() {
		Logger.log(LOG_TAG,"stopAnnounce()");
		hostNetworkService.stopAnnounce();
		doingAnnounce=false;
	}
	
	private void startAccept() {
		Logger.log(LOG_TAG,"stopAnnounce()");
		if (connectServiceBound&&!doingAccept&&wifiEnabled) {
			spawnAccept();
		}
		doingAccept=true;
	}
	
	private void stopAccept() {
		Logger.log(LOG_TAG,"stopAccept()");
		hostNetworkService.stopAccept();
		doingAccept=false;
	}
	
	public void spawnAnnounce() {
		Logger.log(LOG_TAG,"spawnAnnounce()");
		String hostAnnounceStr=HostNetwork.TAG_TABLE_NAME_OPEN+tableName+HostNetwork.TAG_TABLE_NAME_CLOSE;
		hostAnnounceStr+=HostNetwork.TAG_VAL_A_OPEN+ChipCase.values[ChipCase.CHIP_A]+HostNetwork.TAG_VAL_A_CLOSE;
		hostAnnounceStr+=HostNetwork.TAG_VAL_B_OPEN+ChipCase.values[ChipCase.CHIP_B]+HostNetwork.TAG_VAL_B_CLOSE;
		hostAnnounceStr+=HostNetwork.TAG_VAL_C_OPEN+ChipCase.values[ChipCase.CHIP_C]+HostNetwork.TAG_VAL_C_CLOSE;
		String connectNowStr=HostNetwork.TAG_CONNECT_NOW;
		hostNetworkService.startAnnounce(hostAnnounceStr,connectNowStr);
	}
	
	public void spawnAccept() {
		Logger.log(LOG_TAG,"spawnAccept()");
		String tableNameMsg=HostNetwork.TAG_TABLE_NAME_OPEN+tableName+HostNetwork.TAG_TABLE_NAME_CLOSE;
		String ackMsg=HostNetwork.TAG_GAME_ACK;
		String failedStr=HostNetwork.TAG_CONNECT_UNSUCCESSFUL;
		hostNetworkService.startAccept(tableNameMsg,ackMsg,failedStr);
	}
	
	/////////////// Getters and Setters ///////////////
	public void setWifiEnabled(boolean en,String ipAddress_) {
    	wifiEnabled=en;
    	ipAddress=ipAddress_;
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
	
	public static String unwrapString(String text,String open,String close) {
		String unwrappedStr="";
		int startIndex=text.indexOf(open) + open.length();
		int endIndex=text.indexOf(close);
		if (startIndex>=0&&endIndex>startIndex) {
			unwrappedStr=text.substring(startIndex,endIndex);
		}
		return unwrappedStr;
	}
	
	public static int unwrapInt(String text,String open,String close) {
		String unwrappedStr=unwrapString(text,open,close);
		return Integer.parseInt(unwrappedStr);
	}
	
	public static String unwrapPlayerName(String text) {
		return unwrapString(text,PlayerNetwork.TAG_PLAYER_NAME_NEG_OPEN,PlayerNetwork.TAG_PLAYER_NAME_NEG_CLOSE);
	}

}

