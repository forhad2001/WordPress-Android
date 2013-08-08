package org.wordpress.android.ui.stats;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.widget.CursorAdapter;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.android.volley.VolleyError;
import com.wordpress.rest.RestRequest.ErrorListener;
import com.wordpress.rest.RestRequest.Listener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.datasets.StatsVideosTable;
import org.wordpress.android.models.StatsVideo;
import org.wordpress.android.models.StatsVideoSummary;
import org.wordpress.android.providers.StatsContentProvider;
import org.wordpress.android.ui.HorizontalTabView.TabListener;
import org.wordpress.android.util.StatUtils;

public class StatsVideoFragment extends StatsAbsListViewFragment  implements TabListener {
    
    private static final Uri STATS_VIDEOS_URI = StatsContentProvider.STATS_VIDEOS_URI;
    private static final String[] TITLES = new String[] { StatsTimeframe.TODAY.getLabel(), StatsTimeframe.YESTERDAY.getLabel(), "Summary" };
    
    public static final String TAG = StatsVideoFragment.class.getSimpleName();
    
    
    @Override
    public FragmentStatePagerAdapter getAdapter() {
        return new CustomPagerAdapter(getChildFragmentManager());
    }

    private class CustomPagerAdapter extends FragmentStatePagerAdapter {

        public CustomPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return getFragment(position);
            
        }

        @Override
        public int getCount() {
            return TITLES.length;
        }
        
