package net.bytten.comicviewer;

import android.net.Uri;

public interface IComicDefinition {

    public String getComicTitle();
    public String getAuthorName();
    public Uri getArchiveUrl();
    public String getPackageName();
    public boolean hasAltText();
    public boolean isComicUrl(Uri url);
    public boolean isArchiveUrl(Uri url);
    public boolean isHomeUrl(Uri url);
    public String getAuthorLinkText();
    public Uri getAuthorLinkUrl();
    public String getComicTitleAbbrev();
    public boolean idsAreNumbers();
    public IComicProvider getProvider();
    public Uri getDonateUrl();
    public Uri getDeveloperUrl();

}
