package top.trumeet.common.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.support.v4.util.LruCache;

import top.trumeet.common.utils.ImgUtils;

import static top.trumeet.common.utils.ImgUtils.drawableToBitmap;

/**
 * Author: TimothyZhang023
 * Icon Cache
 * 
 * Code implements port from
 * https://github.com/MiPushFramework/MiPushFramework
 */
public class IconCache {

    private volatile static IconCache cache = null;
    private final LruCache<String, Bitmap> bitmapLruCache;
    private final LruCache<String, Icon> mIconMemoryCaches;
    private final LruCache<String, Integer> appColorCache;

    private IconCache() {
        bitmapLruCache = new LruCache<>(100);
        mIconMemoryCaches = new LruCache<>(100);
        appColorCache = new LruCache<>(100);
        //TODO check cacheSizes is correct ?
    }

    public static IconCache getInstance() {
        if (cache == null) {
            synchronized (IconCache.class) {
                if (cache == null) {
                    cache = new IconCache();
                }
            }
        }
        return cache;
    }

    public Bitmap getRawIconBitmapWithoutLoader(final Context ctx, final String pkg) {
        return bitmapLruCache.get("raw_" + pkg);
    }

    public Bitmap getRawIconBitmap(final Context ctx, final String pkg) {
        return new AbstractCacheAspect<Bitmap>(bitmapLruCache) {
            @Override
            Bitmap gen() {
                try {
                    Drawable icon = ctx.getPackageManager().getApplicationIcon(pkg);
                    return drawableToBitmap(icon);
                } catch (Exception ignored) {
                    return null;
                }
            }
        }.get("raw_" + pkg);
	}
	
	public Icon getIconCache(final Context ctx, final String pkg, final Converter<String, Bitmap> raw,
			final Converter<Bitmap, Bitmap> white, final Converter<Bitmap, Icon> callback) {
		return new AbstractCacheAspect<Icon>(mIconMemoryCaches) {
			@Override
			Icon gen() {
				final Bitmap rawIcon = raw.convert(ctx, pkg);
				final Bitmap whiteIcon = white.convert(ctx, rawIcon);
				return callback.convert(ctx, whiteIcon);
			}
		}.get("white_" + pkg);
	}
	
	public Icon getIconCache(final Context ctx, final String pkg, final Converter<String, Bitmap> raw, final Converter<Bitmap, Bitmap> white) {
		return getIconCache(ctx, pkg, raw, white, IconCache::createIconWithBitmap);
	}
	
	public Icon getIconCache(final Context ctx, final String pkg, final Converter<String, Bitmap> raw) {
		return getIconCache(ctx, pkg, this::getRawIconBitmap, IconCache::whitenBitmap, IconCache::createIconWithBitmap);
	}

    public Icon getIconCache(final Context ctx, final String pkg) {
		return getIconCache(ctx, pkg, this::getRawIconBitmap, IconCache::whitenBitmap, IconCache::createIconWithBitmap);
    }

    public int getAppColor(final Context ctx, final String pkg, final Converter<Bitmap, Integer> callback) {
        return new AbstractCacheAspect<Integer>(appColorCache) {
            @Override
            Integer gen() {
                Bitmap rawIconBitmap = getRawIconBitmap(ctx, pkg);
                if (rawIconBitmap == null) {
                    return -1;
                }
                return callback.convert(ctx, rawIconBitmap);
            }
        }.get(pkg);
	}
	
	private static Bitmap whitenBitmap(final Context ctx, final Bitmap b) {
		if (b == null) {
			return null;
		}

		//scaleImage to 64dp
		int dip2px = dip2px(ctx, 64);
		return ImgUtils.scaleImage(ImgUtils.convertToTransparentAndWhite(b), dip2px, dip2px);
	}

	private static Icon createIconWithBitmap(final Context ctx, final Bitmap b) { return Icon.createWithBitmap(b); }

    public static class WhiteIconProcess implements Converter<Bitmap, Bitmap> {
        @Override
        public Bitmap convert(Context ctx, Bitmap b) {
            if (b == null) {
                return null;
            }

            //scaleImage to 64dp
            int dip2px = dip2px(ctx, 64);
            return ImgUtils.scaleImage(ImgUtils.convertToTransparentAndWhite(b), dip2px, dip2px);
        }
    }

    public interface Converter<T,R> {
        R convert(Context ctx, T b);
    }

    public static int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

}
