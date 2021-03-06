package mono.hg.helpers;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

import mono.hg.Utils;

/*
 * A class used to handle icon packs.
 * The implementation is based off of rickeythefox's code @ StackOverflow
 * (https://stackoverflow.com/questions/24937890/using-icon-packs-in-my-app)
 */

public class IconPackHelper {
    private static HashMap<String, String> mPackagesDrawables = new HashMap<>();

    /**
     * Fetches icon from an icon pack by reading through its appfilter.xml content.
     *
     * @param packageManager PackageManager object used to fetch resources from the
     *                       icon pack.
     */
    public void loadIconPack(PackageManager packageManager) {
        String iconPackageName = PreferenceHelper.getIconPackName();
        XmlPullParser iconFilterXml = null;
        Resources iconRes = null;

        try {
            iconRes = packageManager.getResourcesForApplication(iconPackageName);
        } catch (PackageManager.NameNotFoundException e) {
            Utils.sendLog(3, e.toString());
        }

        // Get appfilter from the icon pack.
        try {
            InputStream iconAsset;
            int appFilterXml = 0;

            if (iconRes != null) {
                appFilterXml = iconRes.getIdentifier("appfilter", "xml", iconPackageName);
            }

            if (appFilterXml > 0) {
                iconFilterXml = iconRes.getXml(appFilterXml);
            } else {
                iconAsset = Utils.requireNonNull(iconRes).getAssets().open("appfilter.xml");
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                iconFilterXml = factory.newPullParser();
                iconFilterXml.setInput(iconAsset, "utf-8");
            }
        } catch (IOException | XmlPullParserException e) {
            Utils.sendLog(3, e.toString());
        }

        // Begin parsing the received appfilter.
        if (iconFilterXml != null) {
            try {
                int eventType = iconFilterXml.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if (iconFilterXml.getName().equals("item")) {
                            String componentName = null;
                            String drawableName = null;

                            for (int i = 0; i < iconFilterXml.getAttributeCount(); i++) {
                                if (iconFilterXml.getAttributeName(i).equals("component")) {
                                    componentName = iconFilterXml.getAttributeValue(i);
                                } else if (iconFilterXml.getAttributeName(i).equals("drawable")) {
                                    drawableName = iconFilterXml.getAttributeValue(i);
                                }
                            }

                            if (!mPackagesDrawables.containsKey(componentName)) {
                                mPackagesDrawables.put(componentName, drawableName);
                            }
                        }
                    }
                    eventType = iconFilterXml.next();
                }
            } catch (IOException | XmlPullParserException e) {
                Utils.sendLog(3, e.toString());
            }
        }
    }

    /**
     * Clears cached icon pack.
     */
    public static void clearDrawableCache() {
        mPackagesDrawables.clear();
    }

    private Drawable loadDrawable(Resources resources, String drawableName, String iconPackageName) {
        int icon = resources.getIdentifier(drawableName, "drawable", iconPackageName);
        if (icon > 0)
            return resources.getDrawable(icon);
        return null;
    }

    /**
     * Loads an icon from the icon pack based on the received package name.
     *
     * @param packageManager PackageManager object to determine the launch intent of
     *                       the package name.
     *
     * @param appPackageName Package name of the app whose icon is to be loaded.
     *
     * @return Drawable Will return null if there is no icon associated with the package name,
     *         otherwise an associated icon from the icon pack will be returned.
     */
    // Load icon from the cached appfilter.
    public Drawable getIconDrawable(PackageManager packageManager, String appPackageName) {
        Intent launchIntent = packageManager.getLaunchIntentForPackage(appPackageName);
        String iconPackageName = PreferenceHelper.getIconPackName();
        String componentName = null;
        Resources iconRes = null;

        try {
            iconRes = packageManager.getResourcesForApplication(iconPackageName);
        } catch (PackageManager.NameNotFoundException e) {
            Utils.sendLog(3, e.toString());
        }

        if (launchIntent != null) {
            ComponentName component = Utils.requireNonNull(packageManager.getLaunchIntentForPackage(appPackageName)).getComponent();
            componentName = Utils.requireNonNull(component).toString();
        }

        String drawable = mPackagesDrawables.get(componentName);
        if (drawable != null && iconRes != null) {
            // Load and return.
            return loadDrawable(iconRes, drawable, iconPackageName);
        } else {
            // Manually retrieve resource by brute-forcing its component name.
            if (componentName != null) {
                int start = componentName.indexOf("{") + 1;
                int end = componentName.indexOf("}",  start);
                if (end > start && iconRes != null) {
                    drawable = componentName.substring(start,end).toLowerCase(Locale.getDefault()).replace(".","_").replace("/", "_");
                    if (iconRes.getIdentifier(drawable, "drawable", iconPackageName) > 0)
                        return loadDrawable(iconRes, drawable, iconPackageName);
                }
            }
        }
        return null;
    }
}
