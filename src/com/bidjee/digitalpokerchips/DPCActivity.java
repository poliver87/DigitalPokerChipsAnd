package com.bidjee.digitalpokerchips;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.bidjee.digitalpokerchips.c.DPCGame;
import com.bidjee.digitalpokerchips.i.IActivity;
import com.bidjee.digitalpokerchips.i.IDPCSprite;
import com.bidjee.digitalpokerchips.i.IHostNetwork;
import com.bidjee.digitalpokerchips.i.IPlayerNetwork;
import com.bidjee.digitalpokerchips.i.ITableStore;
import com.bidjee.digitalpokerchips.i.ITextFactory;
import com.bidjee.util.Logger;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;

@SuppressLint("NewApi")
public class DPCActivity extends AndroidApplication implements IActivity, ITableStore {
	
	public static final String LOG_TAG = "DPCActivity";
	
	public static final String SAVE_TABLE_NAME_KEY = "SAVE_TABLE_NAME_KEY";
	public static final String SAVE_TABLE_STATE_KEY = "SAVE_TABLE_STATE_KEY";
	public static final String SAVE_GAME_STATE_KEY = "SAVE_GAME_STATE_KEY";
	
	private WifiLock mWifiLock;
	private IntentFilter wifiBroadcastFilter;
	private WifiBroadcastReceiver mWifiBroadcastReceiver;
	
	DPCGame game;
	private PlayerNetwork playerNetwork;
	private HostNetwork hostNetwork;
	private AndroidTextFactory textFactory;
	
	private UiLifecycleHelper facebookUiHelper;
	LoginButton authButton;
	
	HelpView helpWebView;
	
	//////////////////// Life Cycle Events ///////////////////
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Create Facebook UI Helper
		facebookUiHelper = new UiLifecycleHelper(this,callback);
		facebookUiHelper.onCreate(savedInstanceState);
		
		helpWebView=new HelpView(this);
		helpWebView.clearCache(true);
		helpWebView.loadUrl("http://www.kegrunmobile.com/support/in_app_help.html");
		helpWebView.setBackgroundColor(0x00000000);
		
		// Customise the window
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		// Configure the libGDX environment
		AndroidApplicationConfiguration config=new AndroidApplicationConfiguration();
		config.useGL20=true;
		config.hideStatusBar=false;
		config.useAccelerometer=true;
		config.useCompass=true;
		game=new DPCGame(this);
		View gameView = initializeForView(game,config);
		
		setContentView(R.layout.activity_main);
		
		authButton=(LoginButton)findViewById(R.id.authButton);
		
		RelativeLayout mainLayout=(RelativeLayout)findViewById(R.id.mainLayout);
		mainLayout.addView(gameView, 0);
		
		
		wifiBroadcastFilter=new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
		mWifiBroadcastReceiver=new WifiBroadcastReceiver();
		
