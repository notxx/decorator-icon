package com.notxx.icon

import android.app.Notification
import android.app.NotificationChannel
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.support.annotation.RequiresApi
import android.support.v7.graphics.Palette
import android.util.Log

import java.util.ArrayList
import java.util.Optional

import com.oasisfeng.nevo.sdk.MutableNotification
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification
import com.oasisfeng.nevo.sdk.NevoDecoratorService

abstract class SmallIconDecoratorBase:NevoDecoratorService() {
	protected fun getAppResources(appInfo:ApplicationInfo?):Resources? = getAppResources(appInfo?.packageName)

	protected fun getAppResources(packageName:String?):Resources? {
		if (packageName == null) return null
		try {
			return createPackageContext(packageName, 0)?.getResources()
		} catch (ign:PackageManager.NameNotFoundException) {
			return null
		}
	}

	protected fun applyBigText(n:MutableNotification, extras:Bundle) {
		val text = extras.getCharSequence(Notification.EXTRA_TEXT)
		if (text != null) {
			extras.putCharSequence(Notification.EXTRA_TITLE_BIG, extras.getCharSequence(Notification.EXTRA_TITLE))
			extras.putCharSequence(Notification.EXTRA_BIG_TEXT, text)
			extras.putString(Notification.EXTRA_TEMPLATE, TEMPLATE_BIG_TEXT)
		}
	}

	protected fun applyChannel(evolving:MutableStatusBarNotification, n:MutableNotification, extras:Bundle) {
		val appInfo:ApplicationInfo? = extras.getParcelable("android.appInfo")
		val packageName = evolving.getPackageName()
		val appResources = getAppResources(packageName)
		val channelId = n.getChannelId()
		val labelRes = (appInfo?.labelRes) ?: 0
		val label = if ((labelRes == 0 || appResources == null)) appInfo?.nonLocalizedLabel.toString() else appResources.getString(labelRes)
		// Log.d(T, "label: " + label + " channel: " + channelId)
		val channel = getNotificationChannel(packageName, Process.myUserHandle(), channelId)
		if (channel == null) return
		val newId = "::" + packageName + "::" + channelId
		val newName = getString(R.string.decorator_channel_label, label, channel.getName())
		// Log.d(T, "newId: " + newId + " newName: " + newName)
		val channels = ArrayList<NotificationChannel>()
		channels.add(cloneChannel(channel, newId, newName))
		createNotificationChannels(packageName, Process.myUserHandle(), channels)
		n.setChannelId(newId)
		// Log.d(T, "original extras " + extras)
	}

	protected abstract fun applySmallIcon(evolving:MutableStatusBarNotification, n:MutableNotification)

	protected override fun apply(evolving:MutableStatusBarNotification):Boolean {
		val n = evolving.getNotification()
		val extras = n.extras
		val phase = extras.getByte(EXTRAS_PHASE)
		// Log.d(T, "package name: " + packageName)
		// bigText
		if (phase < PHASE_BIG_TEXT && n.bigContentView == null) {
			Log.d(T, "begin modifying bigText")
			applyBigText(n, extras)
			extras.putByte(EXTRAS_PHASE, PHASE_BIG_TEXT)
		} else {
			Log.d(T, "skip modifying bigText")
		}
		// channel
		if (phase < PHASE_CHANNEL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Log.d(T, "begin modifying channel")
			applyChannel(evolving, n, extras)
			extras.putByte(EXTRAS_PHASE, PHASE_CHANNEL)
		} else {
			Log.d(T, "skip modifying channel")
		}
		// smallIcon
		// if (phase < PHASE_SMALL_ICON && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			Log.d(T, "begin modifying smallIcon")
			applySmallIcon(evolving, n)
		// 	extras.putByte(EXTRAS_PHASE, PHASE_SMALL_ICON)
		// } else {
		// 	Log.d(T, "skip modifying smallIcon")
		// }
		Log.d(T, "end modifying")
		return true
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
		@JvmStatic private val T = "SmallIconDecoratorBase"

		protected val EXTRAS_PHASE = "nevo.smallIcon.phase"
		protected val PHASE_BIG_TEXT = 1.toByte()
		protected val PHASE_CHANNEL = 2.toByte()
		protected val PHASE_SMALL_ICON = 3.toByte()

		@JvmStatic fun getBackgroundColor(bitmap:Bitmap?):Int {
			var backgroundColor = Color.BLACK
			if (bitmap != null) {
				val palette = Palette.from(bitmap).generate()
				val swatch = palette.getDominantSwatch()
				if (swatch != null) {
					backgroundColor = swatch.getRgb()
				}
			}
			return backgroundColor
		}
	}
}