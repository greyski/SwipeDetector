package com.fleksy.swipedetector.model;

import com.fleksy.swipedetector.tool.Calculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by grey on 3/17/16.
 */
public class Gesture {

    public final static int HEAD = 0;
    public final static int BODY = 1;
    public final static int TAIL = 3;

    private final int ID;
    private final ArrayList<FPoint> points;
    private final ArrayList<Gesture> children;
    private Gesture parent;
    private Object TAG;
    private boolean holding = false;
    private boolean refined = false;
    private boolean ignored = false;
    private boolean phantom = false;
    private boolean process = false;

    private Direction direction = Direction.UNDEFINED;

    public Gesture(Object TAG, int ID, float x, float y, long eventTime, Gesture parent) {
        this.ID = ID;
        this.TAG = TAG;
        this.parent = parent;
        points = new ArrayList<>();
        children = new ArrayList<>();
        addPoint(new FPoint(HEAD, x, y, eventTime));
        if (parent != null) {
            parent.haveChild(this);
        }
    }

    public final int getId() {
        return ID;
    }

    public final Object getTag() {
        return TAG;
    }

    public final void process() {
        process = true;
    }

    public final boolean processed() {
        return process;
    }

    public final void setHold(boolean holding) {
        this.holding = holding;
    }

    public final boolean isHeld() {
        return holding;
    }

    public final List<FPoint> getPoints() {
        return points;
    }

    public final void addPoint(FPoint p) {
        points.add(p);
    }

    public final boolean pointExists(int index) {
        return index < points.size() && index >= 0;
    }

    public final FPoint getPointAt(int index) {
        if (pointExists(index)) {
            return points.get(index);
        }
        return null;
    }

    public final FPoint getFirstPoint() {
        return getPointAt(0);
    }

    public final FPoint getLastPoint() {
        return getPointAt(points.size() - 1);
    }

    public final void editPoint(int index, FPoint p) {
        if (pointExists(index)) {
            points.set(index, p);
        }
    }

    private long getPointTime(FPoint point) {
        if (point != null) {
            return point.getTime();
        }
        return -1;
    }

    private float getPointX(FPoint point) {
        if (point != null) {
            return point.getX();
        }
        return -1;
    }

    private float getPointY(FPoint point) {
        if (point != null) {
            return point.getY();
        }
        return -1;
    }

    public final long getPressTime() {
        return getPointTime(getFirstPoint());
    }

    public final long getReleaseTime() {
        return getPointTime(getLastPoint());
    }

    public final long getTimeTaken() {
        return getReleaseTime() - getPressTime();
    }

    public final float getDownX() {
        return getPointX(getFirstPoint());
    }

    public final float getDownY() {
        return getPointY(getFirstPoint());
    }

    public final float getUpX() {
        return getPointX(getLastPoint());
    }

    public final float getUpY() {
        return getPointY(getLastPoint());
    }

    public final float getDeltaX() {
        return Calculator.calcDeltaF(getDownX(), getUpX());
    }

    public final float getDeltaY() {
        return Calculator.calcDeltaF(getDownY(), getUpY());
    }

    public final double getLength() {
        return Calculator.calcPointLength(getFirstPoint(), getLastPoint());
    }

    public final double getRadian() {
        double radian = Calculator.findRads(getDeltaX(), -getDeltaY());
        if (radian < 0) {
            radian += 6.28f;
        }
        return radian;
    }

    public final double getNormalRadian() {
        double radian = Calculator.findRads(-getDeltaX(), getDeltaY());
        if (radian < 0) {
            radian += 6.28f;
        }
        return radian;
    }

    public final boolean isPhantom() {
        return phantom;
    }

    public final void setPhantom(final boolean phantom) {
        this.phantom = phantom;
    }

    public final Direction getDirection() {
        return direction;
    }

    public final void setDirection(Direction direction) {
        this.direction = direction;
    }

    public final Gesture getRoot() {
        if (parent == null) {
            return this;
        } else {
            return parent.getRoot();
        }
    }

    public final Gesture leaveParent() {
        if (parent != null) {
            parent.loseChild(this);
        }
        return parent;
    }

    public final Gesture getParent() {
        return parent;
    }

    public final void setParent(Gesture adopter) {
        if (parent != null) {
            parent.loseChild(this);
        }
        parent = adopter;
        if (parent != null) {
            parent.haveChild(this);
        }
    }

    public final boolean canLiberate(final int target) {
        return children.size() >= target;
    }

    public final Gesture getLiberator() {
        if (!hasChildren()) {
            return this;
        }
        return children.get(children.size() - 1);
    }

    public final void haveChild(Gesture child) {
        children.add(child);
    }

    private void loseChild(Gesture child) {
        children.remove(child);
    }

    public final void setIgnore(final boolean ignore) {
        this.ignored = ignore;
    }

    public final boolean ignore() {
        return ignored;
    }

    public final void refine() {
        refined = true;
    }

    public final boolean refined() {
        return refined;
    }

    public final boolean orphaned() {
        return parent == null;
    }

    public final boolean freed() {
        return orphaned() && refined();
    }

    public final boolean hasParent() {
        return parent != null;
    }

    public final boolean hasChildren() {
        return children.size() > 0;
    }

    public final int getChildCount() {
        return children.size();
    }

    public final List<Gesture> getChildren() {
        return children;
    }

    public final List<Gesture> abandonChildren() {
        if (children.isEmpty()) {
            return children;
        }
        List<Gesture> abandoned = new ArrayList<Gesture>();
        for (int i = 0; i < children.size(); i++) {
            final Gesture child = children.get(i);
            child.parent = null;
            abandoned.add(child);
        }
        children.clear();
        return abandoned;
    }

    public final void adoptChildrenFrom(Gesture old) {
        for (int i = 0; i < old.getChildCount(); i++) {
            final Gesture child = old.children.get(i);
            child.parent = this;
            haveChild(child);
        }
        old.children.clear();
    }

}
