package net.bytten.xkcdviewer;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.bytten.comicviewer.IComicInfo;
import net.bytten.comicviewer.IComicProvider;
import net.bytten.comicviewer.Utility;
import net.bytten.comicviewer.ArchiveData.ArchiveItem;

import org.json.JSONObject;
import org.json.JSONTokener;

import android.net.Uri;
import android.util.Log;

public class XkcdComicProvider implements IComicProvider {

    private static final Pattern archiveItemPattern = Pattern.compile(
            // group(1): comic number;   group(2): date;   group(3): title
            "\\s*<a href=\"/(\\d+)/\" title=\"(\\d+-\\d+-\\d+)\">([^<]+)</a><br/>\\s*");
    private static final String ARCHIVE_URL = "https://www.xkcd.com/archive/";
    
    private XkcdComicDefinition def;
    
    public XkcdComicProvider(XkcdComicDefinition def) {
        this.def = def;
    }
    
    @Override
    public Uri comicDataUrlForUrl(Uri url) {
        Matcher m = XkcdComicDefinition.comicUrlPattern
            .matcher(url.toString());
        if (m.matches())
            return createComicUrl(m.group(3));
        return null;
    }

    @Override
    public Uri createComicUrl(String comicId) {
        return Uri.parse("https://xkcd.com/"+comicId+"/info.0.json");
    }

    @Override
    public IComicInfo fetchComicInfo(Uri url) throws Exception {
        // Uses xkcd's JSON interface
        //      (https://xkcd.com/json.html) 
        String text = Utility.blockingReadUri(url);
        JSONObject obj = (JSONObject)new JSONTokener(text).nextValue();
        Log.d("json", obj.names().toString());
        XkcdComicInfo data = new XkcdComicInfo();
        data.img = Uri.parse(obj.getString("img"));
        byte [] encodedAlt = obj.getString("alt").getBytes("ISO-8859-1");
        data.alt = new String(encodedAlt, "UTF-8");
        data.num = obj.getInt("num");
        data.title = obj.getString("title");
        if (obj.has("link") && obj.getString("link").length() > 0) {
            data.link = Uri.parse(obj.getString("link"));
        }
        return data;
    }

    @Override
    public Uri fetchRandomComicUrl() throws Exception {
        HttpURLConnection http = (HttpURLConnection) new URL("https",
                "dynamic.xkcd.com", "/random/comic").openConnection();
        http.setInstanceFollowRedirects(false);
        String redirect = http.getHeaderField("Location");
        if (redirect != null) {
            Uri loc = Uri.parse(redirect);
            if (def.isComicUrl(loc)) {
                return comicDataUrlForUrl(loc);
            }
        }
        Log.w("headers", Integer.toString(http.getResponseCode()));
        Map<String, List<String>> headers = http.getHeaderFields();
        for (String key: headers.keySet()) {
            if (key == null) continue;
            Log.w("headers", key);
            for (String val: headers.get(key)) {
                Log.w("headers", "    "+val);
            }
        }
        return null;
    }

    @Override
    public Uri getFinalComicUrl() {
        return Uri.parse("https://xkcd.com/info.0.json");
    }

    @Override
    public String getFirstId() {
        return "1";
    }

    @Override
    public IComicInfo createEmptyComicInfo() {
        return new XkcdComicInfo();
    }

    @Override
    public List<ArchiveItem> fetchArchive() throws Exception {
        List<ArchiveItem> archiveItems = new ArrayList<ArchiveItem>();
        URL url = new URL(ARCHIVE_URL);
        BufferedReader br = new BufferedReader(new InputStreamReader(Utility.openRedirectableConnection(url).getInputStream()));

        try {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = archiveItemPattern.matcher(line);
                while (m.find()) {
                    ArchiveItem item = new ArchiveItem();
                    item.comicId = m.group(1);
                    item.title = item.comicId + " - " + m.group(3);
                    archiveItems.add(item);
                }

                Utility.allowInterrupt();
            }

        } finally {
            br.close();
        }
        return archiveItems;
    }

    @Override
    public Uri getExplainUrl(IComicInfo comic) {
        return Uri.parse("http://www.explainxkcd.com/wiki/index.php?title="+
                comic.getId());
    }

}
