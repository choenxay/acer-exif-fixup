/**
 * Main activity
 * 
 * Roman Yepishev <rtg@rtg.in.ua>
 * This code is in public domain
 */
package net.lappyfamily.android.coconut;

import java.io.File;

import net.lappyfamily.android.coconut.R;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	ImageScannerTask scannerTask;
	File cameraDirectory;
	Dialog scannerDialog;
	ProgressBar progressBar;
	TextView progressInfoText;
	
	/* Yes, this is a bad idea to put AsyncTask in Activity.
	 * Will fix this once I read at least some docs on Java :)
	 * 
	 * I am really sorry about this.
	 */
	private class ImageScannerTask extends AsyncTask<String, Integer, Integer>{		
		Context context;
		
		ImageScannerTask(Context context) {
			this.context = context;
		}
		
		@Override
		protected void onPreExecute() {
			// We disable the service because we will be generating the inotify events.
			Intent serviceIntent = new Intent(context, ExifService.class);
	        stopService(serviceIntent);
		}
		
		@Override
		protected Integer doInBackground(String... params) {
			File cameraFolder = new File(params[0]);
			if (! cameraFolder.isDirectory()) {
				return 0;
			}
			
			ExifCorrector e = new ExifCorrector();
			
			File[] fileList = cameraFolder.listFiles();
						
			int fileCount = fileList.length;
			int correctedFileCount = 0;
			
			for (int i = 0; i < fileCount; i++) {
				
				if (this.isCancelled()) {
					break;
				}
				
				boolean res = e.correct(fileList[i]);
				publishProgress((int)(i * 100.0 / (float) fileCount),
						        i + 1, fileCount);
				
				if (res) {
					correctedFileCount++;
				}
			}
			return correctedFileCount;
		}
		
		protected void onProgressUpdate(Integer... progress) {
			progressBar.setProgress(progress[0]);
			progressInfoText.setText(String.format("%d/%d", progress[1], progress[2]));
		}
		
		protected void onPostExecute(Integer result) {
			Intent serviceIntent = new Intent(context, ExifService.class);
	        startService(serviceIntent);

			String itemsFixedLabel = getResources().getQuantityString(R.plurals.file_corrected_template, result);
			Toast t = Toast.makeText(context, String.format(itemsFixedLabel, result), Toast.LENGTH_SHORT);
			t.show();
			
			onRescanCompleted();
		}
		
		protected void onCancelled() {
			Intent serviceIntent = new Intent(context, ExifService.class);
	        startService(serviceIntent);
	        
	        onRescanCompleted();
		}
		
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
		File dcimDirectory = Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
		cameraDirectory = new File(dcimDirectory, "Camera");
        
		TextView cameraFolderInfo = (TextView)findViewById(R.id.camera_folder_info);
        cameraFolderInfo.setText(getString(R.string.camera_folder_info_template, cameraDirectory.getAbsolutePath()));
        
        Intent serviceIntent = new Intent(this, ExifService.class);
        startService(serviceIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if (item.getItemId() == R.id.fix_existing) {
    		startScanningExistingFiles();
    		return true;
    	}
    	return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onDestroy() {
    	stopScanningExistingFiles();
    	super.onDestroy();
    }
    
    @Override
    public void onStop() {
    	stopScanningExistingFiles();
    	super.onStop();
    }
    
    private void startScanningExistingFiles() {
    	scannerDialog = new Dialog(this);
        scannerDialog.setTitle(getString(R.string.scanning_title));
        scannerDialog.setContentView(R.layout.progress);
        
        progressBar = (ProgressBar) scannerDialog.findViewById(R.id.progressBar1);
        progressInfoText = (TextView) scannerDialog.findViewById(R.id.textView1);
        
        Button cancelButton = (Button) scannerDialog.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new OnClickListener() {	
			public void onClick(View arg0) {
				stopScanningExistingFiles();
			}
	    });

        scannerDialog.show();
        
    	scannerTask = new ImageScannerTask(this);
        scannerTask.execute(cameraDirectory.getAbsolutePath());
    }
    
    private void onRescanCompleted() {
		try {
			scannerDialog.dismiss();
		}
		catch(Exception e) {
			// Nothing. The Activity may have already gone.
		}
		scannerDialog = null;
	}
    
    private void stopScanningExistingFiles() {
    	if (scannerTask != null && scannerTask.getStatus() == AsyncTask.Status.RUNNING) {
    		scannerTask.cancel(true);
    		onRescanCompleted();
    	}
    }
}
