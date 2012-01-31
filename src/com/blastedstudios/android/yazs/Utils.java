package com.blastedstudios.android.yazs;

public class Utils {
	public static float TWOPI = new Float(2*Math.PI);
	public static float PIOVERTWO = new Float(Math.PI/2);
	
	/**
	 * Make sure rotation is normalized
	 */
	public static float rotNormalize(float rot) {
		while(rot > TWOPI)	rot -= TWOPI;
		while(rot < 0)		rot += TWOPI;
		return rot;
	}
	
	public static float targetAngleFromDirection(Vec2 dir){
		return rotNormalize((float)Math.atan2(dir.x, dir.y));
	}
}
