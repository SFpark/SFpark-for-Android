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

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DetailViewActivity extends SherlockActivity {

    private static final String TAG = "SFpark";
    public static MyAnnotation annotation = null;

    public String	row,row2,beg,end,from,to,rate,desc,rq,rr;

    private TextView garageUse;
    private TextView addressLabel;
    private TextView phoneTextView;
    private TextView detailName;

    private LinearLayout listLayout;
    static Context       mContext;

    boolean displayedRates = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detailview);

        if (annotation == null) {
            Log.v(TAG, "annotation is null");
            return;
        }

        // Set up ActionBar
        ActionBar ab = getSupportActionBar();
        ab.setDisplayShowTitleEnabled(true);
        ab.setTitle("  ::  " + annotation.title);
        ab.setIcon(R.drawable.logo_header);
        ab.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        ab.setDisplayHomeAsUpEnabled(true);

        detailName = (TextView) findViewById(R.id.detailName);
        detailName.setText(annotation.title.toUpperCase());

        garageUse = (TextView) findViewById(R.id.usageText);
        garageUse.setText(annotation.subtitle);

        View availColor = findViewById(R.id.blockColor);
        availColor.setBackgroundColor(
                annotation.onStreet ? annotation.blockColorAvailability : annotation.garageColor);

        if (!annotation.onStreet) {
            addressLabel = (TextView) findViewById(R.id.addressText);
            phoneTextView = (TextView) findViewById(R.id.phoneText);

            addressLabel.setVisibility(View.VISIBLE);
            phoneTextView.setVisibility(View.VISIBLE);

            String str;

            str = annotation.allGarageData.optString("DESC") +
                    " (" + annotation.allGarageData.optString("INTER") +
                    ")";

            if (str != null) {
                addressLabel.setText(str);
            }

            str = annotation.allGarageData.optString("TEL");
            if (str != null) {
                phoneTextView.setText(str);
            }

        }

        listLayout = (LinearLayout)findViewById(R.id.detailLinearLayout);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        try {
            parseHours();
            parseRates();
            parseInfo();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    //create a text view for the header of a section
    public View headerText(String val) {
        View header = View.inflate(this,R.layout.header_row, null);
        TextView title = (TextView) header.findViewById(R.id.header_title);
        title.setText(val);
        return header;
    }

    //create a text view for the regular text within a section
    public TextView normalText(String val) {
        TextView ntv = new TextView(this);
        ntv.setPadding(8, 7, 5, 7);
        ntv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        ntv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        ntv.setTextColor(Color.BLACK);
        ntv.setText(val);
        return ntv;
    }


    //create a text view for highlighted text within a section
    public TextView highlightText(String val) {
        TextView gtv = normalText(val);
        gtv.setBackgroundResource(R.color.highlight_row);
        return gtv;
    }


    public static void present(Context ctx, MyAnnotation a) {
        Intent intent = new Intent(ctx, DetailViewActivity.class);
        annotation = a;
        ctx.startActivity(intent);
        mContext = ctx;
    }


    private String fixDay(String dstr) {
        return dstr;
    }

    private void parseHours() throws JSONException {
        Log.v(TAG, "parsing hours");

        beg	 = null;
        end	 = null;
        from = null;
        to	 = null;

        JSONObject ophrs = annotation.allGarageData.optJSONObject("OPHRS");

        if (ophrs == null) {
            return;
        }

        listLayout.addView(headerText(getString(R.string.hours)));

        JSONArray  opsArray = ophrs.optJSONArray("OPS");

        // its not an array, just one object
        if (opsArray == null) {
            JSONObject opsObject = ophrs.optJSONObject("OPS");

            // want null if fail not 'empty string' ?
            beg	 = opsObject.optString("BEG",  null);
            end	 = opsObject.optString("END",  null);
            from = opsObject.optString("FROM", null);
            to	 = opsObject.optString("TO",   null);

            if (end != null) {
                end = "- " + end;
            } else {
                end = "";
            }

            if (to != null) {
                to = fixDay(to);
                to = "- " + to;
            } else {
                to = "";
            }

            if (beg == null) beg = "";
            if (from == null) from = "";

            row = fixDay(from) + " " + to + ": " + beg + " " + end;

            listLayout.addView(normalText(row));

            return;
        }

        int hoursRows = opsArray.length();
        for (int i =0; i < hoursRows; i++) {
            JSONObject opsObject = opsArray.getJSONObject(i);

            // want null if fail not 'empty string' ?
            beg	 = opsObject.optString("BEG",  null);
            end	 = opsObject.optString("END",  null);
            from = opsObject.optString("FROM", null);
            to	 = opsObject.optString("TO",   null);

            if (to != null) {
                row = fixDay(from) + " - " + fixDay(to) + ": " + beg + " - " + end;
            } else {
                row = fixDay(from) + ": " + beg + " - " + end;
            }

            listLayout.addView(normalText(row));
            hr();
        }

    }

    //RS + BEG/END goes to Rates bucket. If DESCs only, don't display section
    private void parseRates() throws JSONException {
        Log.v(TAG, "parsing rates");

        beg	 = null;
        end	 = null;
        from = null;
        to	 = null;

        JSONObject rates = annotation.allGarageData.optJSONObject("RATES");

        displayedRates = false;

        if (rates == null) {
            return;
        }

        JSONArray  rsArray = rates.optJSONArray("RS");

        if (rsArray == null) {	//its not an array, just one object
            JSONObject rsObject = rates.optJSONObject("RS");

            // want null if fail not 'empty string' ?
            beg	 = rsObject.optString("BEG",  null);
            end	 = rsObject.optString("END",  null);
            rate = rsObject.optString("RATE", null);
            rq	 = rsObject.optString("RQ",	  null);

            float phr = Float.parseFloat(rate);

            if (beg != null) {
                listLayout.addView(headerText(getString(R.string.rates)));
                displayedRates = true;

                row = beg + " - " + end;

                if (phr == 0) {
                    row2 = rq;
                } else {
                    row2 = String.format("$%.2f hr",phr);
                }

                row = row + "  " + row2;

                if (annotation.inThisBucketBegin(beg, end)) {
                    listLayout.addView(highlightText(row));
                } else {
                    listLayout.addView(normalText(row));
                }
            }
            return;
        }

        int rsc = rsArray.length();

        for (int i =0; i < rsc; i++) {
            JSONObject rsObject = rsArray.getJSONObject(i);

            // want null if fail not 'empty string' ?
            beg	 = rsObject.optString("BEG",  null);
            end	 = rsObject.optString("END",  null);
            rate = rsObject.optString("RATE", null);
            rq	 = rsObject.optString("RQ",	  null);

            float phr = Float.parseFloat(rate);

            if (beg != null) {
                if (!displayedRates) {
                    listLayout.addView(headerText(getString(R.string.rates)));
                    displayedRates = true;
                }

                row = beg + " - " + end;

                if (phr == 0) {
                    row2 = rq;
                } else {
                    row2 = String.format("$%.2f hr",phr);
                }

                row = row + "  " + row2;

                if (annotation.inThisBucketBegin(beg, end)) {
                    listLayout.addView(highlightText(row));
                } else {
                    listLayout.addView(normalText(row));
                }

                hr();
            }
        }
    }

    private void hr() {
        View ruler = new View(mContext); ruler.setBackgroundColor(Color.WHITE);
        listLayout.addView(ruler, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 2));
    }

    // RS + DESC goes to Information bucket.
    private void parseInfo() throws JSONException {
        Log.v(TAG, "parsing info");

        beg	 = null;
        end	 = null;
        from = null;
        to	 = null;

        boolean displayedInfo = false;

        JSONObject rates = annotation.allGarageData.optJSONObject("RATES");

        if (rates == null) {
            return;
        }

        JSONArray  rsArray = rates.optJSONArray("RS");

        // just return if it is not an array
        if (rsArray == null) {
            return;
        }

        int rsc = rsArray.length();

        for (int i =0; i < rsc; i++) {
            JSONObject rsObject = rsArray.getJSONObject(i);

            desc = rsObject.optString("DESC",  null);
            rate = rsObject.optString("RATE",  null);
            rq	 = rsObject.optString("RQ",	  null);
            rr	 = rsObject.optString("RR",	  null);

            float phr = Float.parseFloat(rate);

            if (desc != null) {
                if (!displayedInfo) {
                    if (displayedRates) {
                        listLayout.addView(headerText(getString(R.string.info)));
                    } else {
                        listLayout.addView(headerText(getString(R.string.rates)));
                    }
                    displayedInfo = true;
                }

                row = desc + ":";
                listLayout.addView(normalText(row));

                if (phr == 0) {
                    listLayout.addView(normalText(rq));
                } else {
                    if (rq != null) {
                        row = String.format("$%.2f ",phr) + rq;
                    } else {
                        row = String.format("$%.2f hr",phr);
                    }

                    listLayout.addView(normalText(row));
                }

                if (rr != null) {
                    //check for semicolon, insert new line
                    String rrn = rr.replace(";", "\n");
                    listLayout.addView(normalText(rrn));
                }
                hr();
            }
        }
    }
}
