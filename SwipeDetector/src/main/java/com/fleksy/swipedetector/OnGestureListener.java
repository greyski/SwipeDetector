package com.fleksy.swipedetector;

import android.graphics.RectF;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import com.fleksy.swipedetector.model.Direction;
import com.fleksy.swipedetector.model.FPoint;
import com.fleksy.swipedetector.model.Gesture;
import com.fleksy.swipedetector.tool.Calculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by grey on 3/17/16.
 */
public abstract class OnGestureListener extends TouchRefinery implements OnTouchListener {

    private final Handler altHandler = new Handler();
    private final Handler mainHandler = new Handler();
    private final Runnable doubleTapTimer = new Runnable() {
        @Override
        public void run() {
            setDoubleTapObj(null);
        }
    };
    private final Runnable preHolding = new Runnable() {
        @Override
        public void run() {
            heldCount = 0;
            final Gesture heldTouch = getTouchAt(heldID);
            if (heldTouch != null) {
                if (debugging()) {
                    Log.e(getClass().getSimpleName(), "preHolding");
                }
                onPreHold(heldTouch);
                mainHandler.postDelayed(onHolding, holdDelay(heldTouch));
            }
        }
    };
    private final Runnable onHolding = new Runnable() {
        @Override
        public void run() {
            isHolding = true;
            setDoubleTapObj(null);
            final Gesture heldTouch = getTouchAt(heldID);
            if (heldTouch != null) {
                if (debugging()) {
                    Log.e(getClass().getSimpleName(), "onHolding");
                }
                heldCount++;
                heldTouch.setHold(true);
                onHold(heldTouch);
                if (repeatHold(heldTouch)) {
                    mainHandler.postDelayed(onHolding, postHoldDelay(heldTouch));
                }
            }
        }
    };

    private int touchCount;
    private int recentID;

    private boolean doubleTap;

    private int heldID;
    private int heldCount;
    private boolean heldDrag;
    private boolean isHolding;

    private int specialID;
    private RectF specialArea;
    private boolean useLatest;
    private boolean isSpecial;
    private boolean wasSpecial;
    private boolean ranSpecial;
    private boolean heldSpecial;
    private Direction specialDir;

    private int areaID;
    private ArrayList<RectF> areas = new ArrayList<>();

    private boolean isMultiTouch;

    public OnGestureListener(float pixelSize) {
        super(pixelSize);
    }

    @Override
    public boolean onTouch(View v, MotionEvent me) {
        if (me == null) {
            return true;
        }
        switch (me.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_DOWN:
                return onDown(getID(me), me);
            case MotionEvent.ACTION_MOVE:
                if (ignoreEvent(getID(me))) {
                    return onCancel();
                }
                return onMove(me);
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                if (ignoreEvent(getID(me))) {
                    return onCancel();
                }
                return onUp(getID(me), me);
            case MotionEvent.ACTION_CANCEL:
                return onCancel();
        }
        return v.onTouchEvent(me);
    }

    public final int getRecentID() {
        return recentID;
    }

    private int getID(final MotionEvent me) {
        return me.getPointerId(me.getActionIndex());
    }

    public final int getHeldCount() {
        return heldCount;
    }

    public final boolean isHolding() {
        return isHolding;
    }

    public final int getTouchCount() {
        return touchCount;
    }

    public final boolean isDoubleTap() {
        return doubleTap;
    }

    public final boolean canHoldDrag() {
        return heldDrag;
    }

    public final void enableHeldDrag() {
        heldDrag = true;
    }

    public final boolean isSpecialDragging() {
        return isSpecial;
    }

    public final RectF getSpecialArea() {
        return specialArea;
    }

    public final boolean inSpecialArea() {
        return specialArea != null;
    }

    public final boolean isMultiTouch() {
        return isMultiTouch;
    }

    public final boolean hasSpecialDir() {
        return specialDir != Direction.UNDEFINED;
    }

