package com.paperlocal.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Multi-profile AI endpoint store: [{name, baseUrl, apiKey, model}, ...] +
 * active name, in SharedPreferences. Same shape as the mod's config panel so
 * users can keep several servers/models and switch instantly.
 */
public final class AiCfgStore {

    private static final String PREF = "paperlocal_ai_cfg";

    public static JSONArray profiles(Context c) {
        try {
            return new JSONArray(prefs(c).getString("profiles", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static String activeName(Context c) {
        return prefs(c).getString("active", "");
    }

    /** Active profile: {name, baseUrl, apiKey, model}; empty object if none. */
    public static JSONObject active(Context c) {
        JSONArray arr = profiles(c);
        String want = activeName(c);
        try {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optString("name", "").equals(want)) {
                    return o;
                }
            }
            if (arr.length() > 0) {
                return arr.getJSONObject(0);
            }
        } catch (Exception e) {
        }
        return new JSONObject();
    }

    public static void saveProfiles(Context c, String profilesJson, String active) {
        prefs(c).edit()
                .putString("profiles", profilesJson)
                .putString("active", active == null ? "" : active)
                .apply();
    }

    public static void activate(Context c, String name) {
        prefs(c).edit().putString("active", name == null ? "" : name).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private AiCfgStore() {
    }
}
