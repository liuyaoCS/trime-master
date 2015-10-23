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

package com.osfans.dialog;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;

import com.osfans.helper.Config;
import com.osfans.trime.R;
import com.osfans.trime.R.string;

public class ResetDialog {
  String[] items;
  boolean[] checked;
  AlertDialog dialog;
  Context context;

  public void select() {
    if (items == null) return;
    boolean ret = true;
    int n = items.length;
    for (int i = 0; i < n; i++) {
      if (checked[i]) {
        ret = Config.copyFileOrDir(context, "rime/" + items[i], true);
      }
    }
    Toast.makeText(context, ret ? R.string.reset_success : R.string.reset_failure, Toast.LENGTH_SHORT).show();
  }

  public ResetDialog(Context context) {
    this.context = context;
    items = Config.list(context, "rime");
    if (items == null) return;
    checked = new boolean[items.length];
    dialog = new AlertDialog.Builder(context)
      .setTitle(R.string.pref_reset)
      .setCancelable(true)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          select();
        }
      })
      .setMultiChoiceItems(items, checked, new DialogInterface.OnMultiChoiceClickListener() {
        public void onClick(DialogInterface di, int id, boolean isChecked) {
          checked[id] = isChecked;
        }
      })
      .create();
  }

  public AlertDialog getDialog() {
    return dialog;
  }

  public void show() {
    if (dialog != null) dialog.show();
  }
}
