AsyncTaskFragment
=================

A fragment wrapper around an AsyncTask that handles screen rotations

This will add an invisible fragment to your activity. This fragment calls `setRetainInstance(true)` so it will never be deleted. Also, when it is detached from the activity (screen rotation, configuration change, etc.), it will nullify the pointer to the activity, so there is no memory leak. It will automatically call the callback methods when the activity is attached again after recreation.

Usage
-----

1. Have your activity call `AsyncTaskFragment.attachAsyncTaskFragment(this)` from the `onCreate` method.
2. Create a static subclass or a dedicated class that extends `AsyncTaskFragment.Task`
3. Where you need a background task to be run, call `AsyncTaskFragment.runTask(this, new YourTask(yourParam))`

Example
-------

```java
public class MyActivity extends AppCompatActivity {
	/**
	 * Create our class that will handle the background processing.
	 * It has to indicate the types for the activity, parameters, progress and result to the AsyncTaskFragment API
	 * In this example, the activity class is MyActivity, the parameters are a String, the progress an Integer and the result a Double
	 */
	private static class MyClass extends AsyncTaskFragment.Task<MyActivity, String, Integer, Double> {
		/**
		 * The constructor takes the parameters of our task
		 */
		public MyClass(String string) {
			super(string);
		}

		/**
		 * Called before the background processing has started. Called in the main thread
		 */
		@Override
		public void onPreExecute(MyActivity activity) {
			// Here you have a pointer to the activity, so you can do something with the views
			activity.mTextView.setText("We will start background processing shortly");
		}

		/**
		 * Called off the main thread
		 */
		@Override
		public Double doInBackground(@NonNull Context applicationContext) {
			// You have a pointer to the application context, if needed
			try {
				Thread.sleep(1000);
				publishProgress(20);
				Thread.sleep(1000);
				publishProgress(40);
				Thread.sleep(1000);
				publishProgress(60);
				Thread.sleep(1000);
				publishProgress(80);
				Thread.sleep(1000);
				publishProgress(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			break;

			// the parameter in your constructor is saved into the mParameter field
			return (double) mParameter.length();
		}

		/**
		 * Called on the main thread after the background processing has finished
		 */
		@Override
		public void onPostExecute(@NonNull MyActivity activity, Double result) {
			// You have the result, and a valid pointer to your activity
			// If the activity is not available when the background has finished, it will wait for up to 15 seconds for the
			// activity to be ready
			activity.mTextView.setText(String.format("The length of the EditText is: %.1f chars", result));
		}

		/**
		 * Called on the main thread shortly after the background thread calls publishProgress
		 * If the activity is gone when the background thread calls publishProgress, the progress will be discarded and the
		 * background thread will continue to run.
		 * So this method is not guaranteed to be called all the time
		 */
		@Override
		public void onProgressUpdate(@NonNull MyActivity activity, Integer progress) {
			activity.mTextView.setText(String.format("Working: %.2f%% done", progress));
		}
	}

	public TextView mTextView;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity);
		AsyncTaskFragment.attachAsyncTaskFragment(this); // Here we prepare the AsyncTaskFragment, but nothing happened yet

		mTextView = (TextView) findViewById(R.id.result);
		EditText text = (EditText) findViewById(R.id.text);
		findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				// Here we actually trigger the background task
				AsyncTaskFragment.runTask(MyActivity.this, new MyTask(text.getText()));
			}
		});
	}
}
```

LICENSE
=======

	Copyright 2013 Benoit Duffez
	
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	
	   http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

