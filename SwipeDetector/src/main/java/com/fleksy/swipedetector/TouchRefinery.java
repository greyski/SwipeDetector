package com.fleksy.swipedetector;

import android.util.Log;

import com.fleksy.swipedetector.model.Direction;
import com.fleksy.swipedetector.model.FPoint;
import com.fleksy.swipedetector.model.Gesture;
import com.fleksy.swipedetector.tool.Calculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by grey on 3/17/16.
 */
abstract class TouchRefinery extends SwipeDetector {

    private final Gesture[] touches;
    private final int MAX_FINGERS = 10;
    private Object doubleTapObj;
    private long prevTime = 0;
    private boolean isTapping = false;

    public TouchRefinery(final float pixelSize) {
        super(pixelSize);
        touches = new Gesture[MAX_FINGERS];
    }

    /**
     * Provides the minimum amount of distance a gesture needs to overcome before it can be considered
     * a swipe (as opposed to a tap).
     *
     * @param direction The direction the gesture is currently heading in. Passed for customization.
     * @return The minimum amount of length a gesture needs to be. Defaults to the area's height
     * divided by 12 (assumed best dimension for Fleksy keyboard)
     */
    protected float getMinimumLengthForDirection(Direction direction) {
        return getHeight() / 12.0f;
    }

    protected final void clearTouches() {
        for (int i = 0; i < touches.length; i++) {
            touches[i] = null;
        }
    }

    protected final boolean ignoreEvent(final int index) {
        return isOutOfBounds(index) || touches[index] == null;
    }

    private boolean isOutOfBounds(final int index) {
        return (index < 0 || index >= MAX_FINGERS);
    }

    protected final Gesture getLatestTouch() {
        for (int i = (MAX_FINGERS - 1); i >= 0; i--) {
            if (touches[i] != null) {
                return touches[i];
            }
        }
        return null;
    }

    public final Gesture getTouchAt(final int index) {
        if (isOutOfBounds(index)) {
            return null;
        }
        return touches[index];
    }

    protected final void setTouchAt(final int index, final Gesture touch) {
        if (isOutOfBounds(index)) {
            return;
        }
        touches[index] = touch;
    }

    protected final Object getDoubleTapObj() {
        return doubleTapObj;
    }

    protected final void setDoubleTapObj(Object obj) {
        doubleTapObj = obj;
    }

    protected final Gesture[] getTouches() {
        return touches;
    }

    /**
     * Refinery() :: Checks if the Touch is a swipe, if it is, we refine that tap into one point
     * (The First Point of Contact) and add them onto OUTPUT. If the touch was not a tap, the SwipeChecker
     * scans for phantoms. Mistaken swipes are converted to single taps the first point of contact.
     * Touches that pass this are then attached to the OUTPUT. Those that are phantoms are altered
     * to be two taps that reside at the beginning and end of the original touch.
     */
    protected final List<Gesture> refinery(Gesture touch, float swipeFactor, boolean invertHorizontal) {
        if (touch.refined()) {
            return refineTouch(touch, false);
        }
        final Direction direction = Calculator.getDirection(touch.getDeltaX(), touch.getDeltaY(), invertHorizontal);
        touch.setIgnore(timeCheck(touch));
        if (debugging()) {
            Log.w(getClass().getSimpleName(), "Swipe Factor : " + swipeFactor);
        }
        if (tapChecker(touch, direction, touch.getLength(), swipeFactor)) {    // TAPS
            touch.setDirection(Direction.TAP);
            isTapping = true;
            return refineTouch(touch, onCheckedTap(touch));
        } else {                            // SWIPES
            touch = check(touch);
            if (touch.isPhantom()) {        // PHANTOM SWIPES
                if (debugging()) {
                    Log.e(getClass().getSimpleName(), "PHANTOM SWIPE " + getRawOutput());
                }
                return refineTouch(touch, onPhantomSwipe(touch) && !isTap(touch.getLength(), direction, 1.0f));
            } else {                        // GOOD SWIPE
                touch.setDirection(direction);
                if (debugging()) {
                    Log.w(getClass().getSimpleName(), "GOOD SWIPE " + getRawOutput());
                }
                isTapping = onDetectedSwipe(touch);
                touch.refine();
                ArrayList<Gesture> swipe = new ArrayList<>();
                swipe.add(touch);
                return swipe;
            }
        }
    }

    /**
     * timeCheck() :: Checks whether the time between this touch's down and the previous touch's up allows
     * it to be registered as a swipe.
     */
    private boolean timeCheck(Gesture touch) {
        final boolean isTap = (touch.getPressTime() - prevTime) < getTimeLimit(touch);
        prevTime = touch.getReleaseTime();
        return (isTap && isTapping);
    }

    /**
     * TapChecker() :: Checks if the length between the HEAD and TAIL are a MINIUM_LENGTH apart to
     * be either a swipe or tap. TRUE if tap.
     */
    protected boolean tapChecker(final Gesture touch, final Direction direction, final double length, final float multiple) { /* TAP CHECKER */
        return isTap(length, direction, multiple)
                || direction == Direction.UNDEFINED
                || ignoreSwipe(touch, direction);
    }

    private boolean isTap(double length, Direction action, float multiplier) {
        return action == Direction.UNDEFINED ||
                (length <= (getMinimumLengthForDirection(action) * multiplier));
    }

    /**
     * RefineTouch() :: Takes Touches and refines them to be more absolute and start at their origin.
     * Whereas phantom swipes get refined into two touches at both their beginning and end.
     * Added to OUTPUT.
     */
    private List<Gesture> refineTouch(final Gesture old, final boolean phantom) { /* REFINE TAP */

        final List<Gesture> freed = new ArrayList<>();
        final Gesture parent = old.leaveParent();
        final FPoint start = old.getFirstPoint();

        freed.add(makeTap(start, old, parent, phantom));

        if (phantom) {
            final FPoint end = Calculator.findFurthestPoint(start, old.getLastPoint(), old.getPoints());
            freed.add(makeTap(end, old, parent, true));
        }

        decideChildrensFate(parent, old, freed);

        return freed;
    }

    private Gesture makeTap(FPoint origin, Gesture old, Gesture parent, boolean phantom) {
        Gesture touch = new Gesture(old.getTag(), old.getId(), origin.getX(), origin.getY(), origin.getTime(), parent);
        touch.setDirection(Direction.TAP);
        touch.refine();
        touch.setPhantom(phantom);
        touch.setHold(old.isHeld());
        return touch;
    }

    private void decideChildrensFate(Gesture parent, Gesture old, List<Gesture> refined) {
        if (parent != null) {
            parent.adoptChildrenFrom(old);
        } else {
            for (Gesture orphan : old.abandonChildren()) {
                refined.add(orphan);
            }
        }
    }

    protected abstract int getTimeLimit(Gesture touch);

    protected abstract boolean ignoreSwipe(final Gesture touch, final Direction direction);

    protected abstract boolean onCheckedTap(Gesture tap);

    protected abstract boolean onPhantomSwipe(Gesture phantom);

    protected abstract boolean onDetectedSwipe(Gesture swipe);

    protected abstract float getMinSwipeLength();

}
