package com.paperlocal;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.paperlocal.ai.AiCfgStore;
import com.paperlocal.ai.AiClient;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Multi-profile endpoint editor: list of saved endpoints (tap to edit, long
 * press to delete), a form (name/baseUrl/apiKey/model), "获取模型列表"
 * (GET /models, fills the model field, doubles as a connection test) and
 * "保存并启用".
 */
public class SettingsActivity extends Activity {

    private ListView list;
    private EditText name, baseUrl, apiKey, model;
    private JSONArray profiles = new JSONArray();
    private int editing = -1;   // index in profiles, -1 = new

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView h1 = new TextView(this);
        h1.setText("已保存的接口（点击编辑，长按删除）");
        h1.setTextSize(14);
        root.addView(h1);

        list = new ListView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(140));
        root.addView(list, lp);

        ScrollView sc = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        sc.addView(form);

        name = field("名称，如 我的服务器");
        baseUrl = field("baseUrl，如 http://192.168.1.10:8000/v1");
        apiKey = field("apiKey（本地服务可留空）");
        model = field("model，如 qwen3.8-27b（可用下方按钮获取）");
        form.addView(name);
        form.addView(baseUrl);
        form.addView(apiKey);
        form.addView(model);

        Button fetch = new Button(this);
        fetch.setText("获取模型列表（同时测试连接）");
        fetch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { fetchModels(); }
        });
        form.addView(fetch);

        final Spinner spinner = new Spinner(this);
        form.addView(spinner);

        Button save = new Button(this);
        save.setText("保存并启用");
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { save(); }
        });
        form.addView(save);

        Button cancel = new Button(this);
        cancel.setText("新建（清空表单）");
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { editing = -1; name.setText("我的服务器"); }
        });
        form.addView(cancel);

        root.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        reload();
    }

    private void reload() {
        profiles = AiCfgStore.profiles(this);
        String active = AiCfgStore.activeName(this);
        String[] labels = new String[profiles.length()];
        try {
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject o = profiles.getJSONObject(i);
                String mark = o.optString("name", "").equals(active) ? " ✔" : "";
                labels[i] = o.optString("name", "?") + " · "
                        + o.optString("model", "?") + mark;
            }
        } catch (Exception e) {
        }
        list.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, labels));
    }

    private void fetchModels() {
        final String url = AiClient.normalizeBase(baseUrl.getText().toString())
                + "/models";
        final String key = apiKey.getText().toString().trim();
        toast("获取中…");
        AiClient.getAsync(url, key, new AiClient.Callback() {
            public void onResult(final boolean ok, final int status, final String body) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!ok) {
                            toast("连接失败 HTTP " + status + "：" +
                                    body.substring(0, Math.min(100, body.length())));
                            return;
                        }
                        try {
                            JSONArray data = new JSONObject(body).optJSONArray("data");
                            if (data == null) {
                                toast("返回里没有模型列表");
                                return;
                            }
                            String[] ids = new String[data.length()];
                            for (int i = 0; i < data.length(); i++) {
                                ids[i] = data.getJSONObject(i).optString("id", "?");
                            }
                            if (ids.length == 0) {
                                toast("连接成功，但没有可用模型");
                                return;
                            }
                            model.setText(ids[0]);
                            toast("连接成功，共 " + ids.length +
                                    " 个模型，已填 " + ids[0] + "（可手改）");
                        } catch (Exception e) {
                            toast("连接成功但列表解析失败：" +
                                    body.substring(0, Math.min(100, body.length())));
                        }
                    }
                });
            }
        });
    }

    private void save() {
        try {
            JSONObject o = new JSONObject();
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                n = "接口" + (profiles.length() + 1);
            }
            o.put("name", n);
            o.put("baseUrl", baseUrl.getText().toString().trim());
            o.put("apiKey", apiKey.getText().toString().trim());
            o.put("model", model.getText().toString().trim());
            JSONArray next = new JSONArray();
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject p = profiles.getJSONObject(i);
                if (i != editing) {
                    // avoid duplicate names
                    if (p.optString("name", "").equals(n)) {
                        continue;
                    }
                    next.put(p);
                }
            }
            next.put(o);
            AiCfgStore.saveProfiles(this, next.toString(), n);
            toast("已保存并启用 " + n);
            finish();
        } catch (Exception e) {
            toast("保存失败 " + e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int i, long id) {
                try {
                    JSONObject o = profiles.getJSONObject(i);
                    editing = i;
                    name.setText(o.optString("name", ""));
                    baseUrl.setText(o.optString("baseUrl", ""));
                    apiKey.setText(o.optString("apiKey", ""));
                    model.setText(o.optString("model", ""));
                } catch (Exception e) {
                }
            }
        });
        list.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int i, long id) {
                try {
                    JSONArray next = new JSONArray();
                    for (int j = 0; j < profiles.length(); j++) {
                        if (j != i) {
                            next.put(profiles.getJSONObject(j));
                        }
                    }
                    String active = AiCfgStore.activeName(SettingsActivity.this);
                    AiCfgStore.saveProfiles(SettingsActivity.this, next.toString(), active);
                    reload();
                    toast("已删除");
                } catch (Exception e) {
                }
                return true;
            }
        });
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show();
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        return e;
    }
}
