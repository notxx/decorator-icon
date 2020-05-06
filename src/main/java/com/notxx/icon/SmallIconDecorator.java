package com.notxx.icon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.Optional;

import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;

import top.trumeet.common.cache.IconCache;
import top.trumeet.common.utils.ImgUtils;

public class SmallIconDecorator extends SmallIconDecoratorBase {

	private static final String MIPUSH_SMALL_ICON = "mipush_small_notification";

	private static final String TAG = "SmallIconDecorator";

	private static final String RES_PACKAGE = "com.notxx.icon.res";

	private Icon defIcon;
	private Resources resources;

	@Override
	protected void onConnected() {
		// Log.d(TAG, "begin onConnected");
		defIcon = Icon.createWithResource(this, R.drawable.default_notification_icon);
		// Log.d(TAG, "defIcon " + defIcon);
		try {
			final ApplicationInfo resApp = getPackageManager().getApplicationInfo(RES_PACKAGE, 0);
			// Log.d(TAG, "resApp " + resApp);
			resources = getResourcesForApplication(resApp);
			Log.d(TAG, "resources " + resources);
			// Log.d(TAG, "resources stringId " + resources.getIdentifier("cn_com_weilaihui3", "string", RES_PACKAGE));
			// Log.d(TAG, "resources drawableId " + resources.getIdentifier("cn_com_weilaihui3", "drawable", RES_PACKAGE));
			// final int arrayId = resources.getIdentifier("decorator_icon_embed", "array", RES_PACKAGE);
			// Log.d(TAG, "arrayId " + arrayId);
		} catch (PackageManager.NameNotFoundException ign) {
			resources = null;
		}
		// Log.d(TAG, "end onConnected");
	}

	@Override
	protected void applySmallIcon(MutableStatusBarNotification evolving, MutableNotification n) {
		final ApplicationInfo appInfo = n.extras.getParcelable("android.appInfo");
		final Resources appResources = getResourcesForApplication(appInfo);
		final String packageName = evolving.getPackageName(),
			channelId = n.getChannelId();
		final IconCache cache = IconCache.getInstance();
		int iconId = 0, colorId = 0;
		String key = packageName.toLowerCase().replaceAll("\\.", "_");
		if (resources != null && (iconId = resources.getIdentifier(key, "drawable", RES_PACKAGE)) != 0) { // has icon in icon-res
			// Log.d(TAG, "iconId: " + iconId);
			final int ref = iconId;
			Icon cached = cache.getIconCache(this, packageName, 
					(ctx, pkg) -> ImgUtils.drawableToBitmap(resources.getDrawable(ref)),
					(ctx, b) -> b);
			if (cached != null)
				n.setSmallIcon(cached);
			else
				iconId = 0;
		}
		if (resources != null && (colorId = resources.getIdentifier(key, "string", RES_PACKAGE)) != 0) { // has color in icon-res
			// Log.d(TAG, "colorId: " + colorId);
			n.color = Color.parseColor(resources.getString(colorId));
		}
		if (iconId != 0) { // do nothing
			// Log.d(TAG, "do nothing iconId: " + iconId);
		} else if ((iconId  = appResources.getIdentifier(MIPUSH_SMALL_ICON, "drawable", packageName)) != 0) { // has embed icon
			// Log.d(TAG, "mipush_small iconId: " + iconId);
			final int ref = iconId;
			Icon cached = cache.getIconCache(this, packageName, 
					(ctx, pkg) -> ImgUtils.drawableToBitmap(appResources.getDrawable(ref)),
					(ctx, b) -> b);
			if (cached != null)
				n.setSmallIcon(cached);
			else
				iconId = 0;
		} else { // does not have icon
			// Log.d(TAG, "generate iconId: " + iconId);
			Icon cached = cache.getIconCache(this, packageName);
			if (cached != null) {
				n.setSmallIcon(cached);
			} else {
				n.setSmallIcon(defIcon);
			}
		}
		if (colorId != 0) { // do nothing
			// Log.d(TAG, "do nothing colorId: " + colorId);
		} else {
			n.color = cache.getAppColor(this, packageName, (ctx, b) -> getBackgroundColor(b));
		}
	}

	@RequiresApi(Build.VERSION_CODES.O) private NotificationChannel cloneChannel(final NotificationChannel channel, final String id, final String label) {
		final NotificationChannel clone = new NotificationChannel(id, label, channel.getImportance());
		clone.setGroup(channel.getGroup());
		clone.setDescription(channel.getDescription());
		clone.setLockscreenVisibility(channel.getLockscreenVisibility());
		clone.setSound(Optional.ofNullable(channel.getSound()).orElse(Settings.System.DEFAULT_NOTIFICATION_URI), channel.getAudioAttributes());
		clone.setBypassDnd(channel.canBypassDnd());
		clone.setLightColor(channel.getLightColor());
		clone.setShowBadge(channel.canShowBadge());
		clone.setVibrationPattern(channel.getVibrationPattern());
		return clone;
	}
}
