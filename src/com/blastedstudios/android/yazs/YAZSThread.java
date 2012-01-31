package com.blastedstudios.android.yazs;

import java.util.Random;
import java.util.Vector;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;

public class YAZSThread extends Thread {
	public static final int DIFFICULTY_EASY = 0, DIFFICULTY_HARD = 1, DIFFICULTY_MEDIUM = 2;
	public static final int STATE_LOSE = 1, STATE_PAUSE = 2, STATE_READY = 3, STATE_RUNNING = 4, STATE_WIN = 5;
	
	private static final String KEY_DIFFICULTY = "mDifficulty";
	private static float PLAYER_SPEED = 2.0f, 
		ZOMBIE_SPEED = .5f, PLAYER_TURN_RATE = .2f, ZOMBIE_TURN_RATE = .016f,
		SHOT_REMOVE_DISTANCE = 99999f, SHOT_VELOCITY = 6f, SHOT_LENGTH = 11f,
		ENEMY_RESPAWN_MAX_INTERIM = 10f, ENEMY_RESPAWN_DIVISOR = 1.007f,
		ENEMY_SPAWN_COUNT_BEGIN = 8f, ENEMY_SPAWN_COUNT_INCREASE_FACTOR =1.09f,
		DISTANCE_FROM_PLAYER_TO_SPAWN = 550f, ZOMBIE_POWER_DROP_CHANCE = .035f,
		MAX_SHOT_ANGLE = .45f;
	private static int MAX_ZOMBIES = 128, UPGRADES_PER_BULLET = 3, ZOMBIE_SCORE = 10;
	public static float TOUCH_MOVEMENT_AREA_WIDTH = 128f;
	
	public static final int ALPHA_MAX = 255;
	public static final Random rand = new Random();

	/** The drawable to use as the background of the animation canvas */
	private Bitmap mBackgroundImage, zombie1Bitmap, playerBitmap, 
		zombie1deadBitmap, analogBitmap, powerupBitmap, blastedBitmap;
	private int mCanvasHeight = 1, mCanvasWidth = 1, mDifficulty;
	private Handler mHandler;

	/** Used to figure out elapsed time between frames */
	private long mLastTime;

	/** The state of the game. One of READY, RUNNING, PAUSE, LOSE, or WIN */
	public int mMode;

	private Double timeTillRespawn = 10.0;

	/** Indicate whether the surface has been created & is ready to draw */
	private boolean mRun = false;
	public boolean logo = true;

	private Vec2 lastPress = new Vec2();//last location on screen use touched so we may figure out where to move
	private boolean isMoving=false;
	private Context mContext;
	private Being player;
	private Vector<Being> enemies;
	private Vector<Being> enemiesDead;
	private Vector<Shot> shots;
	private int playerUpgrades;
	private long timeOfLastShot, score;
	private float timeBetweenEnemyRespawns;
	private float enemySpawnCount;
	private Vector<Vec2> powerups;
	public SoundManager soundManager;
	Resources res;

	/** Handle to the surface manager object we interact with */
	private SurfaceHolder mSurfaceHolder;

	public YAZSThread(SurfaceHolder surfaceHolder, Context context,
			Handler handler) {

		// get handles to some important objects
		mSurfaceHolder = surfaceHolder;
		mHandler = handler;
		mContext = context;

		res = context.getResources();
		// cache handles to our key sprites & other drawables

		// load background image as a Bitmap instead of a Drawable b/c
		// we don't need to transform it and it's faster to draw this way
		mBackgroundImage = BitmapFactory.decodeResource(res,R.drawable.parkinglot);

		analogBitmap = BitmapFactory.decodeResource(res, R.drawable.analog128);
		playerBitmap = BitmapFactory.decodeResource(res, R.drawable.player);
		zombie1Bitmap = BitmapFactory.decodeResource(res, R.drawable.zombie1);
		zombie1deadBitmap = BitmapFactory.decodeResource(res, R.drawable.zombie1dead);
		powerupBitmap = BitmapFactory.decodeResource(res, R.drawable.powerup);
		blastedBitmap = BitmapFactory.decodeResource(res, R.drawable.blasted);
		
		TOUCH_MOVEMENT_AREA_WIDTH = analogBitmap.getHeight();

		mDifficulty = DIFFICULTY_MEDIUM;
		
		soundManager = new SoundManager();
		soundManager.initSounds(context);
		soundManager.addSound(R.raw.shotgun_wildweasel);

		newGame();
	}

