package com.bidjee.digitalpokerchips;

import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.bidjee.digitalpokerchips.i.ITextFactory;
import com.bidjee.digitalpokerchips.m.TextLabel;

public class AndroidTextFactory implements ITextFactory {
	
	private static Paint textPaint;
	private static DPCActivity activity;
	
	private ArrayList<TextLabel> textureCache=new ArrayList<TextLabel>();
	
	public AndroidTextFactory(DPCActivity activity_) {
		textPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
		activity=activity_;
	}
	
	@Override
	public int getMaxTextSize(TextLabel label) {
		int textSize=1;
		textPaint.setTextSize(textSize);
		if (label.fontFace!=null) {
			Typeface fontFace_=Typeface.createFromAsset(activity.getApplicationContext().getAssets(),"fonts/"+label.fontFace);
			textPaint.setTypeface(fontFace_);
		}
		if (label.bold) {
			textPaint.setFakeBoldText(true);
		}
		if (label.outline||label.strokeWidth>0) {
			textPaint.setStyle(Paint.Style.STROKE);
			if (label.strokeWidth>0) {
				textPaint.setStrokeWidth(textSize*label.strokeWidth);
			} else {
				textPaint.setStrokeWidth(textSize*0.1f);
			}
		} else {
			textPaint.setStyle(Paint.Style.FILL);
		}
		boolean sizingDone=false;
		while (!sizingDone) {
			textSize++;
			textPaint.setTextSize(textSize);
			if (label.strokeWidth>0) {
				textPaint.setStrokeWidth(textSize*label.strokeWidth);
			} else if (label.outline){
				textPaint.setStrokeWidth(textSize*0.1f);
			}
			if (!label.renderVertical) {
				if (Math.abs(textPaint.ascent())+Math.abs(textPaint.descent())>label.maxRadiusY*2) {
					sizingDone=true;
				}
				if (textPaint.measureText(label.getText())+textPaint.getStrokeWidth()>label.maxRadiusX*2) {
					sizingDone=true;
				}
			} else {
				if (Math.abs(textPaint.ascent())+Math.abs(textPaint.descent())*label.getText().length()>label.maxRadiusY*2) {
					sizingDone=true;
				}
				for (int letterIndex=0;letterIndex<label.getText().length();letterIndex++) {
					if (textPaint.measureText(label.getText().substring(letterIndex,letterIndex)+1)+textPaint.getStrokeWidth()>label.maxRadiusX*2) {
						sizingDone=true;
					}
				}
			}
		}
		textSize--;
		return textSize;
	}
	
	public static Bitmap labelToBitmap(TextLabel label,Color color,Color outlineColor,boolean powerTwo) {
		// set the appropriate size and style
		if (label.fontFace!=null) {
			Typeface fontFace_=Typeface.createFromAsset(activity.getApplicationContext().getAssets(),"fonts/"+label.fontFace);
			textPaint.setTypeface(fontFace_);
		} else {
			textPaint.setTypeface(Typeface.DEFAULT);
		}
		if (label.bold) {
			textPaint.setFakeBoldText(true);
		} else {
			textPaint.setFakeBoldText(false);
		}
		textPaint.setTextSize(label.getTextSize());
		float strokeWidth=0;
		if (label.outline||label.strokeWidth>0) {
			if (label.strokeWidth>0) {
				strokeWidth=Math.round(label.getTextSize()*label.strokeWidth);
			} else {
				strokeWidth=Math.round(label.getTextSize()*0.1f);
			}
		}
		textPaint.setStrokeWidth(strokeWidth);
		
		int width=0;
		int height=0;
		if (label.renderVertical) {
			height=(int) (Math.abs(textPaint.ascent())+
					Math.abs(textPaint.descent())+strokeWidth)+1;
			height*=label.getText().length();
			for (int i=0;i<label.getText().length();i++) {
				int thisWidth=(int)(textPaint.measureText(label.getText().substring(i,i+1))+strokeWidth)+1;
				if (thisWidth>width) {
					width=thisWidth;
				}
			}
		} else {
			//height=(int) (Math.abs(textPaint.ascent())+
			//		Math.abs(textPaint.descent())+strokeWidth)+1;
			height=(int) (textPaint.descent()-textPaint.ascent()+strokeWidth);
			Gdx.app.log("", label.getText()+ " height: "+height);
			width=(int)(textPaint.measureText(label.getText())+strokeWidth)+1;
		}
		if (label.shadow) {
			// tan (-0.4), double because text will be centered
			width+=2*textPaint.ascent()*-0.43;
		}
		label.radiusX=(int) (width*0.5f)+3;
		width=label.radiusX*2;
		label.radiusY=(int) (height*0.5f)+2;
		height=label.radiusY*2;
		if (powerTwo) {
			width=nextPowerTwo(width);
			height=nextPowerTwo(height);
		}
		
		Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
		textPaint.setTextAlign(Paint.Align.CENTER);
		if (label.renderVertical) {
			renderTextVertical(label,bitmap,color,outlineColor);
		} else {
			renderTextHorizontal(label,bitmap,color,outlineColor);
		}
		
		return bitmap;
	}
	
