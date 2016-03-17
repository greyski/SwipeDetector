package com.fleksy.swipedetector;

import com.fleksy.swipedetector.model.Direction;
import com.fleksy.swipedetector.model.FPoint;
import com.fleksy.swipedetector.model.Gesture;
import com.fleksy.swipedetector.tool.Calculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by grey on 3/17/16.
 */
abstract class SwipeDetector extends ProcessLogicProvider {

    private boolean isPhantomSwipe; // Final flag setting gesture's authenticity
    private float weight; // Main Counter For Number of Bad Flags (The Weight)

    private FPoint p1;
    private FPoint p2;
    private int perfect;
    private int directions;
    private long deltaTime;
    private double eRad;
    private double prevLn;
    private double totalLn;
    private double prevRad;
    private double prevDelta;
    private float phantomLn;
    private Direction prevDir;
    private Direction direction;
    private boolean badAngle;
    private boolean badDirections;
    private List<Double> lengths = new ArrayList<>();

    /**
     * The brains that parse a gesture, analyze every point, find faults, count faults and
     * conclude as to whether the gesture made was artificial or human. Welcome to Magic Number land!
     * Where there was totally research involved, but it may not work best with your custom UI.
     * Final magic numbers listed above should work fine for all UI
     *
     * @param pixelSize 1 DPI typically specified in the dimens.xml under /values
     */
    public SwipeDetector(float pixelSize) {
        super(pixelSize);
    }

    /**
     * Called for debugging purposes for immediate analyzing of a gesture and it's output.
     *
     * @param swipe Gesture that will be immediately processed
     * @return The String containing all messages and details during the gesture analyzing process
     */
    protected final String getData(Gesture swipe) {
        check(swipe);
        return getRawOutput();
    }

    /**
     * Check() :: This is the main calculator that iterates through the
     * FPoints of a Touch object, both assessing and retaining data from
     * the change in degrees to the length. This is the main hub for
     * detecting phantom swipes.
     */
    protected Gesture check(Gesture swipe) {            /** MAIN PHANTOM SWIPE DESTROYER METHOD **/

        reset();                                                    //Resets all values for new process

        if (debugging()) {                                          //Adds a new line for clarity
            println();
        }

        List<FPoint> ps = swipe.getPoints();

        if (badFPoint(ps.size())) {                                //Only one FPoint or less should not be here
            if (debugging()) println("BAD SWIPE/BAD FPoints!");
            return swipe;
        }

        deltaTime = (swipe.getLastPoint().getTime() - swipe.getFirstPoint().getTime()); //Calc total time taken

        for (int i = 0; i < ps.size() - 1; i++) {                   //Loop through FPoints in pairs

            p1 = ps.get(i);                                         //Current FPoint
            p2 = ps.get(i + 1);                                     //FPoint After

            float dX = Calculator.calcDeltaF(p2.getX(), p1.getX());     //change in x
            float dY = Calculator.calcDeltaF(p2.getY(), p1.getY());     //change in y

            double length = Calculator.calcLength(dX, dY);              //get length between FPoints

            double currRad = Calculator.findRads(dX, -dY);              //current angle in radians, negative Y for inversion

            double dRad = Calculator.getDeltaRad(currRad, prevRad);     //calculate change in radians

            if (!isHolding(length, dRad)) {                         //ignores holding FPoints, if held p2 is set to HEAD
                lengths.add(length);                                //add length
                incrementRadError(dRad, p1.getState(), length);     //calculate radian error based on MIN_DELTA_ERR
                bodyScan(p1.getState(), dRad, length, prevLn);    /*** IMPORTANT **/
            }

            final Direction dir = Calculator.getDirection(
                    Calculator.calcDeltaF(p1.getX(), p2.getX()),
                    Calculator.calcDeltaF(p1.getY(), p2.getY()),
                    false);

            badDirections(dir, p2.getX(), p2.getY());
            /** PRINT FPoint DATA **/
            if (p1.getState() == Gesture.HEAD) {
                printData(p1.getState(), prevRad, prevDelta, p1.getX(), p1.getY(), prevLn, p1.getTime(), Direction.UNDEFINED);
            }
            if (p2.getState() != Gesture.HEAD) {
                printData(i + 1, currRad, dRad, p2.getX(), p2.getY(), length, p2.getTime(), dir);
            }

            setPrevious(length, currRad, dRad, dir);                //sets previous values

        }

        finalScan(swipe, eRad);                                /*** IMPORTANT **/

        swipe.setPhantom(isPhantomSwipe);

        return swipe;
    }

