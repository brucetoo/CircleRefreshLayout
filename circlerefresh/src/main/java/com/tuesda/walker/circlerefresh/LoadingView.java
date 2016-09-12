package com.tuesda.walker.circlerefresh;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Created by Bruce Too
 * On 9/7/16.
 * At 17:06
 */
public class LoadingView extends View {

    private static final String TAG = LoadingView.class.getSimpleName();

    private static final int DEFAULT_ROTATE_STEP_ANGLE = 15;
    private static final int DEFAULT_TEXT_COLOR = Color.WHITE;
    private static final int DEFAULT_TEXT_SIZE = 12;
    private static final String DEFAULT_TEXT = "正在刷新";
    private static final int DEFAULT_BG_COLOR = 0xff1295f4;
    private static final int DEFAULT_BASE_HEIGHT = 84;
    private static final int DEFAULT_TOP_OFFSET = 40;
    private static final int DEFAULT_BOTTOM_OFFSET = 40;
    private static final int DEFAULT_MARGIN_BOTTOM = 10;
    private static final int DEFAULT_REFRESH_THRESHOLD = 5;

    private float mBaseHeight;
    private float mBaseBottomOffset;
    private float mBaseTopOffset;

    private Paint mBackPaint;
    private Paint mTextPaint;
    private Paint mCirclePaint;
    private Path mPath;
    private Path mEndBottomPath;

    private float mCircleRadius;
    private int mMarginBottom;
    private int mRefreshThreshold;

    private int mViewWidth;
    private int mViewHeight;

    private float mDragDelta;
    private float mDragStart;
    private float mDragEnd;

    private String mText;
    private Rect mTextRect;
    private Bitmap mCircleBitmap;
    private Matrix mCircleMatrix;
    private RectF mBitmapRectF;
    private int mRotateAngle;

    private ViewStatus mCurStatus = ViewStatus.STATUS_NORMAL;

    enum ViewStatus {
        STATUS_NORMAL,
        STATUS_PULL_DOWN,
        STATUS_DRAW_REFRESH,
        STATUS_REFRESHING,
        STATUS_STOP
    }

    public LoadingView(Context context) {
        this(context, null);
    }

    public LoadingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LoadingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {

        mBaseHeight = dp2px(DEFAULT_BASE_HEIGHT);
        mBaseBottomOffset = dp2px(DEFAULT_BOTTOM_OFFSET);
        mBaseTopOffset = dp2px(DEFAULT_TOP_OFFSET);
        //can not modify this
        mCircleRadius = dp2px(5);
        mMarginBottom = dp2px(DEFAULT_MARGIN_BOTTOM);
        mRefreshThreshold = dp2px(DEFAULT_REFRESH_THRESHOLD);

        mBackPaint = new Paint();
        mBackPaint.setAntiAlias(true);
        mBackPaint.setColor(DEFAULT_BG_COLOR);
        mBackPaint.setStyle(Paint.Style.FILL);

        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setStrokeWidth(dp2px(2));
        mTextPaint.setTextSize(dp2px(DEFAULT_TEXT_SIZE));
        mTextPaint.setColor(DEFAULT_TEXT_COLOR);

        mCirclePaint = new Paint();
        mCirclePaint.setAntiAlias(true);
        mCirclePaint.setStyle(Paint.Style.STROKE);
        mCirclePaint.setStrokeJoin(Paint.Join.ROUND);
        mCirclePaint.setStrokeCap(Paint.Cap.ROUND);
        mCircleBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.icon_circle);

        mPath = new Path();
        mCircleMatrix = new Matrix();
        mText = DEFAULT_TEXT;
        mTextRect = new Rect();
        mTextPaint.getTextBounds(mText, 0, mText.length(), mTextRect);

        mBitmapRectF = new RectF(0, 0, mCircleBitmap.getWidth(), mCircleBitmap.getHeight());

