package com.coldmint.glimmerworks;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

import java.util.Objects;

public class GameActivity extends SDLActivity {

    @Override
    protected void onResume() {
        super.onResume();
        //Set the full-screen mode and lock the landscape mode.
        //设置全屏模式，且锁定横屏。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            Objects.requireNonNull(getWindow().getInsetsController()).hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    @Override
    protected String[] getLibraries() {
        return new String[]{
                CppConfig.IsDebug() ? "fmtd" : "fmt",
                "SDL3",
                "SDL3_image",
                "SDL3_ttf",
                "box2d",
                "GlimmerWorks",
        };
    }
}