    /**
     * BadFPoint() :: Checks for Touches that contain only
     * two or less FPoints. These are disregarded as they are clearly taps.
     */
    private boolean badFPoint(int size) {                        //BAD FPoint (BAD)
        if (size <= 2) {
            isPhantomSwipe = true;
            return true;
        }                                                        //It's prolly a tap, Interceptor will handle this
        return false;
    }

    /**
     * IncrementRadError() :: Increments the eRad value that holds the total number
     * of degree shifts between FPoints. For example, say the previous angle between
     * two FPoints was 45, and now the current degree between the following two
     * FPoints is 60. The degree error would be 15. The MINIMUM_CHANGE_IN_ERR allowed
     * is shown above in order to disregard insubstantial degree shifts.
     *
     * @param deltaRad Change in radians
     * @param s1       State of FPoint (HEAD, BODY, TAIL)
     * @param ln       Length between two given FPoints
     */
    private void incrementRadError(double deltaRad, int s1, double ln) { //GET RAD ERROR
        if (deltaRad > getMinChangeInRads() && s1 != Gesture.HEAD && ln > 0) {
            eRad += deltaRad;
            if (debugging()) {
                println("Delta Err: " + eRad);
            }
        }
    }

    /**
     * IsHolding() :: When a user is holding their finger, the FPoints tend to move
     * very little, if at all. We need to ignore that BS cause it could really screw things up.
     * However, we keep track of the number of lengths from the HEAD that have no degree shift.
     * Why? Because it's suspicious and robotic.
     *
     * @param ln       Length between two given FPoints
     * @param deltaRad Change in radians/Delta radians
     * @return Returns true if the two FPoints provide no length or negative change in radians
     */
    private boolean isHolding(double ln, double deltaRad) {    //IS HOLDING?
        deltaRad = Math.round((deltaRad) * 1000) / 1000.0;
        /** No degree change and prev hasn't moved */
        if (p1.getState() == Gesture.HEAD && deltaRad <= 0) {
            if (ln > 0) {
                lengths.add(ln);
            }                    //Add to length total
            else if (debugging()) {
                println("EARLY ZERO LENGTH");
            }
            phantomLn += ln;                                    //weird beginning length
            p2.setState(Gesture.HEAD);                                    //not moving
            return true;
        }
        /** No Lengths */
        else if (ln == 0) {                                        //There can sometimes be two FPoints at the end
            if (debugging()) println("LATE ZERO LENGTH");
            return true;
        }
        return false;
    }

    /**
     * BadDirections() :: When the change in the direction between points occurs we
     * track to make sure it doesn't change too often/drastically.
     *
     * @param dir Current direction
     * @param x   Latest X coordinate
     * @param y   Latest Y coordinate
     */
    private void badDirections(Direction dir, float x, float y) {
        if (x <= 0 || x >= getWidth()) {
            return;
        }
        if (y <= 0 || y >= getHeight()) {
            return;
        }
        if (dir == Direction.UNDEFINED) {
            return;
        }
        if (dir != prevDir) {
            directions = 0;
        }
        if (dir != direction) {
            directions++;
            if (directions == 3) {
                if (direction != Direction.UNDEFINED) {
                    weight++;
                    badDirections = true;
                    if (debugging()) println("BAD DIRECTIONS");
                }
                direction = dir;
                if (debugging()) println("DIRECTION: " + direction);
            }
        } else {
            directions = 0;
        }
    }

    /**
     * BodyScan() :: Main area where we check for errors while we loop through the FPoints.
     *
     * @param p1       First FPoint
     * @param deltaRad Change in radians
     * @param ln       Length between two FPoints
     * @param prevLn   Length between the two previous FPoints
     */
    private void bodyScan(int p1, double deltaRad, double ln, double prevLn) { /*** BODY SCAN **/
        if (p1 == Gesture.BODY) {

            /** No change in degree for 3 FPoints */
            tooPerfect(deltaRad);

            /** Dramatic change in angle */
            crazyAngle(deltaRad, ln, prevLn);

//            //Apparently this is no longer necessary as we can't reproduce phantom
//            //swipes than can be prevented with this logic.
//            /** Change in length and actual length are too oddly small */
//            tinyLengths(ln, (ln - prevLn));

        }
    }