        @Override
        public CharSequence getPageTitle(int position) {
            return TITLES[position]; 
        }

    }

    @Override
    protected Fragment getFragment(int position) {
        if (position < 2) {
            int entryLabelResId = R.string.stats_entry_video_plays;
            int totalsLabelResId = R.string.stats_totals_plays;
            int emptyLabelResId = R.string.stats_empty_video;
            StatsCursorFragment fragment = StatsCursorFragment.newInstance(STATS_VIDEOS_URI, entryLabelResId, totalsLabelResId, emptyLabelResId);
            fragment.setListAdapter(new CustomCursorAdapter(getActivity(), null));
            return fragment;
        } else {
            VideoSummaryFragment fragment = new VideoSummaryFragment();
            return fragment;
        }
    }
    
    public class CustomCursorAdapter extends CursorAdapter {

        public CustomCursorAdapter(Context context, Cursor c) {
            super(context, c, true);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            
            String entry = cursor.getString(cursor.getColumnIndex(StatsVideosTable.Columns.NAME));
            String url = cursor.getString(cursor.getColumnIndex(StatsVideosTable.Columns.URL));
            int total = cursor.getInt(cursor.getColumnIndex(StatsVideosTable.Columns.PLAYS));

            // entries
            TextView entryTextView = (TextView) view.findViewById(R.id.stats_list_cell_entry);
            if (url != null && url.length() > 0) {
                Spanned link = Html.fromHtml("<a href=\"" + url + "\">" + entry + "</a>");
                entryTextView.setText(link);
                entryTextView.setMovementMethod(LinkMovementMethod.getInstance());
            } else {
                entryTextView.setText(entry);
            }
            
            // totals
            TextView totalsTextView = (TextView) view.findViewById(R.id.stats_list_cell_total);
            totalsTextView.setText(total + "");
            
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup root) {
            LayoutInflater inflater = LayoutInflater.from(context);
            return inflater.inflate(R.layout.stats_list_cell, root, false);
        }

    }

    @Override
    public String getTitle() {
        return getString(R.string.stats_view_video_plays);
    }
    
    @Override
    public String[] getTabTitles() {
        return TITLES;
    }
    

    @Override
    public void refresh(final int position) {
        final String blogId = getCurrentBlogId();
        if (getCurrentBlogId() == null)
            return;
 
        if (position == 0 || position == 1)
            refreshVideoStats(position, blogId);
    }


    private void refreshVideoStats(final int position, final String blogId) {
        WordPress.restClient.getStatsVideoPlays(blogId, 
                new Listener() {
                    
                    @Override
                    public void onResponse(JSONObject response) {
                        new ParseJsonTask().execute(blogId, response, position);
                    }
                }, 
                new ErrorListener() {
                    
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("WordPress Stats", StatsVideoFragment.class.getSimpleName() + ": " + error.toString());
                    }
                });
    }
    
    private static class ParseJsonTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            String blogId = (String) params[0];
            JSONObject response = (JSONObject) params[1];
            // int position = (Integer) params[2];
            
            Context context = WordPress.getContext();
            
            if (response != null && response.has("result")) {
                try {
                    JSONArray results = response.getJSONArray("result");

                    int count = results.length();
                    for (int i = 0; i < count; i++ ) {
                        JSONObject result = results.getJSONObject(i);
                        StatsVideo stat = new StatsVideo(blogId, result);
                        ContentValues values = StatsVideosTable.getContentValues(stat);
                        context.getContentResolver().insert(STATS_VIDEOS_URI, values);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                
            }
            return null;
        }        
    }
    
    /**
     * Fragment used for video summary
     */
    public static class VideoSummaryFragment extends SherlockFragment {
        
        private TextView mHeader;
        private TextView mPlays;
        private TextView mImpressions;
        private TextView mPlaybackTotals;
        private TextView mPlaybackUnit;
        private TextView mBandwidth;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.stats_video_summary, container, false); 
            
            mHeader = (TextView) view.findViewById(R.id.stats_video_summary_header);
            mHeader.setText("");
            mPlays = (TextView) view.findViewById(R.id.stats_video_summary_plays_total);
            mImpressions = (TextView) view.findViewById(R.id.stats_video_summary_impressions_total);
            mPlaybackTotals = (TextView) view.findViewById(R.id.stats_video_summary_playback_length_total);
            mPlaybackUnit = (TextView) view.findViewById(R.id.stats_video_summary_playback_length_unit);
            mBandwidth = (TextView) view.findViewById(R.id.stats_video_summary_bandwidth_total);
            
            return view;
        }
        
        @Override
        public void onResume() {
            super.onResume();
            refreshSummary();
        }

        private void refreshSummary() {

            if (WordPress.getCurrentBlog() == null)
                return; 

            String blogId = String.valueOf(WordPress.getCurrentBlog());
            
            new AsyncTask<String, Void, StatsVideoSummary>() {

                @Override
                protected StatsVideoSummary doInBackground(String... params) {
                    final String blogId = params[0];
                    
                    StatsVideoSummary stats = StatUtils.getVideoSummary(blogId);
                    if (stats == null || StatUtils.isDayOld(stats.getDate())) {
                        refreshStatsFromServer(blogId);
                    }
                    
                    return stats;
                }
                
                protected void onPostExecute(StatsVideoSummary result) {
                    refreshSummaryViews(result);
                };
            }.execute(blogId);
        }


        private void refreshStatsFromServer(final String blogId) {
            WordPress.restClient.getStatsVideoSummary(blogId, 
                    new Listener() {
                        
                        @Override
                        public void onResponse(JSONObject response) {
                            StatUtils.saveVideoSummary(blogId, response);
                            if (getActivity() != null)
                                getActivity().runOnUiThread(new Runnable() {
                                    
                                    @Override
                                    public void run() {
                                        refreshSummary();
                                        
                                    }
                                });
                        }
                    }, 
                    new ErrorListener() {
                        
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO Auto-generated method stub
                            
                        }
                    });
        }
        
        protected void refreshSummaryViews(StatsVideoSummary result) {

            String header = "";
            int plays = 0;
            int impressions = 0;
            int playbackTotals = 0;
            String bandwidth = "0 MB";
            
            if (result != null) {
                plays = result.getPlays();
                impressions = result.getImpressions();
                playbackTotals = result.getMinutes();
                bandwidth = result.getBandwidth();
                header = String.format(getString(R.string.stats_video_summary_header), result.getTimeframe());
            }

            mHeader.setText(header);
            mPlays.setText(plays + "");
            mImpressions.setText(impressions + "");
            mPlaybackTotals.setText(playbackTotals + "");
            mBandwidth.setText(bandwidth);
        }

        
    }
}
