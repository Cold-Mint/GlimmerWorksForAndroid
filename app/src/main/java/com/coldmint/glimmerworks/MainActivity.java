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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
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

                for (int i = 0; i < assetsArray.length(); i++) {
                    JSONObject entry = assetsArray.getJSONObject(i);
                    String path = entry.getString("path");
                    boolean isFile = entry.getBoolean("isFile");
                    String sha512 = entry.getString("sha512");
                    if (isFile && path.equals("config.json")) {
                        File outFile = new File(assetsDir, path);
                        JSONObject mergedConfig;
                        JSONObject newConfig;
                        try (InputStream is = getAssets().open(path)) {
                            String jsonText;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                jsonText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            } else {
                                jsonText = readAll(is);
                            }
                            newConfig = new JSONObject(jsonText);
                        }

                        if (outFile.exists()) {
                            try {
                                String oldJsonText;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    oldJsonText = new String(
                                            Files.readAllBytes(outFile.toPath()),
                                            StandardCharsets.UTF_8
                                    );
                                } else {
                                    InputStream old = getAssets().open(path);
                                    oldJsonText = readAll(old);
                                    old.close();
                                }
                                JSONObject oldConfig = new JSONObject(oldJsonText);

                                int newVer = newConfig.optInt("configVersion", -1);
                                int oldVer = oldConfig.optInt("configVersion", -1);

                                if (newVer != oldVer) {
                                    mergedConfig = deepMerge(newConfig, oldConfig);
                                } else {
                                    mergedConfig = oldConfig;
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                                mergedConfig = newConfig;
                            }

                        } else {
                            mergedConfig = newConfig;
                        }

                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            fos.write(mergedConfig.toString(4).getBytes(StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        continue;
                    }
                    if (isFile && (path.endsWith(".png") || path.endsWith(".ttf"))) {
                        File outFile = new File(assetsDir, path);
                        if (!Objects.requireNonNull(outFile.getParentFile()).exists()) {
                            if (!outFile.getParentFile().mkdirs()) {
                                continue;
                            }
                        }

                        if (outFile.exists()) {
                            try {
                                String localSha512 = calculateSha512(outFile);
                                if (localSha512.equalsIgnoreCase(sha512)) {
                                    continue;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }


                        final String displayName = new File(path).getName();

                        runOnUiThread(() -> viewBinding.textView.setText(
                                getString(R.string.decompressed_resources, displayName)
                        ));

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

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        return bos.toString("UTF-8");
    }


    public static JSONObject deepMerge(JSONObject newObj, JSONObject oldObj) {
        JSONObject result = null;
        try {
            result = new JSONObject(newObj.toString());
            Iterator<String> keys = oldObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();

                if (key.equals("configVersion")) continue;

                Object oldVal = oldObj.get(key);

                if (!result.has(key)) {
                    result.put(key, oldVal);
                    continue;
                }

                Object newVal = result.get(key);

                if (newVal instanceof JSONObject && oldVal instanceof JSONObject) {
                    result.put(key, deepMerge((JSONObject) newVal, (JSONObject) oldVal));
                }
                else if (newVal instanceof JSONArray && oldVal instanceof JSONArray) {
                    result.put(key, oldVal);
                }
                else {
                    result.put(key, oldVal);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }


    private static String calculateSha512(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");

        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
            }
        }

        byte[] hashBytes = digest.digest();

        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


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