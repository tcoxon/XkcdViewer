package net.bytten.xkcdviewer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ArchiveActivity extends ListActivity {
    
    static protected enum LoadType { ARCHIVE, BOOKMARKS, SEARCH_TITLE };
    
    static Pattern archiveItemPattern = Pattern.compile(
            // group(1): comic number;   group(2): date;   group(3): title
            "\\s*<a href=\"/(\\d+)/\" title=\"(\\d+-\\d+-\\d+)\">([^<]+)</a><br/>\\s*");
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        loadContent();
    }
    
    /* Only call this from the UI thread */
    protected void failed(final String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ArchiveActivity.this);
        builder.setMessage(msg);
        AlertDialog alert = builder.create();
        alert.show();
    }
    
    class ArchiveAdapter extends ArrayAdapter<ArchiveItem> {
        
        public ArchiveAdapter(List<ArchiveItem> items) {
            super(ArchiveActivity.this, 0, items);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ArchiveItem item = getItem(position);
            if (convertView != null && convertView instanceof ArchiveItemView) {
                ((ArchiveItemView)convertView).setItem(item);
                return convertView;
            } else {
                return new ArchiveItemView(getContext(), item);
            }
        }
    }
    
    static class ArchiveItemView extends LinearLayout implements View.OnClickListener {
        protected ArchiveItem item = null;
        protected TextView tv = null;
        protected ImageView star = null;
        
        public ArchiveItemView(Context cxt, ArchiveItem item) {
            super(cxt);
            tv = new TextView(cxt);
            star = new ImageView(cxt);
            setOrientation(LinearLayout.HORIZONTAL);
            setOnClickListener(this);
            star.setClickable(true);
            addView(star);
            addView(tv);
            setItem(item);
        }
        public ArchiveItem getItem() { return item; }
        public void setItem(ArchiveItem itm) {
            item = itm;
            tv.setText(item.comicNumber + " - " + item.title);
            refreshStar();
            star.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (item.bookmarked) {
                        BookmarksHelper.removeBookmark(getContext(), item);
                    } else {
                        BookmarksHelper.addBookmark(getContext(), item);
                    }
                    item.bookmarked = !item.bookmarked;
                    refreshStar();
                }
            });
        }
        public void refreshStar() {
            if (item.bookmarked) {
                star.setBackgroundResource(android.R.drawable.btn_star_big_on);
            } else {
                star.setBackgroundResource(android.R.drawable.btn_star_big_off);
            }
        }
        public void onClick(View v) {
            Intent comic = new Intent();
            comic.addCategory(Intent.CATEGORY_BROWSABLE);
            comic.setData(Uri.parse("http://www.xkcd.com/"+item.comicNumber+"/"));
            comic.setClass(getContext(), XkcdViewerActivity.class);
            getContext().startActivity(comic);
            
        }
        
        
    }
    
    static class ArchiveItem {
        public boolean bookmarked = false;
        public String title, comicNumber;
    }

    /* Archive-loading implementation using AsyncTasks follows.
     * 
     * load*, show* methods must be called in UI thread
     * fetch* methods must be called in a background thread
     */
    
    private String getQuery(Intent i) {
        if (i != null && i.getData() != null &&
            i.getData().getQuery() != null)
        {
            return i.getData().getQuery();
        } else {
            return "";
        }
    }
    
    private LoadType getLoadType(String q) {
        if (q.equals("bookmarks")) {
            return LoadType.BOOKMARKS;
        } else if (q.length() >= 2 &&
            q.substring(0,2).equals("q="))
        {
            return LoadType.SEARCH_TITLE;
        } else {
            return LoadType.ARCHIVE;
        }
    }
    
    public void loadContent() {
        final Intent intent = getIntent();
        final String query = getQuery(intent);
        final LoadType loadType = getLoadType(query);
        
        new Utility.CancellableAsyncTaskWithProgressDialog<Object,
            List<ArchiveItem> >()
        {
            protected Throwable failReason = null;
            
            protected List<ArchiveItem> doInBackground(Object... params) {
                try {
                    return fetchContent(loadType, query);
                } catch (Throwable e) {
                    failReason = e;
                    return null;
                }
            }
            
            protected void onPostExecute(List<ArchiveItem> result) {
                super.onPostExecute(result);
                if (result != null) {
                    showResults(loadType, result);
                } else {
                    failReason.printStackTrace();
                    /* Pattern match against type of failReason */
                    try {
                        throw failReason;
                    } catch (MalformedURLException e) {
                        failed("Malformed URL: "+e);
                    } catch (IOException e) {
                        failed("IO error: "+e);
                    } catch (InterruptedException e) {
                        // Do nothing. Loading was cancelled.
                    } catch (Throwable e) {
                        failed(e.toString());
                    }
                }
            }
            
        }.start(this, "Loading archive...", new Object[]{null});
    }
    
    protected List<ArchiveItem> fetchContent(LoadType loadType, String query)
        throws Throwable
    {
        switch (loadType) {
        case BOOKMARKS:
            return fetchBookmarks();
        case SEARCH_TITLE:
            return fetchSearchByTitleResults(query.substring(2));
        default:
            return fetchArchive();
        }
    }
    
    protected void showResults(LoadType loadType, List<ArchiveItem> results) {
        switch (loadType) {
        case BOOKMARKS:
            setTitle(R.string.app_bookmarks_label);
            break;
        case SEARCH_TITLE:
            setTitle(R.string.app_search_title_label);
            break;
        default:
            setTitle(R.string.app_archive_label);
        }
        setListAdapter(new ArchiveAdapter(results));
    }
    
    protected List<ArchiveItem> fetchBookmarks() throws Throwable {
        return BookmarksHelper.getBookmarks(this);
    }
    
    protected List<ArchiveItem> fetchSearchByTitleResults(String titleQuery)
        throws Throwable
    {
        String[] titleWords = titleQuery.toLowerCase().split("\\s");
        List<ArchiveItem> items = fetchArchive();
        Utility.allowInterrupt();
        for (int i = 0; i < items.size(); i++) {
            String title = items.get(i).title.toLowerCase();
            for (String w: titleWords) {
                if (title.indexOf(w) == -1) {
                    items.remove(i);
                    i--;
                    break;
                }
            }
        }
        return items;
    }
    
    protected List<ArchiveItem> fetchArchive() throws Throwable {
        List<ArchiveItem> items = new ArrayList<ArchiveItem>();
        URL url = new URL("http://www.xkcd.com/archive/");
        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
        
        try {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = archiveItemPattern.matcher(line);
                while (m.find()) {
                    ArchiveItem item = new ArchiveItem();
                    item.comicNumber = m.group(1);
                    item.title = m.group(3);
                    if (BookmarksHelper.isBookmarked(ArchiveActivity.this, item))
                        item.bookmarked = true;
                    items.add(item);
                }

                Utility.allowInterrupt();
            }
        } finally {
            br.close();
        }
        return items;
    }
    
}