	/**
	 * Randomly add one enemy somewhere DISTANCE_FROM_PLAYER_TO_SPAWN 
	 * distance units away
	 */
	private void addEnemy(){
		if(enemies.size() < MAX_ZOMBIES){
			int yMult = (rand.nextInt(2)==0)? -1 : 1;

			//get angle toward where we shall add enemy
			float direction = rand.nextFloat() * new Float(Math.PI);
			float x = player.pos.x + new Float(Math.cos(direction)) * DISTANCE_FROM_PLAYER_TO_SPAWN;
			float y = player.pos.y + new Float(Math.sin(direction)) * DISTANCE_FROM_PLAYER_TO_SPAWN * yMult;
			//for speed move them closer
			final float DISTANCE_THRESHHOLD = 10f;
			if(x<-DISTANCE_THRESHHOLD) 	x = -DISTANCE_THRESHHOLD;
			if(y<-DISTANCE_THRESHHOLD) 	y = -DISTANCE_THRESHHOLD;
			if(x>getScreenMetrics().x + DISTANCE_THRESHHOLD) 	x = getScreenMetrics().x+DISTANCE_THRESHHOLD;
			if(y>getScreenMetrics().y + DISTANCE_THRESHHOLD) 	y = getScreenMetrics().y+DISTANCE_THRESHHOLD;
			float rot = Utils.targetAngleFromDirection(player.pos.sub(new Vec2(x, y)));
			Being enemy = new Being(x, y, rot);
			enemies.add(enemy);
		}
	}

	/**
	 * Starts the game, setting parameters for the current difficulty.
	 */
	public void doStart() {
		synchronized (mSurfaceHolder) {
			// Adjust difficulty params for EASY/HARD
			if (mDifficulty == DIFFICULTY_EASY) {
			} else if (mDifficulty == DIFFICULTY_HARD) {
			}
			mLastTime = System.currentTimeMillis() + 100;
			setState(STATE_RUNNING);
			newGame();
		}
	}

	public void newGame(){
		powerups = new Vector<Vec2>();
		timeBetweenEnemyRespawns = ENEMY_RESPAWN_MAX_INTERIM;
		player = new Being(getScreenMetrics().x/2, getScreenMetrics().y/2, 0);
		enemies = new Vector<Being>();
		enemiesDead = new Vector<Being>();
		shots = new Vector<Shot>();
		enemySpawnCount = ENEMY_SPAWN_COUNT_BEGIN;
		playerUpgrades = 0;
		timeOfLastShot = score = 0;
	}

	/**
	 * Pauses the physics update & animation.
	 */
	public void pause() {
		synchronized (mSurfaceHolder) {
			if (mMode == STATE_RUNNING) setState(STATE_PAUSE);
		}
	}

	/**
	 * Restores game state from the indicated Bundle. Typically called when
	 * the Activity is being restored after having been previously
	 * destroyed.
	 * 
	 * @param savedState Bundle containing the game state
	 */
	public synchronized void restoreState(Bundle savedState) {
		synchronized (mSurfaceHolder) {
			setState(STATE_PAUSE);
			mDifficulty = savedState.getInt(KEY_DIFFICULTY);
		}
	}

	@Override
	public void run() {
		while (mRun) {
			Canvas c = null;
			try {
				c = mSurfaceHolder.lockCanvas(null);
				synchronized (mSurfaceHolder) {
					if (mMode == STATE_RUNNING) 
						update();
					doDraw(c);
				}
			} finally {
				// do this in a finally so that if an exception is thrown
				// during the above, we don't leave the Surface in an
				// inconsistent state
				if (c != null) {
					mSurfaceHolder.unlockCanvasAndPost(c);
				}
			}
		}
	}

