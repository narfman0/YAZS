package com.blastedstudios.android.yazs;

public class Being {
	public static final float BEING_RADIUS = 10f;
	int hp = 1, ticksToDelete = YAZSThread.ALPHA_MAX;
	float rot = YAZSThread.rand.nextFloat() * new Float(Math.PI);
	Vec2 pos;
	
	public Being(float x, float y, float rot){
		pos = new Vec2(x, y);
		this.rot = rot;
	}
	
	public void addRot(float rot){
		this.rot += rot;
		rot = Utils.rotNormalize(rot);
	}

	public void setRot(float rot) {
		this.rot = rot;
	}

	public void applyForce(Vec2 force) {
		pos.addLocal(force);
	}
	
	/**
	 * 
	 * @param point
	 * @return if the point is within the being
	 */
	public boolean checkBounds(Vec2 point){
		if(point.x < pos.x + BEING_RADIUS &&
				point.x > pos.x - BEING_RADIUS &&
				point.y < pos.y + BEING_RADIUS &&
				point.y > pos.y - BEING_RADIUS)
			return true;
		return false;
	}
}
