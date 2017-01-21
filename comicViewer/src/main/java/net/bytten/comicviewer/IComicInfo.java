package net.bytten.comicviewer;

import android.net.Uri;

public interface IComicInfo {
    public Uri getImage();
    public String getTitle();
    public String getAlt();
    public Uri getLink();
    public String getId();
    public boolean isBookmarked();
    public void setBookmarked(boolean b);
    public String getNextId();
    public String getPrevId();
    public String getUrl();
}