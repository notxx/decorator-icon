package com.notxx.icon;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import top.trumeet.common.cache.IconCache;

public class MainActivity extends Activity {
	private static final String NOTIFICATION_ICON = "mipush_notification";
	private static final String NOTIFICATION_SMALL_ICON = "mipush_small_notification";

	private ListView listView;
	private IconCache cache;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		listView = new ListView(this);
		cache = IconCache.getInstance();
		final PackageManager manager = getPackageManager();
		final List<PackageInfo> packages = manager.getInstalledPackages(0);
		listView.setAdapter(new BaseAdapter() {
			@Override
			public int getCount() {
				return packages.size();
			}

			@Override
			public PackageInfo getItem(int position) {
				return packages.get(position);
			}

			@Override
			public long getItemId(int position) {
				return packages.get(position).hashCode();
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

				PackageInfo info = getItem(position);
				// applicationLabel
				TextView appName = (TextView) view.findViewById(R.id.appName);
				appName.setText(manager.getApplicationLabel(info.applicationInfo));
				// applicationIcon
				ImageView appIcon = (ImageView) view.findViewById(R.id.appIcon);
				appIcon.setImageDrawable(manager.getApplicationIcon(info.applicationInfo));
				// applicationLogo
				ImageView appLogo = (ImageView) view.findViewById(R.id.appLogo);
				appLogo.setImageDrawable(manager.getApplicationLogo(info.applicationInfo));
				// smallIcon
				ImageView smallIcon = (ImageView) view.findViewById(R.id.smallIcon);
				ImageView gen = (ImageView) view.findViewById(R.id.gen);
				try {
					final Context context = createPackageContext(info.packageName, 0);
					int iconId;
					if ((iconId  = context.getResources().getIdentifier(NOTIFICATION_SMALL_ICON, "drawable", info.packageName)) != 0) // has icon
						smallIcon.setImageIcon(Icon.createWithResource(info.packageName, iconId));
					else if ((iconId = context.getResources().getIdentifier(NOTIFICATION_ICON, "drawable", info.packageName)) != 0)
						smallIcon.setImageIcon(Icon.createWithResource(info.packageName, iconId));
					else
						smallIcon.setImageIcon(null);
					Icon iconCache = cache.getIconCache(context, info.packageName);
					if (iconCache != null) {
						gen.setImageIcon(iconCache);
						gen.setColorFilter(cache.getAppColor(context, info.packageName, (ctx, b) -> SmallIconDecorator.getBackgroundColor(b)));
					}
				} catch (IllegalArgumentException | PackageManager.NameNotFoundException ign) { Log.d("inspect", "ex " + info.packageName);}
				return view;
			}
		});
		setContentView(listView);
	}

}