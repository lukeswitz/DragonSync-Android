package com.rootdown.dragonsync.ui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.rootdown.dragonsync.R;

public class CircularGaugeView extends FrameLayout {
    private CircularProgressIndicator progressBackground;
    private CircularProgressIndicator progress;
    private TextView valueText;
    private TextView unitText;
    private TextView titleText;

    public CircularGaugeView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public CircularGaugeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        // Read custom attributes
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CircularGaugeView);

        String title = a.getString(R.styleable.CircularGaugeView_gaugeTitle);
        float value = a.getFloat(R.styleable.CircularGaugeView_gaugeValue, 0f);
        String unit = a.getString(R.styleable.CircularGaugeView_gaugeUnit);
        int color = a.getColor(R.styleable.CircularGaugeView_gaugeColor, Color.GREEN);

        a.recycle();

        init(context);

        // Apply attributes after initialization
        setTitle(title != null ? title : "");
        setValue(value);
        setUnit(unit != null ? unit : "");
        setColor(color);
    }

    public CircularGaugeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_circular_gauge, this, true);

        progressBackground = findViewById(R.id.progress_background);
        progress = findViewById(R.id.progress);
        valueText = findViewById(R.id.value);
        unitText = findViewById(R.id.unit);
        titleText = findViewById(R.id.title);

        // Set default values
        setValue(0);
        setUnit("");
        setTitle("");
        setColor(Color.GREEN);
    }

    public void setValue(double value) {
        // Format value based on magnitude
        String formattedValue;
        if (value >= 100) {
            formattedValue = String.format("%.0f", value);
        } else if (value >= 10) {
            formattedValue = String.format("%.1f", value);
        } else {
            formattedValue = String.format("%.1f", value);
        }

        valueText.setText(formattedValue);

        // Set progress (clamp between 0-100)
        int progressValue = (int) Math.min(100, Math.max(0, value));
        progress.setProgress(progressValue);
    }

    public void setUnit(String unit) {
        unitText.setText(unit);
    }

    public void setTitle(String title) {
        titleText.setText(title);
    }

    public void setColor(int color) {
        progress.setIndicatorColor(color);
        valueText.setTextColor(color);
        titleText.setTextColor(color);
        unitText.setTextColor(color);

    }
}