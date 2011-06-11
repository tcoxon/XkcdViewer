/*
 *  xkcdViewer - Android app to view xkcd comics with hover text
 *  Copyright (C) 2009-2010 Tom Coxon, Tyler Breisacher, David McCullough
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
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

public class XkcdViewerActivity extends Activity {

    static class CouldntParseComicPage extends Exception {
        private static final long serialVersionUID = 1L;
    }

    static class ComicInfo {
        public Uri img;
        public String title = "", alt = "";
        public int num;
        public boolean bookmarked = false;
    }

    static Pattern comicPattern = Pattern.compile(
                       "<img\\ssrc=\"(http://[^\"]*imgs\\.xkcd\\.com/comics/[^\"]*)\"\\s"+
                       "title=\"([^\"]*)\" alt=\"([^\"]*)\""),
                   comicNumberPattern = Pattern.compile(
                       "<h3>Permanent link to this comic: "+
                       "http://xkcd\\.com/([0-9]+)/</h3>"),
                   xkcdHomePattern = Pattern.compile(
                       "http://(www\\.)?xkcd\\.com(/)?"),
                   comicUrlPattern = Pattern.compile(
                       "http://(www\\.)?xkcd\\.com/([0-9]+)(/)?"),
                   archiveUrlPattern = Pattern.compile(
                       "http://(www\\.)?xkcd\\.com/archive(/)?");
    
    public static final String DONATE_URL = "https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=C9JRVA3NTULSL&lc=US&item_name=XkcdViewer%20donation&item_number=xkcdviewer&currency_code=USD";
    
    public static final FrameLayout.LayoutParams ZOOM_PARAMS =
        new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);

    public static final String PACKAGE_NAME = "net.bytten.xkcdviewer";
    

    private HackedWebView webview;
    private TextView title;
    private ComicInfo comicInfo = new ComicInfo();
    private EditText comicIdSel;
    
    private View zoom = null;
    
    private Handler handler = new Handler();
    
    private ImageView bookmarkBtn = null;

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
                showHoverText();
            }
        });

        title.setText(comicInfo.title);

        comicIdSel.setText(Integer.toString(currentComicNumber()));
        comicIdSel.setInputType(InputType.TYPE_CLASS_NUMBER);
        comicIdSel.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId,
                    KeyEvent event) {
                try {
                    loadComic(createComicUri(Integer.parseInt(
                            comicIdSel.getText().toString())));
                } catch (NumberFormatException e) {
                    toast("Enter a number");
                }
                return false;
            }
        }); 
        comicIdSel.setOnFocusChangeListener(new OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    comicIdSel.setText("");
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

        ((Button)findViewById(R.id.lastBtn)).setOnClickListener(new View.OnClickListener() {
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
        if (comicInfo != null && comicInfo.bookmarked) {
            bookmarkBtn.setBackgroundResource(android.R.drawable.btn_star_big_on);
        } else {
            bookmarkBtn.setBackgroundResource(android.R.drawable.btn_star_big_off);
        }
    }
    
    public void toggleBookmark() {
        if (comicInfo != null) {
            if (comicInfo.bookmarked) {
                BookmarksHelper.removeBookmark(this, Integer.toString(currentComicNumber()));
            } else {
                BookmarksHelper.addBookmark(this, Integer.toString(currentComicNumber()), comicInfo.title);
            }
            comicInfo.bookmarked = !comicInfo.bookmarked;
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
    
    public int getLastReadComic() throws NumberFormatException {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return Integer.parseInt(prefs.getString("lastComic", null));
    }
    
    public void setLastReadComic(int n) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastComic", Integer.toString(n));
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
                    loadComic(createComicUri(Integer.parseInt(m.group(2))));
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
        i.setData(Uri.parse("http://xkcd.com/archive/"));
        i.setAction(Intent.ACTION_VIEW);
        startActivity(i);
    }
    
    public void showBookmarks() {
        Intent i = new Intent(this, ArchiveActivity.class);
        i.setData(Uri.parse("http://xkcd.com/archive/?bookmarks"));
        i.setAction(Intent.ACTION_VIEW);
        startActivity(i);
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
            showHoverText();
            return true;
        case R.id.MENU_REFRESH:
            loadComic(createComicUri(currentComicNumber()));
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
        case R.id.MENU_GO_TO_LAST:
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
            showAbout();
            return true;
        case R.id.MENU_BOOKMARKS:
            showBookmarks();
            return true;
        case R.id.MENU_SEARCH_TITLE:
            searchByTitle();
            return true;
        }
        return false;
    }
    
    public void searchByTitle() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        alert.setTitle("Search by Title");
        alert.setIcon(android.R.drawable.ic_menu_search);
        alert.setView(input);
        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String query = input.getText().toString();
                Uri uri = Uri.parse("http://xkcd.com/archive/?q="+Uri.encode(query));
                Intent i = new Intent(XkcdViewerActivity.this, ArchiveActivity.class);
                i.setAction(Intent.ACTION_VIEW);
                i.setData(uri);
                startActivity(i);
            }
        });
        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        alert.show();
    }
    
    public void showAbout() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.app_name);
        builder.setIcon(android.R.drawable.ic_menu_info_details);
        builder.setNegativeButton(android.R.string.ok, null);
        builder.setNeutralButton("Donate", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                donate();
            }
        });
        final View v = LayoutInflater.from(this).inflate(R.layout.about, null);
        final TextView tv = (TextView)v.findViewById(R.id.aboutText);
        tv.setText(getString(R.string.aboutText, getVersion()));
        builder.setView(v);
        builder.create().show();
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
        return "http://xkcd.com/"+Integer.toString(currentComicNumber())+"/";
    }

    public static interface ImageAttachmentReceiver {
        public void receive(File file);
        public void error(Exception ex);
        public void finish();
        public void cancel();
    }

    public void shareComicImage() {
        if (comicInfo != null && comicInfo.img != null) {
            final Thread[] saveThread = new Thread[1];

            final ProgressDialog pd = ProgressDialog.show(this,
                    "xkcdViewer", "Saving Image...", true, true,
                    new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    if (saveThread[0] != null) {
                        // tell loading to stop
                        saveThread[0].interrupt();
                    }
                }
            });

            saveThread[0] = imageAttachment(comicInfo.img,
                    new ImageAttachmentReceiver() {
                public void finish() {
                    handler.post(new Runnable() {
                        public void run() {
                            pd.dismiss();
                        }
                    });
                }
                public void receive(final File file) {
                    handler.post(new Runnable() {
                        public void run() {
                            Uri uri = Uri.fromFile(file);
                            Intent intent = new Intent(Intent.ACTION_SEND, null);
                            intent.setType("image/png");
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            startActivity(Intent.createChooser(intent, "Share Image..."));
                        }
                    });
                }
                public void error(final Exception ex) {
                    failed("Couldn't save attachment: "+ex);
                }
                public void cancel() {
                    handler.post(new Runnable() {
                        public void run() {
                            toast("Canceled image sharing.");
                        }
                    });
                }
            });

        } else {
            toast("No image loaded."); 
        }
    }

    public Thread imageAttachment(final Uri img, final ImageAttachmentReceiver r) {
        Thread t = new Thread() {
            public void run() {
                FileOutputStream fos = null;
                InputStream is = null;
                try {
                    File file = File.createTempFile("xkcd-attachment-", ".png");
                    fos = new FileOutputStream(file);
                    is = new URL(img.toString()).openStream();

                    byte[] buffer = new byte[512];
                    int count = -1;
                    while ((count = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, count);
                        Thread.sleep(0); // Give a chance for thread interrupts to get through
                    }

                    r.finish();
                    r.receive(file);
                } catch (InterruptedException ex) {
                    r.finish();
                    r.cancel();
                } catch (IOException ex) {
                    r.finish();
                    r.error(ex);
                } finally {
                    try {
                        if (fos != null) fos.close();
                        if (is != null) is.close();
                    } catch (IOException ex) {}
                }
            }
        };
        t.start();
        return t;
    }

    public void showHoverText() {
        AlertDialog.Builder builder = new AlertDialog.Builder(XkcdViewerActivity.this);
        builder.setMessage(comicInfo.alt);
        builder.setNeutralButton("Close", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void failed(final String reason) {
        handler.post(new Runnable() {
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(XkcdViewerActivity.this);
                builder.setMessage("Comic loading failed: "+reason);
                AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }
    
    /* Comic-loading implementation using AsyncTasks and xkcd's JSON
     * interface (http://xkcd.com/json.html) follows.
     * 
     * goTo* methods must be called in UI thread
     * load* methods must be called in UI thread
     * create* methods can be called anywhere
     * fetch* methods must be called in a background thread
     */
    
    private static abstract class CancellableAsyncTaskWithProgressDialog<Params, Result>
        extends AsyncTask<Params, Integer, Result>
    {
        private ProgressDialog pd;
        
        public void start(Context cxt, String pdText, Params... params) {
            pd = ProgressDialog.show(cxt,
                    "xkcdViewer", pdText, true, true,
                    new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }
            });
            execute(params);
        }
        
        protected void onProgressUpdate(Integer... progress) {
            pd.setProgress(progress[0]);
        }
        
        protected void onCancelled(Result result) {
            pd.dismiss();
        }
        
        protected void onPostExecute(Result result) {
            pd.dismiss();
        }
    }
    
    public int currentComicNumber() {
        System.out.println("Current comic: "+Integer.toString(comicInfo.num));
        return comicInfo.num;
    }
    
    public void goToFirst() { loadComic(createComicUri(1)); }
    public void goToPrev() {
        int n = currentComicNumber() - 1;
        if (n == 404) {
            // 404 is xkcd's error page!
            n = 403;
        }
        loadComic(createComicUri(n));
    }
    public void goToNext() {
        int n = currentComicNumber() + 1;
        if (n == 404) {
            // 404 is xkcd's error page!
            n = 405;
        }
        loadComic(createComicUri(n));
    }
    public void goToFinal() {
        loadComic(createFinalComicUri());
    }
    
    public Uri createComicUri(int n) {
        return Uri.parse("http://xkcd.com/"+Integer.toString(n)+"/info.0.json");
    }
    public Uri createFinalComicUri() {
        return Uri.parse("http://xkcd.com/info.0.json");
    }
    
    public void goToRandom() {
        /* Can't just choose a random number and go to the comic, because if
         * the user cancelled the comic loading at start, we won't know how
         * many comics there are! */
        new CancellableAsyncTaskWithProgressDialog<Object, Uri>() {

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
            return createComicUri(Integer.parseInt(m.group(2)));
        } else {
            return null;
        }
    }
    
    private class ComicInfoOrError {
        public ComicInfo comicInfo = null;
        public Throwable e = null;
        public ComicInfoOrError(Throwable e) { this.e = e; }
        public ComicInfoOrError(ComicInfo d) { comicInfo = d; }
    }
    
    public void loadComic(final Uri uri) {
        
        new CancellableAsyncTaskWithProgressDialog<Object, ComicInfoOrError>() {

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
                    String numStr = Integer.toString(comicInfo.num);
                    title.setText(numStr + " - " + comicInfo.title);
                    comicIdSel.setText(numStr);
                    refreshBookmarkBtn();
                    
                    loadComicImage(comicInfo.img);
                } else {
                    result.e.printStackTrace();
                    /* Syntaxhack pattern match against type of result.e: */
                    try {
                        throw result.e;
                    } catch (MalformedURLException e) {
                        failed("Malformed URL: "+e);
                    } catch (FileNotFoundException e) {
                        // Comic doesn't exist. Probably went beyond the last or
                        // before the first.
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
    
    private String blockingReadUri(Uri uri) throws IOException {
        StringBuffer sb = new StringBuffer();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                new URL(uri.toString()).openStream()));
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
            sb.append('\n');
        }
        return sb.toString();
    }
    
    public ComicInfo fetchComicInfo(Uri uri) throws IOException, JSONException {
        String text = blockingReadUri(uri);
        JSONObject obj = (JSONObject)new JSONTokener(text).nextValue();
        Thread.yield();
        ComicInfo data = new ComicInfo();
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

}
