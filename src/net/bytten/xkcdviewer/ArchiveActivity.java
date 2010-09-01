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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ArchiveActivity extends ListActivity {
    
    static protected enum LoadType { ARCHIVE, BOOKMARKS, SEARCH_TITLE };
    
    static Pattern archiveItemPattern = Pattern.compile(
            // group(1): comic number;   group(2): date;   group(3): title
            "\\s*<a href=\"/(\\d+)/\" title=\"(\\d+-\\d+-\\d+)\">([^<]+)</a><br/>\\s*");
    
    public Handler handler = new Handler();
    
    protected LoadType loadType = LoadType.ARCHIVE;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        resetContent();
    }
    
    public void resetContent() {
        final Thread[] loadThread = {null};
        final List<ArchiveItem> items = new ArrayList<ArchiveItem>();
        final Intent intent = getIntent();

        final ProgressDialog pd = ProgressDialog.show(this,
                "XkcdViewer", "Loading archive...", true, true,
                new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                if (loadThread[0] != null) {
                    // tell loading to stop
                    loadThread[0].interrupt();
                }
            }
        });

        loadThread[0] = new Thread(new Runnable() {
            public void run() {
                try {
                    String query = "";
                    if (intent.getData() != null &&
                        intent.getData().getQuery() != null)
                    {
                        query = intent.getData().getQuery();
                    }
                    
                    if (query.equals("bookmarks")) {
                        loadType = LoadType.BOOKMARKS;
                        loadBookmarks(items);
                    } else if (query.length() >= 2 &&
                        query.substring(0, 2).equals("q="))
                    {
                        loadType = LoadType.SEARCH_TITLE;
                        loadSearchTitle(items, query.substring(2));
                    } else {
                        loadArchive(items);
                    }
                    
                    handler.post(new Runnable() {
                        public void run() {
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
                            setListAdapter(new ArchiveAdapter(items));
                        }
                    });
                    
                } catch (MalformedURLException e) {
                    failed("Malformed URL: "+e);
                    e.printStackTrace();
                } catch (IOException e) {
                    failed("IO error: "+e);
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    // Do nothing. Loading was cancelled.
                } catch (Throwable e) {
                    failed(e.toString());
                    e.printStackTrace();
                } finally {
                    handler.post(new Runnable() {
                        public void run() {
                            pd.dismiss();
                        }
                    });
                }   
            }
        });
        loadThread[0].start();
    }

    protected void loadBookmarks(List<ArchiveItem> items) throws Throwable {
        items.addAll(BookmarksHelper.getBookmarks(this));
    }
    
    protected void loadSearchTitle(List<ArchiveItem> items, String titleQuery) throws Throwable {
        String[] titleWords = titleQuery.toLowerCase().split("\\s");
        loadArchive(items);
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
    }
    
    protected void loadArchive(List<ArchiveItem> items) throws Throwable {
        URL url = new URL("http://www.xkcd.com/archive/");
        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
        
        String line;
        while ((line = br.readLine()) != null) {
            Thread.sleep(0); // allow space for interruption
            
            Matcher m = archiveItemPattern.matcher(line);
            while (m.find()) {
                ArchiveItem item = new ArchiveItem();
                item.comicNumber = m.group(1);
                item.title = m.group(3);
                if (BookmarksHelper.isBookmarked(ArchiveActivity.this, item))
                    item.bookmarked = true;
                items.add(item);
            }
        }
        br.close();
    }
    
    protected void failed(final String msg) {
        handler.post(new Runnable() {
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(ArchiveActivity.this);
                builder.setMessage(msg);
                AlertDialog alert = builder.create();
                alert.show();
            }
        });
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
}
