package top.trumeet.common.cache

import kotlin.math.hypot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.support.v4.util.LruCache
import android.util.Log

import top.trumeet.common.utils.ImgUtils

/**
* Author: TimothyZhang023
* Icon Cache
*
* Code implements port from
* https://github.com/MiPushFramework/MiPushFramework
*/
class IconCache private constructor() {
	private val foregroundCache:LruCache<String, Bitmap>
	private val backgroundCache:LruCache<String, Bitmap>
	private val iconCache:LruCache<String, Icon?>
	private val mipushCache:LruCache<String, Icon?>
	private val appColorCache:LruCache<String, Int>
	
	init {
		foregroundCache = LruCache(100)
		backgroundCache = LruCache(100)
		iconCache = LruCache(100)
		mipushCache = LruCache(100)
		appColorCache = LruCache(100)
		//TODO check cacheSizes is correct ?
	}

	/** 获取图标前景 */
	fun getIconForeground(ctx:Context, pkg:String):Bitmap? {
		return object:AbstractCacheAspect<Bitmap>(foregroundCache) {
			override fun gen():Bitmap? {
				try {
					// Log.d("SmallIcon", "foreground $pkg")
					val icon = ctx.getPackageManager().getApplicationIcon(pkg)
					var bitmap:Bitmap?
					if (icon is AdaptiveIconDrawable) {
						// Log.d("SmallIcon", "foreground $BOUNDS")
						bitmap = ImgUtils.drawableToBitmap(icon.getForeground(), ADAPTIVE_CANVAS, BOUNDS.width(), BOUNDS.height())
					} else {
						// Log.d("SmallIcon", "legacy foreground ${icon.getIntrinsicWidth()}, ${icon.getIntrinsicHeight()}")
						bitmap = ImgUtils.drawableToBitmap(icon, BOUNDS, BOUNDS.width(), BOUNDS.height())
						if (removeBackground(bitmap)) {
							val temp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
							val canvas = Canvas(temp);
							canvas.drawBitmap(bitmap, null, Rect(-20, -20, 52, 52), null)
							bitmap.recycle()
							bitmap = temp
						}
					}
					return bitmap
				} catch (ignored:Exception) {
					Log.d("SmallIcon", "foreground", ignored)
					return null
				}
			}
		}.get(pkg)
	}

	/** 获取图标背景 */
	fun getIconBackground(ctx:Context, pkg:String):Bitmap? {
		return object:AbstractCacheAspect<Bitmap>(backgroundCache) {
			override fun gen():Bitmap? {
				try {
					val icon = ctx.getPackageManager().getApplicationIcon(pkg)
					return if (icon is AdaptiveIconDrawable) {
						ImgUtils.drawableToBitmap(icon.getBackground(), ADAPTIVE_CANVAS, BOUNDS.width(), BOUNDS.height())
					} else {
						ImgUtils.drawableToBitmap(icon, BOUNDS, BOUNDS.width(), BOUNDS.height())
					}
				} catch (ignored:Exception) {
					Log.d("SmallIcon", "background", ignored)
					return null
				}
			}
		}.get(pkg)
	}

	@JvmOverloads fun getIcon(ctx:Context, pkg:String,
			raw:((Context, String) -> Bitmap?) = ({ ctx, pkg -> getIconForeground(ctx, pkg) }),
			whiten:((Context, Bitmap?) -> Bitmap?) = ({ _, b -> alphaize(b) }),
			iconize:((Context, Bitmap?) -> Icon?) = ({ _, b -> (if (b != null) Icon.createWithBitmap(b) else null) })):Icon? {
		return object:AbstractCacheAspect<Icon?>(iconCache) {
			override fun gen():Icon? {
				var bitmap = raw(ctx, pkg)
				if (bitmap == null) { return null }
				bitmap = whiten(ctx, bitmap)
				return iconize(ctx, bitmap)
			}
		}.get(pkg)
	}

	@JvmOverloads fun getMiPushIcon(ctx:Context, pkg:String,
			gen:((Context, String) -> Bitmap?),
			whiten:((Context, Bitmap?) -> Bitmap?) = ({ _, b -> alphaize(b) }),
			iconize:((Context, Bitmap?) -> Icon?) = ({ _, b -> (if (b != null) Icon.createWithBitmap(b) else null) })):Icon? {
		return object:AbstractCacheAspect<Icon?>(mipushCache) {
			override fun gen():Icon? {
				var bitmap = gen(ctx, pkg)
				if (bitmap == null) { return null }
				bitmap = whiten(ctx, bitmap)
				val icon = iconize(ctx, bitmap)
				Log.d("SmallIcon", "icon: $icon")
				return icon
			}
		}.get(pkg)
	}

