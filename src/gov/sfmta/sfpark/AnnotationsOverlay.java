/*
  Copyright (C) 2011 San Francisco Municipal Transportation Agency (SFMTA)

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package gov.sfmta.sfpark;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint.Style;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View.OnClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView.LayoutParams;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.Projection;

import org.json.JSONException;

import java.util.ArrayList;

public class AnnotationsOverlay extends ItemizedOverlay {

    private ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
    private static final String TAG = "SFpark";
    Drawable [] iconArray;
    private boolean    isPinch      = false;
    ProgressDialog     pd;
    boolean            myShowPrice;
    LoadOverlaysTask   ld           = null;
    MyAnnotation       aa           = null;
    Context            mContext;
    int                closestIndex = 2000;
    int                index        = 0;
    double             dist         = 3000;
    double             lastDist     = 30000000;
    MyAnnotation       c            = null;
    float              lat0,lon0;
    AlertDialog        tapdlg       = null;
    public static View popupView    = null;
    int                action, last_action;

    public AnnotationsOverlay(Drawable defaultMarker) {
        super(boundCenterBottom(defaultMarker));
    }

    public AnnotationsOverlay(Drawable defaultMarker, Context context) {
        super(boundCenterBottom(defaultMarker));
        mContext = context;

        iconArray = new Drawable[8];

        ShapeDrawable invisible = new ShapeDrawable(new RectShape());
        invisible.getPaint().setColor(0x00000000);

        iconArray[0]  = invisible;
        iconArray[1]  = mContext.getResources().getDrawable(R.drawable.invalid_garage);
        iconArray[2]  = mContext.getResources().getDrawable(R.drawable.garage_availability_high);
        iconArray[3]  = mContext.getResources().getDrawable(R.drawable.garage_availability_medium);
        iconArray[4]  = mContext.getResources().getDrawable(R.drawable.garage_availability_low);
        iconArray[5] = mContext.getResources().getDrawable(R.drawable.garage_price_low);
        iconArray[6] = mContext.getResources().getDrawable(R.drawable.garage_price_medium);
        iconArray[7] = mContext.getResources().getDrawable(R.drawable.garage_price_high);


        boundCenterBottom(iconArray[0]);
        boundCenterBottom(iconArray[1]);
        boundCenterBottom(iconArray[2]);
        boundCenterBottom(iconArray[3]);
        boundCenterBottom(iconArray[4]);
        boundCenterBottom(iconArray[5]);
        boundCenterBottom(iconArray[6]);
        boundCenterBottom(iconArray[7]);

        action = 0;
        last_action = 0;
    }

    public void loadOverlays(boolean showprice) {
        Log.v(TAG, "Drawing annotations");
        int itemImageName = 0;
        mOverlays.clear();

        for (MyAnnotation a : MainScreenActivity.annotations) {
            try {
                a.blockColor = a.blockfaceColorizerWithShowPrice(showprice);
                a.blockColorAvailability = a.blockColor;

                OverlayItem item = new OverlayItem(a.mid, a.title, null);

                itemImageName = a.iconFinder(showprice);
                // Upper and lower bounds for the index mapping into
                // the different icons.
                if ((itemImageName >= 0) && (itemImageName < 8)) {
                    item.setMarker(iconArray[itemImageName]);
                } else {
                    Log.v(TAG, "Out of range " + itemImageName + a);
                }

                mOverlays.add(item);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        populate();
        Log.v(TAG, "Finished drawing annotations");
    }

    public void addOverlay(OverlayItem overlay) {
        mOverlays.add(overlay);
        populate();
    }

    @Override
    protected OverlayItem createItem(int i) {
        return mOverlays.get(i);
    }

    @Override
    public int size() {
        return mOverlays.size();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e, MapView mapView) {
        int fingers = e.getPointerCount();
        int action = e.getAction();

        Log.v(TAG, "onTouchEvent " + action);

        if (action ==  MotionEvent.ACTION_DOWN) {
            isPinch=false;
        }

        if (action == MotionEvent.ACTION_MOVE && fingers == 2 ) {
            isPinch=true;
        }

        if (popupView != null) {
            MainScreenActivity.mapView.removeView(popupView);
            popupView = null;
        }

        return super.onTouchEvent(e,mapView);
    }

    @Override
    protected boolean onTap(final int index) {
        Log.v(TAG, "onTap");

        if (isPinch) {
            return false;
        }

        aa = MainScreenActivity.annotations.get(index);

        if (aa == null) {
            return false;
        }

        try {
            aa.subtitle = aa.availabilityDescriptionShowingPrice(MainScreenActivity.showPrice);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        GeoPoint geoPoint = aa.mid;

        LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        popupView = vi.inflate(R.layout.dialog, null);

        TextView title = (TextView)popupView.findViewById(R.id.detailTitle);
        TextView subtitle = (TextView)popupView.findViewById(R.id.detailSubtitle);


        title.setText(aa.title);

        if (aa.subtitle != null) {
            subtitle.setText(aa.subtitle);
        } else {
            subtitle.setVisibility(View.GONE);
        }

        LayoutParams mapDialogParams = new LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            geoPoint, 0, -20,
            LayoutParams.CENTER);
        MainScreenActivity.mapView.addView(popupView, mapDialogParams);

        popupView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.v(TAG, "Details tapped");
                    DetailViewActivity.present(mContext, aa);
                    return;
                }
            });

        //PreDraw creates infinite loop situations
        ViewTreeObserver vto = popupView.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    Log.v(TAG, "onGlobalLayout");

                    if ((aa == null)
                        || (MainScreenActivity.mapView == null)
                        || (MainScreenActivity.mc == null)
                        || (popupView == null)) {
                        return;
                    }

                    // compromise until figure out horiz Point
                    // offset(dx, 0) projection toPixels fromPixels
                    // dip - pixels - lon getMeasuredWidth etc
                    if ((popupView.getLeft() < MainScreenActivity.mapView.getLeft())
                        || (popupView.getRight() > MainScreenActivity.mapView.getRight())) {
                        // assumes aa is set!
                        MainScreenActivity.mc.animateTo(aa.mid);
                    }

                    // Separated for clarity - something missing re
                    // -20 at top and view height at bottom...

                    if ((popupView.getTop() < MainScreenActivity.mapView.getTop())
                        || (popupView.getBottom() > MainScreenActivity.mapView.getBottom())) {
                        // assumes aa is set!
                        MainScreenActivity.mc.animateTo(aa.mid);
                    }

                    // make sure only called one time!
                    popupView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
            });


        return true;
    }

    public void draw(Canvas canvas, MapView mapv, boolean shadow) {
        if (shadow) {
            return;
        }

        Projection projection = mapv.getProjection();

        Point from = new Point();
        Point to = new Point();

        Paint mPaint = new Paint();
        mPaint.setStyle(Style.STROKE);
        mPaint.setAntiAlias(true);
        mPaint.setStrokeWidth(6);

        for (MyAnnotation a : MainScreenActivity.annotations) {
            if (a.onStreet) {
                projection.toPixels(a.nw, from);
                projection.toPixels(a.se, to);
                if (MainScreenActivity.showPrice) {
                    mPaint.setColor(a.blockColorPrice);
                } else {
                    mPaint.setColor(a.blockColorAvailability);
                }
                canvas.drawLine(from.x, from.y, to.x, to.y, mPaint);
            }
        }

        super.draw(canvas, mapv, shadow);
    }

    public void loadOverlaysProgress(boolean showprice) {

        myShowPrice = showprice;
        mOverlays.clear();

        Log.v(TAG, "Drawing annotations");

        pd = new ProgressDialog(mContext);
        pd.setCancelable(false);
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMessage("Calculating availability");
        pd.setProgress(0);
        pd.setMax(MainScreenActivity.annotations.size());
        pd.show();

        ld  = new LoadOverlaysTask();
        ld.execute("String");
    }

    class LoadOverlaysTask extends AsyncTask<String, Void, Void> {
        protected void onPreExecute() {
            if (myShowPrice) {
                pd.setMessage("Calculating pricing display");
            }
            pd.show();
        }

        protected Void doInBackground(String... urls) {
            int itemImageName = -1;
            long startTime = System.currentTimeMillis();

            try {
                for (MyAnnotation a : MainScreenActivity.annotations) {
                    //called atleast twice
                    a.blockColor = a.blockfaceColorizerWithShowPrice(myShowPrice);
                    a.blockColorPrice = a.blockColor;

                    OverlayItem item = new OverlayItem(a.mid, a.title, null);

                    itemImageName = a.iconFinder(myShowPrice);

                    // Upper and lower bounds for the index mapping
                    // into the different icons.
                    if ((itemImageName >= 0) && (itemImageName < 8)) {
                        item.setMarker(iconArray[itemImageName]);
                    } else {
                        Log.v(TAG, "Out of range " + itemImageName + a);
                    }

                    mOverlays.add(item);
                    pd.incrementProgressBy(1);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            long endTime = System.currentTimeMillis();
            Log.v(TAG, String.format("Time taken %d ms ", endTime - startTime));
            return null;
        }

        protected void onPostExecute(Void unused) {
            pd.setMessage("Populating pricing display");
            populate();

            Log.v(TAG, "Finished drawing annotations");
            pd.dismiss();
            MainScreenActivity.updateMap();
        }
    }
}
