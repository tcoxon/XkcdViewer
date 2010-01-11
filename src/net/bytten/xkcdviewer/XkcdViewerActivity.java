package net.bytten.xkcdviewer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class XkcdViewerActivity extends Activity {
    
    static class ComicInfo {
	public URL imageURL;
	public String title, altText, number;
    }
    
    static Pattern comicPattern = Pattern.compile(
	    "<img src=\"(http://imgs\\.xkcd\\.com/comics/.*)\" "+
	    "title=\"(.*)\" alt=\"(.*)\" /><br/>"),
	   comicNumberPattern = Pattern.compile(
		   "<h3>Permanent link to this comic: "+
		   "http://xkcd\\.com/([0-9]+)/</h3>"); 
    
    private WebView webview;
    private TextView title;
    private Button hoverTextBtn;
    private ComicInfo comicInfo;
    private EditText comicIdSel;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        webview = (WebView)findViewById(R.id.viewer);
        title = (TextView)findViewById(R.id.title);
        hoverTextBtn = (Button)findViewById(R.id.hoverTextBtn);
        comicIdSel = (EditText)findViewById(R.id.comicIdSel);
        
        comicIdSel.setOnKeyListener(new View.OnKeyListener() {
	    public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_ENTER) {
		    loadComicNumber(comicIdSel.getText().toString());
		}
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
    
    public void failed(String reason) {
	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	builder.setMessage("Comic loading failed: "+reason);
	AlertDialog alert = builder.create();
	alert.show();
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
    
    public void loadComicNumber(String number) {
	try {
	    URL url;
	    if (number != null) {
		url = getComicFromNumber(number);
	    } else {
		url = getLastComic();
	    }
	    loadComic(url);
	} catch (MalformedURLException e) {
	    failed("Malformed URL: "+e);
	} catch (IOException e) {
	    failed("IO error: "+e);
	}
    }

    public void loadComic(URL url) throws IOException {
	comicInfo = getComicImageURLFromPage(url);
	webview.loadUrl(comicInfo.imageURL.toString());
	title.setText(comicInfo.title);
	comicIdSel.setText(comicInfo.number);
    }
    
    public URL getComicFromNumber(String number) throws MalformedURLException {
	return new URL("http", "xkcd.com", "/"+number+"/");
    }
    
    public URL getLastComic() throws MalformedURLException {
	return new URL("http", "xkcd.com", "/");
    }
    public ComicInfo getComicImageURLFromPage(URL url) throws IOException {
	ComicInfo comicInfo = new ComicInfo();
	BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
	try {
	    String line;
	    while ((line = br.readLine()) != null) {
		Matcher m = comicPattern.matcher(line);
		if (m.matches()) {
		    comicInfo.imageURL = new URL(m.group(1));
		    comicInfo.altText = m.group(2);
		    comicInfo.title = m.group(3);
		}
		m = comicNumberPattern.matcher(line);
		if (m.matches()) {
		    comicInfo.number = m.group(1);
		}
	    }
	    return comicInfo;
	} finally {
	    br.close();
	}
    }
}
