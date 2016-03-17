package com.fleksy.swipedetector;

import com.fleksy.swipedetector.model.Direction;

/**
 * Created by grey on 3/17/16.
 */
abstract class ProcessLogicProvider {

    private float viewWidth;
    private float viewHeight;

    private final float pixelSize;
    private StringBuilder out = new StringBuilder();

    public ProcessLogicProvider(float pixelSize) {
        this.pixelSize = pixelSize;
    }

    protected final float getPixel() {
        return pixelSize;
    }

    protected final float getWidth() {
        return viewWidth;
    }

    protected final float getHeight() {
        return viewHeight;
    }

    /**
     * Storing the provided touch listening area's width and height
     *
     * @param width  of area
     * @param height of area
     */
    protected final void updateDimensions(float width, float height) {
        viewWidth = width;
        viewHeight = height;
    }

    /**
     * Returns the maximum allowed distance between points. The length in which points can "jump" to.
     * Extreme "jumps" in length hint towards artificial gestures.
     *
     * @return desired jump length,
     * or the area's width divided by 8 (assumed best dimension for Fleksy keyboard)
     */
    protected float getJumpLength() {
        return viewWidth / 8;
    }

    /**
     * The assumed minimum length where phantom (or artificial) swipes will not go past. Although
     * this is never 100% accurate as touch detection hardware varies from device, we've found that
     * this maximum length of a fake gesture to prove useful especially when providing leeway for
     * vertical gestures as they are rarely fabricated by the system.
     *
     * @return assumed nice length (or maximum artificial swipe length),
     * or the area's height divided by 8 (assumed best dimension for Fleksy keyboard)
     */
    protected float getNiceLength() {
        return viewHeight / 8;
    }

    /**
     * A calculated average length that may span between points in a gesture. This a rough average
     * that (as always) varies from device to device, but we found the following (magic number)
     * returned result to be the best value.
     *
     * @return calculated average length between two points in a gesture,
     * or the area's height divided by 40 (assumed best dimension for Fleksy keyboard)
     */
    protected float getAverageLength() {
        return viewHeight / 40;
    }

    /**
     * The maximum number of points that are allowed to have an unaltered change in degrees.
     * It just isn't humanly possible to create such a perfect swipe, with unchanging degrees.
     *
     * @return determined maximum allotted number of points that may have the same angle. Default 3.
     */
    protected float getMaxConcurrentRads() {
        return 3;
    }

    /**
     * Key value that determines whether a gesture is considered artificial or not. Greater than
     * the value means the gesture is artificial, less than the value is human.
     *
     * @return the limiting value that decides whether the swipe is artificial. Default 4.
     */
    protected float getMaxWeight() {
        return 4;
    }

    /**
     * Decided average number of degrees over an entire gesture that can be in error.
     *
     * @return 90 degrees in radians.
     */
    protected float getAvgRadianError() {
        return 1.57f;
    }

    /**
     * Decided maximum number of degrees over an entire gesture that can be in error.
     *
     * @return 180 degrees in radians.
     */
    protected float getMaxRadianError() {
        return 3.14f;
    }

    /**
     * Minimum allowed deviation in degree changes between points.
     *
     * @return 10 degrees in radians.
     */
    protected float getMinChangeInRads() {
        return 0.174f;
    }

    /**
     * Average allowed deviation in degree changes between points
     *
     * @return 45 degrees in radians.
     */
    protected float getAvgChangeInRads() {
        return 0.785f;
    }

    /**
     * Maximum limit of standard deviation between points on a gesture.
     *
     * @return Defaults to 16 (Determined through testing on the Fleksy Keyboard)
     */
    protected float getMaxStandardDeviation() {
        return 16;
    }

    /**
     * Ending points on a gesture tend to become clustered across both human and artificial
     * swipes. We reduce the average length we expect between points as they should be below
     * our given average length value from getAverageLength()
     *
     * @return The ratio (less than 1.0f) that will reduce the size of the average length expected
     * of points towards the end of a gesture. Default 0.9f
     */
    protected float getAvgEndLengthRatio() {
        return 0.9f;
    }

    /**
     * An assumed speed for the beginning of a gesture when calculating the velocity.
     *
     * @return Defaults to 0.7f (Determined through testing on the Fleksy Keyboard)
     */
    protected float getAvgSpeedForVelocity() {
        return 0.7f;
    }

    /**
     * Resets all variables to a clean state
     */
    protected void reset() {
        out.setLength(0);
    }

    /**
     * Enables debug mode, which will display printed messages describing the gesture detection
     *
     * @return True is debugging is enabled, False is what it's set to by default
     */
    protected boolean debugging() {
        return false;
    }

    /**
     * Called for debugging purposes after a gesture has gone through processing
     *
     * @return String containing all messages and details during the gesture analyzing process.
     */
    protected final String getRawOutput() {
        return out.toString();
    }

    protected final void printData(int state, double rad, double dRad, float x, float y, double l, float t, Direction dir) { //PRINT DATA
        if (rad < 0) {
            rad += 6.28; // Provides a positive radian for easier assessment
        }
        if (debugging()) {
            println(state + " d" + (Math.round((rad) * 100) / 100.0) + "b [" + (Math.round((dRad) * 1000) / 1000.0)
                    + "] <" + Math.round(x) + ", " + Math.round(y) + "> l: " + Math.round((l) * 1000) / 1000.0 + " Dir: " + dir);
        }
    }

    protected final void println() {
        out.append("\n");
    }

    protected final void println(String in) {
        out.append(in);
        println();
    }

}
