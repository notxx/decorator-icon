package com.notxx.icon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.content.Context;
import android.content.pm.ApplicationInfo;
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

public class SmallIconDecorator extends NevoDecoratorService {

	private static final String MIPUSH_SMALL_ICON = "mipush_small_notification";

	private static final String TAG = "SmallIconDecorator";

	private static final String PHASE = "nevo.smallIcon.phase";

	private static int getBackgroundColor(Bitmap bitmap) {
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

	private ArrayMap<String, String> embed;

	@Override
	protected void onConnected() {
		// Log.d(TAG, "begin onConnected");
		String[] array = getResources().getStringArray(R.array.decorator_icon_embed);
		this.embed = new ArrayMap<>(array.length);
		// Log.d(TAG, "array: " + array.length);
		for (String s : array) {
			this.embed.put(s, s.toLowerCase().replaceAll("\\.", "_"));
			// Log.d(TAG, s);
		}
		// Log.d(TAG, "end onConnected");
	}

	@Override
	protected boolean apply(MutableStatusBarNotification evolving) {
		final MutableNotification n = evolving.getNotification();
		final Bundle extras = n.extras;
		final String packageName = evolving.getPackageName(),
			channelId = n.getChannelId();
		byte phase = extras.getByte(PHASE);
		Log.d(TAG, "package name: " + packageName);

		// bigText
		Log.d(TAG, "begin modifying bigText");
		if (phase < 1 && n.bigContentView == null) {
			final CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
			if (text != null) {
				extras.putCharSequence(Notification.EXTRA_TITLE_BIG, extras.getCharSequence(Notification.EXTRA_TITLE));
				extras.putCharSequence(Notification.EXTRA_BIG_TEXT, text);
				extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_BIG_TEXT);
			}
			extras.putByte(PHASE, (byte)1);
		}

		// channel
		if (phase < 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Log.d(TAG, "begin modifying channel");
			try {
				final ApplicationInfo appInfo = extras.getParcelable("android.appInfo");
				final int labelRes = appInfo.labelRes;
				final String label = (labelRes == 0) ? appInfo.nonLocalizedLabel.toString() : getPackageManager().getResourcesForApplication(appInfo).getString(appInfo.labelRes);
				Log.d(TAG, "label: " + label + " channel: " + channelId);
				final NotificationChannel channel = getNotificationChannel(packageName, Process.myUserHandle(), channelId);
				final String newId = "::" + packageName + "::" + channelId,
					newName = getString(R.string.decorator_channel_label, label, channel.getName());
				Log.d(TAG, "newId: " + newId + " newName: " + newName);
				final List<NotificationChannel> channels = new ArrayList<>();
				channels.add(cloneChannel(channel, newId, newName));
				createNotificationChannels(packageName, Process.myUserHandle(), channels);
				n.setChannelId(newId);
				Log.d(TAG, "original extras " + extras);
			} catch (final Exception ignored) { Log.e(TAG, "?", ignored); } // Fall-through
			extras.putByte(PHASE, (byte)2);
		}
		
		// smallIcon
		if (phase < 3 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			Log.d(TAG, "begin modifying smallIcon");
			Icon defIcon = Icon.createWithResource(this, R.drawable.default_notification_icon);
			extras.putBoolean("miui.isGrayscaleIcon", true);
			final IconCache cache = IconCache.getInstance();
			int iconId;
			if (channelId == null) {
				n.color = Color.RED;
				Log.d(TAG, "null extras " + extras);
			} else if (channelId.equals("com.huawei.android.pushagent")
					|| channelId.equals("com.huawei.android.pushagent.low")
					|| channelId.endsWith("::com.huawei.android.pushagent")
					|| channelId.endsWith("::com.huawei.android.pushagent.low")) {
				n.color = Color.GREEN;
				Log.d(TAG, "hwpush extras " + extras);
			} else {
				n.color = Color.BLUE;
				Log.d(TAG, "other extras " + extras);
			}
			/*if (embed.containsKey(packageName)) {
				String key = embed.get(packageName);
				// Log.d(TAG, "key: " + key);
				iconId = getResources().getIdentifier(key, "drawable", getPackageName());
				// Log.d(TAG, "iconId: " + iconId);
				// Log.d(TAG, "com.xiaomi.smarthome iconId: " + R.drawable.com_xiaomi_smarthome);
				if (iconId > 0) // has icon
					n.setSmallIcon(Icon.createWithResource(this, iconId));
				int colorId = getResources().getIdentifier(key, "string", getPackageName());
				// if (colorId != 0) // has color
				// 	n.color = Color.parseColor(getString(colorId));
				// Log.d(TAG, "通知 " + iconId + " channel: " + n.getChannelId());
			} else */if ((iconId  = getResources().getIdentifier(MIPUSH_SMALL_ICON, "drawable", packageName)) != 0) { // has embed icon
				n.setSmallIcon(Icon.createWithResource(packageName, iconId));
			} else { // does not have icon
				Icon cached = cache.getIconCache(this, packageName, (ctx, b) -> Icon.createWithBitmap(b));
				if (cached != null) {
					n.setSmallIcon(cached);
				} else {
					n.setSmallIcon(defIcon);
				}
				// n.color = cache.getAppColor(this, packageName, (ctx, b) -> getBackgroundColor(b));
			}
			extras.putByte(PHASE, (byte)3);
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
