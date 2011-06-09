package net.bytten.xkcdviewer;

import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.content.*;

public class HackedWebView extends WebView {
    public HackedWebView(Context cxt) {
        super(cxt);
    }
    public HackedWebView(Context cxt, AttributeSet attrs) {
        super(cxt, attrs);
    }
    public HackedWebView(Context cxt, AttributeSet attrs, int defStyle) {
        super(cxt, attrs, defStyle);
    }

    private float lastTouchX, lastTouchY;
    private boolean hasMoved = false;
    private boolean allowZoomButtons = false, allowPinchZoom = true;
    
    public boolean getAllowZoomButtons() {
        return allowZoomButtons;
    }
    public void setAllowZoomButtons(boolean b) {
        allowZoomButtons = b;
    }
    
    public void setAllowPinchZoom(boolean b) {
        allowPinchZoom = b;
    }
    public boolean getAllowPinchZoom() {
        return allowPinchZoom;
    }

    private boolean moved(MotionEvent evt) {
        return hasMoved ||
            Math.abs(evt.getX() - lastTouchX) > 10.0 ||
            Math.abs(evt.getY() - lastTouchY) > 10.0;
    }

    private static class PointerDownHack {
        public static boolean isPointerDown(MotionEvent evt) {
            return evt.getAction() == MotionEvent.ACTION_DOWN ||
                evt.getAction() == MotionEvent.ACTION_POINTER_DOWN ||
                /* These next three are deprecated, but we MUST use them to
                 * remain compatible with all devices! */
                evt.getAction() == MotionEvent.ACTION_POINTER_1_DOWN ||
                evt.getAction() == MotionEvent.ACTION_POINTER_2_DOWN ||
                evt.getAction() == MotionEvent.ACTION_POINTER_3_DOWN;
        }
        public static int getPointerCount(MotionEvent evt) {
            return evt.getPointerCount();
        }
    };
    private boolean isPointerDown(MotionEvent evt) {
        if (VersionHacks.getSdkInt() >= 5)
            return PointerDownHack.isPointerDown(evt);
        return false;
    }
    private int getPointerCount(MotionEvent evt) {
        if (VersionHacks.getSdkInt() >= 5)
            return PointerDownHack.getPointerCount(evt);
        return 1;
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        /* Horrible hack #1: Enable pinch-zoom without zoom buttons */
        if (!getAllowZoomButtons() && isPointerDown(evt)) {
            boolean allowZoom = getPointerCount(evt) > 1;
            getSettings().setSupportZoom(allowZoom);
            getSettings().setBuiltInZoomControls(allowZoom && getAllowPinchZoom());
        }

        boolean consumed = super.onTouchEvent(evt);

        /* Horrible hack #2: enable click events following drag / pinch-zoom */
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
