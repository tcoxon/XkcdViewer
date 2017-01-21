package net.bytten.comicviewer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.net.Uri;
import android.os.AsyncTask;

public class Utility {

    public static URLConnection openRedirectableConnection(URL url) throws IOException {
        return openRedirectableConnection(url, 0);
    }

    private static URLConnection openRedirectableConnection(URL url, int redirects) throws IOException {
        URLConnection conn = url.openConnection();
        if (conn instanceof HttpURLConnection) {
            HttpURLConnection http = (HttpURLConnection)conn;
            int status = http.getResponseCode();
            if (status == -1) {
                throw new IOException("A certificate failure occurred. Make sure your device is fully up to date."); // In practice, that seems to be when this HTTP failure occurs
            }
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
                if (redirects > 2)
                    throw new IOException("Too many HTTP redirects");
                String location = http.getHeaderField("Location");
                if (location == null)
                    throw new IOException("Invalid redirect");
                return openRedirectableConnection(new URL(location), redirects + 1);
            }
        }
        return conn;
    }

    public static String blockingReadUri(Uri uri) throws IOException,
        InterruptedException
    {
        InputStream is = openRedirectableConnection(new URL(uri.toString())).getInputStream();
        try {
            StringBuffer sb = new StringBuffer();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
                Utility.allowInterrupt();
            }
            return sb.toString();
        } finally {
            is.close();
        }
    }

    public static void allowInterrupt() throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
    }

    public static abstract class CancellableAsyncTaskWithProgressDialog<Params, Result>
        extends AsyncTask<Params, Integer, Result>
    {
        private ProgressDialog pd;
        
        private String title;
        
        public CancellableAsyncTaskWithProgressDialog(String title) {
            this.title = title;
        }

        public void start(Context cxt, String pdText, Params... params) {
            pd = ProgressDialog.show(cxt,
                    title, pdText, true, true,
                    new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }
            });
            execute(params);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            pd.setProgress(progress[0]);
        }

        @Override
        protected void onCancelled(Result result) {
            pd.dismiss();
        }

        @Override
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
            is = openRedirectableConnection(new URL(uri.toString())).getInputStream();

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

    public static String getContentType(Uri uri) throws MalformedURLException, IOException {
        URLConnection conn = new URL(uri.toString()).openConnection();
        return conn.getContentType();
    }
    
}