    /**
     * TooPerfect() :: If a swipe has three lengths that have the same angle, it's a phantom swipe.
     *
     * @param deltaRad Change in radians
     */
    private void tooPerfect(double deltaRad) {                    //TOO PERFECT (+0.5f)
        if (deltaRad == 0) {                                    //No change in degrees
            perfect++;
            if (perfect == getMaxConcurrentRads()) {
                weight += 0.5f;
                if (debugging()) {
                    println(".:*PERFECT COMBO*:. " + weight);
                }
            }
        } else {
            perfect = 0;
        } //C-C-C-C-COMBO BREAKUR!
    }

    /**
     * CrazyAngle() :: More than a MID_DELTA_ERR degree change in angle within a good sized length
     * all of a sudden? That's bull!
     *
     * @param deltaRad Change in radians
     * @param ln       Length between two FPoints
     */
    private void crazyAngle(double deltaRad, double ln, double pln) { //CRAZY ANGLE (+1)
        if (deltaRad >= getAvgChangeInRads() && ln > getPixel() && pln > getPixel()) {
            badAngle = !badAngle;
            if (debugging()) {
                println("OVER " + Math.round(Math.toDegrees(getAvgRadianError() * 100) / 100.0) + " ANGLE");
            }
            if (!badAngle) {
                eRad = 0;
            }
        } else if (badAngle) {
            weight++;
            badAngle = false;
            if (debugging()) {
                println("CRAZY ANGLE " + weight);
            }
        }
    }

//    //Apparently this is no longer necessary as we can't reproduce phantom
//    //swipes than can be prevented with this logic.
//    /**
//     * TinyLengths() :: For every length that is less than MIN_LENGTH that is further than
//     * halfway down the Touch, as well as having very little change in length since the previous
//     * we increment a variable SMALLS where we count how often this happens.
//     *
//     * @param ln      Length between two FPoints
//     * @param deltaLn Change in length between the current length and previous
//     */
//    private void tinyLengths(double ln, double deltaLn) { //TINY LENGTHS (SMALLS+2)
//        if (deltaLn < pixelSize && ln < pixelSize && ln > 0) {
//            if (smallChain) {  //Smalls are typically chained together
//                smalls++; //DIRECTLY ADDED TO WEIGHT
//                if (debug) {
//                    println("SMALL LENGTHS " + smalls);
//                }
//            } else {
//                smallChain = true;
//            }
//        } else {
//            smallChain = false;
//        }
//    }

    /**
     * FinalScan() :: Main area where all variables are calculated from the loop and we now
     * apply them to the included methods
     *
     * @param swipe Current touch
     * @param err   Total count of erroneous change in radians
     */
    private void finalScan(Gesture swipe, double err) { /** FINAL SCAN **/

        totalLn = Calculator.calcTotal(lengths); /** Calculates total length */

        if (lengths.size() > 2) { /** Ignores small swipes */

            float deltaV = Calculator.getDeltaVelocity(lengths, getAvgSpeedForVelocity());
            double avg = Calculator.calcAverage(totalLn, lengths.size());
            double stdDev = Calculator.calcStandardDeviation(lengths);

//            //Apparently this is no longer necessary as we can't reproduce phantom
//            //swipes than can be prevented with this logic.
//            /** Small Average removes small length errors */
//            yerKillingMeSmalls(avg, err, total);

            /** Check if radian errors are high */
            checkErrors(err, avg);

            /** Odd jump, larger than 2xAverage */
            isJumping(lengths, avg);

            /** Phantom Lengths make majority */
            phantomLengths(err);//total);

//					.:UNRELIABLE:.
//			/** Irregular velocity change */
//			checkDeltaVelocity(deltaV); 

            /** Stupid wrong number of FPoints in end */
            badEnding(avg);

            /** Bad angles and weird length changes */
            terribleSwipe(stdDev);

            /** Good Downward Vertical Direction */
            verticalSave(swipe.getRadian(), totalLn);

            /** Phantom swipes are typically less than niceLength long, but weight will identify if they're shit */
            label(totalLn, err, avg);

//					.:UNRELIABLE:.
//			/** The longer the time, the better the swipes */
//			goodTimes();

            /** PRINT RETRIEVED DATA **/
            if (debugging()) {
                println("DeltaV: " + deltaV + " Length: " + totalLn);
                println("N Std Dev: " + stdDev + " Avg L: " + avg);
            } //PRINT

        } else {
            /** If the one FPoint is closer to the end than the beginning,
             bad news small swipe! */
            tooTiny(swipe.getRadian(), totalLn);
        }

        if (debugging()) { /** PRINT DATA **/
            println("Deg Err: " + (Math.round((err) * 1000) / 1000.0));
            println("Time Taken: " + deltaTime);
        }

    }

//    //Apparently this is no longer necessary as we can't reproduce phantom
//    //swipes than can be prevented with this logic.
//    /**
//     * YerKillingMeSmalls() :: Should the average length of the Touch's lengths be less than the
//     * MINIMUM_AVERAGE, while at the same time containing instances of oddly small lengths.
//     * Increment weight of WTF by the number of small lengths calculated in SMALLS.
//     */
//    private void yerKillingMeSmalls(double avg, double e, double ln) { //YERKILLINGMESMALLS (+smalls)
//        if ((avg > logicMap.get(opNames[11]) || e > logicMap.get(opNames[5])) || ln < logicMap.get(opNames[13])) {
//            //Too many small lengths for a large swipe
//            if (smalls > 0) {
//                weight += smalls;
//                if (debug) {
//                    println("KILLIN ME SMALLS " + weight);
//                }
//            }
//        }
//    }