        mEndBottomPath = new Path();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (height > mBaseHeight + mBaseBottomOffset) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec((int) (mBaseBottomOffset + mBaseHeight), MeasureSpec.getMode(heightMeasureSpec));
        } else if (height < mBaseHeight) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec((int) (mBaseHeight), MeasureSpec.getMode(heightMeasureSpec));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        mViewWidth = getWidth();
        mViewHeight = getHeight();

        mDragStart = mViewHeight - mBaseTopOffset - mBaseBottomOffset;
        mDragEnd = mViewHeight;

        mEndBottomPath.reset();
        mEndBottomPath.moveTo(0, mBaseHeight);
        mEndBottomPath.quadTo(mViewWidth / 2, mDragEnd + (mDragEnd - mBaseHeight) / 2, mViewWidth, mBaseHeight);

        Log.e(TAG, "width:" + mViewWidth + "  -> height:" + mViewHeight + " -> baseHeight:" + mBaseHeight +
                " -> baseTopDelta:" + mBaseTopOffset + " -> baseBottomDelta:" + mBaseBottomOffset);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Log.e(TAG, "onDraw Status: " + mCurStatus);

        switch (mCurStatus) {
            case STATUS_NORMAL:
                mDragDelta = mDragStart + (mDragStart - mBaseHeight) / 2;
                drawUp2DownDrag(canvas);
                break;
            case STATUS_PULL_DOWN:
                drawUp2DownDrag(canvas);
                break;
            case STATUS_DRAW_REFRESH:
                drawRefresh(canvas, mCurStatus);
                break;
            case STATUS_REFRESHING:
                drawRefresh(canvas, mCurStatus);
                break;
            case STATUS_STOP:

                break;
            default:
                mDragDelta = mDragStart + (mDragStart - mBaseHeight) / 2;
                drawUp2DownDrag(canvas);
                break;
        }

    }

    private void drawRefresh(Canvas canvas, ViewStatus status) {

        drawUp2DownDrag(canvas);

        //get point of the middle path
        PathMeasure pm = new PathMeasure(mEndBottomPath, false);
        float point[] = {0f, 0f};
        pm.getPosTan(pm.getLength() * 0.5f, point, null);

        float textX = (mViewWidth - mTextRect.width()) / 2;
        float textY = point[1] - mMarginBottom;

        canvas.drawText(mText, textX, textY, mTextPaint);

        float circleLeft = mViewWidth / 2 - mCircleRadius;
        float circleTop = point[1] - mMarginBottom * 2 - mTextRect.height() - mCircleRadius * 2;
//        Log.e(TAG, "drawRefresh -> circleLeft:" + circleLeft + "  circleTop:" + circleTop
//        + "  bitmapWidth:" + mBitmapRectF.width() + "  radius" + mCircleRadius);

        if (status == ViewStatus.STATUS_DRAW_REFRESH) {
            mCircleMatrix.setRotate((mRotateAngle + DEFAULT_ROTATE_STEP_ANGLE) % 360, mBitmapRectF.width() / 2, mBitmapRectF.height() / 2);
            mCircleMatrix.postTranslate(circleLeft - mCircleRadius, circleTop - mCircleRadius);
            canvas.drawBitmap(mCircleBitmap, mCircleMatrix, null);

            //when drag to bottom and start refreshing
            if (mDragDelta == mDragEnd + (mDragEnd - mBaseHeight) / 2) {
                mCurStatus = ViewStatus.STATUS_REFRESHING;
                invalidate();
            }

        } else {
            mRotateAngle += DEFAULT_ROTATE_STEP_ANGLE;
            mCircleMatrix.setRotate(mRotateAngle % 360, mBitmapRectF.width() / 2, mBitmapRectF.height() / 2);
            mCircleMatrix.postTranslate(circleLeft - mCircleRadius, circleTop - mCircleRadius);
            canvas.drawBitmap(mCircleBitmap, mCircleMatrix, null);
            invalidate();
        }
    }


    private void drawUp2DownDrag(Canvas canvas) {
        mPath.reset();
        mPath.moveTo(0, 0);
        mPath.lineTo(0, mBaseHeight);
        mPath.quadTo(mViewWidth / 2, mDragDelta, mViewWidth, mBaseHeight);
        mPath.lineTo(mViewWidth, 0);
        canvas.drawPath(mPath, mBackPaint);
    }

    private int dp2px(int px) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px, getContext().getResources().getDisplayMetrics());
    }

    public void startDrag(float delta) {

        mDragDelta = Math.min(Math.max(mDragStart + delta, mBaseHeight - mBaseTopOffset), mDragEnd);

        mDragDelta += (mDragDelta - mBaseHeight) / 2;

//        Log.e(TAG, "startDrag :" + mDragDelta);
        mCurStatus = ViewStatus.STATUS_PULL_DOWN;
        //time to draw refresh bitmap and text
        if (delta >= mBaseTopOffset + mBaseBottomOffset - mRefreshThreshold) {
            mCurStatus = ViewStatus.STATUS_DRAW_REFRESH;
            int alpha = (int) (255 * (mBaseTopOffset + mBaseBottomOffset - delta)) / mRefreshThreshold;
            Log.e(TAG, "startDrag -> alpha:" + alpha);
            if (alpha > 255) {
                alpha = 255;
            }
            if (alpha < 0) {
                alpha = 0;
            }
            mTextPaint.setAlpha(255 - alpha);
        } else {
            mTextPaint.setAlpha(255);
        }
        invalidate();
    }

    public void releaseDrag() {

        ValueAnimator backAnim = ValueAnimator.ofFloat(mDragDelta, 0);
        backAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        backAnim.setDuration(500);
        backAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                startDrag((Float) animation.getAnimatedValue());
            }
        });
        backAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startBounceAnim();
            }
        });
        backAnim.start();
    }

    private void startBounceAnim() {
        ValueAnimator bounceAnim = ValueAnimator.ofFloat(-10, 10, -8, 8, -6, 6, -4, 4, -2, 2, 0);
//        bounceAnim.setInterpolator(new OvershootInterpolator(6));
        bounceAnim.setDuration(1000);
        bounceAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
//                Log.e(TAG, "startBounceAnim value:" + (Float) animation.getAnimatedValue());
                startDrag((Float) animation.getAnimatedValue());
            }
        });
        bounceAnim.start();
    }

}