    protected final boolean isSpecialArea(RectF area) {
        return inSpecialArea() && area.equals(specialArea);
    }

    public final boolean inAnArea() {
        return !areas.isEmpty();
    }

    protected final boolean isInArea(RectF area) {
        return inAnArea() && areas.contains(area);
    }

    protected final boolean isInArea(Gesture touch) {
        return touch.getId() == areaID;
    }

    private void clearAltThreads() {
        altHandler.removeCallbacksAndMessages(null);
    }

    protected void clearMainThreads() {
        mainHandler.removeCallbacksAndMessages(null);
    }

    private Gesture addTouch(final Gesture adding) {
        setTouchAt(adding.getId(), adding);
        if (debugging()) {
            Log.e(getClass().getSimpleName(), "addTouch");
        }
        touchCount++;
        return adding;
    }

    private Gesture removeTouch(final Gesture removing) {
        setTouchAt(removing.getId(), null);
        if (debugging()) {
            Log.e(getClass().getSimpleName(), "removeTouch");
        }
        touchCount--;
        return removing;
    }

    private boolean onDown(final int pointerID, final MotionEvent me) {
        if (touchCount <= 0) {
            resetVariables();
        }
        if (debugging()) {
            Log.e(getClass().getSimpleName(), "onDown----------------" + pointerID + " " + touchCount);
        }
        Gesture parent = getTouchAt(pointerID - 1);
        final float x = me.getX(me.getActionIndex());
        final float y = me.getY(me.getActionIndex());
        if (useLatest || (pointerID == 0 && pointerID != touchCount)) {
            parent = getLatestTouch();
            if (useLatest && parent != null && parent.refined()) {
                parent = null;
            }
        }
        recentID = pointerID;
        Gesture latest = preHolding(addTouch(doubleTapping(preDown(new Gesture(
                createTag(x, y, refineX(x), refineY(y)), pointerID,
                refineX(x), refineY(y), me.getEventTime(), parent)))));
        if (parent != null && parent.canLiberate(maxMultiTouch())) {
            latest.setParent(null);
            liberateTouches(parent.getLiberator());
            useLatest = true;
        }
        return true;
    }

    private Gesture preDown(final Gesture onDown) {
        determineSpecial(onDown);
        determineArea(onDown);
        if (canDoubleTap(onDown)) {
            if (debugging()) {
                Log.e(getClass().getSimpleName(), "onDoubleTap");
            }
            setDoubleTapObj(null);
            doubleTap = true;
            return onTap(onDoubleTap(onDown));
        }
        if (debugging()) {
            Log.e(getClass().getSimpleName(), "onTap");
        }
        setDoubleTapObj(getDoubleTapObj(onDown));
        return onTap(onDown);
    }

    private void determineSpecial(final Gesture onDown) {
        if (canSpecialize(onDown)) {
            specialArea = null;
            final RectF[] areas = getSpecialAreas();
            for (final RectF area : areas) {
                if (Calculator.contains(onDown.getDownX(), onDown.getDownY(), area)) {
                    specialArea = area;
                    specialID = onDown.getId();
                    if (debugging()) {
                        Log.e(getClass().getSimpleName(), "canSpecialize " + area.toShortString());
                    }
                    break;
                }
            }
        }
    }

    private void determineArea(final Gesture onDown) {
        if (canBeInAnArea(onDown)) {
            areas.clear();
            final RectF[] rects = getAreas();
            for (final RectF area : rects) {
                if (Calculator.contains(onDown.getDownX(), onDown.getDownY(), area)) {
                    areas.add(area);
                    areaID = onDown.getId();
                }
            }
        }
    }

    private boolean canDoubleTap(final Gesture onDown) {
        if (debugging()) {
            Log.e(getClass().getSimpleName(), "canDoubleTap " + getDoubleTapObj());
        }
        if (getDoubleTapObj() == null) {
            return false;
        }
        final Object latestObj = getDoubleTapObj(onDown);
        return latestObj != null && getDoubleTapObj().equals(latestObj);
    }

