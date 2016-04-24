import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

/**
 * Created by Benoit Duffez on 04/07/13.
 * <p/>
 * Used to create an AsyncTask that doesn't leak the activity, but yet it provides the mTask even when the activity is recreated (e.g. screen rotation)
 * <p/>
 * Add the AsyncTaskFragment to your activity:
 * <pre>
 *     class MyActivity extends AppCompatActivity {
 *         public void onCreate(Bundle savedInstanceState) {
 *             AsyncTaskFragment.attachAsyncTaskFragment(this);
 *         }
 *
 *         // ...
 *
 *         // Class that will do the background job
 *         // Declare it as static or in a separate file to ensure that it will not leak the activity
 *         private static class MyBackgroundTask extends AsyncTaskFragment.TaskFragmentCallbacks<MyActivity, Uri, Integer, Boolean> {
 *             public RestoreTask(Uri uri) {
 *                 super(uri);
 *             }
 *
 *             public void onPreExecute(@NonNull RestoreActivity activity) {
 *                 // you can prepare your activity here
 *             }
 *
 *             public RestoreResult doInBackground(@NonNull Context applicationContext) {
 *                 // do something in background
 *                 return null;
 *             }
 *
 *             public void onPostExecute(@NonNull RestoreActivity activity, RestoreResult result) {
 *                 // back to your activity, do something with the result
 *                 activity.callSomeMethod(result);
 *             }
 *
 *             public void onProgressUpdate(@NonNull RestoreActivity activity, Integer progress) {
 *                 // do something with the progress
 *                 activity.updateProgressBar(progress);
 *             }
 *         }
 *     }
 * </pre>
 * <p/>
 * Then, somewhere in your activity:
 * <pre>
 *     AsyncTaskFragment.runTask(this, MyBackgroundTask(uri)); // this is an example with Uri as parameter
 * </pre>
 */
public class AsyncTaskFragment<CallingActivity extends AppCompatActivity> extends Fragment {
    /**
     * Amount of seconds we allow ourselves to wait for the activity to be created again and the fragment to be attached to it
     */
    private static final int ACTIVITY_WAIT_TIME_MAX = 15;

    /**
     * Used for the log tag and as a base of other tags/keys
     */
    private static final String TAG = AsyncTaskFragment.class.getSimpleName();

    /**
     * Our tag in the fragment manager
     */
    private static final String FRAGMENT_TAG = TAG + ".TaskFragmentTag";

    /**
     * This will set to null when the activity is deleted (e.g. screen rotation)
     */
    private CallingActivity mActivity;

    /**
     * Does not change, because it's not linked to the activity lifecycle
     */
    private Context mAppContext;

    public static <CallingActivity extends AppCompatActivity> void attachAsyncTaskFragment(CallingActivity activity) {
        FragmentManager fm = activity.getSupportFragmentManager();
        if (fm.findFragmentByTag(FRAGMENT_TAG) != null) {
            return;
        }
        AsyncTaskFragment fragment = new AsyncTaskFragment();
        fragment.setArguments(new Bundle());
        fm.beginTransaction().add(fragment, FRAGMENT_TAG).commit();
    }

