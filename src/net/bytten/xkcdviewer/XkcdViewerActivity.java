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
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class XkcdViewerActivity extends Activity {
    
    static class CouldntParseComicPage extends Exception {}
    
    static class ComicInfo {
	public URL imageURL;
	public String title, altText, number;
    }
    
    static Pattern comicPattern = Pattern.compile(
	    "<img src=\"(http://imgs\\.xkcd\\.com/comics/.*)\" "+
	    "title=\"(.*)\" alt=\"(.*)\" />"),
	   comicNumberPattern = Pattern.compile(
		   "<h3>Permanent link to this comic: "+
		   "http://xkcd\\.com/([0-9]+)/</h3>"); 
    
    private WebView webview;
    private TextView title;
    private Button hoverTextBtn;
    private ComicInfo comicInfo;
    private EditText comicIdSel;
    
    private boolean cancelLoad = false;
    
    private Handler handler = new Handler();
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        webview = (WebView)findViewById(R.id.viewer);
        title = (TextView)findViewById(R.id.title);
        hoverTextBtn = (Button)findViewById(R.id.hoverTextBtn);
        comicIdSel = (EditText)findViewById(R.id.comicIdSel);
        
        comicIdSel.setOnEditorActionListener(new OnEditorActionListener() {
	    public boolean onEditorAction(TextView v, int actionId,
		    KeyEvent event) {
		//if (keyCode == KeyEvent.KEYCODE_ENTER) {
		    loadComicNumber(comicIdSel.getText().toString());
		//}
		return false;
	    }
        });
        
        hoverTextBtn.setOnTouchListener(new View.OnTouchListener() {
	    public boolean onTouch(View v, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_UP) {
      		    AlertDialog.Builder builder = new AlertDialog.Builder(XkcdViewerActivity.this);
      		    builder.setMessage(comicInfo.altText);
      		    AlertDialog alert = builder.create();
      		    alert.show();
		}
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
        
        loadComicNumber(null);
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
    
    public void loadComicNumber(int number) {
	loadComicNumber(Integer.toString(number));
    }
    
    public void loadComicNumber(final String number) {

	cancelLoad = false;

	final ProgressDialog pd = ProgressDialog.show(this,
		"XkcdViewer", "Loading comic...", true, true,
		new OnCancelListener() {
        	    public void onCancel(DialogInterface dialog) {
        		cancelLoad = true;
        	    }
		});
	
	new Thread(new Runnable() {
	    public void run() {
        	try {
        	    URL url;
        	    if (number != null) {
        		url = getComicFromNumber(number);
        	    } else {
        		url = getLastComic();
        	    }
        	    if (!cancelLoad)
        		loadComic(url);
        	} catch (MalformedURLException e) {
        	    failed("Malformed URL: "+e);
        	} catch (IOException e) {
        	    failed("IO error: "+e);
        	} catch (CouldntParseComicPage e) {
        	    failed("Couldn't scrape info from the comic's HTML page");
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
	}).start();
    }

    public void loadComic(URL url) throws IOException, CouldntParseComicPage {
	comicInfo = getComicImageURLFromPage(url);
	handler.post(new Runnable() {
	    public void run() {
		if (cancelLoad) return;
        	title.setText(comicInfo.number + " - " + comicInfo.title);
        	comicIdSel.setText(comicInfo.number);
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
    public ComicInfo getComicImageURLFromPage(URL url) throws IOException, CouldntParseComicPage {
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
		if (m.matches()) {
		    comicInfo.number = m.group(1);
		}
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
