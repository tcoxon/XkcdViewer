package net.bytten.xkcdviewer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.net.Uri;
import android.os.AsyncTask;

public class Utility {

    public static void allowInterrupt() throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
    }

    public static abstract class CancellableAsyncTaskWithProgressDialog<Params, Result>
        extends AsyncTask<Params, Integer, Result>
    {
        private ProgressDialog pd;
        
        public void start(Context cxt, String pdText, Params... params) {
            pd = ProgressDialog.show(cxt,
                    "xkcdViewer", pdText, true, true,
                    new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }
            });
            execute(params);
        }
        
        protected void onProgressUpdate(Integer... progress) {
            pd.setProgress(progress[0]);
        }
        
        protected void onCancelled(Result result) {
            pd.dismiss();
        }
        
        protected void onPostExecute(Result result) {
            pd.dismiss();
        }
    }

    public static void blockingSaveFile(File file, Uri uri) throws IOException,
        InterruptedException
    {
        FileOutputStream fos = null;
        InputStream is = null;
        try {
            fos = new FileOutputStream(file);
            is = new URL(uri.toString()).openStream();
            
            byte[] buffer = new byte[512];
            int count = -1;
            while ((count = is.read(buffer)) != -1) {
                fos.write(buffer, 0, count);
                Utility.allowInterrupt();
            }
        } finally {
            try {
                if (fos != null) fos.close();
                if (is != null) is.close();
            } catch (IOException ex) {}
        }
    }

}
