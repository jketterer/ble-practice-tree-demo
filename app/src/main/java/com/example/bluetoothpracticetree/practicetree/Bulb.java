package com.example.bluetoothpracticetree.practicetree;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.bluetoothpracticetree.R;

/*
    This class creates a custom view for the bulbs displayed in the practice tree.
*/

public class Bulb extends View {

    private int radius;
    private boolean active;
    private boolean persisted;
    private int color;
    private Paint paint;

    public Bulb(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        // Get attributes from R.styleable.bulb
        TypedArray attributes = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.Bulb,
                0, 0
        );

        // Apply custom attributes to bulb
        try {
            radius = attributes.getInteger(R.styleable.Bulb_radius, 90);
            active = attributes.getBoolean(R.styleable.Bulb_active, false);
            color = attributes.getColor(R.styleable.Bulb_color, Color.YELLOW);
        } finally {
            attributes.recycle();
        }

        init();
    }
    
    private void init() {
        paint = new Paint();
        paint.setColor(color);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (active) {
            paint.setAlpha(255);
        } else {
            paint.setAlpha(100);
        }

        canvas.drawCircle(radius, radius, radius, paint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(radius*2, radius*2);
    }

    public void setActive(boolean active) {
        this.active = active;
        invalidate();
    }

    // This method turns a bulb "on" for 0.5 seconds, then "off" again
    public void activate() {
        if (active)
            return;

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!persisted) {
                    setActive(false);
                }
            }
        }, 500);

        setActive(true);
    }

    // This method turns a bulb "on" indefinitely
    public void persist() {
        setActive(true);
        persisted = true;
    }

    // This method turns a bulb "off"
    public void reset() {
        setActive(false);
        persisted = false;
    }

    public boolean isActive() {
        return active;
    }
}
