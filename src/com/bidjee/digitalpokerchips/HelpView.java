package com.bidjee.digitalpokerchips;

import android.content.Context;
import android.view.View;
import android.webkit.WebView;
import android.widget.RelativeLayout;

import com.badlogic.gdx.Gdx;
import com.bidjee.digitalpokerchips.i.IDPCSprite;

public class HelpView extends WebView implements IDPCSprite {
	
	float x;
	float y;
	int radiusX;
	int radiusY;
	
	public HelpView(Context context) {
		super(context);
		setOpacity(0);
	}

	@Override
	public void setDimensions(int radiusX, int radiusY) {
		this.radiusX=radiusX;
		this.radiusY=radiusY;
		updateMargins();
		
	}

	@Override
	public void setPosition(float x, float y) {
		this.x=x;
		this.y=y;
		updateMargins();
	}

	@Override
	public void setOpacity(final float o) {
		post(new Runnable() {
			
			@Override
			public void run() {
				if (o==0) {
					setVisibility(View.GONE);
				} else {
					setVisibility(View.VISIBLE);
				}
			}
		});
		
	}
	
	private void updateMargins() {
		post(new Runnable() {
			
			@Override
			public void run() {
				int screenWidth=Gdx.graphics.getWidth();
				int screenHeight=Gdx.graphics.getHeight();
				RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
						RelativeLayout.LayoutParams.MATCH_PARENT);
				params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
				
				params.leftMargin=(int) (x-radiusX);
				params.rightMargin=(int) (screenWidth-(x+radiusX));
				params.bottomMargin=(int) (y-radiusY);
				params.topMargin=(int) (screenHeight-(y+radiusY));
				
				setLayoutParams(params);
			}
		});
		
	}

}
