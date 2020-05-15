package com.notxx.icon;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import top.trumeet.common.cache.IconCache;
import top.trumeet.common.utils.ImgUtils;

public class MainActivity extends Activity {
	private class PackagesAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {
		private final IconCache cache = IconCache.getInstance();
		private final PackageManager manager;
		private final List<Map.Entry<CharSequence, ActivityInfo>> infos = new LinkedList<>();
		private final Context context;

		PackagesAdapter(PackageManager manager, Context context) {
			// manager
			this.manager = manager;
			// infos
			Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
			mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
			List<ResolveInfo> query = manager.queryIntentActivities(mainIntent, 0);
			SortedMap<CharSequence, ActivityInfo> map = new TreeMap<>();
			for (ResolveInfo info : query) {
				ActivityInfo ai = info.activityInfo;
				CharSequence label = manager.getApplicationLabel(ai.applicationInfo);
				map.put(label, ai);
			}
			for (Map.Entry<CharSequence, ActivityInfo> entry : map.entrySet()) {
				this.infos.add(entry);
			}
			this.context = context;
		}

		public void onItemClick(AdapterView parent, View v, int position, long id) {
			Map.Entry<CharSequence, ActivityInfo> item = getItem(position);
			CharSequence label = item.getKey();
			ActivityInfo info = item.getValue();
			// Log.d("SmallIcon", "item click");
			Notification.Builder n = new Notification.Builder(MainActivity.this, CHANNEL_ID)
					.setSmallIcon(R.drawable.default_notification_icon)
					.setContentTitle(label)
					.setContentText(info.packageName);
			Icon cached = cache.getIcon(MainActivity.this, info.packageName);
			if (cached != null) { n.setSmallIcon(cached); }
			int color = cache.getAppColor(MainActivity.this, info.packageName);
			if (color != -1) {
				n.setColor(color);
				n.setColorized(false);
			}
			NotificationManager notificationManager = getSystemService(NotificationManager.class);
			notificationManager.notify(notificationId++, n.build());
		}

		@Override
		public int getCount() {
			return this.infos.size();
		}

		@Override
		public Map.Entry<CharSequence, ActivityInfo> getItem(int position) {
			return this.infos.get(position);
		}