    /**
     * IsJumping() :: Checks for oddly large length between FPoints that is larger than the
     * Touch's average length multiplied by a JUMP_RATIO
     */
    private void isJumping(List<Double> nums, double avg) { //IS JUMPING (*2) //WAS +1
        for (int i = 0; i < nums.size(); i++) {
            if (nums.get(i) > getJumpLength()) { //Odd jumps in length
                weight++;
                if (debugging()) {
                    println("JUMPER " + weight);
                }
            }
        }
    }

    /**
     * CheckErrors() :: Increments the weight based on how bad the overall change in rads
     * was over the span of the Touch. If it's especially bad, then we increase the weight to
     * max as no mortal being would do such a terrible thing to a poor defenseless swipe.
     */
    private void checkErrors(double e, double avg) { //CHECK ERRORS (+1 || +(e - MAX_ERR))
        //That was terrible, really awful
        if (e > getAvgRadianError()) {
            weight++;
            if (debugging()) {
                println("ERRORS " + weight);
            }
            if (e > getMaxRadianError() && avg > getAverageLength()) {
                weight += e - getMaxRadianError();
                weight = Math.round(weight * 10) / 10.0f;
                if (debugging()) {
                    println("MAX ERRORS " + weight);
                }
            }
        }
    }

    /**
     * PhantomLengths() :: On the rare occasion that a phantom swipe makes these obnoxious
     * phantom lengths that span the majority of the swipe, we increment the weight. Again,
     * these phantom lengths are based on the number of lengths that occured without ANY
     * change in degrees. It's weird and robotic.
     */
    private void phantomLengths(double e) {//double totalLength) { //PHANTOM LINE (+PHANLN)
        if (phantomLn > 0) {
            if (e > getAvgRadianError()) {
                weight++;
                if (e > getMaxRadianError()) {
                    weight += phantomLn;
                    if (debugging()) {
                        println("PHANTOM LINE! " + weight);
                    }
                } else {
                    if (debugging()) {
                        println("PHANTOM LINE? " + weight);
                    }
                }
            }
        }
    }

//	.:CODE REMOVED : UNRELIABLE:.
//	/** CheckDotProduct() :: After instantiating the first FPoint with a GHOST_VELOCITY of
//	 * less than the next FPoint, we calculate to see whether the velocity was positive or
//	 * less than 1. If it is less than 1 then the Touch was making back an forth progress 
//	 * to it's TAIL.
//	 */
//	private void checkDeltaVelocity(float deltaVelocity) { //CHECK DELTA VELOCITY (+0.5f)
//		if(deltaVelocity < 1) { //Bad Change in Velocity
//			weight += 0.5f;
//			if(debug) append( "BAD DOT PRODUCT " + weight);
//		}
//	}

    /**
     * VerticalSave() :: If downward direction, check if the beginning and end location
     * are within a reasonable bounds of
     */
    private boolean verticalSave(double rad, double ln) { //VERTICAL SAVE (weight/1.5)
        if (((rad > 4.3f && rad < 5.1f) || (rad > 0.8f && rad < 2.4f))
                && !badDirections && ln > getNiceLength()) {
            weight /= 1.5;
            if (debugging()) {
                println("VERTICAL SAVE! " + weight);
            }
            return true;
        }
        return false;
    }

