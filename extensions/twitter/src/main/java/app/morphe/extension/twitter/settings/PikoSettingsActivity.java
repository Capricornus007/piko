/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import android.app.Activity;
import android.os.Bundle;

/**
 * 12.46.1-prod 起宿主移除了 com.twitter.android.AuthorizeAppActivity
 * （piko 設定的舊宿主 Activity，ActivityHook.startActivity 以顯式 intent
 * 指向它——類消失後直接 ActivityNotFoundException，設定打不開）。
 *
 * 改由擴展自帶本 Activity，並由 SettingsResourcePatch 在 AndroidManifest
 * 註冊（exported=false，僅供應用內顯式 intent 啟動）。onCreate 委託
 * ActivityHook.create 承接原本的 fragment 宿主邏輯；非 piko intent
 * 防禦性 finish()。
 */
public class PikoSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ActivityHook.create(this)) {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (SettingsSearchUIController.handleBackPressed(this)) {
                return;
            }
        } catch (Throwable ex) {
            // 搜尋控制器未安裝等情況，走系統返回。
        }
        super.onBackPressed();
    }
}
