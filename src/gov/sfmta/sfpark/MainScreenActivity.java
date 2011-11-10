/*
  Copyright (C) 2011 San Francisco Municipal Transportation Agency (SFMTA)

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.	If not, see <http://www.gnu.org/licenses/>.
*/

package gov.sfmta.sfpark;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class MainScreenActivity extends MapActivity
{

    private static final String TAG = "SFpark";
    private static final String WARNING_KEY = "SFpark:SpeedWarning";

    public static ProgressDialog pd;

    static final double INITIAL_LATITUDE = 37.78;
    static final double INITIAL_LONGITUDE = -122.42;

    // 10.0 mph == 4.47 mps Driving speed threshold
    static float SPEED_THRESHOLD = (float) 4.47;

    LocationManager  locationManager;
    LocationListener locationListener;
    String		   thisProvider;
    float            speed, lastSpeed;
    Location         userLocation;

    public static ArrayList<MyAnnotation> annotations;

    static AnnotationsOverlay availabilityAnnotationsOverlay = null;
    static AnnotationsOverlay pricingAnnotationsOverlay = null;

    static UserLocationOverlay userOverlay;


    TextView       debugText    = null;
    static boolean showPrice    = false;
    String         timeStampXML = null;
    boolean        warningSeen  = false;

    TextView  legendlabel;
    ImageView legendImage;

    public static MapView       mapView;
    public static MapController mc;
    public static Date          timeStamp;
    public static int           timeStampMinutes;

    String serviceURL = "http://api.sfpark.org/sfpark/rest/availabilityservice?radius=2.0&response=json&pricing=yes&version=1.0";

    private static final int SECOND_IN_MILLIS = (int) DateUtils.SECOND_IN_MILLIS;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        MYLOG("onCreate");

        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        if (SFparkActivity.DEBUG) {
            debugText = (TextView) findViewById(R.id.debugText);
            debugText.setText("Debug String");
            debugText.setVisibility(View.VISIBLE);
        }

        legendlabel = (TextView) findViewById(R.id.legendText);
        legendImage = (ImageView) findViewById(R.id.keyImage);

        mapView = (MapView) findViewById(R.id.mapview);
        mapView.setBuiltInZoomControls(true);
        mc = mapView.getController();

        // Creating and initializing Map
        GeoPoint p = getPoint(INITIAL_LATITUDE,INITIAL_LONGITUDE);

        userOverlay = new UserLocationOverlay(this);

        mc.setCenter(p);
        mc.setZoom(14);

        startLocation();

        ImageButton refreshButton = (ImageButton) findViewById(R.id.Button_REFRESH);
        refreshButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    reset();
                }
            });

        reset();
    }

    void reset() {
        List<Overlay> mapOverlays = mapView.getOverlays();
        mapOverlays.clear();
        showPrice = false;
        SFparkActivity.responseString = null;
        availabilityAnnotationsOverlay = null;
        pricingAnnotationsOverlay = null;
        if (annotations != null) {
            annotations.clear();
        }
        annotations = null;
        refreshData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.layout.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.m_availability:
                {
                    mapView.removeAllViews();
                    showPrice = false;
                    displayData(showPrice);
                    return true;
                }

            case R.id.m_price:
                {
                    if (showPrice == false) {
                        mapView.removeAllViews();
                        showPrice = true;
                        displayData(showPrice);
                        return true;
                    }
                }

            case R.id.m_about:
                {
                    showInfo();
                    return true;
                }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MYLOG("onDestroy");

        locationUpdates(false);
        SFparkActivity.responseString = null;
        mapView = null;
        showPrice = false;
        availabilityAnnotationsOverlay = null;
        pricingAnnotationsOverlay = null;
        annotations.clear();
        annotations = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "onPause");
        locationUpdates(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MYLOG("onResume");
        locationUpdates(true);
        updateMap();
    }

    public void locationUpdates(boolean state) {
        // 1000 for one second, 0 for as often as possible.
        // reality == anything. once per second dont kill battery?
        int interval = 10*1000;
        int traveled = 10;			// every 10 meters

        if (locationListener != null) {
            if (state && thisProvider != null) {
                locationManager.requestLocationUpdates(thisProvider,
                                                       interval, traveled,
                                                       locationListener);
                locationListener.onLocationChanged(
                    locationManager.getLastKnownLocation(thisProvider));
            } else {
                locationManager.removeUpdates(locationListener);
            }
        }
    }

    void refreshData() {
        pd = new ProgressDialog(mapView.getContext());
        LoadDataTask ld = new LoadDataTask();
        ld.execute("String");
    }


    public void showInfo() {
        MYLOG("showInfo");
        CreditsViewActivity.present(mapView.getContext());
    }

    protected class UserLocationOverlay extends com.google.android.maps.Overlay {
        private final Drawable reticle;
        private final Paint accuracyCirclePaint;
        private final Paint accuracyCircleFill;

        public UserLocationOverlay(Context context) {
            reticle = context.getResources().getDrawable(R.drawable.ic_maps_indicator_current_position);
            int reticleWidth = reticle.getIntrinsicWidth();
            int reticleHeight = reticle.getIntrinsicHeight();
            reticle.setBounds(0, 0, reticleWidth, reticleHeight);

            accuracyCirclePaint = new Paint();
            accuracyCirclePaint.setAntiAlias(true);
            accuracyCirclePaint.setColor(0xFFAABBFF);
            accuracyCirclePaint.setStyle(Paint.Style.STROKE);
            accuracyCirclePaint.setStrokeWidth(1);
            accuracyCircleFill = new Paint();
            accuracyCircleFill.setAntiAlias(true);
            accuracyCircleFill.setColor(0x33AABBFF);
            accuracyCircleFill.setStyle(Paint.Style.FILL_AND_STROKE);
        }

        @Override
        public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
            super.draw(canvas, mapView, shadow);

            if (userLocation == null) {
                return false;
            }

            Paint paint   = new Paint();
            Point pt      = new Point();
            int   offsetX = reticle.getIntrinsicWidth() / 2;
            int   offsetY = reticle.getIntrinsicHeight() / 2;

            Projection projection = mapView.getProjection();
            projection.toPixels(getPointFromLocation(userLocation), pt);
            float radius = projection.metersToEquatorPixels(userLocation.getAccuracy());
            canvas.drawCircle(pt.x, pt.y, radius, accuracyCircleFill);
            canvas.drawCircle(pt.x, pt.y, radius, accuracyCirclePaint);
            canvas.save();
            canvas.translate(pt.x - offsetX, pt.y - offsetY);
            reticle.draw(canvas);
            canvas.restore();

            if (SFparkActivity.DEBUG) {
                paint.setStrokeWidth(1);
                paint.setColor(Color.BLACK);
                paint.setStyle(Paint.Style.STROKE);
                Double mph = speed * 2.23693629;
                String spd = String.format("Speed %dmph", mph.intValue());
                canvas.drawText(spd, pt.x, pt.y, paint);
            }

            return true;
        }
    }


    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    public boolean loadData() {
        MYLOG("loadData() start");

        if (SFparkActivity.responseString != null) {
            return true;
        }

        boolean didLoad = false;

        try {
            SFparkActivity.responseString = getStringContent(serviceURL);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (SFparkActivity.responseString != null) {
            didLoad = true;
        }

        MYLOG("loadData() finish");

        return didLoad;
    }

    public void readData() throws JSONException {
        MYLOG("readData() start");

        if (SFparkActivity.responseString == null
            || SFparkActivity.responseString == "") {
            return;
        }

        String	   message = null;
        JSONObject rootObject = null;
        JSONArray  jsonAVL = null;

        rootObject = new JSONObject(SFparkActivity.responseString);
        message = rootObject.getString("MESSAGE");
        jsonAVL = rootObject.getJSONArray("AVL");
        timeStampXML = rootObject.getString("AVAILABILITY_UPDATED_TIMESTAMP");

        MYLOG(message);

        int i=0;
        int len = jsonAVL.length();

        if (annotations == null) {
            annotations = new ArrayList<MyAnnotation>();
        } else {
            annotations.clear();
        }

        for(i = 0; i < len; ++i) {
            JSONObject interestArea = jsonAVL.getJSONObject(i);
            MyAnnotation annotation = new MyAnnotation();
            annotation.allGarageData = interestArea;
            annotation.initFromData();
            annotations.add(annotation);
        }

        MYLOG("readData() finish ");
    }

    private class LoadDataTask extends AsyncTask<String, Void, Void> {
        protected void onPreExecute() {
            pd.setMessage("Downloading data..");
            pd.show();
        }

        protected Void doInBackground(String... urls) {
            loadData();
            return null;
        }

        protected void onPostExecute(Void unused) {
            try {
                pd.setMessage("Reading data..");
                readData();
                pd.setMessage("Displaying data..");
                displayData(false);
                pd.dismiss();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean displayData(boolean progress) {

        if (SFparkActivity.responseString == null
            || SFparkActivity.responseString == "") {
            return false;
        }

        // ISO8601 date
        SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd'T'H:mm:ss'.'SSSZZZZ" );
        try {
            timeStamp = df.parse(timeStampXML);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        SimpleDateFormat formatter = new SimpleDateFormat("h:mma");
        String now = formatter.format(timeStamp);

        if (showPrice) {
            legendImage.setImageResource(R.drawable.key_pricing);
            legendlabel.setText("Price as of " + now);
        } else {
            legendImage.setImageResource(R.drawable.key_availability);
            legendlabel.setText("Availability as of " + now);
        }

        //convert time stamp to minutes. String now is 24 hour time display.
        SimpleDateFormat formatter24 = new SimpleDateFormat("HH:mm");
        String now24 = formatter24.format(timeStamp);
        String[] time2 = now24.split(":");
        int hours = Integer.valueOf(time2[0]);
        int minutes = Integer.valueOf(time2[1]);

        // for price bucket calculations
        timeStampMinutes = (hours * 60) + minutes;

        if ((showPrice == true) && (pricingAnnotationsOverlay !=null)) {
            updateMap();
            return true;
        }

        if ((showPrice == false) && (availabilityAnnotationsOverlay !=null)) {
            updateMap();
            return true;
        }

        ShapeDrawable invisible = new ShapeDrawable(new RectShape());
        invisible.getPaint().setColor(0x00000000);

        if (showPrice == true) {
            pricingAnnotationsOverlay = new AnnotationsOverlay(invisible,mapView.getContext());
            pricingAnnotationsOverlay.loadOverlaysProgress(showPrice);
        } else {
            availabilityAnnotationsOverlay = new AnnotationsOverlay(invisible,mapView.getContext());
            availabilityAnnotationsOverlay.loadOverlays(showPrice);
            updateMap();
        }
        return true;
    }

    public static void updateMap() {
        if (mapView == null) {
            return;
        }

        List<Overlay> mapOverlays = mapView.getOverlays();
        mapOverlays.clear();

        if (showPrice) {
            if (pricingAnnotationsOverlay != null) {
                mapOverlays.add(pricingAnnotationsOverlay);
            }
        } else {
            if (availabilityAnnotationsOverlay != null) {
                mapOverlays.add(availabilityAnnotationsOverlay);
            }
        }

        if (userOverlay != null) {
            mapOverlays.add(userOverlay);
        }
        mapView.invalidate();
    }

    public static Location getLocation(GeoPoint p) {
        Location result = new Location("");
        result.setLatitude(p.getLatitudeE6() / 1.0E6);
        result.setLongitude(p.getLongitudeE6() / 1.0E6);
        return result;
    }

    public static GeoPoint getPointFromLocation(Location location) {
        return getPoint(location.getLatitude(),
                        location.getLongitude());
    }

    public static GeoPoint getPoint(double lat, double lon) {
        return new GeoPoint((int) (lat * 1E6),
                            (int) (lon * 1E6));
    }

    public static String getStringContent(String uri) throws Exception {
        try {
            HttpGet request = new HttpGet();
            request.setURI(new URI(uri));
            request.addHeader("Accept-Encoding", "gzip");

            final HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(params, 30 * SECOND_IN_MILLIS);
            HttpConnectionParams.setSoTimeout(params, 30 * SECOND_IN_MILLIS);
            HttpConnectionParams.setSocketBufferSize(params, 8192);

            final DefaultHttpClient client = new DefaultHttpClient(params);
            client.addResponseInterceptor(new HttpResponseInterceptor() {
                    public void process(HttpResponse response, HttpContext context) {
                        final HttpEntity entity = response.getEntity();
                        final Header encoding = entity.getContentEncoding();
                        if (encoding != null) {
                            for (HeaderElement element : encoding.getElements()) {
                                if (element.getName().equalsIgnoreCase("gzip")) {
                                    response.setEntity(new InflatingEntity(response.getEntity()));
                                    break;
                                }
                            }
                        }
                    }
                });

            return client.execute(request, new BasicResponseHandler());
        } finally {
            // any cleanup code...
        }
    }

    void startLocation() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);

        warningSeen = preferences.getBoolean (WARNING_KEY, false);

        if (warningSeen) {
            //once only, ever!
            return;
        }

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        speed = 0;
        lastSpeed = 0;

        thisProvider = LocationManager.GPS_PROVIDER;

        // Define a listener that responds to location updates
        locationListener = new LocationListener() {
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    MYLOG(String.format("onStatusChanged %d",status));
                }

                public void onProviderEnabled(String provider) {
                    MYLOG("onProviderEnabled" + provider);
                    thisProvider = provider;
                }

                public void onProviderDisabled(String provider) {
                    MYLOG("onProviderDisabled" + provider);
                    thisProvider = null;
                }

                @Override
                public void onLocationChanged(Location loc) {
                    MYLOG("onLocationChanged");

                    lastSpeed = speed;

                    if (loc == null) {
                        speed = 0;
                    } else {
                        // speed is returned in meters per second
                        speed = loc.getSpeed();
                        userLocation = loc;
                        mapView.invalidate();
                    }

                    // need last speed > threshold as well.
                    if (speed > SPEED_THRESHOLD) {
                        if (lastSpeed > SPEED_THRESHOLD) {
                            SharedPreferences preferences = getPreferences(MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();

                            warningSeen = preferences.getBoolean (WARNING_KEY, false);

                            if (warningSeen == false) {
                                warningSeen = true;
                                editor.putBoolean(WARNING_KEY, warningSeen);
                                editor.commit();
                                // turn off location now to save battery, the deal is done
                                // locationManager.removeUpdates(this);
                                SpeedingViewActivity.present(mapView.getContext());
                            }
                        }
                    }

                    if (SFparkActivity.DEBUG) {
                        String locStr = String.format("speed:%f last speed:%f, when %d",
                                                      speed, lastSpeed,
                                                      System.currentTimeMillis());
                        debugText.setText(locStr);
                    }
                }
            };

        locationUpdates(true);
    }

    public void MYLOG(String message) {
        String logStr = getClass().getName() + ": " + message;
        Log.v(TAG, logStr);
        if (SFparkActivity.DEBUG) {
            debugText.setText(logStr);
        }
    }

    private static class InflatingEntity extends HttpEntityWrapper {
        public InflatingEntity(HttpEntity wrapped) {
            super(wrapped);
        }

        @Override
        public InputStream getContent() throws IOException {
            return new GZIPInputStream(wrappedEntity.getContent());
        }

        @Override
        public long getContentLength() {
            return -1;
        }
    }
}
