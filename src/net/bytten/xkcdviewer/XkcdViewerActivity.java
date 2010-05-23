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
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.Config;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

public class XkcdViewerActivity extends Activity {

    static class CouldntParseComicPage extends Exception {}

    static class ComicInfo {
        public URL imageURL;
        public String title = "", altText = "", number = "1";
    }

    static Pattern comicPattern = Pattern.compile(
            "<img src=\"(http://[^\"]*imgs\\.xkcd\\.com/comics/[^\"]*)\" "+
    "title=\"([^\"]*)\" alt=\"([^\"]*)\" />"),
    comicNumberPattern = Pattern.compile(
            "<h3>Permanent link to this comic: "+
    "http://xkcd\\.com/([0-9]+)/</h3>");

    public static final String PACKAGE_NAME = "net.bytten.xkcdviewer";
    
    public static final int MENU_HOVER_TEXT = 0,
    MENU_REFRESH = 1,
    MENU_RANDOM = 2,
    MENU_SHARE_LINK = 3,
    MENU_SHARE_IMAGE = 4,
    MENU_DEBUG = 10;

    private WebView webview;
    private TextView title;
    private ComicInfo comicInfo = new ComicInfo();
    private EditText comicIdSel;

    private Thread currentLoadThread = null;

    private Handler handler = new Handler();

    protected void resetContent() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.main);
        webview = (WebView)findViewById(R.id.viewer);
        title = (TextView)findViewById(R.id.title);
        comicIdSel = (EditText)findViewById(R.id.comicIdSel);

        webview.requestFocus();

        webview.getSettings().setBuiltInZoomControls(true);
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

        ((Button)findViewById(R.id.firstBtn)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                loadComicNumber("1");
            }
        });

        ((Button)findViewById(R.id.prevBtn)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                loadComicNumber(getComicNumber()-1);
            }
        });

        ((Button)findViewById(R.id.nextBtn)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                loadComicNumber(getComicNumber()+1);
            }
        });

        ((Button)findViewById(R.id.lastBtn)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                loadComicNumber(null);
            }
        });
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resetContent();

        loadComicNumber(null);
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
    
    private boolean isIncredible() {
        return Build.MODEL.toLowerCase().contains("incredible") ||
            Build.MODEL.toLowerCase().contains("adr6300");
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_RANDOM, 0, "Random");
        menu.add(0, MENU_HOVER_TEXT, 0, "Hover Text");
        menu.add(0, MENU_REFRESH, 0, "Refresh");
        menu.add(0, MENU_SHARE_LINK, 0, "Share Link...");
        menu.add(0, MENU_SHARE_IMAGE, 0, "Share Image...");
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
        case MENU_DEBUG:
            toast("Build.MODEL: \""+Build.MODEL+"\"");
            return true;
        }
        return false;
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

    private static class HtmlEntity {
        char chr;
        String code;
        public HtmlEntity(char chr_, String code_) {
            chr = chr_; code = code_;
        }
    }

    private final static HtmlEntity[] HTML_ENTITIES = {
        new HtmlEntity(' ', "&nbsp;"),
        new HtmlEntity('<', "&lt;"),
        new HtmlEntity('>', "&gt;"),
        new HtmlEntity('&', "&amp;"),
        new HtmlEntity('¢', "&cent;"),
        new HtmlEntity('£', "&pound;"),
        new HtmlEntity('¥', "&yen;"),
        new HtmlEntity('€', "&euro;"),
        new HtmlEntity('§', "&sect;"),
        new HtmlEntity('©', "&copy;"),
        new HtmlEntity('®', "&reg;"),
    };

    public String htmlEntityConvert(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                boolean found = false;
                for (HtmlEntity he: HTML_ENTITIES) {
                    if (i+he.code.length() <= text.length() &&
                            text.substring(i,i+he.code.length()).equals(he.code)) {
                        result.append(he.chr);
                        found = true;
                        i += he.code.length()-1;
                        break;
                    }
                }
                if (!found) {
                    if (i+2 < text.length() && text.charAt(i+1) == '#') {
                        int end = text.indexOf(';',i+2);
                        if (end == -1) end = text.length();
                        String num = text.substring(i+2,end);
                        try {
                            int n = Integer.parseInt(num);
                            result.append((char)n);
                            found = true;
                            i = end;
                        } catch (NumberFormatException ex) {
                        }
                    }
                    if (!found) {
                        result.append('&');
                    }
                }
            } else {
                result.append(text.charAt(i));
            }
        }
        return result.toString();
    }
}
