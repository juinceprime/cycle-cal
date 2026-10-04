package com.cyclecal.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_PERM = 41;

    private EditText etTitle;
    private EditText etInterval;
    private TextView tvDate;
    private TextView tvTime;
    private TextView tvMonths;
    private TextView tvPreview;
    private TextView tvCalendar;
    private TextView btnCreate;
    private RadioButton rbRule;
    private RadioGroup rgMode;
    private Switch swReminder;
    private Switch swAllDay;
    private LinearLayout warnBar;

    private Calendar baseDate;
    private int hour = 9;
    private int minute = 0;
    private int months = 6;

    private List<CalendarHelper.CalInfo> cals = new ArrayList<>();
    private CalendarHelper.CalInfo chosenCal;
    private List<Calendar> lastOccurrences = new ArrayList<>();
    private boolean busy = false;

    private final SimpleDateFormat dfFull =
            new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA);
    private final SimpleDateFormat dfShort =
            new SimpleDateFormat("M月d日 EEEE", Locale.CHINA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        baseDate = Calendar.getInstance();
        baseDate.set(Calendar.HOUR_OF_DAY, 0);
        baseDate.set(Calendar.MINUTE, 0);
        baseDate.set(Calendar.SECOND, 0);
        baseDate.set(Calendar.MILLISECOND, 0);

        bindViews();
        setupListeners();
        updateDateText();
        updateTimeText();
        tvMonths.setText(String.valueOf(months));
        playIntro();
        refresh();

        if (hasPerms()) {
            loadCalendars();
        } else {
            requestPermissions(new String[]{
                    "android.permission.READ_CALENDAR",
                    "android.permission.WRITE_CALENDAR"
            }, REQ_PERM);
        }
    }

    private void bindViews() {
        etTitle = findViewById(R.id.etTitle);
        etInterval = findViewById(R.id.etInterval);
        tvDate = findViewById(R.id.tvDate);
        tvTime = findViewById(R.id.tvTime);
        tvMonths = findViewById(R.id.tvMonths);
        tvPreview = findViewById(R.id.tvPreview);
        tvCalendar = findViewById(R.id.tvCalendar);
        btnCreate = findViewById(R.id.btnCreate);
        rbRule = findViewById(R.id.rbRule);
        rgMode = findViewById(R.id.rgMode);
        swReminder = findViewById(R.id.swReminder);
        swAllDay = findViewById(R.id.swAllDay);
        warnBar = findViewById(R.id.warnBar);
    }

    private void setupListeners() {
        findViewById(R.id.rowDate).setOnClickListener(v -> showDatePicker());
        findViewById(R.id.rowTime).setOnClickListener(v -> showTimePicker());
        findViewById(R.id.rowCalendar).setOnClickListener(v -> pickCalendar());

        findViewById(R.id.btnMinusInterval).setOnClickListener(v -> setInterval(curInterval() - 1));
        findViewById(R.id.btnPlusInterval).setOnClickListener(v -> setInterval(curInterval() + 1));
        findViewById(R.id.btnMinusMonths).setOnClickListener(v -> setMonths(months - 1));
        findViewById(R.id.btnPlusMonths).setOnClickListener(v -> setMonths(months + 1));

        etInterval.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });

        rgMode.setOnCheckedChangeListener((group, checkedId) -> refresh());
        swReminder.setOnCheckedChangeListener((b, v) -> refresh());
        swAllDay.setOnCheckedChangeListener((b, v) -> refresh());

        etInterval.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) setInterval(curInterval());
        });

        findViewById(R.id.btnGrant).setOnClickListener(v ->
                requestPermissions(new String[]{
                        "android.permission.READ_CALENDAR",
                        "android.permission.WRITE_CALENDAR"
                }, REQ_PERM));

        btnCreate.setOnClickListener(v -> onCreateClicked());
    }

    private void playIntro() {
        LinearLayout cards = findViewById(R.id.cards);
        int delay = 0;
        for (int i = 0; i < cards.getChildCount(); i++) {
            View v = cards.getChildAt(i);
            if (v.getVisibility() != View.VISIBLE) continue;
            v.setAlpha(0f);
            v.setTranslationY(40f);
            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(340)
                    .setStartDelay(delay)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
            delay += 55;
        }
    }

    // ---------- 日期时间选择 ----------

    private void showDatePicker() {
        new DatePickerDialog(this, (DatePicker dp, int y, int m, int d) -> {
            baseDate.set(Calendar.YEAR, y);
            baseDate.set(Calendar.MONTH, m);
            baseDate.set(Calendar.DAY_OF_MONTH, d);
            updateDateText();
            refresh();
        }, baseDate.get(Calendar.YEAR), baseDate.get(Calendar.MONTH),
                baseDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (TimePicker tp, int h, int m) -> {
            hour = h;
            minute = m;
            updateTimeText();
            refresh();
        }, hour, minute, DateFormat.is24HourFormat(this)).show();
    }

    private void updateDateText() {
        tvDate.setText(dfFull.format(baseDate.getTime()));
    }

    private void updateTimeText() {
        tvTime.setText(String.format(Locale.CHINA, "%02d:%02d", hour, minute));
    }

    // ---------- 数值输入 ----------

    private int curInterval() {
        String s = etInterval.getText().toString().trim();
        if (TextUtils.isEmpty(s)) return 30;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    private void setInterval(int v) {
        v = Math.max(1, Math.min(365, v));
        etInterval.setText(String.valueOf(v));
        etInterval.setSelection(etInterval.getText().length());
    }

    private void setMonths(int v) {
        months = Math.max(1, Math.min(36, v));
        tvMonths.setText(String.valueOf(months));
        refresh();
    }

    // ---------- 预览 ----------

    private List<Calendar> computeOccurrences(int intervalDays, int monthsCount) {
        List<Calendar> list = new ArrayList<>();
        Calendar start = (Calendar) baseDate.clone();
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, monthsCount);
        Calendar cur = (Calendar) start.clone();
        int guard = 0;
        while (!cur.after(end) && guard < 400) {
            list.add((Calendar) cur.clone());
            cur.add(Calendar.DAY_OF_MONTH, intervalDays);
            guard++;
        }
        return list;
    }

    private void refresh() {
        if (busy) return;
        lastOccurrences = computeOccurrences(curInterval(), months);
        int n = lastOccurrences.size();
        String first = n > 0 ? dfFull.format(lastOccurrences.get(0).getTime()) : "—";
        String last = n > 0 ? dfFull.format(lastOccurrences.get(n - 1).getTime()) : "—";
        String mode = rbRule.isChecked() ? "循环日程" : "独立日程";
        String allDay = swAllDay.isChecked() ? " · 全天" : "";
        tvPreview.setText("将创建 " + n + " 个" + mode + allDay
                + "\n从 " + first + " 到 " + last);
        btnCreate.setText(n > 0 ? "创建 " + n + " 个日程" : "创建日程");
    }

    // ---------- 日历账户 ----------

    private boolean hasPerms() {
        return checkSelfPermission("android.permission.READ_CALENDAR") == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission("android.permission.WRITE_CALENDAR") == PackageManager.PERMISSION_GRANTED;
    }

    private void loadCalendars() {
        new Thread(() -> {
            final List<CalendarHelper.CalInfo> list = CalendarHelper.queryCalendars(MainActivity.this);
            runOnUiThread(() -> {
                cals = list;
                if (!list.isEmpty()) {
                    chosenCal = list.get(0);
                    tvCalendar.setText(chosenCal.toString());
                } else {
                    chosenCal = null;
                    tvCalendar.setText("未找到可用日历");
                }
            });
        }).start();
    }

    private void pickCalendar() {
        if (!hasPerms()) {
            requestPermissions(new String[]{
                    "android.permission.READ_CALENDAR",
                    "android.permission.WRITE_CALENDAR"
            }, REQ_PERM);
            return;
        }
        if (cals.isEmpty()) {
            toast("未找到可用日历，请先在系统日历中登录或添加账户");
            return;
        }
        String[] items = new String[cals.size()];
        for (int i = 0; i < cals.size(); i++) items[i] = cals.get(i).toString();
        new AlertDialog.Builder(this)
                .setTitle("写入哪个日历")
                .setItems(items, (d, w) -> {
                    chosenCal = cals.get(w);
                    tvCalendar.setText(chosenCal.toString());
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERM) return;
        boolean ok = grantResults.length > 0;
        for (int r : grantResults) ok = ok && r == PackageManager.PERMISSION_GRANTED;
        if (ok) {
            warnBar.setVisibility(View.GONE);
            loadCalendars();
        } else {
            warnBar.setVisibility(View.VISIBLE);
            tvCalendar.setText("无权限");
        }
    }

    // ---------- 创建 ----------

    private void onCreateClicked() {
        if (busy) return;
        if (!hasPerms()) {
            warnBar.setVisibility(View.VISIBLE);
            requestPermissions(new String[]{
                    "android.permission.READ_CALENDAR",
                    "android.permission.WRITE_CALENDAR"
            }, REQ_PERM);
            return;
        }
        String title = etTitle.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            etTitle.setError("请填写日程名称");
            etTitle.requestFocus();
            return;
        }
        int interval = curInterval();
        if (interval < 1 || interval > 365) {
            etInterval.setError("请输入 1–365");
            etInterval.requestFocus();
            return;
        }
        if (chosenCal == null) {
            pickCalendar();
            toast("请先选择要写入的日历");
            return;
        }
        final List<Calendar> occ = new ArrayList<>(lastOccurrences);
        if (occ.isEmpty()) {
            toast("没有可创建的日程");
            return;
        }

        final boolean recurring = rbRule.isChecked();
        final boolean allDay = swAllDay.isChecked();
        final boolean remind = swReminder.isChecked();
        final String fTitle = title;
        final CalendarHelper.CalInfo cal = chosenCal;
        final int h = hour, m = minute;

        setBusy(true);
        new Thread(() -> {
            try {
                final List<Uri> created = CalendarHelper.insertSeries(
                        MainActivity.this, cal, fTitle, occ,
                        recurring, allDay, h, m, remind);
                runOnUiThread(() -> showResult(created, occ));
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> {
                    setBusy(false);
                    toast("创建失败：" + msg);
                });
            }
        }).start();
    }

    private void setBusy(boolean b) {
        busy = b;
        btnCreate.setEnabled(!b);
        btnCreate.setAlpha(b ? 0.55f : 1f);
        if (b) {
            btnCreate.setText("创建中…");
        } else {
            refresh();
        }
    }

    private void showResult(List<Uri> created, List<Calendar> occ) {
        setBusy(false);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < occ.size() && i < 15; i++) {
            lines.add("· " + dfShort.format(occ.get(i).getTime()));
        }
        if (occ.size() > 15) lines.add("… 等共 " + occ.size() + " 条");
        new AlertDialog.Builder(this)
                .setTitle("已创建 " + created.size() + " 条日程")
                .setItems(lines.toArray(new String[0]), null)
                .setPositiveButton("完成", null)
                .setNeutralButton("撤销", (d, w) -> {
                    new Thread(() -> {
                        int n = CalendarHelper.deleteAll(MainActivity.this, created);
                        runOnUiThread(() -> toast(n > 0 ? "已撤销 " + n + " 条日程" : "撤销失败"));
                    }).start();
                })
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
