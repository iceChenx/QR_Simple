/*
 * Copyright (C) 2015 ZXing authors
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

/**
 * 打开相机，它实际是持有了被打开的相机，真正打开相机的操作是在OpenCameraInterface中进行的
 */
public final class OpenCamera {
  
  private final int index;
  private final Camera camera;
  private final CameraFacing facing;
  private final int orientation;

  /**
   * 打开相机的构造方法
   * @param index
   * @param camera 相机
   * @param facing 相机位置枚举类，BACK表示后置，FRONT表示前置
   * @param orientation
   */
  public OpenCamera(int index, Camera camera, CameraFacing facing, int orientation) {
    this.index = index;
    this.camera = camera;
    this.facing = facing;
    this.orientation = orientation;
  }

  /**
   * 获得打开的相机
   * @return 相机位置枚举类，BACK表示后置，FRONT表示前置
   */
  public Camera getCamera() {
    return camera;
  }

  /**
   * 获得打开的是哪一个相机
   * @return
   */
  public CameraFacing getFacing() {
    return facing;
  }

  /**
   * 获得方向？
   * @return
   */
  public int getOrientation() {
    return orientation;
  }

  /**
   * 获得打开相机的信息
   * @return
   */
  @Override
  public String toString() {
    return "Camera #" + index + " : " + facing + ',' + orientation;
  }

}