    private Gesture doubleTapping(final Gesture onDown) {
        clearAltThreads();
        if (getDoubleTapObj() != null) {
            if (debugging()) {
                Log.e(getClass().getSimpleName(), "doubleTapping");
            }
            altHandler.postDelayed(doubleTapTimer, doubleTapDelay(onDown));
        }
        return onDown;
    }

    private Gesture preHolding(final Gesture onDown) {
        if (canHold(onDown)) {
            clearMainThreads();
            heldID = onDown.getId();
            mainHandler.postDelayed(preHolding, preHoldDelay(onDown));
        }
        return onDown;
    }

    private boolean onMove(final MotionEvent me) {
        if (debugging()) {
            Log.e(getClass().getSimpleName(), "onMove");
        }
        final int historySize = me.getHistorySize();
        final int pointerCount = me.getPointerCount();

        for (int h = 0; h < historySize; h++) {
            for (int i = 0; i < pointerCount; i++) {
                final Gesture touch = getTouchAt(me.getPointerId(i));
                if (touch != null) {
                    touch.addPoint(new FPoint(Gesture.BODY,
                            refineX(me.getHistoricalX(i, h)),
                            refineY(me.getHistoricalY(i, h)),
                            me.getHistoricalEventTime(h)));
                }
            }
        }
        for (int i = 0; i < pointerCount; i++) {
            final Gesture touch = getTouchAt(me.getPointerId(i));
            if (touch != null) {
                touch.addPoint(new FPoint(Gesture.BODY,
                        refineX(me.getX(i)), refineY(me.getY(i)), me.getEventTime()));
            }
        }
        if (!isHolding && specialArea != null && getTouchAt(specialID) != null) {
            if (debugging()) {
                Log.e(getClass().getSimpleName(), "onSpecialDrag " + specialDir);
            }
            onSpecialDrag(getTouchAt(specialID), specialArea);
        } else if (heldDrag && getTouchAt(heldID) != null) {
            if (debugging()) {
                Log.e(getClass().getSimpleName(), "onHeldDrag");
            }
            final Gesture heldTouch = getTouchAt(heldID);
            setTouchAt(heldID, onHeldDrag(heldTouch,
                    makeRawX(heldTouch.getUpX()),
                    makeRawY(heldTouch.getUpY())));
        }
        return true;
    }

