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

package com.icechen.qr_simple.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.icechen.qr_simple.PreferencesActivity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.RejectedExecutionException;

/**
 * 自动对焦管理器，实现了相机了自动对焦的回调方法
 */
final class AutoFocusManager implements Camera.AutoFocusCallback {

  private static final String TAG = AutoFocusManager.class.getSimpleName();

  //自动对焦时间为2000ms
  private static final long AUTO_FOCUS_INTERVAL_MS = 2000L;
  private static final Collection<String> FOCUS_MODES_CALLING_AF;


  static {
    //静态加载对焦方式字段
    FOCUS_MODES_CALLING_AF = new ArrayList<>(2);
    FOCUS_MODES_CALLING_AF.add(Camera.Parameters.FOCUS_MODE_AUTO);
    FOCUS_MODES_CALLING_AF.add(Camera.Parameters.FOCUS_MODE_MACRO);
  }

  private boolean stopped;
  private boolean focusing;
  private final boolean useAutoFocus;
  private final Camera camera;
  private AsyncTask<?,?,?> outstandingTask;

  //自动对焦管理器构造方法
  public AutoFocusManager(Context context, Camera camera) {
    this.camera = camera;
    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    String currentFocusMode = camera.getParameters().getFocusMode();
    //判断是否启用了自动对焦
    useAutoFocus =
        sharedPrefs.getBoolean(PreferencesActivity.KEY_AUTO_FOCUS, true) &&
        FOCUS_MODES_CALLING_AF.contains(currentFocusMode);
    Log.i(TAG, "Current focus mode '" + currentFocusMode + "'; use auto focus? " + useAutoFocus);
    start();
  }

  /**
   * 自动对焦的回调方法，这里进行了重写
   * 实际上是直接调用了{@link #autoFocusAgainLater()}
   * 这里会导致上两层锁？
   * @param success
   * @param theCamera
   */
  @Override
  public synchronized void onAutoFocus(boolean success, Camera theCamera) {
    focusing = false;
    autoFocusAgainLater();
  }

  /**
   * 当初次对焦出错后，需要重新对焦，就会调用该方法
   *
   * 这个方法被设计为有锁的，这个方法锁会导致该类的实例被上锁。
   */
  private synchronized void autoFocusAgainLater() {
    if (!stopped && outstandingTask == null) {
      AutoFocusTask newTask = new AutoFocusTask();
      try {
        newTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        outstandingTask = newTask;
      } catch (RejectedExecutionException ree) {
        Log.w(TAG, "Could not request auto focus", ree);
      }
    }
  }

 public synchronized void start() {

    if (useAutoFocus) {
      //如果启用了自动对焦
      outstandingTask = null;
      if (!stopped && !focusing) {
        //如果没有停止，并且没有对焦
        try {
          /**
           * 让相机去对焦，这里的回调方法被重写了
           * {@link #onAutoFocus(boolean, Camera)}}
           */
          camera.autoFocus(this);
          //对好焦了就让该boolean变量记录以对焦状态
          focusing = true;
        } catch (RuntimeException re) {
          // Have heard RuntimeException reported in Android 4.0.x+; continue?
          Log.w(TAG, "Unexpected exception while focusing", re);
          // 如果出现错误，从新对焦
          autoFocusAgainLater();
        }
      }
    }
  }

  /**
   * 该方法用于停止计时线程
   */
  private synchronized void cancelOutstandingTask() {
    if (outstandingTask != null) {
      //如果计时线程存在
      if (outstandingTask.getStatus() != AsyncTask.Status.FINISHED) {
        //如果计时线程的状态不是FINISHED
        //让线程取消
        outstandingTask.cancel(true);
      }
      //需要把线程变量指向空，释放内存
      outstandingTask = null;
    }
  }

  /**
   * 该方法用来停止相机对焦
   */
  public synchronized void stop() {
    //首先该方法一调用就需要把stopped状态变量该变
    stopped = true;

    if (useAutoFocus) {
      //如果当前处于获得焦点状态

        /**
         * 调用{@link #cancelOutstandingTask()}来停止计时线程
         */
      cancelOutstandingTask();
      // Doesn't hurt to call this even if not focusing
      try {
        //让camera取消自动对焦
        camera.cancelAutoFocus();
      } catch (RuntimeException re) {
        // Have heard RuntimeException reported in Android 4.0.x+; continue?
        Log.w(TAG, "Unexpected exception while cancelling focusing", re);
      }
    }
  }

  private final class AutoFocusTask extends AsyncTask<Object,Object,Object> {
    @Override
    protected Object doInBackground(Object... voids) {
      try {
        Thread.sleep(AUTO_FOCUS_INTERVAL_MS);
      } catch (InterruptedException e) {
        // continue
      }
      start();
      return null;
    }
  }

}
