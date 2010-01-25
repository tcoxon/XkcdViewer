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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
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
    
    private static final FrameLayout.LayoutParams ZOOM_PARAMS =
	    new FrameLayout.LayoutParams(
	      ViewGroup.LayoutParams.FILL_PARENT,
	      ViewGroup.LayoutParams.WRAP_CONTENT,
	      Gravity.BOTTOM);
    
    public static final int MENU_HOVER_TEXT = 0,
    			    MENU_REFRESH = 1,
    			    MENU_RANDOM = 2;
    
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
        final View zoom = webview.getZoomControls();
        ((ViewGroup)webview.getParent().getParent()).addView(zoom, ZOOM_PARAMS);
        zoom.setVisibility(View.GONE);
        
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
        
        ((Button)findViewById(R.id.firstBtn)).setOnTouchListener(new View.OnTouchListener() {
	    public boolean onTouch(View v, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_UP) {
		    loadComicNumber("1");
		}
		return false;
	    }
        });
        
        ((Button)findViewById(R.id.prevBtn)).setOnTouchListener(new View.OnTouchListener() {
	    public boolean onTouch(View v, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_UP) {
		    loadComicNumber(getComicNumber()-1);
		}
		return false;
	    }
        });
        
        ((Button)findViewById(R.id.nextBtn)).setOnTouchListener(new View.OnTouchListener() {
	    public boolean onTouch(View v, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_UP) {
		    loadComicNumber(getComicNumber()+1);
		}
		return false;
	    }
        });
        
        ((Button)findViewById(R.id.lastBtn)).setOnTouchListener(new View.OnTouchListener() {
	    public boolean onTouch(View v, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_UP) {
		    loadComicNumber(null);
		}
		return false;
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
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
	menu.add(0, MENU_RANDOM, 0, "Random");
	menu.add(0, MENU_HOVER_TEXT, 0, "Hover Text");
	menu.add(0, MENU_REFRESH, 0, "Refresh");
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
	}
	return false;
    }
    
    public void showHoverText() {
	AlertDialog.Builder builder = new AlertDialog.Builder(XkcdViewerActivity.this);
      	builder.setMessage(comicInfo.altText);
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
	// Me 1: Hey me, this code is full of really awful hacks and spaghetti
	//	 code. When are you going to fix it?
	// Me 2: Meh. If my job is writing nice code, why should I do it at
	//       home too?
	// Me 1: And what's with these weird comments?
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
        	try {
        	    URL url;
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
    
    public URL getRandomComic() throws MalformedURLException {
	return new URL("http", "dynamic.xkcd.com", "/comic/random/");
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
		    comicInfo.altText = m.group(2);
		    comicInfo.title = m.group(3);
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
}
