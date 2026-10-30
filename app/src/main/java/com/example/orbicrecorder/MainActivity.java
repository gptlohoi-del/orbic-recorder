package com.example.orbicrecorder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    // This string is checked by GitHub Actions: a build can never silently use the old UI.
    private static final String VERSION_TITLE = "ORBIC RECORDER v4.0";
    private static final int REQUEST_MIC = 101;
    private static final int REQUEST_FOLDER = 102;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] recentLevels = new int[48];
    private int wavePosition = 0;
    private boolean dark;
    private int foreground, secondary, surface, background;
    private TextView status, details, lastFile, sourceText, thresholdText;
    private Button startButton, pauseButton, stopButton;
    private Waveform waveform;

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private SharedPreferences settings() {
        return getSharedPreferences("config", MODE_PRIVATE);
    }

    private SharedPreferences state() {
        return getSharedPreferences("state", MODE_PRIVATE);
    }

    @Override public void onCreate(Bundle bundle) {
        String theme = settings().getString("theme", "system");
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        dark = "dark".equals(theme) || ("system".equals(theme) && mode == Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(bundle);
        buildUi();
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView text(String value, float sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(foreground);
        if (bold) view.setTypeface(null, 1);
        return view;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void gap(LinearLayout l, int height) {
        l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private Button button(String title, int color) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setBackground(shape(color, 12));
        return b;
    }

    private String duration(long ms) {
        long seconds = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private void buildUi() {
        foreground = dark ? Color.rgb(237, 244, 249) : Color.rgb(23, 39, 50);
        secondary = dark ? Color.rgb(165, 186, 200) : Color.rgb(92, 110, 122);
        surface = dark ? Color.rgb(30, 45, 57) : Color.rgb(238, 245, 248);
        background = dark ? Color.rgb(13, 23, 31) : Color.WHITE;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(16), dp(16), dp(20));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(VERSION_TITLE, 20, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button settingsButton = button("CÀI ĐẶT", dark ? 0xFF485F6B : 0xFF607D8B);
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(100), dp(44)));
        settingsButton.setOnClickListener(v -> showSettings());
        root.addView(header);
        TextView sub = text("Bộ lọc khoảng lặng • WAV mono 16 kHz", 13, false);
        sub.setTextColor(secondary);
        root.addView(sub);
        gap(root, 16);

        LinearLayout signalBox = column();
        signalBox.setPadding(dp(13), dp(12), dp(13), dp(12));
        signalBox.setBackground(shape(surface, 14));
        LinearLayout captionRow = new LinearLayout(this);
        captionRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView waveTitle = text("SÓNG ÂM ĐẦU VÀO", 13, true);
        captionRow.addView(waveTitle, new LinearLayout.LayoutParams(0, -2, 1));
        thresholdText = text("— dBFS", 13, false);
        thresholdText.setTextColor(dark ? 0xFF5BE1AA : 0xFF008763);
        captionRow.addView(thresholdText);
        signalBox.addView(captionRow);
        gap(signalBox, 8);
        waveform = new Waveform();
        signalBox.addView(waveform, new LinearLayout.LayoutParams(-1, dp(75)));
        root.addView(signalBox);
        gap(root, 14);

        status = text("SẴN SÀNG", 17, true);
        root.addView(status);
        gap(root, 7);
        details = text("", 15, false);
        root.addView(details);
        gap(root, 8);
        sourceText = text("", 13, false);
        sourceText.setTextColor(secondary);
        root.addView(sourceText);
        gap(root, 12);

        LinearLayout controls = new LinearLayout(this);
        startButton = button("● BẮT ĐẦU GHI", 0xFF157C54);
        controls.addView(startButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        gapBetween(controls, 8);
        pauseButton = button("Ⅱ PAUSE", 0xFFB47821);
        controls.addView(pauseButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(controls);
        gap(root, 8);
        stopButton = button("■ DỪNG VÀ LƯU", 0xFFB23A43);
        root.addView(stopButton, new LinearLayout.LayoutParams(-1, dp(50)));
        gap(root, 11);

        Button playLast = button("▶ NGHE BẢN GHI GẦN NHẤT", dark ? 0xFF465F70 : 0xFF557B95);
        root.addView(playLast, new LinearLayout.LayoutParams(-1, dp(46)));
        playLast.setOnClickListener(v -> playLastRecording());
        gap(root, 12);

        lastFile = text("", 12, false);
        lastFile.setTextColor(secondary);
        lastFile.setTextIsSelectable(true);
        root.addView(lastFile);
        gap(root, 8);
        TextView note = text("Chỉ giữ các đoạn vượt ngưỡng âm lượng. Tiếng động lớn vẫn có thể được giữ; hãy thử ngưỡng lọc với nguồn thực tế.", 12, false);
        note.setTextColor(secondary);
        root.addView(note);

        startButton.setOnClickListener(v -> startRecording());
        pauseButton.setOnClickListener(v -> {
            boolean paused = state().getBoolean("paused", false);
            startService(new Intent(this, RecorderService.class).setAction(paused ? "RESUME" : "PAUSE"));
        });
        stopButton.setOnClickListener(v -> startService(new Intent(this, RecorderService.class).setAction("STOP")));
        setContentView(scroll);
        redrawState();
    }

    private void gapBetween(LinearLayout row, int pixels) {
        row.addView(new View(this), new LinearLayout.LayoutParams(dp(pixels), 1));
    }

    private void startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        try {
            startForegroundService(new Intent(this, RecorderService.class).setAction("START"));
            startButton.setEnabled(false);
        } catch (Exception error) {
            Toast.makeText(this, "Không thể khởi động: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQUEST_MIC) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startRecording();
            else Toast.makeText(this, "Cần quyền micro để ghi âm", Toast.LENGTH_LONG).show();
        }
    }

    private void redrawState() {
        if (status == null) return;
        SharedPreferences p = state();
        boolean running = p.getBoolean("running", false);
        boolean paused = p.getBoolean("paused", false);
        boolean saving = p.getBoolean("saving", false);
        int level = p.getInt("level", 0);
        status.setText(saving ? "ĐANG LƯU FILE..." : running ? (paused ? "Ⅱ ĐANG TẠM DỪNG" : p.getString("capture", "ĐANG GHI")) : "○ SẴN SÀNG / ĐÃ DỪNG");
        details.setText("Thời gian thu: " + duration(p.getLong("elapsedMs", 0L)) +
                "     Đã giữ: " + duration(p.getLong("keptMs", 0L)) +
                "\nSố đoạn tiếng nói: " + p.getInt("segments", 0));
        sourceText.setText("Đệm trước: " + settings().getInt("pre", 1000) + " ms   •   Đệm sau: " + settings().getInt("tail", 1000) + " ms" +
                "\nNguồn: " + p.getString("source", "Chưa xác định"));
        thresholdText.setText(p.getString("db", "—") + " dBFS");
        String error = p.getString("error", "");
        lastFile.setText("Bản ghi: " + p.getString("file", "Chưa có") +
                (error.isEmpty() ? "" : "\nLỗi: " + error));
        startButton.setEnabled(!running && !saving);
        pauseButton.setEnabled(running && !saving);
        pauseButton.setText(paused ? "▶ TIẾP TỤC" : "Ⅱ PAUSE");
        stopButton.setEnabled(running && !saving);
        recentLevels[wavePosition] = running && !paused ? level : 0;
        wavePosition = (wavePosition + 1) % recentLevels.length;
        waveform.invalidate();
    }

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            redrawState();
            handler.postDelayed(this, 120);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void playLastRecording() {
        String value = state().getString("uri", "");
        if (value.isEmpty()) {
            Toast.makeText(this, "Chưa có bản ghi", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(value), "audio/wav");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Nghe bản ghi"));
        } catch (Exception error) {
            Toast.makeText(this, "Không tìm thấy ứng dụng phát WAV", Toast.LENGTH_LONG).show();
        }
    }

    private EditText settingNumber(LinearLayout container, String label, int number) {
        TextView caption = text(label, 14, false);
        container.addView(caption);
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        edit.setText(String.valueOf(number));
        container.addView(edit);
        gap(container, 9);
        return edit;
    }

    private void showSettings() {
        SharedPreferences cfg = settings();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(18), dp(8), dp(18), dp(8));
        scroll.addView(content);

        EditText threshold = settingNumber(content, "Ngưỡng âm thanh (dBFS, -70 đến -10)", cfg.getInt("threshold", -38));
        EditText confirm = settingNumber(content, "Xác nhận có tiếng (ms, 60 đến 1000)", cfg.getInt("minSpeech", 180));
        EditText pre = settingNumber(content, "GIỮ TRƯỚC (ms, 0 đến 5000) — mặc định 1000", cfg.getInt("pre", 1000));
        EditText tail = settingNumber(content, "GIỮ SAU (ms, 100 đến 5000) — mặc định 1000", cfg.getInt("tail", 1000));
        EditText warn = settingNumber(content, "Nhắc kiểm tra tín hiệu sau (giây, 10 đến 120)", cfg.getInt("warnSeconds", 20));

        TextView themeTitle = text("Giao diện", 15, true);
        content.addView(themeTitle);
        Spinner themeSpinner = new Spinner(this);
        String[] themes = {"Theo hệ thống", "Sáng", "Tối"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, themes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themeSpinner.setAdapter(adapter);
        String selectedTheme = cfg.getString("theme", "system");
        themeSpinner.setSelection("light".equals(selectedTheme) ? 1 : "dark".equals(selectedTheme) ? 2 : 0);
        content.addView(themeSpinner);
        gap(content, 12);

        TextView folder = text("Thư mục lưu: " + cfg.getString("folderLabel", "Music/OrbicRecorder"), 13, false);
        folder.setTextColor(secondary);
        content.addView(folder);
        gap(content, 6);
        Button chooseFolder = button("CHỌN THƯ MỤC KHÁC", 0xFF547B8A);
        content.addView(chooseFolder, new LinearLayout.LayoutParams(-1, dp(44)));
        gap(content, 8);
        TextView hint = text("Các thay đổi xử lý tiếng nói áp dụng từ phiên ghi tiếp theo. Khi PAUSE, âm thanh được bỏ qua và bộ đệm được tạo lại khi tiếp tục.", 12, false);
        hint.setTextColor(secondary);
        content.addView(hint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("CÀI ĐẶT")
                .setView(scroll)
                .setPositiveButton("LƯU", null)
                .setNegativeButton("ĐÓNG", (d, w) -> {})
                .setNeutralButton("MẶC ĐỊNH", null)
                .create();
        chooseFolder.setOnClickListener(v -> {
            if (state().getBoolean("running", false)) {
                Toast.makeText(this, "Hãy dừng ghi trước khi đổi thư mục", Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { startActivityForResult(intent, REQUEST_FOLDER); }
            catch (Exception error) { Toast.makeText(this, "Không mở được trình chọn thư mục", Toast.LENGTH_LONG).show(); }
        });
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    int t = Integer.parseInt(threshold.getText().toString().trim());
                    int c = Integer.parseInt(confirm.getText().toString().trim());
                    int a = Integer.parseInt(pre.getText().toString().trim());
                    int z = Integer.parseInt(tail.getText().toString().trim());
                    int w = Integer.parseInt(warn.getText().toString().trim());
                    if (t < -70 || t > -10 || c < 60 || c > 1000 || a < 0 || a > 5000 ||
                            z < 100 || z > 5000 || w < 10 || w > 120) throw new IllegalArgumentException();
                    String mode = themeSpinner.getSelectedItemPosition() == 1 ? "light" :
                            themeSpinner.getSelectedItemPosition() == 2 ? "dark" : "system";
                    cfg.edit().putInt("threshold", t).putInt("minSpeech", c).putInt("pre", a)
                            .putInt("tail", z).putInt("warnSeconds", w).putString("theme", mode).apply();
                    dialog.dismiss();
                    recreate();
                    Toast.makeText(this, "Đã lưu cài đặt", Toast.LENGTH_SHORT).show();
                } catch (Exception error) {
                    Toast.makeText(this, "Giá trị nằm ngoài phạm vi cho phép", Toast.LENGTH_LONG).show();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                cfg.edit().putInt("threshold", -38).putInt("minSpeech", 180).putInt("pre", 1000)
                        .putInt("tail", 1000).putInt("warnSeconds", 20)
                        .putString("theme", "system").remove("treeUri").remove("folderLabel").apply();
                dialog.dismiss();
                recreate();
            });
        });
        dialog.show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
            String label = DocumentsContract.getTreeDocumentId(uri);
            settings().edit().putString("treeUri", uri.toString())
                    .putString("folderLabel", label).apply();
            Toast.makeText(this, "Đã chọn thư mục lưu", Toast.LENGTH_LONG).show();
            redrawState();
        } catch (Exception error) {
            Toast.makeText(this, "Không lưu được quyền thư mục: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private class Waveform extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Waveform() { super(MainActivity.this); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            int n = recentLevels.length;
            float step = width / n;
            paint.setStrokeWidth(0);
            for (int i = 0; i < n; i++) {
                int level = recentLevels[(wavePosition + i) % n];
                float h = Math.max(dp(3), (height - dp(6)) * Math.min(100, level) / 100f);
                paint.setColor(level > 88 ? 0xFFFF6B64 : level > 68 ? 0xFFFFC453 : 0xFF27BB89);
                canvas.drawRoundRect(i * step + dp(1), (height - h) / 2, (i + 1) * step - dp(1),
                        (height + h) / 2, dp(2), dp(2), paint);
            }
        }
    }
}