	/**
	 * Dump game state to the provided Bundle. Typically called when the
	 * Activity is being suspended.
	 * 
	 * @return Bundle with this view's state
	 */
	public Bundle saveState(Bundle map) {
		synchronized (mSurfaceHolder) {
			if (map != null) {
				map.putInt(KEY_DIFFICULTY, Integer.valueOf(mDifficulty));
			}
		}
		return map;
	}

	/**
	 * Sets the current difficulty.
	 * 
	 * @param difficulty
	 */
	public void setDifficulty(int difficulty) {
		synchronized (mSurfaceHolder) {
			mDifficulty = difficulty;
		}
	}

	/**
	 * Used to signal the thread whether it should be running or not.
	 * Passing true allows the thread to run; passing false will shut it
	 * down if it's already running. Calling start() after this was most
	 * recently called with false will result in an immediate shutdown.
	 * 
	 * @param b true to run, false to shut down
	 */
	public void setRunning(boolean b) {
		mRun = b;
	}

	/**
	 * Sets the game mode. That is, whether we are running, paused, in the
	 * failure state, in the victory state, etc.
	 * 
	 * @see #setState(int, CharSequence)
	 * @param mode one of the STATE_* constants
	 */
	public void setState(int mode) {
		synchronized (mSurfaceHolder) {
			setState(mode, null);
		}
	}

	/**
	 * Sets the game mode. That is, whether we are running, paused, in the
	 * failure state, in the victory state, etc.
	 * 
	 * @param mode one of the STATE_* constants
	 * @param message string to add to screen or null
	 */
	public void setState(int mode, CharSequence message) {
		/*
		 * This method optionally can cause a text message to be displayed
		 * to the user when the mode changes. Since the View that actually
		 * renders that text is part of the main View hierarchy and not
		 * owned by this thread, we can't touch the state of that View.
		 * Instead we use a Message + Handler to relay commands to the main
		 * thread, which updates the user-text View.
		 */
		synchronized (mSurfaceHolder) {
			mMode = mode;

			if (mMode == STATE_RUNNING) {
				Message msg = mHandler.obtainMessage();
				Bundle b = new Bundle();
				b.putString("text", "");
				b.putInt("viz", View.INVISIBLE);
				msg.setData(b);
				mHandler.sendMessage(msg);
			} else {
				Resources res = mContext.getResources();
				CharSequence str = "";
				if (mMode == STATE_READY)
					str = res.getText(R.string.mode_ready);
				else if (mMode == STATE_PAUSE)
					str = res.getText(R.string.mode_pause);
				else if (mMode == STATE_LOSE)
					str = res.getText(R.string.mode_lose);

				if (message != null) {
					str = message + "\n" + str;
				}

				Message msg = mHandler.obtainMessage();
				Bundle b = new Bundle();
				b.putString("text", str.toString());
				b.putInt("viz", View.VISIBLE);
				msg.setData(b);
				mHandler.sendMessage(msg);
			}
		}
	}

	/* Callback invoked when the surface dimensions change. */
	public void setSurfaceSize(int width, int height) {
		// synchronized to make sure these all change atomically
		synchronized (mSurfaceHolder) {
			mCanvasWidth = width;
			mCanvasHeight = height;

			// don't forget to resize the background image
			mBackgroundImage = Bitmap.createScaledBitmap(
					mBackgroundImage, width, height, true);
		}
	}

	/**
	 * Resumes from a pause.
	 */
	public void unpause() {
		// Move the real time clock up to now
		synchronized (mSurfaceHolder) {
			mLastTime = System.currentTimeMillis() + 100;
		}
		setState(STATE_RUNNING);
	}

