package com.paperlocal;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.paperlocal.library.LibraryStore;

/** Entry: local PDF list + settings. No account, no cloud. */
public class MainActivity extends Activity {

    private ListView list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        Button settings = new Button(this);
        settings.setText("AI 接口设置");
        settings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        root.addView(settings);

        list = new ListView(this);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                String path = (String) p.getItemAtPosition(i);
                Intent in = new Intent(MainActivity.this, ReaderActivity.class);
                in.putExtra(ReaderActivity.EXTRA_PATH, path);
                startActivity(in);
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String[] pdfs = LibraryStore.scanLocal(this);
        list.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, pdfs));
        if (pdfs.length == 0) {
            Toast.makeText(this, "未在 Download/文档目录找到 PDF（放入后重进）",
                    Toast.LENGTH_LONG).show();
        }
    }
}
