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

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;

public class SFparkActivity extends SherlockActivity {
    public static final boolean DEBUG = false;

    static String VersionText ="error";
    static String VersionCode ="error";
    static String responseString = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);
        TextView version = (TextView) findViewById(R.id.textVersion);

        // Set up ActionBar
        ActionBar ab = getSupportActionBar();
        ab.setDisplayShowTitleEnabled(false);
        ab.setTitle(R.string.app_name);
        ab.setIcon(R.drawable.logo_header);


        PackageManager pm = getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo("gov.sfmta.sfpark", 0);
            VersionText = "Version " + pi.versionName;
            VersionCode = Integer.toString(pi.versionCode);

            if (DEBUG) {
                version.setText(VersionText + "\nBuild " + VersionCode);
            } else {
                version.setText(VersionText);
            }

        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        /** create a thread to show splash up to splash time */
        Thread welcomeThread = new Thread() {

                // 2 seconds, enough time to read version number
                long endit = System.currentTimeMillis() + (1400);

                @Override
                public void run() {
                    try {
                        super.run();
                        while (System.currentTimeMillis() < endit) {}
                    } catch (Exception e) {
                        System.out.println("EXc=" + e);
                    } finally {
                      // Called after splash times up. Do some action after splash
                      //  times up. Here we moved to another main activity class
                        if (DEBUG) {
                            startActivity(new Intent(SFparkActivity.this, MainScreenActivity.class));
                        } else {
                            startActivity(new Intent(SFparkActivity.this, DisclaimerViewActivity.class));
                        }
                        finish();
                    }
                }
            };
        welcomeThread.start();
    }
}
