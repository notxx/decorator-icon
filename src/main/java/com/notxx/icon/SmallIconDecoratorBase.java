package com.notxx.icon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v7.graphics.Palette;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

public abstract class SmallIconDecoratorBase extends NevoDecoratorService {
	private static final String TAG = "SmallIconDecoratorBase";

	protected static final String EXTRAS_PHASE = "nevo.smallIcon.phase";
	protected static final byte PHASE_BIG_TEXT = (byte)1;
	protected static final byte PHASE_CHANNEL = (byte)2;
	protected static final byte PHASE_SMALL_ICON = (byte)3;

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

	protected Resources getResourcesForApplication(ApplicationInfo appInfo) {
		try {
			return getPackageManager().getResourcesForApplication(appInfo);
		} catch (PackageManager.NameNotFoundException ign) {
			return null;
		}
	}

	protected void applyBigText(MutableNotification n, Bundle extras) {
		final CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
		if (text != null) {
			extras.putCharSequence(Notification.EXTRA_TITLE_BIG, extras.getCharSequence(Notification.EXTRA_TITLE));
			extras.putCharSequence(Notification.EXTRA_BIG_TEXT, text);
			extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_BIG_TEXT);
		}
	}

	protected void applyChannel(MutableStatusBarNotification evolving, MutableNotification n, Bundle extras) {
		final ApplicationInfo appInfo = extras.getParcelable("android.appInfo");
		final Resources appResources = getResourcesForApplication(appInfo);
		final String packageName = evolving.getPackageName(),
			channelId = n.getChannelId();
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
	}

	protected abstract void applySmallIcon(MutableStatusBarNotification evolving, MutableNotification n);

	@Override
	protected boolean apply(MutableStatusBarNotification evolving) {
		final MutableNotification n = evolving.getNotification();
		final Bundle extras = n.extras;
		byte phase = extras.getByte(EXTRAS_PHASE);
		// Log.d(TAG, "package name: " + packageName);

		// bigText
		if (phase < PHASE_BIG_TEXT && n.bigContentView == null) {
			Log.d(TAG, "begin modifying bigText");
			applyBigText(n, extras);
			extras.putByte(EXTRAS_PHASE, PHASE_BIG_TEXT);
		} else {
			Log.d(TAG, "skip modifying bigText");
		}

		// channel
		if (phase < PHASE_CHANNEL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Log.d(TAG, "begin modifying channel");
			applyChannel(evolving, n, extras);
			extras.putByte(EXTRAS_PHASE, PHASE_CHANNEL);
		} else {
			Log.d(TAG, "skip modifying channel");
		}
		
		// smallIcon
		if (phase < PHASE_SMALL_ICON && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			Log.d(TAG, "begin modifying smallIcon");
			applySmallIcon(evolving, n);
			extras.putByte(EXTRAS_PHASE, PHASE_SMALL_ICON);
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
