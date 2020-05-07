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

class SmallIconDecorator:SmallIconDecoratorBase() {
	private lateinit var defIcon:Icon

	protected override fun onConnected() {
		// Log.d(TAG, "begin onConnected")
		defIcon = Icon.createWithResource(this, R.drawable.default_notification_icon)
		// Log.d(TAG, "defIcon " + defIcon)
		// Log.d(TAG, "end onConnected")
	}

	protected override fun applySmallIcon(evolving:MutableStatusBarNotification, n:MutableNotification) {
		var resources:Resources?
		try {
			val resApp = getPackageManager().getApplicationInfo(RES_PACKAGE, 0)
			// Log.d(TAG, "resApp " + resApp)
			resources = getResourcesForApplication(resApp)
			Log.d(TAG, "resources " + resources)
			// Log.d(TAG, "resources stringId " + resources.getIdentifier("cn_com_weilaihui3", "string", RES_PACKAGE))
			// Log.d(TAG, "resources drawableId " + resources.getIdentifier("cn_com_weilaihui3", "drawable", RES_PACKAGE))
		} catch (ign:PackageManager.NameNotFoundException) {
			resources = null
		}
		val appInfo:ApplicationInfo? = n.extras.getParcelable("android.appInfo")
		val appResources = getResourcesForApplication(appInfo)
		val packageName = evolving.getPackageName()
		val cache = IconCache.getInstance()
		var iconId:Int?
		var colorId:Int?
		val key = packageName.toLowerCase().replace(("\\.").toRegex(), "_")

		iconId = resources?.getIdentifier(key, "drawable", RES_PACKAGE)
		if (iconId != null) { // has icon in icon-res
			// Log.d(TAG, "iconId: " + iconId)
			val ref = iconId
			val cached = cache.getIconCache(this, packageName,
					{ _, _ -> ImgUtils.drawableToBitmap(resources!!.getDrawable(ref)) }, // TODO
					{ _, b -> b })
			if (cached != null) {
				n.setSmallIcon(cached)
			} else {
				iconId = null
			}
		}
		colorId = resources?.getIdentifier(key, "string", RES_PACKAGE)
		if (colorId != null) { // has color in icon-res
			// Log.d(TAG, "colorId: " + colorId)
			n.color = Color.parseColor(resources!!.getString(colorId))
		}
		if (iconId != null) { // do nothing
			// Log.d(TAG, "do nothing iconId: " + iconId)
		} else {
			iconId = appResources?.getIdentifier(MIPUSH_SMALL_ICON, "drawable", packageName)
			if (iconId != null) { // has embed icon
				// Log.d(TAG, "mipush_small iconId: " + iconId)
				val ref = iconId
				val cached = cache.getIconCache(this, packageName,
						{ _, _ -> ImgUtils.drawableToBitmap(appResources!!.getDrawable(ref)) }, // TODO
						{ _, b -> b })
				if (cached != null) {
					n.setSmallIcon(cached)
				} else {
					iconId = null
				}
			}
			if (iconId == null) { // does not have icon
				// Log.d(TAG, "generate iconId: " + iconId)
				val cached = cache.getIconCache(this, packageName)
				if (cached != null) {
					n.setSmallIcon(cached)
				} else {
					n.setSmallIcon(defIcon)
				}
			}
		}
		if (colorId != null) { // do nothing
			// Log.d(TAG, "do nothing colorId: " + colorId)
		} else {
			n.color = cache.getAppColor(this, packageName, { _, b -> getBackgroundColor(b) })
		}
	}

	@RequiresApi(Build.VERSION_CODES.O) private fun cloneChannel(channel:NotificationChannel, id:String, label:String):NotificationChannel {
		val clone = NotificationChannel(id, label, channel.getImportance())
		clone.setGroup(channel.getGroup())
		clone.setDescription(channel.getDescription())
		clone.setLockscreenVisibility(channel.getLockscreenVisibility())
		clone.setSound(Optional.ofNullable(channel.getSound()).orElse(Settings.System.DEFAULT_NOTIFICATION_URI), channel.getAudioAttributes())
		clone.setBypassDnd(channel.canBypassDnd())
		clone.setLightColor(channel.getLightColor())
		clone.setShowBadge(channel.canShowBadge())
		clone.setVibrationPattern(channel.getVibrationPattern())
		return clone
	}

	companion object {
		private val MIPUSH_SMALL_ICON = "mipush_small_notification"
		private val TAG = "SmallIconDecorator"
		private val RES_PACKAGE = "com.notxx.icon.res"
	}
}