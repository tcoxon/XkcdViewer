package net.bytten.xkcdviewer;

import java.util.regex.Pattern;

import android.net.Uri;

public class XkcdComicDefinition implements IComicDefinition {

    static public final Pattern
        xkcdHomePattern = Pattern.compile(
            "http://(www\\.)?xkcd\\.com(/)?"),
        comicUrlPattern = Pattern.compile(
            "http://(www\\.)?xkcd\\.com/([0-9]+)(/)?"),
        archiveUrlPattern = Pattern.compile(
            "http://(www\\.)?xkcd\\.com/archive(/)?");

    private XkcdComicProvider provider;
    
    public XkcdComicDefinition() {
        provider = new XkcdComicProvider(this);
    }
    
    @Override
    public Uri getArchiveUrl() {
        return Uri.parse("http://xkcd.com/archive/");
    }

    @Override
    public String getAuthorLinkText() {
        return "xkcd Store";
    }

    @Override
    public Uri getAuthorLinkUrl() {
        return Uri.parse("http://store.xkcd.com/");
    }

    @Override
    public String getAuthorName() {
        return "Randall Munroe";
    }

    @Override
    public String getComicTitle() {
        return "xkcd";
    }

    @Override
    public String getComicTitleAbbrev() {
        return "xkcd";
    }

    @Override
    public String getPackageName() {
        return "net.bytten.xkcdviewer";
    }

    @Override
    public boolean hasAltText() {
        return true;
    }

    @Override
    public boolean idsAreNumbers() {
        return true;
    }

    @Override
    public boolean isArchiveUrl(Uri url) {
        return archiveUrlPattern.matcher(url.toString()).matches();
    }
    
    @Override
    public boolean isComicUrl(Uri url) {
        return comicUrlPattern.matcher(url.toString()).matches();
    }

    @Override
    public boolean isHomeUrl(Uri url) {
        return xkcdHomePattern.matcher(url.toString()).matches();
    }

    @Override
    public IComicProvider getProvider() {
        return provider;
    }

}
