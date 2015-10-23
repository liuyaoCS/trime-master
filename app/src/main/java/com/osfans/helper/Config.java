/*
 * Copyright 2010 Google Inc.
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

package com.osfans.helper;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.TypedValue;
import android.util.Log;
import android.graphics.Typeface;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.io.*;

import com.osfans.keyboard.Key;
import com.osfans.trime.Rime;

public class Config {
  private Map<String, Object> mStyle, mDefaultStyle;
  private Map<String, Map<String, Object>> maps;
  private String defaultName = "trime.yaml";
  private static String USER_DATA_DIR = "/sdcard/rime";
  private static int BLK_SIZE = 1024;
  private static Config self = null;

  private Map<String,String> fallbackColors;
  private Map presetColorSchemes, presetKeyboards;

  public Config(Context context) {
    self = this;
    maps = new HashMap<String, Map<String, Object>>();
    mDefaultStyle = (Map<String,Object>)Rime.config_get_map("trime", "style");
    fallbackColors = (Map<String,String>)Rime.config_get_map("trime", "fallback_colors");
    Key.androidKeys = (List<String>)Rime.config_get_list("trime", "android_keys/name");
    Key.presetKeys = (Map<String, Map>)Rime.config_get_map("trime", "preset_keys");
    presetColorSchemes = Rime.config_get_map("trime", "preset_color_schemes");
    presetKeyboards = Rime.config_get_map("trime", "preset_keyboards");
    reset();
  }

  public static boolean prepareRime(Context context) {
    Log.e("Config", "prepare rime");
    if (new File(USER_DATA_DIR).exists()) return false;
    copyFileOrDir(context, "rime", false);
    Rime.get(true);
    return true;
  }

  public static String[] list(Context context, String path) {
    AssetManager assetManager = context.getAssets();
    String assets[] = null;
    try {
      assets = assetManager.list(path);
    } catch (IOException ex) {
      Log.e("Config", "I/O Exception", ex);
    }
    return assets;
  }

  public static boolean copyFileOrDir(Context context, String path, boolean overwrite) {
    AssetManager assetManager = context.getAssets();
    String assets[] = null;
    try {
      assets = assetManager.list(path);
      if (assets.length == 0) {
        copyFile(context, path, overwrite);
      } else {
        String fullPath = "/sdcard/" + path;
        File dir = new File(fullPath);
        if (!dir.exists()) dir.mkdir();
        for (int i = 0; i < assets.length; ++i) {
          copyFileOrDir(context, path + "/" + assets[i], overwrite);
        }
      }
    } catch (IOException ex) {
      Log.e("Config", "I/O Exception", ex);
      return false;
    }
    return true;
  }

  public static boolean copyFile(Context context, String filename, boolean overwrite) {
    AssetManager assetManager = context.getAssets();
    InputStream in = null;
    OutputStream out = null;
    try {
      in = assetManager.open(filename);
      String newFileName = "/sdcard/" + filename;
      if (new File(newFileName).exists() && !overwrite) return true;
      out = new FileOutputStream(newFileName);
      byte[] buffer = new byte[BLK_SIZE];
      int read;
      while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
      }
      in.close();
      in = null;
      out.flush();
      out.close();
      out = null;
    } catch (Exception e) {
      Log.e("Config", e.getMessage());
      return false;
    }
    return true;
  }

  public void reset() {
    String schema_id = Rime.getSchemaId();
    if (maps.containsKey(schema_id)) {
      mStyle = maps.get(schema_id);
      return;
    }
    mStyle = null;
    File f = new File(USER_DATA_DIR, schema_id + "." + defaultName);
    if (!f.exists()) return;
    mStyle = (Map<String,Object>)Rime.config_get_map(schema_id + ".trime", "style");
    maps.put(schema_id, mStyle); //緩存各方案自定義配置
  }

  private Object getValue(String k1) {
    if (mStyle != null && mStyle.containsKey(k1)) return mStyle.get(k1);
    if (mDefaultStyle != null && mDefaultStyle.containsKey(k1)) return mDefaultStyle.get(k1);
    return null;
  }

  public Map<String, Object> getKeyboard(String name) {
    return (Map<String, Object>)presetKeyboards.get(name);
  }

  public List<String> getKeyboardNames() {
    return (List<String>)getValue("keyboards");
  }

  public static Config get() {
    return self;
  }

  public static Config get(Context context) {
    if (self == null) {
      prepareRime(context);
      self = new Config(context);
    }
    return self;
  }

  public void destroy() {
    if (maps != null) maps.clear();
    if (mDefaultStyle != null) mDefaultStyle.clear();
    if (mStyle != null) mStyle.clear();
    self = null;
  }

  public boolean getBoolean(String key) {
    Object o = getValue(key);
    return o == null ? true : (Boolean)o;
  }

  public double getDouble(String key) {
    Object o = getValue(key);
    double size = 0;
    if (o instanceof Integer) size = ((Integer)o).doubleValue();
    else if (o instanceof Float) size = ((Float)o).doubleValue();
    else if (o instanceof Double) size = ((Double)o).doubleValue();
    return size;
  }

  public float getFloat(String key) {
    return (float)getDouble(key);
  }

  public int getInt(String key) {
    return (int)getDouble(key);
  }

  public int getPixel(String key) {
    return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, getFloat(key), Resources.getSystem().getDisplayMetrics());
  }

  public String getString(String key) {
    return (String)getValue(key);
  }

  public int getColor(String key) {
    String scheme = getString("color_scheme");
    Map map = (Map<String, Object>)presetColorSchemes.get(scheme);
    Object o = map.get(key);
    String fallbackKey = key;
    while (o == null && fallbackColors.containsKey(fallbackKey)) {
      fallbackKey = fallbackColors.get(fallbackKey);
      o = map.get(fallbackKey);
    }
    if (o == null) {
      map = (Map<String, Object>)presetColorSchemes.get("default");
      o = map.get(key);
    }
    if (o instanceof Integer) return ((Integer)o).intValue();
    return ((Long)o).intValue();
  }

  public void setColor(String color) {
    Rime.customize_string("trime", "style/color_scheme", color);
    Rime.deployConfigFile();
    mDefaultStyle.put("color_scheme", color);
  }

  public String[] getColorKeys() {
    if (presetColorSchemes == null) return null;
    String[] keys = new String[presetColorSchemes.size()];
    presetColorSchemes.keySet().toArray(keys);
    return keys;
  }

  public String[] getColorNames(String[] keys) {
    if (keys == null) return null;
    int n = keys.length;
    String[] names = new String[n];
    for (int i = 0; i < n; i++) {
      Map<String, Object> m = (Map<String, Object>)presetColorSchemes.get(keys[i]);
      names[i] = (String)m.get("name");
    }
    return names;
  }

  public Typeface getFont(String key){
    String name = getString(key);
    if (name != null) {
      File f = new File(USER_DATA_DIR + "/fonts", name);
      if(f.exists()) return Typeface.createFromFile(f);
    }
    return Typeface.DEFAULT;
  }
}
