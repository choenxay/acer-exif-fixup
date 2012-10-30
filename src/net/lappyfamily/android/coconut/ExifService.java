/**
 * Filesystem monitoring service.
 * 
 * Roman Yepishev <rtg@rtg.in.ua>
 * This code is in public domain
 */
package net.lappyfamily.android.coconut;

import java.io.File;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;

public class ExifService extends Service {
	private CameraDirectoryObserver cdo;
	
	private class CameraDirectoryObserver extends FileObserver {
		private File observedPath;
		final Handler mHandler = new Handler();

		public CameraDirectoryObserver(File cameraDirectory) {
			super(cameraDirectory.getAbsolutePath(), FileObserver.CLOSE_WRITE);
			this.observedPath = cameraDirectory;
		}

		public void onEvent(int event, final String path) {
			if (event == FileObserver.CLOSE_WRITE) {
				final File changedFile = new File(this.observedPath, path);
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
	
	@Override
	public void onCreate() {
		File dcimDirectory = Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
		File cameraDirectory = new File(dcimDirectory, "Camera");

		cdo = new CameraDirectoryObserver(cameraDirectory);
		cdo.startWatching();
	}

	@Override
	public void onDestroy() {
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
