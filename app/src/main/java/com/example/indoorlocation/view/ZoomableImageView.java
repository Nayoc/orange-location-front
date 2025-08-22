package com.example.indoorlocation.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class ZoomableImageView extends androidx.appcompat.widget.AppCompatImageView {
    private static final float MIN_SCALE_RATIO = 0.5f;
    private static final float MAX_SCALE_RATIO = 2f;

    private float initialScale = 1.0f; // 初始缩放比例（默认1.0，即原始尺寸）
    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;
    private PointF start = new PointF();
    private PointF mid = new PointF();
    private float oldDist = 1f;

    public ZoomableImageView(Context context) {
        super(context);
        init();
    }

    public ZoomableImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ZoomableImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setScaleType(ScaleType.MATRIX);
        // 初始化时记录初始缩放（默认1.0）
        initialScale = 1.0f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN:
                savedMatrix.set(matrix);
                start.set(event.getX(), event.getY());
                mode = DRAG;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                oldDist = spacing(event);
                if (oldDist > 10f) {
                    savedMatrix.set(matrix);
                    midPoint(mid, event);
                    mode = ZOOM;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    // 处理平移逻辑
                    matrix.set(savedMatrix);
                    float dx = event.getX() - start.x; // 计算X方向平移距离
                    float dy = event.getY() - start.y; // 计算Y方向平移距离
                    matrix.postTranslate(dx, dy); // 应用平移
                    checkTranslation(); // 限制平移范围
                } else if (mode == ZOOM) {
                    // 处理缩放逻辑
                    float newDist = spacing(event);
                    if (newDist > 10f) {
                        savedMatrix.set(matrix);
                        float scale = newDist / oldDist;

                        // 降低缩放灵敏度：限制每次缩放幅度（最大1.1倍/最小0.9倍）
                        // 避免因手势微小变化导致缩放幅度过大
                        if (scale > 1.1f) {
                            scale = 1.1f; // 每次最大放大1.1倍
                        } else if (scale < 0.9f) {
                            scale = 0.9f; // 每次最大缩小到0.9倍
                        }

                        // 计算当前缩放比例（基于初始缩放）
                        Matrix tempMatrix = new Matrix(matrix);
                        float[] values = new float[9];
                        tempMatrix.getValues(values);
                        float currentScale = values[Matrix.MSCALE_X];

                        // 限制缩放范围：初始的0.5~2倍
                        float targetScale = currentScale * scale;
                        if (targetScale < initialScale * MIN_SCALE_RATIO) {
                            scale = (initialScale * MIN_SCALE_RATIO) / currentScale;
                        } else if (targetScale > initialScale * MAX_SCALE_RATIO) {
                            scale = (initialScale * MAX_SCALE_RATIO) / currentScale;
                        }

                        matrix.set(savedMatrix);
                        matrix.postScale(scale, scale, mid.x, mid.y); // 基于双指中点缩放
                        checkScale(); // 限制缩放范围
                        checkTranslation(); // 限制平移范围（缩放后可能需要重新调整平移）
                    }
                }
                break;
        }
        setImageMatrix(matrix);
        return true;
    }

    // 检查缩放和平移是否超出限制
    private void checkScaleAndTranslation() {
        checkScale();
        checkTranslation();
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    private void checkTranslation() {
        Matrix matrix = getImageMatrix();
        float[] values = new float[9];
        matrix.getValues(values);
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];
        float drawableWidth = getDrawable().getIntrinsicWidth() * Math.abs(values[Matrix.MSCALE_X]);
        float drawableHeight = getDrawable().getIntrinsicHeight() * Math.abs(values[Matrix.MSCALE_Y]);
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        // 限制平移范围：不超过图片对应尺寸的一半
        if (transX > drawableWidth / 2 - viewWidth / 2) {
            transX = drawableWidth / 2 - viewWidth / 2;
        } else if (transX < -drawableWidth / 2 + viewWidth / 2) {
            transX = -drawableWidth / 2 + viewWidth / 2;
        }
        if (transY > drawableHeight / 2 - viewHeight / 2) {
            transY = drawableHeight / 2 - viewHeight / 2;
        } else if (transY < -drawableHeight / 2 + viewHeight / 2) {
            transY = -drawableHeight / 2 + viewHeight / 2;
        }

        matrix.setTranslate(transX, transY);
        setImageMatrix(matrix);
    }

    private void checkScale() {
        Matrix matrix = getImageMatrix();
        float[] values = new float[9];
        matrix.getValues(values);
        float currentScale = values[Matrix.MSCALE_X];

        // 限制缩放范围：基于初始缩放的0.5~2倍
        if (currentScale < initialScale * MIN_SCALE_RATIO) {
            matrix.setScale(initialScale * MIN_SCALE_RATIO, initialScale * MIN_SCALE_RATIO);
        } else if (currentScale > initialScale * MAX_SCALE_RATIO) {
            matrix.setScale(initialScale * MAX_SCALE_RATIO, initialScale * MAX_SCALE_RATIO);
        }
        setImageMatrix(matrix);
    }

    public void reset() {
        matrix.setScale(1f, 1f);
        matrix.postTranslate(0, 0);
        setImageMatrix(matrix);
    }
}
