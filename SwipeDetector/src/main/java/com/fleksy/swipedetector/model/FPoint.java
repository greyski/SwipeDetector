package com.fleksy.swipedetector.model;

/**
 * Created by grey on 3/17/16.
 */
public final class FPoint {
    public float x;
    public float y;
    private int state;
    private long time;

    public FPoint(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public FPoint(int s, float x, float y, long time) {
        this.state = s;
        this.x = x;
        this.y = y;
        this.time = time;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public void setState(int s) {
        this.state = s;
    }

    public int getState() {
        return state;
    }
}