    /**
     * Will create the {@link AsyncTask} in the fragment and call the {@link Task} callbacks
     *
     * @param activity      Used to retrieve the {@link AsyncTaskFragment}
     * @param taskCallbacks Task that will be run in the background
     */
    public static <CallingActivity extends AppCompatActivity, Parameters, Progress, Result>
    void runTask(AppCompatActivity activity, Task<CallingActivity, Parameters, Progress, Result> taskCallbacks) {
        FragmentManager fm = activity.getSupportFragmentManager();
        Fragment f = fm.findFragmentByTag(FRAGMENT_TAG);
        if (f != null && f instanceof AsyncTaskFragment) {
            //noinspection unchecked
            ((AsyncTaskFragment<CallingActivity>) f).run(taskCallbacks);
        } else {
            throw new IllegalStateException("Your activity must call AsyncTaskFragment.attachAsyncTaskFragment() in its onCreate method.");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        //noinspection unchecked
        mActivity = (CallingActivity) context;
        mAppContext = context.getApplicationContext();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mActivity = null;
    }

    @SuppressWarnings("unchecked")
    public <Params, Progress, Result> void run(final Task<CallingActivity, Params, Progress, Result> callbacks) {
        final AsyncTaskWrapper task = new AsyncTaskWrapper(callbacks, mAppContext, this);
        callbacks.setAsyncTask(task);
        task.execute(callbacks.mParameters);
    }

    /**
     * This will block the current thread for up to {@link #ACTIVITY_WAIT_TIME_MAX} seconds before giving
     * If the activity is attached within this period, the wait is cleared.
     * This ensures the {@link #mActivity} pointer is valid
     */
    private void waitForActivity() {
        int seconds = 0;
        while (mActivity == null && seconds < ACTIVITY_WAIT_TIME_MAX) {
            try {
                Thread.sleep(1000);
                seconds++;
            } catch (InterruptedException e) {
                Log.e(TAG, "Couldn't waitForCallbacks 1 second", null);
            }
        }
    }

    /**
     * Wrapper around an {@link AsyncTask} from the framework.
     *
     * @param <CallingActivity> The calling activity, which is derived from {@link AppCompatActivity}
     * @param <Params>          The parameters of the background task
     * @param <Progress>        The progress that the background task may publish
     * @param <Result>          The result of the background task
     */
    private static class AsyncTaskWrapper<CallingActivity extends AppCompatActivity, Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {
        @NonNull
        final Task<CallingActivity, Params, Progress, Result> mTask;

        @NonNull
        final Context mAppContext;

        @NonNull
        final AsyncTaskFragment<CallingActivity> mFragment;

        public AsyncTaskWrapper(@NonNull Task<CallingActivity, Params, Progress, Result> task, @NonNull Context appContext, @NonNull AsyncTaskFragment<CallingActivity> fragment) {
            mTask = task;
            mAppContext = appContext;
            mFragment = fragment;
        }

        @SafeVarargs
        @Override
        protected final Result doInBackground(Params... params) {
            return mTask.doInBackground(mAppContext);
        }

        @Override
        protected void onPreExecute() {
            synchronized (mFragment) {
                mFragment.waitForActivity();
                if (mFragment.mActivity != null) {
                    mTask.onPreExecute(mFragment.mActivity);
                }
            }
        }

        @Override
        protected void onPostExecute(Result result) {
            synchronized (mFragment) {
                mFragment.waitForActivity();
                if (mFragment.mActivity != null) {
                    mTask.onPostExecute(mFragment.mActivity, result);
                }
            }
        }

        @SafeVarargs
        @Override
        protected final void onProgressUpdate(Progress... params) {
            synchronized (mFragment) {
                // we don't wait for the activity here, because during the screen rotation the background task has to continue
                if (mFragment.mActivity != null) {
                    final Progress progress = params != null && params.length > 0 ? params[0] : null;
                    mTask.onProgressUpdate(mFragment.mActivity, progress);
                }
            }
        }
    }

    /**
     * Task definition with callbacks
     *
     * @param <CallingActivity> The calling activity, which is derived from {@link AppCompatActivity}
     * @param <Params>          The parameters of your background task
     * @param <Progress>        The progress that your background task may publish
     * @param <Result>          The result of your background task
     */
    public static abstract class Task<CallingActivity extends AppCompatActivity, Params, Progress, Result> {
        protected Params mParameters;

        private AsyncTaskWrapper<CallingActivity, Params, Progress, Result> mAsyncTask;

        public Task(Params params) {
            mParameters = params;
        }

        /**
         * Called on the UI thread, before the background task is started.
         */
        public abstract void onPreExecute(@NonNull CallingActivity activity);

        /**
         * The background task. Must return the result data.
         *
         * @param applicationContext The application Context, that can be used by the background task when a Context is required, and when the Activity may not be available.
         */
        @Nullable
        public abstract Result doInBackground(@NonNull Context applicationContext);

        /**
         * Called from the UI thread, when the background task is complete
         *
         * @param activity The calling activity
         * @param result   The result data of the background task
         */
        public abstract void onPostExecute(@NonNull CallingActivity activity, @Nullable Result result);

        /**
         * Called from the UI thread, when the background task has published some progress
         *
         * @param activity The calling activity
         * @param progress The progress of the background task
         */
        public abstract void onProgressUpdate(@NonNull CallingActivity activity, @Nullable Progress progress);

        /**
         * AsyncTaskFragment API: link the {@link AsyncTaskWrapper} and the task together
         *
         * @param task The AsyncTask wrapper
         */
        public void setAsyncTask(@NonNull AsyncTaskWrapper<CallingActivity, Params, Progress, Result> task) {
            mAsyncTask = task;
        }

        /**
         * Call this from the background thread to publish some progress
         * This will later call {@link Task#onProgressUpdate(AppCompatActivity, Object)} if the activity exists.
         *
         * @param progress The new progress of the background task
         */
        protected void publishProgress(Progress progress) {
            if (mAsyncTask != null) {
                mAsyncTask.onProgressUpdate(progress);
            }
        }
    }
}

