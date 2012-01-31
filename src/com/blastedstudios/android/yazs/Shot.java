package com.blastedstudios.android.yazs;

public class Shot {
	Vec2 pos, vel;
	public Shot(Vec2 pos, Vec2 vel){
		this.pos = pos;
		this.vel = vel;
	}
	public Shot(float x, float y, float dx, float dy){
		this(new Vec2(x, y), new Vec2(dx,dy));
	}
}
