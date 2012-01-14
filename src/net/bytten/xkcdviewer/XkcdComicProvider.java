package net.bytten.xkcdviewer;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;

import org.json.JSONObject;
import org.json.JSONTokener;

import android.net.Uri;

public class XkcdComicProvider implements IComicProvider {

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

}
