package com.notxx.icon

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon

import com.oasisfeng.nevo.sdk.MutableNotification
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification

import top.trumeet.common.cache.IconCache
import top.trumeet.common.utils.ImgUtils

class DirectSmallIconDecorator:SmallIconDecoratorBase() {
	protected override fun applySmallIcon(evolving:MutableStatusBarNotification, n:MutableNotification) {
		val original = n.getSmallIcon()
		if (original == null) return
		var bitmap = ImgUtils.drawableToBitmap(original.loadDrawable(this))
		n.color = getBackgroundColor(bitmap)
		bitmap = IconCache.whitenBitmap(this, bitmap)
		n.setSmallIcon(Icon.createWithBitmap(bitmap))
	}

	companion object {
		@JvmStatic private val T = "DirectSmallIconDecorator"
	}
}