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

package com.icechen.qr_simple.camera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.zxing.PlanarYUVLuminanceSource;
import com.icechen.qr_simple.CaptureActivity;
import com.icechen.qr_simple.camera.open.OpenCamera;
import com.icechen.qr_simple.camera.open.OpenCameraInterface;

import java.io.IOException;


/**
 * This object wraps the Camera service object and expects to be the only one talking to it. The
 * implementation encapsulates the steps needed to take preview-sized images, which are used for
 * both preview and decoding.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraManager {

  private static final String TAG = CameraManager.class.getSimpleName();

  //边框尺寸
  private static final int MIN_FRAME_WIDTH = 240;
  private static final int MIN_FRAME_HEIGHT = 240;
  private static final int MAX_FRAME_WIDTH = 1200; // = 5/8 * 1920
  private static final int MAX_FRAME_HEIGHT = 675; // = 5/8 * 1080

  private final Context context;
  private final CameraConfigurationManager configManager;
  private OpenCamera camera;
  private AutoFocusManager autoFocusManager;
  private Rect framingRect;
  private Rect framingRectInPreview;
  private boolean initialized;
  private boolean previewing;
  private int requestedCameraId = OpenCameraInterface.NO_REQUESTED_CAMERA;
  private int requestedFramingRectWidth;
  private int requestedFramingRectHeight;
  /**
   * Preview frames are delivered here, which we pass on to the registered handler. Make sure to
   * clear the handler so it will only receive one message.
   */
  private final PreviewCallback previewCallback;

  public CameraManager(Context context) {
    this.context = context;
    //new一个CameraConfigurationManager对象
    this.configManager = new CameraConfigurationManager(context);
    //new一个预览回调对象
    previewCallback = new PreviewCallback(configManager);
  }
  
  /**
   * 打开相机，并初始化参数
   *
   * @param holder 相机图像将绘制在这个SurfaceView上
   * @throws IOException Indicates the com.icechen.qr_simple.camera driver failed to open.
   */
  public synchronized void openDriver(SurfaceHolder holder) throws IOException {
    OpenCamera theCamera = camera;
    if (theCamera == null) {
      //如果OpenCamera对象为空，那么就打开一个OpenCamera
      theCamera = OpenCameraInterface.open(requestedCameraId);
      if (theCamera == null) {
        //如果打开失败，抛出异常
        throw new IOException("Camera.open() failed to return object from driver");
      }
      camera = theCamera;
    }

    if (!initialized) {
      initialized = true;
      //初始化相机
      configManager.initFromCameraParameters(theCamera);
      if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
        setManualFramingRect(requestedFramingRectWidth, requestedFramingRectHeight);
        requestedFramingRectWidth = 0;
        requestedFramingRectHeight = 0;
      }
    }

    //获得OpenCamera中的Camera
    Camera cameraObject = theCamera.getCamera();
    //获得Camera的参数
    Camera.Parameters parameters = cameraObject.getParameters();
    String parametersFlattened = parameters == null ? null : parameters.flatten(); // Save these, temporarily
    try {
      //设置Camera的参数
      configManager.setDesiredCameraParameters(theCamera, false);
    } catch (RuntimeException re) {
      // Driver failed
      Log.w(TAG, "Camera rejected parameters. Setting only minimal safe-mode parameters");
      Log.i(TAG, "Resetting to saved com.icechen.qr_simple.camera params: " + parametersFlattened);
      // Reset:
      //抛异常则再设置一次，什么逻辑？
      if (parametersFlattened != null) {
        parameters = cameraObject.getParameters();
        parameters.unflatten(parametersFlattened);
        try {
          cameraObject.setParameters(parameters);
          configManager.setDesiredCameraParameters(theCamera, true);
        } catch (RuntimeException re2) {
          // Well, darn. Give up
          Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
        }
      }
    }
    //设置相机显示在SurfaceView上
    cameraObject.setPreviewDisplay(holder);

  }

  /**
   * 相机是否打开
   * @return
   */
  public synchronized boolean isOpen() {
    return camera != null;
  }

  /**
   * 关闭相机
   */
  public synchronized void closeDriver() {
    if (camera != null) {
      //如果相机存在，释放相机
      camera.getCamera().release();
      camera = null;
      // Make sure to clear these each time we close the com.icechen.qr_simple.camera, so that any scanning rect
      // requested by intent is forgotten.
      framingRect = null;
      framingRectInPreview = null;
    }
  }

  /**
   * 开始显示画面
   */
  public synchronized void startPreview() {
    OpenCamera theCamera = camera;
    if (theCamera != null && !previewing) {
      //让相机开始显示画面
      theCamera.getCamera().startPreview();
      previewing = true;
      //让相机对焦
      autoFocusManager = new AutoFocusManager(context, theCamera.getCamera());
    }
  }

  /**
   * 让相机停止显示画面
   */
  public synchronized void stopPreview() {
    if (autoFocusManager != null) {
      //停止对焦
      autoFocusManager.stop();
      autoFocusManager = null;
    }
    if (camera != null && previewing) {
      //让相机停止显示画面
      camera.getCamera().stopPreview();
      previewCallback.setHandler(null, 0);
      previewing = false;
    }
  }

  /**
   * 设置闪光灯 {@link CaptureActivity}
   *
   * @param newSetting if {@code true}, light should be turned on if currently off. And vice versa.
   */
  public synchronized void setTorch(boolean newSetting) {
    OpenCamera theCamera = camera;
    if (theCamera != null) {
      if (newSetting != configManager.getTorchState(theCamera.getCamera())) {
        boolean wasAutoFocusManager = autoFocusManager != null;
        if (wasAutoFocusManager) {
          autoFocusManager.stop();
          autoFocusManager = null;
        }
        configManager.setTorch(theCamera.getCamera(), newSetting);
        if (wasAutoFocusManager) {
          autoFocusManager = new AutoFocusManager(context, theCamera.getCamera());
          autoFocusManager.start();
        }
      }
    }
  }

  /**
   * A single preview frame will be returned to the handler supplied. The data will arrive as byte[]
   * in the message.obj field, with width and height encoded as message.arg1 and message.arg2,
   * respectively.
   *
   * @param handler The handler to send the message to.
   * @param message The what field of the message to be sent.
   */
  public synchronized void requestPreviewFrame(Handler handler, int message) {
    OpenCamera theCamera = camera;
    if (theCamera != null && previewing) {
      /**
       * 设置回调的Handler为{@link DecodeHandler}对象
       */
      previewCallback.setHandler(handler, message);
      //设置预览回调
      theCamera.getCamera().setOneShotPreviewCallback(previewCallback);
    }
  }

  /**
   * Calculates the framing rect which the UI should draw to show the user where to place the
   * barcode. This target helps with alignment as well as forces the user to hold the device
   * far enough away to ensure the image will be in focus.
   *
   * @return The rectangle to draw on screen in window coordinates.
   */
  public synchronized Rect getFramingRect() {
    if (framingRect == null) {
      if (camera == null) {
        return null;
      }
      Point screenResolution = configManager.getScreenResolution();
      if (screenResolution == null) {
        // Called early, before init even finished
        return null;
      }

      //获得适合的尺寸
      int width = findDesiredDimensionInRange(screenResolution.x, MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
      int height = findDesiredDimensionInRange(screenResolution.y, MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);

      //统一长和宽
      width = Math.min(width,height);
      height = Math.min(width,height);
      //计算该矩形的第一个点坐标
      int leftOffset = (screenResolution.x - width) / 2;
      int topOffset = (screenResolution.y - height) / 2;
      //new出扫描框来
      framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
      Log.d(TAG, "Calculated framing rect: " + framingRect);
    }
    return framingRect;
  }

  /**
   * 找到适合的尺寸
   * 如果resolution<最小的尺寸，则取最小
   * 如果resolution>最大尺寸，则取最大尺寸
   * 否则取分辨率的尺寸
   * @param resolution
   * @param hardMin
   * @param hardMax
   * @return
   */
  private static int findDesiredDimensionInRange(int resolution, int hardMin, int hardMax) {
    //分辨率尺寸的5/8
    int dim = 5 * resolution / 8; // Target 5/8 of each dimension
    if (dim < hardMin) {
      //如果resolution<最小的尺寸，则取最小
      return hardMin;
    }
    if (dim > hardMax) {
      //如果resolution>最大尺寸，则取最大尺寸
      return hardMax;
    }
    //否则取分辨率的尺寸
    return dim;
  }

  /**
   * Like {@link #getFramingRect} but coordinates are in terms of the preview frame,
   * not UI / screen.
   *
   * @return {@link Rect} expressing barcode scan area in terms of the preview size
   */
  public synchronized Rect getFramingRectInPreview() {
    if (framingRectInPreview == null) {
      //获得扫描框大小
      Rect framingRect = getFramingRect();
      if (framingRect == null) {
        return null;
      }
      Rect rect = new Rect(framingRect);
      Point cameraResolution = configManager.getCameraResolution();
      Point screenResolution = configManager.getScreenResolution();
      if (cameraResolution == null || screenResolution == null) {
        // Called early, before init even finished
        return null;
      }
      //预览框尺寸 = 扫描框尺寸*相机分辨率/屏幕分辨率
      rect.left = rect.left * cameraResolution.x / screenResolution.x;
      rect.right = rect.right * cameraResolution.x / screenResolution.x;
      rect.top = rect.top * cameraResolution.y / screenResolution.y;
      rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;
      //设置预览框大小
      framingRectInPreview = rect;
    }
    return framingRectInPreview;
  }

  
  /**
   * Allows third party apps to specify the com.icechen.qr_simple.camera ID, rather than determine
   * it automatically based on available cameras and their orientation.
   *
   * @param cameraId com.icechen.qr_simple.camera ID of the com.icechen.qr_simple.camera to use. A negative value means "no preference".
   */
  public synchronized void setManualCameraId(int cameraId) {
    requestedCameraId = cameraId;
  }
  
  /**
   * Allows third party apps to specify the scanning rectangle dimensions, rather than determine
   * them automatically based on screen resolution.
   *
   * @param width The width in pixels to scan.
   * @param height The height in pixels to scan.
   */
  public synchronized void setManualFramingRect(int width, int height) {
    if (initialized) {
      Point screenResolution = configManager.getScreenResolution();
      if (width > screenResolution.x) {
        width = screenResolution.x;
      }
      if (height > screenResolution.y) {
        height = screenResolution.y;
      }
      int leftOffset = (screenResolution.x - width) / 2;
      int topOffset = (screenResolution.y - height) / 2;
      framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
      Log.d(TAG, "Calculated manual framing rect: " + framingRect);
      framingRectInPreview = null;
    } else {
      requestedFramingRectWidth = width;
      requestedFramingRectHeight = height;
    }
  }

  /**
   * 该方法用于创建一个明亮的资源
   *
   * @param data 画面数据.
   * @param width 图片的宽.
   * @param height 图片的高
   * @return 返回一个二位色差明亮的资源
   */
  public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
    Rect rect = getFramingRectInPreview();
    if (rect == null) {
      return null;
    }
    // Go ahead and assume it's YUV rather than die.
    //返回一个二位色差明亮的资源
    return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
            rect.width(), rect.height(), false);
  }

}
