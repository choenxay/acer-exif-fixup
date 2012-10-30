/**
 * Standard simple broadcast receiver
 * 
 * Roman Yepishev <rtg@rtg.in.ua>
 * This code is in public domain.
 */
package net.lappyfamily.android.coconut;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DefaultBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String intentAction = intent.getAction();
		Intent serviceIntent = new Intent(context, ExifService.class);
		if (intentAction.equals("android.intent.action.BOOT_COMPLETED")
			|| intentAction.equals("android.intent.action.MEDIA_MOUNTED")) {
			context.startService(serviceIntent);
		}
		else if (intentAction.equals("android.intent.action.MEDIA_EJECT")) {
			context.stopService(serviceIntent);
		}
	}
}
