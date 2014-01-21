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
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockMapActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import com.google.android.maps.*;

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

public class MainScreenActivity extends SherlockMapActivity
        implements ActionBar.OnNavigationListener
{

    private static final String TAG = "SFpark";
    private static final String WARNING_KEY = "SFpark:SpeedWarning";

    public static ProgressDialog pd;

    static final double INITIAL_LATITUDE = 37.78;
    static final double INITIAL_LONGITUDE = -122.42;

    // 10.0 mph == 4.47 mps Driving speed threshold
    static float SPEED_THRESHOLD = (float) 4.47;

    static MyLocationOverlay mBlueDot;
    LocationManager  locationManager;
    LocationListener locationListener;
    String		   thisProvider;
    float            speed, lastSpeed;
    Location         userLocation;

    public static ArrayList<MyAnnotation> annotations;

    static AnnotationsOverlay availabilityAnnotationsOverlay = null;
    static AnnotationsOverlay pricingAnnotationsOverlay = null;

    TextView       debugText    = null;
    static boolean showPrice    = true;
    String         timeStampXML = null;
    boolean        warningSeen  = false;

    TextView  legendlabel;
    ImageView legendImage;

    public static DoubleTapMapView mapView;
    public static MapController mc;
    public static Date timeStamp;
    public static int  timeStampMinutes;

    String serviceURL = "http://api.sfpark.org/sfpark/rest/availabilityservice?radius=5.0&response=json&pricing=yes&version=1.0";
    // String serviceURL = "http://api.sfpark.org/sfparkTestData.json";
    // String serviceURL = "http://75.10.224.12:9001/testb/sfpark/rest/availabilityservice?radius=5.0&response=json&pricing=yes&version=1.0";

    private static final int SECOND_IN_MILLIS = (int) DateUtils.SECOND_IN_MILLIS;

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        FlurryAgent.onEndSession(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        MYLOG("onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Set up ActionBar
        ActionBar ab = getSupportActionBar();
        ab.setDisplayShowTitleEnabled(false);
        ab.setIcon(R.drawable.logo_header);
        ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        ab.addTab(ab.newTab()
                .setText("Price")
                .setTabListener(new TabListener()));
        ab.addTab(ab.newTab()
                .setText("Availability")
                .setTabListener(new TabListener()));


        // Dropdown navigation
        final String[] dropdownValues = getResources().getStringArray(R.array.dropdown);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(ab.getThemedContext(),
                R.layout.sherlock_spinner_item, android.R.id.text1, dropdownValues);
        adapter.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
        ab.setListNavigationCallbacks(adapter, this);

        if (SFparkActivity.DEBUG) {
            debugText = (TextView) findViewById(R.id.debugText);
            debugText.setText("Debug String");
            debugText.setVisibility(View.VISIBLE);
        }

        legendlabel = (TextView) findViewById(R.id.legendText);
        legendImage = (ImageView) findViewById(R.id.keyImage);

        // DoubleTapMapView... is a mapview that you can double-tap. :-/
        mapView = new DoubleTapMapView(this);
        FrameLayout fl = (FrameLayout) findViewById(R.id.mapframe);
        fl.addView(mapView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mapView.setBuiltInZoomControls(true);
        mc = mapView.getController();

        // Creating and initializing Map
        GeoPoint p = getPoint(INITIAL_LATITUDE,INITIAL_LONGITUDE);

        mc.setCenter(p);
        mc.setZoom(14);

        startLocation();

        showPrice = true;

        enablePanToMe();
        reset();
    }

    private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";

    void enablePanToMe() {
        ImageButton btn = (ImageButton) findViewById(R.id.btnPanToMyLoc);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            try {
                GeoPoint p = mBlueDot.getMyLocation();
                mapView.getController().animateTo(p);
                mc.setZoom(17);
            } catch (NullPointerException npe) {
                // no zoomiepuss if no network
            }
            }
        });
    }

    class TabListener implements ActionBar.TabListener {

        @Override
        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
            if (tab!=null) onNavigationItemSelected(tab.getPosition(),0);
        }

        @Override
        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }

        @Override
        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Restore the previously serialized current dropdown position.
        if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM)) {
            getSupportActionBar().setSelectedNavigationItem(savedInstanceState.getInt(STATE_SELECTED_NAVIGATION_ITEM));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Serialize the current dropdown position.
        outState.putInt(STATE_SELECTED_NAVIGATION_ITEM,
            getSupportActionBar().getSelectedNavigationIndex());
    }

    /** show the correct view when dropdown is changed */
    @Override
    public boolean onNavigationItemSelected(int position, long id) {

        try {
            if (position==0) { // prices
                if (showPrice == false) {
                    FlurryAgent.logEvent("Pricing_Mode_Shown");
                    mapView.removeAllViews();
                    showPrice = true;
                    displayData(showPrice);
                    return true;
                }
            }

            if (position==1) { // availability
                if (showPrice == true) {
                    FlurryAgent.logEvent("Availability_Mode_Shown");
                    mapView.removeAllViews();
                    showPrice = false;
                    displayData(showPrice);
                }
                return true;
            }

        } catch (NullPointerException npe) {
            //yawn
        }
        return true;
    }

    void reset() {
        List<Overlay> mapOverlays = mapView.getOverlays();
        mapOverlays.clear();
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
        getSupportMenuInflater().inflate(R.layout.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.m_refresh:
            {
                FlurryAgent.logEvent("Refresh_Button_Pressed");
                reset();
                return true;
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
        if (annotations != null) {
            annotations.clear();
            annotations = null;
        }
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

        if (mBlueDot != null) {
            if (state) {
                mBlueDot.enableMyLocation();
            }
            else {
                mBlueDot.disableMyLocation();
            }
        }

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
        FlurryAgent.logEvent("About_Mode_Shown");
        CreditsViewActivity.present(mapView.getContext());
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

            // new: UBER-HACK! SFMTA wants me to hide the Calif/Steiner lot.
            if ("California and Steiner Lot".equals(annotation.title)) continue;

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
            } catch (NullPointerException npe) {
                // this can happen if activity finished.  ignore.
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
        } else {                  // NPE!!
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

        if (mBlueDot==null) mBlueDot = new MyLocationOverlay(mapView.getContext(), mapView);
        mBlueDot.enableMyLocation();
        mapOverlays.add(mBlueDot);

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
