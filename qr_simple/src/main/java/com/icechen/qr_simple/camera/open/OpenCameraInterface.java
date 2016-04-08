/*
 * Copyright (C) 2012 ZXing authors
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

package com.icechen.qr_simple.camera.open;

import android.hardware.Camera;
import android.util.Log;

/**
 * 打开照相机的接口类，这里设计成了终态类。
 * Camera对象实际是在该类中创建的。
 */
public final class OpenCameraInterface {

  private static final String TAG = OpenCameraInterface.class.getName();

  private OpenCameraInterface() {
  }

  /** For {@link #open(int)}, 意味着没有请求的相机可以打开 */
  public static final int NO_REQUESTED_CAMERA = -1;

  /**
   * 打开请求的相机 {@link Camera#open(int)}, 如果存在的话.
   *
   * @param cameraId 需要打开的相机的id
   * @return handle to {@link OpenCamera} that was opened
   */
  public static OpenCamera open(int cameraId) {

    //获得相机数量
    int numCameras = Camera.getNumberOfCameras();

    if (numCameras == 0) {
      Log.w(TAG, "No cameras!");
      return null;
    }

    boolean explicitRequest = cameraId >= 0;

    //相机信息对象
    Camera.CameraInfo selectedCameraInfo = null;
    int index;


    if (explicitRequest) {
      //如果相机id大于等于0，表示存在相机。
      index = cameraId;  //把相机的id赋值给指针
      selectedCameraInfo = new Camera.CameraInfo();  //实例化相机信息对象
      //把对应指针的相机的信息装在相机信息对象中
      //但是这里为什么没有判断是否存在相机了？index>相机数量，不会抛异常吗？
      Camera.getCameraInfo(index, selectedCameraInfo);
    } else {
      //如果相机id小于0，表示参数错误，需要打开默认的相机（后置）
      index = 0; //设置指针为0，即这里保证请求错误时，打开默认相机（id为0的相机,后置）
      while (index < numCameras) {
        //下面句是获取对应指针的相机信息
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(index, cameraInfo);

        //获得相机朝向，0代表后置，1代表前置
        CameraFacing reportedFacing = CameraFacing.values()[cameraInfo.facing];

        if (reportedFacing == CameraFacing.BACK) {
          //如果是后置相机，把方法全局的相机信息对象赋值为这里的相机信息对象，这一步保证了找到相机是后置
          //然后跳出循环
          selectedCameraInfo = cameraInfo;
          break;
        }
        //为什么要增1？
        //因为要遍历所有的相机，以便找到后置相机
        index++;
      }
    }

    //定义了一个相机变量
    Camera camera;
    //这是个诡异的设计
    if (index < numCameras) {
      //确保index不是大于相机数量的
      Log.i(TAG, "Opening com.icechen.qr_simple.camera #" + index);
      //打开对应指针的相机
      camera = Camera.open(index);
    } else {
      //如果所填参数大于相机数量，则表示不存在该相机
      if (explicitRequest) {
        Log.w(TAG, "Requested com.icechen.qr_simple.camera does not exist: " + cameraId);
        camera = null;
      } else {
        //如果所填参数<0,默认打开后置相机
        Log.i(TAG, "No com.icechen.qr_simple.camera facing " + CameraFacing.BACK + "; returning com.icechen.qr_simple.camera #0");
        camera = Camera.open(0);
        //这里和上一段代码是诡异的
        selectedCameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(0, selectedCameraInfo);
      }
    }

    if (camera == null) {
      //如果最终没有打开相机，返回空
      return null;
    }
    //否则new一个OpenCamera对象
    return new OpenCamera(index,
                          camera,
                          CameraFacing.values()[selectedCameraInfo.facing],
                          selectedCameraInfo.orientation);
  }

}
