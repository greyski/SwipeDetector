package com.fleksy.swipedetector.tool;

import android.graphics.Rect;
import android.graphics.RectF;

import com.fleksy.swipedetector.model.Direction;
import com.fleksy.swipedetector.model.FPoint;

import java.util.List;

/**
 * Created by grey on 3/17/16.
 */
public final class Calculator {

    public static float calcStandardDeviation(List<Double> numbers) {

        double average;
        double sum = 0;
        int ignore = 0;

        for (double num : numbers) {
            if (num > 0) sum += num;
            else ignore++;
        }
        average = calcAverage(sum, (numbers.size() - ignore));

        sum = 0;
        for (double num : numbers) {
            if (num >= 0) sum += Math.pow(num - average, 2);
        }

        return (float) Math.sqrt(sum / (numbers.size() - ignore));
    }

    /*
     * 		-B +- sqrt(B^2 - 4AC)
     * X =  ---------------------
     * 				2A
     */
    public static double calcQuadraticEquation(double a, double b, double c, boolean negative) {
        double root = Math.sqrt(Math.pow(b, 2) - (4 * a * c));
        root = negative ? -root : root;
        double top = -b + root;
        return top / (2 * a);
    }

    public static float calcDeltaVelocity(List<Double> numbers) {

        float deltaV = 0;

        for (int i = 0; i < numbers.size() - 1; i++) {
            deltaV += numbers.get(i + 1) - numbers.get(i);
        }

        return deltaV;
    }

    public static float calcTotal(List<Double> nums) {

        float total = 0;

        for (int i = 0; i < nums.size(); i++) {

            total += nums.get(i);
        }

        return total;
    }

    public static double calcPointLength(FPoint a, FPoint b) {
        double ln = 0;

        float dX = Calculator.calcDeltaF(b.getX(), a.getX()); //change in x
        float dY = Calculator.calcDeltaF(b.getY(), a.getY()); //change in y

        ln = Calculator.calcLength(dX, dY); //get length between points
        return ln;
    }

    public static double getDeltaRad(double c, double p) {
        double d = calcDeltaD(c, p); //change in angle from previous
        return (deltaRadians(d)); //change in angle refactored to radians
    }

    public static double calcAverage(double total, int count) {
        return (total / count);
    }

    public static float calcDeltaF(float a, float b) {
        return (a - b);
    }

    public static double calcDeltaD(double a, double b) {
        return (a - b);
    }

    public static long calcDeltaL(long a, long b) {
        return (a - b);
    }

    public static double calcLength(float deltaX, float deltaY) {
        return (Math.sqrt(((Math.pow(deltaX, 2)) + (Math.pow(deltaY, 2)))));
    }

    public static double findRads(float x, float y) {
        return ((Math.atan2(y, x)));
    }

    public static double deltaRadians(double r) {
        return (Math.abs(Math.atan2((Math.sin(r)), (Math.cos(r)))));
    }

    public static float getDeltaVelocity(List<Double> nums, float ghostSpeedRatio) {
        float d = 0;
        /* Creates imaginary speed for first number and removes it from the list */
        nums.add(0, (nums.get(0) * ghostSpeedRatio));
        d = Calculator.calcDeltaVelocity(nums);
        return d;
    }

    public static boolean contains(final int x, final int y, final Rect... bounds) {
        if (bounds == null) {
            return false;
        }
        for (final Rect area : bounds) {
            if (area.left <= x && area.top <= y && area.right >= x && area.bottom >= y) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(final float x, final float y, final RectF... bounds) {
        if (bounds == null) {
            return false;
        }
        for (final RectF area : bounds) {
            if (area.left <= x && area.top <= y && area.right >= x && area.bottom >= y) {
                return true;
            }
        }
        return false;
    }

    public static Direction getDirection(final float deltaX, final float deltaY,
                                         final boolean invertHorizontal) {
        if (Math.abs(deltaX) + Math.abs(deltaY) == 0) {
            return Direction.TAP;
        }

        if (Math.abs(deltaX) > Math.abs(deltaY)) {
            if (invertHorizontal) {
                return (deltaX < 0) ? Direction.LEFT : Direction.RIGHT;
            } else {
                return (deltaX < 0) ? Direction.RIGHT : Direction.LEFT;
            }
        } else {
            return (deltaY < 0) ? Direction.DOWN : Direction.UP;
        }
    }

    public static FPoint findFurthestPoint(final FPoint start, FPoint end, final List<FPoint> points) {
        double max = 0;
        for (FPoint p : points) { // Find furthest point from Start
            final double ln = Calculator.calcPointLength(start, p);
            if (ln > max) {
                max = ln;
                end = p;
            }
        }
        return end;
    }

}
