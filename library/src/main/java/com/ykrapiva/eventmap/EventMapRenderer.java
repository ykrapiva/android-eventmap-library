package com.ykrapiva.eventmap;

import android.graphics.PointF;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import com.ykrapiva.eventmap.gl.GLUtils;
import com.ykrapiva.eventmap.gl.Ray;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.util.concurrent.CountDownLatch;

class EventMapRenderer<T extends EventMapSeat> implements GLSurfaceView.Renderer {
    @SuppressWarnings("unused")
    private static final String TAG = EventMapRenderer.class.getSimpleName();

    @SuppressWarnings("FieldCanBeLocal")
    private float mEyeX = 0.0f;
    @SuppressWarnings("FieldCanBeLocal")
    private float mEyeY = 0.0f;
    @SuppressWarnings("FieldCanBeLocal")
    private float mEyeZ = 0.0f;
    @SuppressWarnings("FieldCanBeLocal")
    private float mCenterX = 0.0f;
    @SuppressWarnings("FieldCanBeLocal")
    private float mCenterY = 0.0f;
    @SuppressWarnings("FieldCanBeLocal")
    private float mCenterZ = 0.0f;

    private int mScreenWidth;
    private int mScreenHeight;

    private EventMap<T> mEventMap;

    private final float[] mClearColor = new float[4];
    private volatile float mScaleFactor = 1.0f;
    private volatile float mOffsetX, mOffsetY;
    private final RectF mOffsetBounds = new RectF();

    // Click stuff
    private PointF mClickPoint;
    private T mClickedObject;
    private CountDownLatch mClickedObjectResultAvailableLatch;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        gl.glViewport(0, 0, width, height);
        mScreenWidth = width;
        mScreenHeight = height;

        if (mEventMap != null) {
            mEventMap.notifyNeedReinitialization();
        }

        setupScene(gl);
        calculateOffsetBounds();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Handle user input
        if (mClickPoint != null && mEventMap != null && mClickedObjectResultAvailableLatch != null) {
            Ray ray = new Ray(gl, mScreenWidth, mScreenHeight, mClickPoint.x, mClickPoint.y);
            mClickedObject = mEventMap.findIntersection(gl, ray);
            mClickedObjectResultAvailableLatch.countDown();
        }

        mClickPoint = null;
        mClickedObjectResultAvailableLatch = null;

        // Draw scene
        gl.glClearColor(mClearColor[0], mClearColor[1], mClearColor[2], mClearColor[3]);
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();

        limitOffsetToBounds();

        gl.glTranslatef(mOffsetX, mOffsetY, 0);
        gl.glScalef(mScaleFactor, mScaleFactor, 1.0f);
        GLU.gluLookAt(gl, mEyeX, mEyeY, mEyeZ, mCenterX, mCenterY, mCenterZ, 0, 1, 0);

        gl.glDisable(GL10.GL_DEPTH_TEST);

        if (mEventMap != null) {
            mEventMap.draw(gl);
        }
    }

    private void limitOffsetToBounds() {
        mOffsetX = Math.max(mOffsetBounds.left, mOffsetX);
        mOffsetX = Math.min(mOffsetBounds.right, mOffsetX);
        mOffsetY = Math.max(mOffsetBounds.top, mOffsetY);
        mOffsetY = Math.min(mOffsetBounds.bottom, mOffsetY);
    }

    void setEventMap(EventMap<T> eventMap) {
        this.mEventMap = eventMap;
    }

    void setClearColor(int color) {
        GLUtils.getFloatColorComponents(color, mClearColor);
    }

    private void setupScene(GL10 gl) {
        if (mEventMap != null && mScreenHeight != 0) {
            RectF eventMapBounds = mEventMap.getBounds();
            float eventMapWidth = Math.abs(eventMapBounds.width());
            float eventMapHeight = Math.abs(eventMapBounds.height());
            float boundingSphereDiameter = Math.max(eventMapWidth, eventMapHeight);
            float centerX = eventMapBounds.centerX();
            float centerY = eventMapBounds.centerY();
            float left = centerX - boundingSphereDiameter / 2.0f;
            float right = centerX + boundingSphereDiameter / 2.0f;
            float bottom = centerY - boundingSphereDiameter / 2.0f;
            float top = centerY + boundingSphereDiameter / 2.0f;
            float zNear = 1.0f;
            float zFar = zNear + boundingSphereDiameter;
            float ratio = mScreenWidth / (float) mScreenHeight;

            if (ratio < 1.0f) {
                // window taller than wide
                float newEventMapHeight = eventMapHeight * ratio;
                float extraSpaceAdded = (newEventMapHeight - eventMapHeight) / 2.0f;
                bottom += extraSpaceAdded;
                top -= extraSpaceAdded;
            } else {
                float newEventMapWidth = eventMapWidth * ratio;
                float extraSpaceAdded = (newEventMapWidth - eventMapWidth) / 2.0f;
                left -= extraSpaceAdded;
                right += extraSpaceAdded;
            }

            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();
            gl.glOrthof(left, right, bottom, top, zNear, zFar);

            this.mEyeZ = boundingSphereDiameter;
        }
    }

    private void calculateOffsetBounds() {
        if (mEventMap != null) {
            RectF eventMapBounds = mEventMap.getBounds();
            float eventMapWidth = Math.abs(eventMapBounds.width());
            float eventMapHeight = Math.abs(eventMapBounds.height());
            float maxHorizontalOffset = (eventMapWidth * mScaleFactor - eventMapWidth) / 2.0f;
            float maxVerticalOffset = (eventMapHeight * mScaleFactor - eventMapHeight) / 2.0f;
            mOffsetBounds.left = -maxHorizontalOffset;
            mOffsetBounds.right = maxHorizontalOffset;
            mOffsetBounds.bottom = maxVerticalOffset;
            mOffsetBounds.top = -maxVerticalOffset;
        }
    }

    void onClick(float x, float y, CountDownLatch clickedObjectResultAvailableLatch) {
        this.mClickPoint = new PointF(x, y);
        this.mClickedObjectResultAvailableLatch = clickedObjectResultAvailableLatch;
    }

    public T getClickedObject() {
        return mClickedObject;
    }

    void onScale(float scaleFactor) {
        mScaleFactor *= scaleFactor;
        mScaleFactor = Math.max(1.0f, mScaleFactor);
        calculateOffsetBounds();
    }

    void setOffset(float offsetX, float offsetY) {
        this.mOffsetX = offsetX;
        this.mOffsetY = offsetY;
    }

    public float getOffsetX() {
        return mOffsetX;
    }

    public float getOffsetY() {
        return mOffsetY;
    }

    public float getScaleFactor() {
        return mScaleFactor;
    }
}