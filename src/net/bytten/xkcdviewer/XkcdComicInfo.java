package net.bytten.xkcdviewer;

import net.bytten.comicviewer.IComicInfo;
import android.net.Uri;

public class XkcdComicInfo implements IComicInfo {

    public Uri img, link;
    public int num;
    public String title = "", alt = "";
    public boolean bookmarked;

    @Override
    public String getAlt() {
        return alt;
    }

    @Override
    public String getId() {
        return Integer.toString(num);
    }

    @Override
    public Uri getImage() {
        return img;
    }

    @Override
    public String getNextId() {
        int n = num + 1;
        // #404 is xkcd's error page!
        if (n == 404) ++n;
        return Integer.toString(n);
    }

    @Override
    public String getPrevId() {
        int n = num - 1;
        // #404 is xkcd's error page!
        if (n == 404) --n;
        return Integer.toString(n);
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getUrl() {
        return "http://xkcd.com/"+getId()+"/";
    }

    @Override
    public boolean isBookmarked() {
        return bookmarked;
    }

    @Override
    public void setBookmarked(boolean b) {
        bookmarked = b;
    }

    @Override
    public Uri getLink() {
        return link;
    }

}