	@JvmOverloads fun getAppColor(ctx:Context, pkg:String,
			convert:((Context, Bitmap?) -> Int)):Int {
		return object:AbstractCacheAspect<Int>(appColorCache) {
			override fun gen():Int {
				val background = getIconBackground(ctx, pkg)
				if (background == null) {
					return -1
				}
				return convert(ctx, background)
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
			return Holder.instance
		}

		@JvmStatic fun whitenBitmap(ctx:Context, b:Bitmap?):Bitmap? {
			if (b == null) {
				return null
			}
			val density = ctx.getResources().getDisplayMetrics().density
			return ImgUtils.convertToTransparentAndWhite(b, density)
		}

		@JvmStatic fun alphaize(bitmap:Bitmap?):Bitmap? {
			if (bitmap == null) { return null }

			val width = bitmap.getWidth()
			val height = bitmap.getHeight()
			val pixels = IntArray(width * height)
			bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

			for (i in 0 until height) {
				for (j in 0 until width) {
					val pos = width * i + j // 偏移
					val pixel = pixels[pos] // 颜色值
					val alpha = ((pixel.toLong() and 0xFF000000) shr 24).toFloat() // 透明度通道
					val red = ((pixel and 0x00FF0000) shr 16).toFloat() // 红色通道
					val green = ((pixel and 0x0000FF00) shr 8).toFloat() // 绿色通道
					val blue = (pixel and 0x000000FF).toFloat() // 蓝色通道
					val gray = (alpha * (red * 0.3 + green * 0.59 + blue * 0.11) / 0xFF).toInt() // 混合为明亮度
					pixels[pos] = ((gray shl 24) or 0xFFFFFF) // 以明亮度作为透明度
				}
			}

			val r = bitmap.copy(Bitmap.Config.ARGB_8888, true)
			r.setPixels(pixels, 0, width, 0, 0, width, height)
			return r
		}

		fun blend(pixels:IntArray, width:Int, height:Int) {
			for (i in 0 until height) {
				for (j in 0 until width) {
					val pos = width * i + j // 偏移
					val pixel = pixels[pos] // 颜色值
					val alpha = ((pixel.toLong() and 0xFF000000) shr 24).toFloat() // 透明度通道
					val red = ((pixel and 0x00FF0000) shr 16).toFloat() // 红色通道
					val green = ((pixel and 0x0000FF00) shr 8).toFloat() // 绿色通道
					val blue = (pixel and 0x000000FF).toFloat() // 蓝色通道
					pixels[pos] = (0xFF000000 or // 透明度
							((red * alpha / 0xFF).toLong() shl 16) or
							((green * alpha / 0xFF).toLong() shl 8) or
							(blue * alpha / 0xFF).toLong()).toInt()
				}
			}
		}

		@JvmStatic fun removeBackground(bitmap:Bitmap?, dest:Int = Color.TRANSPARENT):Boolean {
			if (bitmap == null) { return false }

			val width = bitmap.getWidth(); val height = bitmap.getHeight()
			val pixels = IntArray(width * height)
			bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

			// blend(pixels, width, height)
			
			// 方形
			val lt = bitmap.getPixel(0, 0); val rt = bitmap.getPixel(width - 1, 0)
			val lb = bitmap.getPixel(0, height - 1); val rb = bitmap.getPixel(width - 1, height - 1)
			if ((lt == rt) && (rt == lb) && (lb == rb) && (lt != dest)) { // 四角颜色一致
				// Log.d("SmallIcon", "removeBackground1($pixels, $width, $height, ${Integer.toHexString(lt)}, ${Integer.toHexString(dest)})")
				removeBackground0(pixels, width, height, lt, dest)
				bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
				return false
			}

			// 圆形
			val map = mutableMapOf<Int, Int>()
			circularScan(pixels, width, height, map, width.toFloat() * 3 / 4, width.toFloat() / 4)
			// Log.d("SmallIcon", "scan() ${map.size}")
			val maxBy = map.maxBy { it.value }
			// maxBy?.key
			// Log.d("SmallIcon", "scan() ${maxBy}")
			if (maxBy != null && maxBy.value > 300) { // TODO
				// Log.d("SmallIcon", "removeBackground1($pixels, $width, $height, ${Integer.toHexString(lt)}, ${Integer.toHexString(dest)})")
				removeColor(pixels, width, height, maxBy.key, dest) // TODO
				bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
				return true
			}

			bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
			return false
		}

		// 环状扫描
		fun circularScan(pixels:IntArray, width:Int, height:Int, map:MutableMap<Int, Int>, from:Float, to:Float = from + 1):Int {
			assert(from > to)
			// Log.d("SmallIcon", "width,height,cx,cy,r = $width, $height, $cx, $cy, $r")

			val cx = width.toFloat() / 2; val cy = height.toFloat() / 2; var count = 0
			for (y in 0 until height) {
				for (x in 0 until width) {
					val hypot = hypot(x - cx, y - cy)
					if (from < hypot || to > hypot ) continue
					// Log.d("SmallIcon", "x,y = $x, $y")
					val pos = width * y + x // 偏移
					val pixel = pixels[pos] // 颜色值
					if (pixel == Color.TRANSPARENT) continue
					count++
					if (map.containsKey(pixel)) {
						var count = map[pixel]
						if (count != null) { map[pixel] = count + 1 }
					} else {
						map[pixel] = 1
					}
				}
			}
			return count
		}

		private val STATE_SWING:Byte = 0.toByte() // 未决
		private val STATE_KEEP:Byte = 1.toByte() // 保留
		private val STATE_KEEPR:Byte = 2.toByte() // 保留
		private val STATE_REMOVE:Byte = 0x11.toByte() // 移除
		private val STATE_REMOVER:Byte = 0x12.toByte() // 移除

		// 从四角开始移除颜色
		fun removeBackground0(pixels:IntArray, width:Int, height:Int, target:Int, dest:Int = Color.TRANSPARENT) {
			val states = ByteArray(width * height)
			states.fill(STATE_SWING)

			// pixels[0] = pixels[width - 1] = pixels[width * (height - 1)] = pixels[width * (height - 1) + width - 1] = Color.TRANSPARENT
			states[0] = STATE_REMOVE // 种子
			states[width - 1] = STATE_REMOVE // 种子
			states[width * (height - 1)] = STATE_REMOVE // 种子
			states[width * (height - 1) + width - 1] = STATE_REMOVE // 种子
			for (y in 0 until height) { // 纵向播种
				val pos0 = width * y // 偏移0
				if (pixels[pos0] == target) { states[pos0] = STATE_REMOVE }
				val pos1 = width * y + width - 1 // 偏移1
				if (pixels[pos1] == target) { states[pos1] = STATE_REMOVE }
			}
			for (pos in 0 until states.size) { // 正向填充
				if ((pos % width) + 1 == width) continue // 避免行末出错
				val pixel = pixels[pos]; val state = states[pos]
				val np = pixels[pos + 1]; val ns = states[pos + 1]
				if ((pixel == np) && (ns == STATE_SWING) && (state == STATE_REMOVE || state == STATE_REMOVER)) {
					states[pos + 1] = STATE_REMOVE
				}
			}
			for (pos in states.size - 1 downTo 0) { // 反向填充
				if ((pos % width) == 0) continue // 避免行首出错
				val pixel = pixels[pos]; val state = states[pos]
				val np = pixels[pos - 1]; val ns = states[pos - 1]
				if ((pixel == np) && (ns == STATE_SWING) && (state == STATE_REMOVE || state == STATE_REMOVER)) {
					states[pos - 1] = STATE_REMOVE
				}
			}
			// for (y in 0 until height) {
			// 	for (x in 0 until width) {
			// 		val pos = width * y + x // 偏移
			// 		val state = states[pos] // 状态
			// 		val pixel = pixels[pos] // 颜色值
			// 	}
			// }
			for (pos in 0 until states.size) {
				if (states[pos] == STATE_REMOVE) {
					pixels[pos] = dest
				}
			}
		}

		// 直接把特定颜色移除
		fun removeColor(pixels:IntArray, width:Int, height:Int, target:Int, dest:Int = Color.TRANSPARENT) {
			val r = ((target and 0x00FF0000) shr 16) // 红色
			val g = ((target and 0x0000FF00) shr 8) // 绿色
			val b = (target and 0x000000FF) // 蓝色
			for (pos in 0 until pixels.size) { // 正向填充
				val pixel = pixels[pos]
				if (pixel == Color.TRANSPARENT) continue
				val red = ((pixel and 0x00FF0000) shr 16) - r // 红色差异
				val green = ((pixel and 0x0000FF00) shr 8) - g // 绿色差异
				val blue = (pixel and 0x000000FF) - b // 蓝色差异
				val diff = red * red + green * green + blue * blue
				if (diff <= 256) {
					pixels[pos] = dest
				}
			}
		}
	}
}