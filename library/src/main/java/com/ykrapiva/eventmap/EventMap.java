package com.ykrapiva.eventmap;

import android.annotation.SuppressLint;
import android.graphics.*;
import android.opengl.Matrix;
import android.text.TextUtils;
import com.android.texample.GLText;
import com.ykrapiva.eventmap.gl.GLUtils;
import com.ykrapiva.eventmap.gl.MatrixGrabber;
import com.ykrapiva.eventmap.gl.Ray;
import com.ykrapiva.eventmap.gl.Triangle;

import javax.microedition.khronos.opengles.GL10;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.*;

public class EventMap<T extends EventMapSeat> {
    @SuppressWarnings("unused")
    private static final String TAG = EventMap.class.getSimpleName();

    private static final int FLOAT_SIZE_IN_BYTES = 4;
    private static final int SHORT_SIZE_IN_BYTES = 2;
    private static final int NUM_VERTICES_IN_SQUARE = 4;
    private static final short SQUARE_VERTICES_ORDER_TEMPLATE[] = {0, 1, 2, 0, 2, 3};
    private static final int NUM_COORDS_PER_VERTEX = 3;
    private static final int NUM_COLOR_COMPONENTS = 4;

    // Max Bitmap size the device can decode
    private final int mMaxBitmapSize;

    private final Map<EventMapSeat.ViewType, List<T>> mSeats = new HashMap<EventMapSeat.ViewType, List<T>>();

    // Data for rendering seats
    private final Map<EventMapSeat.ViewType, FloatBuffer> mSeatVertexBuffers = new HashMap<EventMapSeat.ViewType, FloatBuffer>();
    private final Map<EventMapSeat.ViewType, ShortBuffer> mSeatIndicesBuffers = new HashMap<EventMapSeat.ViewType, ShortBuffer>();
    private final Map<EventMapSeat.ViewType, FloatBuffer> mSeatColorBuffers = new HashMap<EventMapSeat.ViewType, FloatBuffer>();
    private final Map<EventMapSeat.ViewType, FloatBuffer> mSeatTextureBuffers = new HashMap<EventMapSeat.ViewType, FloatBuffer>();
    private final Map<EventMapSeat.ViewType, Integer[]> mSeatTextures = new HashMap<EventMapSeat.ViewType, Integer[]>();
    private final Map<EventMapSeat.ViewType, Integer> mSeatTextureIds = new HashMap<EventMapSeat.ViewType, Integer>();
    private final Map<EventMapSeat.ViewType, Bitmap> mSeatTextureBitmaps = new HashMap<EventMapSeat.ViewType, Bitmap>();

    // // Data for rendering background
    private FloatBuffer mBackgroundTextureBuffer;
    private FloatBuffer mBackgroundVertexBuffer;
    private ShortBuffer mBackgroundIndicesBuffer;
    private Bitmap mBackgroundBitmap;
    private int[] mBackgroundTextures;
    private int mBackgroundTextureId = -1;

    // Event map bounds
    private final RectF mEventMapBounds;

    // Used for ray picking
    private final MatrixGrabber matrixGrabber = new MatrixGrabber();

    // Text support
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, GLText> mGlTextMapBySize = new HashMap<Integer, GLText>();

    // Initialization flags
    private boolean mBackgroundSetUp;
    private boolean mSeatsInitialized;


    public EventMap(float eventMapWidth, float eventMapHeight) {
        this.mEventMapBounds = new RectF(-eventMapWidth / 2.0f, eventMapHeight / 2.0f, eventMapWidth / 2.0f, -eventMapHeight / 2.0f);
        this.mSeatTextureBitmaps.put(EventMapSeat.ViewType.CIRCLE, createCircleTexture());
        this.mMaxBitmapSize = GLUtils.getMaxTextureSize();
    }

    public void add(Collection<T> seats) {
        for (T seat : seats) {
            List<T> seatList = mSeats.get(seat.getViewType());
            if (seatList == null) {
                seatList = new ArrayList<T>();
                mSeats.put(seat.getViewType(), seatList);
            }

            seatList.add(seat);
        }
        mSeatsInitialized = false;
    }

    public void add(T seat) {
        this.add(Collections.singleton(seat));
    }

