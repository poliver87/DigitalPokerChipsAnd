package com.bidjee.digitalpokerchips;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;

@ReportsCrashes(formKey = "", // will not be used
mailTo = "peter.oliver@live.com.au",
mode = ReportingInteractionMode.TOAST,
resToastText = R.string.crash_toast_text)
public class myApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		ACRA.init(this);
	}
}
