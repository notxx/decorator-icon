package top.trumeet.common.cache

import android.content.Context
import android.graphics.Bitmap
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

	fun getRawIconBitmapWithoutLoader(ctx:Context, pkg:String):Bitmap? {
		return bitmapLruCache.get("raw_" + pkg)
	}

	fun getRawIconBitmap(ctx:Context, pkg:String):Bitmap? {
		return object:AbstractCacheAspect<Bitmap>(bitmapLruCache) {
			override fun gen():Bitmap? {
				try {
					val icon = ctx.getPackageManager().getApplicationIcon(pkg)
					return ImgUtils.drawableToBitmap(icon)
				} catch (ignored:Exception) {
					return null
				}
			}
		}.get("raw_" + pkg)
	}

	@JvmOverloads fun getIconCache(ctx:Context, pkg:String,
			raw:((Context, String) -> Bitmap?) = ({ ctx, pkg -> getRawIconBitmap(ctx, pkg) }),
			whiten:((Context, Bitmap?) -> Bitmap?) = ({ ctx, b -> whitenBitmap(ctx, b) }),
			iconize:((Context, Bitmap?) -> Icon?) = ({ _, b -> Icon.createWithBitmap(b) })):Icon? {
		return object:AbstractCacheAspect<Icon?>(mIconMemoryCaches) {
			override fun gen():Icon? {
				val rawIcon = raw(ctx, pkg)
				if (rawIcon == null) return null;
				val whiteIcon = whiten(ctx, rawIcon)
				return iconize(ctx, whiteIcon)
			}
		}.get("white_" + pkg)
	}

	fun getAppColor(ctx:Context, pkg:String, convert:((Context, Bitmap?) -> Int)):Int {
		return object:AbstractCacheAspect<Int>(appColorCache) {
			override fun gen():Int {
				val rawIconBitmap = getRawIconBitmap(ctx, pkg)
				if (rawIconBitmap == null) {
					return -1
				}
				return convert(ctx, rawIconBitmap)
			}
		}.get(pkg)
	}

	// interface Converter<T, R> {
	// 	fun convert(ctx:Context, b:T):R
	// }
	
	private object Holder {
		val instance = IconCache()
	}

	companion object {
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
	}
}