    public List<T> getSeats() {
        List<T> seats = new ArrayList<T>();

        for (List<T> viewTypeSpecificSeats : mSeats.values()) {
            seats.addAll(viewTypeSpecificSeats);
        }

        return seats;
    }

    public RectF getBounds() {
        return mEventMapBounds;
    }

    /**
     * Set background image. If image dimensions are not of power of two - it is converted.
     */
    public void setBackground(Bitmap background) {
        this.mBackgroundBitmap = background;

        if (mBackgroundBitmap != null) {
            // Convert to texture like dimensions (power of two)
            int backgroundOriginalWidth = background.getWidth();
            int backgroundOriginalHeight = background.getHeight();

            int widthPo2 = backgroundOriginalWidth > mMaxBitmapSize ? findLesserPowerOfTwo(backgroundOriginalWidth) : findBiggerPowerOfTwo(backgroundOriginalWidth);
            int heightPo2 = backgroundOriginalHeight > mMaxBitmapSize ? findLesserPowerOfTwo(backgroundOriginalHeight) : findBiggerPowerOfTwo(backgroundOriginalHeight);

            if (widthPo2 != backgroundOriginalWidth || heightPo2 != backgroundOriginalHeight) {
                mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap, widthPo2, heightPo2, false);
            }
        }

