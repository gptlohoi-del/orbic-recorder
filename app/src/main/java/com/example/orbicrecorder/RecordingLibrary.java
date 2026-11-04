package com.example.orbicrecorder;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only archive browser for finished Orbic WAV exports (never staging WAVs). */
final class RecordingLibrary {
    private static final int MAX_VISIBLE = 500;
    private static final Pattern DATE_IN_NAME = Pattern.compile("(?:orbic_)?(\\d{8}_\\d{6}_\\d{3})");

    static final class Item {
        final Uri uri;
        final String name;
        final long modified;
        final long size;
        final boolean raw;

        Item(Uri uri, String name, long modified, long size) {
            this.uri = uri;
            this.name = name;
            this.modified = timestampFromName(name, modified);
            this.size = size;
            this.raw = name.endsWith("_raw.wav");
        }

        JSONObject toJson() throws Exception {
            JSONObject json = new JSONObject();
            json.put("id", uri.toString());
            json.put("name", name);
            json.put("modified", modified);
            json.put("size", size);
            json.put("raw", raw);
            return json;
        }
    }

    private RecordingLibrary() { }

    private static boolean isRecordedWav(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".wav") &&
                (lower.startsWith("orbic_") || lower.startsWith("orbic_voice_"));
    }

    private static long timestampFromName(String name, long fallback) {
        Matcher matcher = DATE_IN_NAME.matcher(name);
        if (matcher.find()) {
            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US);
                format.setLenient(false);
                Date date = format.parse(matcher.group(1));
                if (date != null) return date.getTime();
            } catch (Exception ignored) { }
        }
        return fallback;
    }

    private static void add(List<Item> result, Set<String> seen, Uri uri, String name,
                            long modified, long size) {
        if (!isRecordedWav(name) || uri == null || !seen.add(uri.toString())) return;
        result.add(new Item(uri, name, modified, size));
    }

    private static void listSelectedFolder(Context context, List<Item> result, Set<String> seen) {
        SharedPreferences settings = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        String treeText = settings.getString("treeUri", "");
        if (treeText.isEmpty()) return;
        try {
            Uri tree = Uri.parse(treeText);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                    DocumentsContract.getTreeDocumentId(tree));
            String[] fields = { DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE };
            try (Cursor cursor = context.getContentResolver().query(children, fields, null, null, null)) {
                if (cursor == null) return;
                while (cursor.moveToNext()) {
                    String id = cursor.getString(0);
                    String name = cursor.getString(1);
                    if (id == null) continue;
                    Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    add(result, seen, uri, name, cursor.isNull(2) ? 0 : cursor.getLong(2),
                            cursor.isNull(3) ? -1 : cursor.getLong(3));
                }
            }
        } catch (Exception ignored) {
            // A removable/renamed SAF folder may be offline. Default exports remain browsable.
        }
    }

    private static void listDefault(Context context, List<Item> result, Set<String> seen) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String[] projection = {MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.DATE_MODIFIED,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.RELATIVE_PATH};
                String selection = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?";
                String[] args = {Environment.DIRECTORY_MUSIC + "/OrbicRecorder/%"};
                try (Cursor cursor = context.getContentResolver().query(collection,
                        projection, selection, args, null)) {
                    if (cursor != null) while (cursor.moveToNext()) {
                        String path = cursor.getString(4);
                        if (path == null) continue;
                        String normalized = path.replaceAll("/+$", "");
                        String root = Environment.DIRECTORY_MUSIC + "/OrbicRecorder/";
                        if (!(normalized.equals(root + "DaLoc") ||
                                normalized.equals(root + "Goc"))) continue;
                        Uri uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(0));
                        add(result, seen, uri, cursor.getString(1), cursor.getLong(2) * 1000L,
                                cursor.getLong(3));
                    }
                }
            } catch (Exception ignored) { }
        } else {
            File base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if (base == null) base = context.getFilesDir();
            for (String sub : new String[]{"DaLoc", "Goc"}) {
                File folder = new File(base, "OrbicRecorder/" + sub);
                File[] files = folder.listFiles();
                if (files == null) continue;
                for (File file : files) if (file.isFile())
                    add(result, seen, Uri.fromFile(file), file.getName(),
                            file.lastModified(), file.length());
            }
        }
    }

    static List<Item> list(Context context) {
        List<Item> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        listSelectedFolder(context, result, seen);
        listDefault(context, result, seen);
        result.sort(Comparator.comparingLong((Item item) -> item.modified).reversed()
                .thenComparing(item -> item.raw)
                .thenComparing(item -> item.name, Comparator.reverseOrder()));
        if (result.size() > MAX_VISIBLE) return new ArrayList<>(result.subList(0, MAX_VISIBLE));
        return result;
    }

    static String asJson(Context context) {
        JSONArray array = new JSONArray();
        for (Item item : list(context)) {
            try { array.put(item.toJson()); } catch (Exception ignored) { }
        }
        return array.toString();
    }

    static Item find(Context context, String id) {
        if (id == null || id.length() > 2048) return null;
        for (Item item : list(context)) if (item.uri.toString().equals(id)) return item;
        return null;
    }

    static Item newest(Context context) {
        List<Item> recordings = list(context);
        return recordings.isEmpty() ? null : recordings.get(0);
    }
}
