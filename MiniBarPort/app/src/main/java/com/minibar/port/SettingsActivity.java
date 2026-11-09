package com.minibar.port;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 极简设置页：不依赖 AndroidX，全部代码构建 UI，避免额外资源与依赖。
 */
public class SettingsActivity extends Activity {

    private SharedPreferences sp;
    private LinearLayout content;

    private Switch hideSwitch;
    private Switch widthEnabledSwitch;
    private Switch heightEnabledSwitch;
    private Switch triggerEnabledSwitch;
    private Switch maxTriggerEnabledSwitch;
    private SeekBar widthSeek;
    private SeekBar heightSeek;
    private SeekBar triggerSeek;
    private SeekBar maxTriggerSeek;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences(Prefs.FILE, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        addTitle("小窗提示线");
        addDesc("移植自 OShape：隐藏 heads-up 通知下方的小窗提示线，并可调整尺寸与触发阈值。修改后需重启「系统界面」(SystemUI) 生效。");

        hideSwitch = addSwitch("隐藏", Prefs.K_HIDE, true, new Runnable() {
            @Override
            public void run() {
                sp.edit().putBoolean(Prefs.K_HIDE, hideSwitch.isChecked()).apply();
                updateDependencies();
            }
        });

        addSection("尺寸（隐藏关闭时可用）");
        widthEnabledSwitch = addSwitch("自定义宽度", Prefs.K_WIDTH_ENABLED, false, new Runnable() {
            @Override
            public void run() {
                sp.edit().putBoolean(Prefs.K_WIDTH_ENABLED, widthEnabledSwitch.isChecked()).apply();
                updateDependencies();
            }
        });
        widthSeek = addSeek(Prefs.K_WIDTH, 0f, 200f, Prefs.DEFAULT_WIDTH, 2000);

        heightEnabledSwitch = addSwitch("自定义高度", Prefs.K_HEIGHT_ENABLED, false, new Runnable() {
            @Override
            public void run() {
                sp.edit().putBoolean(Prefs.K_HEIGHT_ENABLED, heightEnabledSwitch.isChecked()).apply();
                updateDependencies();
            }
        });
        heightSeek = addSeek(Prefs.K_HEIGHT, 0f, 10f, Prefs.DEFAULT_HEIGHT, 1000);

        addSection("触发阈值");
        triggerEnabledSwitch = addSwitch("自定义触发阈值", Prefs.K_TRIGGER_ENABLED, false, new Runnable() {
            @Override
            public void run() {
                sp.edit().putBoolean(Prefs.K_TRIGGER_ENABLED, triggerEnabledSwitch.isChecked()).apply();
                updateDependencies();
            }
        });
        triggerSeek = addSeek(Prefs.K_TRIGGER, 7.5f, 30f, Prefs.DEFAULT_TRIGGER, 450);

        maxTriggerEnabledSwitch = addSwitch("自定义最大触发阈值", Prefs.K_MAX_TRIGGER_ENABLED, false, new Runnable() {
            @Override
            public void run() {
                sp.edit().putBoolean(Prefs.K_MAX_TRIGGER_ENABLED, maxTriggerEnabledSwitch.isChecked()).apply();
                updateDependencies();
            }
        });
        maxTriggerSeek = addSeek(Prefs.K_MAX_TRIGGER, 30f, 60f, Prefs.DEFAULT_MAX_TRIGGER, 600);

        addSection("说明");
        addDesc("• 隐藏：将 miniBar 设为 GONE，并补偿动作区底部间距。\n"
                + "• 宽度/高度：改写 mini_window_bar 的 LayoutParams（隐藏开启时不可用）。\n"
                + "• 触发阈值：替换 SystemUI 的 mini_window_trigger_threshold / mini_window_max_trigger_threshold dimen。\n"
                + "• 白名单功能未移植。");

        updateDependencies();
    }

    private void updateDependencies() {
        boolean hide = hideSwitch.isChecked();
        setEnabled(widthEnabledSwitch, !hide);
        setEnabled(widthSeek, !hide && widthEnabledSwitch.isChecked());
        setEnabled(heightEnabledSwitch, !hide);
        setEnabled(heightSeek, !hide && heightEnabledSwitch.isChecked());
        setEnabled(triggerSeek, triggerEnabledSwitch.isChecked());
        setEnabled(maxTriggerSeek, maxTriggerEnabledSwitch.isChecked());
    }

    private void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.4f);
    }

    // ------------------------------------------------------------------ UI

    private void addTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(22f);
        tv.setTextColor(Color.BLACK);
        tv.setPadding(0, 0, 0, dp(8));
        content.addView(tv);
    }

    private void addSection(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTextColor(Color.DKGRAY);
        tv.setPadding(0, dp(20), 0, dp(6));
        content.addView(tv);
    }

    private void addDesc(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(Color.GRAY);
        tv.setLineSpacing(dp(2), 1f);
        tv.setPadding(0, 0, 0, dp(10));
        content.addView(tv);
    }

    private Switch addSwitch(String title, String key, boolean def, final Runnable afterChange) {
        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextSize(16f);
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (afterChange != null) {
                    afterChange.run();
                }
            }
        });
        content.addView(sw);
        return sw;
    }

    /**
     * 添加一条 SeekBar，右侧数值显示在该行上方的 TextView。
     */
    private SeekBar addSeek(final String key, final float min, final float max,
                            float def, int steps) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(8));

        final TextView valueView = new TextView(this);
        valueView.setTextSize(14f);
        valueView.setTextColor(Color.BLACK);
        valueView.setText(formatValue(key, def));
        row.addView(valueView);

        SeekBar seek = new SeekBar(this);
        seek.setMax(steps);
        seek.setProgress(Math.round((def - min) / (max - min) * steps));
        row.addView(seek);
        content.addView(row);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = min + (max - min) * progress / seekBar.getMax();
                value = Math.round(value * 100f) / 100f;
                valueView.setText(formatValue(key, value));
                if (fromUser) {
                    sp.edit().putFloat(key, value).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        return seek;
    }

    private String formatValue(String key, float value) {
        String unit = (Prefs.K_WIDTH.equals(key) || Prefs.K_HEIGHT.equals(key)) ? " dp" : "";
        return String.format("%.2f%s", value, unit);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
