package net.bytten.xkcdviewer;

import android.net.Uri;

interface IComicInfo {
    public Uri getImage();
    public String getTitle();
    public String getAlt();
    public String getId();
    public boolean isBookmarked();
    public void setBookmarked(boolean b);
    public String getNextId();
    public String getPrevId();
    public String getUrl();
}