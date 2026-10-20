package com.paperlocal.library;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Local-only library: scan public Download + Documents dirs for PDFs.
 * TODO: SAF (ACTION_OPEN_DOCUMENT) picker for API 33+ and a persisted
 * favorites table; deliberately no cloud sync — this is a pure client.
 */
public final class LibraryStore {

    public static String[] scanLocal(Context c) {
        List<String> out = new ArrayList<>();
        File[] roots = {
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS),
        };
        for (File root : roots) {
            collect(root, out, 0);
        }
        return out.toArray(new String[0]);
    }

    private static void collect(File dir, List<String> out, int depth) {
        if (dir == null || depth > 3 || !dir.isDirectory()) {
            return;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File f : kids) {
            if (f.isDirectory()) {
                collect(f, out, depth + 1);
            } else if (f.getName().toLowerCase().endsWith(".pdf") && f.canRead()) {
                out.add(f.getAbsolutePath());
            }
        }
    }

    private LibraryStore() {
    }
}
