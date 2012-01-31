package com.blastedstudios.android.yazs;

import java.util.HashMap;

import android.content.Context;
import android.media.MediaPlayer;

public class SoundManager {
	private  HashMap<Integer, MediaPlayer> mSoundPoolMap;
	private  Context mContext;

	public void initSounds(Context theContext) {
		mContext = theContext;
		mSoundPoolMap = new HashMap<Integer, MediaPlayer>();
	}

	public void addSound(int soundID){
		mSoundPoolMap.put( soundID, MediaPlayer.create(mContext, soundID));
	}

	public void playSound(int soundID){
		MediaPlayer l= mSoundPoolMap.get(soundID);
		l.start();
		mSoundPoolMap.put( soundID, l);
	}

	public void stopAll() {
		for(MediaPlayer player : mSoundPoolMap.values())
			player.stop();
	}
}
