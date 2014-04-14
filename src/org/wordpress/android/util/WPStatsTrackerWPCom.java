package org.wordpress.android.util;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONObject;
import org.wordpress.android.Constants;
import org.wordpress.android.WordPress;

public class WPStatsTrackerWPCom implements WPStats.Tracker {
    @Override
    public void track(WPStats.Stat stat) {
        track(stat, null);
    }

    @Override
    public void track(WPStats.Stat stat, JSONObject properties) {
        switch (stat) {
            case READER_LOADED_FRESHLY_PRESSED:
                pingWPComStatsEndpoint("freshly");
                break;
            case READER_OPENED_ARTICLE:
                pingWPComStatsEndpoint("details_page");
                break;
            case READER_ACCESSED:
                pingWPComStatsEndpoint("home_page");
                break;
        }
    }

    @Override
    public void beginSession() {
        // No-op
    }

    @Override
    public void endSession() {
        // No-op
    }

    @Override
    public void clearAllData() {
        // No-op
    }

    private void pingWPComStatsEndpoint(String statName) {

        Response.Listener<String> listener = new Response.Listener<String>() {
            public void onResponse(String response) {
            }
        };
        Response.ErrorListener errorListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                String errMsg = String.format("Error pinging WPCom Stats: %s", volleyError.getMessage());
                AppLog.w(AppLog.T.STATS, errMsg);
            }
        };

        int rnd = (int)(Math.random() * 100000);
        String statsURL = String.format("%s%s%s%s%d", Constants.readerURL_v3, "&template=stats&stats_name=", statName, "&rnd=", rnd );
        StringRequest req = new StringRequest(Request.Method.GET, statsURL, listener, errorListener);
        WordPress.requestQueue.add(req);
    }
}