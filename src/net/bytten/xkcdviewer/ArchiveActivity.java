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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

public class ArchiveActivity extends ListActivity {
    static public enum LoadType { ARCHIVE, BOOKMARKS, SEARCH_TITLE };
    
    static Pattern archiveItemPattern = Pattern.compile(
            // group(1): comic number;   group(2): date;   group(3): title
            "\\s*<a href=\"/(\\d+)/\" title=\"(\\d+-\\d+-\\d+)\">([^<]+)</a><br/>\\s*");
    
    private List<ArchiveItem> archiveItems;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        
        final Intent intent = getIntent();
        final String query = intent.getStringExtra(getPackageName() + "query");
        final LoadType loadtype = (LoadType) intent.getSerializableExtra(getPackageName() + "LoadType");
        
        
        new Utility.CancellableAsyncTaskWithProgressDialog<Object,
        List<ArchiveItem> >()
        {
            protected Throwable failReason = null;
            
            protected List<ArchiveItem> doInBackground(Object... params) {
                try {
                    return fetchContent(loadtype, query);
                } catch (Throwable e) {
                    failReason = e;
                    return null;
                }
            }
            
            protected void onPostExecute(List<ArchiveItem> result) {
                super.onPostExecute(result);
                if (result != null) {
                    showResults(loadtype, result);
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

        super.onCreate(savedInstanceState);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        
        Intent comic = new Intent();
        comic.addCategory(Intent.CATEGORY_BROWSABLE);
        comic.putExtra(getPackageName()+ "comicNumber", archiveItems.get(position).comicNumber);
        
        setResult(RESULT_OK, comic);
        finish();
    }
    
    static class ArchiveItem {
        public boolean bookmarked = false;
        public String title, comicNumber;
        
        @Override
        public String toString() {
            return  comicNumber + " - " + title;
        }
    }
    
    private class ArchiveAdapter extends ArrayAdapter<ArchiveItem> {
        
        public ArchiveAdapter(Context context, int textViewResourceId, List<ArchiveItem> results) {
            super(context, textViewResourceId, results);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if(v == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.archive_item_layout, null);
            }
            
            final ArchiveItem i = archiveItems.get(position);
            if(i != null) {
                CheckBox cb = (CheckBox) v.findViewById(R.id.archive_item_bookmarked);
                TextView tv = (TextView) v.findViewById(R.id.archive_item_textView);
                
                if(cb != null) {
                    cb.setChecked(i.bookmarked);
                    cb.setFocusable(false);
                    cb.setFocusableInTouchMode(false);
                    cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (!isChecked) {
                                BookmarksHelper.removeBookmark(getContext(), i.comicNumber);
                            } else {
                                BookmarksHelper.addBookmark(getContext(), i.comicNumber, i.title);
                            }
                            i.bookmarked = !i.bookmarked;                            
                        }
                    });
                }
                if(tv != null) {
                    tv.setText(i.toString());
                }
            }
            
            v.setEnabled(true);
            return v;
        }
    }
    
    protected List<ArchiveItem> fetchContent(LoadType loadtype, String query) throws Throwable {
        switch (loadtype) {
        case BOOKMARKS:
            return fetchBookmarks();
        case SEARCH_TITLE:
            return fetchSearchByTitleResults(query);
        default:
            return fetchArchive();
        }
    }
    
    protected List<ArchiveItem> fetchArchive() throws Throwable {
        archiveItems = new ArrayList<ArchiveItem>();
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
                    archiveItems.add(item);
                }

                Utility.allowInterrupt();
            }
        } finally {
            br.close();
        }
        return archiveItems;
    }
    
    protected List<ArchiveItem> fetchBookmarks() throws Throwable {
        archiveItems = BookmarksHelper.getBookmarks(this);
        return archiveItems;
    }
    
    protected List<ArchiveItem> fetchSearchByTitleResults(String titleQuery) throws Throwable {
        String[] titleWords = titleQuery.toLowerCase().split("\\s");
        archiveItems = fetchArchive();
        Utility.allowInterrupt();
        for (int i = 0; i < archiveItems.size(); i++) {
            String title = archiveItems.get(i).title.toLowerCase();
            for (String w: titleWords) {
                if (title.indexOf(w) == -1) {
                    archiveItems.remove(i);
                    i--;
                    break;
                }
            }
        }
        return archiveItems;
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
        setListAdapter(new ArchiveAdapter(this, R.layout.archive_item_layout, results));
    }

    /* Only call this from the UI thread */
    protected void failed(final String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ArchiveActivity.this);
        builder.setMessage(msg);
        AlertDialog alert = builder.create();
        alert.show();
    }
    
}