	/**
	 * Handles a key-down event.
	 * 
	 * @param keyCode the key that was pressed
	 * @param msg the original event object
	 * @return true
	 */
	boolean doKeyDown(int keyCode, KeyEvent msg) {
		synchronized (mSurfaceHolder) {
			boolean okStart = false;
			if (keyCode == KeyEvent.KEYCODE_DPAD_UP || 
					keyCode == KeyEvent.KEYCODE_S) 
				okStart = true;

			if (okStart && (mMode == STATE_READY || mMode == STATE_LOSE || mMode == STATE_WIN)) {
				doStart();
			} else if (mMode == STATE_PAUSE && okStart) {
				unpause();
			} else if (mMode == STATE_RUNNING) {
				/*if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
						|| keyCode == KeyEvent.KEYCODE_SPACE)
				else*/
				if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
						|| keyCode == KeyEvent.KEYCODE_Q)
					player.addRot(-PLAYER_TURN_RATE);
				else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
						|| keyCode == KeyEvent.KEYCODE_E)
					player.addRot(PLAYER_TURN_RATE);
				else if (keyCode == KeyEvent.KEYCODE_DPAD_UP
						|| keyCode == KeyEvent.KEYCODE_W){
					player.applyForce(new Vec2(
							new Float(Math.cos(player.rot - Utils.PIOVERTWO)), 
							new Float(Math.sin(player.rot + Utils.PIOVERTWO))).mul(PLAYER_SPEED));
				}else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
						|| keyCode == KeyEvent.KEYCODE_S){
					player.applyForce(new Vec2(
							new Float(Math.cos(player.rot - Utils.PIOVERTWO)), 
							new Float(Math.sin(player.rot + Utils.PIOVERTWO))).mul(PLAYER_SPEED).negate());
				}else if (keyCode == KeyEvent.KEYCODE_DPAD_UP)
					pause();
				else if (keyCode == KeyEvent.KEYCODE_M)
					addEnemy();
				else if (keyCode == KeyEvent.KEYCODE_N)
					newGame();
				else if (keyCode == KeyEvent.KEYCODE_1)
					shoot();
				else 
					return false;
			}
			else
				return false;
		}
		return true;
	}

	/**
	 * Fire shot if player is ready.
	 */
	private void shoot() {
		if(isShotReady()){
			timeOfLastShot = System.currentTimeMillis();
			
			//Shot deviance - how far off the angle of the player we are. Start
			//shooting forward, then change from left to right
			float shotDev = -MAX_SHOT_ANGLE/2;
			
			//Fire according to how many upgrades we have
			int numberOfShots = 5 + playerUpgrades/UPGRADES_PER_BULLET;
			
			for(int i=0; i<numberOfShots; i++){
				Vec2 vel = new Vec2(new Float(Math.cos(player.rot + shotDev - Utils.PIOVERTWO)), 
						new Float(Math.sin(player.rot + shotDev + Utils.PIOVERTWO)));
				Shot newShot = new Shot(player.pos,vel);
				shots.add(newShot);
				shotDev += MAX_SHOT_ANGLE / numberOfShots;
			}
			soundManager.playSound(R.raw.shotgun_wildweasel);
		}
	}

	/**
	 * Handles a key-up event.
	 * 
	 * @param keyCode the key that was pressed
	 * @param msg the original event object
	 * @return true if the key was handled and consumed, or else false
	 */
	boolean doKeyUp(int keyCode, KeyEvent msg) {
		/*
		   boolean handled = false;
		   synchronized (mSurfaceHolder) {
			if (mMode == STATE_RUNNING) {
				if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
						|| keyCode == KeyEvent.KEYCODE_SPACE) {
					handled = true;
				} else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
						|| keyCode == KeyEvent.KEYCODE_Q
						|| keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
						|| keyCode == KeyEvent.KEYCODE_W) {
					handled = true;
				}
			}
		}*/
		return true;
	}

	private void doDraw(Canvas canvas) {
		if(logo){
			canvas.drawColor(Color.rgb(250, 167, 35));
			canvas.drawBitmap(blastedBitmap, 
					getScreenMetrics().x/2 - blastedBitmap.getWidth()/2, 
					getScreenMetrics().y/2 - blastedBitmap.getHeight()/2, null);
			return;
		}
		//Draw background
		canvas.drawBitmap(mBackgroundImage, 0, 0, null);

		//Draw controller (on left)
		Matrix matrix = new Matrix();
		matrix.postTranslate(0, mCanvasHeight/2 - TOUCH_MOVEMENT_AREA_WIDTH/2);
		canvas.drawBitmap(analogBitmap, matrix, null);

		//Draw powerups
		for(Vec2 powLoc : powerups)
			drawBitmap(powerupBitmap, 0, powLoc, canvas, 0);
		
		//Draw enemies
		for(Being enemy : enemies)
			drawBitmap(zombie1Bitmap, enemy.rot, enemy.pos, canvas, 0);
		for(Being enemy : enemiesDead)
			drawBitmap(zombie1deadBitmap, enemy.rot, enemy.pos, canvas, enemy.ticksToDelete);
		
		//Draw player
		drawBitmap(playerBitmap, player.rot, player.pos, canvas, 0);
		
		//Draw gun shots
		Paint paint = new Paint();
		paint.setColor(Color.GRAY);
		paint.setStrokeWidth(1.5f);
		for(Shot shot : shots)
			canvas.drawLine(shot.pos.x, mCanvasHeight - shot.pos.y, shot.pos.x + shot.vel.x*SHOT_LENGTH, 
					mCanvasHeight - (shot.pos.y + shot.vel.y*SHOT_LENGTH), paint);

		paint.setColor(Color.GRAY);
		paint.setStrokeWidth(1f);
		String scoreString = "Score: " + score;
		canvas.drawText(scoreString, mCanvasWidth/2 - scoreString.length()*4, 12, paint);
	}
	
	/**
	 * @return A Vec2 representing width, height of display
	 */
	public Vec2 getScreenMetrics(){
		DisplayMetrics dm = new DisplayMetrics();
		WindowManager wManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE); 
		wManager.getDefaultDisplay().getMetrics(dm);
		return new Vec2(dm.widthPixels, dm.heightPixels);
	}

	private void update() {
		long now = System.currentTimeMillis();
		if (mLastTime > now) return;

		Double elapsed = (now - mLastTime) / 1000.0;

		//respawn monsters
		timeTillRespawn -= elapsed;
		if(timeTillRespawn < 0){
			for(int i=0; i<Math.floor(enemySpawnCount); ++i)//insert wolfram alpha equation to figure out how many enemies to add and how often
				addEnemy();
			timeBetweenEnemyRespawns /= ENEMY_RESPAWN_DIVISOR;
			timeTillRespawn = new Double(timeBetweenEnemyRespawns);
			enemySpawnCount *= ENEMY_SPAWN_COUNT_INCREASE_FACTOR;
		}

		updateAI();
		
		//update bullets and list far bullets
		for(int i=0; i<shots.size(); ++i){
			shots.get(i).pos = shots.get(i).pos.add(shots.get(i).vel.mul(SHOT_VELOCITY));
			if(shots.get(i).pos.sub(player.pos).lengthSquared() > SHOT_REMOVE_DISTANCE)
				shots.remove(i--);
		}
		
		//pickup powerups!
		for(int i=0; i<powerups.size(); ++i)
			if(player.checkBounds(powerups.get(i))){
				++playerUpgrades;
				powerups.remove(i--);
			}

		if(isMoving){
			//get direction vector toward finger press
			Vec2 dir = lastPress.sub(getMoveControlStickPos());
			dir.normalize();
			dir.y *= -1;
			player.applyForce(dir.mul(PLAYER_SPEED));
		}
		
		mLastTime = now;
	}
	
	/**
	 * Update all zombies. Moves, turns, kills, etc
	 */
	private void updateAI(){
		for(int i=0; i<enemies.size(); ++i){
			//Calculate exact angle toward player
			Vec2 dir = player.pos.sub(enemies.get(i).pos);
			float targetAngle = Utils.targetAngleFromDirection(dir);
			
			//If difference is great enough, turn
			if(Math.abs(targetAngle - enemies.get(i).rot) > .3){
				float rotate = (Utils.rotNormalize(enemies.get(i).rot - targetAngle) < Math.PI) ? -ZOMBIE_TURN_RATE : ZOMBIE_TURN_RATE;
				enemies.get(i).addRot(rotate);
			}

			//move forward if a certain distance away
			enemies.get(i).applyForce(new Vec2(new Float(Math.cos(enemies.get(i).rot - Utils.PIOVERTWO)), 
					new Float(Math.sin(enemies.get(i).rot + Utils.PIOVERTWO))).mul(ZOMBIE_SPEED));
			
			//check if shot
			for(int shotIndex = 0; shotIndex < shots.size(); shotIndex++){
				if(enemies.get(i).checkBounds(shots.get(shotIndex).pos) && enemies.get(i).hp > 0){
					if(rand.nextFloat() < ZOMBIE_POWER_DROP_CHANCE)
						powerups.add(enemies.get(i).pos.clone());
					--enemies.get(i).hp;
					shots.remove(shotIndex--);
				}
			}
			
			//check if you hit player!
			if(enemies.get(i).checkBounds(player.pos))
				setState(STATE_LOSE);

			if(enemies.get(i).hp < 1){
				enemiesDead.add(enemies.get(i));
				enemies.remove(i--);
				score += (1 + playerUpgrades/10.) * ZOMBIE_SCORE;
			}
		}
		for(int i=0; i<enemiesDead.size(); ++i){
			try{
				enemiesDead.get(i).ticksToDelete -= 3;
				if(enemiesDead.get(i).ticksToDelete < 1)
					enemiesDead.remove(i--);
			}catch(Exception e){}
		}
	}

	private void drawBitmap(final Bitmap bitmap, final float rotateRads, final Vec2 pos, Canvas canvas, int alpha){
		final float BITMAP_SIZE=32f;
		Matrix matrix = new Matrix();
		float degrees = new Float(Math.toDegrees(rotateRads));
		matrix.preRotate(degrees, BITMAP_SIZE/2, BITMAP_SIZE/2);
		matrix.postTranslate(pos.x-BITMAP_SIZE/2, mCanvasHeight-BITMAP_SIZE/2 - pos.y);
		Paint paint = null;
		if(alpha > 0){
			paint = new Paint();
			paint.setAlpha(alpha);
		}
		canvas.drawBitmap(bitmap, matrix, paint);
	}
	
	/**
	 * Figure out if the player can shoot yet. Equation gotten from plot on
	 * wolfram alpha
	 * @return true if player may shoot, false otherwise
	 */
	public boolean isShotReady(){
		int timeReload = new Double(
				-0.000177 * Math.pow(playerUpgrades, 3) + 
				0.004 * Math.pow(playerUpgrades, 2) - 
				0.086 * playerUpgrades + 3).intValue();
		if(timeReload<500)
			timeReload=500;
		if(timeOfLastShot + timeReload*1000 > System.currentTimeMillis() )
			return false;
		return true;
	}

	/** 
	 * @return Absolute center location of the analog movement control stick
	 */
	private Vec2 getMoveControlStickPos() {
		Vec2 screenMetrics = getScreenMetrics();
		return new Vec2(TOUCH_MOVEMENT_AREA_WIDTH/2, screenMetrics.y/2);
	}

	/**
	 * Handle a move. This will both eventually move a player and not move a 
	 * player if it is not held down
	 * @param x
	 * @param y
	 * @param isMoving
	 */
	public void handleTouchMove(float x, float y, boolean isMoving) {
		synchronized (mSurfaceHolder) {
			lastPress=new Vec2(x, y);
			this.isMoving=isMoving;
		}
	}

	public void handleTouchShot(float x, float y) {
		synchronized (mSurfaceHolder) {
			//get direction vector toward finger press
			Vec2 pos = new Vec2(x, mCanvasHeight - y);
			Vec2 dir = pos.sub(player.pos);
			
			//turn player towards finger
			player.setRot(Utils.targetAngleFromDirection(dir));
			
			//then shoot
			shoot();
		}
	}
}