		textFactory=new AndroidTextFactory(this);
		playerNetwork=new PlayerNetwork();
		hostNetwork=new HostNetwork();
        
		
		mainLayout.addView(helpWebView);
		
	}
	
	@Override
	public void onStart() {
		super.onStart();
		Logger.log(LOG_TAG,"onStart()");
		registerReceiver(mWifiBroadcastReceiver, wifiBroadcastFilter);
		WifiManager wm=(WifiManager)getSystemService(Context.WIFI_SERVICE);
		mWifiLock=wm.createWifiLock(WifiManager.WIFI_MODE_FULL,"DPC_WIFI_LOCK");
		mWifiLock.acquire();
		playerNetwork.onStart(this);
		hostNetwork.onStart(this);
		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		Logger.log(LOG_TAG,"onResume()");
		Session session = Session.getActiveSession();
		if (session!=null && (session.isOpened()||session.isClosed())) {
			onSessionStateChange(session, session.getState(), null);
		}
		facebookUiHelper.onResume();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		Logger.log(LOG_TAG,"onPause()");
		facebookUiHelper.onPause();
	}
	
	@Override
	public void onStop() {
		super.onStop();
		Logger.log(LOG_TAG,"onStop()");
		facebookUiHelper.onStop();
		this.unregisterReceiver(mWifiBroadcastReceiver);
		if (mWifiLock.isHeld()) {
			mWifiLock.release();
		}
		mWifiLock=null;
		playerNetwork.onStop(this);
		hostNetwork.onStop(this);
		
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Logger.log(LOG_TAG,"onSaveInstanceState()");
		facebookUiHelper.onSaveInstanceState(outState);
	}
	
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		Logger.log(LOG_TAG,"onRestoreInstanceState()");
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Logger.log(LOG_TAG,"onDestroy()");
		facebookUiHelper.onDestroy();
		mWifiLock=null;
		mWifiBroadcastReceiver=null;
		game=null;
		playerNetwork=null;
		hostNetwork=null;
		
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		facebookUiHelper.onActivityResult(requestCode, resultCode, data);
	}
	
	//////////////////// Messages from WifiBroadcastReceiver ////////////////////
	public void setWifiEnabled(final boolean en) {
		Logger.log(LOG_TAG,"setWifiEnabled("+en+")");
		final String ipAddress;
		if (en) {
			WifiManager wm=(WifiManager)getSystemService(Context.WIFI_SERVICE);
			int address_=wm.getConnectionInfo().getIpAddress();
			if (address_!=0) {
				ipAddress=Formatter.formatIpAddress(address_);	
			} else {
				ipAddress="";
			}
		} else {
			ipAddress="";
		}
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				if (playerNetwork!=null) {
					playerNetwork.setWifiEnabled(en);
				}
				if (hostNetwork!=null) {
					hostNetwork.setWifiEnabled(en,ipAddress);
				}
				if (game!=null) {
					game.setWifiEnabled(en,ipAddress);
				}
			}
		});
	}    
    
	//////////////////// Messages from DPCGame ////////////////////
    @Override
    public void makeToast(final String msg_) {
    	final AndroidApplication activity_=this;
    	this.runOnUiThread(new Runnable() {
    		public void run() {
    			Toast toast=Toast.makeText(activity_,msg_,Toast.LENGTH_SHORT);
            	toast.setGravity(Gravity.CENTER_VERTICAL,0,0);
            	toast.show();
    		}
    	});
    }
	
    @Override
	public void launchSettings() {
    	Logger.log(LOG_TAG,"launchSettings()");
    	Intent intent_=new Intent(Settings.ACTION_WIFI_SETTINGS);
    	intent_.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		startActivity(intent_);
	}

	
	
	/////////////// Table Store Methods ///////////////
    @Override
	public void saveGame(final int saveSlot,final String tableName,String tableState,String gameState) {
    	Logger.log(LOG_TAG,"saveGame()");
		Editor savedGameEditor=getSharedPreferences("SAVED_GAME_"+saveSlot,Activity.MODE_PRIVATE).edit();
		savedGameEditor.putString(SAVE_TABLE_NAME_KEY,tableName);
		savedGameEditor.putString(SAVE_TABLE_STATE_KEY,tableState);
		savedGameEditor.putString(SAVE_GAME_STATE_KEY,gameState);
		savedGameEditor.commit();
    	final AndroidApplication activity=this;
    	this.runOnUiThread(new Runnable() {
    		public void run() {
    			Toast toast=Toast.makeText(activity,tableName+" AutoSaved to slot "+saveSlot,Toast.LENGTH_SHORT);
            	toast.setGravity(Gravity.BOTTOM,0,0);
            	toast.show();
    		}
    	});
	}
	
	@Override
	public String[] getTableNames(int numSlots) {
		Logger.log(LOG_TAG,"getTableNames()");
		String[] names=new String[numSlots];
		for (int i=0;i<numSlots;i++) {
			int slot=i+1;
			SharedPreferences savedGame=getSharedPreferences("SAVED_GAME_"+slot,Activity.MODE_PRIVATE);
			if (savedGame.contains(SAVE_TABLE_NAME_KEY)) {
				names[i]=savedGame.getString(SAVE_TABLE_NAME_KEY,"");
			} else {
				names[i]=null;
			}
		}
		return names;
	}
	
	@Override
	public String getTableName(int slot) {
		String tableName="";
		SharedPreferences savedGame=getSharedPreferences("SAVED_GAME_"+slot,Activity.MODE_PRIVATE);
		if (savedGame.contains(SAVE_TABLE_NAME_KEY)) {
			tableName=savedGame.getString(SAVE_TABLE_NAME_KEY,"");
			Logger.log(LOG_TAG,"getTableName("+slot+")");
		} else {
			Logger.log(LOG_TAG,"getTableName("+slot+") not found");
		}
		return tableName;
	}
	
	@Override
	public String getTableState(int slot) {
		String tableState="";
		SharedPreferences savedGame=getSharedPreferences("SAVED_GAME_"+slot,Activity.MODE_PRIVATE);
		if (savedGame.contains(SAVE_TABLE_STATE_KEY)) {
			tableState=savedGame.getString(SAVE_TABLE_STATE_KEY,"");
			Logger.log(LOG_TAG,"getTableState("+slot+")");
		} else {
			Logger.log(LOG_TAG,"getTableState("+slot+") not found");
		}
		return tableState;
	}
	
	@Override
	public String getGameState(int slot) {
		String gameState="";
		SharedPreferences savedGame=getSharedPreferences("SAVED_GAME_"+slot,Activity.MODE_PRIVATE);
		if (savedGame.contains(SAVE_GAME_STATE_KEY)) {
			gameState=savedGame.getString(SAVE_GAME_STATE_KEY,"");
			Logger.log(LOG_TAG,"getGameState("+slot+")");
		} else {
			Logger.log(LOG_TAG,"getGameState("+slot+") not found");
		}
		return gameState;
	}
	
	private void clearAllTables(int numSlots) {
		for (int i=0;i<numSlots;i++) {
			int slot=i+1;
			SharedPreferences savedGame=getSharedPreferences("SAVED_GAME_"+slot,Activity.MODE_PRIVATE);
			if (savedGame.contains(SAVE_TABLE_NAME_KEY)) {
				Editor e=savedGame.edit();
				e.clear();
				e.commit();
			}
		}
	}
    
    
	/////////////// Getters and Setters ///////////////

	@Override
	public IPlayerNetwork getIPlayerNetwork() {
		return playerNetwork;
	}

	@Override
	public IHostNetwork getIHostNetwork() {
		return hostNetwork;
	}
	
	@Override
	public ITableStore getITableStore() {
		return this;
	}
	
	@Override
	public IDPCSprite getHelpWebView() {
		return helpWebView;
	}

	@Override
	public int getScreenOrientation() {
		Display display_=((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		int rotation_=display_.getRotation();
		int screenOrientation_=0;
		if (rotation_==Surface.ROTATION_0) {
			screenOrientation_=0;
		} else if (rotation_==Surface.ROTATION_90) {
			screenOrientation_=90;
		} else if (rotation_==Surface.ROTATION_180) {
			screenOrientation_=180;
		} else if (rotation_==Surface.ROTATION_270) {
			screenOrientation_=270;
		}
		return screenOrientation_;
	}
	
	@Override
	public ITextFactory getITextFactory() {
		return textFactory;
	}
	
	@Override
	public void brightenScreen() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				WindowManager.LayoutParams layout = getWindow().getAttributes();
				layout.screenBrightness = -1;
				getWindow().setAttributes(layout);
			}
		});
		
	}
	
	@Override
	public void dimScreen() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				WindowManager.LayoutParams layout = getWindow().getAttributes();
				layout.screenBrightness = 0.1f;
				getWindow().setAttributes(layout);
			}
		});
	}
	
	//// Facebook stuff ////
	@Override
	public void performFacebookClick() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				authButton.performClick();
			}
		});
		
	}
	
	private Session.StatusCallback callback = new Session.StatusCallback() {
		@Override
		public void call(Session session, SessionState state, Exception exception) {
			onSessionStateChange(session,state,exception);
		}
	};
	
	private void onSessionStateChange(final Session session, SessionState state, Exception exception) {
		if (state.isOpened()) {
			Logger.log(LOG_TAG,"Facebook logged in");
			Request request = Request.newMeRequest(session, 
		            new Request.GraphUserCallback() {
		        @Override
		        public void onCompleted(final GraphUser user, Response response) {
		            // If the response is successful
		            if (session == Session.getActiveSession()) {
		                if (user != null) {
		                	UpdateFacebookInfoTask updateTask=new UpdateFacebookInfoTask();
		                	updateTask.execute(user);
		                }
		            }
		            if (response.getError() != null) {
		                // Handle errors, will do so later.
		            }
		        }
		    });
		    request.executeAsync();
			
		} else if (state.isClosed()) {
			Logger.log(LOG_TAG,"Facebook logged out");
		}
	}
	
	class UpdateFacebookInfoTask extends AsyncTask<GraphUser,Void,Void> {
		
		@Override
		protected Void doInBackground(final GraphUser... users) {
			URL imageURL=null;
        	Bitmap bitmap=null;
			try {
				imageURL = new URL("https://graph.facebook.com/" + users[0].getId() + "/picture?width=300&height=300");
				bitmap = BitmapFactory.decodeStream(imageURL.openConnection().getInputStream());
				
				
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			final Bitmap bitmapFinal=bitmap;
			Gdx.app.postRunnable(new Runnable() {
				@Override
				public void run() {
					// TODO manage the texture memory
					Texture tex = new Texture(bitmapFinal.getWidth(),bitmapFinal.getHeight(), Format.RGBA8888);
					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,tex.getTextureObjectHandle());
					GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmapFinal,0);
					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
					bitmapFinal.recycle();
					game.mWL.thisPlayer.playerLoginDone(users[0].getFirstName(), tex);
				}
			});
			return null;
		}
	}
	
}
