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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.TextView;

public class CreditsViewActivity extends Activity {
    static Context mContext;

    WebView  webView;
    String   build;
    TextView buildText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.credits);

        if (SFparkActivity.DEBUG) {
            // prepend the versioncode and name from manifest
            build = SFparkActivity.VersionText +
                    " (build " + SFparkActivity.VersionCode + ")";
        } else {
            build = SFparkActivity.VersionText;
        }

        buildText = (TextView) findViewById(R.id.textView1);
        buildText.setText(build);

        webView = (WebView) findViewById(R.id.webView2);
        webView.loadUrl("file:///android_asset/credits.html");
    }

    public static void present(Context ctx) {
        Intent intent = new Intent(ctx, CreditsViewActivity.class);
        ctx.startActivity(intent);
        mContext = ctx;
    }
}
