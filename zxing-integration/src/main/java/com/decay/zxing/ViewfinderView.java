/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.decay.zxing;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;

import com.google.zxing.ResultPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public class ViewfinderView extends ViewFinder {
    protected static final String TAG = "ViewfinderView";

    protected static final long ANIMATION_DELAY = 25L;
    protected static final int CURRENT_POINT_OPACITY = 0xA0;
    protected static final int MAX_RESULT_POINTS = 20;
    protected static final int POINT_SIZE = 6;
    private static final int SCANNING_LINE_SPEED = 5;
    protected final Paint paint;
    protected final int maskColor;
    protected final int resultColor;
    protected final int laserColor;
    protected final int resultPointColor;
    private final int angleColor = Color.parseColor("#3897f0");
    protected Bitmap resultBitmap;
    protected int scannerAlpha;
    protected List<ResultPoint> possibleResultPoints;
    protected List<ResultPoint> lastPossibleResultPoints;
    protected CameraPreview cameraPreview;
    // Cache the framingRect and previewFramingRect, so that we can still draw it after the preview
    // stopped.
    protected Rect framingRect;
    protected Rect previewFramingRect;
    private Bitmap scanningLine;
    private Rect scanningLineRect = new Rect();
    private int scanningLineTop;
    private boolean showPossiblePoint = true;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.zxing_view_finder);

        maskColor = attributes.getColor(R.styleable.zxing_view_finder_zxing_viewfinder_mask,
            ContextCompat.getColor(context, R.color.zxing_viewfinder_mask));
        resultColor = attributes.getColor(R.styleable.zxing_view_finder_zxing_result_view,
            ContextCompat.getColor(context, R.color.zxing_result_view));
        laserColor = attributes.getColor(R.styleable.zxing_view_finder_zxing_viewfinder_laser,
            ContextCompat.getColor(context, R.color.zxing_viewfinder_laser));
        resultPointColor = attributes.getColor(R.styleable.zxing_view_finder_zxing_possible_result_points,
            ContextCompat.getColor(context, R.color.zxing_possible_result_points));
        showPossiblePoint = attributes.getBoolean(R.styleable.zxing_view_finder_zxing_show_possible_result_points,
            true);

        // using ps `ctrl + u` to change color
        Drawable line = ContextCompat.getDrawable(context, R.drawable.qrcode_scanning_line);
        scanningLine = ((BitmapDrawable) line).getBitmap();
        attributes.recycle();

        scannerAlpha = 0;
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = null;
    }

    @Override
    public void setCameraPreview(CameraPreview view) {
        this.cameraPreview = view;
        view.addStateListener(new CameraPreview.StateListener() {
            @Override
            public void previewSized() {
                refreshSizes();
                invalidate();
            }

            @Override
            public void previewStarted() {}

            @Override
            public void previewStopped() {}

            @Override
            public void cameraError(Exception error) {}
        });
    }

    protected void refreshSizes() {
        if (cameraPreview == null) {
            return;
        }
        Rect framingRect = cameraPreview.getFramingRect();
        Rect previewFramingRect = cameraPreview.getPreviewFramingRect();
        if (framingRect != null && previewFramingRect != null) {
            this.framingRect = framingRect;
            this.previewFramingRect = previewFramingRect;
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        refreshSizes();
        if (framingRect == null || previewFramingRect == null) {
            return;
        }

        Rect frame = framingRect;
        Rect previewFrame = previewFramingRect;

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        // 画出扫描框外面的阴影部分，共四个部分，扫描框的上面到屏幕上面，扫描框的下面到屏幕下面，
        // 扫描框的左边面到屏幕左边，扫描框的右边到屏幕右边
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        } else {
            // 绘制扫描框
            paint.setColor(Color.GRAY);
            canvas.drawRect(frame.left, frame.top - 2, frame.right, frame.top, paint);
            canvas.drawRect(frame.left - 2, frame.top, frame.left, frame.bottom, paint);
            canvas.drawRect(frame.right, frame.top, frame.right + 2, frame.bottom, paint);
            canvas.drawRect(frame.left, frame.bottom, frame.right, frame.bottom + 2, paint);

            drawScanningFrameAngle(canvas, frame);
            drawScanningLine(canvas, frame);
            if (showPossiblePoint) {
                drawPossiblePoint(canvas, frame, previewFrame);
            }

            // Request another update at the animation interval, but only repaint the laser line,
            // not the entire viewfinder mask.
            postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top, frame.right, frame.bottom);
        }
    }

    private void drawScanningFrameAngle(Canvas canvas, Rect frame) {
        int angleLength = 48;
        int angleWidth = 12;
        int top = frame.top;
        int bottom = frame.bottom;
        int left = frame.left;
        int right = frame.right;

        paint.setColor(angleColor);
        // 左上
        canvas.drawRect(left - angleWidth, top - angleWidth, left + angleLength, top, paint);
        canvas.drawRect(left - angleWidth, top - angleWidth, left, top + angleLength, paint);
        // 左下
        canvas.drawRect(left - angleWidth, bottom, left + angleLength, bottom + angleWidth, paint);
        canvas.drawRect(left - angleWidth, bottom - angleLength, left, bottom + angleWidth, paint);
        // 右上
        canvas.drawRect(right - angleLength, top - angleWidth, right + angleWidth, top, paint);
        canvas.drawRect(right, top - angleWidth, right + angleWidth, top + angleLength, paint);
        // 右下
        canvas.drawRect(right - angleLength, bottom, right, bottom + angleWidth, paint);
        canvas.drawRect(right, bottom - angleLength, right + angleWidth, bottom + angleWidth, paint);
    }

    // 绘制扫描线
    private void drawScanningLine(Canvas canvas, Rect frame) {
        if (scanningLineTop < frame.top + 5) {
            scanningLineTop = frame.top + 5;
        }
        scanningLineTop += SCANNING_LINE_SPEED;
        if (scanningLineTop >= frame.bottom - 10) {
            scanningLineTop = frame.top + 5;
        }
        scanningLineRect.left = frame.left;
        scanningLineRect.right = frame.right;
        scanningLineRect.top = scanningLineTop;
        scanningLineRect.bottom = scanningLineTop + 16;
        canvas.drawBitmap(scanningLine, null, scanningLineRect, paint);
    }

    // 绘制可能的识别点
    private void drawPossiblePoint(Canvas canvas, Rect frame, Rect previewFrame) {
        float scaleX = frame.width() / (float) previewFrame.width();
        float scaleY = frame.height() / (float) previewFrame.height();

        List<ResultPoint> currentPossible = possibleResultPoints;
        List<ResultPoint> currentLast = lastPossibleResultPoints;
        int frameLeft = frame.left;
        int frameTop = frame.top;
        if (currentPossible.isEmpty()) {
            lastPossibleResultPoints = null;
        } else {
            possibleResultPoints = new ArrayList<>(5);
            lastPossibleResultPoints = currentPossible;
            paint.setAlpha(CURRENT_POINT_OPACITY);
            paint.setColor(resultPointColor);
            for (ResultPoint point : currentPossible) {
                canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX), frameTop + (int) (point.getY() * scaleY),
                    POINT_SIZE, paint);
            }
        }
        if (currentLast != null) {
            paint.setAlpha(CURRENT_POINT_OPACITY / 2);
            paint.setColor(resultPointColor);
            float radius = POINT_SIZE / 2.0f;
            for (ResultPoint point : currentLast) {
                canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX), frameTop + (int) (point.getY() * scaleY),
                    radius, paint);
            }
        }
    }

    public void drawViewfinder() {
        Bitmap resultBitmap = this.resultBitmap;
        this.resultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();
        }
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live scanning display.
     *
     * @param result An image of the result.
     */
    public void drawResultBitmap(Bitmap result) {
        resultBitmap = result;
        invalidate();
    }

    /**
     * Only call from the UI thread.
     *
     * @param point a point to draw, relative to the preview frame
     */
    @Override
    public void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = possibleResultPoints;
        points.add(point);
        int size = points.size();
        if (size > MAX_RESULT_POINTS) {
            // trim it
            points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
        }
    }
}