		@Override
		public long getItemId(int position) {
			return this.infos.get(position).hashCode();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = null;

			// 如果convertView不为空则复用
			if (convertView == null) {
				view = View.inflate(MainActivity.this, R.layout.app_info, null);
			}else {
				view = convertView;
			}

			Map.Entry<CharSequence, ActivityInfo> item = getItem(position);
			CharSequence label = item.getKey();
			ActivityInfo info = item.getValue();
			// applicationLabel
			TextView appName = (TextView) view.findViewById(R.id.appName);
			appName.setText(label);
			// applicationIcon
			ImageView appIcon = (ImageView) view.findViewById(R.id.appIcon);
			appIcon.setImageDrawable(manager.getApplicationIcon(info.applicationInfo));
			// applicationLogo
			ImageView appLogo = (ImageView) view.findViewById(R.id.appLogo);
			appLogo.setImageDrawable(manager.getApplicationLogo(info.applicationInfo));
			// embedIcon
			ImageView embedIcon = (ImageView) view.findViewById(R.id.embed);
			// mipushIcon
			ImageView mipushIcon = (ImageView) view.findViewById(R.id.mipush);
			// background
			ImageView background = (ImageView) view.findViewById(R.id.background);
			// foreground
			ImageView foreground = (ImageView) view.findViewById(R.id.foreground);
			// whiten
			ImageView whiten = (ImageView) view.findViewById(R.id.whiten);
			// gen
			ImageView gen = (ImageView) view.findViewById(R.id.gen);
			try {
				final Context appContext = createPackageContext(info.packageName, 0);
				int iconId = 0, colorId = 0;
				// embedIcon
				final String key = info.packageName.toLowerCase().replaceAll("\\.", "_");
				if (context != null && (iconId  = context.getResources().getIdentifier(key, "drawable", RES_PACKAGE)) != 0) // has icon
					embedIcon.setImageIcon(Icon.createWithResource(RES_PACKAGE, iconId));
				else
					embedIcon.setImageIcon(null);
				if (context != null && (colorId  = context.getResources().getIdentifier(key, "string", RES_PACKAGE)) != 0) // has icon
					embedIcon.setColorFilter(Color.parseColor(context.getResources().getString(colorId)));
				// mipushIcon
				// Log.d("SmallIcon", "appResources: " + appContext.getResources());
				if ((iconId  = appContext.getResources().getIdentifier(CachedSmallIconDecorator.MIPUSH_SMALL_ICON, "drawable", info.packageName)) != 0) { // has icon
				// 	mipushIcon.setImageIcon(Icon.createWithResource(info.packageName, iconId));
				// else if ((iconId = appContext.getResources().getIdentifier(NOTIFICATION_ICON, "drawable", info.packageName)) != 0)
				// 	mipushIcon.setImageIcon(Icon.createWithResource(info.packageName, iconId));
					final int ref = iconId;
					mipushIcon.setImageIcon(cache.getMiPushIcon(appContext.getResources(), iconId, info.packageName));
				} else
					mipushIcon.setImageIcon(null);
				mipushIcon.setColorFilter(cache.getAppColor(appContext, info.packageName));
				// background
				Bitmap iconBackground = cache.getIconBackground(appContext, info.packageName);
				if (iconBackground != null) {
					// IconCache.removeBackground(iconBackground, Color.RED); // TODO 测试移除背景
					background.setImageBitmap(iconBackground);
				} else {
					background.setImageIcon(null);
				}
				// foreground & white
				Bitmap iconForeground = cache.getIconForeground(appContext, info.packageName);
				if (iconForeground != null) {
					foreground.setImageBitmap(iconForeground);
					whiten.setImageBitmap(IconCache.alphaize(info.packageName, iconForeground));
				} else {
					foreground.setImageIcon(null);
					whiten.setImageIcon(null);
				}
				// gen
				Icon iconCache = cache.getIcon(MainActivity.this, info.packageName);
				if (iconCache != null) {
					gen.setImageIcon(iconCache);
					gen.setColorFilter(cache.getAppColor(appContext, info.packageName));
				} else {
					gen.setImageIcon(null);
				}
			} catch (IllegalArgumentException | PackageManager.NameNotFoundException ign) { Log.d("inspect", "ex " + info.packageName);}
			return view;
		}
	}

	private static final String MIPUSH_SMALL_ICON = "mipush_small_notification";
	private static final String RES_PACKAGE = "com.notxx.icon.res";
	private static final String CHANNEL_ID = "test_channel";

	private ListView listView;
	private int notificationId;

	private Context createPackageContext(String packageName) {
		try {
			return createPackageContext(packageName, 0);
		} catch (IllegalArgumentException | PackageManager.NameNotFoundException ign) {
			Log.d("inspect", "ex " + packageName);
			return null;
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		createNotificationChannel();

		listView = new ListView(this);
		final PackageManager manager = getPackageManager();
		final Context context = createPackageContext(RES_PACKAGE);
		PackagesAdapter adapter = new PackagesAdapter(manager, context);
		listView.setOnItemClickListener(adapter);
		listView.setAdapter(adapter);
		setContentView(listView);
	}

	private void createNotificationChannel() {
		// Create the NotificationChannel, but only on API 26+ because
		// the NotificationChannel class is new and not in the support library
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			CharSequence name = getString(R.string.test_channel_name);
			String description = getString(R.string.test_channel_desc);
			int importance = NotificationManager.IMPORTANCE_DEFAULT;
			// Log.d("SmallIcon", "new NC " + name + description);
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
			channel.setDescription(description);
			// Register the channel with the system; you can't change the importance
			// or other notification behaviors after this
			NotificationManager notificationManager = getSystemService(NotificationManager.class);
			// Log.d("SmallIcon", "create NC " + channel);
			notificationManager.createNotificationChannel(channel);
		}
	}
}