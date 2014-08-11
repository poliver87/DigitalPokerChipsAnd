package com.bidjee.digitalpokerchips;

import android.os.Bundle;

import com.bidjee.digitalpokerchips.i.IPersistScreen;
import com.bidjee.digitalpokerchips.m.Chip;
import com.bidjee.digitalpokerchips.m.ChipStack;

public class AndroidPersistScreen implements IPersistScreen {

	// prototype
	
	Bundle screenBundle;
	
	public AndroidPersistScreen() {
	}
	
	public void putInt(String tag_,int i_) {
		screenBundle.putInt(tag_, i_);
	}
	
	public int getInt(String tag_) {
		return screenBundle.getInt(tag_);
	}

	@Override
	public void putBoolean(String tag, boolean i) {
		screenBundle.putBoolean(tag, i);
	}

	@Override
	public void putString(String tag, String i) {
		screenBundle.putString(tag, i);
	}

	@Override
	public void putFloat(String tag, float i) {
		screenBundle.putFloat(tag, i);
	}
	
	@Override
	public void putByteArray(String tag, byte[] i) {
		screenBundle.putByteArray(tag, i);
	}

	@Override
	public boolean getBoolean(String tag) {
		return screenBundle.getBoolean(tag);
	}

	@Override
	public String getString(String tag) {
		return screenBundle.getString(tag);
	}

	@Override
	public float getFloat(String tag) {
		return screenBundle.getFloat(tag);
	}
	
	@Override
	public byte[] getByteArray(String tag) {
		return screenBundle.getByteArray(tag);
	}
	
	@Override
	public void putChip(String tag,Chip i) {
		if (i==null) {
			screenBundle.putBoolean(tag+"isNull",true);
		} else {
			screenBundle.putBoolean(tag+"isNull",false);
			screenBundle.putInt(tag+"chipType",i.chipType);
			screenBundle.putFloat(tag+"x",i.x);
			screenBundle.putFloat(tag+"y",i.y);
			//screenBundle.putFloat(tag+"destX",i.destX);
			//screenBundle.putFloat(tag+"destY",i.destY);
		}
	}
	
	@Override
	public Chip getChip(String tag) {
		Chip c=null;
		if (!screenBundle.getBoolean(tag+"isNull")) {
			int chipType_=screenBundle.getInt(tag+"chipType");
			float x_=screenBundle.getFloat(tag+"x");
			float y_=screenBundle.getFloat(tag+"y");
			//float destX_=screenBundle.getFloat(tag+"destX");
			//float destY_=screenBundle.getFloat(tag+"destY");
			boolean isAtDest_=screenBundle.getBoolean(tag+"isAtDest");
			c=new Chip(chipType_,x_,y_,0);
			//c.destX=destX_;
			//c.destY=destY_;
		}
		return c;
	}
	
	@Override
	public void putStack(String tag,ChipStack i) {
		if (i==null) {
			screenBundle.putBoolean(tag+"isNull", true);
		} else {
			screenBundle.putBoolean(tag+"isNull", false);
			screenBundle.putFloat(tag+"x",i.getX());
			screenBundle.putFloat(tag+"y",i.getY());
			screenBundle.putInt(tag+"size",i.size());
			for (int chip=0;chip<i.size();chip++) {
				putChip(tag+chip,i.get(chip));
			}
		}
	}
	
	@Override
	public ChipStack updateStack(ChipStack s,String tag) {
		if (s==null) {
			s=new ChipStack();
		}
		if (!screenBundle.getBoolean(tag+"isNull")) {
			s.setX(screenBundle.getFloat(tag+"x"));
			s.setY(screenBundle.getFloat(tag+"y"));
			int size=screenBundle.getInt(tag+"size");
			for (int chip=0;chip<size;chip++) {
				s.add(getChip(tag+chip));
			}
		}
		return s;
	}
	
}
