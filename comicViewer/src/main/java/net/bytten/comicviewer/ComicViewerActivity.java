/*
 *  ComicViewer - Android library for viewing comics with hover text.
 *  
 *  xkcdViewer - Android app to view xkcd comics with hover text
 *  Copyright (C) 2009-2014 Tom Coxon, Tyler Breisacher, David McCullough,
 *      Kristian Lundkvist, Ivan Vasiljević.
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package net.bytten.comicviewer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public abstract class ComicViewerActivity extends Activity {

    static class CouldntParseComicPage extends Exception {
        private static final long serialVersionUID = 1L;
    }

    public static final FrameLayout.LayoutParams ZOOM_PARAMS =
        new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);

    private HackedWebView webview;
    private TextView title;
    protected IComicInfo comicInfo;
    protected IComicDefinition comicDef;
    protected IComicProvider provider;
    private EditText comicIdSel;

    private View zoom = null;

    private ImageView bookmarkBtn = null;

    // Constants for showActivityForResult calls
    static final int PICK_ARCHIVE_ITEM = 0;

    // Prep the errors and the failedDialog so we can get a reference to it later.
    private String errors = "";
    private AlertDialog failedDialog;

    // Constants defining dialogs
    static final int DIALOG_SHOW_HOVER_TEXT=0;
    static final int DIALOG_SHOW_ABOUT=1;
    static final int DIALOG_SEARCH_BY_TITLE=2;
    static final int DIALOG_FAILED=3;

    protected abstract IComicDefinition makeComicDef();
    protected abstract Class<? extends ArchiveActivity> getArchiveActivityClass();
    protected abstract String getStringAppName();
    protected abstract String getStringAboutText();
    
    protected void resetContent() {
        comicDef = makeComicDef();
        provider = comicDef.getProvider();
        comicInfo = provider.createEmptyComicInfo();
        
        //Only hide the title bar if we're running an android less than Android 3.0
        if(VersionHacks.getSdkInt() < 11)
            requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.main);
        webview = (HackedWebView)findViewById(R.id.viewer);
        title = (TextView)findViewById(R.id.title);
        comicIdSel = (EditText)findViewById(R.id.comicIdSel);

        webview.requestFocus();
        zoom = webview.getZoomControls();

        webview.setClickable(true);
        webview.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (!"".equals(comicInfo.getAlt()))
                    showDialog(DIALOG_SHOW_HOVER_TEXT);
            }
        });

        title.setText(comicInfo.getTitle());

        comicIdSel.setText(comicInfo.getId());
        if (comicDef.idsAreNumbers())
            comicIdSel.setInputType(InputType.TYPE_CLASS_NUMBER);
        comicIdSel.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId,
                    KeyEvent event) {
                String text = comicIdSel.getText().toString();
                if (!text.equals("") &&
                    (actionId == EditorInfo.IME_ACTION_GO ||
                     (actionId == EditorInfo.IME_NULL &&
                        event.getKeyCode() == KeyEvent.KEYCODE_ENTER)))
                {
                    loadComic(createComicUri(text));
                    comicIdSel.setText("");
                    return true;
                }
                return false;
            }
        });
        comicIdSel.setOnFocusChangeListener(new OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                InputMethodManager imm = (InputMethodManager)getSystemService(
                    Context.INPUT_METHOD_SERVICE);
                if (hasFocus) {
                    comicIdSel.setText("");
                    imm.showSoftInput(comicIdSel, InputMethodManager.SHOW_IMPLICIT);
                } else {
                    imm.hideSoftInputFromWindow(comicIdSel.getWindowToken(), 0);
                }
            }
        });

        ((Button)findViewById(R.id.firstBtn)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goToFirst();
            }
        });

        ((Button)findViewById(R.id.prevBtn)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goToPrev();
            }
        });

        ((Button)findViewById(R.id.nextBtn)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goToNext();
            }
        });

        ((Button)findViewById(R.id.finalBtn)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goToFinal();
            }
        });

        ((ImageView)findViewById(R.id.randomBtn)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goToRandom();
            }
        });

        bookmarkBtn = (ImageView)findViewById(R.id.bookmarkBtn);
        bookmarkBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleBookmark();
            }
        });
        refreshBookmarkBtn();
    }

    public void refreshBookmarkBtn() {
        if (comicInfo != null && comicInfo.isBookmarked()) {
            bookmarkBtn.setBackgroundResource(android.R.drawable.btn_star_big_on);
        } else {
            bookmarkBtn.setBackgroundResource(android.R.drawable.btn_star_big_off);
        }
    }

    public void toggleBookmark() {
        if (comicInfo != null) {
            if (comicInfo.isBookmarked()) {
                BookmarksHelper.removeBookmark(this, comicInfo.getId());
            } else {
                BookmarksHelper.addBookmark(this, comicInfo.getId(), comicInfo.getTitle());
            }
            comicInfo.setBookmarked(!comicInfo.isBookmarked());
            refreshBookmarkBtn();
        }
    }

    public void resetZoomControlEnable() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean allowPinchZoom = prefs.getBoolean("useZoomControls",!VersionHacks.isIncredible()),
                      showZoomButtons = prefs.getBoolean("showZoomButtons", true);
        setZoomControlEnable(allowPinchZoom, showZoomButtons);
    }

    public boolean isReopenLastComic() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getBoolean("reopenLastComic",false);
    }

    public String getLastReadComic() throws NumberFormatException {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getString("lastComic", null);
    }

    public void setLastReadComic(String id) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastComic", id);
        editor.commit();
    }

    public void setZoomControlEnable(boolean allowPinchZoom, boolean showZoomButtons) {
        final ViewGroup zoomParent = (ViewGroup)webview.getParent().getParent();
        if (zoom.getParent() == zoomParent) zoomParent.removeView(zoom);
        webview.getSettings().setBuiltInZoomControls(allowPinchZoom);
        webview.setAllowZoomButtons(false);
        webview.setAllowPinchZoom(allowPinchZoom);
        if (showZoomButtons) {
            if (allowPinchZoom) {
                webview.setAllowZoomButtons(true);
            } else {
                zoomParent.addView(zoom, ZOOM_PARAMS);
                zoom.setVisibility(View.GONE);
            }
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resetContent();

        final Intent i = this.getIntent();
        // Decide if application is called from launcher or from other application
        // by using action. If you use category it can be null, that is the case
        // with gmail application and link from that application.
        if (Intent.ACTION_VIEW.equals(i.getAction())) {
            // Link to comic
            boolean tryArchive = true;
            if (comicDef.isComicUrl(i.getData())) {
                try {
                    loadComic(provider.comicDataUrlForUrl(i.getData()));
                    tryArchive = false;
                } catch (NumberFormatException e) {
                    // Fall through to trying the URL as an archive URL
                }
            }
            if (tryArchive) {
                if (comicDef.isArchiveUrl(i.getData())) {
                    showArchive();
                    this.finish();
                } else {
                    // it wasn't a link to comic or to the archive
                    // last ditch attempt: was it a link to the home page?
                    if (comicDef.isHomeUrl(i.getData())) {
                        goToFinal();
                    } else {
                        toast("This comic viewer can't display this content.");
                        this.finish();
                    }
                }
            }
        } else {
            // Started by application icon
            if (isReopenLastComic()) {
                try {
                    loadComic(createComicUri(getLastReadComic()));
                } catch (NumberFormatException e) {
                    goToFinal();
                }
            } else {
                goToFinal();
            }
        }
    }

    public void showArchive() {
        Intent i = new Intent(this, getArchiveActivityClass());
        i.setData(comicDef.getArchiveUrl());
        i.setAction(Intent.ACTION_VIEW);
        i.putExtra(getPackageName() + "LoadType", ArchiveActivity.LoadType.ARCHIVE);
        startActivityForResult(i, PICK_ARCHIVE_ITEM);
    }

    public void showBookmarks() {
        Intent i = new Intent(this, getArchiveActivityClass());
        i.setData(comicDef.getArchiveUrl());
        i.setAction(Intent.ACTION_VIEW);
        i.putExtra(getPackageName() + "LoadType", ArchiveActivity.LoadType.BOOKMARKS);
        startActivityForResult(i, PICK_ARCHIVE_ITEM);
    }

    @Override
    public void onResume() {
        super.onResume();
        resetZoomControlEnable();
    }

    @Override
    public void onConfigurationChanged(Configuration conf) {
        super.onConfigurationChanged(conf);
        // Overrode this so we can catch keyboardHidden|orientation conf changes
        // do nothing prevents activity destruction.
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void longToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        menu.findItem(R.id.MENU_AUTHOR_LINK)
            .setTitle(comicDef.getAuthorLinkText());
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.MENU_HOVER_TEXT).
                setVisible(comicDef.hasAltText());
        menu.findItem(R.id.MENU_COMIC_LINK).
                setVisible(comicInfo.getLink() != null);
        menu.findItem(R.id.MENU_EXPLAIN).
                setVisible(provider.getExplainUrl(comicInfo) != null);
        return true;
    }
    
    public void loadAuthorLink() {
        Intent browser = new Intent();
        browser.setAction(Intent.ACTION_VIEW);
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        browser.setData(comicDef.getAuthorLinkUrl());
        startActivity(browser);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
		if (itemId == R.id.MENU_HOVER_TEXT) {
			if (!"".equals(comicInfo.getAlt()))
                showDialog(DIALOG_SHOW_HOVER_TEXT);
			return true;
		} else if (itemId == R.id.MENU_COMIC_LINK) {
		    if (comicInfo.getLink() != null)
		        openComicLink();
		    return true;
		} else if (itemId == R.id.MENU_EXPLAIN) {
		    explain();
		    return true;
		} else if (itemId == R.id.MENU_REFRESH) {
			loadComic(createComicUri(comicInfo.getId()));
			return true;
		} else if (itemId == R.id.MENU_RANDOM) {
			goToRandom();
			return true;
		} else if (itemId == R.id.MENU_SHARE_LINK) {
			shareComicLink();
			return true;
		} else if (itemId == R.id.MENU_SHARE_IMAGE) {
			shareComicImage();
			return true;
		} else if (itemId == R.id.MENU_SETTINGS) {
			showSettings();
			return true;
		} else if (itemId == R.id.MENU_GO_TO_FINAL) {
			goToFinal();
			return true;
		} else if (itemId == R.id.MENU_GO_TO_NEXT) {
			goToNext();
			return true;
		} else if (itemId == R.id.MENU_GO_TO_PREV) {
			goToPrev();
			return true;
		} else if (itemId == R.id.MENU_GO_TO_FIRST) {
			goToFirst();
			return true;
		} else if (itemId == R.id.MENU_WEBSITE) {
			launchWebsite();
			return true;
		} else if (itemId == R.id.MENU_ARCHIVE) {
			showArchive();
			return true;
		} else if (itemId == R.id.MENU_AUTHOR_LINK) {
			loadAuthorLink();
			return true;
		} else if (itemId == R.id.MENU_DONATE) {
			donate();
			return true;
		} else if (itemId == R.id.MENU_ABOUT) {
			showDialog(DIALOG_SHOW_ABOUT);
			return true;
		} else if (itemId == R.id.MENU_BOOKMARKS) {
			showBookmarks();
			return true;
		} else if (itemId == R.id.MENU_SEARCH_TITLE) {
			showDialog(DIALOG_SEARCH_BY_TITLE);
			return true;
		}
        return false;
    }



    public String getVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            return "???";
        }
    }

    public void donate() {
        Intent browser = new Intent();
        browser.setAction(Intent.ACTION_VIEW);
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        browser.setData(comicDef.getDonateUrl());
        startActivity(browser);
    }

    public void launchWebsite() {
        Intent browser = new Intent();
        browser.setAction(Intent.ACTION_VIEW);
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        browser.setData(Uri.parse(comicInfo.getUrl()));
        startActivity(browser);
    }
    
    public void openComicLink() {
        Intent browser = new Intent();
        browser.setAction(Intent.ACTION_VIEW);
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        browser.setData(comicInfo.getLink());
        startActivity(browser);
    }

    public void showSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void shareComicLink() {
        Intent intent = new Intent(Intent.ACTION_SEND, null);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, getCurrentComicUrl());
        startActivity(Intent.createChooser(intent, "Share Link..."));
    }

    public String getCurrentComicUrl() {
        return comicInfo.getUrl();
    }
    
    public void shareComicImage() {
        if (comicInfo == null || comicInfo.getImage() == null) {
            toast("No image loaded.");
            return;
        }

        new Utility.CancellableAsyncTaskWithProgressDialog<Uri, File>(getStringAppName()) {
            Throwable e;

            @Override
            protected File doInBackground(Uri... params) {
                try {
                    File file = File.createTempFile(
                            comicDef.getComicTitleAbbrev()+"-attachment-",
                            params[0].getLastPathSegment());
                    Utility.blockingSaveFile(file, params[0]);
                    return file;

                } catch (InterruptedException ex) {
                    return null;
                } catch (Throwable ex) {
                    e = ex;
                    return null;
                }
            }

            @Override
            protected void onPostExecute(File result) {
                super.onPostExecute(result);
                if (result != null && e == null) {

                    try {
                        Uri uri = Uri.fromFile(result);
                        Intent intent = new Intent(Intent.ACTION_SEND, null);
                        intent.setType(Utility.getContentType(uri));
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        startActivity(Intent.createChooser(intent, "Share image..."));
                        return;
                    } catch (MalformedURLException ex) {
                        e = ex;
                    } catch (IOException ex) {
                        e = ex;
                    }
                }
                e.printStackTrace();
                failed("Couldn't save attachment: "+e);
            }

        }.start(this, "Saving image...", new Uri[]{comicInfo.getImage()});
    }

    public void failed(final String reason) {
        runOnUiThread(new Runnable() {
            public void run() {
                if(failedDialog != null && !failedDialog.isShowing()) {
                    if (!failedDialog.isShowing())
                        errors = "";
                }
                if (!errors.equals("")) errors += "\n\n";
                errors += reason;
                showDialog(DIALOG_FAILED);
            }
        });
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        // Set up variables for a dialog and a dialog builder. Only need one of each.
        Dialog dialog = null;
        AlertDialog.Builder builder = null;

        // Determine the type of dialog based on the integer passed. These are defined in constants
        // at the top of the class.
        switch(id){
        case DIALOG_SHOW_HOVER_TEXT:
            //Build and show the Hover Text dialog
            builder = new AlertDialog.Builder(ComicViewerActivity.this);
            builder.setMessage(comicInfo.getAlt());
            builder.setPositiveButton("Open Link...", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    openComicLink();
                }
            });
            builder.setNegativeButton("Close", null);
            dialog = builder.create();
            builder = null;
            break;
        case DIALOG_SHOW_ABOUT:
            //Build and show the About dialog
            builder = new AlertDialog.Builder(this);
            builder.setTitle(getStringAppName());
            builder.setIcon(android.R.drawable.ic_menu_info_details);
            builder.setNegativeButton(android.R.string.ok, null);
            builder.setNeutralButton("Donate", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    donate();
                }
            });
            View v = LayoutInflater.from(this).inflate(R.layout.about, null);
            TextView tv = (TextView)v.findViewById(R.id.aboutText);
            tv.setText(String.format(getStringAboutText(), getVersion()));
            builder.setView(v);
            dialog = builder.create();
            builder = null;
            v = null;
            tv = null;
            break;
        case DIALOG_SEARCH_BY_TITLE:
            //Build and show the Search By Title dialog
            builder = new AlertDialog.Builder(this);

            LayoutInflater inflater = (LayoutInflater)getSystemService(
                    LAYOUT_INFLATER_SERVICE);
            View layout = inflater.inflate(R.layout.search_dlg,
                    (ViewGroup)findViewById(R.id.search_dlg));

            final EditText input = (EditText)layout.findViewById(
                    R.id.search_dlg_edit_box);

            builder.setTitle("Search by Title");
            builder.setIcon(android.R.drawable.ic_menu_search);
            builder.setView(layout);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    String query = input.getText().toString();
                    Uri uri = comicDef.getArchiveUrl();
                    Intent i = new Intent(ComicViewerActivity.this, getArchiveActivityClass());
                    i.setAction(Intent.ACTION_VIEW);
                    i.setData(uri);
                    i.putExtra(getPackageName() + "LoadType", ArchiveActivity.LoadType.SEARCH_TITLE);
                    i.putExtra(getPackageName() + "query", query);
                    startActivityForResult(i, PICK_ARCHIVE_ITEM);
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            dialog = builder.create();
            builder = null;
            break;
        case DIALOG_FAILED:
            // Probably doesn't need its own builder, but because this is a special case
            // dialog I gave it one.
            AlertDialog.Builder adb = new AlertDialog.Builder(this);
            adb.setTitle("Error");
            adb.setIcon(android.R.drawable.ic_dialog_alert);

            adb.setNeutralButton(android.R.string.ok, null);

            //Set failedDialog to our dialog so we can dismiss
            //it manually
            failedDialog = adb.create();
            failedDialog.setMessage(errors);

            dialog = failedDialog;
            break;
        default:
            dialog = null;
        }

        return dialog;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        // Determine the type of dialog based on the integer passed. These are defined in constants
        // at the top of the class.
        switch(id){
        case DIALOG_SHOW_HOVER_TEXT:
            //Get an alertdialog so we can edit it.
            AlertDialog adh = (AlertDialog) dialog;
            adh.setMessage(comicInfo.getAlt());
            
            adh.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(
                    comicInfo.getLink() != null ? Button.VISIBLE : Button.GONE);
            
            break;
        case DIALOG_FAILED:
            //Get the alertdialog for the failedDialog
            AlertDialog adf = (AlertDialog) dialog;

            adf.setMessage(errors);
            //Set failedDialog to our dialog so we can dismiss
            //it manually
            failedDialog = adf;
            break;
        case DIALOG_SEARCH_BY_TITLE:
            // Clear the text box
            AlertDialog ads = (AlertDialog)dialog;
            ((EditText)ads.findViewById(R.id.search_dlg_edit_box))
                .setText("");
            break;
        default:
            break;
        }
        super.onPrepareDialog(id, dialog);
    }

    /* Comic-loading implementation using AsyncTasks and IComicProvider
     * interface follows.
     *
     * goTo* methods must be called in UI thread
     * load* methods must be called in UI thread
     * create* methods can be called anywhere
     * fetch* methods must be called in a background thread
     */

    public void goToFirst() {
        loadComic(createComicUri(provider.getFirstId()));
    }
    public void goToPrev() {
        loadComic(createComicUri(comicInfo.getPrevId()));
    }
    public void goToNext() {
        loadComic(createComicUri(comicInfo.getNextId()));
    }
    public void goToFinal() {
        loadComic(provider.getFinalComicUrl());
    }

    public Uri createComicUri(String id) {
        return provider.createComicUrl(id);
    }

    public void goToRandom() {
        /* Can't just choose a random number and go to the comic, because if
         * the user cancelled the comic loading at start, we won't know how
         * many comics there are! */
        new Utility.CancellableAsyncTaskWithProgressDialog<Object, Uri>(getStringAppName()) {

            @Override
            protected Uri doInBackground(Object... params) {
                try {
                    return fetchRandomUri();
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Uri result) {
                super.onPostExecute(result);
                if (result != null)
                    loadComic(result);
                else
                    toast("Failed to get random comic");
            }

       }.start(this, "Randomizing...", new Object[]{null});
    }

    public Uri fetchRandomUri() throws Exception {
        return provider.fetchRandomComicUrl();
    }

    private class ComicInfoOrError {
        public IComicInfo comicInfo = null;
        public Throwable e = null;
        public ComicInfoOrError(Throwable e) { this.e = e; }
        public ComicInfoOrError(IComicInfo d) { comicInfo = d; }
    }
    
    public void explain() {
        final IComicInfo comic = comicInfo;
        new AsyncTask<Object, Integer, Integer>() {

            @Override
            protected Integer doInBackground(Object... params) {
                try {
                    URL url = new URL(provider.getExplainUrl(comic).toString());
                    HttpURLConnection http = (HttpURLConnection)url.openConnection();
                    return http.getResponseCode();
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);
                if (result == null || result != 200) {
                    toast("This comic has no user-supplied explanation.");
                }
                
            }
        }.execute(new Object[]{});
        
        Intent browser = new Intent();
        browser.setAction(Intent.ACTION_VIEW);
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        browser.setData(provider.getExplainUrl(comic));
        startActivity(browser);
    }

    public void loadComic(final Uri uri) {

        new Utility.CancellableAsyncTaskWithProgressDialog<Object, ComicInfoOrError>(getStringAppName()) {

            @Override
            protected ComicInfoOrError doInBackground(Object... params) {
                try {
                    return new ComicInfoOrError(fetchComicInfo(uri));
                } catch (Throwable e) {
                    return new ComicInfoOrError(e);
                }
            }

            @Override
            protected void onPostExecute(ComicInfoOrError result) {
                super.onPostExecute(result);
                if (result.comicInfo != null) {
                    comicInfo = result.comicInfo;
                    title.setText(comicInfo.getTitle());
                    comicIdSel.setText(comicInfo.getId());
                    refreshBookmarkBtn();
                    setLastReadComic(comicInfo.getId());

                    loadComicImage(comicInfo.getImage());
                    
                    if (comicInfo.getLink() != null) {
                        longToast("This comic has a link or larger image attached.\n"+
                            "Tap the image and select 'Open Link' to see it.");
                    }
                } else {
                    result.e.printStackTrace();
                    /* Syntaxhack pattern match against type of result.e: */
                    try {
                        throw result.e;
                    } catch (MalformedURLException e) {
                        failed("Malformed URL: "+e);
                    } catch (FileNotFoundException e) {
                        // Comic doesn't exist. Probably went beyond the final
                        // or before the first.
                        toast("Comic doesn't exist");
                    } catch (IOException e) {
                        failed("IO error: "+e);
                    } catch (InterruptedException e) {
                        // Do nothing. Loading was cancelled.
                    } catch (JSONException e) {
                        failed("Data returned from website didn't match expected format");
                    } catch (Throwable e) {
                        failed(e.toString());
                    }
                }
            }

        }.start(this, "Loading comic...", new Object[]{null});
    }

    public IComicInfo fetchComicInfo(Uri uri) throws Exception {
        IComicInfo ci = provider.fetchComicInfo(uri);
        ci.setBookmarked(BookmarksHelper.isBookmarked(this, ci.getId()));
        return ci;
    }

    public void loadComicImage(Uri uri) {
        webview.clearView();
        final ProgressDialog pd = ProgressDialog.show(
                this, getStringAppName(),
                "Loading comic image...", false, true,
                new OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        webview.stopLoading();
                        webview.requestFocus();
                    }
                });
        pd.setProgress(0);
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pd.dismiss();
                webview.requestFocus();
            }
        });
        webview.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                pd.setProgress(newProgress * 100);
            }
        });
        if ("".equals(uri.toString())) {
            failed("Couldn't identify image in post");
            webview.loadUrl("about:blank");
        } else {
            webview.loadUrl(uri.toString());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == PICK_ARCHIVE_ITEM) {
            if(resultCode == RESULT_OK) {
                // This means an archive item was picked. Display it.
                loadComic(createComicUri(data.getStringExtra(getPackageName() + "comicId")));
            }
        }
    }
}
