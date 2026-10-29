package com.university.schedule.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.university.schedule.model.GroupOrTeacher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class FavoritesManager {
    private static final String PREF_FAVORITES_FILE = "unischedule_favorites";
    private static final String KEY_FAVORITES_JSON = "favorites_list";

    private FavoritesManager() {
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_FAVORITES_FILE, Context.MODE_PRIVATE);
    }

    public static synchronized List<GroupOrTeacher> getFavorites(Context context) {
        List<GroupOrTeacher> list = new ArrayList<>();
        String json = getPrefs(context).getString(KEY_FAVORITES_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String type = obj.optString("type", GroupOrTeacher.TYPE_GROUP);
                String name = obj.optString("name", "");
                if (!name.isEmpty()) {
                    list.add(new GroupOrTeacher(name, type));
                }
            }
        } catch (JSONException e) {
            // fallback to empty list
        }
        return list;
    }

    public static synchronized boolean isFavorite(Context context, String type, String name) {
        if (name == null || name.isEmpty()) return false;
        List<GroupOrTeacher> list = getFavorites(context);
        for (GroupOrTeacher it : list) {
            if (it.getName().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean toggleFavorite(Context context, String type, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        List<GroupOrTeacher> list = getFavorites(context);
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getName().equalsIgnoreCase(name.trim())) {
                index = i;
                break;
            }
        }

        boolean nowFavorite;
        if (index >= 0) {
            list.remove(index);
            nowFavorite = false;
        } else {
            list.add(new GroupOrTeacher(name.trim(), type));
            nowFavorite = true;
        }

        saveList(context, list);
        return nowFavorite;
    }

    public static synchronized void addFavorite(Context context, String type, String name) {
        if (!isFavorite(context, type, name)) {
            toggleFavorite(context, type, name);
        }
    }

    public static synchronized void removeFavorite(Context context, String type, String name) {
        if (isFavorite(context, type, name)) {
            toggleFavorite(context, type, name);
        }
    }

    private static void saveList(Context context, List<GroupOrTeacher> list) {
        JSONArray arr = new JSONArray();
        for (GroupOrTeacher it : list) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", it.getType());
                obj.put("name", it.getName());
                arr.put(obj);
            } catch (JSONException ignored) {
            }
        }
        getPrefs(context).edit().putString(KEY_FAVORITES_JSON, arr.toString()).apply();
    }
}
