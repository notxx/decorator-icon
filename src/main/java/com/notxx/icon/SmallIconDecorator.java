package com.notxx.icon;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.util.ArrayMap;
import android.support.v7.graphics.Palette;
import android.util.Log;

import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import top.trumeet.common.cache.IconCache;

public class SmallIconDecorator extends NevoDecoratorService {

	private static final String MIPUSH_SMALL_ICON = "mipush_small_notification";

	private static final String TAG = "SmallIconDecorator";

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
		Bundle extras = n.extras;

		// bigText
		Log.d(TAG, "begin modifying bigText");
		if (n.bigContentView == null) {
			final CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
			if (text != null) {
				extras.putCharSequence(Notification.EXTRA_TITLE_BIG, extras.getCharSequence(Notification.EXTRA_TITLE));
				extras.putCharSequence(Notification.EXTRA_BIG_TEXT, text);
				extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_BIG_TEXT);
			}
		}

		// smallIcon
		Log.d(TAG, "begin modifying smallIcon");
		Icon defIcon = Icon.createWithResource(this, R.drawable.default_notification_icon);
		String packageName = null;
		try {
			packageName = evolving.getPackageName();
			Log.d(TAG, "package name: " + packageName);
			if ("com.xiaomi.xmsf".equals(packageName))
				packageName = extras.getString("target_package", null);
		} catch (final RuntimeException ignored) {}    // Fall-through
		if (packageName == null) {
			Log.e(TAG, "packageName is null");
			return false;
		}
		extras.putBoolean("miui.isGrayscaleIcon", true);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			// do nothing
		} else {
			final IconCache cache = IconCache.getInstance();
			int iconId;
			// Log.d(TAG, "packageName: " + packageName);
			if (embed.containsKey(packageName)) {
				String key = embed.get(packageName);
				// Log.d(TAG, "key: " + key);
				iconId = getResources().getIdentifier(key, "drawable", getPackageName());
				// Log.d(TAG, "iconId: " + iconId);
				// Log.d(TAG, "com.xiaomi.smarthome iconId: " + R.drawable.com_xiaomi_smarthome);
				if (iconId > 0) // has icon
					n.setSmallIcon(Icon.createWithResource(this, iconId));
				int colorId = getResources().getIdentifier(key, "string", getPackageName());
				// Log.d(TAG, "colorId: " + colorId);
				if (colorId > 0) // has color
					n.color = Color.parseColor(getString(colorId));
			} else if ((iconId  = getResources().getIdentifier(MIPUSH_SMALL_ICON, "drawable", packageName)) != 0) { // has embed icon
				n.setSmallIcon(Icon.createWithResource(packageName, iconId));
			} else { // does not have icon
				Icon cached = cache.getIconCache(this, packageName, (ctx, b) -> Icon.createWithBitmap(b));
				if (cached != null) {
					n.setSmallIcon(cached);
				} else {
					n.setSmallIcon(defIcon);
				}
				n.color = cache.getAppColor(this, packageName, (ctx, b) -> getBackgroundColor(b));
			}
		}
		Log.d(TAG, "end modifying");
		return true;
	}
}
