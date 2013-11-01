package gov.sfmta.sfpark;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import com.google.android.maps.MapView;

/** Enable double-tap to zoom */
public class DoubleTapMapView extends MapView {
    static final String APIKEY = "--your-api-key-here--";
    long lastTouchTime = -1;

  public DoubleTapMapView(Context ctx) {
    super(ctx, APIKEY);

    this.setClickable(true);
    this.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT));
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {

    if (ev.getAction() == MotionEvent.ACTION_DOWN) {

      long thisTime = System.currentTimeMillis();
      if (thisTime - lastTouchTime < 250) {

        // Double tap
        try {
          this.getController().zoomInFixing((int) ev.getX(), (int) ev.getY());
        } catch (ArrayIndexOutOfBoundsException aioobe) {
          // weird.... ignore.
        }
        lastTouchTime = -1;

      } else {

        // Too slow
        lastTouchTime = thisTime;
      }
    }

    return super.onInterceptTouchEvent(ev);
  }
}
