package id.wraddev.sketchware.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;

import static id.wraddev.sketchware.Activities.MainActivity.TAG;

public class ScalableImageView extends AppCompatImageView {

    public static final int DEFAULT_COLOR = Color.YELLOW;
    // Always in these 3 states
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private static final int CLICK = 3;
    private static final float TOUCH_TOLERANCE = 4;
    public static int sBrushSize = 10;
    protected float mOriginWidth;
    protected float mOriginHeight;
    private Matrix mMatrix;
    // Mode
    private int mMode = NONE;
    // For zooming usage
    private PointF mLast = new PointF();
    private PointF mStart = new PointF();
    private float mMinScale = 1f;
    private float mMaxScale = 4f;
    private float[] mM;
    private int mViewWidth;
    private int mViewHeight;
    private float mSaveScale = 1f;
    private int mOldMeasureWidth;
    private int mOldMeasureHeight;
    private ScaleGestureDetector mScaleDetector;
    private float mX, mY;
    private int mCurrentColor;
    private int mStrokeWidth;
    private ArrayList<FingerPath> paths = new ArrayList<>();
    private Path mPath;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Paint mPaint;
    private Paint mBitmapPaint = new Paint(Paint.DITHER_FLAG);


    public ScalableImageView(Context context) {
        super(context);
        sharedConstructing(context);
    }

