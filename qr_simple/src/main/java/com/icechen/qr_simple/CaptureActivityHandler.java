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

package com.icechen.qr_simple;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Browser;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.icechen.qr_simple.camera.CameraManager;

import java.util.Collection;
import java.util.Map;

/**
 * 这个类处理扫描结果
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CaptureActivityHandler extends Handler {

  private static final String TAG = CaptureActivityHandler.class.getSimpleName();

  private final CaptureActivity activity;
  private final DecodeThread decodeThread;
  private State state;
  private final CameraManager cameraManager;

  private static final int  restart_preview = 1;
  private static final int  decode_succeeded = 2;
  private static final int  decode_failed = 3;
  private static final int  return_scan_result = 4;
  private static final int  launch_product_query = 5;
  private enum State {
    PREVIEW,
    SUCCESS,
    DONE
  }

  CaptureActivityHandler(CaptureActivity activity,
                         Collection<BarcodeFormat> decodeFormats,
                         Map<DecodeHintType,?> baseHints,
                         String characterSet,
                         CameraManager cameraManager) {
    this.activity = activity;

    //new一个解码线程
    decodeThread = new DecodeThread(activity, decodeFormats, baseHints, characterSet,
            new ViewfinderResultPointCallback(activity.getViewfinderView()));
    decodeThread.start();

    state = State.SUCCESS;

    // Start ourselves capturing previews and decoding.
    this.cameraManager = cameraManager;
    cameraManager.startPreview();
    restartPreviewAndDecode();
  }

  @Override
  public void handleMessage(Message message) {
    if (message.what == R.id.restart_preview) {
      restartPreviewAndDecode();

    } else if (message.what == R.id.decode_succeeded) {
      state = State.SUCCESS;
      Bundle bundle = message.getData();
      //用于绘制二维码的Bitmap
      Bitmap barcode = null;
      float scaleFactor = 1.0f;
      if (bundle != null) {
        //获得接收到的byte[]数组
        /**
         * 这个byte[]来自{@link DecodeHandler}
         *
         * 这个byte[]数组最初是产生于{@link com.google.zxing.client.android.camera.PreviewCallback}
         * 然后在{@link PlanarYUVLuminanceSource}中产生了一个略缩图，再把这个略缩图转换为Bitmap，在转换为byte[]数组
         */
        byte[] compressedBitmap = bundle.getByteArray(DecodeThread.BARCODE_BITMAP);
        if (compressedBitmap != null) {
          //用byte[]创建Bitmap
          barcode = BitmapFactory.decodeByteArray(compressedBitmap, 0, compressedBitmap.length, null);
          // Mutable copy:
          barcode = barcode.copy(Bitmap.Config.ARGB_8888, true);
        }
        scaleFactor = bundle.getFloat(DecodeThread.BARCODE_SCALED_FACTOR);
      }
      //扫描到结果后调用，在Activity中处理
      //即把bitmap传回Activity中处理
      activity.handleDecode((Result) message.obj, barcode, scaleFactor);

    } else if (message.what == R.id.decode_failed) {// We're decoding as fast as possible, so when one decode fails, start another.
      state = State.PREVIEW;
      /**
       * 这里调用后，PreviewCallback就有handler对象了，这是一个{@link DecodeHandler}对象
       */
      cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);

    } else if (message.what == R.id.return_scan_result) {
      activity.setResult(Activity.RESULT_OK, (Intent) message.obj);
      activity.finish();

    } else if (message.what == R.id.launch_product_query) {
      String url = (String) message.obj;

      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
      intent.setData(Uri.parse(url));

      ResolveInfo resolveInfo =
              activity.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
      String browserPackageName = null;
      if (resolveInfo != null && resolveInfo.activityInfo != null) {
        browserPackageName = resolveInfo.activityInfo.packageName;
        Log.d(TAG, "Using browser in package " + browserPackageName);
      }

      // Needed for default Android browser / Chrome only apparently
      if ("com.android.browser".equals(browserPackageName) || "com.android.chrome".equals(browserPackageName)) {
        intent.setPackage(browserPackageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, browserPackageName);
      }

      try {
        activity.startActivity(intent);
      } catch (ActivityNotFoundException ignored) {
        Log.w(TAG, "Can't find anything to handle VIEW of URI " + url);
      }

    }
  }

  public void quitSynchronously() {
    state = State.DONE;
    cameraManager.stopPreview();
    Message quit = Message.obtain(decodeThread.getHandler(), R.id.quit);
    quit.sendToTarget();
    try {
      // Wait at most half a second; should be enough time, and onPause() will timeout quickly
      decodeThread.join(500L);
    } catch (InterruptedException e) {
      // continue
    }

    // Be absolutely sure we don't send any queued up messages
    removeMessages(R.id.decode_succeeded);
    removeMessages(R.id.decode_failed);
  }

  private void restartPreviewAndDecode() {
    if (state == State.SUCCESS) {
      state = State.PREVIEW;
      cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);
      activity.drawViewfinder();
    }
  }

}
