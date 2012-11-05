/**
 * Filesystem monitoring service.
 * 
 * Roman Yepishev <rtg@rtg.in.ua>
 * This code is in public domain
 */
package net.lappyfamily.android.coconut;

import java.io.File;
import java.util.HashSet;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class ExifService extends Service {
	private CameraDirectoryObserver cdo;
	private HashSet<String> ignoredFiles;
	
	private class CameraDirectoryObserver extends FileObserver {
		private File observedPath;
		final Handler mHandler = new Handler();

		public CameraDirectoryObserver(File cameraDirectory) {
			super(cameraDirectory.getAbsolutePath(), FileObserver.CLOSE_WRITE);
			observedPath = cameraDirectory;
		}

		public void onEvent(int event, final String path) {
			if (event == FileObserver.CLOSE_WRITE) {
				final File changedFile = new File(observedPath, path);
				
				if (ignoredFiles.contains(path)) {
					/* This is the second time we hear about the file so
					 * we just ignore it for this run and forget.
					 */
					ignoredFiles.remove(path);
				}
				else {
					ignoredFiles.add(path);
					
					Runnable mCorrectFile = new Runnable() {
						public void run() {
							ExifCorrector corrector = new ExifCorrector();
							corrector.correct(changedFile);
						}
					};
	
					mHandler.post(mCorrectFile);
				}
			}
		}
	}
	
	@Override
	public void onCreate() {
		File dcimDirectory = Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
		File cameraDirectory = new File(dcimDirectory, "Camera");
		ignoredFiles = new HashSet<String>();
		
		cdo = new CameraDirectoryObserver(cameraDirectory);
		cdo.startWatching();
	}

	@Override
	public void onDestroy() {
		if (! ignoredFiles.isEmpty()) {
			Log.w("ExifService", "Lost track of " + ignoredFiles.size() + " ignored files");
		}
		cdo.stopWatching();
	}

	public int OnStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

}