    private void onSpecialDrag(Gesture onDrag, final RectF area) {
        final float moveX = onDrag.getUpX();
        final float moveY = onDrag.getUpY();
        isSpecial = !Calculator.contains(moveX, moveY, specialArea);
        if (isSpecial) {
            if (!hasSpecialDir()) {
                specialDir = Calculator.getDirection(onDrag.getDeltaX(), onDrag.getDeltaY(), false);
            }
            setDoubleTapObj(null);
            clearMainThreads();
            final float delta;
            switch (specialDir) {
                case LEFT:
                    delta = area.left - moveX;
                    break;
                case UP:
                    delta = area.top - moveY;
                    break;
                case RIGHT:
                    delta = moveX - area.right;
                    break;
                case DOWN:
                    delta = moveY - area.bottom;
                    break;
                default:
                    return;
            }
            if (inSpecialHold(onDrag, area, specialDir)) {
                if (!ranSpecial) {
                    ranSpecial = true;
                    altHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (getTouchAt(specialID) != null && touchCount > 0) {
                                heldSpecial = onSpecialHold(area);
                            }
                        }
                    }, getSpecialDelay(area));
                }
            } else {
                onSpecialDrag(onDrag, area, delta, specialDir);
                ranSpecial = false;
                clearAltThreads();
            }
        } else {
            onSpecialArea(onDrag, area);
        }
    }

    private boolean onUp(final int pointerID, final MotionEvent me) {
        final Gesture onUp = getTouchAt(pointerID);
        onUp.addPoint(new FPoint(Gesture.TAIL,
                refineX(me.getX(me.getActionIndex())),
                refineY(me.getY(me.getActionIndex())),
                me.getEventTime()));
        if (onUp.getId() == heldID) {
            if (debugging()) {
                Log.e(getClass().getSimpleName(), "onProcessHold");
            }
            clearMainThreads();
            onProcessHold(onUp);
        }
        if (onUp.getId() == specialID) {
            if (debugging()) {
                Log.e(getClass().getSimpleName(), "onProcessSpecial");
            }
            wasSpecial = onProcessSpecial(onUp, specialArea, specialDir, heldSpecial);
        }
        if (!isHolding && !wasSpecial) {
            if (canMultiTouch(onUp)) {
                if (debugging()) {
                    Log.e(getClass().getSimpleName(), "onMultiTouch");
                }
                isMultiTouch = onMultiTouch(getTouches()) || isMultiTouch;
            }
            if (!isMultiTouch && !onUp.refined()) {
                process(refinery(onUp, swipeFactor(), invertHorizontalSwipes()), false);
            }
        }
        removeTouch(onUp);
        if (touchCount == 0) {
            clearMainThreads();
            onRelease(onUp);
        }
        if (debugging()) {
            Log.e(getClass().getSimpleName(), "onUp------------------" + pointerID + " " + touchCount);
        }
        return true;
    }

    protected final void process(final List<Gesture> touches, final boolean force) {
        preprocess();
        for (int i = 0; i < touches.size(); i++) {
            final Gesture touch = touches.get(i);
            if (debugging()) {
                Log.e(getClass().getSimpleName(), touch.getDownX() + " process " + touch.hasParent() + " " + touch.refined());
            }
            if (touch.freed()) {
                if (!touch.isHeld()) {
                    onProcessTouch(touch);
                }
                process(touch.abandonChildren(), force);
            } else if (force) {
                onFreedom(touch);
                process(refinery(touch, swipeFactor(), invertHorizontalSwipes()), true);
            }
            touch.process();
        }
    }

    public final boolean onCancel() {
        if (debugging()) {
            Log.e(getClass().getSimpleName(), "onCancel");
        }
        clearMainThreads();
        clearAltThreads();
        clearTouches();
        hardReset();
        resetVariables();
        return true;
    }

    protected final void liberateTouches(final Gesture liberator) {
        if (liberator == null) {
            return;
        }
        onLiberate(liberator);
        freeTouch(liberator);
        final Gesture root = liberator.getRoot();
        if (debugging()) {
            Log.e(getClass().getSimpleName(), "liberateTouches " + root + " " + liberator);
        }
        if (!root.equals(liberator)) {
            if (root.refined()) {
                process(root.abandonChildren(), true);
            } else {
                root.refine();
                process(refinery(root, swipeFactor(), invertHorizontalSwipes()), true);
            }
        }
    }

    protected final void freeTouches(List<Gesture> touches) {
        for (Gesture touch : touches) {
            freeTouch(touch);
        }
    }

    protected final void freeTouch(Gesture touch) {
        if (touch == null) {
            return;
        }
        onFreedom(touch);
        freeTouches(touch.abandonChildren());
    }

    protected final Direction getMultiTouchDirection(final Gesture[] touches) {
        if (getTouchCount() != 2 || isMultiTouch()) {
            return Direction.UNDEFINED;
        }
        int right = 0;
        int left = 0;
        int down = 0;
        int up = 0;
        int tap = 0;
        Direction dir = Direction.UNDEFINED;
        for (int i = 0; i < getTouchCount(); i++) {
            final Gesture touch = touches[i];
            if (touch == null) {
                return Direction.UNDEFINED;
            }
            final Direction direction = Calculator.getDirection(touch.getDeltaX(), touch.getDeltaY(), invertHorizontalSwipes());
            if (tapChecker(touch, direction, touch.getLength(), 1.0f)) {
                tap++;
                if (tap == maxMultiTouch() && multiTouchDirectionEnabled(Direction.TAP)) {
                    dir = Direction.TAP;
                }
            } else {
                switch (direction) {
                    case RIGHT:
                        right++;
                        if (right == maxMultiTouch() && multiTouchDirectionEnabled(direction)) {
                            dir = direction;
                        }
                        break;
                    case LEFT:
                        left++;
                        if (left == maxMultiTouch() && multiTouchDirectionEnabled(direction)) {
                            dir = direction;
                        }
                        break;
                    case DOWN:
                        down++;
                        if (down == maxMultiTouch() && multiTouchDirectionEnabled(direction)) {
                            dir = direction;
                        }
                        break;
                    case UP:
                        up++;
                        if (up == maxMultiTouch() && multiTouchDirectionEnabled(direction)) {
                            dir = direction;
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        return dir;
    }

    private void resetVariables() {
        onReset();

        recentID = -1;
        touchCount = 0;
        useLatest = false;

        doubleTap = false;

        heldID = -1;
        heldCount = 0;
        heldDrag = false;
        isHolding = false;

        areaID = -1;
        areas.clear();

        specialID = -1;
        specialArea = null;
        isSpecial = false;
        wasSpecial = false;
        ranSpecial = false;
        heldSpecial = false;
        specialDir = Direction.UNDEFINED;

        isMultiTouch = false;
    }

    /**
     * Returns a refined x using the offset provided.
     *
     * @param x Original x coordinate
     * @return Refined x coordinate after applying the offset
     */
    protected final float refineX(float x) {
        return x + xOffset();
    }

    /**
     * Returns a refined y using the offset provided.
     *
     * @param y Original y coordinate
     * @return Refined y coordinate after applying the offset
     */
    protected final float refineY(float y) {
        return y + yOffset();
    }

    /**
     * Returns the original x coordinate after removing the offset.
     *
     * @param x Refined x coordinate
     * @return Original x coordinate after removing the offset
     */
    protected final float makeRawX(float x) {
        return x - xOffset();
    }

    /**
     * Returns the original y coordinate after removing the offset.
     *
     * @param y Refined y coordinate
     * @return Original y coordinate adter removing the offset
     */
    protected final float makeRawY(float y) {
        return y - yOffset();
    }

    /**
     * Method used to update any UI elements that will need to be released
     * during the normal flow of user input.
     *
     * @param touch Gesture object to be freed
     */
    protected abstract void onFreedom(Gesture touch);

    /**
     * Additional method used to update any UI elements that require
     * specific handling. This will typically called alongside onRelease
     * or onFreedom, and during an onHold phase.
     *
     * @param touch Gesture object to be liberated
     */
    protected abstract void onLiberate(Gesture touch);

    /**
     * Creates a tag to be set to every gesture event passed by the user.
     * This should contain any details necessary to perform the correct
     * action based on the user's gesture.
     *
     * @param rawX Coordinate directly passed by the Android OS
     * @param rawY Coordinate directly passed by the Android OS
     * @param x    Coordinate refined by the specified offset
     * @param y    Coordinate refined by the specified offset
     * @return Tag object that will be attached to the gesture event
     */
    protected abstract Object createTag(float rawX, float rawY, float x, float y);

    /**
     * Defines the horizontal offset to be applied to all x coordinates.
     *
     * @return The horizontal offset
     */
    protected abstract float xOffset();

    /**
     * Defins the vertical offset to be applied to all y coordinates.
     *
     * @return The vertical offset
     */
    protected abstract float yOffset();

    /**
     * The current swipe factor used to control the desired lenght of
     * applicable swipes with 1.0f being the default, < 1.0f being shorter
     * swipes, > 1.0f being longer swipes.
     *
     * @return A ratio to apply to the minimum swipe length desired
     */
    protected abstract float swipeFactor();

    /**
     * Inverts the gesture handling.
     *
     * @return True will turn RIGHT directed swipes to LEFT
     */
    protected abstract boolean invertHorizontalSwipes();

    /**
     * Resets all elements being used either after a input session has ended,
     * or a gesture event that invalidates the current session.
     */
    protected abstract void onReset();

    /**
     * Called when the listener has determined a hold action is being performed.
     *
     * @param touch The gesture used to perform the hold
     */
    protected abstract void onHold(Gesture touch);

    /**
     * The hold delay used to determine when onHold can be called.
     *
     * @param touch The gesture used to perform the hold
     * @return Delay duration to be applied before onHold is called.
     */
    protected abstract long holdDelay(Gesture touch);

    /**
     * Called when the listener begins to define some touch details and
     * screen the gesture to see if it's viable to perform a hold event.
     *
     * @param touch The gesture used to perform the hold
     * @return The updated gesture that will be used to perform a hold event.
     */
    protected abstract Gesture onPreHold(Gesture touch);

    /**
     * The hold delay used to determine when onPreHold can be called.
     *
     * @param touch The gesture used to perform the hold
     * @return Delay duration to be applied before onPreHold is called.
     */
    protected abstract long preHoldDelay(Gesture touch);

    /**
     * The hold delay used after an onHold has been performed and
     * repeatHold has returned True
     *
     * @param touch The gesture used to perform the hold
     * @return Delay duration to be applied before onHold is called again.
     */
    protected abstract long postHoldDelay(Gesture touch);

    /**
     * The double tap delay that determines the speed in which one
     * must perform a double tap under.
     *
     * @param touch The gesture used to perform a double tap
     * @return Delay duration that the user must beat in order to perform
     * a double tap.
     */
    protected abstract long doubleTapDelay(Gesture touch);

    /**
     * Determines whether or not the current touch can perform a hold event.
     *
     * @param touch The current gesture being analyzed
     * @return True if the gesture can call onHold
     */
    protected abstract boolean canHold(Gesture touch);

    /**
     * Determines whether or not the held touch's action can be repeated.
     *
     * @param touch The gesture used to perform the hold
     * @return True if the gesture can call onHold again
     */
    protected abstract boolean repeatHold(Gesture touch);

    /**
     * Called once a held touch has been performed (released).
     *
     * @param touch The gesture used to perform the hold
     * @return True if the gesture was handled and can be destroyed
     */
    protected abstract boolean onProcessHold(Gesture touch);

    /**
     * Determines whether or not the touch can perform a special action.
     *
     * @param touch The current gesture being analyzed
     * @return True if the gesture can perform a special action
     */
    protected abstract boolean canSpecialize(Gesture touch);

    /**
     * Determines whether or not the touch can perform an action in a
     * specific area.
     *
     * @param touch The current gesture being analyzed
     * @return True if the gesture is allowed in a specific area
     */
    protected abstract boolean canBeInAnArea(Gesture touch);

    /**
     * Returns the double tap object being tracked.
     *
     * @param touch The current gesture being analyzed
     * @return The double tap object being tracked
     */
    protected abstract Object getDoubleTapObj(Gesture touch);

    /**
     * One of the earliest methods to be called in the on down
     * event area. This should be the location to update any tag
     * details or UI elements necessary before the user's gesture
     * is fully carried out.
     *
     * @param touch The current gesture being analyzed
     * @return The updated gesture after analyzation
     */
    protected abstract Gesture onTap(Gesture touch);

    /**
     * Called when the user has successfully performed a double tap.
     *
     * @param touch The gesture used to perform a double tap
     * @return The updated gesture after analyzation
     */
    protected abstract Gesture onDoubleTap(Gesture touch);

    /**
     * Final method called when a gesture is being released by the user.
     *
     * @param touch The gesture to be released
     * @return The udpated gesture after release
     */
    protected abstract Gesture onRelease(Gesture touch);

    /**
     * Provides the special areas used for complicated gestures.
     * Usually gestures that require one to get from point A -> B.
     *
     * @return An array of areas used for special gestures
     */
    protected abstract RectF[] getSpecialAreas();

    /**
     * Provides the areas used for simple gestures.
     * Usually gestures that simply require the user to start
     * (on down) an action in the specified area.
     *
     * @return An array of areas used for simple gestures
     */
    protected abstract RectF[] getAreas();

    /**
     * Returns the delay used for special area gestures.
     *
     * @param area The area where the gesture is being performed
     * @return Delay used to perform special actions
     */
    protected abstract long getSpecialDelay(final RectF area);

    /**
     * Called when a special action is being held.
     *
     * @param area The area where the gesture is being performed
     * @return True if the area can perform a held action
     */
    protected abstract boolean onSpecialHold(final RectF area);

    /**
     * Called when a tap n hold is being performed.
     *
     * @param touch The current gesture being dragged
     * @param rawX  Coordinate directly passed by the Android OS
     * @param rawY  Coordinate directly passed by the Android OS
     * @return The updated gesture object
     */
    protected abstract Gesture onHeldDrag(Gesture touch, final float rawX, final float rawY);

    /**
     * Called when a gesture is in a special area.
     *
     * @param touch The current gesture being analyzed
     * @param area  The area where the gesture is being performed
     */
    protected abstract void onSpecialArea(Gesture touch, final RectF area);

    /**
     * Called when a gesture is in the middle of performing a special hold event.
     *
     * @param touch The current gesture being analyzed
     * @param area  The area where the gesture is being performed
     * @param dir   The direction of the gesture
     * @return True if the gesture is successfully performing a hold event
     */
    protected abstract boolean inSpecialHold(Gesture touch, final RectF area, final Direction dir);

    /**
     * Called when the user has begun to perform a special gesture event.
     *
     * @param touch The current gesture being analyzed
     * @param area  The area where the gesture is being performed
     * @param delta The change in length from the onDown to the latest onMove
     * @param dir   The direction of the gesture
     * @return True if the gesture has potential to perform a special action
     */
    protected abstract boolean onSpecialDrag(Gesture touch, final RectF area, final float delta, final Direction dir);

    /**
     * Called once a special action can be/has been performed
     *
     * @param touch   The gesture used to perform a special drag
     * @param area    The area where the gesture was performed
     * @param dir     The direction of the gesture
     * @param wasHeld True if the gesture performed a special hold action
     * @return True if the gesture was handled and can be destroyed
     */
    protected abstract boolean onProcessSpecial(Gesture touch, final RectF area, final Direction dir, final boolean wasHeld);

    /**
     * Determines whether the current gesture can produce a multitouch event.
     *
     * @param touch The current gesture being analyzed
     * @return True if the gesture can attribute itself to a multitouch event
     */
    protected abstract boolean canMultiTouch(Gesture touch);

    /**
     * Called when a multitouch event occurs.
     *
     * @param touches The current array of touches up for multitouch consideration
     * @return True if the gesture was handled and can be destroyed
     */
    protected abstract boolean onMultiTouch(Gesture[] touches);

    /**
     * Called before a gesture is processed. Should be used for data tracking or
     * variable initialization that will be found in the OnProcessTouch() method.
     */
    protected abstract void preprocess();

    /**
     * Called when the gesture has not met any special exceptions and needs to be
     * processed then destroyed. This may be the last time you see this gesture
     * so be sure to take a picture, give it a hug, and send it on it's way.
     *
     * @param touch The current gesture being processed
     */
    protected abstract void onProcessTouch(Gesture touch);

    /**
     * Emergency call to reset all variables and invalidate any user input currently
     * being performed. Typically only OnCancel will call this method.
     */
    public abstract void hardReset();

    /**
     * Determines the max number of fingers to be handled in multitouch events.
     *
     * @return The number of gestures allowed to perform a multitouch event
     */
    protected abstract int maxMultiTouch();

    /**
     * Determines whether the passed direction can perform a multitouch action
     * or event.
     *
     * @param dir The direction of the gesture
     * @return True if the direction can perform a multitouch action
     */
    protected abstract boolean multiTouchDirectionEnabled(Direction dir);

}
