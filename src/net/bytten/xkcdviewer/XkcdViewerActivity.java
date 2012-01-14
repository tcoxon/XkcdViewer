/*
 *  xkcdViewer - Android app to view xkcd comics with hover text
 *  Copyright (C) 2009-2010 Tom Coxon, Tyler Breisacher, David McCullough,
 *      Kristian Lundkvist.
 *  xkcd belongs to Randall Munroe.
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
package net.bytten.xkcdviewer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.net.Uri;
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
import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

public class XkcdViewerActivity extends Activity {

    static class CouldntParseComicPage extends Exception {
        private static final long serialVersionUID = 1L;
    }

    static private Pattern
                   xkcdHomePattern = Pattern.compile(
                       "http://(www\\.)?xkcd\\.com(/)?"),
                   comicUrlPattern = Pattern.compile(
                       "http://(www\\.)?xkcd\\.com/([0-9]+)(/)?"),
                   archiveUrlPattern = Pattern.compile(
                       "http://(www\\.)?xkcd\\.com/archive(/)?");
    
    public static final String DONATE_URL = "https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=C9JRVA3NTULSL&lc=US&item_name=XkcdViewer%20donation&item_number=xkcdviewer&currency_code=USD";
    public static final String XKCD_ARCHIVE_STRING = "http://xkcd.com/archive/";
    
    public static final FrameLayout.LayoutParams ZOOM_PARAMS =
        new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);

    public static final String PACKAGE_NAME = "net.bytten.xkcdviewer";
    

    private HackedWebView webview;
    private TextView title;
    private IComicInfo comicInfo = new XkcdComicInfo();
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
    
    protected void resetContent() {
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
                showDialog(DIALOG_SHOW_HOVER_TEXT);
            }
        });

        title.setText(comicInfo.getTitle());

        comicIdSel.setText(comicInfo.getId());
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
                    try {
                        loadComic(createComicUri(text));
                        comicIdSel.setText("");
                    } catch (NumberFormatException e) {
                        toast("Enter a number");
                    }
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
        if (i.hasCategory(Intent.CATEGORY_BROWSABLE)) {
            // Link to comic
            boolean tryArchive = true;
            Matcher m = comicUrlPattern.matcher(i.getDataString());
            if (m.matches()) {
                try {
                    loadComic(createComicUri(m.group(2)));
                    tryArchive = false;
                } catch (NumberFormatException e) {
                    // Fall through to trying the URL as an archive URL
                }
            }
            if (tryArchive) {
                m = archiveUrlPattern.matcher(i.getDataString());
                if (m.matches()) {
                    showArchive();
                    this.finish();
                } else {
                    // it wasn't a link to comic or to the archive
                    m = xkcdHomePattern.matcher(i.getDataString());
                    // last ditch attempt: was it a link to the home page?
                    if (m.matches()) {
                        goToFinal();
                    } else {
                        toast("xkcdViewer can't display this content.");
                        this.finish();
                    }
                }
            }
        } else {
            // Started by xkcdViewer icon
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
        Intent i = new Intent(this, ArchiveActivity.class);
        i.setData(Uri.parse(XKCD_ARCHIVE_STRING));
        i.setAction(Intent.ACTION_VIEW);
        i.putExtra(getPackageName() + "LoadType", ArchiveActivity.LoadType.ARCHIVE);
        startActivityForResult(i, PICK_ARCHIVE_ITEM);
    }
    
    public void showBookmarks() {
        Intent i = new Intent(this, ArchiveActivity.class);
        i.setData(Uri.parse(XKCD_ARCHIVE_STRING));
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
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.MENU_HOVER_TEXT:
            showDialog(DIALOG_SHOW_HOVER_TEXT);
            return true;
        case R.id.MENU_REFRESH:
            loadComic(createComicUri(comicInfo.getId()));
            return true;
        case R.id.MENU_RANDOM:
            goToRandom();
            return true;
        case R.id.MENU_SHARE_LINK:
            shareComicLink();
            return true;
        case R.id.MENU_SHARE_IMAGE:
            shareComicImage();
            return true;
        case R.id.MENU_SETTINGS:
            showSettings();
            return true;
        case R.id.MENU_GO_TO_FINAL:
            goToFinal();
            return true;
        case R.id.MENU_GO_TO_NEXT:
            goToNext();
            return true;
        case R.id.MENU_GO_TO_PREV:
            goToPrev();
            return true;
        case R.id.MENU_GO_TO_FIRST:
            goToFirst();
            return true;
        case R.id.MENU_ARCHIVE:
            showArchive();
            return true;
        case R.id.MENU_DONATE:
            donate();
            return true;
        case R.id.MENU_ABOUT:
            showDialog(DIALOG_SHOW_ABOUT);
            return true;
        case R.id.MENU_BOOKMARKS:
            showBookmarks();
            return true;
        case R.id.MENU_SEARCH_TITLE:
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
        browser.setData(Uri.parse(DONATE_URL));
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
        
        new Utility.CancellableAsyncTaskWithProgressDialog<Uri, File>() {
            Throwable e;
            
            protected File doInBackground(Uri... params) {
                try {
                    
                    File file = File.createTempFile("xkcd-attachment-", ".png");
                    Utility.blockingSaveFile(file, params[0]);
                    return file;
                    
                } catch (InterruptedException ex) {
                    return null;
                } catch (Throwable ex) {
                    e = ex;
                    return null;
                }
            }
            
            protected void onPostExecute(File result) {
                super.onPostExecute(result);
                if (result != null) {
                    
                    Uri uri = Uri.fromFile(result);
                    Intent intent = new Intent(Intent.ACTION_SEND, null);
                    intent.setType("image/png");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    startActivity(Intent.createChooser(intent, "Share image..."));
                    
                } else if (e != null) {
                    e.printStackTrace();
                    failed("Couldn't save attachment: "+e);
                }
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
            builder = new AlertDialog.Builder(XkcdViewerActivity.this);
            builder.setMessage(comicInfo.getAlt());
            builder.setNeutralButton("Close", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            dialog = builder.create();
            builder = null;
            break;
        case DIALOG_SHOW_ABOUT:
            //Build and show the About dialog
            builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.app_name);
            builder.setIcon(android.R.drawable.ic_menu_info_details);
            builder.setNegativeButton(android.R.string.ok, null);
            builder.setNeutralButton("Donate", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    donate();
                }
            });
            View v = LayoutInflater.from(this).inflate(R.layout.about, null);
            TextView tv = (TextView)v.findViewById(R.id.aboutText);
            tv.setText(getString(R.string.aboutText, getVersion()));
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
                    Uri uri = Uri.parse("http://xkcd.com/archive/");
                    Intent i = new Intent(XkcdViewerActivity.this, ArchiveActivity.class);
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

    /* Comic-loading implementation using AsyncTasks and xkcd's JSON
     * interface (http://xkcd.com/json.html) follows.
     * 
     * goTo* methods must be called in UI thread
     * load* methods must be called in UI thread
     * create* methods can be called anywhere
     * fetch* methods must be called in a background thread
     */
    
    public void goToFirst() {
    	loadComic(createComicUri("1" /* TODO use comic provider */));
    }
    public void goToPrev() {
        loadComic(createComicUri(comicInfo.getPrevId()));
    }
    public void goToNext() {
        loadComic(createComicUri(comicInfo.getNextId()));
    }
    public void goToFinal() {
        loadComic(createFinalComicUri());
    }
    
    public Uri createComicUri(String id) {
        return Uri.parse("http://xkcd.com/"+id+"/info.0.json");
    }
    public Uri createFinalComicUri() {
        return Uri.parse("http://xkcd.com/info.0.json");
    }
    
    public void goToRandom() {
        /* Can't just choose a random number and go to the comic, because if
         * the user cancelled the comic loading at start, we won't know how
         * many comics there are! */
        new Utility.CancellableAsyncTaskWithProgressDialog<Object, Uri>() {

            protected Uri doInBackground(Object... params) {
                try {
                    return fetchRandomUri();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    return null;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    return null;
                }
            }
            
            protected void onPostExecute(Uri result) {
                super.onPostExecute(result);
                if (result != null)
                    loadComic(result);
                else
                    toast("Failed to get random comic");
            }
            
       }.start(this, "Randomizing...", new Object[]{null});
    }
    
    public Uri fetchRandomUri() throws MalformedURLException, IOException,
        NumberFormatException
    {
        HttpURLConnection http = (HttpURLConnection) new URL("http",
                "dynamic.xkcd.com", "/random/comic").openConnection();
        String redirect = http.getHeaderField("Location");
        Matcher m = comicUrlPattern.matcher(redirect);
        if (m.matches()) {
            return createComicUri(m.group(2));
        } else {
            return null;
        }
    }
    
    private class ComicInfoOrError {
        public IComicInfo comicInfo = null;
        public Throwable e = null;
        public ComicInfoOrError(Throwable e) { this.e = e; }
        public ComicInfoOrError(IComicInfo d) { comicInfo = d; }
    }
    
    public void loadComic(final Uri uri) {
        
        new Utility.CancellableAsyncTaskWithProgressDialog<Object, ComicInfoOrError>() {

            protected ComicInfoOrError doInBackground(Object... params) {
                try {
                    return new ComicInfoOrError(fetchComicInfo(uri));
                } catch (Throwable e) {
                    return new ComicInfoOrError(e);
                }
            }
            
            protected void onPostExecute(ComicInfoOrError result) {
                super.onPostExecute(result);
                if (result.comicInfo != null) {
                    comicInfo = result.comicInfo;
                    title.setText(comicInfo.getTitle());
                    comicIdSel.setText(comicInfo.getId());
                    refreshBookmarkBtn();
                    setLastReadComic(comicInfo.getId());
                    
                    loadComicImage(comicInfo.getImage());
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
    
    private String blockingReadUri(Uri uri) throws IOException,
        InterruptedException
    {
        StringBuffer sb = new StringBuffer();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                new URL(uri.toString()).openStream()));
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
            sb.append('\n');
            Utility.allowInterrupt();
        }
        return sb.toString();
    }
    
    public IComicInfo fetchComicInfo(Uri uri) throws IOException, JSONException,
        InterruptedException
    {
        String text = blockingReadUri(uri);
        JSONObject obj = (JSONObject)new JSONTokener(text).nextValue();
        XkcdComicInfo data = new XkcdComicInfo();
        data.img = Uri.parse(obj.getString("img"));
        data.alt = obj.getString("alt");
        data.num = obj.getInt("num");
        data.title = obj.getString("title");
        data.bookmarked = BookmarksHelper.isBookmarked(this,
                Integer.toString(data.num));
        return data;
    }
    
    public void loadComicImage(Uri uri) {
        webview.clearView();
        final ProgressDialog pd = ProgressDialog.show(
                this, "xkcdViewer", "Loading comic image...", false, true,
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
        webview.loadUrl(uri.toString());
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
