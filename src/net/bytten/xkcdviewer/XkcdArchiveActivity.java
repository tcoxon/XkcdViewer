package net.bytten.xkcdviewer;

public class XkcdArchiveActivity extends ArchiveActivity {

    @Override
    protected IComicDefinition makeComicDef() {
        return new XkcdComicDefinition();
    }

}
