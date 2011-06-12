package net.bytten.xkcdviewer;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
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


}