    /**
     * BadEnding() :: When the last two FPoints of a swipe, when added together, do not equal
     * the AVERAGE_LENGTH of the Touch. This is suspicious and increments the weight.
     * Sadly there is no GoodEnding in the world of swipes. :(
     */
    private void badEnding(double avg) { //BAD ENDING (+0.5f)
        int index = lengths.size() - 1;
        int sub = 1;
        //Last two FPoints don't meet average length x AVG_RATIO
        if ((lengths.get(index) + lengths.get(index - sub)) <= avg * getAvgEndLengthRatio()) {
            weight += 0.5f;
            if (debugging()) {
                println("BAD ENDING " + weight);
            }
        }
    }

    /**
     * TerribleSwipe() :: The standard deviation is larger than the set
     * MAXIMUM_ALLOTMENT_OF_DEVIATION, which is weird and increments the weight of the
     * Touch.
     */
    private void terribleSwipe(double std) { //TERRIBLE SWIPE (+1)
        if (std >= getMaxStandardDeviation()) {
            weight++;
            if (debugging()) {
                println("STD " + Math.round(std) + " TERRIBLE SWIPE " + weight);
            }
        }
    }

    /**
     * TooTiny() :: For Touches that are only 3 FPoints. If the middle FPoint is closer
     * to the TAIL, then that's bullshit and defines the Touch as a phantom swipe. I mean,
     * these swipes shouldn't even exist to be quite honest.
     */
    private void tooTiny(double rad, double ln) { //TOO TINY (BAD)
        if (lengths.size() > 0) {
            float totalL = 0;
            for (double length : lengths) {
                totalL += length;
            }
            //If majority of FPoints close to end
            if (lengths.get(lengths.size() - 1) < (totalL * 0.25f)) {
                if (!verticalSave(rad, ln)) {
                    // Phantom vertical swipes are rare,
                    // So we minimize how aggressively we check them
                    isPhantomSwipe = true;
                    if (debugging()) {
                        println("TINY ENDING");
                    }
                }
            }
        }
    }

    /**
     * Label() :: The Final label checks over all the main defining criterea of a good
     * swipe. If the total weight is less than the MAX_WTF alloted. Whether the swipe
     * is within a certain length that typically defines a phantom swipe. Also if this swipe
     * has already been tagged as BAD, then we know it's not good.
     * There is one more statement here that checks to see if the weight is equal to a certain limit,
     * if the length is less than the MAX_SWIPE, while the average is greater than the MINIMUM_AVERAGE
     */
    private void label(double ln, double err, double avg) { /** FINAL LABEL */
        if (ln > getNiceLength() && !badDirections) { // Has human qualities (weight/2)
            weight /= ln / getNiceLength();
            if (debugging()) {
                println("GOOD LENGTH REDEMPTION " + weight);
            }
        }
        if (weight < getMaxWeight()) {
            isPhantomSwipe = false;
            if (debugging()) {
                println("GOOD SWIPE: " + weight);
            }
        } else {
            isPhantomSwipe = true;
            if (debugging()) {
                println("BAD SWIPE: " + weight);
            }
        }
    }

//	.:CODE REMOVED : UNRELIABLE:.
//	/** GoodTimes() :: Should the touch's overall time taken be larger than the set MAX_TIME 
//	 * alloted or fall into the category of normal time. Then it is a regular swipe. NO EXCEPTIONS!
//	 */
//	private void goodTimes() { //GOOD TIMES (GOOD)
//		if(deltaTime > logicMap.get(opNames[1])){ //MAX TIME
//			type = GOOD;
//			if(debug){ println( "SLOW SWIPE"); }
//		}
//		else if(deltaTime < 100 && deltaTime > 40) { //NORM TIME (KINDA RISKY)
//			type = GOOD;
//			if(debug){ println( "SOLID SWIPE"); }
//		}
//	}

    /**
     * UNINTERESTING CODE BELOW, YOU'VE BE WARNED
     */

    @Override
    protected void reset() {
        super.reset();
        direction = Direction.UNDEFINED;
        prevDir = Direction.UNDEFINED;
        isPhantomSwipe = false;
        badDirections = false;
        badAngle = false;
        phantomLn = 0;
        prevDelta = 0;
        perfect = 0;
        prevRad = 0;
        totalLn = 0;
        weight = 0;
        prevLn = 0;
        eRad = 0;
        lengths.clear();
    }

    /**
     * Populate data for the previous point
     *
     * @param l length
     * @param r radian
     * @param d change in length (delta) length
     * @param s Direction detected it was heading in
     */
    private void setPrevious(double l, double r, double d, Direction s) {
        prevLn = l;
        prevRad = r;
        prevDelta = d;
        prevDir = s;
    }

}