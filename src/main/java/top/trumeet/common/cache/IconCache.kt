package top.trumeet.common.cache

import kotlin.math.max
import kotlin.math.hypot

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
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
					// Log.d(T, "foreground $pkg")
					val icon = ctx.getPackageManager().getApplicationIcon(pkg)
					var bitmap:Bitmap?
					if (icon is AdaptiveIconDrawable) {
						// Log.d(T, "foreground $pkg $SIZE")
						val recommand = { width:Int -> if (width > 0) width * 72 / 108 else 72 }
						val slice = { width:Int -> if (width > 0) width * -18 / 72 else -18 }
						bitmap = render(icon.getForeground(), recommand, recommand,
								setBounds = { d, width, height -> val w = slice(width); val h = slice(height); d.setBounds(w, h, width - w, height - h) })
					} else {
						// Log.d(T, "legacy foreground $pkg $SIZE ${icon.getIntrinsicWidth()}, ${icon.getIntrinsicHeight()}")
						bitmap = render(icon)
					}
					val width = bitmap.getWidth(); val height = bitmap.getHeight()
					val pixels = IntArray(width * height)
					bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
					removeBackground(pkg, pixels, width, height)
					bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
					var top = height; var bottom = 0
					var left = width; var right = 0
					top@ for (i in 0 until height) {
						for (j in 0 until width) {
							val pos = width * i + j // 偏移
							val pixel = pixels[pos] // 颜色值
							if (pixel != Color.TRANSPARENT) { top = i; break@top }
						}
					}
					bottom@ for (i in height - 1 downTo 0) {
						for (j in 0 until width) {
							val pos = width * i + j // 偏移
							val pixel = pixels[pos] // 颜色值
							if (pixel != Color.TRANSPARENT) { bottom = i; break@bottom }
						}
					}
					left@ for (j in 0 until width) {
						for (i in 0 until height) {
							val pos = width * i + j // 偏移
							val pixel = pixels[pos] // 颜色值
							if (pixel != Color.TRANSPARENT) { left = j; break@left }
						}
					}
					right@ for (j in width - 1 downTo 0) {
						for (i in 0 until height) {
							val pos = width * i + j // 偏移
							val pixel = pixels[pos] // 颜色值
							if (pixel != Color.TRANSPARENT) { right = j; break@right }
						}
					}
					// if (pkg == "com.apple.android.music") {
					// 	Log.d(T, "l,r,t,b = $left,$right,$top,$bottom")
					// }
					if ((left != 0 || right != width - 1 || top != 0 || bottom != height - 1) &&
							(left < right && top < bottom)) {
						val w = right - left; val h = bottom - top
						val side = max(w, h)
						val l = left - (side - w) / 2
						val t = top - (side - h) / 2
						val temp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
						val canvas = Canvas(temp);
						canvas.drawBitmap(bitmap, Rect(l, t, l + side, t + side), Rect(0, 0, side, side), null)
						bitmap.recycle()
						bitmap = temp
					}
					return bitmap
				} catch (ignored:Exception) {
					Log.d(T, "foreground", ignored)
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
						val recommand = { width:Int -> if (width > 0) width * 72 / 108 else 72 }
						val slice = { width:Int -> if (width > 0) width * -18 / 72 else -18 }
						render(icon.getBackground(), recommand, recommand,
								setBounds = { d, width, height -> val w = slice(width); val h = slice(height); d.setBounds(w, h, width - w, height - h) })
					} else {
						render(icon)
					}
				} catch (ignored:Exception) {
					Log.d(T, "background", ignored)
					return null
				}
			}
		}.get(pkg)
	}

	@JvmOverloads fun getIcon(ctx:Context, pkg:String,
			gen:((Context, String) -> Bitmap?) = ({ ctx, pkg -> getIconForeground(ctx, pkg) }),
			whiten:((Context, Bitmap?) -> Bitmap?) = ({ _, b -> alphaize(pkg, b) }),
			iconize:((Context, Bitmap?) -> Icon?) = ({ _, b -> (if (b != null) Icon.createWithBitmap(b) else null) })):Icon? {
		return object:AbstractCacheAspect<Icon?>(iconCache) {
			override fun gen():Icon? {
				var bitmap = gen(ctx, pkg)
				if (bitmap == null) { return null }
				bitmap = whiten(ctx, bitmap)
				return iconize(ctx, bitmap)
			}
		}.get(pkg)
	}

	@JvmOverloads fun getMiPushIcon(resources:Resources, iconId:Int, pkg:String,
			gen:((Resources, Int) -> Bitmap?) = { resources, iconId -> render(resources.getDrawable(iconId, null)) },
			whiten:((Bitmap?) -> Bitmap?) = { b -> alphaize(pkg, b) },
			iconize:((Bitmap?) -> Icon?) = { b -> (if (b != null) Icon.createWithBitmap(b) else null) }):Icon? {
		return object:AbstractCacheAspect<Icon?>(mipushCache) {
			override fun gen():Icon? {
				var bitmap = gen(resources, iconId)
				if (bitmap == null) { return null }
				bitmap = whiten(bitmap)
				val icon = iconize(bitmap)
				// Log.d(T, "icon: $icon")
				return icon
			}
		}.get(pkg)
	}

	@JvmOverloads fun getAppColor(ctx:Context, pkg:String,
			convert:((Context, Bitmap?) -> Int) = { _, b -> backgroundColor(pkg, b)}):Int {
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
		private val T = "SmallIcon"
		@JvmField public val SIZE = 144 // 建议的边长
		private val ADAPTIVE_CANVAS = Rect(-18, -18, 90, 90) // TODO density
		@JvmStatic public val BOUNDS = Rect(0, 0, 72, 72) // TODO density

		@JvmStatic fun getInstance():IconCache {
			return Holder.instance
		}

		/**
		 * 转换Drawable为Bitmap
		 *
		 * @param drawable
		 *
		 * @return
		 */
		@JvmStatic fun render(drawable:Drawable,
				recommandWidth:((Int) -> Int) = { width -> if (width > 0) width else SIZE },
				recommandHeight:((Int) -> Int) = { height -> if (height > 0) height else SIZE },
				createBitmap:((Int, Int) -> Bitmap) = { width, height -> Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) },
				setBounds:((Drawable, Int, Int) -> Unit) = { d, width, height -> d.setBounds(0, 0, width, height) }):Bitmap {
			var width = recommandWidth(drawable.getIntrinsicWidth())
			var height = recommandHeight(drawable.getIntrinsicHeight())
			val bitmap = createBitmap(width, height)
			val canvas = Canvas(bitmap)
			setBounds(drawable, width, height)
			drawable.draw(canvas)
			return bitmap
		}

		@JvmStatic fun whitenBitmap(ctx:Context, b:Bitmap?):Bitmap? {
			if (b == null) { return null }
			return whiten(ctx, b)
		}

		@JvmStatic fun whiten(ctx:Context, b:Bitmap):Bitmap {
			val density = ctx.getResources().getDisplayMetrics().density
			return ImgUtils.convertToTransparentAndWhite(b, density)
		}

		val ALPHA_MAX:Short = 0xFF
		val ALPHA_MIN:Short = 0x3F
		// 将图像灰度化然后转化为透明度形式
		@JvmStatic @JvmOverloads fun alphaize(pkg:String, bitmap:Bitmap?, autoLevel:Boolean = true):Bitmap? {
			if (bitmap == null) { return null }

			val width = bitmap.getWidth()
			val height = bitmap.getHeight()
			val temp = bitmap.copy(Bitmap.Config.ARGB_8888, true)
			try {
				val pixels = IntArray(width * height)
				temp.getPixels(pixels, 0, width, 0, 0, width, height)

				val alphas = ShortArray(width * height)
				val map = mutableMapOf<Short, Int>()
				for (pos in 0 until pixels.size) {
					val pixel = pixels[pos] // 颜色值
					val alpha = ((pixel.toLong() and 0xFF000000) shr 24).toInt() // 透明度通道
					if (alpha == 0) continue
					val red = ((pixel and 0x00FF0000) shr 16).toFloat() // 红色通道
					val green = ((pixel and 0x0000FF00) shr 8).toFloat() // 绿色通道
					val blue = (pixel and 0x000000FF).toFloat() // 蓝色通道
					var gray = (alpha * (red * 0.3 + green * 0.59 + blue * 0.11) / 0xFF).toShort() // 混合为明亮度
					if (gray == 0.toShort()) gray = 1 // 强制加1，避免与透明背景混同
					alphas[pos] = gray
					if (map.containsKey(gray)) {
						val count = map[gray]
						if (count != null) map[gray] = count + 1
					} else {
						map[gray] = 1
					}
				}
				if (autoLevel) {
					val threshold = alphas.size * 0.01
					val filtered = map.filter { it.value > threshold }
					val max = filtered.maxBy { it.key }; val min = map.minBy { it.key }
					// if (pkg.startsWith("com.lastpass")) {
					// 	Log.d(T, "autoLevel $pkg max,min = $max, $min")
					// 	Log.d(T, "autoLevel $pkg threshold,map,filtered = $threshold, ${map.size}, ${filtered.size}")
					// }
					if (max != null && min != null && max.key <= ALPHA_MAX) {
						if (max.key > min.key) {
							val q = (ALPHA_MAX - ALPHA_MIN).toFloat() / (max.key - min.key)
							// if (pkg.startsWith("com.lastpass")) {
							// 	Log.d(T, "autoLevel $pkg q = $q, ${(max.key - min.key) * q + min.key}")
							// }
							for (key in map.keys) {
								map[key] = if (key >= max.key) {
									ALPHA_MAX.toInt()
								} else if (key >= min.key) {
									((key - min.key) * q + ALPHA_MIN).toInt()
								} else {
									ALPHA_MIN.toInt()
								}
							}
						} else {
							for (key in map.keys) {
								map[key] = if (key >= max.key) {
									ALPHA_MAX.toInt()
								} else {
									ALPHA_MIN.toInt()
								}
							}
						}
					}
				}
				// if (pkg.startsWith("com.lastpass")) {
				// 	Log.d(T, "autoLevel $pkg map = $map")
				// }
				for (pos in 0 until pixels.size) {
					val alpha = alphas[pos]
					val a = map[alpha] ?: 0
					pixels[pos] = ((a shl 24) or 0xFFFFFF) // 以明亮度作为透明度
				}

				val r = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
				r.setPixels(pixels, 0, width, 0, 0, width, height)
				return r
			} finally { temp.recycle() }
		}

		// 混合颜色
		fun blend(pixels:IntArray) {
			for (pos in 0 until pixels.size) {
				val pixel = pixels[pos] // 颜色值
				if (pixel == Color.TRANSPARENT) continue
				val alpha = ((pixel.toLong() and 0xFF000000) shr 24).toFloat() // 透明度通道
				val red = ((pixel and 0x00FF0000) shr 16).toFloat() // 红色通道
				val green = ((pixel and 0x0000FF00) shr 8).toFloat() // 绿色通道
				val blue = (pixel and 0x000000FF).toFloat() // 蓝色通道
				pixels[pos] = (0xFF000000 or // 透明度
						((red * alpha / 0xFF + (0xFF - alpha)).toLong() shl 16) or
						((green * alpha / 0xFF + (0xFF - alpha)).toLong() shl 8) or
						(blue * alpha / 0xFF + (0xFF - alpha)).toLong()).toInt()
			}
		}

		fun blend(pixel:Int, background:Int):Int {
			if (pixel == Color.TRANSPARENT) return background
			val alpha = ((pixel.toLong() and 0xFF000000) shr 24) // 透明度通道
			// val a1 = ((background.toLong() and 0xFF000000) shr 24) // 透明度通道
			val r0 = ((pixel and 0x00FF0000) shr 16) // 红色通道
			val r1 = ((background and 0x00FF0000) shr 16) // 红色通道
			val g0 = ((pixel and 0x0000FF00) shr 8) // 绿色通道
			val g1 = ((background and 0x0000FF00) shr 8) // 绿色通道
			val b0 = (pixel and 0x000000FF) // 蓝色通道
			val b1 = (background and 0x000000FF) // 蓝色通道
			return (0xFF000000 or // 透明度
					(((r0 * alpha + r1 * (0xFF - alpha)) / 0xFF).toLong() shl 16) or
					(((g0 * alpha + g1 * (0xFF - alpha)) / 0xFF).toLong() shl 8) or
					((b0 * alpha + b1 * (0xFF - alpha)) / 0xFF).toLong()).toInt()
		}

		@JvmStatic fun removeBackground(pkg:String, pixels:IntArray, width:Int, height:Int, dest:Int = Color.TRANSPARENT):Boolean {
			// blend(pixels)
			
			// 方形
			// val lt = bitmap.getPixel(0, 0); val rt = bitmap.getPixel(width - 1, 0)
			// val lb = bitmap.getPixel(0, height - 1); val rb = bitmap.getPixel(width - 1, height - 1)
			// if ((lt == rt) && (rt == lb) && (lb == rb) && (lt != dest)) { // 四角颜色一致
			// 	// Log.d(T, "removeBackground1($pixels, $width, $height, ${Integer.toHexString(lt)}, ${Integer.toHexString(dest)})")
			// 	// floodFill(pixels, width, height, lt, dest)
			// 	removeColor(pixels, lt, dest)
			// 	bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
			// 	return false
			// }

			// 圆形
			// pixels.fill(0xFFFFFFFF.toInt())
			// 		// val hypot = hypot(x - cx, y - cy)
			// 		// if (outside < hypot || inside > hypot ) continue
			val outside = width.toFloat() / 2; val inside = width.toFloat() / 4
			assert(outside > inside)
			// circularFill(pixels, width, height) { dx, dy, pixel ->
			// 	val hypot = hypot(dx, dy)
			// 	// Log.d(T, "hypot = $hypot dx,dy = $dx, $dy")
			// 	(outside < hypot || inside > hypot)
			// }
			val map = mutableMapOf<Int, Int>()
			val total = circularScan(pixels, width, height, map) { dx, dy, pixel ->
				val hypot = hypot(dx, dy)
				pixel != Color.TRANSPARENT || outside < hypot || inside > hypot
			}
			// Log.d(T, "$pkg circularScan() ${map.size} / $total")
			if (map.size > 1) {
				val maxBy = map.maxBy { it.value }
				if (maxBy != null) {
					// maxBy?.key
					// Log.d(T, "$pkg circularScan() ${Integer.toHexString(maxBy!!.key)} = ${maxBy!!.value} ${maxBy!!.value.toFloat() / total}")
					val q = maxBy.value.toFloat() / total
					if (q > 0.2 && q < 0.8) {
						// Log.d(T, "$pkg removeBackground1($pixels, ${Integer.toHexString(maxBy.key)}, ${Integer.toHexString(dest)})")
						removeColor(pkg, pixels, maxBy.key, dest) // TODO 考虑改用播种加洪泛式的颜色移除
						return true
					}
				}
			}

			return false
		}

		// 环状填充
		fun circularFill(pixels:IntArray, width:Int, height:Int, included:(Float,Float,Int) -> Boolean) {
			Log.d(T, "circularFill(width,height = $width, $height)")

			val cx = width.toFloat() / 2; val cy = height.toFloat() / 2
			for (y in 0 until height) {
				for (x in 0 until width) {
					val pos = width * y + x // 偏移
					if (!included(x - cx, y - cy, pixels[pos])) continue
					pixels[pos] = 0xFFFF0000.toInt()
				}
			}
		}

		// 环状扫描
		fun circularScan(pixels:IntArray, width:Int, height:Int, colorMap:MutableMap<Int, Int>, included:(Float,Float,Int) -> Boolean):Int {
			// Log.d(T, "circularScan(width,height = $width, $height)")

			val cx = width.toFloat() / 2; val cy = height.toFloat() / 2; var count = 0
			for (y in 0 until height) {
				for (x in 0 until width) {
					val pos = width * y + x // 偏移
					val pixel = pixels[pos]
					if (!included(x - cx, y - cy, pixel)) continue
					count++
					if (colorMap.containsKey(pixel)) {
						var count = colorMap[pixel]
						if (count != null) { colorMap[pixel] = count + 1 }
					} else {
						colorMap[pixel] = 1
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

		// 从四角开始洪泛法移除相同颜色
		fun floodFill(pixels:IntArray, width:Int, height:Int, target:Int, dest:Int = Color.TRANSPARENT) {
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

		fun _h(_int:Int) = Integer.toHexString(_int)

		// 颜色容差
		private val DIFF = 1 shl 13
		// 直接把特定颜色移除
		fun removeColor(pkg:String, pixels:IntArray, target:Int, dest:Int = Color.TRANSPARENT) {
			val r = ((target and 0x00FF0000) shr 16) // 红色
			val g = ((target and 0x0000FF00) shr 8) // 绿色
			val b = (target and 0x000000FF) // 蓝色
			for (pos in 0 until pixels.size) { // 正向填充
				var pixel = pixels[pos]
				val alpha = ((pixel.toLong() and 0xFF000000) ushr 24).toInt() // 透明度通道
				if (alpha == 0) continue
				pixel = blend(pixel, target)
				val dr = ((pixel and 0x00FF0000) shr 16) - r // 红色差异
				val dg = ((pixel and 0x0000FF00) shr 8) - g // 绿色差异
				val db = (pixel and 0x000000FF) - b // 蓝色差异
				val diff = dr * dr + dg * dg + db * db
				// if (pkg == "com.apple.android.music" && diff > DIFF) {
				// 	Log.d(T, "$pkg $pos $dr($r) $dg($g) $db($b) ${_h(pixel)} $diff")
				// }
				if (diff <= DIFF) {
					pixels[pos] = dest
				}
			}
		}

		@JvmStatic fun backgroundColor(pkg:String, bitmap:Bitmap?):Int {
			if (bitmap == null) { return Color.BLACK }

			val width = bitmap.getWidth(); val height = bitmap.getHeight()
			val temp = bitmap.copy(Bitmap.Config.ARGB_8888, true)
			try {
				val pixels = IntArray(width * height)
				temp.getPixels(pixels, 0, width, 0, 0, width, height)
				val map = mutableMapOf<Int, Int>()
				for (pos in 0 until pixels.size) {
					val pixel = pixels[pos]
					val alpha = ((pixel.toLong() and 0xFF000000) shr 24).toInt() // 透明度通道
					if (alpha == 0) continue
					val red = ((pixel and 0x00FF0000) shr 16).toFloat() // 红色通道
					val green = ((pixel and 0x0000FF00) shr 8).toFloat() // 绿色通道
					val blue = (pixel and 0x000000FF).toFloat() // 蓝色通道
					if (red == green && green == blue) continue
					val rgb = pixel and 0xFFFFFF // RGB颜色值
					if (map.containsKey(rgb)) {
						val count = map[rgb]
						if (count != null) map[rgb] = count + 1
					} else {
						map[rgb] = 1
					}
				}
				val filtered = map.filter { it.key != 0 && it.key != 0xFFFFFF }  // 预先剔除黑色和白色
				// if (pkg.startsWith("com.apple")) {
				// 	val view = map.filter { it.value > 10 }.map { "${_h(it.key)} = ${it.value}" }
				// 	Log.d(T, "backgroundColor filtered $view") 
				// }
				val max = filtered.maxBy { it.value } // 获得最多的颜色
				return if (max != null) {
					if (pkg.startsWith("com.apple")) { Log.d(T, "backgroundColor max ${_h(max.key)} = ${max.value}") }
					(max.key.toLong() or 0xFF000000).toInt()
				} else { Color.BLACK }
			} finally { temp.recycle() }
		}
	}
}