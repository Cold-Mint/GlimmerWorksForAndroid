package com.coldmint.glimmerworks;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.coldmint.glimmerworks.databinding.ActivityMainBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding viewBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityMainBinding.inflate(LayoutInflater.from(this));
        EdgeToEdge.enable(this);
        setContentView(viewBinding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        decompressAssets();
    }

    public void openGameActivity() {
        startActivity(new Intent(this, GameActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    public void decompressAssets() {
        new Thread(() -> {
            try {
                String indexJsonStr = loadAssetText();
                JSONObject indexJson = new JSONObject(indexJsonStr);
                JSONArray assetsArray = indexJson.getJSONArray("assets");
                File assetsDir = new File(getFilesDir(), "assets");
                if (!assetsDir.exists()) {
                    if (!assetsDir.mkdirs()) {
                        return;
                    }
                }

                // 3. 遍历文件列表
                for (int i = 0; i < assetsArray.length(); i++) {
                    JSONObject entry = assetsArray.getJSONObject(i);
                    String path = entry.getString("path");
                    boolean isFile = entry.getBoolean("isFile");

                    // 只处理 config.json、.png 和 .ttf
                    if (isFile && (path.endsWith(".png") || path.endsWith(".ttf") || path.equals("config.json"))) {
                        File outFile = new File(assetsDir, path);
                        if (!Objects.requireNonNull(outFile.getParentFile()).exists()) {
                            if (!outFile.getParentFile().mkdirs()) {
                                continue;
                            }
                        }

                        // 如果文件已存在，跳过
                        if (outFile.exists()) continue;

                        final String displayName = new File(path).getName();

                        // 在主线程更新 UI
                        runOnUiThread(() -> viewBinding.textView.setText(
                                getString(R.string.decompressed_resources, displayName)
                        ));

                        // 解压文件
                        try (InputStream is = getAssets().open(path);
                             FileOutputStream fos = new FileOutputStream(outFile)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                }

                // 解压完成
                runOnUiThread(() -> {
                            viewBinding.textView.setVisibility(View.GONE);
                            openGameActivity();
                        }

                );
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    viewBinding.textView.setText(
                            getString(R.string.decompression_failed)
                    );
                    viewBinding.progressBar.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    // 读取 assets 文件内容为字符串
    private String loadAssetText() throws Exception {
        try (InputStream is = getAssets().open("index.json")) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return baos.toString(StandardCharsets.UTF_8);
            } else {
                return baos.toString();
            }
        }
    }


}