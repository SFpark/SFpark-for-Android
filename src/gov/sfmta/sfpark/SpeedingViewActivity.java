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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View.OnClickListener;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageButton;

public class SpeedingViewActivity extends Activity {
    public static boolean speeding;

    static Context mContext;
    static Intent speedintent;

    private static final String TAG = "SFpark:SpeedingViewActivity";

    WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");

        super.onCreate(savedInstanceState);

        speeding = false;

        setContentView(R.layout.speedingview);

        webView = (WebView) findViewById(R.id.webView3);
        webView.loadUrl("file:///android_asset/speeding.html");

        ImageButton acceptButton = (ImageButton) findViewById(R.id.Button_ACCEPT);
        acceptButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    speeding = false;
                    finish();
                }
            });
    }

    public static void present(Context ctx) {
        speeding = true;
        mContext = ctx;
        speedintent = new Intent(ctx, SpeedingViewActivity.class);
        ctx.startActivity(speedintent);
    }
}
