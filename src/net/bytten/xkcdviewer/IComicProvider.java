package net.bytten.xkcdviewer;

import android.net.Uri;

public interface IComicProvider {

    public Uri comicDataUrlForUrl(Uri url);
    public Uri createComicUrl(String comicId);
    public String getFirstId();
    public Uri getFinalComicUrl();
    public Uri fetchRandomComicUrl() throws Exception;
    public IComicInfo fetchComicInfo(Uri url) throws Exception;
    public IComicInfo createEmptyComicInfo();
    
}
