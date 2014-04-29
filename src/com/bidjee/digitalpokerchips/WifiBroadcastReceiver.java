package com.bidjee.digitalpokerchips;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

public class WifiBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String action=intent.getAction();
		if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
			NetworkInfo info=(NetworkInfo)intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
			if (info.isConnected()&&info.isAvailable()&&info.getType()==ConnectivityManager.TYPE_WIFI) {
				((DPCActivity)context).setWifiEnabled(true);
			} else {
				((DPCActivity)context).setWifiEnabled(false);
			}
		}
	}

}