        mBackgroundSetUp = false;
    }

    /**
     * Set background image. Image is decoded from byte array performing necessary scaling and prevents from loading too large images
     *
     * @param data Image data
     */
    @SuppressWarnings("unused")
    public void setBackground(byte[] data) throws IOException {
        setBackground(new ByteArrayInputStream(data));
    }

    /**
     * Set background image.
     * Image is decoded from the stream performing necessary scaling and prevents from loading too large images.
     * Passed in stream is closed.
     *
     * @param is Image data stream
     */
    public void setBackground(InputStream is) throws IOException {
        if (!is.markSupported()) {
            is = cacheInputStream(is);
        }

        // Remember stream's initial position
        is.mark(Integer.MAX_VALUE);

        // Just get dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeStream(is, null, options);

        // Calculate inSampleSize to not decoding too large images
        options.inSampleSize = calculateInSampleSize(options, mMaxBitmapSize, mMaxBitmapSize);

        // Revert stream position
        is.reset();

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        Bitmap background = BitmapFactory.decodeStream(is, null, options);

        // close stream
        is.close();

        // finally set decoded background image
        setBackground(background);
    }

    private void initializeBackground(GL10 gl) {
        if (mBackgroundTextures != null) {
            // Delete a texture.
            gl.glDeleteTextures(1, mBackgroundTextures, 0);
        }

        mBackgroundTextures = null;
        mBackgroundIndicesBuffer = null;
        mBackgroundVertexBuffer = null;
        mBackgroundTextureBuffer = null;
        mBackgroundTextureId = -1;

        if (mBackgroundBitmap != null) {
            // Generate one texture pointer...
            mBackgroundTextures = new int[1];
            gl.glGenTextures(1, mBackgroundTextures, 0);
            mBackgroundTextureId = mBackgroundTextures[0];

            // ...and bind it to our array
            gl.glBindTexture(GL10.GL_TEXTURE_2D, mBackgroundTextureId);

            // Create Nearest Filtered Texture
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);

            // Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
            android.opengl.GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, mBackgroundBitmap, 0);

            final float textureCoordinates[] = {
                    0.0f, 0.0f,
                    0.0f, 1.0f,
                    1.0f, 1.0f,
                    1.0f, 0.0f
            };

            float textureZPosition = 0;

            float[] coords = {
                    mEventMapBounds.left, mEventMapBounds.top, textureZPosition,
                    mEventMapBounds.left, mEventMapBounds.bottom, textureZPosition,
                    mEventMapBounds.right, mEventMapBounds.bottom, textureZPosition,
                    mEventMapBounds.right, mEventMapBounds.top, textureZPosition
            };

            final short indices[] = {0, 1, 2, 0, 2, 3};

            mBackgroundTextureBuffer = ByteBuffer.allocateDirect(textureCoordinates.length * FLOAT_SIZE_IN_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
                    .put(textureCoordinates);

            mBackgroundVertexBuffer = ByteBuffer.allocateDirect(coords.length * FLOAT_SIZE_IN_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
                    .put(coords);

            mBackgroundIndicesBuffer = ByteBuffer.allocateDirect(indices.length * SHORT_SIZE_IN_BYTES)
                    .order(ByteOrder.nativeOrder()).asShortBuffer()
                    .put(indices);

            mBackgroundTextureBuffer.position(0);
            mBackgroundVertexBuffer.position(0);
            mBackgroundIndicesBuffer.position(0);
        }
    }

    private void initializeSeats(GL10 gl) {
        mSeatVertexBuffers.clear();
        mSeatColorBuffers.clear();
        mSeatIndicesBuffers.clear();
        mSeatTextureBuffers.clear();

        for (EventMapSeat.ViewType viewType : mSeats.keySet()) {
            Integer[] textureIds = mSeatTextures.remove(viewType);
            if (textureIds != null) {
                gl.glDeleteTextures(1, toPrimitiveArray(textureIds), 0);
            }

            mSeatTextureIds.remove(viewType);
        }

        if (mSeats.isEmpty()) {
            return;
        }

        // Free texts and associated texture resources
        for (GLText glText : mGlTextMapBySize.values()) {
            glText.destroy(gl);
        }
        mGlTextMapBySize.clear();

        for (Map.Entry<EventMapSeat.ViewType, List<T>> entry : mSeats.entrySet()) {
            final short[] indexList = SQUARE_VERTICES_ORDER_TEMPLATE.clone();
            final float[] colors = new float[NUM_COLOR_COMPONENTS];
            final float[] vertices = new float[NUM_COORDS_PER_VERTEX * NUM_VERTICES_IN_SQUARE];
            final float textureCoordinates[] = {
                    0.0f, 0.0f,
                    0.0f, 1.0f,
                    1.0f, 1.0f,
                    1.0f, 0.0f
            };

            EventMapSeat.ViewType viewType = entry.getKey();
            List<T> seats = entry.getValue();

            // Init vertex buffer
            ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * FLOAT_SIZE_IN_BYTES * seats.size());
            vbb.order(ByteOrder.nativeOrder());
            FloatBuffer vertexBuffer = vbb.asFloatBuffer();

            // Init indices buffer
            ByteBuffer tbibb = ByteBuffer.allocateDirect(indexList.length * SHORT_SIZE_IN_BYTES * seats.size());
            tbibb.order(ByteOrder.nativeOrder());
            ShortBuffer indicesBuffer = tbibb.asShortBuffer();

            // Init color buffer
            ByteBuffer cbb = ByteBuffer.allocateDirect(NUM_VERTICES_IN_SQUARE * colors.length * FLOAT_SIZE_IN_BYTES * seats.size());
            cbb.order(ByteOrder.nativeOrder());
            FloatBuffer colorBuffer = cbb.asFloatBuffer();

            for (T seat : seats) {
                RectF worldCoordinates = toWorldCoordinates(seat.getRect());
                makeRelativeToWorldCenter(worldCoordinates, Math.abs(mEventMapBounds.width()), Math.abs(mEventMapBounds.height()));

                int color = seat.getColor();

                vertices[0] = worldCoordinates.left;
                vertices[1] = worldCoordinates.top;
                vertices[2] = 0;
                vertices[3] = worldCoordinates.left;
                vertices[4] = worldCoordinates.bottom;
                vertices[5] = 0;
                vertices[6] = worldCoordinates.right;
                vertices[7] = worldCoordinates.bottom;
                vertices[8] = 0;
                vertices[9] = worldCoordinates.right;
                vertices[10] = worldCoordinates.top;
                vertices[11] = 0;

                vertexBuffer.put(vertices);
                indicesBuffer.put(indexList);

                // Prepare index list for insertion of the next seat
                for (int index = 0; index < indexList.length; index++) {
                    indexList[index] += NUM_VERTICES_IN_SQUARE;
                }

                // Convert integer color into float components
                GLUtils.getFloatColorComponents(color, colors);

                // Each vertex has the same color (no gradients)
                for (int n = 0; n < NUM_VERTICES_IN_SQUARE; n++) {
                    colorBuffer.put(colors);
                }

                // Load text variants, depending on the seat size
                int textSize = calcTextSize(seat.getRect());
                GLText glText = mGlTextMapBySize.get(textSize);
                if (glText == null) {
                    glText = new GLText(gl);
                    glText.load(textSize, 0, 0);
                    mGlTextMapBySize.put(textSize, glText);
                }
            }

            mSeatVertexBuffers.put(viewType, vertexBuffer);
            mSeatColorBuffers.put(viewType, colorBuffer);
            mSeatIndicesBuffers.put(viewType, indicesBuffer);

            // Init textures
            Bitmap seatTextureBitmap = mSeatTextureBitmaps.get(viewType);
            if (seatTextureBitmap != null) {
                // Init texture buffer
                FloatBuffer textureBuffer = ByteBuffer.allocateDirect(textureCoordinates.length * FLOAT_SIZE_IN_BYTES * seats.size())
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();

                for (int i = 0; i < seats.size(); i++) {
                    textureBuffer.put(textureCoordinates);
                }

                mSeatTextureBuffers.put(viewType, textureBuffer);

                // Generate one texture pointer...
                int[] seatTextures = new int[1];
                gl.glGenTextures(1, seatTextures, 0);
                int seatTextureId = seatTextures[0];

                mSeatTextures.put(viewType, new Integer[]{seatTextureId});
                mSeatTextureIds.put(viewType, seatTextureId);

                // ...and bind it to our array
                gl.glBindTexture(GL10.GL_TEXTURE_2D, seatTextureId);

                // Create Nearest Filtered Texture
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);

                // Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
                android.opengl.GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, seatTextureBitmap, 0);
            }
        }
    }

    public void draw(GL10 gl) {
        drawBackground(gl);
        drawSeats(gl);
    }

    private void drawBackground(GL10 gl) {
        if (!mBackgroundSetUp) {
            initializeBackground(gl);
            mBackgroundSetUp = true;
        }

        if (mBackgroundTextureId != -1 && mBackgroundTextureBuffer != null && mBackgroundVertexBuffer != null && mBackgroundIndicesBuffer != null) {
            // Enable texture
            gl.glEnable(GL10.GL_TEXTURE_2D);
            gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            // Enable blending so that transparent background areas remain transparent
            gl.glEnable(GL10.GL_BLEND);
            gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
            // Texture coordinates
            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mBackgroundTextureBuffer);
            gl.glBindTexture(GL10.GL_TEXTURE_2D, mBackgroundTextureId);

            // Bind texture rectangle coordinates
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mBackgroundVertexBuffer);

            // Draw background texture
            gl.glDrawElements(GL10.GL_TRIANGLES, mBackgroundIndicesBuffer.capacity(), GL10.GL_UNSIGNED_SHORT, mBackgroundIndicesBuffer);

            // Disable everything we enabled
            gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glDisable(GL10.GL_BLEND);
            gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            gl.glDisable(GL10.GL_TEXTURE_2D);
        }
    }

    private void drawSeats(GL10 gl) {
        if (!mSeatsInitialized) {
            initializeSeats(gl);
            mSeatsInitialized = true;
        }

        for (Map.Entry<EventMapSeat.ViewType, List<T>> entry : mSeats.entrySet()) {
            EventMapSeat.ViewType viewType = entry.getKey();

            FloatBuffer vertexBuffer = mSeatVertexBuffers.get(viewType);
            FloatBuffer colorBuffer = mSeatColorBuffers.get(viewType);
            ShortBuffer indicesBuffer = mSeatIndicesBuffers.get(viewType);
            FloatBuffer textureBuffer = mSeatTextureBuffers.get(viewType);

            vertexBuffer.position(0);
            indicesBuffer.position(0);
            colorBuffer.position(0);

            if (textureBuffer != null) {
                textureBuffer.position(0);

                // Enable texture
                gl.glEnable(GL10.GL_TEXTURE_2D);
                gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
                // Texture coordinates
                gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, textureBuffer);
                gl.glBindTexture(GL10.GL_TEXTURE_2D, mSeatTextureIds.get(viewType));
            }

            // Enable blending so that transparent background areas remain transparent
            gl.glEnable(GL10.GL_BLEND);
            gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);

            // the vertex array is enabled for writing and used during rendering
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            // the color array is enabled for writing and used during rendering
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY);

            // specifies the location and data format of an array of vertex colors to use when rendering
            gl.glColorPointer(4, GL10.GL_FLOAT, 0, colorBuffer);
            // specifies the location and data format of an array of vertex coordinates to use when rendering
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer);

            // draw mSeats
            gl.glDrawElements(GL10.GL_TRIANGLES, indicesBuffer.capacity(), GL10.GL_UNSIGNED_SHORT, indicesBuffer);

            gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
            gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            gl.glDisable(GL10.GL_BLEND);
            gl.glDisable(GL10.GL_TEXTURE_2D);
        }

        drawSeatCaptions(gl);
    }

    private void drawSeatCaptions(GL10 gl) {
        // enable texture + alpha blending
        // NOTE: this is required for text rendering! we could incorporate it into
        // the GLText class, but then it would be called multiple times (which impacts performance).
        gl.glEnable(GL10.GL_TEXTURE_2D);              // Enable Texture Mapping
        gl.glEnable(GL10.GL_BLEND);                   // Enable Alpha Blend
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);  // Set Alpha Blend Function

        // Draw captions
        for (GLText glText : mGlTextMapBySize.values()) {
            glText.begin(0.0f, 0.0f, 0.0f, 1.0f); // Begin Text Rendering
        }

        float[] coords = new float[NUM_COORDS_PER_VERTEX * NUM_VERTICES_IN_SQUARE];

        for (Map.Entry<EventMapSeat.ViewType, List<T>> entry : mSeats.entrySet()) {
            EventMapSeat.ViewType viewType = entry.getKey();
            List<T> seats = entry.getValue();

            FloatBuffer vertexBuffer = mSeatVertexBuffers.get(viewType);

            for (int i = 0; i < seats.size(); i++) {
                T square = seats.get(i);

                String caption = square.getCaption();
                GLText glText = mGlTextMapBySize.get(calcTextSize(square.getRect()));

                if (!TextUtils.isEmpty(caption) && glText != null) {
                    vertexBuffer.position(i * NUM_COORDS_PER_VERTEX * NUM_VERTICES_IN_SQUARE);
                    vertexBuffer.get(coords, 0, NUM_COORDS_PER_VERTEX * NUM_VERTICES_IN_SQUARE);

                    RectF rect = new RectF(coords[0], coords[1], coords[6], coords[7]);
                    float strWidth = glText.getLength(caption);
                    float strHeight = glText.getHeight();
                    glText.draw(caption, rect.centerX() - strWidth / 2.0f, rect.centerY() - strHeight / 2.0f);
                }
            }
        }

        for (GLText glText : mGlTextMapBySize.values()) {
            glText.end(); // End Text Rendering
        }

        // disable texture + alpha
        gl.glDisable(GL10.GL_BLEND);                  // Disable Alpha Blend
        gl.glDisable(GL10.GL_TEXTURE_2D);             // Disable Texture Mapping
    }

    public T findIntersection(GL10 gl, Ray ray) {
        float[] coords = new float[NUM_VERTICES_IN_SQUARE * NUM_COORDS_PER_VERTEX];

        for (Map.Entry<EventMapSeat.ViewType, List<T>> entry : mSeats.entrySet()) {
            EventMapSeat.ViewType viewType = entry.getKey();
            List<T> seats = entry.getValue();

            FloatBuffer vertextBuffer = mSeatVertexBuffers.get(viewType);

            for (int i = 0; i < seats.size(); i++) {
                vertextBuffer.position(i * coords.length);
                vertextBuffer.get(coords, 0, coords.length);

                if (isIntersected(gl, ray, coords)) {
                    return seats.get(i);
                }
            }
        }

        return null;
    }

    private boolean isIntersected(GL10 gl, Ray ray, float[] coords) {
        matrixGrabber.getCurrentState(gl);

        int coordCount = coords.length;
        float[] convertedSquare = new float[coordCount];
        float[] resultVector = new float[4];
        float[] inputVector = new float[4];

        for (int i = 0; i < coordCount; i = i + 3) {
            inputVector[0] = coords[i];
            inputVector[1] = coords[i + 1];
            inputVector[2] = coords[i + 2];
            inputVector[3] = 1;
            Matrix.multiplyMV(resultVector, 0, matrixGrabber.mModelView, 0, inputVector, 0);
            convertedSquare[i] = resultVector[0] / resultVector[3];
            convertedSquare[i + 1] = resultVector[1] / resultVector[3];
            convertedSquare[i + 2] = resultVector[2] / resultVector[3];
        }

        Triangle t1 = new Triangle(new float[]{convertedSquare[0], convertedSquare[1], convertedSquare[2]}, new float[]{convertedSquare[3], convertedSquare[4], convertedSquare[5]}, new float[]{convertedSquare[6], convertedSquare[7], convertedSquare[8]});
        Triangle t2 = new Triangle(new float[]{convertedSquare[0], convertedSquare[1], convertedSquare[2]}, new float[]{convertedSquare[6], convertedSquare[7], convertedSquare[8]}, new float[]{convertedSquare[9], convertedSquare[10], convertedSquare[11]});

        float[] point1 = new float[3];
        float[] point2 = new float[3];
        int intersects1 = Triangle.intersectRayAndTriangle(ray, t1, point1);
        int intersects2 = Triangle.intersectRayAndTriangle(ray, t2, point2);

        if (intersects1 == 1 || intersects1 == 2) {
            return true;
        } else if (intersects2 == 1 || intersects2 == 2) {
            return true;
        }

        return false;
    }

    private Bitmap createCircleTexture() {
        Bitmap circleTexture = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(circleTexture);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        c.drawCircle(circleTexture.getWidth() / 2.0f, circleTexture.getHeight() / 2.0f, circleTexture.getWidth() / 2.0f, paint);
        return circleTexture;
    }

    void updateColor(T seat) {
        FloatBuffer colorBuffer = mSeatColorBuffers.get(seat.getViewType());
        List<T> seats = mSeats.get(seat.getViewType());

        if (colorBuffer != null && seats != null) {
            int seatIndex = seats.indexOf(seat);
            if (seatIndex != -1) {
                float[] colors = GLUtils.getFloatColorComponents(seat.getColor());
                colorBuffer.position(seatIndex * NUM_VERTICES_IN_SQUARE * NUM_COLOR_COMPONENTS);
                for (int n = 0; n < NUM_VERTICES_IN_SQUARE; n++) {
                    colorBuffer.put(colors);
                }
            }
        }
    }

    private InputStream cacheInputStream(InputStream is) throws IOException {
        byte[] buff = new byte[10 * 1024];
        int numRead;
        ByteArrayOutputStream out = new ByteArrayOutputStream(is.available() > 0 ? is.available() : buff.length);

        while ((numRead = is.read(buff, 0, buff.length)) != -1) {
            out.write(buff, 0, numRead);
        }

        is.close();
        out.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    private int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int width = options.outWidth;
        final int height = options.outHeight;
        int inSampleSize = 1;

        while ((width / inSampleSize) > reqHeight || (height / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
        }

        return inSampleSize;
    }

    void notifyNeedReinitialization() {
        mSeatsInitialized = false;
        mBackgroundSetUp = false;
    }

    private int[] toPrimitiveArray(Integer[] array) {
        int[] out = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            out[i] = array[i];
        }

        return out;
    }

    private RectF toWorldCoordinates(RectF rect) {
        RectF worldCoordinates = new RectF(rect);
        worldCoordinates.top = -worldCoordinates.top;
        worldCoordinates.bottom = -worldCoordinates.bottom;
        return worldCoordinates;
    }

    private void makeRelativeToWorldCenter(RectF rect, float worldWidth, float worldHeight) {
        rect.offset(-worldWidth / 2.0f, worldHeight / 2.0f);
    }

    private int calcTextSize(RectF rect) {
        return (int) Math.min(Math.abs(rect.width()), Math.abs(rect.height())) / 2;
    }

    private int findBiggerPowerOfTwo(int value) {
        int b = 1;

        while (b < value) {
            b = b << 1;
        }

        return b;
    }

    private int findLesserPowerOfTwo(int value) {
        int b = 1;
        int prev = b;

        while (b < value) {
            prev = b;
            b = b << 1;
        }

        return b != value ? prev : b;
    }
}
