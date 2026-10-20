package com.paperlocal.library;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Local-only library. Two sources, deliberately no cloud sync:
 * 1. imported PDFs copied into app-private files/library/ (always readable,
 *    works on Android 13+ scoped storage);
 * 2. best-effort scan of public Download/Documents (works where file perms
 *    allow, e.g. Android <=12).
 */
public final class LibraryStore {

    public static File libraryDir(Context c) {
        File d = new File(c.getFilesDir(), "library");
        if (!d.isDirectory()) {
            d.mkdirs();
        }
        return d;
    }

    /** Copy a SAF-picked PDF into the library; returns the file or null. */
    public static File importUri(Context c, Uri uri) {
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(uri);
            if (in == null) {
                return null;
            }
            String name = displayName(c, uri);
            if (name == null || !name.toLowerCase().endsWith(".pdf")) {
                name = "document.pdf";
            }
            File out = unique(new File(libraryDir(c), name));
            FileOutputStream os = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            os.close();
            in.close();
            return out;
        } catch (Exception e) {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignore) {
            }
            return null;
        }
    }

    /** Library files (newest first) + scanned public PDFs. */
    public static String[] list(Context c) {
        List<File> files = new ArrayList<>();
        File[] mine = libraryDir(c).listFiles();
        if (mine != null) {
            files.addAll(Arrays.asList(mine));
        }
        for (String p : scanPublic()) {
            files.add(new File(p));
        }
        File[] arr = files.toArray(new File[0]);
        Arrays.sort(arr, new Comparator<File>() {
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        String[] out = new String[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i].getAbsolutePath();
        }
        return out;
    }

    public static String[] scanPublic(Context c) {
        return scanPublic();
    }

    private static String[] scanPublic() {
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

    private static File unique(File f) {
        if (!f.exists()) {
            return f;
        }
        String base = f.getName().replaceFirst("\\.pdf$", "");
        for (int i = 2; i < 1000; i++) {
            File g = new File(f.getParentFile(), base + "_" + i + ".pdf");
            if (!g.exists()) {
                return g;
            }
        }
        return new File(f.getParentFile(), base + "_" + System.currentTimeMillis() + ".pdf");
    }

    private static String displayName(Context c, Uri uri) {
        try {
            android.database.Cursor cur = c.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null) {
                try {
                    if (cur.moveToFirst()) {
                        return cur.getString(0);
                    }
                } finally {
                    cur.close();
                }
            }
        } catch (Exception e) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? null : last;
    }

    private LibraryStore() {
    }
}
