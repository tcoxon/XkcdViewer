package net.bytten.xkcdviewer;

import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.content.*;

public class ClickableWebView extends WebView {
    public ClickableWebView(Context cxt) {
        super(cxt);
    }
    public ClickableWebView(Context cxt, AttributeSet attrs) {
        super(cxt, attrs);
    }
    public ClickableWebView(Context cxt, AttributeSet attrs, int defStyle) {
        super(cxt, attrs, defStyle);
    }

    private float lastTouchX, lastTouchY;
    private boolean hasMoved = false;

    private boolean moved(MotionEvent evt) {
        return hasMoved ||
            Math.abs(evt.getX() - lastTouchX) > 10.0 ||
            Math.abs(evt.getY() - lastTouchY) > 10.0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        boolean consumed = super.onTouchEvent(evt);

        if (isClickable()) {
            switch (evt.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = evt.getX();
                lastTouchY = evt.getY();
                hasMoved = false;
                break;
            case MotionEvent.ACTION_MOVE:
                hasMoved = moved(evt);
                break;
            case MotionEvent.ACTION_UP:
                if (!moved(evt)) performClick();
                break;
            }
        }

        return consumed;
    }

}