	public static void renderTextHorizontal(TextLabel label,Bitmap bitmap,Color color,Color outlineColor) {
		Canvas canvas_=new Canvas(bitmap);
		int x_=label.radiusX;
		//int y_=(int) (Math.abs(textPaint.ascent())+textPaint.getStrokeWidth()*0.5f+1);
		float textHeight=textPaint.descent()-textPaint.ascent();
		float textOffset=(textHeight/2)-textPaint.descent();
		int y_=(int) (label.radiusY+textOffset);
		if (label.shadow) {
			textPaint.setColor(colorToInt(new Color(0,0,0,0.4f)));
			textPaint.setTextSkewX((float) -0.4);
			textPaint.setStyle(Paint.Style.FILL);
			canvas_.drawText(label.getText(),x_,y_,textPaint);
			textPaint.setTextSkewX(0);
		}
		
		if (label.outline) {
			textPaint.setColor(colorToInt(outlineColor));
			textPaint.setStyle(Paint.Style.STROKE);
			canvas_.drawText(label.getText(),x_,y_,textPaint);
			textPaint.setColor(colorToInt(color));
			textPaint.setStyle(Paint.Style.FILL);
			canvas_.drawText(label.getText(),x_,y_,textPaint);
		} else if (label.strokeWidth>0)  {
			textPaint.setColor(colorToInt(color));
			textPaint.setStyle(Paint.Style.STROKE);
			canvas_.drawText(label.getText(),x_,y_,textPaint);
		} else {
			textPaint.setColor(colorToInt(color));
			textPaint.setStyle(Paint.Style.FILL);
			canvas_.drawText(label.getText(),x_,y_,textPaint);
		}
		
	}
	
	public static void renderTextVertical(TextLabel label,Bitmap bitmap,Color color,Color outlineColor) {
		Canvas canvas_=new Canvas(bitmap);
		int x_=label.radiusX;
		int labelHeight=0;
		Rect bounds=new Rect();		
		for (int i=0;i<label.getText().length();i++) {
			String str=label.getText().substring(i,i+1);
			textPaint.getTextBounds(str,0,1,bounds);
			int height=bounds.height();
			int bottom=bounds.bottom;
			int heightMinusBottom=height-bottom;
			labelHeight+=heightMinusBottom*1.2f;
			Gdx.app.log("DPC", str+":"+height);
			if (label.shadow) {
				textPaint.setColor(colorToInt(new Color(0,0,0,0.4f)));
				textPaint.setTextSkewX((float) -0.4);
				textPaint.setStyle(Paint.Style.FILL);
				canvas_.drawText(label.getText().substring(i,i+1),x_,labelHeight,textPaint);
				textPaint.setTextSkewX(0);
			}
			if (label.outline) {
				textPaint.setColor(colorToInt(outlineColor));
				textPaint.setStyle(Paint.Style.STROKE);
				canvas_.drawText(label.getText().substring(i,i+1),x_,labelHeight,textPaint);
				textPaint.setColor(colorToInt(color));
				textPaint.setStyle(Paint.Style.FILL);
				canvas_.drawText(label.getText().substring(i,i+1),x_,labelHeight,textPaint);
			} else if (label.strokeWidth>0)  {
				textPaint.setColor(colorToInt(color));
				textPaint.setStyle(Paint.Style.STROKE);
				canvas_.drawText(label.getText().substring(i,i+1),x_,labelHeight,textPaint);
			} else {
				textPaint.setColor(colorToInt(color));
				textPaint.setStyle(Paint.Style.FILL);
				canvas_.drawText(label.getText().substring(i,i+1),x_,labelHeight,textPaint);
			}
			labelHeight+=bounds.bottom;
		}
		label.radiusY=(int) (labelHeight*0.5f)+1;
	}
	
	@Override
	public void createTextureForLabel(TextLabel label,Color color,Color outlineColor,boolean powerOfTwo) {
		Bitmap bitmap=AndroidTextFactory.labelToBitmap(label, color, outlineColor, powerOfTwo);
		Texture tex = new Texture(bitmap.getWidth(), bitmap.getHeight(), Format.RGBA8888);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,tex.getTextureObjectHandle());
		GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
		bitmap.recycle();
		int labelIndex=textureCache.indexOf(label);
		if (labelIndex>=0) {
			textureCache.get(labelIndex).texture.dispose();
		} else {
			textureCache.add(label);
		}
		label.texture=tex;
	}
	
	public boolean isWithinBounds(String text_,TextLabel label) {
		if (label.fontFace!=null) {
			Typeface fontFace_=Typeface.createFromAsset(activity.getApplicationContext().getAssets(),"fonts/"+label.fontFace);
			textPaint.setTypeface(fontFace_);
		} else {
			textPaint.setTypeface(Typeface.DEFAULT);
		}
		textPaint.setTextSize(label.getTextSize());
		float strokeWidth=0;
		if (label.outline||label.strokeWidth>0) {
			if (label.strokeWidth>0) {
				strokeWidth=Math.round(label.getTextSize()*label.strokeWidth);
			} else {
				strokeWidth=Math.round(label.getTextSize()*0.1f);
			}
		}
		textPaint.setStrokeWidth(strokeWidth);
		float textWidth_=textPaint.measureText(text_);
		boolean withinBounds_=textWidth_<label.maxRadiusX*2;
		return withinBounds_;
	}
	
	private static int nextPowerTwo(int n_) {
		int result_=1;
		while (result_<n_) {
			result_*=2;
		}
		return result_;
	}
	
	private static int colorToInt(Color color) {
		return ((int)(0xFF*color.a)<<24)|
				((int)(0xFF*color.r)<<16)|
				((int)(0xFF*color.g)<<8)|
				(int)(0xFF*color.b);
	}
	
	@Override
	public void dispose(TextLabel label) {
		int labelIndex=textureCache.indexOf(label);
		if (labelIndex>=0) {
			textureCache.get(labelIndex).texture.dispose();
			textureCache.remove(labelIndex);
		} else {
			Gdx.app.log("DPC", "Could not dispose label texture: not found in cache");
		}
	}
	
	@Override
	public void dispose() {
		Gdx.app.log("DPCLifecycle", "AndroidTextFactory - dispose()");
		for (int i=0;i<textureCache.size();i++) {
			textureCache.get(i).texture.dispose();
		}
		textureCache.clear();
	}

}
