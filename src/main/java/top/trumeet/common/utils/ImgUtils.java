package top.trumeet.common.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.VectorDrawable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Code implements port from
 * https://www.cnblogs.com/Imageshop/p/3307308.html
 * AND
 * http://imagej.net/Auto_Threshold
 *
 * @author zts
 */
public class ImgUtils {
	private static int NUM_256 = 256;

	/**
	 * 把图片切掉一圈.
	 * 
	 * @param color
	 * @param width
	 * @param height
	 * @param pixels
	 * @param rExpand
	 */
	private static void clipImgToCircle(int color, int width, int height, int[] pixels, int rExpand) {

		int r = (width < height ? width : height) / 2 + rExpand;

		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {

				if ((i - width / 2) * (i - width / 2) + (j - height / 2) * (j - height / 2) > r * r) {
					pixels[width * i + j] = color;
				}

			}
		}
	}

	/**
	 * 把图片转换为二值（白和透明）
	 */
	public static Bitmap convertToTransparentAndWhite(Bitmap bitmap, float density) {
		int r = (int) (-8 * density);
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		int[] pixels = new int[width * height];
		bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
		int calculateThreshold = calculateThreshold(pixels, width, height, r);

		int whiteCnt = 0;
		int blackCnt = 0;

		// clipImgToCircle(Color.TRANSPARENT, width, height, pixels, r);

		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				if (pixels[width * i + j] == Color.TRANSPARENT) {
					// blackCnt++;
					continue;
				}
				int dot = pixels[width * i + j];
				int red = ((dot & 0x00FF0000) >> 16);
				int green = ((dot & 0x0000FF00) >> 8);
				int blue = (dot & 0x000000FF);
				int gray = (int) ((float) red * 0.3 + (float) green * 0.59 + (float) blue * 0.11);

				if (gray > calculateThreshold) {
					pixels[width * i + j] = Color.BLACK;
					blackCnt++;
				} else {
					pixels[width * i + j] = Color.WHITE;
					whiteCnt++;
				}
			}
		}

		// clipImgToCircle(Color.TRANSPARENT, width, height, pixels, r);

		if (whiteCnt > blackCnt) {
			// WHITE => TRANSPARENT
			for (int i = 0; i < height; i++) {
				for (int j = 0; j < width; j++) {
					int dot = pixels[width * i + j];
					if (dot == Color.WHITE) {
						pixels[width * i + j] = Color.TRANSPARENT;
					} else if (dot == Color.BLACK) {
						pixels[width * i + j] = Color.WHITE;
					}
				}
			}
		} else {
			// BLACK => TRANSPARENT
			for (int i = 0; i < height; i++) {
				for (int j = 0; j < width; j++) {
					int dot = pixels[width * i + j];
					if (dot == Color.BLACK) {
						pixels[width * i + j] = Color.TRANSPARENT;
					// } else if (dot == Color.WHITE) {
					//     pixels[width * i + j] = Color.WHITE;
					}
				}
			}
		}

		clipImgToCircle(Color.TRANSPARENT, width, height, pixels, r);

		//todo use bwareaopen
		// denoiseWhitePoint(width, height, pixels, (int)density);

		int top = 0;
		int left = 0;
		int right = 0;
		int bottom = 0;

		for (int h = 0; h < height; h++) {
			boolean holdBlackPix = false;
			for (int w = 0; w < width; w++) {
				if (pixels[width * h + w] != Color.TRANSPARENT) {
					holdBlackPix = true;
					break;
				}
			}

			if (holdBlackPix) {
				break;
			}
			top++;
		}

		for (int w = 0; w < width; w++) {
			boolean holdBlackPix = false;
			for (int h = 0; h < height; h++) {
				if (pixels[width * h + w] != Color.TRANSPARENT) {
					holdBlackPix = true;
					break;
				}
			}
			if (holdBlackPix) {
				break;
			}
			left++;
		}

		for (int w = width - 1; w >= left; w--) {
			boolean holdBlackPix = false;
			for (int h = 0; h < height; h++) {
				if (pixels[width * h + w] != Color.TRANSPARENT) {
					holdBlackPix = true;
					break;
				}
			}
			if (holdBlackPix) {
				break;
			}
			right++;
		}

		for (int h = height - 1; h >= top; h--) {
			boolean holdBlackPix = false;
			for (int w = 0; w < width; w++) {
				if (pixels[width * h + w] != Color.TRANSPARENT) {
					holdBlackPix = true;
					break;
				}
			}
			if (holdBlackPix) {
				break;
			}
			bottom++;
		}

		int diff = (bottom + top) - (left + right);
		if (diff > 0) {
			bottom -= (diff / 2);
			top -= (diff / 2);

			bottom = bottom < 0 ? 0 : bottom;
			top = top < 0 ? 0 : top;

		} else if (diff < 0) {
			left += (diff / 2);
			right += (diff / 2);
			left = left < 0 ? 0 : left;
			right = right < 0 ? 0 : right;
		}


		int cropHeight = height - bottom - top;
		int cropWidth = width - left - right;

		int padding = (cropHeight + cropWidth) / 16;

		int[] newPix = new int[cropWidth * cropHeight];

		int i = 0;
		for (int h = top; h < top + cropHeight; h++) {
			for (int w = left; w < left + cropWidth; w++) {
				newPix[i++] = pixels[width * h + w];
			}
		}

		try {
			Bitmap newBmp = Bitmap.createBitmap(cropWidth + padding * 2, cropHeight + padding * 2, Bitmap.Config.ARGB_8888);
			newBmp.setPixels(newPix, 0, cropWidth, padding, padding, cropWidth, cropHeight);
			
			return newBmp;
		} catch (java.lang.IllegalArgumentException ex) {
			Log.d("SmallIcon", "width, height " + width + ", " + height + " " + diff);
			Log.d("SmallIcon", width + " " + left + " " + right);
			Log.d("SmallIcon", height + " " + bottom + " " + top);
			Log.d("SmallIcon", width + ", " + height + " " + cropWidth + ", " + cropHeight + " " + padding);
			return null;
		}
	}

	/**
	 * 去噪点
	 * 
	 * @param width
	 * @param height
	 * @param pixels
	 * @param exThre
	 */
	private static void denoiseWhitePoint(int width, int height, int[] pixels, int exThre) {
		for (int i = 1; i < height - 1; i++) {
			for (int j = 1; j < width - 1; j++) {
				int[] dots = new int[]{
						getPixel(width, pixels, i - 1, j - 1),
						getPixel(width, pixels, i - 1, j),
						getPixel(width, pixels, i - 1, j + 1),
						getPixel(width, pixels, i, j - 1),
//                        pixels[width * i + j],
						getPixel(width, pixels, i, j + 1),
						getPixel(width, pixels, i + 1, j - 1),
						getPixel(width, pixels, i + 1, j),
						getPixel(width, pixels, i + 1, j + 1)};

				int whCnt = 0;
				int trCnt = 0;

				for (int dot : dots) {
					if (dot == Color.WHITE) {
						whCnt++;
					} else {
						trCnt++;
					}
				}

				if (trCnt > (dots.length - exThre)) {
					pixels[width * i + j] = Color.TRANSPARENT;
				}
			}
		}
	}

	/**
	 * 获取像素池中的一个像素
	 */
	private static int getPixel(int width, int[] pixels, int i, int j) {
		return pixels[width * i + j];
	}

	/**
	 * 获取灰度直方图
	 * 
	 * @param pixels
	 * @param width
	 * @param height
	 * @param histogram
	 */
	private static void getGreyHistogram(int[] pixels, int width, int height, int[] histogram) {
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				int dot = pixels[width * y + x];
				int alpha = ((dot & 0xFF000000) >> 24);
				if (alpha == 0xFF) continue;
				int red = ((dot & 0x00FF0000) >> 16);
				int green = ((dot & 0x0000FF00) >> 8);
				int blue = (dot & 0x000000FF);
				int gray = (int) ((float) red * 0.3 + (float) green * 0.59 + (float) blue * 0.11);
				histogram[gray]++;
			}
		}
	}

	/**
	 * 直方图是否是双峰
	 * 
	 * @param histogram
	 * @return
	 */
	private static boolean isDimodal(double[] histogram) {
		// 对直方图的峰进行计数，只有峰数位2才为双峰
		int count = 0;
		for (int i = 1; i < (NUM_256 - 1); i++) {
			if (histogram[i - 1] < histogram[i] && histogram[i + 1] < histogram[i]) {
				count++;
				if (count > 2) {
					return false;
				}
			}
		}
		return count == 2;
	}

	/**
	 * 计算阈值
	 * 
	 * @param pixels
	 * @param width
	 * @param height
	 * @param rExpand
	 * @return
	 */
	private static int calculateThreshold(int[] pixels, int width, int height, int rExpand) {

		clipImgToCircle(Color.TRANSPARENT, width, height, pixels, rExpand);

		int[] histogram = new int[NUM_256];
		getGreyHistogram(pixels, width, height, histogram);

		ArrayList<Integer> thresholds = new ArrayList<>();
		thresholds.add(calculateThresholdByOSTU(width * height, histogram));
		thresholds.add(calculateThresholdByMinimum(histogram));
		thresholds.add(calculateThresholdByMean(histogram));

		Collections.sort(thresholds);

		return (thresholds.get(thresholds.size() - 1) * 3 + thresholds.get(thresholds.size() - 2)) / 4;
	}

	/**
	 * OSTU法计算阈值
	 * 
	 * @param total
	 * @param histogram
	 * @return
	 */
	private static int calculateThresholdByOSTU(int total, int[] histogram) {

		double sum = 0;
		for (int i = 0; i < NUM_256; i++) {
			sum += i * histogram[i];
		}

		double sumB = 0;
		int wB = 0;

		double varMax = 0;
		int threshold = 0;

		for (int i = 0; i < NUM_256; i++) {
			wB += histogram[i];
			if (wB == 0) {
				continue;
			}
			int wF = total - wB;

			if (wF == 0) {
				break;
			}

			sumB += (double) (i * histogram[i]);
			double mB = sumB / wB;
			double mF = (sum - sumB) / wF;

			double varBetween = (double) wB * (double) wF * (mB - mF) * (mB - mF);

			if (varBetween > varMax) {
				varMax = varBetween;
				threshold = i;
			}
		}

		return threshold;
	}

	/**
	 * 最小值法计算阈值
	 */
	private static int calculateThresholdByMinimum(int[] histogram) {

		int y, iter = 0;
		double[] histgramc = new double[NUM_256];
		double[] histgramcc = new double[NUM_256];
		for (y = 0; y < NUM_256; y++) {
			histgramc[y] = histogram[y];
			histgramcc[y] = histogram[y];
		}

		while (!isDimodal(histgramcc)) {
			histgramcc[0] = (histgramc[0] + histgramc[0] + histgramc[1]) / 3;
			for (y = 1; y < (NUM_256 - 1); y++) {
				histgramcc[y] = (histgramc[y - 1] + histgramc[y] + histgramc[y + 1]) / 3;
			}
			histgramcc[255] = (histgramc[254] + histgramc[255] + histgramc[255]) / 3;
			System.arraycopy(histgramcc, 0, histgramc, 0, NUM_256);
			iter++;
			if (iter >= 1000) {
				return -1;
			}
		}
		// 阈值极为两峰之间的最小值
		boolean peakFound = false;
		for (y = 1; y < (NUM_256 - 1); y++) {
			if (histgramcc[y - 1] < histgramcc[y] && histgramcc[y + 1] < histgramcc[y]) {
				peakFound = true;
			}
			if (peakFound && histgramcc[y - 1] >= histgramcc[y] && histgramcc[y + 1] >= histgramcc[y]) {
				return y - 1;
			}
		}
		return -1;
	}

	/**
	 * 平均值法计算阈值
	 * @param histogram
	 * @return
	 */
	private static int calculateThresholdByMean(int[] histogram) {

		int sum = 0, amount = 0;
		for (int i = 0; i < NUM_256; i++) {
			amount += histogram[i];
			sum += i * histogram[i];
		}
		return sum / amount;
	}

	/**
	 * 缩放Bitmap
	 */
	public static Bitmap scaleImage(Bitmap bitmap, Rect dest, boolean recycle) {
		if (bitmap == null) {
			return null;
		}
		int w = bitmap.getWidth(),
			h = bitmap.getHeight(),
			width = dest.width(),
			height = dest.height();
		if (dest.left == 0 && dest.top == 0 && w == width && h == height) { return bitmap; }
		Log.d("SmallIcon", "scale dest " + dest + " " + width + ", " + height + " " + w + ", " + h);
		float scaleWidth = ((float) width) / w;
		float scaleHeight = ((float) height) / h;
		Log.d("SmallIcon", "scale " + scaleWidth + ", " + scaleHeight);
		Matrix matrix = new Matrix();
		matrix.postScale(scaleWidth, scaleHeight);
		Bitmap r = Bitmap.createBitmap(bitmap, dest.left, dest.top, w, h, matrix, true);
		if (recycle && !bitmap.isRecycled()) {
			bitmap.recycle();
		}
		Log.d("SmallIcon", "scaled " + r.getWidth() + ", " + r.getHeight());
		return r;
	}


}
