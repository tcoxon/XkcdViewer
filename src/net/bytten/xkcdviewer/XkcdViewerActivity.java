/*
 *  XkcdViewer - Android app to view XKCD comics with hover text
 *  Copyright (C) 2009 Tom Coxon
 *  XKCD belongs to Randall Munroe.
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
import java.util.regex.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

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
                       "title=\"([^\"]*)\" alt=\"([^\"]*)\"(\\s?)/>"),
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
    
    public static final int MENU_REFRESH = 1,
        MENU_RANDOM = 2,
        MENU_SHARE_LINK = 3,
        MENU_SHARE_IMAGE = 4,
        MENU_SETTINGS = 5,
        MENU_GO_TO_LAST = 6,
        MENU_GO_TO_NEXT = 7,
        MENU_GO_TO_PREV = 8,
        MENU_GO_TO_FIRST = 9,
        MENU_DEBUG = 10,
        MENU_HOVER_TEXT = 11,
        MENU_ARCHIVE = 12,
        MENU_DONATE = 13,
        MENU_ABOUT = 14,
        MENU_BOOKMARKS = 15,
        MENU_SEARCH_TITLE = 16;

    private WebView webview;
    private TextView title;
    private ComicInfo comicInfo = new ComicInfo();
    private EditText comicIdSel;
    
    private View zoom = null;
    
    private Thread currentLoadThread = null;

    private Handler handler = new Handler();
    
    private ImageView bookmarkBtn = null;

    protected void resetContent() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.main);
        webview = (WebView)findViewById(R.id.viewer);
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
        final boolean value = prefs.getBoolean("useZoomControls",!isIncredible());
        setZoomControlEnable(value);
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
    
    public void setZoomControlEnable(boolean b) {
        final ViewGroup zoomParent = (ViewGroup)webview.getParent().getParent();
        if (zoom.getParent() == zoomParent) zoomParent.removeView(zoom);
        webview.getSettings().setBuiltInZoomControls(b);
        if (!b) {
            zoomParent.addView(zoom, ZOOM_PARAMS);
            zoom.setVisibility(View.GONE);
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
                        Toast.makeText(this, "XkcdViewer can't display this content.",
                                Toast.LENGTH_SHORT).show();
                        this.finish();
                    }
                }
            }
        } else {
            // Started by XkcdViewer icon
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
    
    private boolean debuggable() {
        final PackageManager pm = getPackageManager();
        try {
            final ApplicationInfo app = pm.getApplicationInfo(PACKAGE_NAME, 0);
            final boolean debuggable = (app.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            return debuggable;
        } catch (PackageManager.NameNotFoundException ex) {
            return false;
        }
    }
    
    public static boolean isIncredible() {
        return Build.MODEL.toLowerCase().contains("incredible") ||
            Build.MODEL.toLowerCase().contains("adr6300");
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final SubMenu smGoTo = menu.addSubMenu("Go To...")
            .setIcon(android.R.drawable.ic_menu_more);
        smGoTo.add(0, MENU_GO_TO_LAST, 0, "Last")
            .setIcon(android.R.drawable.ic_media_next);
        smGoTo.add(0, MENU_GO_TO_NEXT, 0, "Next")
            .setIcon(android.R.drawable.ic_media_ff);
        smGoTo.add(0, MENU_GO_TO_PREV, 0, "Previous")
            .setIcon(android.R.drawable.ic_media_rew);
        smGoTo.add(0, MENU_GO_TO_FIRST, 0, "First")
            .setIcon(android.R.drawable.ic_media_previous);
        smGoTo.add(0, MENU_ARCHIVE, 0, "Archive")
            .setIcon(R.drawable.ic_menu_archive);
        smGoTo.add(0, MENU_RANDOM, 0, "Random")
            .setIcon(R.drawable.ic_menu_dice);
        
        menu.add(0, MENU_HOVER_TEXT, 0, "Hover Text")
            .setIcon(android.R.drawable.ic_menu_info_details);
        
        menu.add(0, MENU_ARCHIVE, 0, "Comic List")
            .setIcon(R.drawable.ic_menu_archive);
        
        final SubMenu smShare = menu.addSubMenu("Share...")
            .setIcon(android.R.drawable.ic_menu_share);
        smShare.add(0, MENU_SHARE_LINK, 0, "Link...")
            .setIcon(android.R.drawable.ic_menu_share);
        smShare.add(0, MENU_SHARE_IMAGE, 0, "Image...")
            .setIcon(android.R.drawable.ic_menu_gallery);
        
        menu.add(0, MENU_BOOKMARKS, 0, "Favorites")
            .setIcon(R.drawable.ic_menu_star);
        menu.add(0, MENU_SEARCH_TITLE, 0, "Search by Title...")
            .setIcon(android.R.drawable.ic_menu_search);
        menu.add(0, MENU_REFRESH, 0, "Refresh")
            .setIcon(R.drawable.ic_menu_refresh);
        menu.add(0, MENU_SETTINGS, 0, "Preferences")
            .setIcon(android.R.drawable.ic_menu_manage);
        menu.add(0, MENU_DONATE, 0, "Donate")
            .setIcon(R.drawable.ic_menu_heart);
        menu.add(0, MENU_ABOUT, 0, "About")
            .setIcon(android.R.drawable.ic_menu_info_details);
        
        if (debuggable())
            menu.add(0, MENU_DEBUG, 0, "Debug");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_HOVER_TEXT:
            showHoverText();
            return true;
        case MENU_REFRESH:
            loadComicNumber(comicInfo.number);
            return true;
        case MENU_RANDOM:
            loadRandomComic();
            return true;
        case MENU_SHARE_LINK:
            shareComicLink();
            return true;
        case MENU_SHARE_IMAGE:
            shareComicImage();
            return true;
        case MENU_SETTINGS:
            showSettings();
            return true;
        case MENU_GO_TO_LAST:
            goToLast();
            return true;
        case MENU_GO_TO_NEXT:
            goToNext();
            return true;
        case MENU_GO_TO_PREV:
            goToPrev();
            return true;
        case MENU_GO_TO_FIRST:
            goToFirst();
            return true;
        case MENU_DEBUG:
            toast("Build.MODEL: \""+Build.MODEL+"\"");
            return true;
        case MENU_ARCHIVE:
            showArchive();
            return true;
        case MENU_DONATE:
            donate();
            return true;
        case MENU_ABOUT:
            showAbout();
            return true;
        case MENU_BOOKMARKS:
            showBookmarks();
            return true;
        case MENU_SEARCH_TITLE:
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
                    "XkcdViewer", "Saving Image...", true, true,
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
                            Toast.makeText(XkcdViewerActivity.this,
                                    "Canceled image sharing.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

        } else {
            Toast.makeText(this, "No image loaded.", Toast.LENGTH_SHORT).show(); 
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

    public void showHoverText() {
        AlertDialog.Builder builder = new AlertDialog.Builder(XkcdViewerActivity.this);
        builder.setMessage(comicInfo.altText);
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
                "XkcdViewer", "Loading comic...", true, true,
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
        final ComicInfo _comicInfo = getComicImageURLFromPage(url);
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
                        "XkcdViewer", "Loading comic image...", false, true,
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

    public ComicInfo getComicImageURLFromPage(URL url) throws InterruptedException, IOException, CouldntParseComicPage {
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
}
