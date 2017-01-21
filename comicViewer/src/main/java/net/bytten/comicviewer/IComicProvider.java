package net.bytten.comicviewer;

import java.util.List;

import net.bytten.comicviewer.ArchiveData.ArchiveItem;
import android.net.Uri;

public interface IComicProvider {

    public Uri comicDataUrlForUrl(Uri url);
    public Uri createComicUrl(String comicId);
    public String getFirstId();
    public Uri getFinalComicUrl();
    public Uri fetchRandomComicUrl() throws Exception;
    public IComicInfo fetchComicInfo(Uri url) throws Exception;
    public IComicInfo createEmptyComicInfo();
    public List<ArchiveItem> fetchArchive() throws Exception;
    
    // Return null if the comic has no explanation
    public Uri getExplainUrl(IComicInfo comic);
    
}
