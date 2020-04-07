package com.notxx.icon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.util.ArrayMap;
import android.support.v7.graphics.Palette;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import top.trumeet.common.cache.IconCache;
import top.trumeet.common.utils.ImgUtils;

public class SmallIconDecorator extends NevoDecoratorService {

	private static final String MIPUSH_SMALL_ICON = "mipush_small_notification";

	private static final String TAG = "SmallIconDecorator";

	private static final String PHASE = "nevo.smallIcon.phase";

	private static final String RES_PACKAGE = "com.notxx.icon.res";

	public static int getBackgroundColor(Bitmap bitmap) {
		int backgroundColor = Color.BLACK;
		if (bitmap != null) {
			Palette palette = Palette.from(bitmap).generate();
			Palette.Swatch swatch = palette.getDominantSwatch();
			if (swatch != null) {
				backgroundColor = swatch.getRgb();
			}
		}
		return backgroundColor;
	}

	private Resources getResourcesForApplication(ApplicationInfo appInfo) {
		try {
			return getPackageManager().getResourcesForApplication(appInfo);
		} catch (PackageManager.NameNotFoundException ign) {
			return null;
		}
	}

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
	protected boolean apply(MutableStatusBarNotification evolving) {
		final MutableNotification n = evolving.getNotification();
		final Bundle extras = n.extras;
		final ApplicationInfo appInfo = extras.getParcelable("android.appInfo");
		final Resources appResources = getResourcesForApplication(appInfo);
		final String packageName = evolving.getPackageName(),
			channelId = n.getChannelId();
		byte phase = extras.getByte(PHASE);
		// Log.d(TAG, "package name: " + packageName);

		// bigText
		if (phase < 1 && n.bigContentView == null) {
			Log.d(TAG, "begin modifying bigText");
			final CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
			if (text != null) {
				extras.putCharSequence(Notification.EXTRA_TITLE_BIG, extras.getCharSequence(Notification.EXTRA_TITLE));
				extras.putCharSequence(Notification.EXTRA_BIG_TEXT, text);
				extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_BIG_TEXT);
			}
			extras.putByte(PHASE, (byte)1);
		} else {
			Log.d(TAG, "skip modifying bigText");
		}

		// channel
		if (phase < 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Log.d(TAG, "begin modifying channel");
			final int labelRes = appInfo.labelRes;
			final String label = (labelRes == 0 || appResources == null) ? appInfo.nonLocalizedLabel.toString() : appResources.getString(appInfo.labelRes);
			// Log.d(TAG, "label: " + label + " channel: " + channelId);
			final NotificationChannel channel = getNotificationChannel(packageName, Process.myUserHandle(), channelId);
			final String newId = "::" + packageName + "::" + channelId,
				newName = getString(R.string.decorator_channel_label, label, channel.getName());
			// Log.d(TAG, "newId: " + newId + " newName: " + newName);
			final List<NotificationChannel> channels = new ArrayList<>();
			channels.add(cloneChannel(channel, newId, newName));
			createNotificationChannels(packageName, Process.myUserHandle(), channels);
			n.setChannelId(newId);
			// Log.d(TAG, "original extras " + extras);
			extras.putByte(PHASE, (byte)2);
		} else {
			Log.d(TAG, "skip modifying channel");
		}
		
		// smallIcon
		if (phase < 4 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			Log.d(TAG, "begin modifying smallIcon");
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
			extras.putByte(PHASE, (byte)3);
		} else {
			Log.d(TAG, "skip modifying smallIcon");
		}
		Log.d(TAG, "end modifying");
		return true;
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
