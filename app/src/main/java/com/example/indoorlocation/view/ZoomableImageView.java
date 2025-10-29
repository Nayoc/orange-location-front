package com.example.indoorlocation.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ZoomableImageView extends androidx.appcompat.widget.AppCompatImageView {
    private static final float MIN_SCALE_RATIO = 0.5f;
    private static final float MAX_SCALE_RATIO = 2f;

    // 坐标系相关变量
    private boolean drawCoordinateSystem = false;
    private float maxX;
    private float maxY;
    private Paint axisPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private float gridInterval = 1f; // 网格间隔（单位：米）

    // 标记点相关
    private PointF markerPoint = null; // 标记点坐标（米）

    private List<PointF> historyMarkerPoints = new ArrayList<>();
    private Paint redMarkerPaint; // 当前标记点画笔
    private Paint greenMarkerPaint; // 历史标记点画笔

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

    // 点击监听接口
    public interface OnMapClickListener {
        void onMapClick(float x, float y); // x,y为米坐标
    }

    private OnMapClickListener onMapClickListener;

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
        initialScale = 1.0f;

        // 初始化坐标系画笔
        initCoordinatePaints();

        // 初始化标记点画笔
        redMarkerPaint = new Paint();
        redMarkerPaint.setColor(Color.RED);
        redMarkerPaint.setStyle(Paint.Style.FILL);
        redMarkerPaint.setAntiAlias(true);

        greenMarkerPaint = new Paint();
        greenMarkerPaint.setColor(Color.GREEN);
        greenMarkerPaint.setStyle(Paint.Style.FILL);
        greenMarkerPaint.setAntiAlias(true);
    }

    private void initCoordinatePaints() {
        // 坐标轴画笔
        axisPaint = new Paint();
        axisPaint.setColor(Color.RED);
        axisPaint.setStrokeWidth(3f);
        axisPaint.setAntiAlias(true);

        // 网格线画笔
        gridPaint = new Paint();
        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setAntiAlias(true);

        // 文本画笔
        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30f);
        textPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 绘制坐标系（在图片之上）
        if (drawCoordinateSystem && getDrawable() != null) {
            drawCoordinateSystem(canvas);
        }

        // 绘制标记点
        if (markerPoint != null && getDrawable() != null) {
            drawMarker(canvas, markerPoint, redMarkerPaint);
        }

        if (historyMarkerPoints != null && getDrawable() != null) {
            drawHistoryMarker(canvas);
        }
    }

    private void drawCoordinateSystem(Canvas canvas) {
        // 获取图片原始尺寸
        int drawableWidth = getDrawable().getIntrinsicWidth();
        int drawableHeight = getDrawable().getIntrinsicHeight();

        // 获取当前矩阵值
        float[] values = new float[9];
        matrix.getValues(values);
        float scaleX = values[Matrix.MSCALE_X];
        float scaleY = values[Matrix.MSCALE_Y];
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];

        // 计算原点（图片左下角）在屏幕上的位置
        float originX = transX;
        float originY = transY + drawableHeight * scaleY;

        // 计算坐标轴终点（图片右上角）
        float endX = transX + drawableWidth * scaleX;
        float endY = transY;

        // 绘制X轴和Y轴
        canvas.drawLine(originX, originY, endX, originY, axisPaint); // X轴
        canvas.drawLine(originX, originY, originX, endY, axisPaint); // Y轴

        // 计算实际米与像素的比例
        float meterToPixelX = drawableWidth * scaleX / maxX;
        float meterToPixelY = drawableHeight * scaleY / maxY;

        // 绘制X轴网格和刻度
        for (int i = 0; i <= maxX; i += gridInterval) {
            float x = originX + i * meterToPixelX;

            // 绘制垂直线
            canvas.drawLine(x, originY, x, endY, gridPaint);

            // 绘制刻度和文字
            canvas.drawLine(x, originY - 10, x, originY + 10, axisPaint);
            canvas.drawText(String.valueOf(i), x - 10, originY + 40, textPaint);
        }

        // 绘制Y轴网格和刻度
        for (int i = 0; i <= maxY; i += gridInterval) {
            float y = originY - i * meterToPixelY;

            // 绘制水平线
            canvas.drawLine(originX, y, endX, y, gridPaint);

            // 绘制刻度和文字
            canvas.drawLine(originX - 10, y, originX + 10, y, axisPaint);
            canvas.drawText(String.valueOf(i), originX - 40, y + 10, textPaint);
        }
    }

    private void drawHistoryMarker(Canvas canvas) {
        if (historyMarkerPoints == null || historyMarkerPoints.isEmpty()) {
            return;
        }

        historyMarkerPoints.forEach(p -> drawMarker(canvas, p, greenMarkerPaint));
    }

    private void drawMarker(Canvas canvas, PointF point, Paint paint) {
        // 获取图片原始尺寸
        int drawableWidth = getDrawable().getIntrinsicWidth();
        int drawableHeight = getDrawable().getIntrinsicHeight();

        // 获取当前矩阵值
        float[] values = new float[9];
        matrix.getValues(values);
        float scaleX = values[Matrix.MSCALE_X];
        float scaleY = values[Matrix.MSCALE_Y];
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];

        // 计算原点（图片左下角）在屏幕上的位置
        float originX = transX;
        float originY = transY + drawableHeight * scaleY;

        // 计算实际米与像素的比例
        float meterToPixelX = drawableWidth * scaleX / maxX;
        float meterToPixelY = drawableHeight * scaleY / maxY;

        // 计算标记点在屏幕上的位置
        float screenX = originX + point.x * meterToPixelX;
        float screenY = originY - point.y * meterToPixelY;

        // 绘制红点标记
        canvas.drawCircle(screenX, screenY, 10 * Math.max(scaleX, scaleY), paint);
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
                // 处理单击事件（如果不是拖动或缩放）
                if (mode == DRAG && isSingleTap(event)) {
                    handleMapClick(event.getX(), event.getY());
                }
                mode = NONE;
                break;
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

                        // 降低缩放灵敏度：限制每次缩放幅度
                        if (scale > 1.1f) {
                            scale = 1.1f;
                        } else if (scale < 0.9f) {
                            scale = 0.9f;
                        }

                        // 计算当前缩放比例
                        Matrix tempMatrix = new Matrix(matrix);
                        float[] values = new float[9];
                        tempMatrix.getValues(values);
                        float currentScale = values[Matrix.MSCALE_X];

                        // 限制缩放范围
                        float targetScale = currentScale * scale;
                        if (targetScale < initialScale * MIN_SCALE_RATIO) {
                            scale = (initialScale * MIN_SCALE_RATIO) / currentScale;
                        } else if (targetScale > initialScale * MAX_SCALE_RATIO) {
                            scale = (initialScale * MAX_SCALE_RATIO) / currentScale;
                        }

                        matrix.set(savedMatrix);
                        matrix.postScale(scale, scale, mid.x, mid.y);
                        checkScale();
                        checkTranslation();
                    }
                }
                break;
        }
        setImageMatrix(matrix);
        return true;
    }

    // 判断是否为单击事件（移动距离很小）
    private boolean isSingleTap(MotionEvent event) {
        float dx = Math.abs(event.getX() - start.x);
        float dy = Math.abs(event.getY() - start.y);
        return dx < 10 && dy < 10;
    }

    // 处理地图点击事件，计算点击位置对应的坐标
    private void handleMapClick(float x, float y) {
        if (!drawCoordinateSystem || getDrawable() == null) return;

        int drawableWidth = getDrawable().getIntrinsicWidth();
        int drawableHeight = getDrawable().getIntrinsicHeight();

        float[] values = new float[9];
        matrix.getValues(values);
        float scaleX = values[Matrix.MSCALE_X];
        float scaleY = values[Matrix.MSCALE_Y];
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];

        // 计算原点（图片左下角）在屏幕上的位置
        float originX = transX;
        float originY = transY + drawableHeight * scaleY;

        // 计算实际米与像素的比例
        float pixelToMeterX = maxX / (drawableWidth * scaleX);
        float pixelToMeterY = maxY / (drawableHeight * scaleY);

        // 计算点击位置对应的米坐标
        float coordX = (x - originX) * pixelToMeterX;
        float coordY = (originY - y) * pixelToMeterY;

        // 限制坐标在有效范围内
        coordX = Math.max(0, Math.min(maxX, coordX));
        coordY = Math.max(0, Math.min(maxY, coordY));

        // 回调点击事件
        if (onMapClickListener != null) {
            onMapClickListener.onMapClick(coordX, coordY);
        }
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

        // 限制平移范围
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

        // 限制缩放范围
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

    // 坐标系控制方法
    public void setCoordinateSystem(float maxX, float maxY) {
        this.maxX = (float) Math.ceil(maxX); // 向上取整
        this.maxY = (float) Math.ceil(maxY); // 向上取整
        this.drawCoordinateSystem = true;
        invalidate(); // 重绘
    }

    public void clearCoordinateSystem() {
        this.drawCoordinateSystem = false;
        invalidate(); // 重绘
    }

    // 绘制历史rp点
    public void setRpTracePoints(List<PointF> points) {

        historyMarkerPoints = points.stream()
                .filter(point -> point.x >= 0 && point.x <= maxX && point.y >= 0 && point.y <= maxY)
                .collect(Collectors.toList());

        invalidate();
    }

    public void clearRpTracePoints() {

        historyMarkerPoints.clear();

        invalidate();
    }

    // 标记点控制方法
    public void setMarkerPoint(float x, float y) {
        // 检查坐标是否在有效范围内
        if (x >= 0 && x <= maxX && y >= 0 && y <= maxY) {
            this.markerPoint = new PointF(x, y);
        } else {
            this.markerPoint = null;
        }
        invalidate();
    }

    public void clearMarker() {
        this.markerPoint = null;
        invalidate();
    }

    // 设置点击监听器
    public void setOnMapClickListener(OnMapClickListener listener) {
        this.onMapClickListener = listener;
    }
}