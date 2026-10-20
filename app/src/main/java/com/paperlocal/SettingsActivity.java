package com.paperlocal;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.paperlocal.ai.AiCfgStore;
import com.paperlocal.ai.AiClient;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Minimal endpoint editor (skeleton): one profile per save, name fixed to
 * "default" for now; multi-profile UI lands with the real settings screen.
 * "测试连接" hits {baseUrl}/models through the same native path the reader uses.
 */
public class SettingsActivity extends Activity {

    private EditText baseUrl, apiKey, model;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        baseUrl = field("baseUrl，如 http://192.168.1.10:8000/v1");
        apiKey = field("apiKey（本地服务可留空）");
        model = field("model，如 qwen3.8-27b");
        root.addView(baseUrl);
        root.addView(apiKey);
        root.addView(model);

        Button test = new Button(this);
        test.setText("测试连接（发一条 1-token chat）");
        test.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String url = AiClient.normalizeBase(baseUrl.getText().toString())
                        + "/chat/completions";
                String body = "{\"model\":\"" + model.getText().toString().trim()
                        + "\",\"max_tokens\":1,\"messages\":"
                        + "[{\"role\":\"user\",\"content\":\"ping\"}]}";
                final String key = apiKey.getText().toString();
                AiClient.postAsync(url, body, key,
                        new AiClient.Callback() {
                            public void onResult(boolean ok, int status, String body) {
                                final String msg = ok
                                        ? "连接成功: " + body.substring(0, Math.min(80, body.length()))
                                        : "失败 HTTP " + status;
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        Toast.makeText(SettingsActivity.this, msg,
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        });
            }
        });
        root.addView(test);

        Button save = new Button(this);
        save.setText("保存并启用");
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("name", "default");
                    o.put("baseUrl", baseUrl.getText().toString().trim());
                    o.put("apiKey", apiKey.getText().toString().trim());
                    o.put("model", model.getText().toString().trim());
                    JSONArray arr = new JSONArray();
                    arr.put(o);
                    AiCfgStore.saveProfiles(SettingsActivity.this, arr.toString(), "default");
                    Toast.makeText(SettingsActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                    finish();
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "保存失败 " + e,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(save);

        setContentView(root);
        load();
    }

    private void load() {
        try {
            JSONObject o = AiCfgStore.active(this);
            baseUrl.setText(o.optString("baseUrl", ""));
            apiKey.setText(o.optString("apiKey", ""));
            model.setText(o.optString("model", ""));
        } catch (Exception e) {
        }
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        return e;
    }
}
