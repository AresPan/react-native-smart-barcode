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

package com.reactnativecomponent.barcode.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;

import com.google.zxing.ResultPoint;
import com.reactnativecomponent.barcode.R;
import com.reactnativecomponent.barcode.camera.CameraManager;
import com.reactnativecomponent.barcode.utils.LangUtils;


import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View
{
    /**扫描页面透明度*/
    private static final int[] SCANNER_ALPHA = { 0, 64, 128, 192, 255, 192,
            128, 64 };
    /**动画延迟*/
    private static final long ANIMATION_DELAY = 10L;
    private static final int OPAQUE = 0xFF;//不透明

    /**
     * 四个蓝色边角对应的长度
     */
    private int ScreenRate;

    /**
     * 四个蓝色边角对应的宽度
     */
    public int CORNER_WIDTH = 1;
    /**
     * 扫描框中的中间线的宽度
     */
    private int MIDDLE_LINE_WIDTH = 3;

    /**
     * 扫描框中的中间线的与扫描框左右的间隙
     */
    private static final int MIDDLE_LINE_PADDING = 5;

    /**
     * 中间那条线每次刷新移动的距离
     */
    private static int SPEEN_DISTANCE = 6;

    /**
     * 手机的屏幕密度
     */
    private static float density;
    /**
     * 字体大小
     */
    private static final int TEXT_SIZE = 16;

    public String ShowText;
    /**
     * 扫码考勤第一个提示语
     */
    public String showFirstText;
    /**
     * 字体距离扫描框下面的距离
     */
    private static final int TEXT_PADDING_TOP = 30;

    private final Paint paint;
    private final Paint paintLine;
    /**返回的照片*/
    private Bitmap resultBitmap;
    /**遮盖物颜色*/
    private final int maskColor;
    /**结果颜色*/
    private final int resultColor;
    /**框架颜色*/
    public int frameColor;
    /**
     * 扫描线渐变色中间色
     */
    public int frameBaseColor;
    /**扫描线颜色*/
    private final int laserColor;
    /**结果点的颜色*/
    private final int resultPointColor;
    private int scannerAlpha;//扫描透明度
    /**可能的结果点数*/
    private Collection<ResultPoint> possibleResultPoints;
    /**最后的结果点数*/
    private Collection<ResultPoint> lastPossibleResultPoints;
    /**
     * 是否画中间线
     */
    public boolean drawLine = false;

    /**
     * 中间滑动线的最顶端位置
     */
    private int slideTop;

    /**
     * 中间滑动线的最底端位置
     */
    private int slideBottom;
    private boolean isFirst;

    /**
     *s扫码横线的移动时间
     */
    public int scanTime;


    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context,int time,int color)
    {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        //将像素转化成dp
        ScreenRate = (int) (15 * density);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint();
        paintLine=new Paint();
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask);
        resultColor = resources.getColor(R.color.backgroud);
        frameColor = color;
        frameBaseColor=reSetColor(frameColor);
        laserColor = resources.getColor(R.color.viewfinder_laser);
        resultPointColor = resources.getColor(R.color.possible_result_points);
        scannerAlpha = 0;
        possibleResultPoints = new HashSet<ResultPoint>(5);
        drawLine=true;
        scanTime=time;
    }

    public void setCORNER_WIDTH(int CORNER_WIDTH) {
        this.CORNER_WIDTH = CORNER_WIDTH;
    }

    public void setMIDDLE_LINE_WIDTH(int MIDDLE_LINE_WIDTH) {
        this.MIDDLE_LINE_WIDTH = MIDDLE_LINE_WIDTH;
    }

    @Override
    public void onDraw(Canvas canvas)
    {
        Rect frame = CameraManager.get().getFramingRect();
        if (frame == null)
        {
            return;
        }

        if (!isFirst)
        {
            isFirst = true;
            slideTop = frame.top + CORNER_WIDTH;
            slideBottom = frame.bottom - CORNER_WIDTH;

            SPEEN_DISTANCE= (slideBottom-slideTop)/((scanTime/16)+2);
        }

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        //画区域
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1,
                paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);

        if (resultBitmap != null)
        {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(OPAQUE);
            canvas.drawBitmap(resultBitmap, frame.left, frame.top, paint);
        }
        else
        {

            // Draw a two pixel solid black border inside the framing rect
            //画框架
            paint.setColor(Color.WHITE);
            //自己画（扫描框边上的角，共8个部分）
            canvas.drawRect(frame.left - CORNER_WIDTH/2 - 5 , frame.top
                    - CORNER_WIDTH /2 - 5, frame.left + ScreenRate - 5, frame.top
                    + CORNER_WIDTH /2 - 5, paint);//左上角横线
            canvas.drawRect(frame.left - CORNER_WIDTH/2  - 5, frame.top
                            - CORNER_WIDTH/2 - 5, frame.left + CORNER_WIDTH/2 - 5,
                    frame.top + ScreenRate - 5, paint);//左上角竖线
            canvas.drawRect(frame.left - CORNER_WIDTH/2 - 5, frame.bottom
                    - ScreenRate + 5, frame.left + CORNER_WIDTH/2 - 5, frame.bottom
                    + CORNER_WIDTH/2 + 5, paint);//左下角竖线
            canvas.drawRect(frame.left - CORNER_WIDTH/2 - 5, frame.bottom
                    - CORNER_WIDTH/2 + 5, frame.left + ScreenRate - 5, frame.bottom
                    + CORNER_WIDTH/2 + 5, paint);//左下角横线
            canvas.drawRect(frame.right - ScreenRate + 5, frame.top - CORNER_WIDTH/2 - 5
                    , frame.right + CORNER_WIDTH/2 + 5, frame.top
                            + CORNER_WIDTH/2 - 5, paint);//右上横线
            canvas.drawRect(frame.right - CORNER_WIDTH / 2 + 5, frame.top
                            - CORNER_WIDTH / 2 - 5, frame.right + CORNER_WIDTH / 2 + 5,
                    frame.top + ScreenRate - 5, paint);//右上竖线
            canvas.drawRect(frame.right - CORNER_WIDTH/2 + 5, frame.bottom
                    - ScreenRate + 5, frame.right + CORNER_WIDTH /2 + 5, frame.bottom
                    + CORNER_WIDTH /2 + 5, paint);//右下竖线
            canvas.drawRect(frame.right - ScreenRate + 5, frame.bottom
                            - CORNER_WIDTH / 2 + 5, frame.right + CORNER_WIDTH / 2 + 5,
                    frame.bottom + CORNER_WIDTH / 2 + 5, paint);

            //画中间移动的线 (int)(SPEEN_DISTANCE*density+0.5f)
            if(drawLine) {
                slideTop += SPEEN_DISTANCE;
                if (slideTop >= slideBottom) {
                    slideTop = frame.top + CORNER_WIDTH;
                }
                //自己画
//                paintLine.setColor(frameColor);
//
//                Shader mShader = new LinearGradient(frame.left + CORNER_WIDTH, slideTop, frame.right
//                        - CORNER_WIDTH, slideTop + MIDDLE_LINE_WIDTH,new int[] {Color.TRANSPARENT,frameBaseColor,frameColor,frameColor,frameColor,frameColor,frameColor,frameBaseColor,Color.TRANSPARENT},null, Shader.TileMode.CLAMP);
//            //新建一个线性渐变，前两个参数是渐变开始的点坐标，第三四个参数是渐变结束的点的坐标。
//            // 连接这2个点就拉出一条渐变线了，玩过PS的都懂。然后那个数组是渐变的颜色。
//                // 下一个参数是渐变颜色的分布，如果为空，每个颜色就是均匀分布的。
//            // 最后是模式，这里设置的是Clamp渐变
//                paintLine.setShader(mShader);
//                canvas.drawRect(frame.left + CORNER_WIDTH, slideTop, frame.right
//                        - CORNER_WIDTH, slideTop + MIDDLE_LINE_WIDTH, paintLine);
                //用图片
                Rect lineRect = new Rect();
                lineRect.left = frame.left;
                lineRect.right = frame.right;
                lineRect.top = slideTop;
                lineRect.bottom = slideTop + MIDDLE_LINE_PADDING;
                canvas.drawBitmap(((BitmapDrawable)(getResources().getDrawable(R.drawable.qrcode_scan_line))).getBitmap(), null, lineRect, paint);

            }
            //画扫描框下面的字
            // 第一个提示语
            paint.setColor(getResources().getColor(R.color.first_scan_desc));
            paint.setTextSize(TEXT_SIZE * density);
            paint.setAlpha(221);
            // paint.setTypeface(Typeface.create("System", Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);//文字居中,X,Y 对应文字坐标中心
            canvas.drawText(
                    showFirstText,
                    width/2, frame.top - 180,
                    paint);
            int textWidth = width - 114;
            int offsetY = frame.top - 140;
            canvas.translate(57, offsetY);
            TextPaint textPaint = new TextPaint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(TEXT_SIZE * density);
            textPaint.setAlpha(221);
            // paint.setTypeface(Typeface.create("System", Typeface.BOLD));
            // textPaint.setTextAlign(Paint.Align.CENTER);//文字居中,X,Y 对应文字坐标中心
            StaticLayout layout = new StaticLayout(ShowText,textPaint,textWidth, Layout.Alignment.ALIGN_CENTER,1.5F,0,false);
            layout.draw(canvas);
            canvas.restore();
//            if (!LangUtils.isChinese(ShowText)) {
//                // 英文提示语
//                String ShowTextTemp1 = ShowText.substring(0, 33);
//                String ShowTextTemp2 = ShowText.substring(33, ShowText.length());
//                canvas.drawText(
//                        ShowTextTemp1,
//                        width/2, frame.top - 100,
//                        paint);
//                canvas.drawText(
//                        ShowTextTemp2,
//                        width/2, frame.top - 40,
//                        paint);
//            } else {
//                canvas.drawText(
//                        ShowText,
//                        width/2, frame.top - 80,
//                        paint);
//            }


            Collection<ResultPoint> currentPossible = possibleResultPoints;
            Collection<ResultPoint> currentLast = lastPossibleResultPoints;
            if (currentPossible.isEmpty())
            {
                lastPossibleResultPoints = null;
            }
            else
            {
                possibleResultPoints = new HashSet<ResultPoint>(5);
                lastPossibleResultPoints = currentPossible;
                paint.setAlpha(OPAQUE);
                paint.setColor(resultPointColor);
                for (ResultPoint point : currentPossible)
                {
                    canvas.drawCircle(frame.left + point.getX(), frame.top
                            + point.getY(), 6.0f, paint);//画扫描到的可能的点
                }
            }
            if (currentLast != null)
            {
                paint.setAlpha(OPAQUE / 2);
                paint.setColor(resultPointColor);
                for (ResultPoint point : currentLast)
                {
                    canvas.drawCircle(frame.left + point.getX(), frame.top
                            + point.getY(), 3.0f, paint);
                }
            }

            // Request another update at the animation interval, but only repaint the laser line,
            // not the entire viewfinder mask.
            postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top,
                    frame.right, frame.bottom);
        }
    }

    public int getCameraTop() {
        Rect frame = CameraManager.get().getFramingRect();
        return frame.top;
    }

    public void drawViewfinder()
    {
        resultBitmap = null;
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live scanning display.
     *
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode)
    {
        resultBitmap = barcode;
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point)
    {
        possibleResultPoints.add(point);
    }


    /**
     * 中间色颜色换算
     */
    public int reSetColor(int startInt) {

        int startA = (startInt >> 24) & 0xff;
        int startR = (startInt >> 16) & 0xff;
        int startG = (startInt >> 8) & 0xff;
        int startB = startInt & 0xff;

        int endA = startA/2;


        return  ((startA + (endA - startA)) << 24)
                | (startR << 16)
                | (startG  << 8)
                | (startB );


    }

}
