package com.notxx.icon;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;

import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;

import top.trumeet.common.cache.IconCache;
import top.trumeet.common.utils.ImgUtils;

public class DirectSmallIconDecorator extends SmallIconDecoratorBase {

	private static final String TAG = "DirectSmallIconDecorator";

	@Override
	protected void applySmallIcon(MutableStatusBarNotification evolving, MutableNotification n) {
		final Icon original = n.getSmallIcon();
		if (original == null) return;
		Bitmap bitmap = ImgUtils.drawableToBitmap(original.loadDrawable(this));
		n.color = getBackgroundColor(bitmap);
		bitmap = IconCache.whitenBitmap(this, bitmap);
		n.setSmallIcon(Icon.createWithBitmap(bitmap));
	}
}
