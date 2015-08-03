package com.ykrapiva.eventmap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.Scroller;
import com.ykrapiva.eventmap.gl.MatrixTrackingGL;

import javax.microedition.khronos.opengles.GL;
import java.util.concurrent.CountDownLatch;

public class EventMapView<T extends EventMapSeat> extends GLSurfaceView {
    private static final String TAG = EventMapView.class.getSimpleName();

    private static final float FLING_VELOCITY_DOWNSCALE = 1.0f;

    private EventMapRenderer<T> mRenderer;
    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleDetector;
    private Scroller mScroller;
    private ValueAnimator mScrollAnimator;
    private EventMap<T> mEventMap;
    private EventMapClickListener<T> mClickListener;

    public EventMapView(Context context) {
        super(context);
        init();
    }

    public EventMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mGestureDetector = new GestureDetector(getContext(), new GestureListener());
        mScaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        mRenderer = new EventMapRenderer<T>();

        setRenderer(mRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        setGLWrapper(new GLSurfaceView.GLWrapper() {
            public GL wrap(GL gl) {
                return new MatrixTrackingGL(gl);
            }
        });

        // Create a Scroller to handle the fling gesture.
        if (Build.VERSION.SDK_INT < 11) {
            mScroller = new Scroller(getContext());
        } else {
            mScroller = new Scroller(getContext(), null, true);
        }

        // The scroller doesn't have any built-in animation functions--it just supplies
        // values when we ask it to. So we have to have a way to call it every frame
        // until the fling ends. This code (ab)uses a ValueAnimator object to generate
        // a callback on every animation frame. We don't use the animated value at all.
        if (Build.VERSION.SDK_INT >= 11) {
            mScrollAnimator = ValueAnimator.ofFloat(0, 1);
            mScrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    tickScrollAnimation();
                }
            });
        }
    }

    public void setBackgroundColor(int color) {
        mRenderer.setClearColor(color);
    }

    public void setClickListener(EventMapClickListener<T> mClickListener) {
        this.mClickListener = mClickListener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        boolean result = mGestureDetector.onTouchEvent(event);

        // If the GestureDetector doesn't want this event, do some custom processing.
        // This code just tries to detect when the user is done scrolling by looking
        // for ACTION_UP events.
        if (!result) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    // User is done scrolling, it's now safe to do things like autocenter
                    stopScrolling();
                    break;
            }
        }

        return true;
    }

    private void onSeatClicked(T seat) {
        if (mClickListener != null) {
            mClickListener.onSeatClicked(seat);
        }
        if (mEventMap != null) {
            mEventMap.updateColor(seat);
        }
        requestRender();
    }

    public void setEventMap(EventMap<T> world) {
        mEventMap = world;
        mRenderer.setEventMap(mEventMap);
        requestRender();
    }

    public EventMap<T> getEventMap() {
        return mEventMap;
    }

    private void tickScrollAnimation() {
        if (!mScroller.isFinished()) {
            mScroller.computeScrollOffset();
            int currX = mScroller.getCurrX();
            int currY = mScroller.getCurrY();

            mRenderer.setOffset(currX, currY);
        } else {
            if (Build.VERSION.SDK_INT >= 11) {
                mScrollAnimator.cancel();
            }
            onScrollFinished();
        }

        requestRender();
    }

    /**
     * Force a stop to all pie motion. Called when the user taps during a fling.
     */
    private void stopScrolling() {
        mScroller.forceFinished(true);
        onScrollFinished();
    }

    private void onScrollFinished() {
        // nothing for now
    }

    private boolean isAnimationRunning() {
        return !mScroller.isFinished();
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, final float distanceX, final float distanceY) {
            float offsetX = mRenderer.getOffsetX();
            float offsetY = mRenderer.getOffsetY();
            float scaleFactor = mRenderer.getScaleFactor();
            offsetX = offsetX - distanceX * scaleFactor;
            offsetY = offsetY + distanceY * scaleFactor;

            //Log.v(TAG, "onScroll(), new offset: [" + offsetX + "," + offsetY + "]");

            mRenderer.setOffset(offsetX, offsetY);

            requestRender();
            return true;
        }

        @Override
        public boolean onSingleTapUp(final MotionEvent e) {
            final CountDownLatch clickResultReadyLatch = new CountDownLatch(1);

            queueEvent(new Runnable() {
                @Override
                public void run() {
                    mRenderer.onClick(e.getX(), e.getY(), clickResultReadyLatch);
                }
            });

            requestRender();

            try {
                clickResultReadyLatch.await();
                T seat = mRenderer.getClickedObject();
                if (seat != null) {
                    onSeatClicked(seat);
                }
            } catch (InterruptedException e1) {
                // ignore
            }

            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float offsetX = mRenderer.getOffsetX();
            float offsetY = mRenderer.getOffsetY();

            //Log.v(TAG, "onFling(), from offset: [" + offsetX + "," + offsetY + "]");

            mScroller.fling(
                    (int) offsetX, (int) offsetY,
                    (int) (velocityX / FLING_VELOCITY_DOWNSCALE), (int) (-velocityY / FLING_VELOCITY_DOWNSCALE),
                    Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);

//            mScroller.setFriction(1.0f);

            // Start the animator and tell it to animate for the expected duration of the fling.
            if (Build.VERSION.SDK_INT >= 11) {
                mScrollAnimator.setDuration(mScroller.getDuration());
                mScrollAnimator.start();
            }

            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            if (isAnimationRunning()) {
                stopScrolling();
            }
            return false;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(final ScaleGestureDetector detector) {
            final float scaleFactor = detector.getScaleFactor();
            mRenderer.onScale(scaleFactor);
            requestRender();
            return true;
        }
    }

    public interface EventMapClickListener<T> {
        void onSeatClicked(T seat);
    }
}
