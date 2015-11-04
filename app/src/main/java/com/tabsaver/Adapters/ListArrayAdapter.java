package com.tabsaver.Adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.tabsaver.Helpers.ParseAnalyticsFunctions;
import com.tabsaver.Helpers.SessionStorage;
import com.tabsaver.R;
import com.tabsaver._Screens.Active.BarDetail;
import com.tabsaver._Screens.Active.LoadingActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

public class ListArrayAdapter extends BaseAdapter {

    //Basic housekeeping
    Context context;
    LayoutInflater inflater;

    //Storing bar data
    ArrayList<HashMap<String, String>> barData;
    ArrayList<HashMap<String, String>> barDataBackupForSearchFiltering;

    //Sorting by day
    private String dayOfWeek;

    //Caching images
    private LruCache<String, Bitmap> mMemoryCache;

    final static long SHOULDUPDATE = 1000 * 60 * 60 * 24;

    //Storing and retrieving session information
    SessionStorage session;

    public ListArrayAdapter(Context context, ArrayList<HashMap<String, String>> barData, String dayOfWeek) {
        this.context = context;

        //Setup the session
        session = new SessionStorage(context);

        //Store our bar data
        this.barData = barData;
        barDataBackupForSearchFiltering = new ArrayList<>();
        barDataBackupForSearchFiltering.addAll(barData);

        this.dayOfWeek = dayOfWeek;

        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 2;

        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };

    }

    public View getView(final int position, View convertView, ViewGroup parent) {
        //Grab bar information
        HashMap<String, String> currentBar = barData.get(position);
        final String barName = currentBar.get("name");
        final String barId = currentBar.get("id");

        //Get our view
        inflater = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        View itemView = inflater.inflate(R.layout.list_item, parent, false); //TODO: What is this damn warning?

        // Determine Day of Week
        String dealsStr = getDealsString(currentBar);

        //setup formater for distance
        NumberFormat formatter = new DecimalFormat("#0.0");

        //Set the name
        ((TextView) itemView.findViewById(R.id.deal)).setText(barName);

        //Set the deals
        ((TextView) itemView.findViewById(R.id.deals)).setText(dealsStr);

        //Set the distance
        ((TextView) itemView.findViewById(R.id.distance)).setText(formatter.format(Double.valueOf(currentBar.get("distance"))) + " mi");

        //Now set the image for the bar
        loadBitmap(barId, ((ImageView) itemView.findViewById(R.id.bar_thumbnail)));

        //Set listener
        itemView.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                Intent i = new Intent(context, BarDetail.class);
                i.putExtra("BarId", barId);
                context.startActivity(i);

                //Update bar analytics for clickthrough
                ParseAnalyticsFunctions.incrementBarClickThrough(barId);
            }
        });

        return itemView;
    }

    public void loadBitmap(String barId, ImageView barImage) {

        Bitmap bitmap = getBitmapFromMemCache(barId+"");

        if (bitmap != null) {
            barImage.setImageBitmap(bitmap);
        } else {
            bitmap = getImage(barId+"");

            if ( bitmap != null ) {
                barImage.setImageBitmap(bitmap);
            }

            addBitmapToMemoryCache(barId + "", bitmap);
        }
    }

    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null && bitmap != null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    public Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public Bitmap getImage(final String barId) {
        //Setup to read the file
        String imageFilePath = context.getFilesDir() + "/" + barId;
        File imageFile = new File( imageFilePath );
        int size = (int) imageFile.length();
        byte[] bytesForImageFile = new byte[size];

        //Set our bitmap
        Bitmap bitmap = null;

        //If the file exists
        if ( size != 0 ) {
            //Try and read it in
            try {
                BufferedInputStream buf = new BufferedInputStream(new FileInputStream(imageFile));
                buf.read(bytesForImageFile, 0, bytesForImageFile.length);
                buf.close();
            } catch (IOException e) {
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            //Setting up the image to create a bitmap of an appropriate size
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytesForImageFile, 0, bytesForImageFile.length, options);

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, 150, 150);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            bitmap = BitmapFactory.decodeByteArray(bytesForImageFile, 0, bytesForImageFile.length, options);

            return bitmap;
        } else {
            return null;
        }
    }

    //Determines which day's deals apply (Today or yesterday - i.e, the bar hasn't closed from last night)
    private int determineDayOfWeekForBar(HashMap<String, String> currentBar){
        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        int curTime = calendar.get(Calendar.HOUR_OF_DAY);
        int prevDay = day;

        //Look at the previous day
        if ( day == 1 ) {
            prevDay = 7;
        } else {
            prevDay = day - 1;
        }

        //Get yesterdays hours
        String[] prevDayHours = getHoursForBar(currentBar, prevDay).split("-");

        //Closed bars situation
        if ( prevDayHours[0].equals("Closed") ) {
            return day;
        }

        //Parse the close time into an integer
        String closeTimeString = prevDayHours[1];
        int closeTime;

        //If they didn't close at night, they could be open today
        if ( !closeTimeString.contains("PM") ) {
            if ( closeTimeString.contains(":")) {
                closeTime = Integer.valueOf(closeTimeString.replace("AM", "").substring(0, closeTimeString.indexOf(':')));
            } else {
                closeTime = Integer.valueOf(closeTimeString.replace("AM", ""));
            }
        } else {
            //They closed last night so the day we should consider for deals is the current one
            return day;
        }

        if ( curTime < closeTime ) {
            return prevDay;
        } else {
            return day;
        }
    }

    //Grabs the hours for the given day int
    private String getHoursForBar(HashMap<String, String> currentBar, int day){

        try {
            JSONObject hours = new JSONObject(currentBar.get("hours"));

            switch(day) {
                case Calendar.SUNDAY:
                    return hours.getString("Sunday");
                case Calendar.MONDAY:
                    return hours.getString("Monday");
                case Calendar.TUESDAY:
                    return hours.getString("Tuesday");
                case Calendar.WEDNESDAY:
                    return hours.getString("Wednesday");
                case Calendar.THURSDAY:
                    return hours.getString("Thursday");
                case Calendar.FRIDAY:
                    return hours.getString("Friday");
                case Calendar.SATURDAY:
                    return hours.getString("Saturday");
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return "";
    }

    /**
     * Determine the deals for the day
     * @return the string representation of the day of the week
     */
    private String getDealsString(HashMap<String, String> currentBar) {

        int day;

        if ( dayOfWeek.equals("Sunday") ) {
            day = 1;
        } else if ( dayOfWeek.equals("Monday") ) {
            day = 2;
        } else if ( dayOfWeek.equals("Tuesday") ) {
            day = 3;
        } else if ( dayOfWeek.equals("Wednesday") ) {
            day = 4;
        } else if ( dayOfWeek.equals("Thursday") ) {
            day = 5;
        } else if ( dayOfWeek.equals("Friday") ) {
            day = 6;
        } else if ( dayOfWeek.equals("Saturday") ) {
            day = 7;
        } else {
            day = determineDayOfWeekForBar(currentBar);
        }

        try {
            JSONObject deals = new JSONObject(currentBar.get("deals"));
            JSONArray daysDeals;
            String dealsResult = "";

            switch (day) {
                case Calendar.SUNDAY:
                    daysDeals = deals.getJSONArray("Sunday");
                    for(int i = 0; i < daysDeals.length(); i++) {
                        dealsResult = dealsResult + daysDeals.getString(i);

                        //Add comas except at the end
                        if (i != daysDeals.length() - 1) {
                            dealsResult = dealsResult + ", ";
                        }
                    }
                    break;
                case Calendar.MONDAY:
                    daysDeals = deals.getJSONArray("Monday");
                    for(int i = 0; i < daysDeals.length(); i++){
                        dealsResult = dealsResult + daysDeals.getString(i);

                        //Add comas except at the end
                        if ( i != daysDeals.length() - 1  ) {
                            dealsResult = dealsResult + ", ";
                        }
                    }
                    break;
                case Calendar.TUESDAY:
                    daysDeals = deals.getJSONArray("Tuesday");
                    for(int i = 0; i < daysDeals.length(); i++){
                        dealsResult = dealsResult + daysDeals.getString(i);

                        //Add comas except at the end
                        if ( i != daysDeals.length() - 1  ) {
                            dealsResult = dealsResult + ", ";
                        }
                    }
                    break;
                case Calendar.WEDNESDAY:
                    daysDeals = deals.getJSONArray("Wednesday");
                    for(int i = 0; i < daysDeals.length(); i++){
                        dealsResult = dealsResult + daysDeals.getString(i);

                        //Add comas except at the end
                        if ( i != daysDeals.length() - 1  ) {
                            dealsResult = dealsResult + ", ";
                        }
                    }
                    break;
                case Calendar.THURSDAY:
                    daysDeals = deals.getJSONArray("Thursday");
                    for(int i = 0; i < daysDeals.length(); i++){
                        dealsResult = dealsResult + daysDeals.getString(i);

                        //Add comas except at the end
                        if ( i != daysDeals.length() - 1  ) {
                            dealsResult = dealsResult + ", ";
                        }
                    }
                    break;
                case Calendar.FRIDAY:
                    daysDeals = deals.getJSONArray("Friday");
                    for(int i = 0; i < daysDeals.length(); i++){
                        dealsResult = dealsResult + daysDeals.getString(i);

                        //Add comas except at the end
                        if ( i != daysDeals.length() - 1  ) {
                            dealsResult = dealsResult + ", ";
                        }
                    }
                    break;
                case Calendar.SATURDAY:
                    daysDeals = deals.getJSONArray("Saturday");
                    for(int i = 0; i < daysDeals.length(); i++){
                        dealsResult = dealsResult + daysDeals.getString(i);

                        //Add comas except at the end
                        if ( i != daysDeals.length() - 1  ) {
                            dealsResult = dealsResult + ", ";
                        }
                    }
                    break;
            }

            return dealsResult;
        } catch (JSONException e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        return "";
    }

    /**
     * Function for filtering the data in the list
     * @param text The input text to filter
     */
    public void filter(String text) {
        text = text.toLowerCase();
        barData.clear();
        if (text.length() == 0) {
            barData.addAll(barDataBackupForSearchFiltering);
        }
        else {
            if ( text.length() > 3){
                ParseAnalyticsFunctions.saveSearchTerm("List View", text, context);
            }

            for (HashMap<String, String> bar : barDataBackupForSearchFiltering)
            {

                //If the deal or bar name contains the search term
                if ( (bar.get("name").toLowerCase().contains(text) || getDealsString(bar).toLowerCase().contains(text) ) && Double.valueOf(bar.get("distance")) <= session.getDistancePreference())
                {
                    barData.add(bar);
                }
            }
        }
        notifyDataSetChanged();
    }


    @Override
    public int getCount() {
        return barData.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }
}