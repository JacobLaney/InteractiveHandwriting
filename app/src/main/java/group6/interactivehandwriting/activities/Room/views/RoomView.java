package group6.interactivehandwriting.activities.Room.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.graphics.Paint;
import android.graphics.Color;
import android.widget.Button;

import group6.interactivehandwriting.activities.Room.RoomViewActionUtility;
import group6.interactivehandwriting.activities.Room.draw.CanvasManager;
import group6.interactivehandwriting.common.app.Profile;

import group6.interactivehandwriting.common.app.actions.draw.EndDrawAction;
import group6.interactivehandwriting.common.app.actions.draw.MoveDrawAction;
import group6.interactivehandwriting.common.app.actions.draw.StartDrawAction;
import group6.interactivehandwriting.common.network.NetworkLayer;

import group6.interactivehandwriting.R;

import static android.view.MotionEvent.INVALID_POINTER_ID;

public class RoomView extends View {
    private static final float TOUCH_TOLERANCE = 4;

    private NetworkLayer networkLayer;

    private CanvasManager canvasManager;
    private Profile profile;

    private float mLastTouchX;
    private float mLastTouchY;
    private float mPosX;
    private float mPosY;
    private float cX, cY; // circle coords

    private ScaleGestureDetector mScaleDetector;
    private float mScaleFactor = 1.0f;
    private float scalePointX;
    private float scalePointY;

    private Paint marker;

    // 0 - zoom/resize .. 1 - drawing
    private int allowDrawing = 1;

    // document resizing
    private ScaleGestureDetector mRoomScaleDetector;
    private ScaleGestureDetector mDocumentScaleDetector;
    private int mActivePointerId = INVALID_POINTER_ID;

    private enum TouchStates {
        RESIZE, DRAW;
    }

    private TouchStates touchState;

    public RoomView(Context context) {
        super(context);
        canvasManager = new CanvasManager(this);

        mRoomScaleDetector = new ScaleGestureDetector(context, new RoomScaleListener());
        touchState = TouchStates.DRAW;

        marker = new Paint();
        marker.setColor(Color.RED);
        marker.setAlpha(125);
        marker.setTextSize(20);
    }

    public void setTouchState(TouchStates touchStateIn) {
        this.touchState = touchStateIn;
    }

    public TouchStates getTouchState() {
        return this.touchState;
    }

    public TouchStates getDrawState() {
        return TouchStates.DRAW;
    }

    public TouchStates getResizeState() {
        return TouchStates.RESIZE;
    }

    public boolean setNetworkLayer(NetworkLayer layer) {
        if (layer != null) {
            this.networkLayer = layer;
            this.networkLayer.receiveDrawActions(canvasManager);
            this.networkLayer.synchronizeRoom();
            this.profile = layer.getMyProfile();
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
        super.onSizeChanged(w, h, oldWidth, oldHeight);
        canvasManager.updateSize(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.scale(mScaleFactor, mScaleFactor, scalePointX, scalePointY);
        canvas.translate(mPosX, mPosY);
        canvas.drawCircle(cX, cY, 10, marker);

        canvasManager.update(canvas);

        canvas.restore();
    }

    public boolean onTouchEvent(MotionEvent event) {
        switch (touchState) {
            case DRAW: /* Draw */
                drawEvent(event);
                break;
            case RESIZE: /* Resize */
                mRoomScaleDetector.onTouchEvent(event);

                final int action = event.getAction();


                switch(action & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN: {

                        final float x = (event.getX() - scalePointX)/mScaleFactor;
                        final float y = (event.getY() - scalePointY)/mScaleFactor;
                        cX = x - mPosX + scalePointX; // canvas X
                        cY = y - mPosY + scalePointY; // canvas Y
                        mLastTouchX = x;
                        mLastTouchY = y;
                        break;
                    }
                    case MotionEvent.ACTION_MOVE: {

                        final float x = (event.getX() - scalePointX)/mScaleFactor;
                        final float y = (event.getY() - scalePointY)/mScaleFactor;
                        cX = x - mPosX + scalePointX; // canvas X
                        cY = y - mPosY + scalePointY; // canvas Y
                        // Only move if the ScaleGestureDetector isn't processing a gesture.
                        if (!mRoomScaleDetector.isInProgress()) {
                            final float dx = x - mLastTouchX; // change in X
                            final float dy = y - mLastTouchY; // change in Y
                            mPosX += dx;
                            mPosY += dy;
                            invalidate();
                        }

                        mLastTouchX = x;
                        mLastTouchY = y;
                        break;

                    }
                    case MotionEvent.ACTION_UP: {
                        final float x = (event.getX() - scalePointX)/mScaleFactor;
                        final float y = (event.getY() - scalePointY)/mScaleFactor;
                        cX = x - mPosX + scalePointX; // canvas X
                        cY = y - mPosY + scalePointY; // canvas Y
                        mLastTouchX = 0;
                        mLastTouchY = 0;
                        invalidate();
                    }
                }
                break;
            default:
                break;
        }
        return true;
    }

    private void drawEvent(MotionEvent event) {
        performClick();
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStarted(x - mPosX, y - mPosY);
                break;
            case MotionEvent.ACTION_MOVE:
                touchMoved(x - mPosX, y - mPosY);
                break;
            case MotionEvent.ACTION_UP:
                touchReleased();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void touchStarted(float x, float y) {
        StartDrawAction action = RoomViewActionUtility.touchStarted(x, y);
        canvasManager.handleDrawAction(profile, action);

        if (networkLayer != null) {
            networkLayer.startDraw(action);
        }
    }

    private void touchMoved(float x, float y) {
        if (RoomViewActionUtility.didTouchMove(x, y, TOUCH_TOLERANCE)) {
            MoveDrawAction action = RoomViewActionUtility.touchMoved(x, y);
            canvasManager.handleDrawAction(profile, action);

            if (networkLayer != null) {
                networkLayer.moveDraw(action);
            }
        }
    }

    private void touchReleased() {
        EndDrawAction action = RoomViewActionUtility.touchReleased();
        canvasManager.handleDrawAction(profile, action);

        if (networkLayer != null) {
            networkLayer.endDraw(action);
        }
    }

    private class RoomScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();
            scalePointX = detector.getFocusX();
            scalePointY = detector.getFocusY();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(1.0f, Math.min(mScaleFactor, 2.0f));

            invalidate();
            return true;
        }
    }

        public void undo() {
            canvasManager.undo(profile);

            if (networkLayer != null) {
                networkLayer.undo(profile);
            }
        }
    }