    public ScalableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        sharedConstructing(context);
    }

    private void sharedConstructing(Context context) {
        super.setClickable(true);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(DEFAULT_COLOR);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);

        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mMatrix = new Matrix();
        mM = new float[9];
        setImageMatrix(mMatrix);
        setScaleType(ScaleType.MATRIX);

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mScaleDetector.onTouchEvent(event);
                PointF current = new PointF(event.getX(), event.getY());

                if (event.getPointerCount() == 2) {
                    Log.e(TAG, "onTouch: 2 FINGERS");

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            Log.e(TAG, "#2 ACTION_DOWN");
                            mLast.set(current);
                            mStart.set(mLast);
                            mMode = DRAG;
                            break;

                        case MotionEvent.ACTION_MOVE:
                            if (mMode == DRAG) {
                                Log.e(TAG, "#2 ACTION_MOVE");
                                float deltaX = current.x - mLast.x;
                                float deltaY = current.y - mLast.y;
                                float fixTransX = getFixDragTrans(deltaX, mViewWidth, mOriginWidth * mSaveScale);
                                float fixTransY = getFixDragTrans(deltaY, mViewHeight, mOriginHeight * mSaveScale);
                                mMatrix.postTranslate(fixTransX, fixTransY);
                                fixTrans();
                                mLast.set(current.x, current.y);
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                            Log.e(TAG, "#2 ACTION_UP");
                            mMode = NONE;
                            int xDiff = (int) Math.abs(current.x - mStart.x);
                            int yDiff = (int) Math.abs(current.y - mStart.y);

                            if (xDiff < CLICK && yDiff < CLICK) performClick();
                            break;

                        case MotionEvent.ACTION_POINTER_UP:
                            Log.e(TAG, "#2 ACTION_POINTER_UP");
                            mMode = NONE;
                            break;
                    }

                } else if (event.getPointerCount() == 1) {
                    Log.e(TAG, "onTouch: 1 FINGER");

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            Log.e(TAG, "onTouch action down X: " + event.getX());
                            Log.e(TAG, "onTouch action down Y: " + event.getY());
                            touchStart(event.getX(), event.getY());
                            postInvalidate();
                            break;

                        case MotionEvent.ACTION_MOVE:
//                            if (mMode == DRAG)
                            touchMove(event.getX(), event.getY());
                            postInvalidate();
                            break;

                        case MotionEvent.ACTION_UP:
                            touchEnd();
                            postInvalidate();
                            break;

                        case MotionEvent.ACTION_POINTER_UP:
                            Log.e(TAG, "#1 ACTION_POINTER_UP");
                            break;
                    }
                }

                setImageMatrix(mMatrix);
                invalidate();
                return true;    // indicate event was handled
            }
        });
    }

    public void init(DisplayMetrics metrics) {
        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        this.mCanvas = new Canvas(mBitmap);

        mCurrentColor = DEFAULT_COLOR;
        mStrokeWidth = sBrushSize;
    }

    public void init(DisplayMetrics metrics, Canvas canvas) {
        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        this.mCanvas = canvas; /*new Canvas(mBitmap);*/

        mCurrentColor = DEFAULT_COLOR;
        mStrokeWidth = sBrushSize;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();

        for (FingerPath path : paths) {
            mPaint.setColor(path.mColor);
            mPaint.setStrokeWidth(path.mStrokeWidth);
            mCanvas.drawPath(path.mPath, mPaint);
            Log.e(TAG, "onDraw: ");
        }

        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        canvas.restore();
    }

    private void touchStart(float x, float y) {
        mPath = new Path();
        FingerPath fp = new FingerPath(mCurrentColor, mStrokeWidth, mPath);
        paths.add(fp);

        mPath.reset();
        mPath.moveTo(x, y);
        mX = x;
        mY = y;
    }

    private void touchMove(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);

        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
            mX = x;
            mY = y;
        }
    }

    private void touchEnd() {
        mPath.lineTo(mX, mY);
    }

    public void setMaxZoom(float maxZoom) {
        mMaxScale = maxZoom;
    }

    private float getFixDragTrans(float delta, float viewSize, float contentSize) {
        if (contentSize <= viewSize) return 0;
        return delta;
    }

    private void fixTrans() {
        mMatrix.getValues(mM);
        float transX = mM[Matrix.MTRANS_X];
        float transY = mM[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, mViewWidth, mOriginWidth * mSaveScale);
        float fixTransY = getFixTrans(transY, mViewHeight, mOriginHeight * mSaveScale);

        if (fixTransX != 0 || fixTransY != 0) mMatrix.postTranslate(fixTransX, fixTransY);
    }

    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans;
        float maxTrans;

        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }
        if (trans < minTrans) return -trans + minTrans;
        if (trans > maxTrans) return -trans + maxTrans;
        return 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        mViewWidth = MeasureSpec.getSize(widthMeasureSpec);
        mViewHeight = MeasureSpec.getSize(heightMeasureSpec);

        /*
         * Rescale image on rotation
         */

        if (mOldMeasureHeight == mViewWidth && mOldMeasureHeight == mViewHeight
                || mViewWidth == 0 || mViewHeight == 0) return;

        mOldMeasureHeight = mViewHeight;
        mOldMeasureWidth = mViewWidth;

        if (mSaveScale == 1) {
            // Fit to screen
            float scale;

            Drawable dwb = getDrawable();
            if (dwb == null || dwb.getIntrinsicWidth() == 0 || dwb.getIntrinsicHeight() == 0)
                return;

            int bmpWidth = dwb.getIntrinsicWidth();
            int bmpHeight = dwb.getIntrinsicHeight();

            Log.e(TAG, "bmWidth: " + bmpWidth + " bmHeight : " + bmpHeight);

            float scaleX = (float) mViewWidth / bmpWidth;
            float scaleY = (float) mViewHeight / bmpHeight;
            scale = Math.min(scaleX, scaleY);
            mMatrix.setScale(scale, scale);

            // Center the image

            float redundantYSpace = mViewHeight - (scale * bmpHeight);
            float redundantXSpace = mViewWidth - (scale * bmpWidth);

            redundantXSpace /= 2;
            redundantYSpace /= 2;

            mMatrix.postTranslate(redundantXSpace, redundantYSpace);

            mOriginWidth = mViewWidth - 2 * redundantXSpace;
            mOriginHeight = mViewHeight - 2 * redundantYSpace;
            setImageMatrix(mMatrix);
        }

        fixTrans();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mMode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float originScale = mSaveScale;
            mSaveScale *= scaleFactor;

            if (mSaveScale > mMaxScale) {
                mSaveScale = mMaxScale;
                scaleFactor = mMaxScale / originScale;
            } else if (mSaveScale < mMinScale) {
                mSaveScale = mMinScale;
                scaleFactor = mMinScale / originScale;
            }

            if (mOriginWidth * mSaveScale <= mViewWidth
                    || mOriginHeight * mSaveScale <= mViewHeight)
                mMatrix.postScale(scaleFactor, scaleFactor, (float) mViewWidth / 2, (float) mViewHeight / 2);
            else
                mMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());

            fixTrans();
            return true;
        }
    }
}