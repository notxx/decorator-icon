package top.trumeet.common.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.support.v4.util.LruCache
import android.util.Log;

import top.trumeet.common.utils.ImgUtils

/**
* Author: TimothyZhang023
* Icon Cache
*
* Code implements port from
* https://github.com/MiPushFramework/MiPushFramework
*/
class IconCache private constructor() {
	private val bitmapLruCache:LruCache<String, Bitmap>
	private val mIconMemoryCaches:LruCache<String, Icon?>
	private val appColorCache:LruCache<String, Int>
	
	init {
		bitmapLruCache = LruCache(100)
		mIconMemoryCaches = LruCache(100)
		appColorCache = LruCache(100)
		//TODO check cacheSizes is correct ?
	}

	/** 获取图标前景 */
	fun getIconForeground(ctx:Context, pkg:String):Bitmap? {
		return object:AbstractCacheAspect<Bitmap>(bitmapLruCache) {
			override fun gen():Bitmap? {
				try {
					Log.d("SmallIcon", "foreground $pkg")
					val icon = ctx.getPackageManager().getApplicationIcon(pkg)
					var bitmap:Bitmap?
					if (icon is AdaptiveIconDrawable) {
						Log.d("SmallIcon", "foreground $BOUNDS")
						bitmap = ImgUtils.drawableToBitmap(icon.getForeground(), ADAPTIVE_CANVAS, BOUNDS.width(), BOUNDS.height())
					} else {
						Log.d("SmallIcon", "legacy foreground ${icon.getIntrinsicWidth()}, ${icon.getIntrinsicHeight()}")
						bitmap = ImgUtils.drawableToBitmap(icon, BOUNDS, BOUNDS.width(), BOUNDS.height()) // TODO remove background
					}
					return bitmap
				} catch (ignored:Exception) {
					Log.d("SmallIcon", "foreground", ignored)
					return null
				}
			}
		}.get("fore_" + pkg)
	}

	/** 获取图标背景 */
	fun getIconBackground(ctx:Context, pkg:String):Bitmap? {
		return object:AbstractCacheAspect<Bitmap>(bitmapLruCache) {
			override fun gen():Bitmap? {
				try {
					val icon = ctx.getPackageManager().getApplicationIcon(pkg)
					return if (icon is AdaptiveIconDrawable) {
						ImgUtils.drawableToBitmap(icon.getBackground(), ADAPTIVE_CANVAS, BOUNDS.width(), BOUNDS.height())
					} else {
						ImgUtils.drawableToBitmap(icon, BOUNDS, BOUNDS.width(), BOUNDS.height())
					}
				} catch (ignored:Exception) {
					Log.d("SmallIcon", "background $ignored")
					return null
				}
			}
		}.get("back_" + pkg)
	}

	@JvmOverloads fun getIconCache(ctx:Context, pkg:String,
			raw:((Context, String) -> Bitmap?) = ({ ctx, pkg -> getIconForeground(ctx, pkg) }),
			whiten:((Context, Bitmap?) -> Bitmap?) = ({ _, b -> alphaize(b) }),
			iconize:((Context, Bitmap?) -> Icon?) = ({ _, b -> (if (b != null) Icon.createWithBitmap(b) else null) })):Icon? {
		return object:AbstractCacheAspect<Icon?>(mIconMemoryCaches) {
			override fun gen():Icon? {
				val rawIcon = raw(ctx, pkg)
				if (rawIcon == null) return null;
				val whiteIcon = whiten(ctx, rawIcon)
				return iconize(ctx, whiteIcon)
			}
		}.get("white_" + pkg)
	}

	@JvmOverloads fun getAppColor(ctx:Context, pkg:String,
			convert:((Context, Bitmap?) -> Int) = ({ _, b -> com.notxx.icon.SmallIconDecoratorBase.getBackgroundColor(b) })):Int {
		return object:AbstractCacheAspect<Int>(appColorCache) {
			override fun gen():Int {
				val rawIconBitmap = getIconBackground(ctx, pkg)
				if (rawIconBitmap == null) {
					return -1
				}
				return convert(ctx, rawIconBitmap)
			}
		}.get(pkg)
	}

	private object Holder {
		val instance = IconCache()
	}

	companion object {
		private val ADAPTIVE_CANVAS = Rect(-18, -18, 90, 90) // TODO density
		@JvmStatic public val BOUNDS = Rect(0, 0, 72, 72) // TODO density

		@JvmStatic fun getInstance():IconCache {
			return Holder.instance;
		}

		@JvmStatic fun whitenBitmap(ctx:Context, b:Bitmap?):Bitmap? {
			if (b == null) {
				return null
			}
			val density = ctx.getResources().getDisplayMetrics().density
			return ImgUtils.convertToTransparentAndWhite(b, density)
		}

		@JvmStatic fun alphaize(bitmap:Bitmap?):Bitmap? {
			if (bitmap == null) { return null; }

			val width = bitmap.getWidth();
			val height = bitmap.getHeight();
			val pixels = IntArray(width * height);
			bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

			for(i in 0 until height) {
				for(j in 0 until width) {
					val pos = width * i + j
					val dot = pixels[pos]
					val red = ((dot and 0x00FF0000) shr 16)
					val green = ((dot and 0x0000FF00) shr 8)
					val blue = (dot and 0x000000FF)
					val gray = (red.toFloat() * 0.3 + green.toFloat() * 0.59 + blue.toFloat() * 0.11).toInt()
					pixels[pos] = ((gray shl 24) or 0xFFFFFF)
					// pixels[pos] = (0xFFFFFF)
				}
			}

			val newBmp = bitmap.copy(Bitmap.Config.ARGB_8888, true);
			newBmp.setPixels(pixels, 0, width, 0, 0, width, height);
			return newBmp;
		}
	}
}