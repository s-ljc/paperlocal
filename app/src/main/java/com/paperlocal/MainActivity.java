package com.paperlocal;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.paperlocal.library.LibraryStore;

import java.io.File;

/** Entry: local PDF list + settings + SAF import. No account, no cloud. */
public class MainActivity extends Activity {

    private static final int REQ_PDF = 42;
    private ListView list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button importBtn = new Button(this);
        importBtn.setText("＋ 导入 PDF");
        importBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                in.addCategory(Intent.CATEGORY_OPENABLE);
                in.setType("application/pdf");
                startActivityForResult(Intent.createChooser(in, "选择 PDF"), REQ_PDF);
            }
        });
        row.addView(importBtn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button settings = new Button(this);
        settings.setText("AI 接口设置");
        settings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        row.addView(settings, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);

        list = new ListView(this);
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int i, long id) {
                String path = (String) p.getItemAtPosition(i);
                File f = new File(path);
                if (f.getParentFile() != null
                        && f.getParentFile().equals(LibraryStore.libraryDir(MainActivity.this))) {
                    f.delete();
                    refresh();
                    Toast.makeText(MainActivity.this, "已从书架删除",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
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
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PDF && res == RESULT_OK && data != null
                && data.getData() != null) {
            Toast.makeText(this, "导入中…", Toast.LENGTH_SHORT).show();
            final Uri uri = data.getData();
            new Thread(new Runnable() {
                public void run() {
                    final File f = LibraryStore.importUri(MainActivity.this, uri);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (f != null) {
                                Intent in = new Intent(MainActivity.this, ReaderActivity.class);
                                in.putExtra(ReaderActivity.EXTRA_PATH, f.getAbsolutePath());
                                startActivity(in);
                            } else {
                                Toast.makeText(MainActivity.this, "导入失败",
                                        Toast.LENGTH_LONG).show();
                            }
                            refresh();
                        }
                    });
                }
            }).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        String[] pdfs = LibraryStore.list(this);
        list.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, pdfs));
        if (pdfs.length == 0) {
            list.setEmptyView(null);
            Toast.makeText(this, "书架是空的：点\"＋ 导入 PDF\"选一个文件",
                    Toast.LENGTH_LONG).show();
        }
    }
}
