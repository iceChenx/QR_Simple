/*
 * Copyright (C) 2010 ZXing authors
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

package com.icechen.qr_simple;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.ByteArrayOutputStream;
import java.util.Map;

public final class DecodeHandler extends Handler {

  private static final String TAG = DecodeHandler.class.getSimpleName();

  private final CaptureActivity activity;
  private final MultiFormatReader multiFormatReader;
  private boolean running = true;

  private static final int  decode = 1;
  private static final int  quit = 2;


  DecodeHandler(CaptureActivity activity, Map<DecodeHintType,Object> hints) {
    multiFormatReader = new MultiFormatReader();
    multiFormatReader.setHints(hints);
    this.activity = activity;
  }

  /**
   * 处理二维码的Handler
   * @param message
   */
  @Override
  public void handleMessage(Message message) {
    if (!running) {
      return;
    }
    if (message.what == R.id.decode) {/**
     * 解码，将会在{@link CaptureActivityHandler}中进行
     * 其中，byte[]来自于{@link PreviweCallback}
     */
      decode((byte[]) message.obj, message.arg1, message.arg2);

    } else if (message.what == R.id.quit) {
      running = false;
      Looper.myLooper().quit();

    }
  }

  /**
   * 处理扫描到的图像数据
   *
   * @param data   byte[]来自于{@link com.icechen.qr_simple.camera.PreviewCallback}
   * @param width  The width of the preview frame.
   * @param height The height of the preview frame.
   */
  private void decode(byte[] data, int width, int height) {
    long start = System.currentTimeMillis();
    //定义一个Result对象
    Result rawResult = null;

    //获得一个二位色差明亮的资源
    PlanarYUVLuminanceSource source = activity.getCameraManager().buildLuminanceSource(data, width, height);
    if (source != null) {
      //通过source可以得到一个Bitmap
      BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
      try {
        //用上面这个Bitmap获得一个Result对象，不用管怎么获得的
        rawResult = multiFormatReader.decodeWithState(bitmap);
      } catch (ReaderException re) {
        // continue
      } finally {
        multiFormatReader.reset();
      }
    }

    /**
     * 获得Activity的Handler，其实就是{@link CaptureActivityHandler}
     * 所以结果就在CaptureActivityHandler中处理
     */
    Handler handler = activity.getHandler();
    if (rawResult != null) {  //永远为空
      // Don't log the barcode contents for security.
      long end = System.currentTimeMillis();
      Log.d(TAG, "Found barcode in " + (end - start) + " ms");
      if (handler != null) {
        /**
         * 把结果发送到{@link CaptureActivityHandler}中处理
         */
        Message message = Message.obtain(handler, R.id.decode_succeeded, rawResult);
        //new一个Bundle
        Bundle bundle = new Bundle();
        //
        bundleThumbnail(source, bundle);
        message.setData(bundle);
        message.sendToTarget();
      }
    } else {
      if (handler != null) {
        //发送解码失败
        Message message = Message.obtain(handler, R.id.decode_failed);
        message.sendToTarget();
      }
    }
  }

  /**
   *
   * @param source
   * @param bundle
   */
  private static void bundleThumbnail(PlanarYUVLuminanceSource source, Bundle bundle) {
    //从PlanarYUVLuminanceSource对象中获得略缩图的像素点
    int[] pixels = source.renderThumbnail();
    //获得略缩图的尺寸
    int width = source.getThumbnailWidth();
    int height = source.getThumbnailHeight();

    //使用略缩图的像素创建Bitmap
    Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    //把Bitmap转换成byte[]
    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
    bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
    bundle.putFloat(DecodeThread.BARCODE_SCALED_FACTOR, (float) width / source.getWidth());
  }

}
