package net.bytten.xkcdviewer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.bytten.comicviewer.IComicInfo;
import net.bytten.comicviewer.IComicProvider;
import net.bytten.comicviewer.Utility;
import net.bytten.comicviewer.ArchiveData.ArchiveItem;

import org.json.JSONObject;
import org.json.JSONTokener;

import android.net.Uri;

public class XkcdComicProvider implements IComicProvider {

    private static final Pattern archiveItemPattern = Pattern.compile(
            // group(1): comic number;   group(2): date;   group(3): title
            "\\s*<a href=\"/(\\d+)/\" title=\"(\\d+-\\d+-\\d+)\">([^<]+)</a><br/>\\s*");
    private static final String ARCHIVE_URL = "http://www.xkcd.com/archive/";
    
    private XkcdComicDefinition def;
    
    public XkcdComicProvider(XkcdComicDefinition def) {
        this.def = def;
    }
    
    @Override
    public Uri comicDataUrlForUrl(Uri url) {
        Matcher m = XkcdComicDefinition.comicUrlPattern
            .matcher(url.toString());
        if (m.matches())
            return createComicUrl(m.group(2));
        return null;
    }

    @Override
    public Uri createComicUrl(String comicId) {
        return Uri.parse("http://xkcd.com/"+comicId+"/info.0.json");
    }

    @Override
    public IComicInfo fetchComicInfo(Uri url) throws Exception {
        // Uses xkcd's JSON interface
        //      (http://xkcd.com/json.html) 
        String text = Utility.blockingReadUri(url);
        JSONObject obj = (JSONObject)new JSONTokener(text).nextValue();
        XkcdComicInfo data = new XkcdComicInfo();
        data.img = Uri.parse(obj.getString("img"));
        data.alt = obj.getString("alt");
        data.num = obj.getInt("num");
        data.title = obj.getString("title");
        return data;
    }

    @Override
    public Uri fetchRandomComicUrl() throws Exception {
        HttpURLConnection http = (HttpURLConnection) new URL("http",
                "dynamic.xkcd.com", "/random/comic").openConnection();
        String redirect = http.getHeaderField("Location");
        Uri loc = Uri.parse(redirect);
        if (def.isComicUrl(loc)) {
            return comicDataUrlForUrl(loc);
        } else {
            return null;
        }
    }

    @Override
    public Uri getFinalComicUrl() {
        return Uri.parse("http://xkcd.com/info.0.json");
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
        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));

        try {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = archiveItemPattern.matcher(line);
                while (m.find()) {
                    ArchiveItem item = new ArchiveItem();
                    item.comicId = m.group(1);
                    item.title = m.group(3);
                    archiveItems.add(item);
                }

                Utility.allowInterrupt();
            }

        } finally {
            br.close();
        }
        return archiveItems;
    }

}
