package net.bytten.comicviewer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import net.bytten.comicviewer.ArchiveData.ArchiveItem;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public abstract class ArchiveActivity extends ListActivity {
    static public enum LoadType { ARCHIVE, BOOKMARKS, SEARCH_TITLE };

    private List<ArchiveItem> archiveItems;
    
    protected String query = null;
    protected LoadType loadtype = LoadType.ARCHIVE;
    protected IComicDefinition comicDef;
    protected ArchiveData archive;

    protected abstract IComicDefinition makeComicDef();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        comicDef = makeComicDef();
        archive = ArchiveData.getArchive(comicDef);

        final Intent intent = getIntent();
        query = intent.getStringExtra(getPackageName() + "query");
        loadtype = (LoadType) intent.getSerializableExtra(getPackageName() + "LoadType");

        resetContent();
    }

    protected void resetContent() {
        new Utility.CancellableAsyncTaskWithProgressDialog<Object,
        List<ArchiveItem> >(getTitle().toString())
        {
            protected Throwable failReason = null;

            @Override
            protected List<ArchiveItem> doInBackground(Object... params) {
                try {
                    return fetchContent(loadtype, query);
                } catch (Throwable e) {
                    failReason = e;
                    return null;
                }
            }

            @Override
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

        getListView().setFastScrollEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.archive, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
		if (itemId == R.id.archive_refresh) {
			refresh();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
    }

    public void refresh() {
        new Utility.CancellableAsyncTaskWithProgressDialog<Object,
            Boolean>(getTitle().toString())
        {
            @Override
            protected Boolean doInBackground(Object... params) {
                try {
                    archive.refresh(ArchiveActivity.this);
                    return true;
                } catch (Throwable e) {
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean result) {
                super.onPostExecute(result);
                if (!result) {
                    Toast.makeText(ArchiveActivity.this,
                        "Failed to refresh archive cache",
                        Toast.LENGTH_SHORT).show();
                } else {
                    resetContent();
                }
            }

        }.start(this, "Loading archive...", new Object[]{null});
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        Intent comic = new Intent();
        comic.addCategory(Intent.CATEGORY_BROWSABLE);
        comic.putExtra(getPackageName()+ "comicId", archiveItems.get(position).comicId);

        setResult(RESULT_OK, comic);
        finish();
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
                                BookmarksHelper.removeBookmark(getContext(), i.comicId);
                            } else {
                                BookmarksHelper.addBookmark(getContext(), i.comicId, i.title);
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
        archiveItems = archive.getData(this);
        return archiveItems;
    }

    protected List<ArchiveItem> fetchBookmarks() throws Throwable {
        archiveItems = BookmarksHelper.getBookmarks(this);
        return archiveItems;
    }

    protected List<ArchiveItem> fetchSearchByTitleResults(String titleQuery) throws Throwable {
        String[] titleWords = titleQuery.toLowerCase().split("\\s");
        List<ArchiveItem> rawItems = fetchArchive();
        archiveItems = new ArrayList<ArchiveItem>();
        Utility.allowInterrupt();
        for (int i = 0; i < rawItems.size(); i++) {
            String title = rawItems.get(i).title.toLowerCase();
            boolean titleMatches = true;
            for (String w: titleWords) {
                if (title.indexOf(w) == -1) {
                    titleMatches = false;
                    break;
                }
            }
            if (titleMatches)
                archiveItems.add(rawItems.get(i));
        }
        return archiveItems;
    }
    
    protected abstract String getStringBookmarks();
    protected abstract String getStringSearchByTitle();
    protected abstract String getStringArchive();

    protected void showResults(LoadType loadType, List<ArchiveItem> results) {
        switch (loadType) {
        case BOOKMARKS:
            setTitle(getStringBookmarks());
            break;
        case SEARCH_TITLE:
            setTitle(getStringSearchByTitle());
            break;
        default:
            setTitle(getStringArchive());
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
