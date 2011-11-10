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

import android.graphics.Color;
import android.util.Log;

import com.google.android.maps.GeoPoint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MyAnnotation {

    private static final int invalid_garage             = 1;
    private static final int garage_availability_high   = 2;
    private static final int garage_availability_medium = 3;
    private static final int garage_availability_low	= 4;
    private static final int garage_price_low           = 5;
    private static final int garage_price_medium        = 6;
    private static final int garage_price_high          = 7;


    private static final int availHigh = Color.parseColor("#FF10CAFB");
    private static final int availLow  = Color.parseColor("#FFFD2C27");
    private static final int availMed  = Color.parseColor("#FF003C58");
    private static final int priceHigh = Color.parseColor("#FF1F4A1F");
    private static final int priceLow  = Color.parseColor("#FF00DA02");
    private static final int priceMed  = Color.parseColor("#FF1C741E");
    private static final int grey      = Color.parseColor("#FF8D8D8D");
    private static final int red       = Color.RED;

    // construct these once(ish), not 1000+ times...they are basically
    // #defines, and a baby step towards multiple language support.
    private static final String PRICE_STR       = "$%5.2f/hr";
    private static final String RESTRICTED_STR  = "Restricted";
    private static final String ESTIMATE_STR    = "Estimated %d of %d spaces available";
    private static final String INCREMENTAL_STR = "Incremental";
    private static final String NOCHARGE_STR    = "No charge";

    // for inThisBucket == 99% of time
    private static final String REPLACE0_STR = "12:00 AM";
    private static final String REPLACE2_STR = "11:59 PM";
    private static final String SPLIT2_STR   = ":";

    private static final String BEG_STR  = "BEG";
    private static final String END_STR  = "END";
    private static final String RATE_STR = "RATE";
    private static final String RQ_STR   = "RQ";
    private static final String AM_STR   = "AM";
    private static final String PM_STR   = "PM";

    private static final String OPER_KEY  = "OPER";
    private static final String OCC_KEY   = "OCC";
    private static final String DESC_KEY  = "DESC";
    private static final String RATES_KEY = "RATES";
    private static final String TYPE_KEY  = "TYPE";
    private static final String RS_KEY    = "RS";
    private static final String RATE_KEY  = "RATE";
    private static final String LOC_KEY   = "LOC";
    private static final String NAME_KEY  = "NAME";

    private static final String TAG = "SFpark";

    public GeoPoint   nw;
    public GeoPoint   se;
    public GeoPoint   mid;
    public float      lat1,lon1,lat2,lon2;
    public String	  title;
    public String	  subtitle;
    public JSONObject allGarageData;
    public String	  type;
    public String	  uniqueID;
    public Boolean    onStreet;
    public int        blockColor;
    public int        blockColorAvailability;
    public int        blockColorPrice;
    public float      pricePerHour;
    public String     rateQualifier;
    public String     row,beg,end,from,to,rate,desc,rq,rr;


    public boolean initFromData() throws JSONException {
        if (allGarageData == null) {
            return false;
        }

        String loc = allGarageData.getString(LOC_KEY);
        String[] params = loc.split(",");

        GeoPoint point1;
        GeoPoint point2;

        lon1 = Float.parseFloat(params[0]);
        lat1 = Float.parseFloat(params[1]);

        point1 = MainScreenActivity.getPoint(lat1,lon1);

        nw = point1;
        se = point1;
        mid = point1;

        onStreet = false;
        type = "garage";

        if (params.length == 4) {
            lon2 = Float.parseFloat(params[2]);
            lat2 = Float.parseFloat(params[3]);

            point2 = MainScreenActivity.getPoint(lat2,lon2);
            se = point2;
            mid = MainScreenActivity.getPoint((lat1 + lat2)/2,(lon1 + lon2)/2);

            onStreet = true;
            type = "blockface";
        }

        title = allGarageData.getString(NAME_KEY);
        subtitle = null;

        beg			  = null;		// Indicates the begin/end time
        end			  = null;		// for this rate schedule (or
        rate		  = null;       // hours schedule use same var for
        rq			  = null;       // both)
        pricePerHour  = 0;
        rateQualifier = "";

        return true;
    }

    public String availabilityDescriptionShowingPrice(boolean shouldShowPrice) throws JSONException {
        String descriptionOfAvailability = null;

        int numberOfOperationalSpaces;
        int numberOfOccupiedSpaces;
        rateQualifier = "";

        if (shouldShowPrice) {
            blockfaceColorizerWithShowPrice(true); // side-effect (using it to set the pricePerHour)
            iconFinder(true); // side-effect (using it to set the pricePerHour for Garages...)
            if (pricePerHour == 0.00) {
                descriptionOfAvailability = rateQualifier;
            } else {
                descriptionOfAvailability = String.format(PRICE_STR, pricePerHour);
            }

            if(!onStreet) {
                if (pricePerHour > 0.0) {
                    descriptionOfAvailability = String.format(PRICE_STR, pricePerHour);
                }
            }
        } else {
            numberOfOperationalSpaces = allGarageData.optInt(OPER_KEY,0);
            numberOfOccupiedSpaces = allGarageData.optInt(OCC_KEY,0);
            if (numberOfOccupiedSpaces > numberOfOperationalSpaces) {
                descriptionOfAvailability = null;
            } else if (numberOfOperationalSpaces == 0 && numberOfOccupiedSpaces == 0) {
                descriptionOfAvailability = RESTRICTED_STR;
            } else {
                descriptionOfAvailability =
                        String.format(ESTIMATE_STR,
                                      (numberOfOperationalSpaces - numberOfOccupiedSpaces),
                                      numberOfOperationalSpaces);
            }
        }
        return descriptionOfAvailability;
    }

    public int iconFinder(boolean showPrice) throws JSONException {
        int itemImageName = 0;
        if (onStreet) {
            return itemImageName;
        }

        JSONObject rates = allGarageData.optJSONObject(RATES_KEY);
        String type = allGarageData.optString(TYPE_KEY);
        int used = allGarageData.optInt(OCC_KEY,0);
        int capacity = allGarageData.optInt(OPER_KEY,0);
        int avail = capacity - used;
        int availpercent = 0;

        boolean invalidData = true;

        if (capacity == 0) {
            availpercent = 0;
            invalidData = true;
        } else {
            availpercent = Math.round((((float)avail/(float)capacity) * 100) * 10)/10;
            invalidData = false;
        }

        int usedpercent = 100 - availpercent;

        if(avail < 2 && avail > 0 && availpercent != 0 && capacity <= 3){
            if(availpercent <= 15) {
                usedpercent = -57; // less than 15 percent available. hack
            } else {
                usedpercent = -58; // more than 15 percent available. hack
            }
        } else if(capacity == 0  && used == 0 && type == "ON") {
            // On street parking, force it to red as capacity is zero
            usedpercent = -42;
        }

        // since the code above and code below is adapted from
        // two different functions from webmap.js this
        // variable links amountUsed and usedpercent to keep
        // the source code consistent here in the java version
        // where the two functions are basically fused
        // together. This is a hack on a hack. *sigh*
        int amountUsed = usedpercent;
        if (invalidData) {
            itemImageName = invalid_garage;
        } else if (amountUsed > 85  || amountUsed == -42) {
            itemImageName = garage_availability_low;
        } else if ((amountUsed <= 85 && amountUsed >= 70) || amountUsed == -58) {
            itemImageName = garage_availability_medium;
        } else if ((amountUsed < 70 && amountUsed >=0) || amountUsed == -57) {
            itemImageName = garage_availability_high;
        }

        if((used > capacity) || amountUsed == -1 || amountUsed == -2) {
            itemImageName = invalid_garage;
        }

        if (showPrice) {
            if (rates != null) {
                JSONArray  rateArray = rates.optJSONArray(RS_KEY);
                if (!(rateArray == null)) {
                    boolean isDynamicPricing = true;
                    int rsc = rateArray.length();
                    for (int i =0; i < rsc; i++) {
                        JSONObject rateObject = rateArray.getJSONObject(i);
                        float phr = (float)rateObject.optDouble(RATE_KEY, 0);
                        String description = rateObject.optString(DESC_KEY);
                        if (description != null) {
                            if (description.contains(INCREMENTAL_STR)) {
                                itemImageName = nameFinder(phr);
                                isDynamicPricing = false;
                                pricePerHour = phr;
                                break;
                            }
                        }
                    }

                    if (isDynamicPricing) {
                        for (int i =0; i < rsc; i++) {
                            JSONObject rateObject = rateArray.getJSONObject(i);
                            float phr = (float)rateObject.optDouble(RATE_KEY, 0);
                            rateStructureHandle(rateObject);
                            if (inThisBucketBegin(beg,end)) {
                                itemImageName = nameFinder(phr);
                                pricePerHour = phr;
                                break;
                            }
                        }
                    }
                }
            }
        }
        rateQualifier = rq;
        return itemImageName;
    }

    // Figure out which parking garage icon to display.
    public int nameFinder(float phr) {
        int imageName = -1;
        if (phr <= 2.00 && phr >= 0.00) {
            imageName = garage_price_low;
        } else if (phr > 2.00 && phr <= 4.00) {
            imageName = garage_price_medium;
        } else if (phr > 4.00) {
            imageName = garage_price_high;
        }
        return imageName;
    }


    // Determine if we are in the time period given by beg and end.
    // convert everything to minutes to avoid using slow Date calcs.
    public boolean inThisBucketBegin(String beginString, String endString) {
        if (beginString == null || endString == null) {
            return false;
        }

        // this is 95%!!!
        // String endString = endStr.replaceAll(REPLACE1_STR, REPLACE2_STR);
        // "(?i)12:00 AM", "11:59 PM" - (?i) is case insensitive

        if (endString.equals(REPLACE0_STR)) {
            endString = REPLACE2_STR;
        }

        String time1 = beginString.substring(0, beginString.length()-3);
        String[] time2 = time1.split(SPLIT2_STR);

        int hours = Integer.valueOf(time2[0]);
        // convert to 24 hour
        if (beginString.endsWith(AM_STR) && (hours == 12)) {
            hours = 0;
        }
        if (beginString.endsWith(PM_STR) && (hours != 12)) {
            hours = hours + 12;
        }

        int minutes = Integer.valueOf(time2[1]);
        int beginTimeMinutes = (hours * 60) + minutes;

        time1 = endString.substring(0, endString.length()-3);
        time2 = time1.split(SPLIT2_STR);

        hours = Integer.valueOf(time2[0]);
        // convert to 24 hour
        if (endString.endsWith(AM_STR) && (hours == 12)) {
            hours = 0;
        }
        if (endString.endsWith(PM_STR) && (hours != 12)) {
            hours = hours + 12;
        }

        minutes = Integer.valueOf(time2[1]);

        int endTimeMinutes = (hours * 60) + minutes;

        if ((MainScreenActivity.timeStampMinutes >= beginTimeMinutes)
            && (MainScreenActivity.timeStampMinutes < endTimeMinutes)) {
            return true;
        }

        return false;
    }

    // Determine which price bucket we are in.
    public int bucketFinder(float price) {
        int	lineColor;

        if (price <= 2.00 && price >= 0.00) {
            if (price == 0.00) {
                if (!rq.equals(NOCHARGE_STR)) {
                    lineColor = grey;
                } else {
                    lineColor = priceLow;
                }
                rateQualifier = rq;
            } else {
                lineColor = priceLow;
            }
            pricePerHour = price;
        } else if (price > 2.00 && price <= 4.00) {
            lineColor = priceMed;
            pricePerHour = price;
        } else if (price > 4.00) {
            lineColor = priceHigh;
            pricePerHour = price;
        } else {
            lineColor = grey;
            pricePerHour = price;
        }
        return lineColor;
    }

    // This method is used for the polylines color finding.
    public int blockfaceColorizerWithShowPrice(boolean showPrice) throws JSONException {
        double usedPercent = 0;
        int numberOfOperationalSpaces = 0;
        int occupied = 0;

        int lineColor;

        lineColor = priceLow;

        if(allGarageData.has(OPER_KEY)) {
            numberOfOperationalSpaces = allGarageData.optInt(OPER_KEY,0);
            if (allGarageData.has(OCC_KEY)) {
                occupied = allGarageData.optInt(OCC_KEY,0);
                if(numberOfOperationalSpaces == 0 && occupied == 0 && !showPrice) {
                    return red;
                }
                if(numberOfOperationalSpaces == 0) {
                    usedPercent = 0.0;
                } else {
                    usedPercent = (double) ((occupied * 1.0)/numberOfOperationalSpaces);
                }
            }
        } else {
            // OPER # wasn't returned, so we should paint the block
            // with low availability or grey as dictated by
            // price/availability mode.
            if (!showPrice || (occupied != 0 && numberOfOperationalSpaces != 0)) {
                if (!(allGarageData.has(OCC_KEY)) && !(allGarageData.has(OPER_KEY))) {
                    return grey;
                }
                return red;
            }
        }

        if (showPrice) {
            if (allGarageData.has(RATES_KEY)) {
                JSONObject rates = allGarageData.getJSONObject(RATES_KEY);
                // Get an optional JSONArray associated with a key. It
                // returns null if there is no such key, or if its
                // value is not a JSONArray.
                JSONArray  rateArray = rates.optJSONArray(RS_KEY);
                if (!(rateArray == null)) {
                    int rsc = rateArray.length();
                    for (int i = 0; i < rsc; i++) {
                        JSONObject rateObject = rateArray.getJSONObject(i);
                        rateStructureHandle(rateObject);
                        if (inThisBucketBegin(beg,end)) {
                            lineColor = bucketFinder(Float.parseFloat(rate));
                            break;
                        }
                    }
                } else {
                    JSONObject rateObject = rates.optJSONObject(RS_KEY);
                    if (!(rateObject == null)) {
                        rateStructureHandle(rateObject);
                        if (inThisBucketBegin(beg,end)) {
                            lineColor = bucketFinder(Float.parseFloat(rate));
                        }
                    } else {
                        Log.v(TAG, "Fail... rateStructure isn't a dictionary or array.");
                    }
                }
            } else {
                Log.v(TAG, "Fail... No rate information...");
            }
        } else {
            // color for availability...
            if (usedPercent >= 0.000000 && usedPercent < 0.70) {
                //low usage --> 0x2B/255.0
                lineColor = availHigh;
                //Handle the case where the number of free spaces is
                //less than two, but usedPercent is < 70%. Don't show
                //as high availability, only medium availability...
                //int availSpaces = numberOfOperationalSpaces - occupied;
                if(numberOfOperationalSpaces <= 2) {
                    lineColor = availMed;
                }
            } else if (usedPercent >= 0.70 && usedPercent <= 0.85) {
                //middle usage
                lineColor = availMed;
            } else if (usedPercent > 0.85) {
                //Full
                lineColor = availLow;
            } else {
                lineColor = grey;
            }
        }
        return lineColor;
    }

    // Update properties of start and end times for the current rate.
    public void rateStructureHandle(JSONObject rateObject) {
        // want null if fail not 'empty string' ?
        beg	 = rateObject.optString(BEG_STR,  null);
        end	 = rateObject.optString(END_STR,  null);
        rate = rateObject.optString(RATE_STR, null);
        rq	 = rateObject.optString(RQ_STR,	  null);
    }
}
