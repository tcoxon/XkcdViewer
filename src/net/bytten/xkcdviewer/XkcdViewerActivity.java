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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
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
import android.view.inputmethod.InputMethodManager;

public class XkcdViewerActivity extends Activity {

    static class CouldntParseComicPage extends Exception {
        private static final long serialVersionUID = 1L;
    }

    static class ComicInfo {
        public URL imageURL;
        public String title = "", altText = "", number = "1";
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
    
    private Thread currentLoadThread = null;

    private Handler handler = new Handler();
    
    private ImageView bookmarkBtn = null;

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
                //showHoverText();
                showDialog(DIALOG_SHOW_HOVER_TEXT);
            }
        });

        title.setText(comicInfo.title);

        comicIdSel.setText(comicInfo.number);
        comicIdSel.setInputType(InputType.TYPE_CLASS_NUMBER);
        comicIdSel.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId,
                    KeyEvent event) {
                loadComicNumber(comicIdSel.getText().toString());
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

        ((Button)findViewById(R.id.lastBtn)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goToLast();
            }
        });
        
        ((ImageView)findViewById(R.id.randomBtn)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                loadRandomComic();
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
                BookmarksHelper.removeBookmark(this, comicInfo.number);
            } else {
                BookmarksHelper.addBookmark(this, comicInfo.number, comicInfo.title);
            }
            comicInfo.bookmarked = !comicInfo.bookmarked;
            refreshBookmarkBtn();
        }
    }
    
    public void goToFirst() { loadComicNumber("1"); }
    public void goToPrev() {
        if (getComicNumber() == 405) {
            loadComicNumber(403);
        }
        else {
            loadComicNumber(getComicNumber()-1);
        }
    }
    public void goToNext() { loadComicNumber(getComicNumber()+1); }
    public void goToLast() { loadComicNumber(null); }
    
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
    
    public String getLastReadComic() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getString("lastComic", null);
    }
    
    public void setLastReadComic(String n) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastComic", n);
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
            Matcher m = comicUrlPattern.matcher(i.getDataString());
            if (m.matches()) {
                loadComicNumber(m.group(2));
            } else {
                m = archiveUrlPattern.matcher(i.getDataString());
                if (m.matches()) {
                    showArchive();
                    this.finish();
                } else {
                    // it wasn't a link to comic or to the archive
                    m = xkcdHomePattern.matcher(i.getDataString());
                    // last ditch attempt: was it a link to the home page?
                    if (m.matches()) {
                        loadComicNumber(null);
                    } else {
                        toast("xkcdViewer can't display this content.");
                        this.finish();
                    }
                }
            }
        } else {
            // Started by xkcdViewer icon
            if (isReopenLastComic()) {
                loadComicNumber(getLastReadComic());
            } else {
                loadComicNumber(null);
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
            //showHoverText();
            showDialog(DIALOG_SHOW_HOVER_TEXT);
            return true;
        case R.id.MENU_REFRESH:
            loadComicNumber(comicInfo.number);
            return true;
        case R.id.MENU_RANDOM:
            loadRandomComic();
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
            goToLast();
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
            //showAbout();
            showDialog(DIALOG_SHOW_ABOUT);
            return true;
        case R.id.MENU_BOOKMARKS:
            showBookmarks();
            return true;
        case R.id.MENU_SEARCH_TITLE:
            //searchByTitle();
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

    public static interface ImageAttachmentReceiver {
        public void receive(File file);
        public void error(Exception ex);
        public void finish();
        public void cancel();
    }

    public void shareComicImage() {
        if (comicInfo != null && comicInfo.imageURL != null) {
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

            saveThread[0] = imageAttachment(comicInfo.imageURL,
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

    public Thread imageAttachment(final URL imageURL, final ImageAttachmentReceiver r) {
        Thread t = new Thread() {
            public void run() {
                FileOutputStream fos = null;
                InputStream is = null;
                try {
                    File file = File.createTempFile("xkcd-attachment-", ".png");
                    fos = new FileOutputStream(file);
                    is = imageURL.openStream();

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

    public String getCurrentComicUrl() {
        return "http://xkcd.com/"+comicInfo.number+"/";
    }

    // This method allows for an error to be added to the error stack.
    // Call showFailedDialogIfErrors() after all calls to this method
    // inside any method that calls this method.
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
    
    public int getComicNumber() {
        try {
            return Integer.parseInt(comicInfo.number);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void loadRandomComic() {
        loadComicNumber("?");
    }

    public void loadComicNumber(int number) {
        loadComicNumber(Integer.toString(number));
    }

    public void loadComicNumber(final String number) {

        final ProgressDialog pd = ProgressDialog.show(this,
                "xkcdViewer", "Loading comic...", true, true,
                new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                if (currentLoadThread != null) {
                    // tell loading to stop
                    currentLoadThread.interrupt();
                }
            }
        });

        currentLoadThread = new Thread(new Runnable() {
            public void run() {
                URL url = null;
                try {
                    if (number == null || number.equals("")) {
                        url = getLastComic();
                    } else if (number.equals("?")) {
                        url = getRandomComic();
                    } else {
                        url = getComicFromNumber(number);
                    }
                    loadComic(url);
                } catch (MalformedURLException e) {
                    failed("Malformed URL: "+e);
                } catch (FileNotFoundException e) {
                    // Comic doesn't exist. Probably went beyond the last or
                    // before the first.
                } catch (IOException e) {
                    failed("IO error: "+e);
                } catch (CouldntParseComicPage e) {
                    failed("Couldn't scrape info from the comic's HTML page");
                } catch (InterruptedException e) {
                    // Do nothing. Loading was cancelled.
                } catch (Throwable e) {
                    failed(e.toString());
                } finally {
                    handler.post(new Runnable() {
                        public void run() {
                            pd.dismiss();
                        }
                    });
                }
            }
        });
        currentLoadThread.start();
    }

    public void loadComic(URL url) throws IOException, CouldntParseComicPage, InterruptedException {
        final ComicInfo _comicInfo = getComicInfoFromPage(url);
        // Thread.sleep(0) gives interrupts a chance to get through.
        Thread.sleep(0);
        handler.post(new Runnable() {
            public void run() {

                comicInfo = _comicInfo;
                title.setText(comicInfo.number + " - " + comicInfo.title);
                comicIdSel.setText(comicInfo.number);
                refreshBookmarkBtn();

                webview.clearView();
                final ProgressDialog pd = ProgressDialog.show(
                        XkcdViewerActivity.this,
                        "xkcdViewer", "Loading comic image...", false, true,
                        new OnCancelListener() {
                            public void onCancel(DialogInterface dialog) {
                                webview.stopLoading();
                            }
                        });
                pd.setProgress(0);
                webview.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);

                        pd.dismiss();
                    }
                });
                webview.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onProgressChanged(WebView view, int newProgress) {
                        super.onProgressChanged(view, newProgress);
                        pd.setProgress(newProgress * 100);
                    }
                });
                webview.loadUrl(comicInfo.imageURL.toString());
                webview.requestFocus();
            }
        });
    }

    public URL getComicFromNumber(String number) throws MalformedURLException {
        if (number.equals("404")) number = "405";
        return new URL("http", "xkcd.com", "/"+number+"/");
    }

    public URL getLastComic() throws MalformedURLException {
        return new URL("http", "xkcd.com", "/");
    }

    public URL getRandomComic() throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL("http",
                "dynamic.xkcd.com", "/random/comic/").openConnection();
        return new URL(http.getHeaderField("Location"));
    }

    public ComicInfo getComicInfoFromPage(URL url) throws InterruptedException, IOException, CouldntParseComicPage {
        ComicInfo comicInfo = new ComicInfo();
        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = comicPattern.matcher(line);
                if (m.find()) {
                    comicInfo.imageURL = new URL(m.group(1));
                    comicInfo.altText = htmlEntityConvert(m.group(2));
                    comicInfo.title = htmlEntityConvert(m.group(3));
                }
                m = comicNumberPattern.matcher(line);
                if (m.find()) {
                    comicInfo.number = m.group(1);
                    setLastReadComic(comicInfo.number);
                    comicInfo.bookmarked = BookmarksHelper.isBookmarked(this, comicInfo.number);
                }
                // Thread.sleep(0) gives interrupts a chance to get through.
                Thread.sleep(0);
            }
            if (comicInfo.imageURL == null || comicInfo.altText == null
                    || comicInfo.title == null || comicInfo.number == null)
                throw new CouldntParseComicPage();
            return comicInfo;
        } finally {
            br.close();
        }
    }

    public String htmlEntityConvert(String text) {
        return Html.fromHtml(text).toString();
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
            builder.setMessage(comicInfo.altText);
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
                    Uri uri = Uri.parse("http://xkcd.com/archive/?q="+Uri.encode(query));
                    Intent i = new Intent(XkcdViewerActivity.this, ArchiveActivity.class);
                    i.setAction(Intent.ACTION_VIEW);
                    i.setData(uri);
                    startActivity(i);
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
            adh.setMessage(comicInfo.altText);
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
}
