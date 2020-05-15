package com.notxx.icon

import android.app.Notification
import android.app.NotificationChannel
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.annotation.RequiresApi
import android.util.Log

import java.util.Optional

import com.oasisfeng.nevo.sdk.MutableNotification
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification

import top.trumeet.common.cache.IconCache
import top.trumeet.common.utils.ImgUtils

class CachedSmallIconDecorator:SmallIconDecoratorBase() {
	private lateinit var defIcon:Icon

	protected override fun onConnected() {
		// Log.d(T, "begin onConnected")
		defIcon = Icon.createWithResource(this, R.drawable.default_notification_icon)
		// Log.d(T, "defIcon " + defIcon)
		// Log.d(T, "end onConnected")
	}

	protected override fun applySmallIcon(evolving:MutableStatusBarNotification, n:MutableNotification) {
		var resources = getAppResources(RES_PACKAGE)
		val packageName = evolving.getPackageName()
		val appResources = getAppResources(packageName)
		val cache = IconCache.getInstance()
		var iconId:Int?
		var colorId:Int?
		val key = packageName.toLowerCase().replace(("\\.").toRegex(), "_")

		iconId = resources?.getIdentifier(key, "drawable", RES_PACKAGE)
		if (iconId != null && iconId != 0) { // has icon in icon-res
			// Log.d(T, "res $packageName iconId: $iconId")
			val ref = iconId
			val cached = cache.getIcon(this, packageName,
					{ _, _ -> IconCache.render(resources!!.getDrawable(ref, null)) }) // TODO
			if (cached != null) {
				n.setSmallIcon(cached)
			} else {
				iconId = null
			}
		}
		colorId = resources?.getIdentifier(key, "string", RES_PACKAGE)
		if (colorId != null && colorId != 0) { // has color in icon-res
			// Log.d(T, "res colorId: " + colorId)
			n.color = Color.parseColor(resources!!.getString(colorId))
		}
		if (iconId != null && iconId != 0) { // do nothing
			// Log.d(T, "do nothing $packageName iconId: $iconId")
		} else if (appResources != null) {
			// Log.d(T, "$packageName appResources: $appResources")
			iconId = appResources.getIdentifier(MIPUSH_SMALL_ICON, "drawable", packageName)
			if (iconId != 0) { // has embed icon
				// Log.d(T, "mipush_small $packageName iconId: $iconId")
				val cached = cache.getMiPushIcon(appResources, iconId, packageName)
				if (cached != null) {
					n.setSmallIcon(cached)
				} else {
					iconId = null
				}
			}
			if (iconId == null || iconId == 0) { // does not have icon
				// Log.d(T, "generate $packageName icon")
				val cached = cache.getIcon(this, packageName)
				if (cached != null) {
					n.setSmallIcon(cached)
				} else {
					n.setSmallIcon(defIcon)
				}
			}
		}
		if (colorId != null && colorId != 0) { // do nothing
			// Log.d(T, "do nothing $packageName colorId: $colorId")
		} else {
			// Log.d(T, "generate $packageName color")
			n.color = cache.getAppColor(this, packageName, { _, b -> getBackgroundColor(b) })
		}
	}

	companion object {
		@JvmField public val MIPUSH_SMALL_ICON = "mipush_small_notification"
		private val T = "CachedSmallIconDecorator"
		private val RES_PACKAGE = "com.notxx.icon.res"
	}
}